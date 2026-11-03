"""Mixed travel cards: legacy API, atomic form save, order and snapshots on a test DB."""
import re
from api_check import Client, BASE

visitor, other, public, admin = Client('visitor'), Client('visitor2'), Client(), Client('admin')
web = BASE.removesuffix('/api/v1')
with public.http.open(web + '/explore') as response:
    html = response.read().decode()
keys = {kind: re.search(r'data-card-key="(' + kind + r':[^"]+)"', html).group(1)
        for kind in ('place', 'event', 'dining')}
assert 'data-card-kind="merchant"' not in html
keys['merchant'] = 'merchant:1'
journey = visitor.request('POST', '/me/journeys', {'name': '통합 카드 검증'}, expected=201)[1]
path = '/me/journeys/' + journey['id']
# Keep the original numeric placeId API compatible and distinguish kinds with equal IDs.
visitor.request('POST', path + '/places', {'placeId': keys['place'].split(':')[1]})
for key in (keys['event'], keys['merchant'], keys['dining'], keys['event']):
    visitor.request('POST', path + '/places', {'cardKey': key})
items = visitor.request('GET', path)[1]['places']
assert len(items) == 4 and {item['cardKind'] for item in items} == {'PLACE', 'EVENT', 'MERCHANT', 'DINING'}
for invalid in ('event:missing', 'dining:missing', 'merchant:999999', 'place:999999'):
    visitor.request('POST', path + '/places', {'cardKey': invalid}, expected=404)
for invalid in ('bad:1', 'event:<script>', 'dining:<script>', 'merchant:0'):
    visitor.request('POST', path + '/places', {'cardKey': invalid}, expected=400)
other.request('POST', path + '/places', {'cardKey': keys['event']}, expected=404)
visitor.request('POST', path + '/places', {'cardKey': keys['event'], 'placeId': '1'}, expected=400)
order = [keys['merchant'], keys['place'], keys['event'], keys['dining']]

def save(values, expected=200):
    return visitor.form('/journeys/update', {
        'id': journey['id'], 'name': '순서를 저장한 여행', 'cardKey': values
    }, expected)

restored = save(order)
assert 'data-selection-order="' + ','.join(order) + '"' in restored
saved = visitor.request('GET', path)[1]['places']
assert [item['cardKey'] for item in saved] == order
assert {item['id'] for item in saved} == {item['id'] for item in items}
# Inactive sources still restore and save their existing snapshots and visit records.
place_id = keys['place'].split(':')[1]
merchant_id = keys['merchant'].split(':')[1]
admin.request('PATCH', '/admin/ontology/places/' + place_id, {'active': False})
admin.request('PATCH', '/admin/merchants/' + merchant_id, {'active': False})
try:
    restored = save(order)
    assert restored.count('data-saved-only="true"') >= 2
    assert visitor.request('GET', path)[1]['places'] == saved
finally:
    admin.request('PATCH', '/admin/ontology/places/' + place_id, {'active': True})
    admin.request('PATCH', '/admin/merchants/' + merchant_id, {'active': True})
save(order + ['event:missing'], 404)
assert visitor.request('GET', path)[1]['places'] == saved
dining = next(item for item in saved if item['cardKind'] == 'DINING')
assert dining['materialKind'] == 'REAL'
visitor.request('PUT', path + '/places/' + dining['id'] + '/record', {
    'visitedOn': '2026-10-01', 'note': '음식점 방문', 'rating': 5
})
assert visitor.request('GET', path)[1]['places'][3]['rating'] == 5
# All kinds support the same order and visit record endpoints.
event = next(item for item in saved if item['cardKind'] == 'EVENT')
visitor.request('PUT', path + '/places/' + event['id'] + '/record', {
    'visitedOn': '2026-10-01', 'note': '행사 방문 기록', 'rating': 4
})
visitor.request('POST', path + '/places/' + event['id'] + '/move', {'direction': 'UP'})
assert visitor.request('GET', path)[1]['places'][1]['cardKey'] == keys['event']
visitor.request('DELETE', path, expected=204)
# A non-place card by itself is sufficient for confirmation.
solo = visitor.request('POST', '/me/journeys', {'name': '행사만 담은 여행'}, expected=201)[1]
solo_path = '/me/journeys/' + solo['id']
visitor.request('POST', solo_path + '/places', {'cardKey': keys['event']})
assert visitor.request('PATCH', solo_path, {'status': 'CONFIRMED'})[1]['status'] == 'CONFIRMED'
visitor.request('POST', solo_path + '/places', {'cardKey': keys['merchant']}, expected=409)
print('PASS: all card kinds, legacy placeId, deduplication, validation, ownership, atomic save/order, records, non-place confirmation')

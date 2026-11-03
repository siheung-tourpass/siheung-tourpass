"""Tourism feedback isolation, validation, current-response aggregation and persistence fixture."""
from concurrent.futures import ThreadPoolExecutor
from api_check import Client

public, visitor, other, staff, admin = [Client(name) for name in (None, 'visitor', 'visitor2', 'staff-a', 'admin')]
path = '/me/places/1/feedback'
stats = '/admin/place-feedback'
assert admin.request('GET', stats)[1]['items'] == []
for client, status in ((public, 401), (staff, 403), (admin, 403)):
    client.request('PUT', path, {'rating': 5}, expected=status)
    client.request('GET', path, expected=status)
for client, status in ((public, 401), (visitor, 403), (staff, 403)):
    client.request('GET', stats, expected=status)
visitor.request('PUT', path, {'rating': 5}, csrf=False, expected=403)
visitor.request('GET', path, expected=404)
for body in ({'rating': 0}, {'rating': 6}, {'rating': '5'}, {'rating': True}, {'rating': 5, 'comment': 'x'*501}, {'rating': 5, 'visitorId': '2'}, {}):
    visitor.request('PUT', path, body, expected=400)
visitor.request('PUT', '/me/places/999999999/feedback', {'rating': 5}, expected=404)
def scores():
    return [(r['place']['id'], r['score']) for r in visitor.request('GET', '/recommendations')[1]['items']]
before = scores()
comment = '<script>private_place_opinion</script>'
visitor.request('PUT', path, {'rating': 5, 'comment': comment})
other.request('GET', path, expected=404)
other.request('DELETE', path, {}, expected=204)
assert visitor.request('GET', path)[1]['comment'] == comment
other.request('PUT', path, {'rating': 1})
def row():
    return next(r for r in admin.request('GET', stats)[1]['items'] if r['placeId'] == '1')
r = row()
assert r['responseCount'] == 2 and r['averageRating'] == 3 and r['rating1'] == 1 and r['rating5'] == 1
assert not {'comment', 'visitorId', 'loginId', 'updatedAt'} & r.keys()
assert scores() == before
second_session = Client('visitor')
with ThreadPoolExecutor(2) as pool:
    list(pool.map(lambda c: c.request('PUT', path, {'rating': 4, 'comment': '재시작 보존 확인'}), (visitor, second_session)))
assert row()['responseCount'] == 2 and row()['averageRating'] == 2.5
admin.request('PATCH', '/admin/ontology/places/1', {'active': False})
assert row()['responseCount'] == 2
assert visitor.request('GET', path)[1]['rating'] == 4
visitor.request('PUT', path, {'rating': 3}, expected=404)
other.request('DELETE', path, {}, expected=204)
assert row()['responseCount'] == 1 and row()['averageRating'] == 4
visitor.request('DELETE', path, {}, expected=204)
assert admin.request('GET', stats)[1]['items'] == []
admin.request('PATCH', '/admin/ontology/places/1', {'active': True})
# Public DRAFT tourism cards accept feedback without enabling recommendation.
draft = next((p for p in admin.request('GET', '/admin/ontology/places?size=100')[1]['items'] if p['materialKind'] == 'REAL' and p['reviewStatus'] == 'DRAFT' and p['active']), None)
if draft:
    draft_path = '/me/places/' + draft['id'] + '/feedback'
    visitor.request('PUT', draft_path, {'rating': 3})
    assert visitor.request('GET', draft_path)[1]['rating'] == 3
    visitor.request('DELETE', draft_path, {}, expected=204)
visitor.request('PUT', path, {'rating': 4, 'comment': '재시작 보존 확인'})
print('PASS: place feedback ownership, roles, CSRF, validation, concurrent upsert, aggregates, hidden places, recommendation separation')

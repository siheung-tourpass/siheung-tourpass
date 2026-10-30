"""Run only on a separate fresh seeded DB with tourism-sql.py imports.
Test approvals exercise application rules; they do not verify source freshness.
Usage: python3 backend/tests/tourism_check.py [API URL]
"""
import json
from datetime import datetime
from pathlib import Path
from urllib.parse import urlencode
from zoneinfo import ZoneInfo
from api_check import Client, BASE

data = json.loads((Path(__file__).resolve().parents[1] / 'data/siheung-tourism.json').read_text())
public, admin, visitor = Client(), Client('admin'), Client('visitor')
places = {p['code']: p for p in admin.request('GET', '/admin/ontology/places?size=100')[1]['items']}
assert not visitor.request('GET', '/me/passes')[1]['items'], 'Use a fresh test DB'
assert all(places[p['code']]['reviewStatus'] == 'DRAFT' for p in data['places'])
with public.http.open(BASE.removesuffix('/api/v1')+'/explore') as response:
    browse_html=response.read().decode()
assert '갯골생태공원' in browse_html and '/assets/places/place_real_001.jpg' in browse_html
assert '검토 대기' in browse_html and '승인 후 담기' not in browse_html
assert '좌우 스크롤로 카드 탐색' in browse_html

options = public.request('GET', '/recommendation-options')[1]
ids = {key: {x['code']: x['id'] for x in rows} for key, rows in options.items() if key != '_links'}
sources = {s['code']: s for s in data['sources']}

def place_path(code):
    return '/admin/ontology/places/' + places[code]['id']

def recommend(example):
    params = example['input']
    query = [('themeId', ids['themes'][t]) for t in params['themes']]
    if params['region']:
        query.append(('regionId', ids['regions'][params['region']]))
    if params['companion']:
        query.append(('companionId', ids['companionTypes'][params['companion']]))
    return public.request('GET', '/recommendations?' + urlencode(query))[1]['items']

# Hide seeded demo candidates so the JSON examples describe the entire result.
for p in places.values():
    if p['materialKind'] == 'DEMO':
        admin.request('PATCH', '/admin/ontology/places/' + p['id'], {'active': False})
for example in data['semantic_examples']:
    assert recommend(example) == []

journey = visitor.request('POST', '/me/journeys', {'name': '실제 자료 통합 검사'}, expected=201)[1]
journey_path = '/me/journeys/' + journey['id']
first = data['places'][0]['code']
pending = visitor.request('POST', journey_path + '/places', {'placeId': places[first]['id']})[1]
assert pending['places'][0]['placeId'] == places[first]['id']
assert recommend(data['semantic_examples'][0]) == []
visitor.request('DELETE', journey_path + '/places/' + pending['places'][0]['id'], {})

for p in data['places']:
    stored = places[p['code']]
    assert stored['materialKind'] == 'REAL' and stored['sourceUrl'] == sources[p['source_ids'][0]]['url']
    assert stored['checkedOn'] == p['checked_on'] and stored['evidenceNote'] == p['evidence_note']
    if p['address_status'] == 'SOURCE_RECORDED' and p['address']:
        assert stored['locationDescription'] == p['address']
    else:
        assert stored['locationDescription'] == p['location_note'] + ' · 상세 주소 미확인'
    assert stored['regionId'] == ids['regions'][p['region_code']]
    public.request('GET', '/places/' + stored['id'], expected=404)
    visitor.request('PUT', place_path(p['code']) + '/review', {'status': 'APPROVED'}, expected=403)
    admin.request('PUT', place_path(p['code']) + '/review', {'status': 'APPROVED'})
    detail = public.request('GET', '/places/' + stored['id'])[1]
    assert detail['themes'] == {} and detail['companions'] == []
    assert {s['sourceUrl'] for s in detail['sources']} == {sources[s]['url'] for s in p['source_ids'][1:]}

# Place approval alone must not enable DRAFT theme/companion relations.
for example in data['semantic_examples'][:2]:
    assert all(item['score']['base'] == 0 for item in recommend(example))
for relation in data['annotations']:
    if relation['predicate'] == 'locatedIn':
        continue
    kind, target = ('has-theme', 'themes') if relation['predicate'] == 'hasTheme' else ('suitable-for', 'companionTypes')
    path = '/admin/ontology/relations/' + kind + '/' + places[relation['subject']]['id'] + '/' + ids[target][relation['object']]
    stored = admin.request('GET', path)[1]
    assert stored['reviewStatus'] == 'DRAFT' and stored['materialKind'] == relation['material_kind']
    assert stored['sourceUrl'] == sources[relation['source_ids'][0]]['url']
    assert stored['checkedOn'] == relation['checked_on'] and stored['evidenceNote'] == relation['evidence_note']
    admin.request('PUT', path + '/review', {'status': 'APPROVED'})

for example in data['semantic_examples'][:2]:
    results = recommend(example)
    assert [(x['place']['code'], x['score']['base']) for x in results] == [(x['place'], x['base_score']) for x in example['expected']]
    for result, expected in zip(results, example['expected']):
        assert result['score']['personal'] == example['personal_adjustment']
        assert result['passBenefits'] == []
        if 'inference' in expected:
            assert result['matchedThemes'][0]['path'] == expected['inference']
            assert result['matchedThemes'][0]['matchType'] == 'INFERRED'

assert public.request('GET', '/recommendations?regionId=' + ids['regions']['REGION_EMPTY'])[1]['items'] == []
saved = visitor.request('POST', journey_path + '/places', {'placeId': places[first]['id']})[1]['places']
assert len(saved) == 1 and saved[0]['materialKind'] == 'REAL'
# Self-reported tourism visits must not become verified benefit feedback.
region_query = '/recommendations?regionId=' + ids['regions'][data['places'][0]['region_code']]
before = visitor.request('GET', region_query)[1]['items']
assert any(item['place']['id'] == places[first]['id'] for item in before)
assert all(item['personalizationReason'] == 'NO_FEEDBACK' for item in before)
assert visitor.request('GET', '/me/redemptions')[1]['items'] == []
visitor.request('PUT', journey_path + '/places/' + saved[0]['id'] + '/record', {
    'visitedOn': datetime.now(ZoneInfo('Asia/Seoul')).date().isoformat(),
    'note': '직접 남긴 실제 관광 장소 방문 기록', 'rating': 1,
})
visitor.request('PATCH', journey_path, {'status': 'CONFIRMED'})
visitor.request('PATCH', journey_path, {'status': 'ARCHIVED'})
archived = visitor.request('GET', journey_path)[1]
assert archived['status'] == 'ARCHIVED'
saved = archived['places']
assert saved[0]['rating'] == 1 and saved[0]['note'] == '직접 남긴 실제 관광 장소 방문 기록'
assert visitor.request('GET', region_query)[1]['items'] == before
assert visitor.request('GET', '/me/redemptions')[1]['items'] == []
assert visitor.request('GET', '/me/passes')[1]['items'] == []
admin.request('PATCH', place_path(first), {'description': '테스트: 재검토 필요'})
public.request('GET', '/places/' + places[first]['id'], expected=404)
assert all(x['place']['code'] != first for x in public.request('GET', region_query)[1]['items'])
assert visitor.request('GET', journey_path)[1]['places'] == saved
admin.request('PUT', place_path(first) + '/review', {'status': 'APPROVED'})
admin.request('PATCH', place_path(first), {'active': False})
assert all(x['place']['code'] != first for x in public.request('GET', region_query)[1]['items'])
assert visitor.request('GET', journey_path)[1]['places'] == saved
print('PASS: tourism DRAFT import, provenance, separate reviews, semantic examples, region filter, journey records/snapshots and feedback separation')

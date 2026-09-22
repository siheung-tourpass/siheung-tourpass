#!/usr/bin/env python3
"""Convert the tourism JSON source into DRAFT imports."""
import argparse
import json
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--replace-demo', action='store_true', help='가상 장소를 비활성화하여 실제 자료로 전환 (기록 보존)')
parser.add_argument('--place-code', action='append', help='지정한 장소와 관계만 추가 (여러 번 지정 가능)')
args = parser.parse_args()
data = json.loads((Path(__file__).resolve().parents[1] / 'data/siheung-tourism.json').read_text())
if args.place_code:
    selected = set(args.place_code)
    unknown = selected - {p['code'] for p in data['places']}
    if unknown:
        parser.error('알 수 없는 장소 코드: ' + ', '.join(sorted(unknown)))
    data['places'] = [p for p in data['places'] if p['code'] in selected]
    data['annotations'] = [a for a in data['annotations'] if a['subject'] in selected]
def quote(value):
    return 'NULL' if value is None else "'" + str(value).replace("'", "''") + "'"
def ref(table, code):
    return '(SELECT id FROM ' + table + ' WHERE code=' + quote(code) + ')'
def insert(table, values, ignore=False):
    print('INSERT INTO ' + table + '(' + ','.join(values) + ') VALUES(' + ','.join(values.values()) + ')' + (' ON CONFLICT(code) DO NOTHING' if ignore else '') + ';')
print("SET TIME ZONE 'UTC'; BEGIN;")
for key, table in [('regions','region'),('themes','theme'),('companion_types','companion_type')]:
    for row in data[key]: insert(table, {'code':quote(row['code']), 'name':quote(row['label'])}, True)
sources = {row['code']:row for row in data['sources']}
for place in data['places']:
    source = sources[place['source_ids'][0]]
    location = place['address'] if place['address_status'] == 'SOURCE_RECORDED' and place['address'] else place['location_note'] + ' · 상세 주소 미확인'
    insert('place', {'code':quote(place['code']), 'region_id':ref('region',place['region_code']), 'name':quote(place['label']), 'description':quote(place['description']), 'location_description':quote(location), 'material_kind':"'REAL'", 'source_url':quote(source['url']), 'checked_on':quote(place['checked_on']), 'evidence_note':quote(place['evidence_note'])})
    for source_id in place['source_ids'][1:]:
        s=sources[source_id]
        insert('place_source', {'place_id':ref('place',place['code']), 'source_url':quote(s['url']), 'checked_on':quote(s['checked_on']), 'evidence_note':quote(s['evidence_section'])})
for row in data['annotations']:
    relation=row['predicate']
    if relation not in ('hasTheme','suitableFor'): continue
    table, target_table, target_column = ('place_theme','theme','theme_id') if relation=='hasTheme' else ('place_companion','companion_type','companion_type_id')
    source=sources[row['source_ids'][0]]
    insert(table, {'place_id':ref('place',row['subject']), target_column:ref(target_table,row['object']), 'material_kind':"'REAL'", 'source_url':quote(source['url']), 'checked_on':quote(row['checked_on']), 'evidence_note':quote(row['evidence_note'])})
if args.replace_demo:
    print("UPDATE place SET active=FALSE WHERE material_kind='DEMO';")
    print("UPDATE region SET active=FALSE WHERE code='REGION_EMPTY';")
print('UPDATE ontology_revision SET revision=revision+1 WHERE id=1; COMMIT;')

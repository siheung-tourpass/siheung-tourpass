#!/usr/bin/env python3
"""Convert the single tourism JSON source into optional DRAFT imports."""
import json
from pathlib import Path

data = json.loads((Path(__file__).resolve().parents[1] / 'data/siheung-tourism.json').read_text())
def quote(value):
    return 'NULL' if value is None else "'" + str(value).replace('\\', '\\\\').replace("'", "''") + "'"
def ref(table, code):
    return '(SELECT id FROM ' + table + ' WHERE code=' + quote(code) + ')'
def insert(table, values, ignore=False):
    print('INSERT ' + ('IGNORE ' if ignore else '') + 'INTO ' + table + '(' + ','.join(values) + ') VALUES(' + ','.join(values.values()) + ');')
print("SET NAMES utf8mb4; SET time_zone='+00:00'; START TRANSACTION;")
for key, table in [('regions','region'),('themes','theme'),('companion_types','companion_type')]:
    for row in data[key]: insert(table, {'code':quote(row['code']), 'name':quote(row['label'])}, True)
sources = {row['code']:row for row in data['sources']}
for place in data['places']:
    source = sources[place['source_ids'][0]]
    insert('place', {'code':quote(place['code']), 'region_id':ref('region',place['region_code']), 'name':quote(place['label']), 'description':quote(place['description']), 'location_description':quote(place['location_note']), 'material_kind':"'REAL'", 'source_url':quote(source['url']), 'checked_on':quote(place['checked_on']), 'evidence_note':quote(place['evidence_note'])})
    for source_id in place['source_ids'][1:]:
        s=sources[source_id]
        insert('place_source', {'place_id':ref('place',place['code']), 'source_url':quote(s['url']), 'checked_on':quote(s['checked_on']), 'evidence_note':quote(s['evidence_section'])})
for row in data['annotations']:
    relation=row['predicate']
    if relation not in ('hasTheme','suitableFor'): continue
    table, target_table, target_column = ('place_theme','theme','theme_id') if relation=='hasTheme' else ('place_companion','companion_type','companion_type_id')
    source=sources[row['source_ids'][0]]
    insert(table, {'place_id':ref('place',row['subject']), target_column:ref(target_table,row['object']), 'material_kind':"'REAL'", 'source_url':quote(source['url']), 'checked_on':quote(row['checked_on']), 'evidence_note':quote(row['evidence_note'])})
print('UPDATE ontology_revision SET revision=revision+1 WHERE id=1; COMMIT;')

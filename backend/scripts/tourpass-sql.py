#!/usr/bin/env python3
"""Convert the virtual merchant/product source into SQL; run after the tourism import.

Re-running adds only new codes and keeps admin edits to existing rows."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1] / 'data'
data = json.loads((root / 'siheung-tourpass.json').read_text())
places = {p['code']: p for p in json.loads((root / 'siheung-tourism.json').read_text())['places']}
dining = {p['code']: p for p in json.loads((root / 'siheung-dining.json').read_text())['places']}
def quote(value):
    return 'NULL' if value is None else "'" + str(value).replace("'", "''") + "'"
def ref(table, code, column='code'):
    return '(SELECT id FROM ' + table + ' WHERE ' + column + '=' + quote(code) + ')'
def insert(table, values):
    print('INSERT INTO ' + table + '(' + ','.join(values) + ') VALUES(' + ','.join(values.values()) + ') ON CONFLICT DO NOTHING;')
print("SET TIME ZONE 'UTC'; BEGIN;")
# Real-card merchants replace the fictional A/B demo set; their issue history stays.
print("UPDATE product SET active=FALSE WHERE code='DEMO_DAY';")
print("UPDATE merchant SET active=FALSE WHERE code IN ('MERCHANT_A','MERCHANT_B');")
merchants = {m['code']: m for m in data['merchants']}
for m in data['merchants']:
    if 'place' in m:
        p = places[m['place']]
        location = p['address'] if p['address_status'] == 'SOURCE_RECORDED' and p['address'] else p['location_note']
        card = {'place_id': ref('place', m['place']), 'region_id': ref('region', p['region_code']), 'name': quote(p['label'])}
    else:
        p = dining[m['dining']]
        location = p['address']
        card = {'dining_code': quote(m['dining']), 'region_id': ref('region', p['regionCode']), 'name': quote(p['name'])}
    insert('merchant', {'code': quote(m['code'])} | card | {'location_description': quote(location)})
    for staff in ('staff-a', 'staff-b'):
        insert('merchant_staff', {'user_id': ref('app_user', staff, 'login_id'), 'merchant_id': ref('merchant', m['code'])})
for product in data['products']:
    insert('product', {'code': quote(product['code']), 'name': quote(product['name']), 'description': quote(product['description']), 'price_won': str(product['price_won'])})
    included = list(merchants) if product['merchants'] == 'ALL' else product['merchants']
    for code in included:
        b = merchants[code]['benefit']
        free = b['type'] == 'FREE_ONCE'
        insert('benefit', {'code': quote(product['code'] + '_' + code), 'product_id': ref('product', product['code']), 'merchant_id': ref('merchant', code),
                           'name': quote(b['name']), 'description': quote(b['description']), 'benefit_type': quote(b['type']),
                           'base_price_won': 'NULL' if free else str(b['base_price_won']), 'discount_won': 'NULL' if free else str(b['discount_won']),
                           'payable_won': '0' if free else str(b['base_price_won'] - b['discount_won']), 'reservation_note': quote('예약 불필요')})
print('COMMIT;')

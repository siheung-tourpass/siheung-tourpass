"""Run after tourism_check.py once tourpass-sql.py output is loaded.
Usage: python3 backend/tests/python/tourpass_check.py [API URL]
"""
import json
from datetime import datetime, time, timedelta
from zoneinfo import ZoneInfo
from pathlib import Path
from api_check import Client

data = json.loads((Path(__file__).resolve().parents[2] / 'data/siheung-tourpass.json').read_text())
KST = ZoneInfo('Asia/Seoul')
public, visitor = Client(), Client('visitor2')
products = {p['code']: p for p in public.request('GET', '/products')[1]['items']}
assert {code: p['priceWon'] for code, p in products.items()} == {p['code']: p['price_won'] for p in data['products']}
merchants = {m['code']: m for m in public.request('GET', '/merchants?size=100')[1]['items']}
assert set(merchants) == {m['code'] for m in data['merchants']}
for m in data['merchants']:
    stored = merchants[m['code']]
    assert (stored['placeId'] is None) == ('dining' in m) and stored['diningCode'] == m.get('dining')
sea = next(p for p in data['products'] if p['code'] == 'TP_OIDO_SEA')
visitor.request('POST', '/me/passes', {'productId': products['TP_OIDO_SEA']['id'], 'uses': 3}, expected=400)
issued = visitor.request('POST', '/me/passes', {'productId': products['TP_OIDO_SEA']['id']}, expected=201)[1]
assert issued['priceWon'] == sea['price_won'] and 'useLimit' not in issued
assert len(issued['benefits']) == len(sea['merchants']) and any(b['diningCode'] and b['placeId'] is None for b in issued['benefits'])
# Every included benefit can be used once; a second use of the same one is refused.
staff = Client('staff-a')
for benefit in issued['benefits']:
    lookup = staff.request('POST', '/merchant/pass-lookups', {'merchantId': benefit['merchantId'], 'code': issued['code']}, expected=201)[1]
    assert lookup['benefits'][0]['usable']
    staff.request('POST', '/merchant/redemptions', {'confirmationToken': lookup['confirmationToken'], 'issuedBenefitId': benefit['id']}, expected=201)
lookup = staff.request('POST', '/merchant/pass-lookups', {'merchantId': issued['benefits'][0]['merchantId'], 'code': issued['code']}, expected=201)[1]
assert lookup['benefits'][0]['reason'] == 'BENEFIT_ALREADY_USED'
# A product without a price is not on sale yet.
admin = Client('admin')
unpriced = admin.request('POST', '/admin/products', {'code': 'TP_UNPRICED_' + datetime.now().strftime('%H%M%S%f'), 'name': '가격 미정', 'description': '검사'}, expected=201)[1]
visitor.request('POST', '/me/passes', {'productId': unpriced['id']}, expected=409, code='PRODUCT_INACTIVE')
# Periods may not overlap; a later trip date gets its own pass linked to that trip.
visitor.request('POST', '/me/passes', {'productId': products['TP_ECO']['id']}, expected=409)
day = (datetime.now(KST) + timedelta(days=10)).date()
trip = visitor.request('POST', '/me/journeys', {'name': '날짜 검사', 'travelDate': day.isoformat()}, expected=201)[1]
later = visitor.request('POST', '/me/passes', {'productId': products['TP_ECO']['id'], 'journeyId': trip['id']}, expected=201)[1]
assert datetime.fromisoformat(later['startsAt']) == datetime.combine(day, time(), KST)
assert visitor.request('GET', '/me/journeys/' + trip['id'])[1]['issuedPassId'] == later['id']
visitor.request('POST', '/me/passes', {'productId': products['TP_ALL']['id'], 'journeyId': trip['id']}, expected=409)
print('PASS: virtual merchants on real places and dining cards, single prices, each benefit once, unpriced products off sale, non-overlapping periods, trip-dated linked purchase')

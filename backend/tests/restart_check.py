"""Run after api_check.py and restarting both Tomcat and MySQL."""
from api_check import Client
v, a = Client('visitor'), Client('admin')
p = v.request('GET','/me/passes')[1]['items'][0]
assert p['status'] == 'CANCELLED'
detail = v.request('GET','/me/passes/'+p['id'])[1]
assert detail['benefits'][0]['remainingCount'] == 0
r = v.request('GET','/me/redemptions')[1]['items'][0]
f = v.request('GET','/me/redemptions/'+r['id']+'/feedback')[1]
assert f['rating'] == 5 and f['comment'] == '재시작 확인'
rec = v.request('GET','/recommendations?themeId=2&companionId=1')[1]
assert rec['items'][0]['score']['total'] == 5
assert 'code' not in a.request('GET','/admin/passes/'+p['id'])[1]
print('PASS: restart preserves snapshots, redemption, cancellation, feedback and ranking')

journeys=v.request('GET','/me/journeys?status=ARCHIVED')[1]['items']
j=next(x for x in journeys if x['name']=='기록 보존 검사')
j=v.request('GET','/me/journeys/'+j['id'])[1]
assert j['places'][0]['note']=='재시작 여행 기록' and j['places'][0]['rating']==4
assert j['benefitPass']['status']=='CANCELLED'
print('PASS: restart preserves journey archive, records and linked cancelled benefit pass')

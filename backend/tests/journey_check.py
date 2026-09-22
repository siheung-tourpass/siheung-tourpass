"""Personal journeys on a seeded test DB; also safe after api_check.py."""
import concurrent.futures
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo
from api_check import Client

v, other, public, staff, admin = [Client(x) for x in ('visitor','visitor2',None,'staff-a','admin')]
public.request('GET','/me/journeys',expected=401)
staff.request('GET','/me/journeys',expected=403)
v.request('POST','/me/journeys',{'name':'여행'},csrf=False,expected=403)
for b in ({'name':''},{'name':'x'*101},{'name':'여행','travelDate':'2026-02-30'},{'name':'여행','travelDate':'0000-01-01'},{'name':'여행','ownerId':'2'}):
    v.request('POST','/me/journeys',b,expected=400)
j=v.request('POST','/me/journeys',{'name':'기록 보존 검사','travelDate':'2026-10-01'},expected=201)[1]
p='/me/journeys/'+j['id']
v.request('PATCH',p,{'name':'여행 수정','travelDate':None})
other.request('GET',p,expected=404)
other.request('PATCH',p,{'name':'타인 변경'},expected=404)
other.request('POST',p+'/places',{'placeId':'1'},expected=404)
v2=Client('visitor')
with concurrent.futures.ThreadPoolExecutor(2) as pool:
    list(pool.map(lambda c:c.request('POST',p+'/places',{'placeId':'1'}),[v,v2]))
j=v.request('GET',p)[1];assert len(j['places'])==1
first=j['places'][0];a=first['id']
j=v.request('POST',p+'/places',{'placeId':'2'})[1];b=j['places'][1]['id']
v.request('PUT',p+'/places/'+b+'/move',{'direction':'UP'})
assert [x['id'] for x in v.request('GET',p)[1]['places']]==[b,a]
v.request('PUT',p+'/places/'+a+'/move',{'direction':'UP'})
record=p+'/places/'+a+'/record'
future=(datetime.now(ZoneInfo('Asia/Seoul')).date()+timedelta(days=1)).isoformat()
for body in ({'visitedOn':None,'note':None,'rating':5},{'visitedOn':future,'note':None,'rating':1},{'visitedOn':'2026-10-01','note':'x'*501,'rating':1},{'visitedOn':'2026-10-01','note':None,'rating':6}):
    v.request('PUT',record,body,expected=400)
other.request('PUT',record,{'visitedOn':'2026-10-01','note':'타인','rating':1},expected=404)
v.request('PUT',record,{'visitedOn':'2026-10-01','note':'<script>개인 기록</script>','rating':5})
v.request('PATCH',p,{'status':'ARCHIVED'})
v.request('POST',p+'/places',{'placeId':'3'},expected=409)
v.request('DELETE',p+'/places/'+b,expected=409)
v.request('PUT',p+'/places/'+a+'/move',{'direction':'DOWN'},expected=409)
assert j['id'] in [x['id'] for x in v.request('GET','/me/journeys?status=ARCHIVED')[1]['items']]
v.request('PUT',record,{'visitedOn':None,'note':None,'rating':None})
v.request('PUT',record,{'visitedOn':'2026-10-01','note':'재시작 여행 기록','rating':4})
# Current place data may change without destroying the saved memory.
admin.request('PATCH','/admin/ontology/places/1',{'active':False})
assert v.request('GET',p)[1]['places'][0]['placeName']==first['placeName']
second=v.request('POST','/me/journeys',{'name':'새 계획'},expected=201)[1]
s='/me/journeys/'+second['id']
v.request('POST',s+'/places',{'placeId':'1'},expected=404)
admin.request('PATCH','/admin/ontology/places/1',{'active':True})
admin.request('PATCH','/admin/ontology/places/1',{'description':'변경 소개'})
v.request('POST',s+'/places',{'placeId':'1'},expected=404)
admin.request('PUT','/admin/ontology/places/1/review',{'status':'APPROVED'})
v.request('PATCH',p,{'status':'PLANNING'})
v.request('DELETE',p+'/places/'+b)
assert len(v.request('GET',p)[1]['places'])==1
# Only own benefit passes can be attached; cancellation leaves the archive intact.
passes=v.request('GET','/me/passes')[1]['items']
benefit=passes[0] if passes else v.request('POST','/me/passes',{'productId':'1'},expected=201)[1]
v.request('PATCH',p,{'issuedPassId':benefit['id']})
other.request('PATCH','/me/journeys/'+other.request('POST','/me/journeys',{'name':'다른 여행'},expected=201)[1]['id'],{'issuedPassId':benefit['id']},expected=404)
if benefit['status']=='ACTIVE':admin.request('PUT','/admin/passes/'+benefit['id']+'/cancellation',{'reason':'여행 연결 검사'})
assert v.request('GET',p)[1]['benefitPass']['status']=='CANCELLED'
v.request('PATCH',p,{'issuedPassId':None})
assert 'benefitPass' not in v.request('GET',p)[1]
v.request('PATCH',p,{'name':'기록 보존 검사','issuedPassId':benefit['id'],'status':'ARCHIVED'})
print('PASS: journey ownership, CSRF, concurrent deduplication, ordering, records, archive/restore, snapshots, benefit linkage')

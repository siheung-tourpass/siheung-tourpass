"""Personal journeys on a seeded test DB; also safe after api_check.py."""
import concurrent.futures
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo
import urllib.error
import urllib.parse
import urllib.request
from api_check import Client, BASE

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
# Boundary moves are no-ops; both directions preserve the other place.
for item,direction,expected in ((a,'UP',[a,b]),(b,'DOWN',[a,b]),(a,'DOWN',[b,a]),(a,'UP',[a,b])):
    moved=v.request('PUT',p+'/places/'+item+'/move',{'direction':direction})[1]
    assert [place['id'] for place in moved['places']]==expected
record=p+'/places/'+a+'/record'
future=(datetime.now(ZoneInfo('Asia/Seoul')).date()+timedelta(days=1)).isoformat()
for body in ({'visitedOn':None,'note':None,'rating':5},{'visitedOn':future,'note':None,'rating':1},{'visitedOn':'2026-10-01','note':'x'*501,'rating':1},{'visitedOn':'2026-10-01','note':None,'rating':6}):
    v.request('PUT',record,body,expected=400)
other.request('PUT',record,{'visitedOn':'2026-10-01','note':'타인','rating':1},expected=404)
v.request('PUT',record,{'visitedOn':'2026-10-01','note':'<script>개인 기록</script>','rating':5})
v.request('DELETE',p,expected=403,csrf=False)
other.request('DELETE',p,expected=404)
v.request('PATCH',p,{'status':'CONFIRMED'})
v.request('DELETE',p,expected=409)
v.request('PATCH',p,{'status':'PLANNING'},expected=409)
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
v.request('PATCH',p,{'status':'CONFIRMED'})
v.request('DELETE',p+'/places/'+b,expected=409)
assert len(v.request('GET',p)[1]['places'])==2
# Only own benefit passes can be attached; cancellation leaves the archive intact.
passes=v.request('GET','/me/passes')[1]['items']
benefit=passes[0] if passes else v.request('POST','/me/passes',{'productId':'1'},expected=201)[1]
assert v.request('GET',p)[1]['benefitPassHistory']==[]
with concurrent.futures.ThreadPoolExecutor(2) as pool:
    list(pool.map(lambda client:client.request('PATCH',p,{'issuedPassId':benefit['id']}),[v,v2]))
history=v.request('GET',p)[1]['benefitPassHistory']
assert len(history)==1 and history[0]['issuedPassId']==benefit['id']
assert history[0]['linkedAt'] and history[0]['disconnectedAt'] is None and 'code' not in history[0]
v.request('PATCH',p,{'issuedPassId':'999999999'},expected=404)
assert v.request('GET',p)[1]['benefitPassHistory']==history
other.request('PATCH','/me/journeys/'+other.request('POST','/me/journeys',{'name':'다른 여행'},expected=201)[1]['id'],{'issuedPassId':benefit['id']},expected=404)
if benefit['status']=='ACTIVE':admin.request('PUT','/admin/passes/'+benefit['id']+'/cancellation',{'reason':'여행 연결 검사'})
assert v.request('GET',p)[1]['benefitPass']['status']=='CANCELLED'
v.request('PATCH',p,{'issuedPassId':None})
detached=v.request('GET',p)[1]
assert 'benefitPass' not in detached
assert len(detached['benefitPassHistory'])==1
assert detached['benefitPassHistory'][0]['disconnectedAt']>=history[0]['linkedAt']
assert detached['benefitPassHistory'][0]['status']=='CANCELLED'
v.request('PATCH',p,{'issuedPassId':None})
assert v.request('GET',p)[1]['benefitPassHistory']==detached['benefitPassHistory']
v.request('PATCH',p,{'name':'기록 보존 검사','issuedPassId':benefit['id'],'status':'ARCHIVED'})
history=v.request('GET',p)[1]['benefitPassHistory']
assert len(history)==2 and history[0]['disconnectedAt'] is None
assert history[1]['disconnectedAt'] is not None
# Replacing a pass closes its interval and opens the new one atomically.
x=other.request('POST','/me/passes',{'productId':'1'},expected=201)[1]
admin.request('PUT','/admin/passes/'+x['id']+'/cancellation',{'reason':'연결 교체 검사'})
y=other.request('POST','/me/passes',{'productId':'1'},expected=201)[1]
admin.request('PUT','/admin/passes/'+y['id']+'/cancellation',{'reason':'연결 교체 검사'})
other_journey=other.request('POST','/me/journeys',{'name':'연결 교체 검사'},expected=201)[1]
op='/me/journeys/'+other_journey['id']
other.request('PATCH',op,{'issuedPassId':x['id']})
replaced=other.request('PATCH',op,{'issuedPassId':y['id']})[1]
assert replaced['issuedPassId']==y['id']
history=replaced['benefitPassHistory']
assert [h['issuedPassId'] for h in history]==[y['id'],x['id']]
assert history[0]['disconnectedAt'] is None and history[1]['disconnectedAt']==history[0]['linkedAt']
v.request('GET',op,expected=404)
# Editing retains unavailable saved places and their records; invalid additions roll back.
draft=v.request('POST','/me/journeys',{'name':'스냅샷 수정 검사'},expected=201)[1]
draft_path='/me/journeys/'+draft['id']
v.request('PATCH',draft_path,{'status':'CONFIRMED'},expected=400)
v.request('POST',draft_path+'/places',{'placeId':'2'})
saved=v.request('GET',draft_path)[1]['places'][0]
v.request('PUT',draft_path+'/places/'+saved['id']+'/record',{'visitedOn':'2026-10-01','note':'보존할 기록','rating':3})
admin.request('PATCH','/admin/ontology/places/2',{'active':False})
web_base=BASE.removesuffix('/api/v1')
with v.http.open(web_base+'/journeys/edit?id='+draft['id']) as response:
    text=response.read().decode()
assert '현재 공개되지 않은 장소' in text and 'data-saved-only="true"' in text

def update_draft(places, expected):
    values={'csrf':v.csrf,'id':draft['id'],'name':'수정한 임시 패스','placeId':places}
    req=urllib.request.Request(web_base+'/journeys/update',data=urllib.parse.urlencode(values,doseq=True).encode())
    try: response=v.http.open(req)
    except urllib.error.HTTPError as error: response=error
    assert response.status==expected, response.read().decode()[:300]

update_draft(['2','999999'],404)
assert v.request('GET',draft_path)[1]['name']=='스냅샷 수정 검사'
update_draft(['2'],200)
edited=v.request('GET',draft_path)[1]
assert edited['places'][0]['id']==saved['id'] and edited['places'][0]['note']=='보존할 기록'
assert edited['places'][0]['placeName']==saved['placeName']
admin.request('PATCH','/admin/ontology/places/2',{'active':True})
v.request('DELETE',draft_path,expected=204)
draft=v.request('POST','/me/journeys',{'name':'삭제할 임시 패스'},expected=201)[1]
v.request('DELETE','/me/journeys/'+draft['id'],expected=204)
v.request('GET','/me/journeys/'+draft['id'],expected=404)
print('PASS: journey ownership, CSRF, concurrent deduplication, ordering, records, archive/restore, snapshots, benefit linkage')

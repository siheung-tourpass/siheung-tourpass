"""Additional CRUD, review and concurrent ontology checks after api_check.py."""
from api_check import Client
from concurrent.futures import ThreadPoolExecutor
from urllib.parse import urlencode
import uuid
suffix=uuid.uuid4().hex[:8]
a, a2, public = Client('admin'), Client('admin'), Client()
def revision(): return int(public.request('GET','/recommendations')[1]['modelRevision'])
initial_revision=revision()
annotation={'materialKind':'DEMO','sourceUrl':None,'checkedOn':None,'evidenceNote':'시연 설정'}
product=a.request('POST','/admin/products',{'code':'P_'+suffix,'name':'테스트 상품','description':'시연'},expected=201)[1]
assert product['durationHours']==24 and product['demoOnly']
assert 'modelRevision' not in product and revision()==initial_revision
a.request('PATCH','/admin/products/'+product['id'],{'durationHours':48},expected=400)
place=a.request('POST','/admin/ontology/places',{'code':'PLACE_'+suffix,'name':'테스트 시연','regionId':'1','description':'시연','locationDescription':'가상 위치','annotation':annotation,'sources':[]},expected=201)[1]
assert int(place['modelRevision'])==initial_revision+1 and revision()==initial_revision+1
public.request('GET','/places/'+place['id'],expected=404)
review=a.request('PUT','/admin/ontology/places/'+place['id']+'/review',{'status':'APPROVED'})[1]
assert int(review['modelRevision'])==initial_revision+2
assert a.request('PUT','/admin/ontology/places/'+place['id']+'/review',{'status':'APPROVED'})[1]==review
assert revision()==initial_revision+2 and '_unchanged' not in review
assert public.request('GET','/places/'+place['id'])[1]['reviewStatus']=='APPROVED'
merchant=a.request('POST','/admin/merchants',{'code':'M_'+suffix,'placeId':place['id'],'name':'시연 가맹점','locationDescription':'가상 위치'},expected=201)[1]
benefit=a.request('POST','/admin/benefits',{'code':'B_'+suffix,'productId':product['id'],'merchantId':merchant['id'],'name':'무료','description':'시연','benefitType':'FREE_ONCE','basePriceWon':None,'discountWon':None,'payableWon':0,'reservationNote':'예약 불필요'},expected=201)[1]
a.request('PATCH','/admin/benefits/'+benefit['id'],{'payableWon':100},expected=400)
a.request('PATCH','/admin/benefits/'+benefit['id'],{'productId':'1'},expected=400)
discount={'benefitType':'DISCOUNT','basePriceWon':5000,'discountWon':1000,'payableWon':4000}
a.request('PATCH','/admin/benefits/'+benefit['id'],discount)
for invalid in ({'discountWon':6000},{'payableWon':3999},{'basePriceWon':None},{'discountWon':None}):
    a.request('PATCH','/admin/benefits/'+benefit['id'],invalid,expected=400)
assert a.request('GET','/admin/benefits/'+benefit['id'])[1]['payableWon']==4000
a.request('PATCH','/admin/merchants/'+merchant['id'],{'active':False})
a.request('PATCH','/admin/products/'+product['id'],{'active':False})
a.request('PATCH','/admin/benefits/'+benefit['id'],{'active':False})
for collection, item, filters in (
    ('products', product, {'active':'false'}),
    ('merchants', merchant, {'active':'false'}),
    ('benefits', benefit, {'active':'false','productId':product['id'],'merchantId':merchant['id']}),
    ('ontology/places', place, {'active':'true','reviewStatus':'APPROVED','regionId':'1'}),
):
    items=a.request('GET','/admin/'+collection+'?'+urlencode({'q':item['code'].lower()} | filters))[1]['items']
    assert [row['id'] for row in items]==[item['id']]
real_annotation={'materialKind':'REAL','sourceUrl':None,'checkedOn':None,'evidenceNote':'검토 전'}
real=a.request('POST','/admin/ontology/places',{'code':'REAL_'+suffix,'name':'미검토 자료','regionId':'1','description':'검토 전','locationDescription':'미확인','annotation':real_annotation,'sources':[]},expected=201)[1]
before=revision()
a.request('PUT','/admin/ontology/places/'+real['id']+'/review',{'status':'APPROVED'},expected=409,code='REVIEW_REQUIRED')
assert revision()==before
a.request('PATCH','/admin/ontology/places/'+real['id'],{'annotation':annotation},expected=400)
a.request('PATCH','/admin/ontology/places/'+real['id'],{'sources':[{'sourceUrl':'javascript:alert(1)','checkedOn':'2026-10-03','evidenceNote':'위험'}]},expected=400)
primary={'materialKind':'REAL','sourceUrl':'https://example.com/primary','checkedOn':'2026-10-03','evidenceNote':'테스트 출처'}
a.request('PATCH','/admin/ontology/places/'+real['id'],{'annotation':primary})
a.request('PATCH','/admin/ontology/places/'+real['id'],{'sources':[{'sourceUrl':primary['sourceUrl'],'checkedOn':'2026-10-03','evidenceNote':'중복'}]},expected=400)
a.request('PATCH','/admin/ontology/places/'+real['id'],{'sources':[{'sourceUrl':'https://example.com/extra','checkedOn':'2026-10-03','evidenceNote':'테스트 추가 출처'}]})
a.request('PATCH','/admin/ontology/places/'+real['id'],{'annotation':dict(primary,sourceUrl='https://example.com/extra')},expected=400)
saved=a.request('GET','/admin/ontology/places/'+real['id'])[1]
assert saved['sourceUrl']==primary['sourceUrl'] and len(saved['sources'])==1
a.request('PATCH','/admin/ontology/places/'+real['id'],{'annotation':dict(primary,evidenceNote='근거 수정')})
assert a.request('GET','/admin/ontology/places/'+real['id'])[1]['sources']==saved['sources']
a.request('PATCH','/admin/ontology/places/'+real['id'],{'sources':[]})
assert a.request('GET','/admin/ontology/places/'+real['id'])[1]['sources']==[]
x=a.request('POST','/admin/ontology/themes',{'code':'TX_'+suffix,'name':'테스트 X'},expected=201)[1]['id']
y=a.request('POST','/admin/ontology/themes',{'code':'TY_'+suffix,'name':'테스트 Y'},expected=201)[1]['id']
def edge(args):
    client,child,parent=args
    status, data = client.request('POST','/admin/ontology/relations',{'type':'sub-theme-of','fromId':child,'toId':parent,'annotation':annotation},expected=(201,409))
    if status == 409: assert data['error']['code']=='THEME_CYCLE',data
    return status==201
with ThreadPoolExecutor(2) as pool: results=list(pool.map(edge,[(a,x,y),(a2,y,x)]))
assert sum(results)==1
before=revision()
relation=a.request('POST','/admin/ontology/relations',{'type':'has-theme','fromId':place['id'],'toId':x,'annotation':annotation},expected=201)[1]
assert int(relation['modelRevision'])==before+1
review=a.request('PUT',f'/admin/ontology/relations/has-theme/{place["id"]}/{x}/review',{'status':'APPROVED'})[1]
assert int(review['modelRevision'])==before+2
assert a.request('PUT',f'/admin/ontology/relations/has-theme/{place["id"]}/{x}/review',{'status':'APPROVED'})[1]==review
assert revision()==before+2
a.request('PATCH',f'/admin/ontology/relations/has-theme/{place["id"]}/{x}',{'annotation':annotation})
reset=a.request('GET',f'/admin/ontology/relations/has-theme/{place["id"]}/{x}')[1]
assert reset['reviewStatus']=='DRAFT' and reset['reviewedBy'] is None and reset['reviewedAt'] is None
assert a.request('PATCH','/admin/ontology/places/'+place['id'],{'active':True})[1]['reviewStatus']=='APPROVED'
reset=a.request('PATCH','/admin/ontology/places/'+place['id'],{'name':'검토 초기화 검사'})[1]
assert reset['reviewStatus']=='DRAFT' and reset['reviewedBy'] is None and reset['reviewedAt'] is None
# Remove these test candidates from public results without physically deleting history.
a.request('PATCH','/admin/ontology/places/'+place['id'],{'active':False})
print('PASS: catalog CRUD, prices, immutable fields, review, source validation and concurrent theme cycle prevention')

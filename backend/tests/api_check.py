"""Run on a fresh seeded database: python3 backend/tests/api_check.py [base URL]."""
import concurrent.futures
import http.cookiejar
import json
import sys
import urllib.request
import urllib.error

BASE = sys.argv[1] if len(sys.argv) > 1 else 'http://localhost:8080/siheung-tourpass/api/v1'

class Client:
    def __init__(self, login=None):
        self.jar = http.cookiejar.CookieJar()
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.csrf = self.request('GET', '/session')[1]['csrfToken']
        if login:
            self.csrf = self.request('POST', '/session', {'loginId':login,'password':'DemoPass!2026'})[1]['csrfToken']
    def request(self, method, path, body=None, expected=200, code=None, csrf=True):
        headers = {'Content-Type':'application/json'}
        if csrf: headers['X-CSRF-Token'] = self.csrf if hasattr(self, 'csrf') else ''
        req = urllib.request.Request(BASE+path, data=json.dumps(body).encode() if body is not None else None, method=method, headers=headers)
        try: response = self.http.open(req)
        except urllib.error.HTTPError as e: response = e
        raw = response.read()
        data = json.loads(raw) if raw else None
        assert response.status == expected, (method, path, response.status, data)
        if code: assert data['error']['code'] == code, data
        return response.status, data

def run():
    public, visitor, other, staff, staff2, admin = [Client(name) for name in (None,'visitor','visitor2','staff-a','staff-b','admin')]
    public.request('GET','/me/passes',expected=401,code='AUTH_REQUIRED')
    visitor.request('GET','/admin/products',expected=403,code='FORBIDDEN')
    visitor.request('POST','/me/passes',{'productId':'1'},expected=403,code='CSRF_INVALID',csrf=False)
    visitor.request('POST','/me/passes',{'productId':1},expected=400,code='INVALID_INPUT')
    visitor.request('POST','/me/passes',{'productId':'1','ownerId':'5'},expected=400)
    public.request('POST','/session',{'loginId':'visitor','password':'bad'},expected=401)
    public.request('GET','/products/1/extra',expected=404)
    public.request('PUT','/products/1',{},expected=405)
    assert public.request('GET','/products/1')[1]['benefits'][1]['payableWon'] == 4000
    assert public.request('GET','/recommendations?regionId=3')[1]['items'] == []
    public.request('GET','/recommendations?regionId=999',expected=400)
    def recommendation(client=public): return client.request('GET','/recommendations?themeId=2&companionId=1')[1]
    def places(client=public): return [x['place']['id'] for x in recommendation(client)['items']]
    assert places() == ['1','3','2']
    assert [x['score']['total'] for x in recommendation()['items']] == [3,3,1]
    assert recommendation()['items'][0]['matchedThemes'][0]['path'] == ['THEME_MARINE_EXPERIENCE','THEME_EXPERIENCE']
    assert [x['place']['id'] for x in public.request('GET','/recommendations?regionId=2&themeId=2&companionId=1')[1]['items']] == ['2','4']
    # Separate sessions of the same owner exercise DB serialization, not session locks.
    v2 = Client('visitor')
    def issue(client):
        try: return client.request('POST','/me/passes',{'productId':'1'},expected=201)[1]
        except AssertionError as e:
            assert e.args[0][2:][0] == 409, e
            return None
    with concurrent.futures.ThreadPoolExecutor(2) as pool: issued=list(pool.map(issue,[visitor,v2]))
    assert sum(x is not None for x in issued)==1
    p=next(x for x in issued if x); pid=p['id']; assert p['benefits'][0]['usable'] and len(p['code'])==22
    assert 'code' not in visitor.request('GET','/me/passes')[1]['items'][0]
    other.request('GET','/me/passes/'+pid,expected=404)
    admin.request('PATCH','/admin/products/1',{'name':'변경 상품'})
    assert visitor.request('GET','/me/passes/'+pid)[1]['productName'] == '시연용 1일 패스'
    staff.request('POST','/merchant/pass-lookups',{'merchantId':'2','code':p['code']},expected=403)
    lookup=staff.request('POST','/merchant/pass-lookups',{'merchantId':'1','code':p['code']})[1]
    assert len(lookup['benefits'])==1 and 'code' not in lookup
    a=lookup['benefits'][0]['issuedBenefitId']; b=p['benefits'][1]['id']
    staff.request('POST','/merchant/redemptions',{'confirmationToken':lookup['confirmationToken'],'issuedBenefitId':b},expected=403)
    second=Client('staff-a'); lookup2=second.request('POST','/merchant/pass-lookups',{'merchantId':'1','code':p['code']})[1]
    def use(args):
        client,l=args
        try:return client.request('POST','/merchant/redemptions',{'confirmationToken':l['confirmationToken'],'issuedBenefitId':a},expected=201)[1]
        except AssertionError as e:
            assert e.args[0][2:][0]==409, e
            return None
    with concurrent.futures.ThreadPoolExecutor(2) as pool: redeemed=list(pool.map(use,[(staff,lookup),(second,lookup2)]))
    assert sum(x is not None for x in redeemed)==1
    used=next(x for x in redeemed if x); rid=used['id']; winner=staff if redeemed[0] else second; winning=lookup if redeemed[0] else lookup2
    winner.request('POST','/merchant/redemptions',{'confirmationToken':winning['confirmationToken'],'issuedBenefitId':a},expected=409,code='CONFIRMATION_USED')
    assert len(visitor.request('GET','/me/redemptions')[1]['items'])==1
    assert visitor.request('GET','/me/passes/'+pid)[1]['benefits'][0]['remainingCount']==0
    staff2.request('GET','/merchant/redemptions/'+rid,expected=404)
    other.request('POST','/me/redemptions/'+rid+'/feedback',{'rating':1,'reasons':[],'comment':None},expected=404)
    fpath='/me/redemptions/'+rid+'/feedback'
    f=visitor.request('POST',fpath,{'rating':1,'reasons':['INTEREST','INTEREST'],'comment':'<script>demo</script>'},expected=201)[1]
    assert len(f['reasons'])==1 and len(f['preservedThemes'])==2
    assert places(visitor)==['1','2','3']
    visitor.request('POST',fpath,{'rating':1,'reasons':[],'comment':None},expected=409,code='DUPLICATE_FEEDBACK')
    visitor.request('PUT',fpath,{'rating':5,'reasons':[],'comment':None})
    assert recommendation(visitor)['items'][0]['score']['total']==5
    visitor.request('DELETE',fpath,expected=204)
    assert places(visitor)==['1','3','2']
    annotation={'materialKind':'DEMO','sourceUrl':None,'checkedOn':None,'evidenceNote':'시연 관계'}
    admin.request('POST','/admin/ontology/relations',{'type':'sub-theme-of','fromId':'2','toId':'1','annotation':annotation},expected=409,code='THEME_CYCLE')
    admin.request('POST','/admin/ontology/relations',{'type':'has-theme','fromId':'1','toId':'1','annotation':annotation},expected=409,code='DUPLICATE_RELATION')
    admin.request('POST','/admin/ontology/relations',{'type':'wrong','fromId':'1','toId':'1','annotation':annotation},expected=400,code='INVALID_RELATION_TYPE')
    admin.request('PATCH','/admin/ontology/places/1',{'description':'변경 소개'})
    public.request('GET','/places/1',expected=404)
    assert '1' not in places()
    admin.request('PUT','/admin/ontology/places/1/review',{'status':'APPROVED'})
    assert places()==['1','3','2']
    admin.request('PATCH','/admin/merchants/2',{'active':False})
    lb=staff2.request('POST','/merchant/pass-lookups',{'merchantId':'2','code':p['code']})[1]
    assert lb['benefits'][0]['reason']=='MERCHANT_INACTIVE'
    staff2.request('POST','/merchant/redemptions',{'confirmationToken':lb['confirmationToken'],'issuedBenefitId':b},expected=409,code='MERCHANT_INACTIVE')
    admin.request('PATCH','/admin/merchants/2',{'active':True})
    cancel=admin.request('PUT','/admin/passes/'+pid+'/cancellation',{'reason':'시연 취소'})[1]
    assert admin.request('PUT','/admin/passes/'+pid+'/cancellation',{'reason':'재취소'})[1]==cancel
    lc=staff2.request('POST','/merchant/pass-lookups',{'merchantId':'2','code':p['code']})[1]
    assert lc['benefits'][0]['reason']=='PASS_CANCELLED'
    visitor.request('POST',fpath,{'rating':5,'reasons':['COST'],'comment':'재시작 확인'},expected=201)
    # Leave persisted pass, successful redemption, cancellation and feedback for restart_check.py.
    admin.request('PATCH','/admin/products/1',{'name':'시연용 1일 패스'})
    print('PASS: authentication, CSRF, ownership, concurrent issue/use, snapshots, recommendation, feedback, ontology, cancellation')
if __name__=='__main__': run()

"""Run on a fresh seeded database: python3 backend/tests/api_check.py [base URL]."""
import base64
import re
import concurrent.futures
import json
import sys
import urllib.parse
import urllib.request
from http_support import opener, send

BASE = sys.argv[1] if len(sys.argv) > 1 else 'http://localhost:8080/siheung-tourpass/api/v1'

class Client:
    def __init__(self, login=None):
        self.http = opener()
        self.authorization = ('Basic ' + base64.b64encode((login + ':DemoPass!2026').encode()).decode()) if login else None
        self.login = login
        self.web_csrf = None
    def web_login(self):
        if self.web_csrf is not None: return
        web = BASE.removesuffix('/api/v1')
        text = self.http.open(web + '/login').read().decode()
        self.web_csrf = re.search(r'name="csrf" value="([^"]+)"', text)[1]
        if self.login:
            req = urllib.request.Request(web + '/login', data=urllib.parse.urlencode({'csrf': self.web_csrf, 'loginId': self.login, 'password': 'DemoPass!2026'}).encode())
            self.http.open(req).close()
            text = self.http.open(web + "/profile").read().decode()
            self.web_csrf = re.search(r'name="csrf" value="([^"]+)"', text)[1]
    def request(self, method, path, body=None, expected=None, code=None, csrf=True):
        if expected is None:
            expected = 201 if method == 'POST' and path == '/merchant/pass-lookups' else (200,201) if method == 'PUT' and path.startswith('/me/places/') and path.endswith('/feedback') else 200
        headers = {'Content-Type':'application/json'}
        if csrf: headers['X-CSRF-Token'] = 'api'
        if self.authorization: headers['Authorization'] = self.authorization
        req = urllib.request.Request(BASE+path, data=json.dumps(body).encode() if body is not None else None, method=method, headers=headers)
        status, self.headers, raw = send(self.http, req)
        data = json.loads(raw) if raw else None
        assert status in (expected if isinstance(expected, (tuple, list, set)) else (expected,)), (method, path, status, data)
        if code: assert data['error']['code'] == code, data
        return status, data

    def form(self, path, values, expected=200):
        self.web_login()
        request = urllib.request.Request(BASE.removesuffix('/api/v1') + path,
            data=urllib.parse.urlencode({'csrf': self.web_csrf} | values, doseq=True).encode())
        status, _, raw = send(self.http, request)
        text = raw.decode()
        assert status == expected, (path, status, text[:300])
        return text

def run():
    public, visitor, other, staff, staff2, admin = [Client(name) for name in (None,'visitor','visitor2','staff-a','staff-b','admin')]
    public.request('GET','/me/passes',expected=401,code='AUTH_REQUIRED')
    # Authorization still precedes CSRF and JSON validation in the shared transaction.
    public.request('POST','/me/passes',{'productId':1},csrf=False,expected=401,code='AUTH_REQUIRED')
    visitor.request('POST','/admin/products',{},csrf=False,expected=403,code='FORBIDDEN')
    assert public.request('GET','/products/1/')[1]['id'] == '1'
    visitor.request('GET','/admin/products',expected=403,code='FORBIDDEN')
    visitor.request('POST','/me/passes',{'productId':'1'},expected=403,code='CSRF_INVALID',csrf=False)
    visitor.request('POST','/me/passes',{'productId':1},expected=400,code='INVALID_INPUT')
    visitor.request('POST','/me/passes',{'productId':'1','ownerId':'5'},expected=400)
    invalid = Client('visitor'); invalid.authorization = 'Basic ' + base64.b64encode(b'visitor:bad').decode()
    invalid.request('GET','',expected=401)
    public.request('GET','/products/1/extra',expected=404)
    for path in ('/unknown', '/places/1/extra', '/me/journeys/1/extra'):
        public.request('GET', path, expected=401 if path.startswith('/me/') else 404,
                       code='AUTH_REQUIRED' if path.startswith('/me/') else 'NOT_FOUND')
    public.request('GET', '/products/not-an-id', expected=400, code='INVALID_INPUT')
    visitor.request('GET', '/admin/ontology/places/1/review', expected=403, code='FORBIDDEN')
    visitor.request('DELETE', '/me/journeys/1', {}, csrf=False, expected=403, code='CSRF_INVALID')

    assert public.request('GET','/products/1')[1]['benefits'][1]['payableWon'] == 4000
    assert public.request('GET','/recommendations?regionId=3')[1]['items'] == []
    public.request('GET','/recommendations?regionId=999',expected=400)
    def recommendation(client=public): return client.request('GET','/recommendations?themeId=2&companionId=1')[1]
    def places(client=public): return [x['place']['id'] for x in recommendation(client)['items']]
    def availability(client):
        return {x['place']['id']:x['myPassAvailability'] for x in recommendation(client)['items']}
    assert all(value is None for value in availability(public).values())
    assert all(value == [] for value in availability(visitor).values())
    assert places() == ['1','3','2']
    assert [x['score']['total'] for x in recommendation()['items']] == [3,3,1]
    assert recommendation()['items'][0]['matchedThemes'][0]['path'] == ['THEME_MARINE_EXPERIENCE','THEME_EXPERIENCE']
    assert [x['place']['id'] for x in public.request('GET','/recommendations?regionId=2&themeId=2&companionId=1')[1]['items']] == ['2','4']
    # Separate sessions of the same owner exercise DB serialization, not session locks.
    v2 = Client('visitor')
    def issue(client):
        status, data = client.request('POST','/me/passes',{'productId':'1'},expected=(201,409))
        if status == 409: assert data['error']['code'] == 'ACTIVE_PASS_EXISTS', data
        return data if status == 201 else None
    with concurrent.futures.ThreadPoolExecutor(2) as pool: issued=list(pool.map(issue,[visitor,v2]))
    assert sum(x is not None for x in issued)==1
    p=next(x for x in issued if x); pid=p['id']; assert p['benefits'][0]['usable'] and len(p['code'])==22
    available=availability(visitor)
    assert available['3'] == []
    for benefit in p['benefits']:
        entries=available[benefit['placeId']]
        assert len(entries)==1
        assert entries[0]['passId']==pid and entries[0]['issuedBenefitId']==benefit['id']
        assert entries[0]['usable'] and entries[0]['reason']=='AVAILABLE' and entries[0]['checkedAt']
    assert all(value == [] for value in availability(other).values())
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
        status, data = client.request('POST','/merchant/redemptions',{'confirmationToken':l['confirmationToken'],'issuedBenefitId':a},expected=(201,409))
        if status == 409: assert data['error']['code'] == 'BENEFIT_ALREADY_USED', data
        return data if status == 201 else None
    with concurrent.futures.ThreadPoolExecutor(2) as pool: redeemed=list(pool.map(use,[(staff,lookup),(second,lookup2)]))
    assert sum(x is not None for x in redeemed)==1
    used=next(x for x in redeemed if x); rid=used['id']; winner=staff if redeemed[0] else second; winning=lookup if redeemed[0] else lookup2
    winner.request('POST','/merchant/redemptions',{'confirmationToken':winning['confirmationToken'],'issuedBenefitId':a},expected=409,code='CONFIRMATION_USED')
    assert len(visitor.request('GET','/me/redemptions')[1]['items'])==1
    assert visitor.request('GET','/me/passes/'+pid)[1]['benefits'][0]['remainingCount']==0
    assert availability(visitor)['1'][0]['reason']=='BENEFIT_ALREADY_USED'
    assert availability(visitor)['1'][0]['usable'] is False
    staff2.request('GET','/merchant/redemptions/'+rid,expected=404)
    other.request('POST','/me/redemptions/'+rid+'/feedback',{'rating':1,'reasons':[],'comment':None},expected=404)
    fpath='/me/redemptions/'+rid+'/feedback'
    f=visitor.request('POST',fpath,{'rating':1,'reasons':['INTEREST','INTEREST'],'comment':'<script>demo</script>'},expected=201)[1]
    assert len(f['reasons'])==1 and len(f['preservedThemes'])==2
    assert places(visitor)==['1','2','3']
    visitor.request('POST',fpath,{'rating':1,'reasons':[],'comment':None},expected=409,code='DUPLICATE_FEEDBACK')
    visitor.request('PUT',fpath,{'rating':5,'reasons':[],'comment':None})
    assert recommendation(visitor)['items'][0]['score']['total']==5
    # Reason changes take effect immediately; place and multiple themes count once.
    def personal(): return {x['place']['id']:x for x in recommendation(visitor)['items']}
    visitor.request('PUT',fpath,{'rating':5,'reasons':['COST','GUIDANCE','COMPANION_FIT'],'comment':'비공개 의견'})
    scores=personal()
    assert scores['1']['score']['personal']==2 and scores['3']['score']['personal']==0
    assert '같은 장소' in scores['1']['explanation'] and '비용' in scores['1']['explanation']
    assert '비공개 의견' not in str(scores)
    assert all(x['score']['personal']==0 for x in recommendation(other)['items'])
    visitor.request('PUT',fpath,{'rating':5,'reasons':['INTEREST','COST'],'comment':None})
    scores=personal()
    for place in ('1','3'):
        assert scores[place]['score']['personal']==2 and scores[place]['score']['feedbackCount']==1
    assert '공통 테마' in scores['3']['explanation'] and '흥미' in scores['3']['explanation']
    visitor.request('DELETE',fpath,expected=204)
    assert places(visitor)==['1','3','2']
    # Feedback still applies to its place when no theme was available to preserve.
    admin.request('PATCH','/admin/ontology/relations/has-theme/1/1',{'active':False})
    f=visitor.request('POST',fpath,{'rating':5,'reasons':['COST'],'comment':None},expected=201)[1]
    assert f['preservedThemes']==[]
    scores=personal()
    assert scores['1']['score']['personal']==2 and scores['1']['score']['feedbackCount']==1
    assert scores['3']['score']['personal']==0
    visitor.request('DELETE',fpath,expected=204)
    admin.request('PATCH','/admin/ontology/relations/has-theme/1/1',{'active':True})
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
    assert '2' not in availability(visitor)
    staff2.request('POST','/merchant/redemptions',{'confirmationToken':lb['confirmationToken'],'issuedBenefitId':b},expected=409,code='MERCHANT_INACTIVE')
    admin.request('PATCH','/admin/merchants/2',{'active':True})
    cancel=admin.request('PUT','/admin/passes/'+pid+'/cancellation',{'reason':'시연 취소'})[1]
    assert admin.request('PUT','/admin/passes/'+pid+'/cancellation',{'reason':'재취소'})[1]==cancel
    lc=staff2.request('POST','/merchant/pass-lookups',{'merchantId':'2','code':p['code']})[1]
    assert lc['benefits'][0]['reason']=='PASS_CANCELLED'
    assert all(entry['reason']=='PASS_CANCELLED' and not entry['usable']
               for entries in availability(visitor).values() for entry in entries)
    visitor.request('POST',fpath,{'rating':5,'reasons':['COST'],'comment':'재시작 확인'},expected=201)
    # Leave persisted pass, successful redemption, cancellation and feedback for restart_check.py.
    admin.request('PATCH','/admin/products/1',{'name':'시연용 1일 패스'})
    print('PASS: authentication, CSRF, ownership, concurrent issue/use, snapshots, recommendation, feedback, ontology, cancellation')
if __name__=='__main__': run()

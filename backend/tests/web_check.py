"""Exercise actual JSP forms on a fresh seeded test DB; stdlib only."""
import html
import json
from datetime import datetime
from zoneinfo import ZoneInfo
from html.parser import HTMLParser
import http.cookiejar
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else 'http://localhost:8080/siheung-tourpass'

class Forms(HTMLParser):
    def __init__(self, text):
        super().__init__()
        self.forms = []
        self.current = None
        self.feed(text)
    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == 'form':
            self.current = {'action': a['action'], 'fields': {}}
            self.forms.append(self.current)
        if tag == 'input' and self.current and a.get('type') == 'hidden':
            self.current['fields'][a['name']] = a.get('value', '')
    def handle_endtag(self, tag):
        if tag == 'form': self.current = None

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args): return None

class Client:
    def __init__(self, login=None):
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()), NoRedirect())
        if login:
            self.get('/login')
            self.submit('/login', {'loginId':login, 'password':'DemoPass!2026'})
            self.get(self.location)
    def request(self, path, body=None, expected=200):
        data = urllib.parse.urlencode(body, doseq=True).encode() if body is not None else None
        try: res = self.http.open(urllib.request.Request(BASE+path, data=data))
        except urllib.error.HTTPError as e: res = e
        self.text = res.read().decode('utf-8')
        assert res.status == expected, (path, res.status, self.text[:300])
        self.location = res.headers.get('Location', '').removeprefix(urllib.parse.urlsplit(BASE).path)
        self.forms = Forms(self.text).forms
        if not path.startswith('/WEB-INF/'):
            assert res.headers.get('Cache-Control') == 'no-store'
        return self.text
    def get(self, path, expected=200): return self.request(path, expected=expected)
    def submit(self, suffix, values=None, expected=303):
        form = next(f for f in self.forms if f['action'].endswith(suffix))
        return self.request(suffix, form['fields'] | (values or {}), expected)

public, visitor, other, staff, admin = [Client(name) for name in (None,'visitor','visitor2','staff-a','admin')]
assert '나만의 시흥 여행을 담다' in public.get('/')
assert '가맹점과 패스 혜택' in public.get('/merchants')
assert '4000원' in public.get('/products/detail?id=1')
public.get('/passes', 303); assert public.location == '/login'
visitor.get('/admin', 403)
public.get('/WEB-INF/views/page.jsp', 404)
public.get('/does-not-exist', 404)
public.request('/login', {'loginId':'visitor','password':'DemoPass!2026'}, 403)
assert '조건에 맞는 장소가 없습니다' in public.get('/recommendations?regionId=3')
text = public.get('/recommendations?themeId=2&companionId=1')
assert 'THEME_MARINE_EXPERIENCE' in text and '관광 추천' in text
assert '시연 자료' in public.get('/places/detail?id=1')
public.get('/merchants/detail?id=1')
visitor.get('/products/detail?id=1'); visitor.submit('/passes/issue')
pid = urllib.parse.parse_qs(urllib.parse.urlsplit(visitor.location).query)['id'][0]
text = visitor.get(visitor.location)
assert '사용 가능' in text and 'ê' not in text
with visitor.http.open(BASE+'/api/v1/me/passes/'+pid) as response:
    issued = json.load(response)
for key in ('startsAt','expiresAt'):
    kst = datetime.fromisoformat(issued[key]).astimezone(ZoneInfo('Asia/Seoul')).strftime('%Y-%m-%d %H:%M:%S 한국 시간')
    assert kst in text, (key, kst)
code = html.unescape(re.search(r'class="code">(.*?)</p>', text).group(1))
other.get('/passes/detail?id='+pid, 404)
visitor.get('/products/detail?id=1'); visitor.submit('/passes/issue', expected=409)
staff.get('/merchant/redeem'); text=staff.submit('/merchant/redeem/lookup', {'merchantId':'1','code':code}, 200)
assert code not in text
confirm = next(f['fields'] for f in staff.forms if f['action'].endswith('/merchant/redeem/confirm'))
staff.submit('/merchant/redeem/confirm')
assert '사용 성공' in staff.get(staff.location)
staff.request('/merchant/redeem/confirm', confirm, 409)
text=visitor.get('/feedback/history')
rid=re.search(r'/feedback/edit\?id=([0-9]+)',text).group(1)
visitor.get('/feedback/edit?id='+rid)
visitor.submit('/feedback/create', {'rating':'1','reasons':['INTEREST','COST'],'comment':'<script>alert(1)</script>'})
assert '본인 평가 1건' in visitor.get('/recommendations?themeId=2&companionId=1')
assert '&lt;script&gt;' in visitor.get('/feedback/edit?id='+rid)
other.get('/feedback/edit?id='+rid,404)
visitor.submit('/feedback/update', {'rating':'5','comment':'수정 의견'})
visitor.get('/feedback/edit?id='+rid); visitor.submit('/feedback/delete')
assert '본인 평가 1건' not in visitor.get('/recommendations?themeId=2&companionId=1')
# Personal travel forms and recommendation-to-journey flow.
public.get('/journeys',303)
staff.get('/journeys',403)
visitor.get('/journeys'); visitor.submit('/journeys/create', {'name':'<script>나의 여행</script>','travelDate':'2026-10-01'})
journey_url=visitor.location
jid=urllib.parse.parse_qs(urllib.parse.urlsplit(journey_url).query)['id'][0]
assert '&lt;script&gt;나의 여행' in visitor.get(journey_url)
other.get(journey_url,404)
visitor.get('/recommendations'); visitor.submit('/journeys/add', {'id':jid})
text=visitor.get(journey_url); assert '본인 기록이며 방문 인증이 아닙니다' in text
visitor.get('/places/detail?id=2'); visitor.submit('/journeys/add', {'id':jid})
visitor.get(journey_url); visitor.submit('/journeys/move', {'direction':'DOWN'})
visitor.get(journey_url); visitor.submit('/journeys/record', {'visitedOn':'2026-10-01','note':'<script>여행 메모</script>','rating':'4'})
assert '&lt;script&gt;여행 메모' in visitor.get(journey_url)
visitor.submit('/journeys/save', {'name':'나의 여행','travelDate':'2026-10-01','status':'ARCHIVED','issuedPassId':pid})
assert '보관된 여행입니다' in visitor.get(journey_url)
assert '나의 여행' in visitor.get('/journeys?status=ARCHIVED')
visitor.get(journey_url); visitor.submit('/journeys/save', {'name':'나의 여행','travelDate':'','status':'PLANNING','issuedPassId':''})
visitor.get(journey_url); visitor.submit('/journeys/remove')
assert '연결된 이용권이 없습니다' in visitor.get(journey_url)
# All admin form variants render and retain their service contracts.
for collection in ('products','merchants','benefits'):
    admin.get('/admin/catalog?collection='+collection)
    admin.get('/admin/catalog?collection='+collection+'&id=1')
for collection in ('places','regions','themes','companion-types'):
    admin.get('/admin/ontology?collection='+collection)
    admin.get('/admin/ontology?collection='+collection+'&id=1')
for relation in ('has-theme','suitable-for','sub-theme-of'):
    admin.get('/admin/relations?type='+relation)
admin.get('/admin/catalog?collection=products&id=1')
admin.submit('/admin/catalog/save', {'name':'<script>상품</script>','description':'시연 상품','active':'true'})
assert '&lt;script&gt;' in public.get('/products/detail?id=1')
def revision():
    with public.http.open(BASE+'/api/v1/recommendations') as response:
        return int(json.load(response)['modelRevision'])

# Create, revise and deactivate a concept through forms.
before=revision()
admin.get('/admin/ontology?collection=themes')
admin.submit('/admin/ontology/save', {'code':'WEB_THEME','name':'화면 검사 테마'})
assert revision()==before+1
theme_id=urllib.parse.parse_qs(urllib.parse.urlsplit(admin.location).query)['id'][0]
admin.get(admin.location); admin.submit('/admin/ontology/save', {'name':'화면 검사 테마 수정','active':'true'})
assert revision()==before+2
admin.get(admin.location); admin.submit('/admin/ontology/deactivate')
assert revision()==before+3
# Modify annotation, approve the DRAFT place and verify XSS escaping.
admin.get('/admin/ontology?collection=places&id=1')
admin.submit('/admin/ontology/save', {'name':'A 가상 체험','regionId':'1','description':'<script>소개</script>', 'locationDescription':'거북섬 가상 위치','active':'true','materialKind':'DEMO','sourceUrl':'','checkedOn':'','evidenceNote':'시연 근거','extraSourceUrl':'','extraCheckedOn':'','extraEvidenceNote':''})
public.get('/places/detail?id=1',404)
admin.get(admin.location); admin.submit('/admin/ontology/review', {'status':'APPROVED'})
assert '&lt;script&gt;' in public.get('/places/detail?id=1')
before=revision()
admin.get(admin.location); admin.submit('/admin/ontology/review', {'status':'APPROVED'})
assert revision()==before
admin.get('/admin/relations?type=has-theme&fromId=1&toId=1')
admin.submit('/admin/relations/save', {'fromId':'1','toId':'1','materialKind':'DEMO','sourceUrl':'','checkedOn':'','evidenceNote':'시연 관계 수정'})
admin.get(admin.location); admin.submit('/admin/relations/review', {'status':'APPROVED'})
assert revision()==before+2
before=revision()
# Reject a cycle via a form, not only via the API.
admin.get('/admin/relations?type=sub-theme-of')
admin.submit('/admin/relations/save', {'fromId':'2','toId':'1','materialKind':'DEMO','sourceUrl':'','checkedOn':'','evidenceNote':'시연 순환'},409)
assert revision()==before
admin.get('/admin/passes/detail?id='+pid); admin.submit('/admin/passes/cancel', {'reason':'화면 검사 취소'})
assert '취소된 패스' in visitor.get('/passes/detail?id='+pid)
visitor.get('/feedback/edit?id='+rid); visitor.submit('/feedback/create', {'rating':'4','comment':'취소 후 평가'})
# Clean shared seed changes for API checks; historical screen data remains.
admin.get('/admin/catalog?collection=products&id=1'); admin.submit('/admin/catalog/save', {'name':'시연용 1일 패스','description':'시연용·실제 사용 불가. 발급부터 24시간','active':'true'})
visitor.get('/passes'); visitor.submit('/logout')
visitor.get('/passes',303)
public.get('/login'); public.submit('/login', {'loginId':'visitor','password':'wrong'},401)
print('PASS: JSP forms, UTF-8, KST, ownership, roles, CSRF, PRG, code confirmation, feedback XSS, admin CRUD/review/cycle/cancel')

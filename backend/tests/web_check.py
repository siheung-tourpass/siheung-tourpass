"""Exercise actual JSP forms on a fresh seeded test DB; stdlib only."""
import base64
import html
import json
from datetime import datetime
from zoneinfo import ZoneInfo
from html.parser import HTMLParser
import re
import sys
from http_support import opener, send
import urllib.parse
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else 'http://localhost:8080/siheung-tourpass'

class Forms(HTMLParser):
    def __init__(self, text):
        super().__init__()
        self.forms = []
        self.current = None
        self.template_depth = 0
        self.feed(text)
    def handle_starttag(self, tag, attrs):
        if tag == 'template': self.template_depth += 1
        if self.template_depth: return
        a = dict(attrs)
        if tag == 'form':
            self.current = {'action': a['action'], 'fields': {}}
            self.forms.append(self.current)
        if tag == 'input' and self.current and a.get('type') == 'hidden':
            self.current['fields'][a['name']] = a.get('value', '')
    def handle_endtag(self, tag):
        if tag == 'template': self.template_depth -= 1; return
        if self.template_depth: return
        if tag == 'form': self.current = None

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args): return None

class Client:
    def __init__(self, login=None):
        self.login = login
        self.http = opener(NoRedirect())
        if login:
            self.get('/login')
            self.submit('/login', {'loginId':login, 'password':'DemoPass!2026'})
            assert self.location == ('/merchant/redeem' if login.startswith('staff') else '/')
            self.get(self.location)
    def api(self, path):
        headers = {}
        if self.login: headers['Authorization'] = 'Basic ' + base64.b64encode((self.login+':DemoPass!2026').encode()).decode()
        return self.http.open(urllib.request.Request(BASE+path, headers=headers))
    def request(self, path, body=None, expected=200):
        data = urllib.parse.urlencode(body, doseq=True).encode() if body is not None else None
        status, headers, raw = send(self.http, urllib.request.Request(BASE+path, data=data))
        self.text = raw.decode('utf-8')
        assert status == expected, (path, status, self.text[:300])
        self.location = headers.get('Location', '').removeprefix(urllib.parse.urlsplit(BASE).path)
        self.forms = Forms(self.text).forms
        if not path.startswith('/WEB-INF/'):
            assert headers.get('Cache-Control') == 'no-store'
        return self.text
    def get(self, path, expected=200): return self.request(path, expected=expected)
    def submit(self, suffix, values=None, expected=303):
        form = next(f for f in self.forms if f['action'].endswith(suffix))
        return self.request(suffix, form['fields'] | (values or {}), expected)

public, visitor, other, staff, admin = [Client(name) for name in (None,'visitor','visitor2','staff-a','admin')]
# Tourism feedback forms use PRG, escape private opinions and expose aggregate statistics only.
assert '아직 피드백이 없습니다' in admin.get('/admin/place-feedback')
assert '/login' in public.get('/cards/detail?key=place:1')
text=visitor.get('/cards/detail?key=place:1')
visitor.submit('/feedback/places/save', {'rating':'5','comment':'<script>private_place_opinion</script>'})
assert visitor.location == '/cards/detail?key=place:1&feedback=saved#place-feedback'
assert '&lt;script&gt;private_place_opinion&lt;/script&gt;' in visitor.get('/places/detail?id=1')
assert 'private_place_opinion' not in other.get('/cards/detail?key=place:1')
assert 'private_place_opinion' not in admin.get('/admin/place-feedback')
assert '5.0 / 5점' in admin.text
visitor.get('/places/detail?id=1'); visitor.submit('/feedback/places/save', {'rating':'2'})
assert '2.0 / 5점' in admin.get('/admin/place-feedback')
visitor.get('/places/detail?id=1'); visitor.submit('/feedback/places/delete')
assert '아직 피드백이 없습니다' in admin.get('/admin/place-feedback')
visitor.get('/admin/place-feedback', 403)
staff.get('/admin/place-feedback', 403)
# Entry points and role redirects are HTTP/view contracts.
text=public.get('/')
assert '/login' in text and '/explore' in text
assert 'aria-label="주 메뉴"' not in text
assert 'aria-label="주 메뉴"' not in public.get('/login')
for client, destination in ((visitor, '/'), (admin, '/'), (staff, '/merchant/redeem')):
    client.get('/login', 303)
    assert client.location == destination
for client in (visitor, staff):
    assert '/assets/places/' in client.get('/')
admin.get('/')
public.get('/missing',404)
public.request('/missing',{},403)
visitor.get('/missing',404)
visitor.request('/missing',{},403)
text = public.get('/explore')
assert all('data-card-kind="'+kind+'"' in text for kind in ('place', 'event', 'dining'))
assert 'id="save-journey"' not in text
assert '4000원' in public.get('/products/detail?id=1')
public.get('/passes', 303); assert public.location == '/login'
visitor.get('/admin', 403)
public.get('/WEB-INF/views/page.jsp', 404)
public.get('/does-not-exist', 404)
public.request('/login', {'loginId':'visitor','password':'DemoPass!2026'}, 403)
assert '조건에 맞는 장소가 없습니다' in public.get('/recommendations?regionId=3')
text = public.get('/recommendations?themeId=2&companionId=1')
assert 'THEME_MARINE_EXPERIENCE' in text and '관광 추천' in text
text = public.get('/recommendations?regionId=1&themeId=1&themeId=2&companionId=1')
assert re.findall(r'name="themeId" value="([0-9]+)" checked', text) == ['1', '2']
assert all(re.search(r'<select name="' + field + r'">.*?<option value="1" selected', text)
           for field in ('regionId', 'companionId'))
text = public.get('/recommendations?themeId=&themeId=2')
assert re.findall(r'name="themeId" value="([0-9]+)" checked', text) == ['2']
assert '시연 자료' in public.get('/places/detail?id=1')
public.get('/merchants/detail?id=1')
visitor.get('/products/detail?id=1'); visitor.submit('/passes/issue')
pid = urllib.parse.parse_qs(urllib.parse.urlsplit(visitor.location).query)['id'][0]
text = visitor.get(visitor.location)
assert '사용 가능' in text and 'ê' not in text
with visitor.api('/api/v1/me/passes/'+pid) as response:
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
text=visitor.get('/feedback/edit?id='+rid)
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
text=visitor.get('/journeys')
visitor.get('/journeys/new?template=oido',409)
visitor.get('/journeys/new?template=unknown',400)
public.get('/journeys/new',303)
staff.get('/journeys/new',403)
text=visitor.get('/journeys/new')
visitor.submit('/journeys/create', {'name':'장소 없음','intent':'confirm'},400)
# Five-stage navigation, profile-only account controls, editable/deletable drafts.
text=visitor.get('/profile')
nav=text.split('aria-label="주 메뉴"')[1]
assert all('<span>'+label+'</span>' in nav for label in ('홈','여행 만들기','내 패스','여행 기록','프로필'))
visitor.get('/journeys/new');visitor.submit('/journeys/create', {'name':'임시 패스','placeId':['2','1']})
draft_url=visitor.location
assert draft_url.startswith('/journeys/edit?id=')
visitor.get('/journeys/photocards?id='+urllib.parse.parse_qs(urllib.parse.urlsplit(draft_url).query)['id'][0],409)
draft_id=urllib.parse.parse_qs(urllib.parse.urlsplit(draft_url).query)['id'][0]
text=visitor.get('/')
assert '임시 패스' in text
text=other.get('/')
assert '임시 패스' not in text
text=visitor.get(draft_url)
assert re.search(r'value="place:2"[^>]* checked', text)
other.get(draft_url,404)
other.get('/profile');other.submit('/logout')
other=Client('visitor2');other.get('/profile')
csrf=next(f['fields']['csrf'] for f in other.forms if f['action'].endswith('/logout'))
other.request('/journeys/delete', {'csrf':csrf,'id':draft_id},404)
visitor.get(draft_url);visitor.submit('/journeys/update', {'name':'수정 임시 패스','placeId':['1','2']})
visitor.get(visitor.location)
with visitor.api('/api/v1/me/journeys/'+draft_id) as response:
    edited=json.load(response)
assert edited['name']=='수정 임시 패스' and [p['placeId'] for p in edited['places']]==['1','2']
visitor.submit('/journeys/delete');visitor.get(draft_url,404)
assert '수정 임시 패스' not in visitor.get('/')
visitor.get('/journeys/new')
# The whole create rolls back if any place is unavailable.
with visitor.api('/api/v1/me/journeys') as response:
    before_journeys = json.load(response)
visitor.get('/journeys/new')
visitor.submit('/journeys/create', {'name':'실패 여행','placeId':['1','999999']},404)
with visitor.api('/api/v1/me/journeys') as response:
    assert json.load(response) == before_journeys
visitor.get('/journeys/new')
visitor.submit('/journeys/create', {'name':'<script>나의 여행</script>','travelDate':'2026-10-01','placeId':['2','1','2'],'intent':'confirm'})
journey_url=visitor.location
jid=urllib.parse.parse_qs(urllib.parse.urlsplit(journey_url).query)['id'][0]
assert '&lt;script&gt;나의 여행' in visitor.get(journey_url)
with visitor.api('/api/v1/me/journeys/'+jid) as response:
    created=json.load(response)
assert [p['placeId'] for p in created['places']] == ['2','1']
photo_url='/journeys/photocards?id='+jid
assert '/journeys/photocards' not in visitor.get(journey_url)
visitor.get(photo_url,409)
other.get(photo_url,404)
public.get(photo_url,303)
staff.get(photo_url,403)
admin.get(photo_url,403)
other.get(journey_url,404)
visitor.get(journey_url)
csrf=next(f['fields']['csrf'] for f in visitor.forms if f['action'].endswith('/journeys/save'))
visitor.request('/journeys/delete', {'csrf':csrf,'id':jid},409)
visitor.get(journey_url)
text=visitor.get(journey_url); assert '본인 기록이며 방문 인증이 아닙니다' in text
visitor.get(journey_url); visitor.submit('/journeys/record', {'visitedOn':'2026-10-01','note':'<script>여행 메모</script>','rating':'4'})
assert '#record-' in visitor.location
assert '&lt;script&gt;여행 메모' in visitor.get(journey_url)
visitor.submit('/journeys/save', {'name':'나의 여행','travelDate':'2026-10-01','status':'ARCHIVED','issuedPassId':pid})
assert visitor.location.endswith('/journeys?status=ARCHIVED')
assert '/journeys/photocards' in visitor.get(journey_url)
text=visitor.get(photo_url)
assert 'journey-photocard' in text and '&lt;script&gt;여행 메모' in text
other.get(photo_url,404)
text=visitor.get(journey_url)
assert '보관된 여행입니다' in text and '이용권 연결 이력' in text and '현재 연결 중' in text
with visitor.api('/api/v1/me/journeys/'+jid) as response:
    history=json.load(response)['benefitPassHistory']
linked_kst=datetime.fromisoformat(history[0]['linkedAt']).astimezone(ZoneInfo('Asia/Seoul')).strftime('%Y-%m-%d %H:%M:%S 한국 시간')
assert linked_kst in text
assert '나의 여행' in visitor.get('/journeys?status=ARCHIVED')
visitor.get(journey_url); visitor.submit('/journeys/save', {'name':'나의 여행','travelDate':'','status':'CONFIRMED','issuedPassId':''})
assert visitor.location.endswith('/journeys?status=CONFIRMED')
visitor.get(photo_url,409)
assert '/journeys/photocards' not in visitor.get(journey_url)
text=visitor.get(journey_url)
assert '연결된 이용권이 없습니다' in text and '해제:' in text
assert '현재 연결 중' not in text and linked_kst in text
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
    with public.api('/api/v1/recommendations') as response:
        return int(json.load(response)['modelRevision'])

# Create, revise and deactivate a concept through forms.
before=revision()
admin.get('/admin/ontology?collection=themes&create=1')
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
visitor.get('/profile'); visitor.submit('/logout')
assert visitor.location == '/'
assert 'page-landing' in visitor.get('/')
visitor.get('/passes',303)
public.get('/login'); public.submit('/login', {'loginId':'visitor','password':'wrong'},401)
assert 'page-login' in public.text and 'role="alert"' in public.text
assert any(f['action'].endswith('/login') for f in public.forms)
print('PASS: JSP forms, UTF-8, KST, ownership, roles, CSRF, PRG, code confirmation, feedback XSS, admin CRUD/review/cycle/cancel')

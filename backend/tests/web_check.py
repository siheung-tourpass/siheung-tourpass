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
public.get('/missing',404)
public.request('/missing',{},403)
visitor.get('/missing',404)
visitor.request('/missing',{},403)
text = public.get('/events?date=2026-10-17')
assert 'MOIM International' in text and '2026 시흥 책모락' in text
assert '세계커피콩축제' not in text and '검토 대기' in text
assert '/assets/events/gg-2026-siheung-3.png' in text
assert '행사 이미지 준비 중' in text and '공공누리 제3유형' in text
assert '자료가 없습니다' in public.get('/events?date=2026-12-01')
public.get('/events?date=2026-02-30', 400)
public.get('/events?date=2026-10-17&date=2026-10-18', 400)
assert '가맹점과 패스 혜택' in public.get('/merchants')
assert '여행 카드 둘러보기' in public.get('/login')
text = public.get('/explore')
assert all('data-card-kind="'+kind+'"' in text for kind in ('place', 'event', 'merchant'))
assert 'id="save-journey"' not in text
nav = text.split('aria-label="주 메뉴"')[1]
assert '/recommendations' not in nav and '/merchants' not in nav
archive = visitor.get('/journeys?status=ARCHIVED')
assert '사용 완료한 패스가 없습니다' in archive and '여행 패스 만들기' in archive
assert '아직 여행 패스가 없습니다' not in archive
assert '4000원' in public.get('/products/detail?id=1')
public.get('/passes', 303); assert public.location == '/login'
visitor.get('/admin', 403)
public.get('/WEB-INF/views/page.jsp', 404)
public.get('/does-not-exist', 404)
public.request('/login', {'loginId':'visitor','password':'DemoPass!2026'}, 403)
assert '조건에 맞는 장소가 없습니다' in public.get('/recommendations?regionId=3')
text = public.get('/recommendations?themeId=2&companionId=1')
assert 'THEME_MARINE_EXPERIENCE' in text and '관광 추천' in text
assert '조건 초기화' in text and '<summary>추천 점수·혜택 확인</summary>' in text
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
text=visitor.get('/journeys')
assert '/journeys/new' in text and '/journeys/create' not in text
assert '템플릿으로 시작하기' in text and '장소 자료 준비 중' in text
visitor.get('/journeys/new?template=oido',409)
visitor.get('/journeys/new?template=unknown',400)
public.get('/journeys/new',303)
staff.get('/journeys/new',403)
text=visitor.get('/journeys/new')
assert 'id="card-date"' in text and 'event-card' in text
assert 'id="deck-stage"' in text and 'id="deck-summary"' in text
assert '좌우 스크롤로 카드 탐색' in text and 'id="deck-previous"' in text and 'id="deck-next"' in text
assert 'id="card-search"' in text and 'id="shuffle-cards"' in text
assert 'data-card-kind="merchant"' in text and '혜택·이용 조건' in text
assert '여행 덱 만들기' in text and 'data-place-id="1"' in text
assert '내 여행 덱' in text and 'journey-builder.js' in text
visitor.submit('/journeys/create', {'name':'장소 없음','intent':'confirm'},400)
# Five-stage navigation, profile-only account controls, editable/deletable drafts.
text=visitor.get('/profile')
nav=text.split('aria-label="주 메뉴"')[1]
assert all('<span>'+label+'</span>' in nav for label in ('홈','여행 만들기','내 패스','여행 기록','프로필'))
assert nav.count('<svg')==5 and '로그아웃' not in text.split('</header>')[0]
assert '화면 설정' in text and '로그아웃' in text
visitor.get('/journeys/new');visitor.submit('/journeys/create', {'name':'임시 패스','placeId':['2','1']})
draft_url=visitor.location
assert draft_url.startswith('/journeys/edit?id=')
draft_id=urllib.parse.parse_qs(urllib.parse.urlsplit(draft_url).query)['id'][0]
text=visitor.get(draft_url)
assert '임시 패스' in text and 'value="place:2" aria-label="B 가상 음료 선택" checked' in text
assert '제작 확정' in text and '이 패스 삭제' in text
other.get(draft_url,404)
other.get('/profile');other.submit('/logout')
other=Client('visitor2');other.get('/profile')
csrf=next(f['fields']['csrf'] for f in other.forms if f['action'].endswith('/logout'))
other.request('/journeys/delete', {'csrf':csrf,'id':draft_id},404)
visitor.get(draft_url);visitor.submit('/journeys/update', {'name':'수정 임시 패스','placeId':['1','2']})
visitor.get(visitor.location)
with visitor.http.open(BASE+'/api/v1/me/journeys/'+draft_id) as response:
    edited=json.load(response)
assert edited['name']=='수정 임시 패스' and [p['placeId'] for p in edited['places']]==['1','2']
visitor.submit('/journeys/delete');visitor.get(draft_url,404)
visitor.get('/journeys/new')
# The whole create rolls back if any place is unavailable.
with visitor.http.open(BASE+'/api/v1/me/journeys') as response:
    before_journeys = json.load(response)
visitor.get('/journeys/new')
visitor.submit('/journeys/create', {'name':'실패 여행','placeId':['1','999999']},404)
with visitor.http.open(BASE+'/api/v1/me/journeys') as response:
    assert json.load(response) == before_journeys
visitor.get('/journeys/new')
visitor.submit('/journeys/create', {'name':'<script>나의 여행</script>','travelDate':'2026-10-01','placeId':['2','1','2'],'intent':'confirm'})
journey_url=visitor.location
jid=urllib.parse.parse_qs(urllib.parse.urlsplit(journey_url).query)['id'][0]
assert '&lt;script&gt;나의 여행' in visitor.get(journey_url)
with visitor.http.open(BASE+'/api/v1/me/journeys/'+jid) as response:
    created=json.load(response)
assert [p['placeId'] for p in created['places']] == ['2','1']
other.get(journey_url,404)
visitor.get(journey_url)
csrf=next(f['fields']['csrf'] for f in visitor.forms if f['action'].endswith('/journeys/save'))
visitor.request('/journeys/delete', {'csrf':csrf,'id':jid},409)
visitor.get(journey_url)
text=visitor.get(journey_url); assert '본인 기록이며 방문 인증이 아닙니다' in text
assert 'record-cards' in text and 'journey-records.js' in text and 'id="record-next"' in text
visitor.get(journey_url); visitor.submit('/journeys/record', {'visitedOn':'2026-10-01','note':'<script>여행 메모</script>','rating':'4'})
assert '#record-' in visitor.location
assert '&lt;script&gt;여행 메모' in visitor.get(journey_url)
visitor.submit('/journeys/save', {'name':'나의 여행','travelDate':'2026-10-01','status':'ARCHIVED','issuedPassId':pid})
text=visitor.get(journey_url)
assert '보관된 여행입니다' in text and '이용권 연결 이력' in text and '현재 연결 중' in text
with visitor.http.open(BASE+'/api/v1/me/journeys/'+jid) as response:
    history=json.load(response)['benefitPassHistory']
linked_kst=datetime.fromisoformat(history[0]['linkedAt']).astimezone(ZoneInfo('Asia/Seoul')).strftime('%Y-%m-%d %H:%M:%S 한국 시간')
assert linked_kst in text
assert '나의 여행' in visitor.get('/journeys?status=ARCHIVED')
visitor.get(journey_url); visitor.submit('/journeys/save', {'name':'나의 여행','travelDate':'','status':'CONFIRMED','issuedPassId':''})
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
visitor.get('/profile'); visitor.submit('/logout')
visitor.get('/passes',303)
public.get('/login'); public.submit('/login', {'loginId':'visitor','password':'wrong'},401)
print('PASS: JSP forms, UTF-8, KST, ownership, roles, CSRF, PRG, code confirmation, feedback XSS, admin CRUD/review/cycle/cancel')

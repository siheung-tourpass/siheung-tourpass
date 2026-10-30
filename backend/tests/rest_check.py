"""HTTP semantics and cookie-free hypermedia flow; safe after api_check.py."""
import json
import urllib.request
import urllib.parse
from api_check import Client, BASE
from http_support import send

public, visitor, staff, other_staff, admin = [Client(n) for n in (None, 'visitor', 'staff-a', 'staff-b', 'admin')]
index = public.request('GET', '')[1]
assert 'products' in index['_links'] and 'journeys' not in index['_links']
assert public.headers.get('Set-Cookie') is None
assert public.headers['Cache-Control'] == 'no-store'
assert 'Access-Control-Allow-Origin' not in public.headers
public.request('GET', '/session', expected=404)
public.request('GET', '/me/journeys', expected=401)
assert public.headers['WWW-Authenticate'].startswith('Basic ')
for authorization in ('Bearer invalid', 'Basic @@@', 'Basic ' + 'x'*5000):
    invalid = Client(); invalid.authorization = authorization
    invalid.request('GET', '', expected=401)
# GET and HEAD agree; OPTIONS and 405 expose exactly the resource's methods.
public.request('GET', '/products/1')
length = public.headers['Content-Length']
public.request('HEAD', '/products/1')
assert public.headers['Content-Length'] == length
public.request('OPTIONS', '/products/1', expected=204)
assert set(public.headers['Allow'].replace(' ', '').split(',')) == {'GET', 'HEAD', 'OPTIONS'}
public.request('PUT', '/products/1', {}, expected=405)
assert set(public.headers['Allow'].replace(' ', '').split(',')) == {'GET', 'HEAD', 'OPTIONS'}
# A browser's form-login cookie alone cannot authenticate API requests.
visitor.web_login()
http = visitor.http
status, _, _ = send(http, urllib.request.Request(BASE+'/me/journeys'))
assert status == 401
status, _, raw = send(http, urllib.request.Request(BASE.removesuffix('/api/v1')+'/recommendations', headers={'Accept':'application/json'}))
assert status == 200 and json.loads(raw)['items']
# Follow returned controls without deriving URLs on the client.
base_path = urllib.parse.urlsplit(BASE).path
def follow(client, control, body=None, expected=200):
    return client.request(control['method'], control['href'].removeprefix(base_path), body, expected=expected)[1]
root = visitor.request('GET', '')[1]
collection = follow(visitor, root['_links']['journeys'])
journey = follow(visitor, collection['_links']['post'], {'name':'REST 검사'}, 201)
location = visitor.headers['Location']
assert location == journey['_links']['self']['href']
assert visitor.headers.get('Set-Cookie') is None
journey = follow(Client('visitor'), journey['_links']['self'])
for place in ('1','2','3'):
    journey = follow(visitor, journey['_links']['add-place'], {'placeId':place})
item = journey['places'][-1]
move = item['_links']['move']
assert move['method'] == 'POST'
visitor.request('PUT', move['href'].removeprefix(base_path), {'direction':'UP'}, expected=405)
assert set(visitor.headers['Allow'].replace(' ', '').split(',')) == {'POST','OPTIONS'}
journey = follow(visitor, move, {'direction':'UP'})
assert journey['places'][1]['id'] == item['id']
record = journey['places'][1]['_links']['record']
body = {'visitedOn':'2026-10-01','note':'같은 요청','rating':4}
follow(visitor, record, body)
first = follow(visitor, journey['_links']['self'])
follow(visitor, record, body)
assert follow(visitor, journey['_links']['self'])['places'] == first['places']
visitor.request('OPTIONS', location.removeprefix(base_path), expected=204)
assert set(visitor.headers['Allow'].replace(' ', '').split(',')) == {'GET','HEAD','PATCH','DELETE','OPTIONS'}
follow(visitor, journey['_links']['delete'], expected=204)
# DELETE can be repeated without resurrecting the resource.
follow(visitor, journey['_links']['delete'], expected=404)
# Relationship and paging links preserve filter parameters.
relations = admin.request('GET','/admin/ontology/relations?type=has-theme&size=1')[1]
follow(admin, relations['items'][0]['_links']['self'])
if 'next' in relations['_links']:
    next_page = follow(admin, relations['_links']['next'])
    assert next_page['page'] == 2 and next_page['size'] == 1
# PUT creates the named resource once, then replaces it on retries.
feedback='/me/places/1/feedback'
visitor.request('DELETE',feedback,expected=204)
visitor.request('PUT',feedback,{'rating':4},expected=201)
assert visitor.headers['Location'].endswith(feedback)
visitor.request('PUT',feedback,{'rating':4},expected=200)
assert visitor.request('GET',feedback)[1]['rating'] == 4
visitor.request('DELETE',feedback,expected=204)
visitor.request('DELETE',feedback,expected=204)
# Stored lookup belongs to a staff account, not an HTTP session.
passes = visitor.request('GET','/me/passes')[1]['items']
assert passes
issued = visitor.request('GET','/me/passes/'+passes[0]['id'])[1]
lookup = staff.request('POST','/merchant/pass-lookups',{'merchantId':'1','code':issued['code']})[1]
assert staff.headers['Location'] == lookup['_links']['self']['href']
assert follow(Client('staff-a'), lookup['_links']['self'])['id'] == lookup['id']
follow(other_staff, lookup['_links']['self'], expected=404)
# Two independent lookups stay valid instead of overwriting a session slot.
second = staff.request('POST','/merchant/pass-lookups',{'merchantId':'1','code':issued['code']})[1]
assert follow(Client('staff-a'), lookup['_links']['self'])['id'] != second['id']
# A confirmation is consumed atomically even when the same token races across clients.
owner = Client('visitor2')
issued = owner.request('POST','/me/passes',{'productId':'1'},expected=201)[1]
lookup = staff.request('POST','/merchant/pass-lookups',{'merchantId':'1','code':issued['code']})[1]
wrong = issued['benefits'][1]['id']
payload = {'confirmationToken':lookup['id'],'issuedBenefitId':wrong}
staff.request('POST','/merchant/redemptions',payload,expected=403)
assert follow(staff,lookup['_links']['self'])['used'] is False
payload['issuedBenefitId'] = issued['benefits'][0]['id']
import concurrent.futures
with concurrent.futures.ThreadPoolExecutor(2) as pool:
    results = list(pool.map(lambda c:c.request('POST','/merchant/redemptions',payload,expected=(201,409)),[Client('staff-a'),Client('staff-a')]))
assert sorted(status for status,_ in results) == [201,409]
assert next(body for status,body in results if status==409)['error']['code'] == 'CONFIRMATION_USED'
consumed = follow(Client('staff-a'),lookup['_links']['self'])
assert consumed['used'] is True and 'redeem' not in consumed['_links']
assert len(owner.request('GET','/me/redemptions?passId='+issued['id'])[1]['items']) == 1
admin.request('PUT','/admin/passes/'+issued['id']+'/cancellation',{'reason':'REST 검사 종료'})
print('PASS: stateless auth, CSRF isolation, HEAD/OPTIONS/Allow, links, paging, PUT/DELETE semantics and persistent lookup ownership')

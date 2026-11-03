const assert = require('node:assert/strict');
const {request} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const root = process.argv[2] || 'http://localhost:8080/siheung-tourpass';
(async () => {
  const contexts=[];
  async function session(loginId) {
    const client=await request.newContext();contexts.push(client);
    let html=await (await client.get(root+'/login')).text();
    await client.post(root+'/login',{form:{csrf:html.match(/name="csrf" value="([^"]+)"/)[1],loginId,password:'DemoPass!2026'}});
    html=await (await client.get(root+'/profile')).text();
    return {client,csrf:html.match(/name="csrf" value="([^"]+)"/)[1]};
  }
  const owner=await session('visitor'),other=await session('visitor2');
  const headers={Authorization:'Basic '+Buffer.from('visitor:DemoPass!2026').toString('base64'),'X-CSRF-Token':'api'};
  async function api(method,path,data) {
    const response=await owner.client.fetch(root+'/api/v1'+path,{method,headers,data});
    assert(response.ok(),await response.text());return response.status()===204?null:response.json();
  }
  const journey=await api('POST','/me/journeys',{name:'사진 검증 여행'}),path='/me/journeys/'+journey.id;
  try {
    const detail=await api('POST',path+'/places',{placeId:'1'}),itemId=detail.places[0].id;
    const photoUrl=root+`/journeys/photo?id=${journey.id}&itemId=${itemId}`;
    const image=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=','base64');
    async function upload(session,buffer=image,csrf=session.csrf) {
      return session.client.post(root+'/journeys/photo',{maxRedirects:0,multipart:{csrf,id:journey.id,itemId,photo:{name:'memory.png',mimeType:'image/png',buffer}}});
    }
    assert.equal((await upload(owner)).status(),303);
    const saved=await owner.client.get(photoUrl);assert.equal(saved.status(),200);assert(saved.headers()['content-type'].startsWith('image/jpeg'));
    assert.equal(saved.headers()['cache-control'],'no-store');
    assert.equal((await other.client.get(photoUrl)).status(),404);
    assert.equal((await upload(other)).status(),404);
    assert.equal((await upload(owner,image,'bad')).status(),403);
    assert.equal((await upload(owner,Buffer.from('<svg/>'))).status(),400);
    assert.equal((await upload(owner,Buffer.alloc(8*1024*1024+1))).status(),413);
    assert.equal((await owner.client.get(photoUrl)).status(),200,'Invalid replacement must preserve saved image');
    assert.equal((await upload(owner)).status(),303,'Replacement');
    await api('PATCH',path,{status:'CONFIRMED'});await api('PATCH',path,{status:'ARCHIVED'});
    const html=await (await owner.client.get(root+'/journeys/photocards?id='+journey.id)).text();
    assert(html.includes('goods-card')&&html.includes('직접 촬영한 사진')&&html.includes('itemId='+itemId));
    assert.equal((await other.client.get(root+'/journeys/photocards?id='+journey.id)).status(),404);
    assert.equal((await other.client.post(root+'/journeys/photo',{form:{csrf:other.csrf,id:journey.id,itemId,intent:'delete'}})).status(),404);
    assert.equal((await owner.client.post(root+'/journeys/photo',{maxRedirects:0,form:{csrf:owner.csrf,id:journey.id,itemId,intent:'delete'}})).status(),303);
    assert.equal((await owner.client.get(photoUrl)).status(),404);
    assert.equal((await upload(owner)).status(),303);
    await api('PATCH',path,{status:'CONFIRMED'});
    assert.equal((await owner.client.get(root+'/journeys/photocards?id='+journey.id)).status(),409);
    await api('PATCH',path,{status:'PLANNING'});await api('DELETE',path+'/places/'+itemId);
    assert.equal((await owner.client.get(photoUrl)).status(),404);
    console.log('PASS: upload, replacement, validation, CSRF, ownership, private delivery, printing state and deletion');
  } finally {
    const current=await api('GET',path);
    if(current.status==='ARCHIVED')await api('PATCH',path,{status:'CONFIRMED'});
    if(current.status!=='PLANNING')await api('PATCH',path,{status:'PLANNING'});
    await api('DELETE',path);
    await Promise.all(contexts.map(client=>client.dispose()));
  }
})().catch(error=>{console.error(error);process.exitCode=1;});

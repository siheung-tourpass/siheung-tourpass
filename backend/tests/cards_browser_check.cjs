const assert=require('node:assert/strict');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
async function checkAccessiblePopup(page, popup) {
 const viewport=page.viewportSize();
 for(const [width,height] of [[320,900],[1280,900],[375,400]]){
  await page.setViewportSize({width,height});
  const box=await popup.boundingBox();assert(box);
  assert(box.x>=0&&box.x+box.width<=width);
  assert(box.y>=0&&box.y+box.height<=height);
  assert(await popup.evaluate(el=>el.scrollWidth<=el.clientWidth));
 }
 await page.setViewportSize(viewport);
}
async function login(request, root) {
 const html=await (await request.get(root+'/login')).text();
 const csrf=html.match(/name="csrf" value="([^"]+)"/)[1];
 const response=await request.post(root+'/login',{form:{csrf,loginId:'visitor',password:'DemoPass!2026'}});
 assert.equal(response.status(),200);
 const logged=await (await request.get(root+'/profile')).text();
 return {csrfToken:logged.match(/name="csrf" value="([^"]+)"/)[1]};
}
(async()=>{
 const browser=await chromium.launch({headless:true,channel:'chrome'});
 const page=await browser.newPage({viewport:{width:1280,height:900}});
 const root=process.argv[2] || 'http://localhost:8080/siheung-tourpass';
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const homeSession=await login(page.request,root);
 await page.goto(root+'/');
 const homePhoto=page.locator('.home-photo'),homeCredit=page.locator('#home-photo-credit');
 assert.equal(await homeCredit.isVisible(),false);
 await homePhoto.getByRole('button',{name:'사진 출처',exact:true}).click();
 await checkAccessiblePopup(page,homeCredit);
 assert.equal(await homeCredit.locator('a').count(),2);
 await page.keyboard.press('Escape');assert(await homePhoto.getByRole('button',{name:'사진 출처',exact:true}).evaluate(el=>el===document.activeElement));
 await homePhoto.getByRole('button',{name:'사진 출처',exact:true}).click();await homeCredit.getByRole('button',{name:'닫기',exact:true}).click();assert.equal(await homeCredit.isVisible(),false);
 assert.equal((await page.request.post(root+'/logout',{form:{csrf:homeSession.csrfToken}})).status(),200);
 await page.goto(root+'/explore');await page.waitForSelector('.is-deck-mode');
 await page.waitForFunction(()=>!document.querySelector('#journey-filters').hasAttribute('aria-busy'));
 assert(await page.locator('#builder-next').isDisabled());
 assert.equal(await page.locator('#bar-count').textContent(),'마음에 드는 한 장을 골라 주세요');
 assert.equal(await page.locator('#deck-summary').isVisible(),false);
 assert(await page.locator('#start-preferences').isVisible());
 // A single proposal has no deck controls; multiple proposals reveal navigation.
 const proposals=await page.locator('[data-card-kind=place]').evaluateAll(cards=>cards.slice(0,2).map(card=>({place:{id:Number(card.dataset.cardKey.split(':')[1])},explanation:'조건에 맞는 관광지'})));
 assert.equal(proposals.length,2);
 let proposalItems=proposals.slice(0,1);
 await page.route('**/recommendations?**',route=>route.fulfill({json:{items:proposalItems}}));
 await page.locator('[name=themeId]').selectOption({index:1});
 await page.waitForFunction(()=>document.querySelector('#journey-builder').dataset.suggesting==='false');
 assert.equal(await page.locator('.deck-navigation').isVisible(),false);
 assert.equal(await page.locator('#start-alternatives').isVisible(),false);
 assert.equal(await page.locator('.builder-card:visible').count(),1);
 proposalItems=proposals;
 await page.locator('[name=themeId]').selectOption({index:2});
 await page.waitForFunction(()=>document.querySelector('#journey-builder').dataset.suggesting==='false');
 assert(await page.locator('.deck-navigation').isVisible());
 assert(await page.locator('#start-alternatives').isVisible());
 await page.unroute('**/recommendations?**');
 await page.locator('[name=themeId]').selectOption('');
 await page.waitForFunction(()=>document.querySelector('#journey-builder').dataset.suggesting==='false');
 const themes=page.locator('select[name=themeId] option:not([value=""])');
 assert(await themes.count()>=2);
 await page.locator('[name=themeId]').selectOption(await themes.nth(1).getAttribute('value'));
 const theme=themes.first();
 const themeId=await theme.getAttribute('value');
 const recommended=page.waitForResponse(response=>response.url().includes('/recommendations?')&&new URL(response.url()).searchParams.getAll('themeId').includes(themeId));
 await page.locator('[name=themeId]').selectOption(themeId);
 const recommendation=await (await recommended).json();
 assert.equal(await page.locator('select[name=themeId] option:checked').count(),1);
 assert.equal(await page.locator('[name=themeId]').inputValue(),themeId);
 await page.waitForFunction(()=>!document.querySelector('#journey-filters').hasAttribute('aria-busy'));
 if(recommendation.items.length)assert.equal(await page.locator('.builder-card.is-current').getAttribute('data-card-key'),'place:'+recommendation.items[0].place.id);
 // Start modes show their own candidates and keep deck composition on the next page.
 await page.locator('[name=startMode]').selectOption('region');
 const regionId=await page.locator('#start-region option:not([value=""])').first().getAttribute('value');
 await page.locator('#start-region').selectOption(regionId);
 await page.waitForFunction(()=>!document.querySelector('#journey-filters').hasAttribute('aria-busy'));
 assert(await page.locator('.builder-card:visible').count()>0);
 assert(await page.locator('.builder-card:visible').evaluateAll((cards,region)=>cards.every(card=>card.dataset.cardKind==='place'&&card.dataset.regionId===region),regionId));
 await page.locator('[name=startMode]').selectOption('event');
 assert(await page.locator('#start-event-panel').isVisible());
 assert(await page.locator('.builder-card:visible').count()>0);
 assert(await page.locator('.builder-card:visible').evaluateAll(cards=>cards.every(card=>card.dataset.cardKind==='event')));
 await page.locator('#start-event-date').fill('1900-01-01');
 assert(await page.locator('#builder-empty').isVisible());
 await page.locator('#start-event-date').fill('');
 await page.locator('.builder-card.is-current .card-select-toggle').click();
 await page.locator('#builder-next').click();
 assert(await page.locator('#deck-summary').isVisible());
 assert.equal(await page.locator('#builder-first-card').isVisible(),false);
 await page.locator('#builder-back').click();
 assert.equal(await page.locator('[name=startMode]').inputValue(),'event');
 await page.locator('.builder-card.is-selected .card-select-toggle').click();
 await page.locator('[name=startMode]').selectOption('theme');
 assert.equal(await page.locator('[name=themeId]').inputValue(),themeId);
 await page.locator('[name=startMode]').selectOption('direct');
 assert(await page.locator('#builder-search-panel').evaluate(el=>el.open));
 // Choosing a card keeps it in view and ranks unselected cards by straight-line distance.
 const anchor=await page.locator('.builder-card[data-card-kind=place]').evaluateAll(cards=>{
  const card=cards.find(card=>card.dataset.latitude&&card.dataset.longitude);
  return card?{key:card.dataset.cardKey,name:card.querySelector('h3').textContent,region:card.dataset.regionId}:null;
 });
 assert(anchor,'The fixture needs a tourism card with sourced coordinates');
 await page.locator('[name=startMode]').selectOption('direct');
 await page.locator('#builder-search-panel').evaluate(el=>el.open=true);
 await page.locator('.builder-filter-panel summary').click();
 await page.locator('#card-search').fill(anchor.name);
 await page.locator(`.builder-card[data-card-key="${anchor.key}"] .card-select-toggle`).click();
 assert.equal(await page.locator('#bar-count').textContent(),anchor.name);
 assert.equal(await page.locator('#bar-label').textContent(),'시작 카드');
 await page.locator('#card-search').fill('');
 assert(await page.locator('#builder-first-card').isVisible());
 assert.equal(await page.locator('#journey-builder').getAttribute('data-step'),'start');
 await page.locator('#builder-next').click();
 assert.equal(await page.locator('#journey-builder').getAttribute('data-step'),'build');
 assert(await page.locator('#builder-page-title').evaluate(el=>el===document.activeElement));
 assert.equal(await page.locator('#builder-first-card').isVisible(),false);
 assert(await page.locator('#builder-first-card').evaluate(el=>el.disabled));
 assert.equal(await page.locator('.builder-card.is-current').getAttribute('data-card-key'),anchor.key);
 const ordered=await page.locator('#browse-cards > article').evaluateAll(cards=>cards.map(card=>({distance:card.dataset.distanceKm,key:card.dataset.cardKey,latitude:card.dataset.latitude,longitude:card.dataset.longitude})));
 let previous=-1;
 for(const card of ordered.slice(1)){
  const km=card.distance===undefined?Infinity:Number(card.distance);
  assert(km>=previous,'Known points must be closest first; unknown points last');previous=km;
 }
 await page.locator('#deck-next').click();
 assert.equal(await page.locator('.builder-card.is-current').getAttribute('data-card-key'),ordered[1].key);
 assert(await page.locator('.builder-card.is-current .card-distance').isVisible());
 const origin=ordered[0],closest=ordered[1];
 const north=(Number(closest.latitude)-Number(origin.latitude))*111.195;
 const east=(Number(closest.longitude)-Number(origin.longitude))*111.195*Math.cos(Number(origin.latitude)*Math.PI/180);
 assert(Math.abs(Number(closest.distance)-Math.hypot(north,east))<.1,'Displayed distance must match the coordinate separation');
 await page.locator('[type=reset]').click();
 await page.waitForFunction(()=>!document.querySelector('#journey-filters').hasAttribute('aria-busy'));
 assert.equal(await page.locator('.card-select-toggle[aria-pressed=true]').count(),1);
 await page.locator('#selected-places [data-action="빼기"]').click();
 assert.equal(await page.locator('#journey-builder').getAttribute('data-step'),'build');
 await page.locator('#builder-next').click();
 assert.equal(await page.locator('#journey-builder').getAttribute('data-step'),'review');
 await page.locator('#builder-back').click();
 await page.locator('#builder-back').click();
 assert(await page.locator('#builder-first-card').isVisible());
 assert.equal(await page.locator('#builder-first-card').evaluate(el=>el.disabled),false);
 await page.locator('[name=startMode]').selectOption('direct');
 await page.locator('#builder-search-panel').evaluate(el=>el.open=true);
 await page.locator('.builder-filter-panel summary').click();
 for(const width of [320,1280]){
  await page.setViewportSize({width,height:900});
  for(const kind of ['event','dining','place']){
   await page.locator(`[data-card-type=${kind}]`).click();await page.waitForTimeout(150);
   const card=page.locator('.builder-card.is-current');
   const select=card.locator('.card-select-toggle');
   await select.click({position:{x:80,y:100}});
   assert.equal(await select.getAttribute('aria-pressed'),'true');
   assert(await card.evaluate(el=>el.classList.contains('is-selected')));
   await card.locator('.card-front .card-flip').click();await page.waitForTimeout(330);
   assert(await card.evaluate(el=>el.classList.contains('is-flipped')));
   await card.locator('.card-back .card-flip').click();await page.waitForTimeout(330);
   await select.focus();await page.keyboard.press('Space');assert.equal(await select.getAttribute('aria-pressed'),'false');
  }
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 }
 await page.locator('[data-card-type=place]').click();
 const active=page.locator('.builder-card.is-current');
 await active.locator('.card-front .card-flip').click();
 await page.locator('[data-card-type=event]').click();
 assert.equal(await page.locator('.builder-card.is-flipped').count(),0);
 assert.equal(await page.locator('.builder-card[aria-current=true]').count(),1);
 const session=await login(page.request,root);let r;
 await page.goto(root+'/journeys');
 const picker=page.locator('#journey-start-picker');assert.equal(await picker.isVisible(),false);
 await page.getByRole('button',{name:'새 여행 만들기',exact:true}).click();
 assert(await picker.locator('.journey-template').count()>0);
 await checkAccessiblePopup(page,picker);
 await picker.getByRole('link',{name:'빈 구성으로 시작',exact:true}).click();await page.waitForSelector('.is-deck-mode');await page.waitForFunction(()=>!document.querySelector('#journey-filters').hasAttribute('aria-busy'));
 assert(await page.locator('#start-preferences').isVisible());
 await page.locator('[name=startMode]').selectOption('direct');
 const keys=[];
 for(const kind of ['event','dining','place']){
  await page.locator(`[data-card-type=${kind}]`).click();const card=page.locator('.builder-card.is-current');keys.push(await card.getAttribute('data-card-key'));await card.locator('.card-select-toggle').click({position:{x:80,y:100}});
  if(kind==='event')await page.locator('#builder-next').click();
 }
 assert.equal(await page.locator('#selected-places li').count(),3);
 // Revisiting the first step replaces only the first card, with no implicit navigation.
 await page.locator('#builder-back').click();
 const replacement=await page.locator('.builder-card[data-card-kind=place]').evaluateAll((cards,keys)=>{
  const card=cards.find(card=>!keys.includes(card.dataset.cardKey));
  return {key:card.dataset.cardKey,name:card.querySelector('h3').textContent};
 },keys);
 assert(await page.locator('#builder-search-panel').evaluate(el=>el.open));
 await page.locator('#card-search').fill(replacement.name);
 await page.locator(`[data-card-key="${replacement.key}"] .card-select-toggle`).click();
 assert.equal(await page.locator('#journey-builder').getAttribute('data-step'),'start');
 assert.deepEqual(await page.locator('#selected-places input').evaluateAll(items=>items.map(x=>x.value)),[replacement.key,...keys.slice(1)]);
 await page.locator('[data-card-type=event]').click();
 const originalName=await page.locator(`[data-card-key="${keys[0]}"] h3`).textContent();
 await page.locator('#card-search').fill(originalName);
 await page.locator(`[data-card-key="${keys[0]}"] .card-select-toggle`).click();
 await page.locator('#card-search').fill('');
 await page.locator('#builder-next').click();
 assert.deepEqual(await page.locator('#selected-places input').evaluateAll(items=>items.map(x=>x.value)),keys);
 await page.locator('#builder-next').click();
 assert.equal(await page.locator('#journey-filters').isVisible(),false);
 assert.equal(await page.locator('.builder-catalog').isVisible(),false);
 assert(await page.locator('input[name=name]').isVisible());
 await page.locator('input[name=name]').fill('단계 이동 보존');
 await page.goBack();
 assert.equal(await page.locator('#journey-builder').getAttribute('data-step'),'build');
 await page.goForward();
 assert.equal(await page.locator('input[name=name]').inputValue(),'단계 이동 보존');
 for(const width of [320,1280]){
  await page.setViewportSize({width,height:900});
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 }
 for (const control of await page.locator('.selection-controls button').all()) {
  const box = await control.boundingBox(); assert(box.width>=44 && box.height>=44); assert(await control.getAttribute('aria-label'));
 }
 const firstControls=page.locator('#selected-places li').first().locator('.selection-controls');
 await firstControls.evaluate(el=>el.append(el.firstElementChild));
 assert.equal(await firstControls.locator('[data-action="위로"]').isDisabled(),true);
 assert.equal(await firstControls.locator('[data-action="아래로"]').isDisabled(),false);
 await page.locator('#selected-place-template').evaluate(el=>{
  const controls=el.content.querySelector('.selection-controls');controls.append(controls.firstElementChild);
 });
 await firstControls.locator('[data-action="아래로"]').click();
 assert.equal(await page.evaluate(()=>document.activeElement.dataset.action),'아래로');
 assert.equal(await firstControls.locator('[data-action="위로"]').isDisabled(),true);
 assert.match(await firstControls.locator('[data-action="빼기"]').getAttribute('aria-label'),/선택 해제$/);
 await page.locator('#selected-places li').nth(1).locator('[data-action="위로"]').click();
 await page.locator('#selected-places li').last().getByRole('button',{name:/위로/}).click();
 assert.equal(await page.evaluate(()=>document.activeElement.dataset.action),'위로');
 const order=[keys[0],keys[2],keys[1]];
 await page.locator('#builder-back').click();
 await page.evaluate(()=>window.dispatchEvent(new Event('pageshow')));
 await page.locator('.builder-filter-panel > summary').click();
 await page.locator('#card-search').fill('no matching cards xyz');
 assert.equal(await page.locator('.builder-card:visible').count(),0);
 assert.equal(await page.locator('#builder-empty').isVisible(),true);
 await page.locator('#card-search').fill('');
 await page.locator('#shuffle-cards').click();
 assert.deepEqual(await page.locator('#selected-places input[name=cardKey]').evaluateAll(inputs=>inputs.map(input=>input.value)),order);
 await page.locator('#builder-next').click();
 await page.locator('input[name=name]').fill('통합 카드 브라우저 검증');await page.locator('#save-journey').click();await page.waitForLoadState();
 const id=new URL(page.url()).searchParams.get('id');assert(id);
 try{
  await page.waitForSelector('.is-deck-mode');
  assert.equal(await page.locator('.card-select-toggle[aria-pressed=true]').count(),3);
  r=await page.request.get(root+'/api/v1/me/journeys/'+id,{headers:{Authorization:'Basic '+Buffer.from('visitor:DemoPass!2026').toString('base64')}});let saved=await r.json();assert.deepEqual(saved.places.map(x=>x.cardKey),order);
  let expectedCover=null;
  for(const key of order){
   const photo=page.locator(`[data-card-key="${key}"] .card-background`);
   if(await photo.count()){expectedCover=await photo.getAttribute('src');break;}
  }
  await page.goto(root+'/journeys');
  const pass=page.locator('.journey-business-card').filter({has:page.locator(`a[href$="edit?id=${id}"]`)});
  assert.equal(await pass.locator('.pass-cover-image').count(),expectedCover?1:0);
  if(expectedCover)assert.equal(await pass.locator('.pass-cover-image').getAttribute('src'),expectedCover);
  if(await pass.locator('.pass-cover-credit').count()){
   const before=await pass.boundingBox();
   await pass.locator('.pass-cover-trigger').click();assert.equal(new URL(page.url()).pathname.endsWith('/journeys'),true);
   assert(await pass.locator('.pass-cover-credit .pass-cover-popover').evaluate(el=>el.matches(':popover-open')));
   assert.deepEqual(await pass.boundingBox(),before);
   await checkAccessiblePopup(page,pass.locator('.pass-cover-credit .pass-cover-popover'));
   await page.keyboard.press('Escape');assert(await pass.locator('.pass-cover-trigger').evaluate(el=>el===document.activeElement));
  }
  const deleteTrigger=pass.locator('.journey-ticket-delete');
  const deletePopover=pass.locator('.journey-delete-popover');
  const beforeDelete=await pass.boundingBox();
  await deleteTrigger.click();assert.equal(new URL(page.url()).pathname.endsWith('/journeys'),true);
  assert(await deletePopover.evaluate(el=>el.matches(':popover-open')));
  assert.deepEqual(await pass.boundingBox(),beforeDelete);
  await checkAccessiblePopup(page,deletePopover);
  await page.keyboard.press('Escape');assert(await deleteTrigger.evaluate(el=>el===document.activeElement));
  await deleteTrigger.click();await deletePopover.getByRole('button',{name:'닫기'}).click();
  assert(!await deletePopover.evaluate(el=>el.matches(':popover-open')));
  await deleteTrigger.click();await page.mouse.click(2,2);
  assert(!await deletePopover.evaluate(el=>el.matches(':popover-open')));
  const passBox=await pass.boundingBox();await page.mouse.click(passBox.x+passBox.width-12,passBox.y+passBox.height/2);
  await page.waitForURL(url=>url.pathname.endsWith('/journeys/edit')&&url.searchParams.get('id')===id);await page.waitForSelector('.is-deck-mode');
  await page.locator('#builder-next').click();assert.deepEqual(await page.locator('#selected-places li').evaluateAll(items=>items.map(x=>x.dataset.selectedId)),order);
  await page.locator('#selected-places li').first().getByRole('button',{name:/선택 해제/}).click();assert.equal(await page.locator('#selected-places li').count(),2);
  await page.locator('#save-journey').click();await page.waitForLoadState();
  r=await page.request.get(root+'/api/v1/me/journeys/'+id,{headers:{Authorization:'Basic '+Buffer.from('visitor:DemoPass!2026').toString('base64')}});saved=await r.json();assert.deepEqual(saved.places.map(x=>x.cardKey),order.slice(1));
  const beforeStatus=saved.places;
  r=await page.request.patch(root+'/api/v1/me/journeys/'+id,{headers:{'X-CSRF-Token':'api','Authorization':'Basic '+Buffer.from('visitor:DemoPass!2026').toString('base64')},data:{status:'CONFIRMED'}});assert.equal(r.status(),200);
  for(const [current,next,label] of [['CONFIRMED','ARCHIVED','사용 완료 · 아카이브'],['ARCHIVED','CONFIRMED','제작 확정 목록으로 복원'],['CONFIRMED','PLANNING','제작 중으로 보내기']]){
   await page.goto(root+'/journeys?status='+current);
   const statusCard=page.locator('.journey-card').filter({has:page.locator(`a[href$="detail?id=${id}"]`)});
   await statusCard.locator('.journey-ticket-action').click();
   await checkAccessiblePopup(page,statusCard.locator('section[id^=pass-status-]'));
   await statusCard.locator('section[id^=pass-status-]').getByRole('button',{name:label,exact:true}).click();
   await page.waitForURL(root+'/journeys?status='+next);
   r=await page.request.get(root+'/api/v1/me/journeys/'+id,{headers:{Authorization:'Basic '+Buffer.from('visitor:DemoPass!2026').toString('base64')}});const changed=await r.json();
   assert.equal(changed.status,next);assert.deepEqual(changed.places.map(({_links,...data})=>data),beforeStatus.map(({_links,...data})=>data));
  }
 }finally{r=await page.request.delete(root+'/api/v1/me/journeys/'+id,{headers:{'X-CSRF-Token':'api','Authorization':'Basic '+Buffer.from('visitor:DemoPass!2026').toString('base64')},data:{}});assert.equal(r.status(),204);}
 // The server-rendered cards remain usable without JavaScript.
 const plainContext=await browser.newContext({javaScriptEnabled:false});
 const plain=await plainContext.newPage();
 const plainSession=await login(plain.request,root);
 await plain.goto(root+'/journeys/new');
 assert.equal(await plain.locator('.card-select-toggle:visible').count(),0);
 const pick=plain.locator('.builder-card[data-card-kind=place] input[type=checkbox]').first();
 await pick.check();const plainKey=await pick.getAttribute('value');
 await plain.locator('input[name=name]').fill('스크립트 없이 저장');
 await plain.locator('#save-journey').click();await plain.waitForLoadState();
 const plainId=new URL(plain.url()).searchParams.get('id');assert(plainId);
 try {
  assert.equal(await plain.locator(`input[value="${plainKey}"]`).isChecked(),true);
  r=await plain.request.get(root+'/api/v1/me/journeys/'+plainId,{headers:{Authorization:'Basic '+Buffer.from('visitor:DemoPass!2026').toString('base64')}});const saved=await r.json();
  assert.deepEqual(saved.places.map(item=>item.cardKey),[plainKey]);
 } finally {
  r=await plain.request.delete(root+'/api/v1/me/journeys/'+plainId,{headers:{'X-CSRF-Token':'api','Authorization':'Basic '+Buffer.from('visitor:DemoPass!2026').toString('base64')},data:{}});assert.equal(r.status(),204);
 }
 await plainContext.close();
 assert.deepEqual(errors,[]);
 await browser.close();console.log('PASS: all three card kinds share click/keyboard/flip/selection UX at mobile and desktop widths; mixed order, composition cover, save/reload, removal and no-JavaScript saving persist.');
})().catch(e=>{console.error(e);process.exit(1)});

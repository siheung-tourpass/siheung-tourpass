const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const root = process.argv[2] || 'http://localhost:8081/siheung-tourpass';
(async () => {
  const browser = await chromium.launch({channel:'chrome',headless:true});
  try {
    const context = await browser.newContext();
    const page = await context.newPage();
    const errors=[];page.on('pageerror',e=>errors.push(e.message));
    await page.goto(root+'/login');
    await page.locator('[name=loginId]').fill('admin');await page.locator('[name=password]').fill('DemoPass!2026');
    await Promise.all([page.waitForURL(root+'/'),page.getByRole('button',{name:'로그인',exact:true}).click()]);
    await page.goto(root+'/admin');
    assert(await page.locator('.admin-place-card').count()>0);
    for(const kind of ['place','event','restaurant','cafe']) {
      await page.locator(`button[data-admin-kind=${kind}]`).click();
      assert(await page.locator('.admin-place-card:visible').count()>0,kind);
      assert(await page.locator('.admin-place-card:visible').evaluateAll((cards,kind)=>cards.every(c=>c.dataset.adminKind===kind),kind));
    }
    await page.locator('button[data-admin-kind=all]').click();
    if(await page.locator('.admin-browser-more').isVisible()){
      const before=await page.locator('.admin-place-card:visible').count();
      await page.locator('.admin-browser-more').click();
      assert(await page.locator('.admin-place-card:visible').count()>before);
    }
    for(const route of ['/admin','/admin/ontology?collection=places','/admin/catalog?collection=products','/admin/relations','/admin/place-feedback']) {
      const response=await page.goto(root+route);assert.equal(response.status(),200,route);
      for(const width of [320,375,414,768,1280]) {
        await page.setViewportSize({width,height:1000});
        assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),route+' '+width);
      }
    }
    await page.goto(root+'/admin/ontology?collection=places&active=true');
    const record = page.locator('.admin-record').first();
    const code = (await record.locator('.admin-record-code').textContent()).split(' · ')[0];
    await page.locator('[name=q]').fill(code);
    await Promise.all([page.waitForNavigation(), page.getByRole('button',{name:'검색',exact:true}).click()]);
    await page.locator('.admin-record').first().click();
    assert.equal(new URL(page.url()).searchParams.get('q'),code);
    assert.equal(new URL(page.url()).searchParams.get('active'),'true');
    assert.equal(new URL(page.url()).hash,'#admin-edit');
    assert.equal(await page.locator('.admin-record[aria-current]').count(),1);
    assert.equal(await page.getByRole('button',{name:'비활성화',exact:true}).isVisible(),false);
    await page.locator('.admin-danger summary').click();
    assert(await page.getByRole('button',{name:'비활성화',exact:true}).isVisible());
    for(const width of [320,375,414,768,1280]) {
      await page.setViewportSize({width,height:1000});
      assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'selected editor '+width);
    }
    await page.goto(root+'/admin/ontology?collection=places&q=NO_MATCH_7b9');
    assert.equal(await page.locator('.admin-record').count(),0);
    await page.goto(root+'/admin');
    const choices=await page.locator('.admin-place-card').evaluateAll(cards=>cards.map(c=>({value:c.dataset.key,text:c.querySelector('h3').textContent})));
    await page.locator('.admin-place-card:visible .admin-card-select').first().click();
    await page.getByRole('link',{name:'카드 출력',exact:true}).click();
    assert(await page.locator('.goods-settings').isVisible());
    assert(await page.locator('.goods-selection').isVisible());
    assert((await page.locator('#goods-selection-name').textContent()).trim().length>0);
    assert.equal(await page.locator('.admin-card-browser').count(),0);
    assert.equal(await page.locator('.bottom-nav [aria-current]').getAttribute('href'),new URL(root).pathname+'/admin');
    for(const kind of ['place','event','dining']) {
      const choice=choices.find(o=>o.value.startsWith(kind+':'));assert(choice,kind);
      await page.goto(root+'/admin/goods?key='+encodeURIComponent(choice.value));
      assert.equal(await page.locator('[name=key]').inputValue(),choice.value);
      await page.locator('[name=caption]').fill('시흥에서 만난 좋은 순간');
      await page.locator('[name=copies]').selectOption('4');
      await Promise.all([page.waitForNavigation(),page.getByRole('button',{name:'미리보기 만들기',exact:true}).click()]);
      assert.equal(await page.locator('.goods-card').count(),4);
      assert.equal(await page.locator('.goods-qr path').count(),4);
      const url=await page.locator('.goods-proof a').first().getAttribute('href');
      const anonymous=await browser.newContext();
      const detail=await anonymous.request.get(url);assert.equal(detail.status(),200);assert((await detail.text()).includes('public-card'));
      await anonymous.close();
      await page.emulateMedia({media:'print'});
      const layout=await page.locator('.goods-sheet').evaluate(sheet=>({width:sheet.getBoundingClientRect().width,height:sheet.getBoundingClientRect().height,cards:[...sheet.children].map(card=>({w:card.getBoundingClientRect().width,h:card.getBoundingClientRect().height,overflow:card.scrollHeight>card.clientHeight}))}));
      assert(layout.width<719 && layout.height<1048,JSON.stringify(layout));
      assert(layout.cards.every(c=>Math.abs(c.w-90*96/25.4)<1&&Math.abs(c.h-130*96/25.4)<1&&!c.overflow),JSON.stringify(layout));
      await page.emulateMedia({media:'screen'});
      for(const width of [320,375,414,768,1280]) {
        await page.setViewportSize({width,height:1000});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
      }
      await page.locator('[name=caption]').fill('새 문구');assert(await page.locator('#goods-print').isDisabled());
    }
    await page.goto(root+'/admin/catalog?collection=products');
    assert.equal(await page.locator('#admin-edit').count(),0);
    assert(await page.locator('.admin-linked-benefit').count()>0);
    await page.locator('.admin-record').first().click();
    assert(await page.locator('#admin-edit').isVisible());
    await page.getByRole('link',{name:'새 자료 등록',exact:true}).click();
    assert(await page.locator('#admin-edit [name=code]').isVisible());
    const noScript=await browser.newContext({javaScriptEnabled:false});
    const fallback=await noScript.newPage();
    await fallback.goto(root+'/login');
    await fallback.locator('[name=loginId]').fill('admin');await fallback.locator('[name=password]').fill('DemoPass!2026');
    await Promise.all([fallback.waitForNavigation(),fallback.getByRole('button',{name:'로그인',exact:true}).click()]);
    await fallback.goto(root+'/admin');
    await fallback.locator('.admin-place-card .admin-card-select').first().click();
    await fallback.getByRole('link',{name:'카드 출력',exact:true}).click();
    assert.notEqual(await fallback.locator('[name=key]').inputValue(),'');
    assert(await fallback.locator('.goods-settings').isVisible());
    await noScript.close();
    await page.goto(root+'/admin/goods?key='+encodeURIComponent(choices[0].value));
    const csrf=await page.locator('[name=csrf]').inputValue();
    const key=choices.find(o=>o.value.startsWith('event:')).value;
    for(const [body,status] of [[{key,baseUrl:root,copies:'5',csrf},400],[{key:'event:missing',baseUrl:root,copies:'1',csrf},404],[{key,baseUrl:'javascript:alert(1)',copies:'1',csrf},400],[{key,baseUrl:root,copies:'1',csrf:'bad'},403]]) {
      const response=await context.request.post(root+'/admin/goods',{form:body});assert.equal(response.status(),status);
    }
    for(const role of ['visitor','staff-a']) {
      const restricted=await browser.newContext();const p=await restricted.newPage();
      await p.goto(root+'/login');await p.locator('[name=loginId]').fill(role);await p.locator('[name=password]').fill('DemoPass!2026');
      await Promise.all([p.waitForNavigation(),p.getByRole('button',{name:'로그인',exact:true}).click()]);
      assert.equal((await restricted.request.get(root+'/admin/goods')).status(),403);
      await restricted.close();
    }
    const escaped=await context.request.post(root+'/admin/goods',{form:{key,baseUrl:root,copies:'1',csrf,caption:'<script>bad()</script>'}});
    assert.equal(escaped.status(),200);assert((await escaped.text()).includes('&lt;script&gt;bad()&lt;/script&gt;'));
    const anonymous=await browser.newContext();
    assert.equal((await anonymous.request.get(root+'/admin/goods',{maxRedirects:0})).status(),303);
    assert.equal((await anonymous.request.get(root+'/cards/detail?key=place:999999999')).status(),404);
    await anonymous.close();
    assert.deepEqual(errors,[]);
    console.log('PASS: admin pages, responsive layout, filters, 3 goods types, public QR destination, A4 dimensions, stale preview and authorization');
  } finally { await browser.close(); }
})().catch(e=>{console.error(e);process.exitCode=1;});

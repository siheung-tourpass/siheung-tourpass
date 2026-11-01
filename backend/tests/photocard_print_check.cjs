const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {runInNewContext} = require('node:vm');
const source = readFileSync('frontend/assets/photocard-print.js', 'utf8');

async function check(failure) {
  let click, printed = false, finishImage;
  const image = new Promise(resolve => { finishImage = resolve; });
  const button = {hidden: true, disabled: false, addEventListener(name, handler) { click = handler; }};
  const status = {textContent: ''};
  runInNewContext(source, {
    document: {
      querySelector: selector => selector === '[data-photo-print]' ? button : status,
      querySelectorAll: () => [{decode: () => failure === 'image' ? Promise.reject(new Error()) : image}],
      fonts: {get ready() { return failure === 'font' ? Promise.reject(new Error()) : Promise.resolve(); }}
    },
    window: {print() { assert(button.disabled); printed = true; }}
  });
  assert.equal(button.hidden, false);
  const pending = click();
  assert(button.disabled);
  assert(status.textContent);
  assert.equal(printed, false);
  finishImage();
  await pending;
  assert.equal(button.disabled, false);
  assert.equal(printed, !failure);
  assert.equal(Boolean(status.textContent), Boolean(failure));
}

(async () => {
  runInNewContext(source, {document: {querySelector: () => null}});
  for (const failure of [null, 'image', 'font']) await check(failure);
  console.log('PASS: shared photocard print waits for images/fonts, restores controls and reports failures');
})().catch(error => { console.error(error); process.exitCode = 1; });

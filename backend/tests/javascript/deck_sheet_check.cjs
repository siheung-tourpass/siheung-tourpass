const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {runInNewContext} = require('node:vm');

// Minimal elements that record listeners, attributes and inline styles.
const element = (extra = {}) => ({
  listeners: {}, dataset: {}, attributes: {}, styles: {}, children: [],
  addEventListener(name, handler) { (this.listeners[name] ||= []).push(handler); },
  fire(name, event = {}) { (this.listeners[name] || []).forEach(handler => handler({target: this, button: 0, pointerId: 1, ...event})); },
  setAttribute(name, value) { this.attributes[name] = value; },
  getAttribute(name) { return this.attributes[name]; },
  contains(target) { return target === this || this.children.includes(target); },
  style: {
    setProperty(name, value) { this.owner.styles[name] = value; },
    removeProperty(name) { delete this.owner.styles[name]; },
    set height(value) { this.owner.styles.height = value; }
  },
  ...extra
});
const withStyle = node => { node.style.owner = node; return node; };
const handle = withStyle(element({hidden: true, focused: false, focus() { this.focused = true; }, setPointerCapture() {}}));
const label = {textContent: ''};
const listItem = withStyle(element());
const preview = withStyle(element());
const next = withStyle(element());
const sheet = withStyle(element({getBoundingClientRect: () => ({height: 200})}));
sheet.children.push(handle, preview, listItem, next);
const outside = withStyle(element());
const document = withStyle(element());
const environment = {document, innerHeight: 800};
const DeckSheet = runInNewContext(readFileSync('frontend/assets/js/deck-sheet.js', 'utf8') + '\nDeckSheet;', environment);
const deck = DeckSheet.bind(sheet, {handle, preview, label});
const state = () => [sheet.dataset.sheet, handle.getAttribute('aria-expanded')];

assert.equal(sheet.dataset.sheet, undefined, 'the sheet stays off until the build step enables it');
handle.fire('click');
assert.equal(sheet.dataset.sheet, undefined);

deck.setEnabled(true);
assert(!handle.hidden);
assert.deepEqual(state(), ['peek', 'false']);
assert.equal(label.textContent, '지도·순서 보기');

handle.fire('click');
assert.deepEqual(state(), ['open', 'true']);
assert.equal(label.textContent, '접기');
document.fire('keydown', {key: 'Escape'});
assert.deepEqual(state(), ['peek', 'false']);
assert(handle.focused, 'Escape returns focus to the handle');

// Pressing the folded map preview or tabbing into it unfolds the sheet; pressing outside folds it.
// The next-step button in the folded row works without unfolding.
next.fire('pointerdown');
assert.equal(sheet.dataset.sheet, 'peek');
preview.fire('pointerdown');
assert.equal(sheet.dataset.sheet, 'open');
document.fire('pointerdown', {target: next});
assert.equal(sheet.dataset.sheet, 'open', 'presses inside the sheet keep it open');
document.fire('pointerdown', {target: outside});
assert.equal(sheet.dataset.sheet, 'peek');
preview.fire('focusin');
assert.equal(sheet.dataset.sheet, 'open');
deck.close();
assert.equal(sheet.dataset.sheet, 'peek');

// Dragging the handle follows the pointer, then snaps open or closed past the threshold.
handle.fire('pointerdown', {clientY: 600});
handle.fire('pointermove', {clientY: 500});
assert.equal(sheet.styles.height, '300px');
assert.equal(sheet.dataset.dragging, 'true');
handle.fire('pointerup', {clientY: 500});
handle.fire('click');
assert.deepEqual(state(), ['open', 'true'], 'a drag ends without the trailing click toggling back');
assert.equal(sheet.styles.height, undefined);
assert.equal(sheet.dataset.dragging, undefined);
handle.fire('pointerdown', {clientY: 300});
handle.fire('pointermove', {clientY: 320});
handle.fire('pointerup', {clientY: 320});
handle.fire('click');
assert.equal(sheet.dataset.sheet, 'open', 'a short drag keeps the current state');
handle.fire('pointerdown', {clientY: 300});
handle.fire('pointermove', {clientY: 420});
handle.fire('pointerup', {clientY: 420});
handle.fire('click');
assert.equal(sheet.dataset.sheet, 'peek');

deck.setEnabled(false);
assert(handle.hidden);
assert.equal(sheet.dataset.sheet, undefined);
console.log('PASS: deck sheet folds by default, toggles by press, drag, Escape and outside press, and turns off outside the build step');

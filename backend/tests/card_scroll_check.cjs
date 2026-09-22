const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {runInNewContext} = require('node:vm');
const environment = {document: {documentElement: {dataset: {}}}, matchMedia: () => ({matches: false})};
const scroll = runInNewContext(readFileSync('frontend/assets/card-scroll.js', 'utf8') + '\nCardScroll;', environment);
let options;
const rail = {clientWidth: 300, scrollLeft: 0, scrollTo(value) { options = value; }};
const cards = [{offsetLeft: 100, offsetWidth: 100}, {offsetLeft: 300, offsetWidth: 100}];
assert.equal(scroll.nearest(rail, []), null);
assert.equal(scroll.nearest(rail, cards), cards[0]);
rail.scrollLeft = 200;
assert.equal(scroll.nearest(rail, cards), cards[1]);
rail.scrollLeft = 100;
assert.equal(scroll.nearest(rail, cards), cards[0]);
scroll.scrollTo(rail, cards[1], 'smooth');
assert.equal(options.left, 200);
assert.equal(options.behavior, 'smooth');
environment.document.documentElement.dataset.reduceMotion = 'true';
scroll.scrollTo(rail, cards[1], 'smooth');
assert.equal(options.behavior, 'instant');
environment.document.documentElement.dataset.reduceMotion = 'false';
environment.matchMedia = () => ({matches: true});
scroll.scrollTo(rail, cards[1], 'smooth');
assert.equal(options.behavior, 'instant');
console.log('PASS: shared card centering, nearest-card ties, empty results and both reduced-motion settings');

// Shared navigation keeps focus, scrolling and keyboard movement on the same card.
const surface = () => ({listeners: {}, addEventListener(name, handler) { this.listeners[name] = handler; }});
const previous = surface(), next = surface();
Object.assign(rail, surface());
cards.forEach(card => { card.contains = target => target === card; });
let visible = cards, active = cards[0], position;
const frames = [];
environment.requestAnimationFrame = callback => { frames.push(callback); return frames.length; };
environment.window = surface();
const go = scroll.bind(rail, {
  cards: () => visible, current: () => active,
  update: (card, index, total) => { active = card; position = [index, total]; }, previous, next
});
go(cards[0]);assert(previous.disabled);assert(!next.disabled);assert.deepEqual(position, [0, 2]);
next.listeners.click();assert.equal(active, cards[1]);assert(!previous.disabled);assert(next.disabled);
next.listeners.click();assert.equal(active, cards[1]);
previous.listeners.click();assert.equal(active, cards[0]);
let prevented = false;
rail.listeners.keydown({key:'ArrowRight',target:{closest:()=>null},preventDefault(){prevented=true;}});
assert(prevented);assert.equal(active, cards[1]);
rail.listeners.keydown({key:'ArrowLeft',target:{closest:()=>true}});
assert.equal(active, cards[1]);
rail.listeners.focusin({target:cards[0]});assert.equal(active, cards[0]);
rail.scrollLeft = 200;
rail.listeners.scroll();rail.listeners.scroll();assert.equal(frames.length, 1);
frames.shift()();assert.equal(active, cards[1]);assert(next.disabled);assert.deepEqual(position, [1, 2]);
environment.window.listeners.resize();frames.shift()();assert.equal(options.left, 200);
visible = [];
go(undefined);assert.equal(active, undefined);assert(previous.disabled);assert(next.disabled);assert.deepEqual(position, [-1, 0]);
next.listeners.click();assert.equal(active, undefined);
console.log('PASS: shared next/previous boundaries, keyboard controls, focus, scroll throttling, resize and empty decks');

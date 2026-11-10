const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {runInNewContext} = require('node:vm');
// Values built inside the vm context fail deepStrictEqual's prototype check, so compare as JSON.
const same = (actual, expected, message) => assert.equal(JSON.stringify(actual), JSON.stringify(expected), message);

// Minimal stand-ins for the DOM and Leaflet: enough to record what the map draws.
const card = (key, latitude, longitude, extra = {}) => ({
  dataset: {cardKey: key, latitude, longitude, ...extra},
  querySelector: () => ({textContent: ` ${key} 이름 `})
});
const layer = (kind, at, options = {}) => ({kind, at, options, handlers: {}, on(name, handler) { this.handlers[name] = handler; return this; }});
const drawn = [], views = [];
const map = {
  attributionControl: {setPrefix() {}},
  setView: (at, zoom) => views.push(['view', at, zoom]),
  fitBounds: points => views.push(['fit', points.length]),
  invalidateSize() {}
};
const L = {
  map: () => map,
  tileLayer: (url, options) => ({url, options, addTo() { L.tiles = this; return this; }}),
  layerGroup: () => ({addTo() { return this; }, clearLayers: () => drawn.splice(0), addLayer: item => drawn.push(item)}),
  marker: (at, options) => layer('marker', at, options),
  polyline: (at, options) => layer('line', at, options),
  divIcon: options => options
};
const form = {listeners: {}, events: [], addEventListener(name, handler) { this.listeners[name] = handler; }, dispatchEvent(event) { this.events.push(event); }};
const box = {hidden: true}, note = {hidden: true, textContent: ''};
const environment = {
  window: {L},
  document: {documentElement: {dataset: {}}, querySelector: selector => ({'#journey-builder': form, '#builder-map': box, '#builder-map-note': note})[selector]},
  CustomEvent: class { constructor(type, init) { this.type = type; this.detail = init.detail; } },
  ResizeObserver: class { observe() {} }
};
const builderMap = runInNewContext(readFileSync('frontend/assets/js/builder-map.js', 'utf8') + '\nBuilderMap;', environment);

const a = card('a', '37.38', '126.80'), b = card('b', '37.40', '126.70'), c = card('c', '37.35', '126.75');
const noPoint = card('n', '', ''), savedOnly = card('s', '37.3', '126.7', {savedOnly: 'true'}), bad = card('x', '120', '126.7');
assert.equal(builderMap.point(noPoint), null);
assert.equal(builderMap.point(savedOnly), null);
assert.equal(builderMap.point(bad), null);
same(builderMap.point(a), [37.38, 126.8]);

const send = detail => form.listeners['deck-map']({detail});
const pins = () => drawn.filter(item => item.kind === 'marker');
send({step: 'start', selected: [a], active: a});
assert(box.hidden, 'start step keeps the map hidden');
assert.equal(drawn.length, 0);

// Entering the build step with a start card that has no point still shows Siheung so picks can appear.
send({step: 'build', selected: [noPoint], active: b});
assert(!box.hidden);
assert(L.tiles.url.startsWith('https://tile.openstreetmap.org/'));
assert.equal(L.tiles.options.referrerPolicy, 'strict-origin-when-cross-origin');
assert.match(L.tiles.options.attribution, /OpenStreetMap/);
assert.equal(pins().length, 0);
same(views.at(-1), ['view', [37.38, 126.8], 12]);
assert(!note.hidden);
assert.match(note.textContent, /1장/);

// Every pick adds a numbered pin, the newest one marked, and extends the line in visit order.
send({step: 'build', selected: [noPoint, b], active: b});
same(pins().map(pin => pin.options.icon.html), ['<span>2</span>']);
assert.match(pins()[0].options.icon.className, /is-new/);
assert.match(pins()[0].options.icon.className, /is-current/);
same(views.at(-1), ['view', [37.4, 126.7], 14]);
send({step: 'build', selected: [noPoint, b, a], active: c});
same(pins().map(pin => pin.options.icon.html), ['<span>2</span>', '<span>3</span>'], 'pins keep visit numbers and skip cards without points');
same(pins().map(pin => /is-new/.test(pin.options.icon.className)), [false, true]);
same(drawn.find(item => item.kind === 'line').at, [[37.4, 126.7], [37.38, 126.8]]);
assert.equal(drawn.find(item => item.kind === 'line').options.interactive, false);
same(views.at(-1), ['fit', 2]);

// Browsing other cards does not reframe the map or replay the pin animation.
views.length = 0;
send({step: 'build', selected: [noPoint, b, a], active: a});
same(views, []);
assert(pins().every(pin => !/is-new/.test(pin.options.icon.className)));
assert.match(pins()[1].options.icon.className, /is-current/);
pins()[0].handlers.click();
assert.equal(form.events.at(-1).type, 'map-pick');
assert.equal(form.events.at(-1).detail, 'b');

// Review shows the final selection without deck links; nothing placeable hides the map there.
send({step: 'review', selected: [b, a, savedOnly], active: null});
assert.equal(pins().length, 2);
assert(pins().every(pin => !pin.options.interactive && !pin.handlers.click));
same(views.at(-1), ['fit', 2]);
assert.match(note.textContent, /1장/);
send({step: 'review', selected: [noPoint], active: null});
assert(box.hidden && note.hidden, 'nothing to draw hides the review map');
console.log('PASS: builder map adds a numbered pin per pick in visit order, skips missing points, frames the final selection and sends pin picks');

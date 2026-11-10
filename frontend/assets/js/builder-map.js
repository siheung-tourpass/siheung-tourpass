// Trip builder map: draws the picked cards that journey-builder.js publishes as `deck-map` events.
// Each pick adds a numbered pin and extends the visit-order line (a straight line, not a route).
// A pin click is sent back as `map-pick` with the card key.
const BuilderMap = (() => {
  const SIHEUNG = [37.38, 126.8];

  function point(card) {
    if (!card || card.dataset.savedOnly === 'true' || !card.dataset.latitude || !card.dataset.longitude) return null;
    const latitude = Number(card.dataset.latitude), longitude = Number(card.dataset.longitude);
    return Number.isFinite(latitude) && Math.abs(latitude) <= 90 && Number.isFinite(longitude) && Math.abs(longitude) <= 180 ? [latitude, longitude] : null;
  }

  /** Numbered pins for picked cards with coordinates; `missing` counts picked cards the map cannot place. */
  function model({ selected, active }) {
    const all = selected.map((card, index) => ({ card, number: index + 1, at: point(card), current: card === active }));
    const pins = all.filter(pin => pin.at);
    return { pins, route: pins.map(pin => pin.at), missing: all.length - pins.length };
  }

  function name(card) {
    return card.querySelector('h3')?.textContent.trim() || '';
  }

  function attach(form, box, note, L) {
    let map, layer, framed = '', shown = new Set(), route = [], size = '';

    function create() {
      map = L.map(box, { scrollWheelZoom: false, zoomSnap: 0.5 });
      map.attributionControl.setPrefix(false);
      L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        referrerPolicy: 'strict-origin-when-cross-origin',
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>'
      }).addTo(map);
      layer = L.layerGroup().addTo(map);
      map.setView(SIHEUNG, 12);
    }

    // Leaflet caches its size; the box changes with the sheet and step, so re-measure before framing.
    function measure() {
      const next = `${box.clientWidth}x${box.clientHeight}`;
      if (next === size) return false;
      size = next;
      map.invalidateSize({ animate: false });
      return true;
    }

    // Fits every pin; the padding shrinks with the box so the folded thumbnail still shows them all.
    // Framing is instant: an animated fit finishes later and would undo a reframe after a resize.
    function frame() {
      const pad = Math.min(28, (box.clientHeight || 112) / 4);
      if (!route.length) map.setView(SIHEUNG, 12, { animate: false });
      else if (route.length === 1) map.setView(route[0], 14, { animate: false });
      else map.fitBounds(route, { padding: [pad, pad], maxZoom: 15, animate: false });
    }

    form.addEventListener('deck-map', ({ detail }) => {
      const drawn = model(detail);
      const { pins, missing } = drawn;
      route = drawn.route;
      const building = detail.step === 'build';
      box.hidden = detail.step === 'start' || !building && !pins.length;
      if (note) {
        note.hidden = box.hidden || !missing;
        note.textContent = `좌표가 없는 카드 ${missing}장은 지도에 표시하지 않아요.`;
      }
      if (box.hidden) return;
      const first = !map;
      if (first) create();
      layer.clearLayers();
      if (route.length > 1) layer.addLayer(L.polyline(route, { className: 'map-route', interactive: false }));
      const keys = pins.map(pin => pin.card.dataset.cardKey);
      pins.forEach(({ card, number, at, current }) => {
        const fresh = building && !first && !shown.has(card.dataset.cardKey);
        const className = `map-pin${current ? ' is-current' : ''}${fresh ? ' is-new' : ''}`;
        const icon = L.divIcon({ className, html: `<span>${number}</span>`, iconSize: [28, 28] });
        const marker = L.marker(at, { icon, title: `${number}. ${name(card)}`, alt: `${number}. ${name(card)}`, keyboard: building, interactive: building, zIndexOffset: 1000 });
        if (building) marker.on('click', () => form.dispatchEvent(new CustomEvent('map-pick', { detail: card.dataset.cardKey })));
        layer.addLayer(marker);
      });
      shown = new Set(keys);
      const resized = measure();
      const key = `${detail.step}|${keys.join(',')}`;
      if (key === framed && !resized) return;
      framed = key;
      frame();
    });
    new ResizeObserver(() => {
      if (map && !box.hidden && measure()) frame();
    }).observe(box);
  }

  const form = typeof document === 'undefined' ? null : document.querySelector('#journey-builder');
  const box = form && document.querySelector('#builder-map');
  if (box && window.L) attach(form, box, document.querySelector('#builder-map-note'), window.L);
  return { model, point };
})();

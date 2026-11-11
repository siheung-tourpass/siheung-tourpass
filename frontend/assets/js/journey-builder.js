(() => {
  const form = document.querySelector('#journey-builder');
  if (!form) return;
  const filters = document.querySelector('#journey-filters');
  const browse = form.querySelector('#browse-cards');
  const cards = [...browse.children];
  const byId = new Map(cards.map(card => [card.dataset.cardKey, card]));
  const controls = new Map(cards.map(card => [card, {
    name: card.querySelector('h3').textContent,
    searchText: card.textContent.toLocaleLowerCase('ko'),
    checkbox: card.querySelector('input[type=checkbox]'),
    label: card.querySelector('.place-pick span'),
    toggle: card.querySelector('.card-select-toggle'),
    reason: card.querySelector('.result-reason'),
    distance: card.querySelector('.card-distance'),
    front: card.querySelector('.card-front'),
    back: card.querySelector('.card-back'),
    details: card.querySelector('.card-detail-content'),
    flipFront: card.querySelector('[data-flip=front]'),
    flipBack: card.querySelector('[data-flip=back]')
  }]));
  let selected = readSelection((form.dataset.selectionOrder || '').split(','));
  const list = form.querySelector('#selected-places');
  const selectionTemplate = form.querySelector('#selected-place-template');
  const status = document.querySelector('#builder-status');
  const search = document.querySelector('#card-search');
  const date = document.querySelector('#card-date');
  const typeButtons = [...form.querySelectorAll('[data-card-type]')];
  const stage = form.querySelector('#deck-stage');
  const summary = form.querySelector('#deck-summary');
  const sheet = DeckSheet.bind(summary, { handle: summary.querySelector('#deck-sheet-handle'), preview: summary.querySelector('#selection-summary'), label: summary.querySelector('.deck-sheet-label') });
  const title = document.querySelector('#builder-page-title');
  const backStep = form.querySelector('#builder-back');
  const nextStep = form.querySelector('#builder-next');
  let step = 'build';
  const previous = form.querySelector('#deck-previous');
  const next = form.querySelector('#deck-next');
  const feedback = form.querySelector('#deck-feedback');
  let filteredCards = [];
  let activeCard;
  let drag;
  let suppressClick = false;
  let kind = 'all';
  let request;
  let rankedCards = cards;
  let resortTimer = null;
  let resortPending = false;
  // Short enough to overlap the pick animation, so the deck never sits still waiting.
  const RESORT_DELAY = 200;

  function point(card) {
    if (!card || card.dataset.savedOnly === 'true' || !card.dataset.latitude || !card.dataset.longitude) return;
    const latitude = Number(card.dataset.latitude), longitude = Number(card.dataset.longitude);
    if (Number.isFinite(latitude) && Math.abs(latitude) <= 90 && Number.isFinite(longitude) && Math.abs(longitude) <= 180) return { latitude, longitude };
  }

  function distance(from, to) {
    if (!from || !to) return Infinity;
    const radians = degrees => degrees * Math.PI / 180;
    const latitude = radians(to.latitude - from.latitude), longitude = radians(to.longitude - from.longitude);
    const value = Math.sin(latitude / 2) ** 2 + Math.cos(radians(from.latitude)) * Math.cos(radians(to.latitude)) * Math.sin(longitude / 2) ** 2;
    return 6371 * 2 * Math.asin(Math.sqrt(Math.min(1, value)));
  }

  function arrangeCards() {
    const selectedCards = [...selected].map(id => byId.get(id));
    const anchor = selectedCards.at(-1);
    const origin = point(anchor);
    const region = anchor?.dataset.savedOnly !== 'true' ? anchor?.dataset.regionId : '';
    const available = rankedCards.filter(card => !selected.has(card.dataset.cardKey));
    const distances = new Map(cards.map(card => [card, distance(origin, point(card))]));
    available.sort((a, b) => origin ? distances.get(a) - distances.get(b)
      : Number(!!region && b.dataset.regionId === region) - Number(!!region && a.dataset.regionId === region));
    cards.forEach(card => {
      const label = controls.get(card).distance;
      const km = distances.get(card);
      label.hidden = !anchor || card === anchor;
      label.textContent = Number.isFinite(km) ? `직선 ${km < 1 ? `${Math.round(km * 1000)}m` : `${km.toFixed(1)}km`}` : '거리 미확인';
      if (Number.isFinite(km)) card.dataset.distanceKm = String(km);
      else delete card.dataset.distanceKm;
    });
    const focused = document.activeElement;
    browse.append(...(anchor ? [anchor] : []), ...available, ...selectedCards.filter(card => card !== anchor));
    if (browse.contains(focused)) focused.focus({ preventScroll: true });
    document.querySelector('#builder-browse-title').textContent = anchor ? '다음 후보' : '추천 카드';
    document.querySelector('#builder-context').textContent = !anchor
      ? '취향에 맞는 추천 관광지부터 보여드려요. 카드를 담으면 그 카드에서 가까운 후보부터 보여드려요.'
      : origin ? `${controls.get(anchor).name}에서 가까운 순서예요 · 대표 지점 간 직선거리로, 실제 이동거리와 달라요.`
      : `${controls.get(anchor).name}의 좌표가 없어 같은 지역 카드부터 보여드려요. 거리는 미확인이에요.`;
    filterCards();
  }

  function reducedMotion() {
    return document.documentElement.dataset.reduceMotion === 'true' || matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  // While the pick flies into the sheet, the deck slides out, re-sorts from the newest pick and
  // slides back in on the nearest candidate with the new basis highlighted.
  function scheduleResort() {
    resortPending = true;
    clearTimeout(resortTimer);
    resortTimer = setTimeout(resort, RESORT_DELAY);
  }
  function cancelResort() {
    resortPending = false;
    clearTimeout(resortTimer);
    resortTimer = null;
  }
  // Changing what the deck shows settles a pending resort at once, so the deck never moves under the user.
  function refreshDeck() {
    if (resortPending) {
      cancelResort();
      arrangeCards();
    } else filterCards();
  }
  async function resort() {
    resortTimer = null;
    // Someone reading the back of the current card keeps it; the resort waits until it is turned back.
    if (activeCard?.classList.contains('is-flipped')) { scheduleResort(); return; }
    const reduced = reducedMotion();
    const out = stage.animate([{ opacity: 1, transform: 'none' }, { opacity: 0, transform: reduced ? 'none' : 'translateX(-32px)' }],
      { duration: reduced ? 100 : 200, easing: 'ease-in', fill: 'forwards' });
    await out.finished.catch(() => {});
    if (resortTimer || !resortPending || step !== 'build') { out.cancel(); return; }
    resortPending = false;
    arrangeCards();
    const nearest = filteredCards.find(card => !selected.has(card.dataset.cardKey));
    if (nearest) activate(nearest);
    out.cancel();
    stage.animate([{ opacity: 0, transform: reduced ? 'none' : 'translateX(32px)' }, { opacity: 1, transform: 'none' }],
      { duration: reduced ? 100 : 280, easing: 'cubic-bezier(0.16, 1, 0.3, 1)' });
    const anchor = byId.get([...selected].at(-1));
    feedback.textContent = anchor
      ? `${controls.get(anchor).name}에서 가까운 순으로 다시 놓았어요${nearest ? ` · 가장 가까운 후보는 ${controls.get(nearest).name}` : ''}`
      : '담은 카드가 없어 추천 순서로 돌아왔어요.';
    const context = document.querySelector('#builder-context');
    context.classList.remove('is-updated');
    void context.offsetWidth;
    context.classList.add('is-updated');
  }

  /** The picked card pops and a photo dot flies into the sheet's map; the count bumps when it lands. */
  function flyToSheet(card) {
    const count = summary.querySelector('#sheet-count');
    const bump = () => { count.classList.remove('is-bumped'); void count.offsetWidth; count.classList.add('is-bumped'); };
    if (reducedMotion()) { bump(); return; }
    card.animate([{ scale: 1 }, { scale: 1.04 }, { scale: 1 }], { duration: 280, easing: 'ease-out' });
    const from = controls.get(card).toggle.getBoundingClientRect();
    const box = summary.querySelector('#builder-map');
    const to = (box.hidden ? count : box).getBoundingClientRect();
    if (!to.width) { bump(); return; }
    const dot = document.createElement('span');
    dot.className = 'pick-dot';
    const photo = card.querySelector('.card-background');
    if (photo) dot.style.backgroundImage = `url("${photo.currentSrc || photo.src}")`;
    const size = 56;
    dot.style.left = `${from.left + from.width / 2 - size / 2}px`;
    dot.style.top = `${from.top + from.height / 2 - size / 2}px`;
    document.body.append(dot);
    const dx = to.left + to.width / 2 - (from.left + from.width / 2), dy = to.top + to.height / 2 - (from.top + from.height / 2);
    dot.animate([
      { transform: 'translate(0, 0) scale(.6)', opacity: 0 },
      { transform: 'translate(0, -12px) scale(1)', opacity: 1, offset: .2 },
      { transform: `translate(${dx * .55}px, ${dy * .35 - 48}px) scale(.8)`, opacity: 1, offset: .6 },
      { transform: `translate(${dx}px, ${dy}px) scale(.3)`, opacity: .4 }
    ], { duration: 560, easing: 'cubic-bezier(.45, 0, .2, 1)' }).finished.finally(() => { dot.remove(); bump(); });
  }

  function setStatus(state, text) {
    status.dataset.state = state;
    status.textContent = text;
  }

  function filterCards() {
    const region = filters.elements.regionId.value;
    const eventDate = date.value;
    const term = search.value.trim().toLocaleLowerCase('ko');
    filteredCards = [...browse.children].filter(card => {
      const wrongRegion = card.dataset.cardKind !== 'event' && card.dataset.savedOnly !== 'true' && region && card.dataset.regionId !== region;
      const wrongDate = card.dataset.cardKind === 'event' && card.dataset.savedOnly !== 'true' && eventDate &&
        (eventDate < card.dataset.startDate || eventDate > card.dataset.endDate);
      card.hidden = !!(wrongRegion || wrongDate || (kind !== 'all' && kind !== card.dataset.cardKind) || !controls.get(card).searchText.includes(term));
      return !card.hidden;
    });
    stage.hidden = filteredCards.length === 0;
    // With cards already picked (a product start, a draft) the deck opens on the nearest card not yet picked.
    activate(filteredCards.includes(activeCard) ? activeCard : filteredCards.find(card => !selected.has(card.dataset.cardKey)) || filteredCards[0]);
    form.querySelector('#builder-empty').hidden = filteredCards.length > 0;
    form.querySelector('#card-count').textContent = `${filteredCards.length}장 찾음`;
    emitMap();
  }

  /** Publishes the picked cards in visit order for builder-map.js. */
  function emitMap() {
    form.dispatchEvent(new CustomEvent('deck-map', { detail: {
      step,
      selected: [...selected].map(id => byId.get(id)).filter(Boolean),
      active: step === 'review' ? null : activeCard
    } }));
  }

  function updateActiveCard(card, index, total) {
    if (card !== activeCard) {
      if (activeCard) {
        activeCard.classList.remove('is-current');
        activeCard.removeAttribute('aria-current');
        setFlipped(activeCard, false);
      }
      activeCard = card;
      if (card) {
        card.classList.add('is-current');
        card.setAttribute('aria-current', 'true');
      }
      emitMap();
    }
    form.querySelector('#deck-position').textContent = activeCard ? `${index + 1} / ${total}` : '0 / 0';
    updateCardDepth();
    stage.setAttribute('aria-label', activeCard ? `${controls.get(activeCard).name} · 좌우로 밀어 탐색` : '여행 카드 덱');
  }

  function updateCardDepth() {
    const center = browse.scrollLeft + browse.clientWidth / 2;
    filteredCards.forEach(card => {
      const distance = Math.max(-1, Math.min(1, (card.offsetLeft + card.offsetWidth / 2 - center) / card.offsetWidth));
      card.style.setProperty('--card-yaw', `${-distance * 16}deg`);
      card.style.setProperty('--card-depth', `${-Math.abs(distance) * 64}px`);
      card.style.setProperty('--card-scale', String(1 - Math.abs(distance) * .06));
    });
  }

  const activate = CardScroll.bind(browse, {
    cards: () => filteredCards, current: () => activeCard, update: updateActiveCard,
    previous, next, keyboard: stage
  });

  form.addEventListener('map-pick', ({ detail }) => {
    const card = byId.get(detail);
    if (!filteredCards.includes(card) || step === 'review') return;
    sheet.close();
    // Page scroll first: scrolling an ancestor would cut the deck's smooth scroll short.
    stage.scrollIntoView({ block: 'nearest' });
    activate(card, 'smooth');
  });

  browse.addEventListener('wheel', event => {
    if (event.target.closest('.card-back') || Math.abs(event.deltaX) >= Math.abs(event.deltaY) || event.shiftKey) return;
    if ((browse.scrollLeft <= 1 && event.deltaY < 0) || (browse.scrollLeft >= browse.scrollWidth - browse.clientWidth - 1 && event.deltaY > 0)) return;
    event.preventDefault();
    browse.scrollBy({ left: event.deltaY, behavior: 'instant' });
  }, { passive: false });
  browse.addEventListener('pointerdown', event => {
    if (event.pointerType === 'touch' || event.button !== 0 || event.target.closest('input, button:not(.card-select-toggle), a, summary, label, .card-back')) return;
    suppressClick = false;
    drag = { x: event.clientX, scroll: browse.scrollLeft, moved: false };
  });
  browse.addEventListener('pointermove', event => {
    if (!drag) return;
    if (!drag.moved && Math.abs(event.clientX - drag.x) < 6) return;
    drag.moved = true;
    browse.setPointerCapture(event.pointerId);
    browse.classList.add('is-dragging');
    event.preventDefault();
    browse.scrollLeft = drag.scroll + drag.x - event.clientX;
  });
  function endDrag() {
    if (!drag) return;
    suppressClick = drag.moved;
    drag = null;
    browse.classList.remove('is-dragging');
    activate(CardScroll.nearest(browse, filteredCards), 'smooth');
  }
  browse.addEventListener('pointerup', endDrag);
  browse.addEventListener('pointercancel', endDrag);
  browse.addEventListener('click', event => {
    if (!suppressClick) return;
    suppressClick = false;
    event.preventDefault();
    event.stopImmediatePropagation();
  }, true);
  function showStep(value, focus = true) {
    step = value;
    form.dataset.step = step;
    title.textContent = { build: '덱 만들기', review: '여행 확인' }[step];
    const hint = document.querySelector('#builder-step-hint');
    hint.hidden = false;
    hint.textContent = { build: '추천 카드를 둘러보고 담은 뒤, 방문할 순서를 정해 보세요.', review: '여행 순서를 확인하고 이름을 붙여 저장하세요.' }[step];
    document.querySelectorAll('#builder-steps [data-step]').forEach(item => {
      if (item.dataset.step === step) item.setAttribute('aria-current', 'step');
      else item.removeAttribute('aria-current');
    });
    filters.hidden = step === 'review';
    status.hidden = step === 'review';
    form.querySelector('.builder-catalog').hidden = step === 'review';
    sheet.setEnabled(step === 'build');
    form.querySelector('#journey-details').hidden = step !== 'review';
    const actions = form.querySelector('.deck-summary-actions');
    if (actions) actions.hidden = step !== 'review';
    renderSelection();
    if (focus) { title.focus(); title.scrollIntoView({ block: 'start' }); }
    recommend();
  }

  function goStep(value) {
    history.pushState(null, '', `#${value}`);
    showStep(value);
  }
  nextStep.addEventListener('click', () => goStep('review'));
  backStep.addEventListener('click', () => goStep('build'));
  window.addEventListener('popstate', () => {
    const value = location.hash.slice(1);
    if (['build', 'review'].includes(value)) showStep(value);
  });
  form.addEventListener('invalid', () => {
    if (step !== 'review') goStep('review');
  }, true);
  form.addEventListener('submit', event => {
    if (step !== 'review') { event.preventDefault(); goStep('review'); }
  });
  function closeSearch(event) {
    event.preventDefault();
    stage.focus({ preventScroll: true });
  }
  search.addEventListener('keydown', event => {
    if (event.key === 'Enter') closeSearch(event);
  });

  search.addEventListener('input', refreshDeck);
  date.addEventListener('input', refreshDeck);
  function setKind(value) {
    kind = value;
    typeButtons.forEach(item => item.setAttribute('aria-pressed', String(item.dataset.cardType === kind)));
    refreshDeck();
  }
  typeButtons.forEach(control => control.addEventListener('click', () => setKind(control.dataset.cardType)));
  // Same rule as the server: the product covering the most selected cards, the cheaper one on ties.
  function renderOffer() {
    const preview = form.querySelector('#trip-offer-preview');
    const purchase = form.querySelector('#purchase-journey');
    if (!preview || !purchase) return;
    let best = null;
    form.querySelectorAll('#pass-offers li').forEach(offer => {
      const count = offer.dataset.cards.split(' ').filter(key => selected.has(key)).length;
      const price = Number(offer.dataset.price);
      if (count && (!best || count > best.count || count === best.count && price < best.price)) best = { offer, count, price };
    });
    preview.hidden = !best;
    purchase.hidden = !best;
    const confirm = form.querySelector('#confirm-journey');
    confirm.hidden = false;
    confirm.classList.toggle('secondary', Boolean(best));
    confirm.textContent = best ? '구매 없이 여행 확정' : '여행 확정';
    if (!best) return;
    preview.querySelector('[data-offer=name]').textContent = best.offer.dataset.name;
    preview.querySelector('[data-offer=summary]').textContent = `담은 장소 중 ${best.count}곳에서 혜택을 받을 수 있어요. ${best.price.toLocaleString('ko-KR')}원`;
  }

  /** Redraws the picked list and card states; `arrange: false` leaves the deck order for a pending resort. */
  function renderSelection(focus, arrange = true) {
    list.replaceChildren();
    const ids = [...selected];
    ids.forEach((id, index) => {
      const card = byId.get(id);
      const name = controls.get(card).name;
      const item = selectionTemplate.content.firstElementChild.cloneNode(true);
      item.dataset.selectedId = id;
      const thumb = item.querySelector('.selection-thumb');
      const photo = card.querySelector('.card-background');
      const kind = card.querySelector('.card-kind');
      if (photo) {
        const image = photo.cloneNode(false);
        image.className = 'selection-image';
        image.alt = '';
        image.loading = 'eager';
        thumb.append(image);
      } else if (kind?.querySelector('svg')) thumb.append(kind.querySelector('svg').cloneNode(true));
      item.querySelector('.selection-order').textContent = String(index + 1);
      const title = item.querySelector('.selection-name');
      title.textContent = name;
      title.title = name;
      item.querySelector('.selection-meta').textContent = [kind?.textContent.trim(), index === 0 ? '시작' : ''].filter(Boolean).join(' · ');
      const km = index ? distance(point(byId.get(ids[index - 1])), point(card)) : Infinity;
      const leg = item.querySelector('.selection-leg');
      leg.hidden = !Number.isFinite(km);
      leg.textContent = Number.isFinite(km) ? `직선 ${km < 1 ? `${Math.round(km * 1000)}m` : `${km.toFixed(1)}km`}` : '';
      item.querySelector('input').value = id;
      item.querySelectorAll('button').forEach(button => {
        const action = button.dataset.action;
        button.setAttribute('aria-label', `${index + 1}번 ${name} ${action === '빼기' ? '선택 해제' : action}`);
        button.disabled = action === '위로' && index === 0 || action === '아래로' && index === ids.length - 1;
      });
      list.append(item);
    });
    cards.forEach(card => {
      const checked = isSelected(card);
      const { checkbox, label, toggle, name } = controls.get(card);
      checkbox.checked = selected.has(card.dataset.cardKey);
      card.classList.toggle('is-selected', checked);
      label.textContent = checked ? '✓ 내 여행에 담김' : '여행에 담기';
      toggle.setAttribute('aria-label', `${name} ${checked ? '여행에서 빼기' : '여행에 담기'}`);
      toggle.setAttribute('aria-pressed', String(checked));
    });
    form.querySelector('#selection-count').textContent = `${selected.size}장`;
    form.querySelector('#sheet-count').textContent = `${selected.size}장`;
    form.dataset.picked = String(selected.size > 0);
    form.querySelector('#selection-empty').hidden = selected.size > 0;
    const save = form.querySelector('#confirm-journey');
    if (save) save.disabled = selected.size === 0;
    renderOffer();
    nextStep.hidden = step !== 'build';
    backStep.hidden = step !== 'review';
    if (arrange) {
      cancelResort();
      arrangeCards();
    } else emitMap();
    if (focus?.id) {
      const item = [...list.children].find(item => item.dataset.selectedId === focus.id);
      const controls = [...item.querySelectorAll('button')];
      (controls.find(control => control.dataset.action === focus.text && !control.disabled) || controls.find(control => !control.disabled)).focus();
    }
  }

  list.addEventListener('click', event => {
    const control = event.target.closest('button');
    if (!control) return;
    const id = control.closest('li').dataset.selectedId;
    const ids = [...selected];
    const index = ids.indexOf(id);
    if (control.dataset.action === '빼기') {
      selected.delete(id);
      renderSelection({ id: ids[index + 1] || ids[index - 1], text: '빼기' });
      if (!selected.size) {
        const card = byId.get(id);
        (step === 'review' || card.hidden ? form.querySelector('#selection-summary') : controls.get(card).toggle).focus();
      }
    } else {
      const to = index + (control.dataset.action === '위로' ? -1 : 1);
      [ids[index], ids[to]] = [ids[to], ids[index]];
      selected = new Set(ids);
      renderSelection({ id, text: control.dataset.action });
    }
  });

  function readSelection(order = selected) {
    return new Set([...order, ...byId.keys()].filter(id => controls.get(byId.get(id))?.checkbox.checked));
  }
  function isSelected(card) {
    return selected.has(card.dataset.cardKey);
  }
  function selectCard(card, checked) {
    const id = card.dataset.cardKey;
    if (checked) selected.add(id);
    else selected.delete(id);
    renderSelection(undefined, false);
    if (checked) flyToSheet(card);
    scheduleResort();
    feedback.textContent = `${controls.get(card).name} ${checked ? '여행에 담았어요' : '여행에서 뺐어요'} · 총 ${selected.size}장`;
  }
  browse.addEventListener('change', event => {
    if (event.target.matches('input[type=checkbox]')) selectCard(event.target.closest('.builder-card'), event.target.checked);
  });

  function showResults(items) {
    const results = new Map(items.map(item => [`place:${item.place.id}`, item]));
    cards.forEach(card => {
      const match = results.get(card.dataset.cardKey);
      const { reason } = controls.get(card);
      if (!reason) return;
      reason.textContent = match ? `찾은 이유: ${match.explanation}` : '';
      reason.hidden = !match;
      card.classList.toggle('is-match', !!match);
    });
    const ranked = [...results.keys()].map(id => byId.get(id)).filter(Boolean);
    rankedCards = [...ranked, ...cards.filter(card => !results.has(card.dataset.cardKey))];
    if (!selected.size) activate(null);
    arrangeCards();
  }

  async function recommend() {
    request?.abort();
    const current = new AbortController();
    request = current;
    setStatus('loading', '취향에 맞는 카드를 찾고 있어요…');
    filters.setAttribute('aria-busy', 'true');
    try {
      const query = new URLSearchParams([...new FormData(filters)].filter(([name, value]) => ['themeId', 'regionId', 'companionId'].includes(name) && value !== ''));
      const response = await fetch(`${filters.dataset.recommendationsUrl}?${query}`, { signal: current.signal, headers: { Accept: 'application/json' } });
      if (!response.ok) throw new Error('recommendations');
      const data = await response.json();
      if (request !== current) return;
      showResults(data.items);
      setStatus(!data.items.length && filters.elements.themeId.value ? 'empty' : 'success', selected.size
        ? '담은 카드 주변에서 다음 카드를 골라 보세요.'
        : data.items.length ? `추천 관광지 ${data.items.length}곳을 앞에 두었어요. 마음에 드는 카드를 담아 보세요.` : '조건에 맞는 추천 관광지가 없어요. 조건을 바꾸거나 카드에서 직접 골라 보세요.');
    } catch (error) {
      if (error.name === 'AbortError' || request !== current) return;
      showResults([]);
      setStatus('error', '추천을 불러오지 못했어요. 여행 취향을 다시 선택하거나 카드에서 직접 골라 주세요.');
    } finally {
      if (request === current) filters.removeAttribute('aria-busy');
    }
  }

  filters.addEventListener('change', event => {
    if (!event.target.matches('[name=themeId], [name=regionId], [name=companionId]')) return;
    activate(null);
    refreshDeck();
    recommend();
  });
  filters.addEventListener('submit', closeSearch);
  filters.addEventListener('reset', () => { setTimeout(() => { setKind('all'); showStep(step, false); }, 0); });
  window.addEventListener('pageshow', () => {
    selected = readSelection();
    renderSelection();
  });
  function setFlipped(card, flipped) {
    const { front, back, details } = controls.get(card);
    if (!front || !back) return;
    card.classList.toggle('is-flipped', flipped);
    front.inert = flipped;
    back.inert = !flipped;
    front.setAttribute('aria-hidden', String(flipped));
    back.setAttribute('aria-hidden', String(!flipped));
    if (!flipped) details.scrollTop = 0;
  }
  document.querySelector('#builder-filters').disabled = false;
  form.querySelector('.card-tools').hidden = false;
  form.querySelector('.deck-navigation').hidden = false;
  cards.forEach(card => {
    const { back, checkbox } = controls.get(card);
    checkbox.removeAttribute('name');
    back.open = true;
    back.querySelector('summary').hidden = true;
    card.querySelectorAll('[data-flip], .card-select-toggle').forEach(control => { control.hidden = false; });
    setFlipped(card, false);
  });
  browse.addEventListener('click', event => {
    const control = event.target.closest('[data-flip], .card-select-toggle');
    if (!control) return;
    const card = control.closest('.builder-card');
    if (control.dataset.flip) {
      const flipped = control.dataset.flip === 'back';
      setFlipped(card, flipped);
      controls.get(card)[flipped ? 'flipFront' : 'flipBack'].focus({ preventScroll: true });
    } else {
      selectCard(card, !isSelected(card));
    }
  });
  document.body.classList.add('is-deck-mode');
  document.querySelector('#builder-steps').hidden = false;
  history.replaceState(null, '', `#${step}`);
  showStep(step, false);
})();

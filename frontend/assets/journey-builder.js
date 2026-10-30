(() => {
  const form = document.querySelector('#journey-builder');
  if (!form) return;
  const filters = document.querySelector('#journey-filters');
  const browse = form.querySelector('#browse-cards');
  const cards = [...browse.children];
  const byId = new Map(cards.map(card => [card.dataset.cardKey, card]));
  const controls = new Map(cards.map(card => [card, {
    name: card.querySelector('h3').textContent,
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
  const bar = form.querySelector('.builder-selection-bar');
  const status = document.querySelector('#builder-status');
  const search = document.querySelector('#card-search');
  const date = document.querySelector('#card-date');
  const searchPanel = filters.querySelector('#builder-search-panel');
  const startRegion = filters.querySelector('#start-region');
  const startDate = filters.querySelector('#start-event-date');
  let startMode = 'theme';
  const typeButtons = [...form.querySelectorAll('[data-card-type]')];
  const searchText = new Map(cards.map(card => [card, card.textContent.toLocaleLowerCase('ko')]));
  const stage = form.querySelector('#deck-stage');
  const summary = form.querySelector('#deck-summary');
  const title = document.querySelector('#builder-page-title');
  const backStep = form.querySelector('#builder-back');
  const nextStep = form.querySelector('#builder-next');
  let step = selected.size || form.querySelector('[name=id]') ? 'build' : 'start';
  const previous = form.querySelector('#deck-previous');
  const next = form.querySelector('#deck-next');
  const feedback = form.querySelector('#deck-feedback');
  let filteredCards = [];
  let activeCard;
  let drag;
  let suppressClick = false;
  let kind = 'all';
  let request;
  let rankedCards = [...cards];
  let suggestedKeys;
  let reveal;

  function revealSuggestion() {
    reveal?.cancel();
    if (step !== 'start' || !activeCard || form.dataset.suggesting === 'true') return;
    const reduced = document.documentElement.dataset.reduceMotion === 'true' || matchMedia('(prefers-reduced-motion: reduce)').matches;
    reveal = stage.animate(reduced ? [{ opacity: 0 }, { opacity: 1 }]
      : [{ opacity: 0, transform: 'translateY(16px) scale(.92)' }, { opacity: 1, transform: 'translateY(0) scale(1)' }],
    { duration: reduced ? 100 : 360, easing: 'cubic-bezier(0.16, 1, 0.3, 1)' });
  }

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
    const anchor = step === 'start' ? null : byId.get([...selected].at(-1));
    const origin = point(anchor);
    const region = anchor?.dataset.savedOnly !== 'true' ? anchor?.dataset.regionId : '';
    const available = rankedCards.filter(card => !selected.has(card.dataset.cardKey));
    const distances = new Map(cards.map(card => [card, distance(origin, point(card))]));
    available.sort((a, b) => origin ? (distances.get(a) === distances.get(b) ? 0 : distances.get(a) - distances.get(b))
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
    browse.append(...(anchor ? [anchor] : []), ...available, ...[...selected].map(id => byId.get(id)).filter(card => card !== anchor));
    if (browse.contains(focused)) focused.focus({ preventScroll: true });
    document.querySelector('#builder-browse-title').textContent = anchor ? '다음 후보' : '시작할 카드';
    document.querySelector('#builder-context').textContent = !anchor
      ? step === 'start' ? '좌우로 넘겨 보고, + 버튼으로 선택하세요.' : '카드를 담으면 가까운 후보부터 보여드려요.'
      : origin ? `${controls.get(anchor).name}에서 가까운 순서예요 · 대표 지점 간 직선거리로, 실제 이동거리와 달라요.`
      : `${controls.get(anchor).name}의 좌표가 없어 같은 지역 카드부터 보여드려요. 거리는 미확인이에요.`;
    filterCards();
  }

  function setStatus(state, text) {
    status.dataset.state = state;
    status.textContent = text;
  }

  function filterCards() {
    const direct = step !== 'start' || startMode === 'direct';
    const region = direct ? filters.elements.regionId.value : startMode === 'region' ? startRegion.value : '';
    const eventDate = direct ? date.value : startMode === 'event' ? startDate.value : '';
    const cardKind = direct ? kind : startMode === 'event' ? 'event' : 'place';
    const term = direct ? search.value.trim().toLocaleLowerCase('ko') : '';
    filteredCards = [...browse.children].filter(card => {
      const wrongRegion = card.dataset.cardKind !== 'event' && card.dataset.savedOnly !== 'true' && region && card.dataset.regionId !== region;
      const wrongDate = card.dataset.cardKind === 'event' && card.dataset.savedOnly !== 'true' && eventDate &&
        (eventDate < card.dataset.startDate || eventDate > card.dataset.endDate);
      const outsideSuggestions = step === 'start' && suggestedKeys && !suggestedKeys.has(card.dataset.cardKey);
      card.hidden = !!(outsideSuggestions || wrongRegion || wrongDate || (cardKind !== 'all' && cardKind !== card.dataset.cardKind) || !searchText.get(card).includes(term));
      return !card.hidden;
    });
    stage.hidden = filteredCards.length === 0;
    const multiple = filteredCards.length > 1;
    form.querySelector('.deck-navigation').hidden = step === 'start' && !multiple;
    form.querySelector('#start-alternatives').hidden = step !== 'start' || !multiple;
    form.dataset.multiple = String(multiple);
    activate(filteredCards.includes(activeCard) ? activeCard : filteredCards[0]);
    form.querySelector('#builder-empty').hidden = filteredCards.length > 0;
    form.querySelector('#card-count').textContent = `${filteredCards.length}장 찾음`;
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
        revealSuggestion();
      }
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
    title.textContent = { start: '시작 카드 고르기', build: '덱 만들기', review: '여행 확인' }[step];
    const hint = document.querySelector('#builder-step-hint');
    hint.hidden = step === 'start' && selected.size < 2;
    filters.querySelector('#start-preferences').hidden = step !== 'start';
    if (step !== 'start') reveal?.cancel();
    hint.textContent = { start: selected.size > 1 ? '시작 카드를 다시 고르면 첫 카드만 바뀌고, 나머지 구성은 유지돼요.' : '어디에서 여행을 시작할까요? 마음에 드는 한 장을 골라 주세요.', build: '카드를 더 담고, 방문할 순서를 정해 보세요.', review: '여행 순서를 확인하고 이름을 붙여 저장하세요.' }[step];
    document.querySelectorAll('#builder-steps [data-step]').forEach(item => {
      if (item.dataset.step === step) item.setAttribute('aria-current', 'step');
      else item.removeAttribute('aria-current');
    });
    filters.hidden = step === 'review';
    const direct = step !== 'start' || startMode === 'direct';
    searchPanel.hidden = !direct;
    searchPanel.open = direct;
    form.querySelector('.card-tools').hidden = !direct;
    filters.querySelector('#start-theme-panel').hidden = startMode !== 'theme';
    filters.querySelector('#start-theme-panel').disabled = step !== 'start' || startMode !== 'theme';
    filters.querySelector('#start-region-panel').hidden = startMode !== 'region';
    filters.querySelector('#start-event-panel').hidden = startMode !== 'event';
    status.hidden = step === 'review';
    form.querySelector('.builder-catalog').hidden = step === 'review';
    summary.hidden = step === 'start';
    form.querySelector('#journey-details').hidden = step !== 'review';
    const actions = form.querySelector('.deck-summary-actions');
    if (actions) actions.hidden = step !== 'review';
    const firstCard = filters.querySelector('#builder-first-card');
    firstCard.hidden = step !== 'start';
    firstCard.disabled = step !== 'start';
    renderSelection();
    if (focus) { title.focus(); title.scrollIntoView({ block: 'start' }); }
    recommend();
  }

  function goStep(value) {
    history.pushState(null, '', `#${value}`);
    showStep(value);
  }
  nextStep.addEventListener('click', () => goStep(step === 'start' ? 'build' : 'review'));
  backStep.addEventListener('click', () => goStep(step === 'review' ? 'build' : 'start'));
  window.addEventListener('popstate', () => {
    const value = location.hash.slice(1);
    if (['start', 'build', 'review'].includes(value)) showStep(value);
  });
  form.addEventListener('invalid', () => {
    if (step !== 'review') goStep('review');
  }, true);
  form.addEventListener('submit', event => {
    if (step !== 'review') { event.preventDefault(); goStep('review'); }
  });
  function closeSearch(event) {
    event.preventDefault();
    searchPanel.open = false;
    stage.focus({ preventScroll: true });
  }
  search.addEventListener('keydown', event => {
    if (event.key === 'Enter') closeSearch(event);
  });

  search.addEventListener('input', filterCards);
  date.addEventListener('input', filterCards);
  function setKind(value) {
    kind = value;
    typeButtons.forEach(item => item.setAttribute('aria-pressed', String(item.dataset.cardType === kind)));
    filterCards();
  }
  typeButtons.forEach(control => control.addEventListener('click', () => setKind(control.dataset.cardType)));
  form.querySelector('#shuffle-cards').addEventListener('click', () => {
    const shuffled = [...cards];
    for (let index = shuffled.length - 1; index > 0; index--) {
      const other = Math.floor(Math.random() * (index + 1));
      [shuffled[index], shuffled[other]] = [shuffled[other], shuffled[index]];
    }
    rankedCards = shuffled;
    activate(null);
    arrangeCards();
    setStatus('success', '카드를 섞었어요. 선택한 관광지와 여행 순서는 그대로예요.');
  });

  function renderSelection(focus) {
    list.replaceChildren();
    const ids = [...selected];
    ids.forEach((id, index) => {
      const name = controls.get(byId.get(id)).name;
      const item = selectionTemplate.content.firstElementChild.cloneNode(true);
      item.dataset.selectedId = id;
      item.querySelector('span').textContent = `${index + 1}. ${name}${index === 0 ? ' · 시작' : ''}`;
      item.querySelector('input').value = id;
      item.querySelectorAll('button').forEach(button => {
        const action = button.dataset.action;
        button.setAttribute('aria-label', `${name} ${action === '빼기' ? '선택 해제' : action}`);
        button.disabled = action === '위로' && index === 0 || action === '아래로' && index === ids.length - 1;
      });
      list.append(item);
    });
    cards.forEach(card => {
      const checked = isSelected(card);
      const { checkbox, label, toggle } = controls.get(card);
      checkbox.checked = selected.has(card.dataset.cardKey);
      card.classList.toggle('is-selected', checked);
      label.textContent = step === 'start' ? checked ? '✓ 시작 카드로 선택됨' : '시작 카드로 선택' : checked ? '✓ 내 여행에 담김' : '여행에 담기';
      toggle.setAttribute('aria-label', `${controls.get(card).name} ${step === 'start' ? checked ? '시작 카드 선택 해제' : '시작 카드로 선택' : checked ? '여행에서 빼기' : '여행에 담기'}`);
      toggle.setAttribute('aria-pressed', String(checked));
    });
    form.querySelector('#selection-count').textContent = `${selected.size}장`;
    form.querySelector('#bar-label').textContent = step === 'start' ? '시작 카드' : '담은 카드';
    form.querySelector('#bar-count').textContent = step === 'start'
      ? selected.size ? controls.get(byId.get(ids[0])).name : '마음에 드는 한 장을 골라 주세요'
      : `${selected.size}장 선택`;
    form.querySelector('#selection-empty').hidden = selected.size > 0;
    const save = form.querySelector('#confirm-journey');
    if (save) save.disabled = selected.size === 0;
    nextStep.disabled = step === 'start' && selected.size === 0;
    nextStep.hidden = step === 'review';
    nextStep.textContent = step === 'start' ? '이 카드로 시작' : '여행 확인';
    backStep.hidden = step === 'start';
    backStep.textContent = step === 'build' ? '시작 카드 변경' : '덱 수정';
    arrangeCards();
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
    return step === 'start' ? selected.values().next().value === card.dataset.cardKey : selected.has(card.dataset.cardKey);
  }
  function selectCard(card, checked) {
    const id = card.dataset.cardKey;
    if (checked && step === 'start') selected = new Set([id, ...[...selected].slice(1)]);
    else if (checked) selected.add(id);
    else selected.delete(id);
    renderSelection();
    feedback.textContent = step === 'start'
      ? checked ? `${controls.get(card).name}에서 시작해요. ‘이 카드로 시작’을 누르면 다음 카드를 담을 수 있어요.` : '시작 카드 선택을 해제했어요. 다른 한 장을 골라 주세요.'
      : `${controls.get(card).name} ${checked ? '여행에 담았어요' : '여행에서 뺐어요'} · 총 ${selected.size}장`;
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
    if (step === 'start' || !selected.size) activate(null);
    arrangeCards();
  }

  async function recommend() {
    request?.abort();
    const current = new AbortController();
    request = current;
    suggestedKeys = null;
    form.dataset.suggesting = String(step === 'start');
    setStatus('loading', '취향에 맞는 카드를 찾고 있어요…');
    filters.setAttribute('aria-busy', 'true');
    try {
      if (step === 'start' && (startMode === 'event' || startMode === 'direct')) {
        showResults([]);
        setStatus('success', startMode === 'event' ? '일정과 공식 안내를 확인하고 시작할 행사 한 장을 골라 주세요.' : '검색과 조건으로 시작할 카드 한 장을 골라 주세요.');
        return;
      }
      const query = new URLSearchParams([...new FormData(filters)].filter(([name, value]) => ['themeId', 'regionId', 'companionId'].includes(name) && value !== ''));
      if (step === 'start' && startMode !== 'direct') {
        query.delete('regionId');
        query.delete('companionId');
        if (startMode === 'region' && startRegion.value) query.set('regionId', startRegion.value);
      }
      const response = await fetch(`${filters.dataset.recommendationsUrl}?${query}`, { signal: current.signal, headers: { Accept: 'application/json' } });
      if (!response.ok) throw new Error('recommendations');
      const data = await response.json();
      if (request !== current) return;
      suggestedKeys = step === 'start' && data.items.length ? new Set(data.items.map(item => `place:${item.place.id}`)) : null;
      showResults(data.items);
      setStatus(!data.items.length && filters.querySelector('[name=themeId]:checked') ? 'empty' : 'success', step !== 'start' ? '담은 카드 주변에서 다음 카드를 골라 보세요.' : data.items.length ? '첫 관광지 후보를 찾았어요. 한 장을 고르고 ‘이 카드로 시작’을 눌러 주세요.' : '추천할 관광지가 아직 없어요. 공개된 카드에서 직접 골라 시작해 보세요.');
    } catch (error) {
      if (error.name === 'AbortError' || request !== current) return;
      showResults([]);
      setStatus('error', '추천을 불러오지 못했어요. 테마를 다시 선택하거나 카드에서 직접 골라 주세요.');
    } finally {
      if (request === current) {
        filters.removeAttribute('aria-busy');
        form.dataset.suggesting = 'false';
        revealSuggestion();
      }
    }
  }

  filters.addEventListener('change', event => {
    if (event.target.matches('[name=startMode]')) {
      startMode = event.target.value;
      activate(null);
      showStep(step, false);
      return;
    }
    if (event.target !== startRegion && event.target !== startDate && !event.target.matches('[name=themeId], [name=regionId], [name=companionId]')) return;
    activate(null);
    filterCards();
    recommend();
  });
  filters.addEventListener('submit', closeSearch);
  filters.addEventListener('reset', () => { setTimeout(() => { startMode = 'theme'; setKind('all'); showStep(step, false); }, 0); });
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
  filters.querySelector('#start-preferences').open = false;
  document.body.classList.add('is-deck-mode');
  document.querySelector('#builder-steps').hidden = false;
  history.replaceState(null, '', `#${step}`);
  showStep(step, false);
  bar.hidden = false;
})();

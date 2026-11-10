// Bottom sheet for the deck-building step: the map and picked list stay folded in one row above the
// bottom menu until the handle or the map preview is pressed, or the handle is dragged up.
// The open/closed look lives in CSS under [data-sheet].
const DeckSheet = (() => {
  const DRAG_THRESHOLD = 48;

  /** Binds the sheet once. `setEnabled(true)` turns it on (folded); `close()` folds it again. */
  function bind(sheet, { handle, preview, label }) {
    let enabled = false, drag = null;
    const isOpen = () => sheet.dataset.sheet === 'open';

    function set(open, focus) {
      if (!enabled) return;
      sheet.dataset.sheet = open ? 'open' : 'peek';
      handle.setAttribute('aria-expanded', String(open));
      label.textContent = open ? '접기' : '지도·순서 보기';
      if (focus) handle.focus({ preventScroll: true });
    }

    function setEnabled(value) {
      enabled = value;
      handle.hidden = !value;
      if (value) set(false);
      else {
        delete sheet.dataset.sheet;
        sheet.style.removeProperty('height');
      }
    }

    handle.addEventListener('pointerdown', event => {
      if (!enabled || event.button !== 0) return;
      drag = { y: event.clientY, height: sheet.getBoundingClientRect().height, moved: false };
      handle.setPointerCapture(event.pointerId);
    });
    handle.addEventListener('pointermove', event => {
      if (!drag) return;
      const dy = event.clientY - drag.y;
      if (!drag.moved && Math.abs(dy) < 6) return;
      drag.moved = true;
      sheet.dataset.dragging = 'true';
      sheet.style.height = `${Math.max(64, Math.min(innerHeight * .9, drag.height - dy))}px`;
    });
    const release = event => {
      if (!drag) return;
      const { moved, y } = drag;
      drag = null;
      delete sheet.dataset.dragging;
      sheet.style.removeProperty('height');
      if (!moved) return;
      const dy = event.clientY - y;
      if (Math.abs(dy) >= DRAG_THRESHOLD) set(dy < 0);
      handle.dataset.dragged = 'true';
    };
    handle.addEventListener('pointerup', release);
    handle.addEventListener('pointercancel', release);
    handle.addEventListener('click', () => {
      // A drag ends with a click on the same element; only a plain press toggles.
      if (handle.dataset.dragged) delete handle.dataset.dragged;
      else set(!isOpen());
    });

    document.addEventListener('keydown', event => {
      if (event.key === 'Escape' && isOpen()) set(false, true);
    });
    document.addEventListener('pointerdown', event => {
      if (isOpen() && !sheet.contains(event.target)) set(false);
    });
    // The folded map is a preview: pressing it unfolds the sheet.
    // Decided on pointerdown so a pin click that folds the open sheet does not unfold it again.
    preview.addEventListener('pointerdown', () => {
      if (enabled && !isOpen()) set(true);
    });
    // Keyboard users tabbing into the folded content get it unfolded instead of focusing hidden controls.
    preview.addEventListener('focusin', () => {
      if (enabled && !isOpen()) set(true);
    });

    return { setEnabled, close: () => set(false) };
  }

  return { bind };
})();

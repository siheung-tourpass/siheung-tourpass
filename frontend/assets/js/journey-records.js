(() => {
  // Picking a star needs a visit date; fill the trip date (or today) when none is set.
  for (const form of document.querySelectorAll('.record-form')) {
    const date = form.elements.visitedOn;
    form.addEventListener('change', event => {
      if (event.target.name === 'rating' && event.target.value && !date.value) date.value = form.dataset.defaultVisit;
    });
  }
  // A chosen photo uploads right away; the save button stays only without JavaScript.
  for (const form of document.querySelectorAll('.record-photo form[enctype]')) {
    form.querySelector('.record-photo-submit').hidden = true;
    form.elements.photo.addEventListener('change', () => { if (form.elements.photo.files.length) form.requestSubmit(); });
  }
  // Coming back from a save, reopen the place that was edited.
  const target = location.hash && document.querySelector(location.hash);
  target?.querySelector('.trip-stop-edit')?.setAttribute('open', '');
})();

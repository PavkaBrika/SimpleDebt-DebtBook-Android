'use strict';

const hapticPatterns = {selection: 8, confirm: 20, reject: [9, 45, 9]};
const hapticLabels = {selection: 'Выбор · щелчок', confirm: 'Успех · импульс', reject: 'Отказ · два импульса'};
const hapticToggle = document.getElementById('haptics');
const hapticReadout = document.getElementById('haptic-readout');

function setHapticsEnabled(enabled) {
  hapticToggle.setAttribute('aria-pressed', String(enabled));
  hapticReadout.textContent = enabled ? 'Включена' : 'Выключена';
}

try {
  setHapticsEnabled(localStorage.getItem('simpledebt-studio-haptics') !== 'off');
} catch { /* The toggle also works for this tab with storage disabled. */ }

hapticToggle.addEventListener('click', () => {
  const enabled = hapticToggle.getAttribute('aria-pressed') === 'false';
  setHapticsEnabled(enabled);
  try { localStorage.setItem('simpledebt-studio-haptics', enabled ? 'on' : 'off'); } catch { /* Session only. */ }
});

document.addEventListener('prototype-haptic', ({detail}) => {
  hapticReadout.textContent = hapticLabels[detail];
  if (!reducedMotion.matches) hapticReadout.animate([{opacity:.45}, {opacity:1}], {duration:160});
});

function playHaptic(effect) {
  if (hapticToggle.getAttribute('aria-pressed') === 'false') return;
  document.dispatchEvent(new CustomEvent('prototype-haptic', {detail: effect}));
  if (typeof navigator.vibrate !== 'function') return;
  try {
    navigator.vibrate(hapticPatterns[effect]);
  } catch {
    // A browser can expose vibration while blocking it for the current page.
  }
}

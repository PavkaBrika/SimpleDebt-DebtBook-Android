'use strict';

const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
const settleEase = 'cubic-bezier(.2,.8,.2,1)';
const ledgerSnapshots = new WeakMap();

function motionAmount(key, cents, cur, signed=false) {
  return `<span data-amount="${escapeHtml(key)}" data-cents="${cents}" data-currency="${escapeHtml(cur)}"${signed?' data-signed':''}>${escapeHtml(amount(cents,cur,signed))}</span>`;
}

function progressTrack(key, percent) {
  return `<div class="track"><i data-progress="${escapeHtml(key)}" style="width:${percent}%"></i></div>`;
}

// Keep the exact result available to assistive technology throughout the count.
function countAmount(node, from, to) {
  const cur = node.dataset.currency, signed = node.hasAttribute('data-signed');
  const step = cur === 'JPY' || (from % 100 === 0 && to % 100 === 0) ? 100 : 1;
  const result = document.createElement('span');
  result.className = 'sr-only';
  result.textContent = node.textContent;
  const counter = document.createElement('span');
  counter.setAttribute('aria-hidden','true');
  node.replaceChildren(result,counter);
  const start = performance.now();
  function tick(now) {
    if (!node.isConnected) return;
    const fraction = Math.min(1, Math.max(0,(now-start)/360));
    if (fraction === 1 || reducedMotion.matches) {
      node.textContent = result.textContent;
      return;
    }
    const value = to + (from-to) * (1-fraction)**3;
    counter.textContent = amount(Math.round(value/step)*step,cur,signed);
    requestAnimationFrame(tick);
  }
  tick(start);
}

// Each panel can return from an editor to the same ledger. Keep one baseline per
// list route, keyed by its currency, period or owner, independent of other panels.
function updateLedgerMotion(frame, panel, committed) {
  if (!reviewableLists.includes(frame.route)) return;
  const context = {
    home:frame.cur, person:frame.personId, goals:'goals', goalDetail:frame.goalId,
    categoryHistory:[frame.categoryId,frame.financeCur].join('/'),
    finance:[frame.financeCur,frame.financeDir,...periodRange(frame)].join('/')
  }[frame.route];
  if (!ledgerSnapshots.has(frame)) ledgerSnapshots.set(frame,new Map());
  const routes = ledgerSnapshots.get(frame);
  const previous = routes.get(frame.route);
  const next = {context, amounts:new Map(), widths:new Map(), rows:new Map()};
  const visibleMotion = !reducedMotion.matches && panel.getClientRects().length > 0;
  const play = committed && previous?.context === context && visibleMotion;
  for (const node of panel.querySelectorAll('[data-amount]')) {
    const key = node.dataset.amount + '/' + node.dataset.currency;
    const to = Number(node.dataset.cents), from = previous?.amounts.get(key);
    next.amounts.set(key,to);
    if (play && from !== undefined && from !== to) countAmount(node,from,to);
  }
  for (const bar of panel.querySelectorAll('[data-progress]')) {
    const key = bar.dataset.progress, width = bar.style.width, from = previous?.widths.get(key);
    next.widths.set(key,width);
    if (play && from && from !== width) bar.animate([{width:from},{width}],{duration:360,easing:settleEase});
  }
  for (const row of panel.querySelectorAll('[data-motion-row]')) {
    const key = row.dataset.motionRow, top = visibleMotion ? row.offsetTop : null;
    next.rows.set(key,top);
    if (!play) continue;
    const oldTop = previous.rows.get(key);
    if (oldTop === undefined) {
      row.classList.add('row-arrival');
    } else if (oldTop !== null && top !== oldTop) {
      const distance = Math.max(-64,Math.min(64,oldTop-top));
      row.animate([{transform:`translateY(${distance}px)`},{transform:'none'}],{duration:240,easing:settleEase});
    }
  }
  routes.set(frame.route,next);
}

function enterRoute(panel, back) {
  if (reducedMotion.matches || !panel.getClientRects().length) return;
  const content = panel.querySelector('.goal-screen-body') || panel.querySelector('.scroll');
  content.animate([
    {opacity:.65, transform:`translateX(${back ? -12 : 16}px)`},
    {opacity:1, transform:'none'}
  ], {duration:back ? 160 : 180, easing:settleEase});
}

function enterSheet(panel) {
  if (reducedMotion.matches) return;
  const overlay = panel.querySelector('.overlay');
  overlay.animate([{opacity:0},{opacity:1}],{duration:160});
  overlay.querySelector('.sheet').animate(
    [{transform:'translateY(32px)'},{transform:'translateY(0)'}],{duration:220,easing:settleEase}
  );
}

function leaveSheet(phone, overlay) {
  if (reducedMotion.matches) return;
  // The action and focus change immediately. Only the outgoing image lingers.
  overlay.inert = true;
  overlay.setAttribute('aria-hidden','true');
  overlay.classList.add('leaving');
  phone.append(overlay);
  overlay.querySelector('.sheet').animate([{transform:'none'},{transform:'translateY(24px)'}],{duration:140});
  const fade = overlay.animate([{opacity:1},{opacity:0}],{duration:140,fill:'forwards'});
  fade.finished.then(()=>overlay.remove(),()=>overlay.remove());
}

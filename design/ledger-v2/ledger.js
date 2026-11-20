'use strict';

// studio.js owns the demo data, navigation, forms and persistence.
// Only the two renderers used for the debt register change in this entry point.
const registerFilters = {all:'Все записи', positive:'Мне должны', negative:'Я должен'};
const registerOrders = ['Сначала по сроку', 'Дата создания ↓', 'Дата создания ↑', 'Сумма ↓', 'Сумма ↑'];
const shortOrder = {
  'Сначала по сроку':'по сроку', 'Дата создания ↓':'сначала новые', 'Дата создания ↑':'сначала старые',
  'Сумма ↓':'по убыванию суммы', 'Сумма ↑':'по возрастанию суммы'
};
const stampedDate = new Date(TODAY + 'T12:00:00Z');
const datePart = options => stampedDate.toLocaleDateString('ru-RU', {...options, timeZone:'UTC'});
const registerDate = datePart({day:'numeric', month:'long'}).split(' ');
const registerCurrency = new Intl.DisplayNames(['ru'], {type:'currency'});

function registerMoney(cents, cur, signed = false) {
  const parts = amount(cents,cur,signed).split(' ');
  const symbol = parts.pop();
  return `<span class="figure">${escapeHtml(parts.join(' '))}</span><span class="denomination"> ${escapeHtml(symbol)}</span>`;
}

function openLoans(person) {
  return person.loans.filter(loan => remaining(loan) > 0);
}

function nextDue(person) {
  return openLoans(person).filter(loan => loan.due).sort((a,b) => a.due.localeCompare(b.due))[0];
}

function entryLine(person) {
  const loans = openLoans(person);
  const next = nextDue(person);
  const description = loans.length === 1 && loans[0].note ? loans[0].note :
    loans.length ? 'Открытых записей · ' + loans.length : 'Расчёт завершён';
  const due = next ? dueLabel(next) : '';
  return `<button class="entry" data-act="person" data-value="${person.id}">
    <span class="entry-name">${escapeHtml(person.name)}</span>
    <span class="entry-sum">${registerMoney(balance(person),person.cur,true)}</span>
    <span class="entry-note">${escapeHtml(description)}</span>
    ${due ? `<span class="entry-date ${next.due < TODAY ? 'overdue' : ''}">${escapeHtml(due)}</span>` : ''}
  </button>`;
}

function registerRows(f) {
  const query = f.query.trim().toLocaleLowerCase('ru');
  const pool = people.filter(person => person.cur === f.cur &&
    person.name.toLocaleLowerCase('ru').includes(query) &&
    (f.filter === 'all' || (f.filter === 'positive' ? balance(person) > 0 : balance(person) < 0)));
  if (!f.registerOrder || f.registerOrder === registerOrders[0]) {
    pool.sort((a,b) => (nextDue(a)?.due || '9999').localeCompare(nextDue(b)?.due || '9999') ||
      a.name.localeCompare(b.name, 'ru'));
  } else {
    sortPeople(pool, f.registerOrder);
  }
  if (pool.length) return pool.map(entryLine).join('');
  const message = query ? 'По этому имени никого нет.' : 'Записей в этой подборке пока нет.';
  return `<p class="register-empty">${message}</p>`;
}

function registerSummary(f) {
  const balances = people.filter(p => p.cur === f.cur).map(balance);
  const incoming = balances.reduce((sum,v) => sum + Math.max(0,v), 0);
  const outgoing = balances.reduce((sum,v) => sum + Math.max(0,-v), 0);
  const share = incoming + outgoing ? 100 * incoming / (incoming + outgoing) : 0;
  const currencies = [...new Set([prefs.first, prefs.second, ...people.map(p=>p.cur), f.cur])];
  const currencyOptions = currencies.map(code =>
    `<option value="${escapeHtml(code)}" ${code === f.cur ? 'selected' : ''}>${escapeHtml(code)} — ${escapeHtml(registerCurrency.of(code))}</option>`).join('');
  return `<section class="ledger-summary" aria-label="Сводка долгов">
    <div class="balance-heading">
      <h2>Общий баланс</h2>
      <label class="ledger-currency"><span aria-hidden="true">${escapeHtml(f.cur)} ${icon('down',14)}</span>
        <select data-bind="cur" aria-label="Валюта обзора">${currencyOptions}</select>
      </label>
    </div>
    <p class="net">${registerMoney(incoming-outgoing, f.cur, true)}</p>
    <div class="tally ${incoming + outgoing ? '' : 'empty-tally'}" aria-hidden="true" style="--incoming:${share}%"></div>
    <div class="breakdown">
      <div><span class="ledger-key incoming">Мне должны</span><strong>${registerMoney(incoming,f.cur)}</strong></div>
      <div><span class="ledger-key outgoing">Я должен</span><strong>${registerMoney(outgoing,f.cur)}</strong></div>
    </div>
  </section>`;
}

function registerHome(f) {
  const body = `<div class="register">
    <header class="masthead"><h1>Долги</h1>
      <time datetime="${TODAY}" aria-label="${escapeHtml(datePart({dateStyle:'full'}))}">
        <span class="day">${registerDate[0]}</span><span class="stamp-text">${registerDate[1]}<br>${datePart({weekday:'long'})}</span>
      </time>
    </header>
    ${registerSummary(f)}
    <section class="register-list" aria-label="Люди">
      <div class="list-heading"><h2>Люди</h2><button class="write" data-act="new">${icon('plus',17)}Новый долг</button></div>
      <div class="list-tools">
        <button class="filter" data-act="register-options" aria-label="Фильтр и сортировка: ${registerFilters[f.filter]}">
          ${registerFilters[f.filter]} · ${shortOrder[f.registerOrder || registerOrders[0]]}${icon('down',14)}
        </button>
        <button class="find" data-act="search" aria-label="${f.search ? 'Закрыть поиск' : 'Найти человека'}">${icon(f.search ? 'close' : 'search',19)}</button>
      </div>
      ${f.search ? `<input class="register-search" data-bind="query" aria-label="Имя человека" placeholder="Найти по имени" value="${escapeHtml(f.query)}">` : ''}
      <div class="people register-entries">${registerRows(f)}</div>
    </section>
  </div>`;
  return layout(body, '', nav('home'));
}

flowActions['register-options'] = (i, value, trigger) => {
  const f = frames[i];
  showDialog(i, 'Вид списка', s => '<div class="group">' +
    field('book','Какие записи показать',picker('sheet.direction',Object.values(registerFilters),s.direction,'Какие записи показать')) +
    field('settings','Порядок',picker('sheet.order',registerOrders,s.order,'Порядок')) + '</div>',
    trigger, (_,s) => {
      f.filter = Object.keys(registerFilters).find(key => registerFilters[key] === s.direction) || 'all';
      f.registerOrder = s.order;
      closeSheet(i);
    }, 'Применить', {direction:registerFilters[f.filter], order:f.registerOrder || registerOrders[0]});
};

home = registerHome;
peopleList = registerRows;
render(0);

'use strict';

const TODAY = '2026-11-20';
const startScreens = ['home', 'finance', 'goals'];
const screenNames = {home:'Список долгов', person:'История долгов', new:'Новый долг', finance:'Финансы', goals:'Цели', settings:'Настройки'};
const tabs = [['home','Долги','book'],['finance','Финансы','pie'],['goals','Цели','flag'],['settings','Настройки','settings']];
const currencySigns = {...Object.fromEntries(currencyCodes.map(code=>[code,code])), RUB:'₽', USD:'$', EUR:'€', GBP:'£', JPY:'¥'};
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
const shapes = {
  edit:'<path d="m4 16-1 5 5-1L20 8l-4-4zM14 6l4 4"/>', more:'<g fill="currentColor"><circle cx="4" cy="12" r="1.2"/><circle cx="12" cy="12" r="1.2"/><circle cx="20" cy="12" r="1.2"/></g>',
  trash:'<path d="M4 6h16M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7m4-7v7"/>',
  book:'<path d="M3 5.5c3-1 6.5-.1 9 2 2.5-2.1 6-3 9-2v13c-3-1-6.5 0-9 2-2.5-2-6-3-9-2zM12 8v12"/>',
  pie:'<path d="M10 3.5a8.8 8.8 0 1 0 10.5 10H10zM14 2.5v7.4h7.4A8 8 0 0 0 14 2.5z"/>',
  flag:'<path d="M5 21V4c5-4 9 4 15 0v10c-6 4-10-4-15 0"/>',
  settings:'<path d="M3 6h6m4 0h8M3 12h11m4 0h3M3 18h3m4 0h11"/><circle cx="11" cy="6" r="2"/><circle cx="16" cy="12" r="2"/><circle cx="8" cy="18" r="2"/>',
  search:'<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>',
  plus:'<path d="M12 5v14M5 12h14"/>', back:'<path d="m13 5-7 7 7 7M6 12h15"/>', down:'<path d="m6 9 6 6 6-6"/>',
  give:'<path d="M5 17 17 5M8 5h9v9"/>', take:'<path d="M18 5 6 17m0-9v9h9"/>',
  date:'<rect x="3" y="5" width="18" height="16" rx="4"/><path d="M7 3v4m10-4v4M3 10h18"/>',
  bell:'<path d="M5 16c2-3 2-4 2-7a5 5 0 0 1 10 0c0 3 0 4 2 7l1 1H4l1-1zm5 4c1 1.4 3 1.4 4 0"/>',
  user:'<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
  note:'<path d="M5 3h14a2 2 0 0 1 2 2v10l-6 6H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zM14 21v-6h6M7 8h10M7 12h6"/>',
  close:'<path d="m6 6 12 12M6 18 18 6"/>', moon:'<path d="M20.5 14A8.8 8.8 0 0 1 10 3.5 9 9 0 1 0 20.5 14z"/>',
  sun:'<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5"/>',
  reset:'<path d="M3 10a9 9 0 1 1 1 7M3 4v6h6"/>', check:'<path d="m4 12 5 5L20 6"/>',
  history:'<path d="M3 10a9 9 0 1 1 0 5M3 4v6h6M12 7v5l4 2"/>',
  chevron:'<path d="m9 5 7 7-7 7"/>', stack:'<path d="M4 9 12 4l8 5-8 5-8-5zm0 5 8 5 8-5"/>',
};
function svg(body, view='0 0 24 24', attrs='') {
  return '<svg xmlns="http://www.w3.org/2000/svg" aria-hidden="true" viewBox="'+view+'" '+attrs+'>'+body+'</svg>';
}
function icon(name,size=20) {
  return svg(shapes[name],undefined,'width="'+size+'" height="'+size+'" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"');
}
function motif(index) {
  const palettes = [['#f4cebb','#a74e38'],['#d5e4f3','#45678c'],['#e5ddb8','#76682c'],['#dce5b5','#617337'],['#e7dbed','#865f92']];
  const [bg,ink] = palettes[index%palettes.length];
  const fill = [
    Array.from({length:8},(_,i)=>'<ellipse cx="24" cy="14" rx="5.4" ry="9.4" transform="rotate('+i*45+' 24 24)"/>').join('')+'<circle cx="24" cy="24" r="4.6" fill="'+bg+'"/>',
    [0,1,2,3,4,5].map(i=>'<path d="M'+(i*8-10)+' 46 26 9" transform="translate('+i*3+' 0)" stroke="'+ink+'" stroke-width="3.3" fill="none"/>').join(''),
    '<g transform="rotate(32 24 24)">'+[0,1,2].map(i=>'<rect x="8" y="'+(10+i*10)+'" width="32" height="6" rx="2"/>').join('')+'</g>',
    Array.from({length:9},(_,i)=>'<ellipse cx="24" cy="13" rx="2.4" ry="8.5" transform="rotate('+i*40+' 24 24)"/>').join(''),
    '<path d="m24 4 5 14 14 6-14 5-5 15-5-15-14-5 14-6z"/>'
  ][index%palettes.length];
  return '<span class="motif">'+svg('<circle cx="24" cy="24" r="24" fill="'+bg+'"/><g fill="'+ink+'">'+fill+'</g>','0 0 48 48')+'</span>';
}
function personAvatar(person) {
  return person.photo?'<img class="motif person-photo" alt="" src="'+escapeHtml(person.photo)+'">':motif(person.art);
}
// Parallel contours give the balance its engraved coin motif. The same ellipses fit a Compose Canvas.
function coinArt() {
  const coins = [[24,56,30],[21,49,30],[19,42,30],[43,32,31],[41,25,31],[39,18,31]];
  const lines = coins.map(([x,y,r])=>'<g transform="translate('+x+' '+y+')"><path d="M0 0v6a'+r+' 10 0 0 0 '+(2*r)+' 0V0" fill="var(--hero)" stroke="currentColor"/><ellipse cx="'+r+'" cy="0" rx="'+r+'" ry="10" fill="var(--hero)" stroke="currentColor"/>'+[7,12,17,22,27,32,37,42,47,52].map(i=>'<path d="M'+i+' '+(7+2*Math.sin(i/60*Math.PI))+'v4" stroke="currentColor"/>').join('')+'</g>').join('');
  return '<div class="art">'+svg('<g transform="rotate(-20 57 42)" color="var(--canvas-pattern)" stroke-width="1.3">'+lines+'<ellipse cx="70" cy="18" rx="22" ry="6" fill="none" stroke="currentColor"/></g>','0 0 114 83')+'</div>';
}
const status = '<div class="status" aria-hidden="true"><span>9:41</span><div class="status-icons">'+svg('<path fill="currentColor" d="M1 11h2v3H1zm4-3h2v6H5zm4-3h2v9H9zm4-3h2v12h-2z"/>','0 0 17 16')+svg('<path fill="currentColor" d="M0 5Q8-1 16 5l-8 9z"/>','0 0 16 16')+svg('<rect x="1" y="3" width="19" height="10" rx="2" fill="none" stroke="currentColor"/><rect x="3" y="5" width="14" height="6" rx="1" fill="currentColor"/><path d="M22 6v4" stroke="currentColor" stroke-width="2"/>','0 0 24 16')+'</div></div>';
const gesture = '<div class="gesture" aria-hidden="true"></div>';
function iconButton(action,label,name) {
  return '<button class="icon-button" data-act="'+action+'" aria-label="'+label+'" title="'+label+'">'+icon(name)+'</button>';
}
function primary(action,label,name,secondary=false) {
  return '<button class="btn'+(secondary?' secondary':'')+'" data-act="'+action+'">'+(name?icon(name,18):'')+label+'</button>';
}
function options(values,selected) {
  return values.map(v=>'<option value="'+v+'" '+(v===selected?'selected':'')+'>'+v+'</option>').join('');
}
const moneyDigits = new Intl.NumberFormat('ru-RU',{maximumFractionDigits:2});
function amount(cents,cur,withSign=false) {
  const sign=cents<0?'−':(withSign && cents>0?'+':'');
  return sign+moneyDigits.format(Math.abs(cents)/100)+' '+currencySigns[cur];
}
function parseAmount(raw) {
  const s=raw.replace(/[\s\u00a0]/g,'').replace(',','.');
  if(!/^\d{1,10}([.]\d{1,2})?$/.test(s)) return 0;
  const [whole,part='']=s.split('.');
  return Number(whole)*100+Number(part.padEnd(2,'0'));
}
const dateText = day => new Intl.DateTimeFormat('ru-RU',{day:'numeric',month:'short',timeZone:'UTC'}).format(new Date(day+'T12:00:00Z')).replace(/\.$/,'');
function freshData() {
  const specs=[['Анна Соколова',1600000,'Поездка в Казань','2026-11-05','2026-11-21'],['Михаил Орлов',-420000,'Билеты','2026-11-07','2026-11-18'],['Елена Ким',750000,'За жильё','2026-11-10',''],['Илья Волков',260000,'Ужин','2026-11-15',''],['Дарья Ли',8000,'Книги','2026-11-12','']];
  return specs.map(([name,principal,note,date,due],i)=>({id:i+1,name,cur:i===4?'USD':'RUB',art:i,loans:[{id:i+1,principal,note,date,due,reminder:'',returns:i===0?[{id:6,amount:360000,date:'2026-11-14',cancelled:false}]:[]}]}));
}
let people=freshData(), serial=10, currentPanel=0, before=false, dark=false;
const remaining = l => Math.abs(l.principal)-l.returns.reduce((sum,r)=>sum+(r.cancelled?0:r.amount),0);
const balance = p => p.loans.reduce((sum,l)=>sum+Math.sign(l.principal)*remaining(l),0);
function dueLabel(l) {
  if(remaining(l)===0) return 'Погашено';
  if(!l.due) return 'Без срока';
  if(l.due<TODAY) return 'Просрочен · '+dateText(l.due);
  if(l.due===TODAY) return 'Срок сегодня';
  if(l.due==='2026-11-21') return 'Срок завтра';
  return 'До '+dateText(l.due);
}
function draft(cur=defaultCurrency()) { return {name:'',note:'',sum:'',cur,dir:1,date:TODAY,due:'',remind:false,time:'09:00',fixed:0}; }
function frame(route) {
  const d=draft();
  return {route,beforeScreen:route,personId:1,cur:defaultCurrency(),filter:'all',query:'',search:false,d,back:'home',sheet:null,error:'',returnPoints:new Map(),...featureFrame()};
}
ledger=freshLedger(); savings=freshGoals();
loadDemo();
let frames=startScreens.map(frame);
function nav(route) {
  const selectedColumn = tabs.findIndex(([key]) => key === route) + 1;
  const buttons = tabs.map(([key, label, symbol], index) =>
    `<button data-act="nav" data-value="${key}" style="grid-column:${index + 1}"
      ${route === key ? 'aria-current="page"' : ''}>${icon(symbol, 21)}<span>${label}</span></button>`
  ).join('');
  return `<nav class="nav" aria-label="Разделы приложения" style="--selected-column:${selectedColumn}">
    <span class="nav-indicator" aria-hidden="true"></span>${buttons}</nav>`;
}
function heading(title,action='') { return '<div class="apphead"><h2>'+title+'</h2>'+action+'</div>'; }
function backbar(title, {action='', avatar='', pillTitle=false}={}) {
  return `<header class="detailhead${pillTitle?' detailhead-pill':''}">
    ${iconButton('back','Назад','back')}
    <div class="detail-title">${avatar}<h2>${escapeHtml(title)}</h2></div>
    ${action}
  </header>`;
}
function titleRow(title,hint='') { return '<div class="section-title"><h3>'+title+'</h3><small>'+escapeHtml(hint)+'</small></div>'; }
function peopleList(f) {
  const pool=people.filter(p=>p.cur===f.cur && (f.filter==='all'||(f.filter==='positive'?balance(p)>0:balance(p)<0)) && p.name.toLocaleLowerCase('ru').includes(f.query.trim().toLocaleLowerCase('ru')));
  sortPeople(pool,f.peopleOrder);
  return pool.length?pool.map(p=>{
    const next=p.loans.filter(l=>remaining(l)>0&&l.due).sort((a,b)=>a.due.localeCompare(b.due))[0];
    const sub=next?dueLabel(next):p.loans.every(l=>remaining(l)===0)?'Расчёт завершён':'Без срока';
    return '<button class="person-row" data-act="person" data-value="'+p.id+'">'+personAvatar(p)+'<span class="person-text"><strong>'+escapeHtml(p.name)+'</strong><small class="'+(next?.due<TODAY?'late':'')+'">'+sub+'</small></span><span class="money '+(balance(p)<0?'neg':'pos')+'">'+motionAmount('person-'+p.id,balance(p),p.cur,true)+'</span></button>';
  }).join(''):'<p class="empty">'+(f.query?'Никого не нашли. Попробуйте другое имя.':'Здесь пока нет записей для этого фильтра.')+'</p>';
}
function home(f) {
  const balances=people.filter(p=>p.cur===f.cur).map(balance);
  const plus=balances.reduce((s,v)=>s+Math.max(0,v),0),minus=balances.reduce((s,v)=>s+Math.max(0,-v),0);
  const searchLabel=f.query?'Очистить поиск':'Закрыть поиск';
  const topBar=`<div class="apphead debt-home-header">${f.search
    ? `<div class="people-search"><input type="text" inputmode="search" data-bind="query" aria-label="Найти человека" placeholder="Найти человека" value="${escapeHtml(f.query)}">${iconButton('search-clear-or-close',searchLabel,'close')}</div>`
    : '<h2>Долги</h2>'+iconButton('search','Найти человека','search')
  }</div>`;
  const summary=f.search?'':balanceCard('Общий баланс',
      '<select data-bind="cur" aria-label="Валюта обзора">'+options([...new Set([defaultCurrency(),...people.map(p=>p.cur)])],f.cur)+'</select>',
      motionAmount('net',plus-minus,f.cur,true),
      '<div class="summary-grid"><div><span class="legend">'+icon('take',15)+'Мне должны</span><strong>'+motionAmount('credit',plus,f.cur)+'</strong></div><div><span class="legend">'+icon('give',15)+'Я должен</span><strong>'+motionAmount('debit',minus,f.cur)+'</strong></div></div>',coinArt());
  const html=summary+'<div class="section-title"><h3>Люди</h3>'+button('people-sort','Порядок','','chip','settings')+'</div>'+debtChips(debtDirections,f.filter,'filter','Направление долга')+
    '<div class="people">'+peopleList(f)+'</div>';
  return layout(html,{topBar,action:f.search?'':primary('new','Новый долг','plus'),navigation:f.search?'':nav('home')});
}
function setPeopleSearch(i,open) {
  const f=frames[i],panel=sections[i];
  if(open)f.homeScrollBeforeSearch=panel.querySelector('.scroll').scrollTop;
  f.search=open;
  f.query='';
  render(i);
  if(open)panel.querySelector('[data-bind=query]').focus({preventScroll:true});
  else {
    panel.querySelector('.scroll').scrollTop=f.homeScrollBeforeSearch;
    restoreActionFocus(panel,'search');
  }
}
function updatePeopleSearch(i,value) {
  const f=frames[i],panel=sections[i];
  f.query=value;
  panel.querySelector('.people').innerHTML=peopleList(f);
  const button=panel.querySelector('[data-act=search-clear-or-close]');
  button.title=value?'Очистить поиск':'Закрыть поиск';
  button.setAttribute('aria-label',button.title);
}
function layout(body,{action='',navigation='',topBar=''}={}) {
  const cta=action?'<div class="sticky-action">'+action+'</div>':'';
  return '<div class="screen-top">'+status+topBar+'</div>'+
    '<div class="scroll"><div class="screen-body">'+body+'</div></div>'+
    '<div class="screen-dock">'+(navigation?cta:'')+
    '<div class="screen-bottom-fade">'+(navigation||cta)+gesture+'</div></div>';
}
function loanCard(l,p) {
  const left=remaining(l), paid=Math.abs(l.principal)-left;
  const tagClass=left===0?'ok':(l.due && l.due<TODAY?'late':'');
  return '<article class="loan"><div class="loan-top"><div><h4>'+escapeHtml(l.note||'Долг')+'</h4><span class="subtle">'+dateText(l.date)+' · '+(l.principal>0?'Я дал':'Я взял')+'</span></div><button class="tag '+tagClass+'" '+(left?'':'data-motion-row="settled-'+l.id+'"')+' data-act="due" data-value="'+l.id+'" aria-label="Срок и напоминание: '+dueLabel(l)+'">'+icon(left?'date':'check',13)+dueLabel(l)+'</button></div>'+progressTrack('paid-'+l.id,100*paid/Math.abs(l.principal))+'<div class="loan-numbers"><span>Возвращено '+motionAmount('paid-'+l.id,paid,p.cur)+'</span><span>из '+amount(Math.abs(l.principal),p.cur)+'</span></div><p class="subtle">Остаток по записи · <strong>'+motionAmount('left-'+l.id,left,p.cur)+'</strong>'+(l.reminder?' · Напомнить в '+l.reminder:'')+'</p>'+button('loan-actions','Действия с записью',l.id,'text-action','more')+(left?'<button class="btn secondary" data-act="repay" data-value="'+l.id+'">'+icon('take',17)+'Учесть возврат</button>':'')+'</article>';
}
function history(p,f) {
  return orderedLoans(p,f).map(l=>{
    const payments=[...l.returns].sort((a,b)=>a.date.localeCompare(b.date)||a.id-b.id).map(r=>{
      const tag=r.cancelled?'div':'button';
      const label=r.cancelled?'Возврат отменён':l.principal>0?'Получен возврат':'Вернул долг';
      const action=r.cancelled?'':'data-act="undo" data-value="'+r.id+'"';
      return '<'+tag+' class="timeline-row" data-motion-row="return-'+l.id+'-'+r.id+(r.cancelled?'-cancelled':'')+'" '+action+
        '><span class="event-icon return">'+icon('take',16)+'</span><span class="event-text"><strong>'+label+
        '</strong><small>'+dateText(r.date)+'</small></span><span class="money"'+(r.cancelled?' style="text-decoration:line-through"':'')+
        '>'+amount(-Math.sign(l.principal)*r.amount,p.cur,true)+'</span></'+tag+'>';
    }).join('');
    return loanCard(l,p)+(payments?'<div class="activity">'+payments+'</div>':'');
  }).join('');
}
const debtDirections=[['all','Все'],['positive','Мне должны'],['negative','Я должен']];
function debtChips(choices,active,action,label) {
  return '<div class="tabs" role="group" aria-label="'+label+'">'+choices.map(([id,text])=>
    '<button class="chip'+(active===id?' active':'')+'" data-act="'+action+'" data-value="'+id+'" aria-pressed="'+(active===id)+'">'+text+'</button>'
  ).join('')+'</div>';
}
function person(f) {
  const p=people.find(p=>p.id===f.personId),sum=balance(p);
  return layout('<section class="balance"><div class="eyebrow">'+(sum===0?'Баланс с человеком':sum>0?'Мне должны':'Я должен')+' · '+p.cur+'</div><div class="big-money">'+motionAmount('balance',Math.abs(sum),p.cur)+'</div></section>'+
    '<div class="history-actions">'+button('person-share','Поделиться','','chip','give')+'</div>'+
    debtChips([['open','Открытые долги'],['settled','Погашенные долги']],f.personSection,'history-section','Статус долга')+
    '<div class="section-title"><h3>История</h3>'+button('history-sort','Порядок','','chip','settings')+'</div>'+
    debtChips(debtDirections,f.historyFilter,'history-direction','Направление долга')+
    (history(p,f)||empty(f.personSection==='open'?'Нет открытых долгов.':'Нет погашенных долгов.')),
    {action:primary('new','Новый долг','plus'),topBar:backbar(p.name,{action:personMenu(p),avatar:personAvatar(p)})});
}
function field(iconName,label,control,trailing='') {
  return '<div class="field"><span class="field-icon">'+icon(iconName,17)+'</span><label class="field-body"><span class="label">'+label+'</span>'+control+'</label>'+trailing+'</div>';
}
function input(bind,value,placeholder='',type='text',extra='') {
  return '<input type="'+type+'" data-bind="'+bind+'" value="'+escapeHtml(value)+'" placeholder="'+placeholder+'" '+extra+'>';
}
function amountField(bind,value,cur,choose=true) {
  const currency=choose?'<select class="currency" aria-label="Валюта долга" data-bind="d.cur">'+options(Object.keys(currencySigns),cur)+'</select>':'<strong>'+cur+'</strong>';
  return amountFieldLayout('<input inputmode="decimal" data-bind="'+bind+'" aria-label="Сумма" placeholder="0" value="'+escapeHtml(value)+'">', currency);
}
function debtErrorSlot(key) {
  return '<p class="debt-field-error" data-debt-field-error="'+key+'" role="alert" hidden></p>';
}
function scheduleFields(obj,prefix,outstanding=true) {
  const inlineError=prefix==='d.'?debtErrorSlot:()=>'';
  return '<div class="group">'+field('date','Срок возврата',input(prefix+'due',obj.due,'','date'))+inlineError('due')+
    '<label class="field"><span class="field-icon">'+icon('bell',17)+'</span><span class="field-body">Напомнить мне</span><input data-bind="'+prefix+'remind" type="checkbox" '+(obj.remind?'checked':'')+' '+(!obj.due||!outstanding?'disabled':'')+'></label>'+inlineError('remind')+
    (obj.remind?field('bell','Время в день срока',input(prefix+'time',obj.time,'','time'))+inlineError('time'):'')+'</div>'+permissionPrompt(obj,prefix);
}
function newDebt(f) {
  const d=f.d;
  const owner=people.find(p=>p.id===(d.fixed||d.personId));
  const action=d.fixed?'':owner?iconButton('clear-debt-person','Изменить выбор человека','close'):button('contacts','Контакты','','debt-contact-action');
  const personField=field('user',owner&&!d.fixed?'Человек выбран':'Человек',input('d.name',owner?.name||d.name,'Имя или название','text',owner?'readonly':'maxlength="80"'),action);
  const suggestions=owner?'':debtErrorSlot('name')+'<div class="debt-person-suggestions" data-debt-person-suggestions>'+debtPersonSuggestions(d)+'</div>'+
    '<div data-draft-person-photo>'+debtDraftPhoto(d)+'</div>';
  const negative=d.dir<0?' debt-negative':'';
  return layout('<div class="segment'+negative+'">'+[[1,'Мне должны','take'],[-1,'Я должен','give']].map(([v,l,i])=>'<button data-act="direction" data-value="'+v+'" aria-pressed="'+(d.dir===v)+'" class="'+(d.dir===v?'active':'')+'">'+icon(i,16)+l+'</button>').join('')+'</div>'+
    '<div class="debt-amount'+negative+'">'+amountField('d.sum',d.sum,owner?.cur||d.cur,!owner)+debtErrorSlot('sum')+'</div><div class="group">'+personField+suggestions+
    field('note','Комментарий',input('d.note',d.note,'На что одолжили','text','maxlength="160"'))+field('date','Дата записи',input('d.date',d.date,'','date'))+debtErrorSlot('date')+'</div>'+titleRow('Возврат')+scheduleFields(d,'d.')+(f.error?'<p role="alert" class="error">'+f.error+'</p>':''),
    {action:button('save','Сохранить долг','','btn'+negative,'check'),topBar:backbar(d.editId?'Редактировать долг':screenNames.new)});
}
function findLoan(f,id) { return people.find(p=>p.id===f.personId).loans.find(l=>l.id===id); }
function sheet(f) {
  if(f.sheet.kind==='flow')return flowDialog(f);
  const s=f.sheet, p=people.find(p=>p.id===f.personId), l=findLoan(f,s.loan);
  const name={repay:'Учесть возврат',due:'Срок и напоминание',undo:'Отменить возврат?'}[s.kind];
  let body='<div class="sheet-head"><h3>'+name+'</h3>'+iconButton('close','Закрыть','close')+'</div><p class="note">'+escapeHtml(p.name)+' · '+escapeHtml(l.note||'Долг')+'</p>';
  if(s.kind==='repay') {
    body+='<div class="setting group"><span>Остаток по записи</span><strong>'+amount(remaining(l),p.cur)+'</strong></div>'+amountField('s.sum',s.sum,p.cur,false)+
      '<button class="chip" data-act="full">Весь остаток</button><p class="note" data-live="remaining"></p>';
  } else if(s.kind==='due') {
    body+=scheduleFields(s,'s.',remaining(l)>0);
  } else {
    const r=l.returns.find(r=>r.id===s.returnId);
    body+='<p class="note">После отмены сумма '+amount(r.amount,p.cur)+' вернётся в остаток этого долга. Запись об отмене останется в истории.</p>';
  }
  if(f.error) body+='<p class="error" role="alert">'+f.error+'</p>';
  body+=primary('confirm',s.kind==='repay'?'Записать возврат':s.kind==='due'?'Сохранить':'Отменить возврат',s.kind==='undo'?'reset':'check');
  return '<div class="overlay"><section class="sheet" role="dialog" aria-modal="true" aria-label="'+name+'">'+body+'</section></div>';
}
const board=document.querySelector('#board');
const mobile=document.querySelector('.mobile-switch');
const sections=[];
const screenBars='.screen-top,.screen-dock';
function updateScreenInset(bar) {
  const height=bar.offsetHeight;
  const edge=bar.classList.contains('screen-top')?'top':'bottom';
  if(height)bar.parentElement.style.setProperty('--screen-'+edge+'-inset',height+'px');
}
const screenBarObserver=new ResizeObserver(entries=>{
  for(const {target} of entries)updateScreenInset(target);
});
for(let i=0;i<3;i++) {
  const section=document.createElement('section');
  section.className='panel'; section.dataset.index=i; board.appendChild(section); sections.push(section);
  const button=document.createElement('button'); button.textContent=['Долги','Финансы','Цели'][i];button.onclick=()=>{currentPanel=i;updateTools()};mobile.appendChild(button);
}
function updateTools() {
  document.documentElement.dataset.dark=String(dark);
  document.querySelector('#after').setAttribute('aria-pressed',String(!before));
  document.querySelector('#before').setAttribute('aria-pressed',String(before));
  document.querySelector('.haptic-review').hidden=before;
  const theme=document.querySelector('#theme');
  theme.innerHTML=icon(dark?'sun':'moon',17);theme.setAttribute('aria-label',dark?'Светлая тема':'Тёмная тема');theme.disabled=before;
  document.querySelector('#reset').innerHTML=icon('reset',17);
  sections.forEach((s,i)=>{s.classList.toggle('chosen',i===currentPanel);mobile.children[i].setAttribute('aria-pressed',String(i===currentPanel));});
}
function updateRemaining(i) {
  const f=frames[i], area=sections[i].querySelector('[data-live=remaining]');
  if(!area||f.sheet?.kind!=='repay')return;
  area.hidden=Boolean(f.error);
  if(area.hidden)return;
  const p=people.find(p=>p.id===f.personId), max=remaining(findLoan(f,f.sheet.loan)),n=parseAmount(f.sheet.sum);
  area.textContent=n>max?'Сумма больше остатка.':'После возврата останется '+amount(Math.max(0,max-n),p.cur)+'.';
}
function render(i,keepScroll=false,committed=false) {
  const f=frames[i],target=sections[i], sameRoute=target.dataset.route===f.route;
  const navigation=f.navigation;
  f.navigation=null;f.trigger=null;
  const outgoing=target.querySelector('.overlay:not(.leaving)');
  const lastSegment=target.querySelector('.segment .active')?.dataset.value;
  const debtDirectionChanged=f.route==='new'&&sameRoute&&lastSegment&&Number(lastSegment)!==f.d.dir&&!reducedMotion.matches;
  const debtColorSelectors=['.debt-amount .amount-field','[data-act=save]','.segment button:first-child','.segment button:last-child'];
  const oldDebtColors=debtDirectionChanged?debtColorSelectors.map(selector=>{
    const style=getComputedStyle(target.querySelector(selector));
    return {backgroundColor:style.backgroundColor,color:style.color};
  }):[];
  const oldIndicator=debtDirectionChanged?getComputedStyle(target.querySelector('.segment'),'::before').backgroundColor:'';
  const lastRange=target.querySelector('.period-line')?.dataset.range;
  target.dataset.route=before?'':f.route;
  const y=keepScroll&&sameRoute?(target.querySelector('.scroll')?.scrollTop||0):0;
  for(const bar of target.querySelectorAll(screenBars))screenBarObserver.unobserve(bar);
  if(before) {
    const select='<select class="compare-select" data-bind="beforeScreen" aria-label="Снимок текущего приложения">'+Object.entries(screenNames).map(([k,v])=>'<option value="'+k+'" '+(f.beforeScreen===k?'selected':'')+'>'+v+'</option>').join('')+'</select>';
    target.innerHTML='<div class="panel-heading">'+select+'</div><div class="phone before"><img class="thumb" src="before/'+f.beforeScreen+'.webp" alt="Текущий экран: '+screenNames[f.beforeScreen]+'"></div><div class="before-mobile">'+select+'</div>';
    return;
  }
  const listState=listStateControl(f);
  const html=listState&&f.listState&&f.listState!=='Данные'?reviewList(f):f.route==='home'?home(f):f.route==='person'?person(f):f.route==='new'?newDebt(f):flowViews[f.route](f);
  target.innerHTML='<div class="panel-heading">'+(screenNames[f.route]||flowNames[f.route])+listState+'</div><div class="phone">'+html+(f.sheet?sheet(f):'')+'</div>';
  if(f.route==='new')updateDebtErrors(i);
  for(const bar of target.querySelectorAll(screenBars)){
    updateScreenInset(bar);
    screenBarObserver.observe(bar);
  }
  target.querySelector('.scroll').scrollTop=navigation?.origin?.y ?? y;
  if(navigation && !sameRoute) {
    const {origin,direction}=navigation;
    const heading=target.querySelector('.phone h2');
    if(heading)heading.tabIndex=-1;
    restoreActionFocus(target,origin?.act,origin?.value,heading);
    enterRoute(target,direction==='back');
  }
  if(f.sheet) {
    for(const element of target.querySelector('.phone').children) if(!element.classList.contains('overlay'))element.inert=true;
    updateRemaining(i);
  } else if(outgoing && sameRoute) {
    leaveSheet(target.querySelector('.phone'),outgoing);
  }
  updateLedgerMotion(f,target,committed);
  if(sameRoute) {
    const selected=target.querySelector('.segment .active');
    if(selected && lastSegment && selected.dataset.value!==lastSegment) {
      selected.focus({preventScroll:true});
      selected.parentElement.classList.add('segment-moving');
      if(debtDirectionChanged) {
        selected.parentElement.classList.add('debt-direction-moving');
        selected.parentElement.style.setProperty('--previous-fill',oldIndicator);
        debtColorSelectors.forEach((selector,index)=>{
          const element=target.querySelector(selector),style=getComputedStyle(element);
          element.animate([oldDebtColors[index],{backgroundColor:style.backgroundColor,color:style.color}],{duration:200});
        });
      }
    }
    const range=target.querySelector('.period-line')?.dataset.range;
    if(!reducedMotion.matches && lastRange && range && lastRange!==range) {
      const x=range>lastRange?12:-12;
      for(const el of target.querySelectorAll('.period-current,.hero-money,.people,.scroll .empty')) {
        el.animate([{opacity:.45,transform:`translateX(${x}px)`},{opacity:1,transform:'none'}],{duration:200,easing:settleEase});
      }
    }
  }
}
function renderAll(keep=false,committed=false) { updateTools();frames.forEach((_,i)=>render(i,keep,committed)); }
function toast(i,message) {
  const phone=sections[i].querySelector('.phone'), t=document.createElement('div');
  t.className='toast';t.setAttribute('role','status');t.textContent=message;phone.appendChild(t);
  setTimeout(()=>t.remove(),2400);
}
function toggleTheme() {dark=!dark;prefs.theme=dark?'Тёмная':'Светлая';persistDemo();renderAll(true);}
document.querySelector('#theme').onclick=toggleTheme;
document.querySelector('#before').onclick=()=>{before=true;frames.forEach(f=>f.beforeScreen=screenNames[f.route]?f.route:baseRoute(f.route));renderAll();};
document.querySelector('#after').onclick=()=>{before=false;renderAll();};
document.querySelector('#reset').onclick=()=>{
  const i=currentPanel;
  if(before){before=false;renderAll();}
  confirmAction(i,document.querySelector('#reset'),'Сбросить демоданные?','Записи этого прототипа будут заменены исходными примерами.',()=>{
    resetDemo();frames=startScreens.map(frame);currentPanel=0;before=false;persistDemo();renderAll();toast(0,'Демоданные восстановлены');
  },'Сбросить');
};
function setRoute(f,to,direction) {
  if(direction==='forward') {
    const panel=sections[frames.indexOf(f)];
    const trigger=f.sheet?{act:f.sheet.opener,value:f.sheet.openerValue}:f.trigger;
    f.returnPoints.set(f.route,{
      y:panel.querySelector('.scroll').scrollTop, ...trigger
    });
  }
  const origin=direction==='back'?f.returnPoints.get(to):null;
  if(direction==='back')f.returnPoints.delete(to);
  if(!direction)f.returnPoints.clear();
  f.navigation=direction?{direction,origin}:null;
  f.route=to;f.sheet=null;f.error='';f.listState='Данные';
}
function refreshData(i,message) {
  changed(i,message);
}
function validSchedule(s) {
  if(s.askNotify)return 'Выберите, разрешать ли уведомления.';
  if(!s.remind) return '';
  if(!s.due || !s.time) return 'Укажите день и время напоминания.';
  if(s.due+'T'+s.time<=TODAY+'T09:41') return 'Выберите будущее время или выключите напоминание.';
  return '';
}
function fail(i,message,amountRule) {
  frames[i].error=message;
  render(i,true);
  const panel=sections[i], error=panel.querySelector('.sheet .error') || panel.querySelector('.error');
  if(amountRule) {
    const amountInput=panel.querySelector('.sheet .amount-field input');
    error.id='amount-error-'+i;
    amountInput.dataset.currency=amountRule.currency;
    amountInput.dataset.maximum=amountRule.maximum;
    amountInput.setAttribute('aria-invalid','true');
    amountInput.setAttribute('aria-describedby',error.id);
    amountInput.focus({preventScroll:true});
  }
  error?.scrollIntoView({block:'nearest'});
}
function openSheet(i,s,button) {
  const f=frames[i];f.sheet={...s,opener:button.dataset.act,openerValue:button.dataset.value};f.error='';
  render(i,true);enterSheet(sections[i]);sections[i].querySelector(s.cancelBelow?'.sheet [data-act=close]':'.sheet button')?.focus({preventScroll:true});
}
function restoreActionFocus(panel,act,value,fallback) {
  const trigger=act && [...panel.querySelectorAll('[data-act]')].find(el=>el.dataset.act===act && el.dataset.value===value);
  const visible=trigger?.closest('.context-actions')?.querySelector('summary') || trigger || fallback;
  visible?.focus({preventScroll:true});
}
function closeSheet(i) {
  const f=frames[i],s=f.sheet;f.sheet=null;f.error='';render(i,true);
  restoreActionFocus(sections[i],s.opener,s.openerValue);
}
function confirmSheet(i) {
  const f=frames[i],s=f.sheet,l=findLoan(f,s.loan);
  let message='';
  if(s.kind==='repay') {
    const currency=people.find(p=>p.id===f.personId).cur, maximum=remaining(l);
    const n=readMoney(s.sum,currency);
    if(!n) return fail(i,'Введите сумму больше нуля в пределах остатка.',{currency,maximum});
    if(n>maximum) {
      playHaptic('reject');
      return fail(i,'Сумма превышает остаток по записи.',{currency,maximum});
    }
    l.returns.push({id:serial++,amount:n,date:TODAY,cancelled:false});
    if(!remaining(l))l.reminder='';
    message='Возврат записан';
  } else if(s.kind==='due') {
    const issue=validSchedule(s);if(issue)return fail(i,issue);
    l.due=s.due;l.reminder=remaining(l)&&s.remind?s.time:'';message='Срок и напоминание сохранены в макете';
  } else {
    l.returns.find(r=>r.id===s.returnId).cancelled=true;message='Возврат отменён';
  }
  f.sheet=null;f.error='';refreshData(i,message);
  if(s.kind==='repay' && remaining(l)===0) playHaptic('confirm');
}
function debtFormValidation(d) {
  const owner=people.find(p=>p.id===(d.fixed||d.personId));
  const n=readMoney(d.sum,owner?.cur||d.cur,true);
  const errors={};
  if(!d.sum.trim())errors.sum='Введите сумму.';
  else if(n===null)errors.sum='Введите допустимую сумму для этой валюты.';
  else if(n===0)errors.sum='Долг не может быть равен нулю.';
  if(!owner&&!d.name.trim())errors.name='Введите имя.';
  else if(!owner&&d.personError)errors.name='Выберите нужного человека из совпадений.';
  if(!d.date)errors.date='Выберите дату записи.';
  else if(d.date>TODAY)errors.date='Дата записи не может быть в будущем.';
  if(d.due&&d.date&&d.due<d.date)errors.due='Срок возврата не может быть раньше даты записи.';
  if(d.askNotify)errors.remind='Выберите, разрешать ли уведомления.';
  else if(d.remind&&!d.time)errors.time='Укажите время напоминания.';
  else if(d.remind&&d.due+'T'+d.time<=TODAY+'T09:41')errors.time='Выберите будущее время или выключите напоминание.';
  return {n,errors,owner};
}
function updateDebtErrors(i) {
  const d=frames[i].d, panel=sections[i];
  const errors=d.validating?debtFormValidation(d).errors:{};
  for(const message of panel.querySelectorAll('[data-debt-field-error]')) {
    const key=message.dataset.debtFieldError, text=errors[key]||'';
    const control=panel.querySelector('[data-bind="d.'+key+'"]');
    message.id='debt-field-error-'+i+'-'+key;
    message.hidden=!text;
    if(message.textContent!==text)message.textContent=text;
    if(text) {
      control.setAttribute('aria-invalid','true');
      control.setAttribute('aria-describedby',message.id);
    } else {
      control.removeAttribute('aria-invalid');
      control.removeAttribute('aria-describedby');
    }
  }
}
function saveDebt(i) {
  const f=frames[i],d=f.d,name=d.name.trim();
  d.validating=true;
  const validation=debtFormValidation(d);
  const {n}=validation;
  let p=validation.owner;
  if(!p&&Object.keys(validation.errors).length===0) {
    const matches=exactDebtPeople(d);
    if(matches.length>1) {
      d.personError=true;
      validation.errors.name='Выберите нужного человека из совпадений.';
    } else p=matches[0];
  }
  const first=Object.keys(validation.errors)[0];
  if(first) {
    updateDebtPersonOptions(i);
    updateDebtErrors(i);
    sections[i].querySelector('[data-debt-field-error="'+first+'"]').scrollIntoView({block:'center'});
    return;
  }
  if(d.editId)return updateEditedDebt(i,n);
  if(!p){p={id:serial++,name,art:people.length,cur:d.cur,photo:d.photo||undefined,loans:[]};people.unshift(p);}
  p.loans.unshift({id:serial++,principal:n*d.dir,note:d.note.trim(),date:d.date,due:d.due,reminder:d.remind?d.time:'',returns:[]});
  f.cur=p.cur;f.personId=p.id;f.personSection='open';
  if(!d.fixed)f.historyOrder=prefs.historyOrder;
  if(!d.fixed||f.historyFilter!==(d.dir>0?'positive':'negative'))f.historyFilter='all';
  setRoute(f,'person',d.fixed?'back':'replace');refreshData(i,'Долг сохранён');
}
board.addEventListener('click',event=>{
  const target=event.target.closest('.panel');if(!target)return;
  const i=Number(target.dataset.index),f=frames[i],b=event.target.closest('[data-act]');
  if(event.target.classList.contains('overlay'))return closeSheet(i);
  if(!b)return;
  const action=b.dataset.act,value=b.dataset.value;
  f.trigger={act:action,value};
  if(b.closest('.segment')) {
    if(b.getAttribute('aria-pressed')==='true')return;
    playHaptic('selection');
  }
  if(Object.hasOwn(flowActions,action))return flowActions[action](i,value,b);
  switch(action) {
    case 'theme':return toggleTheme();
    case 'nav': {
      if (f.route === value) return;
      const previousLeft = target.querySelector('.nav-indicator').getBoundingClientRect().left;
      f.trail=[];setRoute(f, value);
      render(i);
      target.querySelector('.nav [aria-current=page]').focus({preventScroll:true});
      if (window.matchMedia('(prefers-reduced-motion: no-preference)').matches) {
        const indicator = target.querySelector('.nav-indicator');
        const distance = previousLeft - indicator.getBoundingClientRect().left;
        indicator.animate(
          [{transform:`translateX(${distance}px)`}, {transform:'translateX(0)'}],
          {duration:240, easing:'cubic-bezier(.2,.8,.2,1)'}
        );
      }
      return;
    }
    case 'search':return setPeopleSearch(i,true);
    case 'search-clear-or-close':{
      if(!f.query)return setPeopleSearch(i,false);
      const input=target.querySelector('[data-bind=query]');
      input.value='';
      updatePeopleSearch(i,'');
      input.focus({preventScroll:true});
      return;
    }
    case 'filter':f.filter=value;render(i,true);return;
    case 'person':f.personId=Number(value);f.personSection='open';f.historyFilter='all';f.historyOrder=prefs.historyOrder;setRoute(f,'person','forward');break;
    case 'back':if(f.trail.length)return pop(i);setRoute(f,f.route==='new'?f.back:'home','back');break;
    case 'new':{
      f.back=f.route;f.d=draft();
      if(f.route==='person'){const p=people.find(p=>p.id===f.personId);Object.assign(f.d,{fixed:p.id,name:p.name,cur:p.cur});}
      setRoute(f,'new','forward');break;
    }
    case 'direction':f.d.dir=Number(value);render(i,true);return;
    case 'save':return saveDebt(i);
    case 'repay':return openSheet(i,{kind:'repay',loan:Number(value),sum:''},b);
    case 'due':{
      const l=findLoan(f,Number(value));
      return openSheet(i,{kind:'due',loan:l.id,due:l.due,remind:!!l.reminder,time:l.reminder||'09:00'},b);
    }
    case 'undo':{
      const p=people.find(p=>p.id===f.personId),l=p.loans.find(l=>l.returns.some(r=>r.id===Number(value)));
      return openSheet(i,{kind:'undo',loan:l.id,returnId:Number(value)},b);
    }
    case 'full':{
      const s=f.sheet;s.sum=String(remaining(findLoan(f,s.loan))/100).replace('.',',');
      f.error='';
      render(i,true);target.querySelector('.sheet [data-act=full]').focus({preventScroll:true});return;
    }
    case 'confirm':return confirmSheet(i);
    case 'close':return closeSheet(i);
    case 'finance-dir':f.financeDir=value;render(i,true);return;
    default:return;
  }
  render(i);
});
function bind(event) {
  const el=event.target;
  if(event.type==='input' && el.matches('.sheet .amount-field input[aria-invalid="true"]')) {
    const value=readMoney(el.value,el.dataset.currency);
    if(value!==null && value<=Number(el.dataset.maximum)) {
      document.getElementById(el.getAttribute('aria-describedby'))?.remove();
      el.removeAttribute('aria-invalid');el.removeAttribute('aria-describedby');
      frames[Number(el.closest('.panel').dataset.index)].error='';
    }
  }
  if(el.hasAttribute('data-list-state')){if(event.type==='change'){const i=Number(el.closest('.panel').dataset.index);frames[i].listState=el.value;render(i);}return;}
  if(el.dataset.field)return setField(event,el,Number(el.closest('.panel').dataset.index));
  if(el.dataset.file&&event.type==='change'&&el.files?.length){
    const i=Number(el.closest('.panel').dataset.index),file=el.files[0];
    if(el.dataset.file==='goal-photo')return selectPhoto(i,file);
    if(el.dataset.file==='person-photo')return selectPersonPhoto(i,file);
    if(el.dataset.file==='debt-draft-photo')return selectDebtDraftPhoto(i,file);
    return readBackupFile(i,file);
  }
  const key=el.dataset.bind;if(!key)return;
  const i=Number(el.closest('.panel').dataset.index),f=frames[i];
  if(key==='beforeScreen'||key==='cur') {if(event.type==='change'){f[key]=el.value;render(i,true);}return;}
  if(key==='query'){
    if(event.type==='input')updatePeopleSearch(i,el.value);
    return;
  }
  if(key==='d.name'){
    if(event.type==='input'){
      f.d.name=el.value;
      f.d.personError=false;
      updateDebtPersonOptions(i);
      updateDebtErrors(i);
    }
    return;
  }
  const [scope,prop]=key.split('.'),obj=scope==='s'?f.sheet:f.d;
  if(!obj)return;
  obj[prop]=el.type==='checkbox'?el.checked:el.value;
  if(!obj.due)obj.remind=false;
  if(prop==='remind'||prop==='due')obj.askNotify=!!obj.remind&&notificationAccess!=='granted';
  if(event.type==='change'&&['due','remind','cur'].includes(prop)){
    if(key==='d.cur')obj.personError=false;
    render(i,true);
    if(key==='d.remind'&&obj.remind)sections[i].querySelector('[data-bind="d.time"]')?.scrollIntoView({
      block:'center',behavior:reducedMotion.matches?'instant':'smooth'
    });
  }
  if(scope==='d')updateDebtErrors(i);
  if(key==='s.sum')updateRemaining(i);
}
board.addEventListener('input',bind);board.addEventListener('change',bind);
document.addEventListener('click',event=>{
  for(const menu of board.querySelectorAll('.context-actions[open]')) {
    if(!menu.contains(event.target))menu.open=false;
  }
});
document.addEventListener('keydown',event=>{
  const section=event.target.closest('.panel');if(!section)return;
  const i=Number(section.dataset.index),f=frames[i];
  const menu=section.querySelector('.context-actions[open]');
  if(menu && event.key==='Escape'){
    event.preventDefault();menu.open=false;menu.querySelector('summary').focus();return;
  }
  if(f.sheet && event.key==='Escape'){event.preventDefault();closeSheet(i);return;}
  if(f.route==='home' && f.search && event.key==='Escape'){event.preventDefault();setPeopleSearch(i,false);return;}
  if(f.sheet && event.key==='Tab'){
    const all=[...section.querySelectorAll('.sheet button,.sheet input,.sheet select')].filter(el=>!el.disabled);
    const first=all[0],last=all[all.length-1];
    if(event.shiftKey&&event.target===first){event.preventDefault();last.focus();}
    if(!event.shiftKey&&event.target===last){event.preventDefault();first.focus();}
  }
});
renderAll();

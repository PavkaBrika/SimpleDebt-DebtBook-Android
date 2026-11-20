'use strict';
// Classic scripts keep the design review usable offline from index.html.
const flowViews = {}, flowActions = {};
const flowNames = {categoryHistory:'История категории', financeForm:'Финансовая запись', categoryForm:'Новая категория', goalDetail:'Детали цели', goalForm:'Форма цели', backup:'Резервная копия', security:'Защита', pin:'Вход по PIN', recovery:'Восстановление PIN'};
const flowRoots = {categoryHistory:'finance',financeForm:'finance',categoryForm:'finance',goalDetail:'goals',goalForm:'goals',backup:'settings',security:'settings',pin:'settings',recovery:'settings'};
const currencyCodes = 'USD EUR RUB BYN UAH KZT JPY GBP AUD CAD CHF CNY SEK MXN PLN AED RON SAR RSD TJS TRY UZS MNT CZK AZN AMD BGN ILS'.split(' ');
function featureFrame() {
  return {financeDir:'Расходы',financeCur:'RUB',period:'День',day:TODAY,goalId:null,categoryId:null,trail:[],peopleOrder:'Исходный порядок',historyOrder:'Дата операции ↓',historyFilter:'Все'};
}
function go(i, route) {
  const f=frames[i]; f.trail.push(f.route); setRoute(f,route); render(i);
}
function pop(i) {
  const f=frames[i]; setRoute(f,f.trail.pop() || flowRoots[f.route] || 'home'); repairRoute(f); render(i);
}
function button(action,label,value='',className='chip',symbol='') {
  return `<button class="${className}" data-act="${action}" data-value="${escapeHtml(value)}">${symbol?icon(symbol,16):''}${escapeHtml(label)}</button>`;
}
function pairButtons(...buttons) { return '<div class="button-pair">'+buttons.join('')+'</div>'; }
function errorText(f) {return f.error?'<p role="alert" class="error">'+escapeHtml(f.error)+'</p>':'';}
function empty(text) {return '<div class="group empty">'+escapeHtml(text)+'</div>';}
function picker(scope, values, value, label) {
  return `<select aria-label="${escapeHtml(label)}" data-field="${scope}">${values.map(v=>`<option ${v===value?'selected':''}>${escapeHtml(v)}</option>`).join('')}</select>`;
}
function entry(scope, value='', type='text', extra='') {
  return `<input type="${type}" data-field="${scope}" value="${escapeHtml(value)}" ${extra}>`;
}
function rowAction(action,label,value='',detail='',symbol='chevron') {
  return `<button class="setting menu-row" data-act="${action}" data-value="${escapeHtml(value)}"><strong>${escapeHtml(label)}</strong><span>${escapeHtml(detail)} ${icon(symbol,15)}</span></button>`;
}
function segment(action, options, current) {
  return '<div class="segment">'+options.map(option=>button(action,option,option,current===option?'active':'')).join('')+'</div>';
}
function editAmount(scope,value,cur) {
  return '<div class="amount-field"><span class="eyebrow">Сумма</span><div class="amount-line">'+entry(scope,value,'text','inputmode="decimal" aria-label="Сумма" placeholder="0"')+'<strong>'+escapeHtml(cur)+'</strong></div></div>';
}
function showDialog(i,title,content,b,submit=null,cta='Сохранить',fields={}) {
  openSheet(i,{kind:'flow',title,content,submit,cta,...fields},b);
}
function confirmAction(i,b,title,description,submit,label='Удалить') {
  showDialog(i,title,()=>'<p class="note">'+escapeHtml(description)+'</p>',b,submit,label);
}
function flowDialog(f) {
  const s=f.sheet;
  return '<div class="overlay"><section class="sheet" role="dialog" aria-modal="true" aria-label="'+escapeHtml(s.title)+'"><div class="sheet-head"><h3>'+escapeHtml(s.title)+'</h3>'+iconButton('close','Закрыть','close')+'</div>'+s.content(s)+errorText(f)+(s.submit?primary('flow-submit',s.cta,'check'):'')+'</section></div>';
}
flowActions['flow-submit']=(i)=>frames[i].sheet.submit(i,frames[i].sheet);
function setField(event,el,i) {
  const [scope,key]=el.dataset.field.split('.');
  const state=scope==='frame'?frames[i]:frames[i][scope];
  if(!state)return;
  state[key]=el.type==='checkbox'?el.checked:el.value;
  if(scope==='sheet'&&key==='query'&&event.type==='input'){
    const rows=sections[i].querySelector('[data-contacts]');if(rows)rows.innerHTML=contactRows(el.value);
  }
  if(scope==='categoryDraft'&&key==='name')sections[i].querySelector('.category-sample strong').textContent=el.value||'Категория';
  if(event.type==='change' && (scope==='frame'||key==='emojiGroup'||el.dataset.refresh))render(i,true);
}
function persistDemo() {
  try {localStorage.setItem('simpledebt-bubble-v2',JSON.stringify(demoSnapshot()));return true;} catch {return false;}
}
function demoSnapshot() {
  return {format:'SimpleDebt-design-2',people,finance:ledger,goals:savings,preferences:{...prefs},serial};
}
function applySnapshot(data) {
  people=data.people;ledger=data.finance;savings=data.goals;prefs=data.preferences;serial=data.serial;
}
function baseRoute(route) {return flowRoots[route] || (route==='person'||route==='new'?'home':route);}
function repairRoute(f) {
    const missingPerson=!people.some(p=>p.id===f.personId);
    if((f.route==='person'||(f.route==='new'&&f.d.fixed))&&missingPerson)setRoute(f,'home');
    if((f.route==='goalDetail'||(f.route==='goalForm'&&f.goalDraft?.id))&&!savings.some(g=>g.id===f.goalId))setRoute(f,'goals');
    if(f.route==='categoryHistory'&&!ledger.categories.some(c=>c.id===f.categoryId))setRoute(f,'finance');
    if(f.route==='financeForm'&&f.record?.id&&!ledger.entries.some(e=>e.id===f.record.id))setRoute(f,'finance');
    if(!Object.hasOwn(flowRoots,f.route)&&f.route!=='person'&&f.route!=='new')f.trail=[];
}
function syncFrames(i) {
  frames.forEach((f,j)=>{
    if(j!==i || f.sheet?.kind==='flow') f.sheet=null;
    repairRoute(f);
  });
}
function changed(i,message) {
  frames[i].error='';syncFrames(i);
  const stored=persistDemo();renderAll(true);toast(i,stored?message:message+' · Только в этой вкладке');
}
function newId(){return serial++;}
function moneyInput(cents){return String(cents/100).replace('.',',');}
function readMoney(text,cur,zero=false) {
  const trimmed=String(text).trim().replace(/[\s\u00a0]/g,'');
  const digits=cur==='JPY'?0:2;
  if(!(digits?/^\d{1,10}([.,]\d{1,2})?$/:/^\d{1,10}$/).test(trimmed))return null;
  const cents=parseAmount(trimmed);
  return cents > 0 || zero ? cents : null;
}
// Review controls sit outside the phone and only affect the displayed list state.
const reviewableLists=['home','person','finance','categoryHistory','goals','goalDetail'];
function listStateControl(f){
  if(!reviewableLists.includes(f.route))return '';
  return '<label class="state-picker">Вид списка <select data-list-state aria-label="Состояние списка">'+['Данные','Загрузка','Пусто','Ошибка'].map(state=>'<option '+(state===(f.listState||'Данные')?'selected':'')+'>'+state+'</option>').join('')+'</select></label>';
}
function reviewList(f){
  const root=baseRoute(f.route), main=f.route===root;
  const titles={home:'Долги',finance:'Финансы',goals:'Цели',person:people.find(p=>p.id===f.personId)?.name,categoryHistory:categoryById(f.categoryId)?.name,goalDetail:goalById(f.goalId)?.name};
  const title=titles[f.route], h=main?heading(title):backbar(title);
  if(f.listState==='Загрузка')return layout(h+'<div class="skeleton" role="status" aria-label="Загрузка"><i></i><i></i><i></i></div>','',main?nav(root):'');
  if(f.listState==='Ошибка')return layout(h+empty('Не удалось загрузить записи.')+primary('list-retry','Повторить','reset',true),'',main?nav(root):'');
  const labels={home:'В книге пока нет долгов.',finance:'За этот период записей нет.',goals:'Целей пока нет.',person:'История долгов пуста.',categoryHistory:'Операций пока нет.',goalDetail:'Пополнений и снятий пока нет.'};
  const actions={home:['new','Новый долг'],finance:['record-new',f.financeDir==='Расходы'?'Добавить расход':'Добавить доход'],goals:['goal-new','Новая цель'],person:['new','Новый долг'],categoryHistory:['record-new','Добавить операцию']};
  const action=actions[f.route];
  return layout(h+empty(labels[f.route]),action?primary(action[0],action[1],'plus'):'',main?nav(root):'');
}
flowActions['list-retry']=i=>{frames[i].listState='Данные';render(i);};

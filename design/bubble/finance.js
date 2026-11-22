'use strict';
const directions=['Расходы','Доходы'];
const periods=['День','Неделя','Месяц','Год'];
const categoryColors=['#f1d4bd','#d7e7c1','#d6e6f1','#e4d7ef','#f1e5bf','#bfe3d7','#eed0d3','#ccd6ec'];
const emojiGroups={"Еда": ["🍽", "🍔", "🍕", "☕", "🎂", "🍜", "🍦", "🍷", "🍺", "🍪"], "Покупки": ["🎁", "💈", "🛒", "👕", "👟", "👜", "💄", "🛍", "🍭", "🍩"], "Досуг": ["❤", "🎬", "🎉", "🔥", "🎮", "🎵", "🎸", "🎨", "⭐", "🥂"], "Здоровье": ["🏋", "⚽", "🏀", "🎾", "💊", "🩺", "💉", "🏃", "🚴", "🧘"], "Учёба": ["🎓", "📚", "📖", "✏", "💻", "📱", "📷", "🔬", "🧪", "📝"], "Финансы": ["🏛", "🏦", "💰", "💳", "💸", "💼", "📈", "📊", "🔔", "🧾"], "Дом": ["🏠", "🏢", "🏭", "⚡", "💡", "🔧", "🔨", "🚗", "✈", "🔑"], "Природа": ["🐶", "🐱", "🌳", "🌻", "🏖", "🌍", "🕯", "🌸", "🦋", "🍄"]};
const categoryPalette=["#EF9A9A", "#F48FB1", "#CE93D8", "#B39DDB", "#9FA8DA", "#90CAF9", "#81D4FA", "#80DEEA", "#80CBC4", "#A5D6A7", "#C5E1A5", "#E6EE9C", "#FFF59D", "#FFE082", "#FFCC80", "#FFAB91", "#BCAAA4", "#EEEEEE", "#B0BEC5", "#FF8A80", "#FF80AB", "#EA80FC", "#A7FFEB", "#B9F6CA"];
function freshLedger() {
  const defs=[['Продукты','🍽️'],['Транспорт','🚇'],['Кафе','☕'],['Здоровье','❤️'],['Развлечения','🎬'],['Дом','🏠'],['Образование','🎓'],['Подарки','🎁'],['Другое','🛍️'],['Зарплата','💵'],['Подарки','🎁'],['Инвестиции','💼'],['Другое','🏦']];
  const categories=defs.map(([name,emoji],i)=>({id:i+1,name,emoji,color:categoryColors[i%categoryColors.length],dir:directions[Number(i>=9)]}));
  const entries=[[1,285000,'Продукты на неделю'],[1,80000,'Рынок'],[2,124000,'Проездной'],[3,96000,'Обед'],[10,7400000,'Аванс']].map(([category,cents,note],i)=>({id:i+1,category,cents,note,date:TODAY,cur:'RUB'}));
  return {categories,entries};
}
let ledger;
function dateUTC(day){return new Date(day+'T12:00:00Z');}
function isoDay(date){return date.toISOString().slice(0,10);}
function addDays(day,n){const d=dateUTC(day);d.setUTCDate(d.getUTCDate()+n);return isoDay(d);}
function periodRange(f) {
  const first=f.day.slice(0,8)+'01';
  if(f.period==='Неделя'){const start=addDays(f.day,-((dateUTC(f.day).getUTCDay()+6)%7));return [start,addDays(start,6)];}
  if(f.period==='Месяц'){const d=dateUTC(first);d.setUTCMonth(d.getUTCMonth()+1,0);return [first,isoDay(d)];}
  if(f.period==='Год')return [f.day.slice(0,4)+'-01-01',f.day.slice(0,4)+'-12-31'];
  return [f.day,f.day];
}
function periodText(f,start,end) {
  if(f.period==='Месяц')return new Intl.DateTimeFormat('ru-RU',{month:'long',year:'numeric',timeZone:'UTC'}).format(dateUTC(start));
  if(f.period==='Год')return start.slice(0,4);
  if(f.period==='Неделя')return dateText(start)+' — '+dateText(end)+' '+end.slice(0,4);
  return dateText(start)+' '+start.slice(0,4);
}
const categoryById=id=>ledger.categories.find(c=>c.id===id);
function badge(c){return `<span class="category-icon" style="background:${c.color||'var(--fill)'}">${escapeHtml(c.emoji||'◇')}</span>`;}
flowViews.finance=f=>{
  const [start,end]=periodRange(f);
  const totals=new Map();
  for(const e of ledger.entries)if(e.cur===f.financeCur&&e.date>=start&&e.date<=end)totals.set(e.category,(totals.get(e.category)||0)+e.cents);
  const cats=ledger.categories.filter(c=>c.dir===f.financeDir&&totals.has(c.id)).sort((a,b)=>totals.get(b.id)-totals.get(a.id));
  const total=cats.reduce((s,c)=>s+totals.get(c.id),0);
  const body=heading('Финансы')+segment('finance-dir',directions,f.financeDir)+
    balanceCard(f.financeDir, picker('frame.financeCur',currencyCodes,f.financeCur,'Валюта финансов'), motionAmount('total',total,f.financeCur),
      '<div class="period-line" data-range="'+start+'/'+end+'">'+button('period-step','‹','-1','period-step')+button('period-pick',periodText(f,start,end),'','period-current','date')+button('period-step','›','1','period-step')+'</div>')+
    titleRow('Категории',f.period)+(cats.length?'<div class="people">'+cats.map(c=>`<button class="person-row" data-motion-row="category-${c.id}" data-act="category-view" data-value="${c.id}">${badge(c)}<span class="person-text"><strong>${escapeHtml(c.name)}</strong><small>${Math.round(totals.get(c.id)/total*100)}% от суммы</small></span><span class="money">${motionAmount('category-'+c.id,totals.get(c.id),f.financeCur)}</span>${icon('chevron',14)}</button>`).join('')+'</div>':empty('За этот период записей нет.'))+
    (f.day===TODAY?'':button('period-today','К текущей дате','','chip spaced','date'));
  return layout(body,{action:primary('record-new',f.financeDir==='Расходы'?'Добавить расход':'Добавить доход','plus'),navigation:nav('finance')});
};
flowActions['period-pick']=(i,v,b)=>{
  const f=frames[i]; showDialog(i,'Период',s=>'<div class="group">'+field('date','Интервал',picker('sheet.period',periods,s.period,'Интервал'))+field('date','Дата в периоде',entry('sheet.day',s.day,'date'))+'</div>',b,(_,s)=>{
    if(!s.day)return fail(i,'Выберите дату.');f.period=s.period;f.day=s.day;closeSheet(i);
  },'Показать',{period:f.period,day:f.day});
};
flowActions['period-step']=(i,v)=>{
  const f=frames[i], step=Number(v),d=dateUTC(f.day);
  if(f.period==='День'||f.period==='Неделя')f.day=addDays(f.day,step*(f.period==='День'?1:7));
  else {d.setUTCDate(1); if(f.period==='Месяц')d.setUTCMonth(d.getUTCMonth()+step);else d.setUTCFullYear(d.getUTCFullYear()+step);f.day=isoDay(d);}
  render(i,true);
};
flowActions['period-today']=i=>{frames[i].day=TODAY;render(i,true);};
flowActions['category-view']=(i,v)=>{frames[i].categoryId=Number(v);go(i,'categoryHistory');};
flowViews.categoryHistory=f=>{
  const c=categoryById(f.categoryId);
  const list=ledger.entries.filter(e=>e.category===c.id&&e.cur===f.financeCur).sort((a,b)=>b.date.localeCompare(a.date)||b.id-a.id);
  const rows=list.map(e=>{
    const label=escapeHtml(e.note||c.name);
    return `<div class="finance-line" data-motion-row="record-${e.id}"><button class="person-row" data-act="record-actions" data-value="${e.id}">
      <span class="event-icon">${icon('note',16)}</span><span class="person-text"><strong>${label}</strong><small>${dateText(e.date)} ${e.date.slice(0,4)}</small></span>
      <span class="money">${amount(e.cents,e.cur)}</span></button>
      <button class="row-edit" data-act="record-edit" data-value="${e.id}" aria-label="Редактировать операцию: ${label}" title="Редактировать операцию">${icon('edit',17)}</button></div>`;
  }).join('');
  return layout('<div class="balance"><span class="eyebrow">За всё время · '+c.dir.toLowerCase()+'</span><div class="big-money">'+motionAmount('total',list.reduce((s,e)=>s+e.cents,0),f.financeCur)+'</div></div>'+titleRow('Операции',String(list.length))+(rows?'<div class="people">'+rows+'</div>':empty('Операций пока нет.')),
    {action:primary('record-new','Добавить операцию','plus'),topBar:backbar(c.name)});
};
flowActions['record-new']=i=>{
  const f=frames[i],c=f.route==='categoryHistory'?categoryById(f.categoryId):null;
  f.record={id:0,dir:c?.dir||f.financeDir,category:c?.id||0,cur:f.financeCur,sum:'',date:f.day,note:''};go(i,'financeForm');
};
flowActions['record-actions']=(i,v,b)=>{
  const e=ledger.entries.find(e=>e.id===Number(v));
  showDialog(i,amount(e.cents,e.cur),()=>'<p class="note">'+escapeHtml(e.note||categoryById(e.category).name)+' · '+dateText(e.date)+'</p><div class="group">'+rowAction('record-edit','Редактировать',v)+rowAction('record-delete','Удалить операцию',v)+'</div>',b);
};
flowActions['record-edit']=(i,v)=>{
  const e=ledger.entries.find(e=>e.id===Number(v));frames[i].record={...e,dir:categoryById(e.category).dir,sum:moneyInput(e.cents)};go(i,'financeForm');
};
flowActions['record-delete']=(i,v,b)=>confirmAction(i,b,'Удалить операцию?','Сумма категории будет пересчитана.',()=>{ledger.entries=ledger.entries.filter(e=>e.id!==Number(v));changed(i,'Операция удалена');});
flowViews.financeForm=f=>{
  const r=f.record,cats=ledger.categories.filter(c=>c.dir===r.dir);
  const choices=cats.map(c=>`<button class="chip category-choice ${c.id===r.category?'active':''}" aria-pressed="${c.id===r.category}" data-act="record-category" data-value="${c.id}">${badge(c)}${escapeHtml(c.name)}</button>`).join('');
  const selected=categoryById(r.category);
  const category=r.id?'<div class="setting group"><span>Категория</span><strong>'+escapeHtml(selected.name)+'</strong></div>':titleRow('Категория')+'<div class="category-grid">'+choices+button('category-new','Категория','','chip','plus')+'</div>'+(selected?button('category-delete','Удалить выбранную категорию',selected.id,'text-action danger'):'');
  return layout((r.id?'':segment('record-dir',directions,r.dir))+editAmount('record.sum',r.sum,r.cur)+
    '<div class="group">'+field('date','Дата операции',entry('record.date',r.date,'date'))+field('note','Комментарий',entry('record.note',r.note,'text','maxlength="180" placeholder="Необязательно"'))+'</div>'+category+errorText(f),
    {action:primary('record-save','Сохранить операцию','check'),topBar:backbar(r.id?'Редактировать операцию':r.dir==='Расходы'?'Новый расход':'Новый доход')});
};
flowActions['record-dir']=(i,v)=>{const r=frames[i].record;r.dir=v;r.category=0;render(i,true);};
flowActions['record-category']=(i,v)=>{frames[i].record.category=Number(v);render(i,true);};
flowActions['record-save']=i=>{
  const f=frames[i],r=f.record,c=categoryById(r.category),n=readMoney(r.sum,r.cur);
  if(!n||!r.date||!c||c.dir!==r.dir)return fail(i,'Введите положительную сумму, выберите дату и категорию.');
  const e={id:r.id||newId(),category:c.id,cents:n,note:r.note.trim(),date:r.date,cur:r.cur};
  if(r.id)ledger.entries[ledger.entries.findIndex(old=>old.id===r.id)]=e;else ledger.entries.push(e);
  f.financeDir=c.dir;f.day=r.date;pop(i,'Операция сохранена');
};
flowActions['category-new']=i=>{frames[i].categoryDraft={name:'',emoji:'',color:'',emojiGroup:'Еда'};go(i,'categoryForm');};
flowViews.categoryForm=f=>{
  const d=f.categoryDraft;
  return layout('<div class="category-sample">'+badge(d)+'<strong>'+escapeHtml(d.name||'Категория')+'</strong></div><div class="group">'+field('note','Название',entry('categoryDraft.name',d.name,'text','maxlength="60"'))+'</div>'+titleRow('Значок')+field('flag','Набор значков',picker('categoryDraft.emojiGroup',Object.keys(emojiGroups),d.emojiGroup,'Набор значков'))+'<div class="choices">'+emojiGroups[d.emojiGroup].map(v=>button('category-emoji',v,v,'swatch '+(v===d.emoji?'picked':''))).join('')+'</div>'+titleRow('Цвет')+'<div class="choices">'+categoryPalette.map((v,index)=>`<button class="swatch ${v===d.color?'picked':''}" data-act="category-color" aria-label="Цвет ${index+1}" data-value="${v}" style="background:${v}">${v===d.color?icon('check',17):''}</button>`).join('')+'</div>'+errorText(f),
    {action:primary('category-save','Сохранить категорию','check'),topBar:backbar('Новая категория')});
};
flowActions['category-emoji']=(i,v)=>{frames[i].categoryDraft.emoji=v;render(i,true);};
flowActions['category-color']=(i,v)=>{frames[i].categoryDraft.color=v;render(i,true);};
flowActions['category-save']=i=>{
  const f=frames[i],d=f.categoryDraft;if(!d.name.trim()||!d.emoji||!d.color)return fail(i,'Введите название, выберите значок и цвет.');
  const c={name:d.name.trim(),emoji:d.emoji,color:d.color,dir:f.record.dir,id:newId()};ledger.categories.push(c);f.record.category=c.id;pop(i,'Категория добавлена');
};
flowActions['category-delete']=(i,v,b)=>{
  const id=Number(v),c=categoryById(id),count=ledger.entries.filter(e=>e.category===id).length;
  confirmAction(i,b,'Удалить категорию?',c.name+' · Будут удалены все операции этой категории ('+count+'), во всех валютах.',()=>{
    ledger.categories=ledger.categories.filter(c=>c.id!==id);ledger.entries=ledger.entries.filter(e=>e.category!==id);
    frames.forEach(f=>{if(f.record?.category===id)f.record.category=0;});changed(i,'Категория удалена');
  });
};

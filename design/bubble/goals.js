'use strict';
function freshGoals() {
  return [{id:1,name:'Путешествие',target:9000000,cur:'RUB',due:'2027-03-01',created:'2026-10-01',photo:'',opening:1500000,
    deposits:[{id:1,date:'2026-11-09',cents:1250000},{id:2,date:'2026-11-17',cents:850000}]},
    {id:2,name:'Наушники',target:1800000,cur:'RUB',due:'',created:'2026-09-18',photo:'',opening:1200000,deposits:[{id:3,date:'2026-11-12',cents:600000}]}];
}
let savings;
const goalById=id=>savings.find(g=>g.id===id);
const saved=g=>g.opening+g.deposits.reduce((sum,d)=>sum+d.cents,0);
function goalBalance(g, overview=false) {
  const total=saved(g),done=total>=g.target,percent=Math.max(0,Math.min(100,Math.floor(total/g.target*100)));
  const due=done?'Цель достигнута':g.due?(g.due<TODAY?'Срок прошёл · ':'К ')+dateText(g.due)+' '+g.due.slice(0,4):'';
  const photo=g.photo?'<img class="goal-photo" alt="" src="'+escapeHtml(g.photo)+'">':'';
  const heading=overview?`<button class="goal-link" data-act="goal-open" data-value="${g.id}"><strong>${escapeHtml(g.name)}</strong>${icon('chevron')}</button>`:'';
  return '<div class="goal-hero goal-card">'+photo+(!g.photo?fanArt():'')+heading+(due?'<div class="eyebrow">'+due+'</div>':'')+'<div class="big-money">'+amount(total,g.cur)+'</div><div class="track"><i style="width:'+percent+'%"></i></div><div class="loan-numbers"><span>из '+amount(g.target,g.cur)+'</span><strong>'+percent+'%</strong></div></div>';
}
flowViews.goals=f=>{
  const cards=savings.map(g=>'<article class="goal-item">'+goalBalance(g,true)+(saved(g)<g.target?pairButtons(button('goal-add','Пополнить',g.id,'chip','plus'),button('goal-edit','Редактировать',g.id,'chip','edit')):button('goal-delete','Удалить цель',g.id,'text-action danger'))+'</article>').join('');
  return layout(heading('Цели')+(cards||empty('Пока нет целей. Создайте первую цель накопления.')),primary('goal-new','Новая цель','plus'),nav('goals'),true);
};
flowActions['goal-open']=(i,v)=>{frames[i].goalId=Number(v);go(i,'goalDetail');};
function goalScreenLayout(title,body,{menu='',action=''}={}) {
  const footer=action?'<div class="goals-dock goal-form-dock"><div class="sticky-action">'+action+'</div>'+gesture+'</div>':gesture;
  return '<div class="scroll goal-screen-scroll"><div class="goal-screen-head">'+status+backbar(title,menu)+'</div>'+
    '<div class="goal-screen-body">'+body+'</div></div>'+footer;
}
flowViews.goalDetail=f=>{
  const g=goalById(f.goalId),sum=saved(g),done=sum>=g.target;
  const history=[...g.deposits].sort((a,b)=>b.date.localeCompare(a.date)||b.id-a.id).map(d=>'<div class="timeline-row'+(d.cents<0?' withdrawal':'')+'"><span class="event-icon">'+icon(d.cents>=0?'take':'give',16)+'</span><span class="event-text"><strong>'+(d.cents>=0?'Пополнение':'Снятие')+'</strong><small>'+dateText(d.date)+' '+d.date.slice(0,4)+'</small></span><span class="money">'+amount(d.cents,g.cur,true)+'</span></div>').join('');
  const controls=done?'':pairButtons(button('goal-add','Пополнить',g.id,'btn secondary','plus'),button('goal-sub','Снять',g.id,'btn withdrawal','give'));
  const menu='<details class="goal-actions"><summary class="icon-button" aria-label="Действия с целью">'+icon('more')+'</summary><div class="goal-menu">'
    +(done?'':button('goal-edit','Редактировать цель',g.id,'menu-action','edit'))
    +button('goal-delete','Удалить цель',g.id,'menu-action danger','trash')+'</div></details>';
  return goalScreenLayout(g.name,goalBalance(g)+controls+'<div class="group spaced"><div class="setting"><span>Осталось накопить</span><strong>'+amount(Math.max(0,g.target-sum),g.cur)+'</strong></div><div class="setting"><span>Создана</span><span>'+dateText(g.created)+' '+g.created.slice(0,4)+'</span></div></div>'+titleRow('История')+(history?'<div class="activity">'+history+'</div>':empty('Пополнения и снятия появятся здесь.')),{menu});
};
function goalAdjustment(i,v,b,sign) {
  const g=goalById(Number(v));if(!g||saved(g)>=g.target)return;
  showDialog(i,sign>0?'Пополнить цель':'Снять с цели',s=>'<p class="note">'+escapeHtml(g.name)+' · Накоплено '+amount(saved(g),g.cur)+'</p>'+editAmount('sheet.sum',s.sum,g.cur),b,(_,s)=>{
    const n=readMoney(s.sum,g.cur);if(!n)return fail(i,'Введите положительную сумму в валюте цели.');
    if(sign<0&&n>saved(g))return fail(i,'Сумма превышает накопления.');
    g.deposits.push({id:newId(),date:TODAY,cents:n*sign});changed(i,sign>0?'Пополнение записано':'Снятие записано');
  },sign>0?'Пополнить':'Снять',{sum:'',withdrawal:sign<0});
}
flowActions['goal-add']=(i,v,b)=>goalAdjustment(i,v,b,1);
flowActions['goal-sub']=(i,v,b)=>goalAdjustment(i,v,b,-1);
flowActions['goal-new']=i=>{frames[i].goalDraft={id:0,name:'',target:'',saved:'',cur:prefs.defaultCur,due:'',photo:''};go(i,'goalForm');};
flowActions['goal-edit']=(i,v)=>{
  const g=goalById(Number(v));if(!g||saved(g)>=g.target)return;
  frames[i].goalId=g.id;frames[i].goalDraft={id:g.id,name:g.name,target:moneyInput(g.target),saved:moneyInput(saved(g)),initialSaved:saved(g),cur:g.cur,due:g.due,photo:g.photo};go(i,'goalForm');
};
flowViews.goalForm=f=>{
  const d=f.goalDraft;
  const preview=d.photo?'<img class="goal-photo" alt="" src="'+escapeHtml(d.photo)+'">':'';
  const photo='<label>'+preview+'<span>'+(d.photo?'Заменить фото':'Выбрать фото')+'</span><input type="file" accept="image/*" data-file="goal-photo"></label>'
    +(d.photo?button('goal-photo-clear','Убрать фото','','text-action danger'):'');
  return goalScreenLayout(d.id?'Редактировать цель':'Новая цель','<div class="group">'+field('flag','Название',entry('goalDraft.name',d.name,'text','maxlength="100" placeholder="На что копим?"'))+field('stack','Целевая сумма',entry('goalDraft.target',d.target,'text','inputmode="decimal" placeholder="0"'))+field('take','Уже накоплено',entry('goalDraft.saved',d.saved,'text','inputmode="decimal" placeholder="0"'))+field('pie','Валюта',picker('goalDraft.cur',currencyCodes,d.cur,'Валюта цели'))+field('date','Срок · необязательно',entry('goalDraft.due',d.due,'date','min="'+addDays(TODAY,1)+'"'))+'</div><div class="group spaced photo-picker">'+photo+'</div>'+errorText(f),{action:primary('goal-save','Сохранить цель','check')});
};
flowActions['goal-photo-clear']=i=>{frames[i].goalDraft.photo='';render(i,true);};
async function selectPhoto(i,file) {
  const d=frames[i].goalDraft;
  try {
    const img=await createImageBitmap(file);
    const scale=Math.min(1,720/Math.max(img.width,img.height));
    const canvas=document.createElement('canvas');
    canvas.width=Math.max(1,Math.round(img.width*scale));canvas.height=Math.max(1,Math.round(img.height*scale));
    canvas.getContext('2d').drawImage(img,0,0,canvas.width,canvas.height);img.close();
    if(frames[i].goalDraft===d&&frames[i].route==='goalForm'){d.photo=canvas.toDataURL('image/jpeg',0.8);render(i,true);}
  } catch {if(frames[i].goalDraft===d)fail(i,'Не удалось прочитать фото. Выберите другое изображение.');}
}
flowActions['goal-save']=i=>{
  const f=frames[i],d=f.goalDraft,n=readMoney(d.target,d.cur),current=readMoney(d.saved||'0',d.cur,true);
  if(!d.name.trim()||!n||current===null||current>=n)return fail(i,'Введите название и целевую сумму. Накопленная сумма должна быть меньше цели.');
  const g=d.id?goalById(d.id):{id:newId(),created:TODAY,deposits:[]};
  if(d.id&&saved(g)!==d.initialSaved)return fail(i,'Накопления изменились в другой панели. Откройте цель заново, чтобы увидеть новый остаток.');
  if(d.due&&d.due<=TODAY&&d.due!==g.due)return fail(i,'Выберите будущую дату.');
  Object.assign(g,{name:d.name.trim(),target:n,cur:d.cur,due:d.due,photo:d.photo,opening:current-g.deposits.reduce((sum,x)=>sum+x.cents,0)});
  if(!d.id)savings.unshift(g);
  f.goalId=g.id;pop(i);changed(i,'Цель сохранена');
};
flowActions['goal-delete']=(i,v,b)=>{
  const id=Number(v),g=goalById(id);
  showDialog(i,'Удалить цель?',()=>'<p class="note">'+escapeHtml(g.name)+' и история пополнений и снятий будут удалены.</p>',b,()=>{
    savings=savings.filter(goal=>goal.id!==id);
    changed(i,'Цель удалена');
  },'Удалить',{cancelBelow:true});
};

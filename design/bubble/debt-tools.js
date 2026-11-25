'use strict';
const peopleOrders = {
  newest:'Сначала новые',
  oldest:'Сначала старые',
  highest:'Сначала с большим балансом',
  lowest:'Сначала с меньшим балансом',
};
function sortPeople(list,order) {
  if(order==='highest') list.sort((a,b)=>balance(b)-balance(a));
  else if(order==='lowest') list.sort((a,b)=>balance(a)-balance(b));
  else list.sort((a,b)=>order==='oldest'?a.id-b.id:b.id-a.id);
}
const historyOrders = {
  dateNewest:'Дата записи · сначала новые',
  dateOldest:'Дата записи · сначала старые',
  createdNewest:'Дата создания · сначала новые',
  createdOldest:'Дата создания · сначала старые',
  amountHighest:'Сумма · сначала большие',
  amountLowest:'Сумма · сначала меньшие',
};
function orderedLoans(p,f) {
  const ascending=f.historyOrder.endsWith('Oldest')||f.historyOrder.endsWith('Lowest');
  const sign=ascending?1:-1;
  return p.loans.filter(l=>(remaining(l)>0)===(f.personSection==='open')&&
    (f.historyFilter==='all'||(l.principal>0)===(f.historyFilter==='positive'))).sort((a,b)=>{
    if(f.historyOrder.startsWith('amount'))return sign*(a.principal-b.principal||a.id-b.id);
    if(f.historyOrder.startsWith('created'))return sign*(a.id-b.id);
    return sign*(a.date.localeCompare(b.date)||a.id-b.id);
  });
}
function showDebtOrder(i,title,choices,current,select,trigger) {
  showDialog(i,title,()=>{
    const remember='<div class="group"><label class="setting order-choice">Запомнить выбор<input type="checkbox" data-field="sheet.rememberOrder"></label></div>';
    const rows=Object.entries(choices).map(([order,label])=>
      `<button class="setting order-choice" role="radio" aria-checked="${order===current}" data-act="${select}" data-value="${order}">${label}<span class="order-indicator" aria-hidden="true"></span></button>`
    ).join('');
    return remember+'<div class="group" role="radiogroup" aria-label="'+title+'">'+rows+'</div>';
  },trigger);
}
flowActions['people-sort']=(i,v,b)=>showDebtOrder(i,'Порядок людей',peopleOrders,frames[i].peopleOrder,'choose-people-order',b);
flowActions['history-sort']=(i,v,b)=>showDebtOrder(i,'Порядок истории',historyOrders,frames[i].historyOrder,'choose-history-order',b);
function chooseDebtOrder(i,field,order) {
  const f=frames[i];
  f[field]=order;
  if(f.sheet.rememberOrder){prefs[field]=order;persistDemo();}
  closeSheet(i);
}
flowActions['choose-people-order']=(i,order)=>chooseDebtOrder(i,'peopleOrder',order);
flowActions['choose-history-order']=(i,order)=>chooseDebtOrder(i,'historyOrder',order);
flowActions['history-section']=(i,value)=>{frames[i].personSection=value;render(i,true);};
flowActions['history-direction']=(i,value)=>{frames[i].historyFilter=value;render(i,true);};
function personAt(i){return people.find(p=>p.id===frames[i].personId);}
function personMenu(p) {
  return contextActionMenu('Управление карточкой',
    button('person-rename','Изменить имя','','menu-action','edit')+
    '<label class="menu-action person-photo-action">'+icon('user',18)+(p.photo?'Заменить фото':'Выбрать фото')+
      '<input type="file" accept="image/*" data-file="person-photo"></label>'+
    (p.photo?button('person-photo-clear','Убрать фото','','menu-action','close'):'')+
    button('person-delete','Удалить человека','','menu-action danger','trash'));
}
async function selectPersonPhoto(i,file) {
  const p=personAt(i),f=frames[i];
  const isCurrent=()=>f.route==='person'&&f.personId===p.id&&people.includes(p);
  try {
    const photo=await readDemoPhoto(file,192);
    if(isCurrent()){p.photo=photo;changed(i,'Фото сохранено');}
  } catch {if(isCurrent())toast(i,'Не удалось прочитать фото. Выберите другое изображение.');}
}
flowActions['person-photo-clear']=i=>{delete personAt(i).photo;changed(i,'Фото убрано');};
flowActions['person-rename']=(i,v,b)=>{
  const p=personAt(i);
  showDialog(i,'Изменить имя',s=>field('user','Имя',entry('sheet.name',s.name,'text','maxlength="80"')),b,(_,s)=>{
    if(!s.name.trim())return fail(i,'Введите имя.');p.name=s.name.trim();changed(i,'Имя изменено');
  },'Сохранить',{name:p.name});
};
flowActions['person-delete']=(i,v,b)=>{
  const p=personAt(i);showDialog(i,'Удалить карточку «'+p.name+'»?',()=>'<p class="note">Все долги и возвраты в этой карточке будут удалены.</p>',b,()=>{
    people=people.filter(row=>row.id!==p.id);changed(i,'Человек удалён');
  },'Удалить',{cancelBelow:true});
};
flowActions['loan-actions']=(i,v,b)=>{
  const l=findLoan(frames[i],Number(v));
  showDialog(i,l.principal>0?'Дал в долг':'Взял в долг',()=>'<div class="group">'+
    (remaining(l)?rowAction('repay','Учесть возврат',v,'',l.principal>0?'take':'give')+rowAction('due','Срок и напоминание',v,'','date'):'')+
    (l.reminder&&remaining(l)?rowAction('reminder-preview','Предпросмотр уведомления',v,'','bell'):'')+
    rowAction('loan-edit','Редактировать запись',v,'','edit')+
    (!l.returns.length?rowAction('loan-delete','Удалить запись',v,'','trash'):'')+'</div>',b);
};
flowActions['loan-edit']=(i,v)=>{
  const f=frames[i],p=personAt(i),l=findLoan(f,Number(v));if(!l)return;
  f.d={...draft(p.cur),fixed:p.id,editId:l.id,locked:l.returns.length>0,settled:!remaining(l),dir:Math.sign(l.principal),name:p.name,
    note:l.note,sum:moneyInput(Math.abs(l.principal)),date:l.date,due:l.due,remind:!!l.reminder,time:l.reminder||'09:00'};
  go(i,'new');
};
function updateEditedDebt(i,n) {
  const f=frames[i],d=f.d,l=findLoan(f,d.editId);
  if(!l||(l.returns.length&&(l.principal!==n*d.dir||l.date!==d.date)))return fail(i,'Исходная запись изменилась. Вернитесь в историю.');
  Object.assign(l,{principal:n*d.dir,note:d.note.trim(),date:d.date});
  if(remaining(l))Object.assign(l,{due:d.due,reminder:d.remind?d.time:''});
  pop(i,'Запись изменена');
}
flowActions['loan-delete']=(i,v,b)=>{
  const p=personAt(i),l=p.loans.find(l=>l.id===Number(v));if(!l||l.returns.length)return;
  confirmAction(i,b,'Удалить запись?',(l.note||'Долг')+' · '+amount(l.principal,p.cur,true),()=>{p.loans=p.loans.filter(row=>row.id!==l.id);changed(i,'Запись удалена');});
};
function exportRows(p) {
  const entries=[];
  for(const l of p.loans) {
    entries.push({date:l.date,id:l.id,label:l.note||'Долг',cents:l.principal});
    for(const r of l.returns)entries.push({date:r.date,id:r.id,label:r.cancelled?'Возврат отменён':'Возврат',cents:-Math.sign(l.principal)*r.amount});
  }
  return entries.sort((a,b)=>a.date.localeCompare(b.date)||a.id-b.id)
    .map(e=>[e.date,e.label,amount(e.cents,p.cur,true)]);
}
function downloadFile(name,type,content) {
  const url=URL.createObjectURL(new Blob([content],{type})),a=document.createElement('a');
  a.href=url;a.download=name;document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);
}
function shareText(p){return p.name+' · '+amount(balance(p),p.cur,true)+'\n\n'+exportRows(p).map(row=>row.join(' · ')).join('\n');}
flowActions['person-share']=(i,v,b)=>{
  showDialog(i,'Поделиться историей',()=>'<div class="group">'+rowAction('share-preview','Текст','','','note')+
    rowAction('share-excel','Файл Excel','','','stack')+'</div>',b);
};
flowActions['share-preview']=(i,v,b)=>{
  showDialog(i,'Предпросмотр выписки',()=>'<div class="group statement-preview"><pre>'+escapeHtml(shareText(personAt(i)))+'</pre></div>',b,
    ()=>flowActions['share-text'](i),'Сохранить текст');
};
flowActions['share-text']=i=>{const p=personAt(i);downloadFile('SimpleDebt-'+p.name+'.txt','text/plain;charset=utf-8',shareText(p));};
flowActions['share-excel']=i=>{
  const p=personAt(i),rows=[['Дата','Запись','Сумма'],...exportRows(p)];
  const xml=rows.map(row=>'<Row>'+row.map(c=>'<Cell><Data ss:Type="String">'+escapeHtml(c)+'</Data></Cell>').join('')+'</Row>').join('');
  downloadFile('SimpleDebt-'+p.name+'.xml','application/xml','<?xml version="1.0"?><?mso-application progid="Excel.Sheet"?><Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"><Worksheet ss:Name="История"><Table>'+xml+'</Table></Worksheet></Workbook>');
};
const demoContacts=['Алексей Миронов','Анна Соколова','Виктория Ли','Елена Ким','Илья Волков','Михаил Орлов'];
function normalizedPersonName(name) {return name.trim().toLocaleLowerCase('ru');}
function exactDebtPeople(d) {
  const name=normalizedPersonName(d.name);
  return people.filter(p=>p.cur===d.cur&&normalizedPersonName(p.name)===name);
}
function debtPersonSuggestions(d) {
  const query=normalizedPersonName(d.name);
  if(!query)return '';
  const matching=people.filter(p=>normalizedPersonName(p.name).includes(query));
  const exact=matching.filter(p=>normalizedPersonName(p.name)===query);
  const byCurrency=(a,b)=>Number(a.cur!==d.cur)-Number(b.cur!==d.cur);
  const candidates=exact.sort(byCurrency).concat(matching.filter(p=>!exact.includes(p)).sort((a,b)=>
    Number(!normalizedPersonName(a.name).startsWith(query))-Number(!normalizedPersonName(b.name).startsWith(query))||byCurrency(a,b)
  ).slice(0,Math.max(0,5-exact.length)));
  return candidates.length?'<div class="debt-person-choices'+(d.personError?' choice-error':'')+'"><p class="debt-person-hint">Добавить долг к существующему человеку</p>'+candidates.map(p=>
    '<button class="person-row" data-act="select-debt-person" data-value="'+p.id+'">'+personAvatar(p)+'<span class="person-text"><strong>'+escapeHtml(p.name)+'</strong><small>'+p.cur+'</small></span><span class="money">'+amount(balance(p),p.cur)+'</span>'+icon('chevron',16)+'</button>'
  ).join('')+'</div>':'';
}
function debtDraftPhoto(d) {
  if(d.fixed||d.personId||exactDebtPeople(d).length)return '';
  const photo=d.photo?'<img class="motif person-photo" alt="" src="'+escapeHtml(d.photo)+'">':icon('user',17);
  return '<div class="debt-draft-photo"><label class="setting person-photo-action">'+photo+'<strong>'+
    (d.photo?'Заменить фото':'Выбрать фото')+'</strong><input type="file" accept="image/*" data-file="debt-draft-photo"></label>'+
    (d.photo?iconButton('clear-debt-draft-photo','Убрать фото','close'):'')+'</div>'+
    (d.photoError?'<p class="debt-field-error" role="alert">Не удалось загрузить фото.</p>':'');
}
function updateDebtPersonOptions(i) {
  const d=frames[i].d,panel=sections[i];
  const suggestions=panel.querySelector('[data-debt-person-suggestions]');
  if(!suggestions)return;
  const from=suggestions.getBoundingClientRect().height;
  suggestions.getAnimations().forEach(animation=>animation.cancel());
  suggestions.innerHTML=debtPersonSuggestions(d);
  const to=suggestions.getBoundingClientRect().height;
  if(from!==to&&!reducedMotion.matches) {
    suggestions.animate([{height:from+'px',opacity:from?1:0},{height:to+'px',opacity:1}],{duration:230,easing:settleEase});
  }
  panel.querySelector('[data-draft-person-photo]').innerHTML=debtDraftPhoto(d);
}
async function selectDebtDraftPhoto(i,file) {
  const f=frames[i],d=f.d;
  try {
    const photo=await readDemoPhoto(file,192);
    if(f.route==='new'&&f.d===d&&!d.personId){d.photo=photo;d.photoError=false;render(i,true);}
  } catch {if(f.route==='new'&&f.d===d){d.photoError=true;render(i,true);}}
}
flowActions['clear-debt-draft-photo']=i=>{frames[i].d.photo='';render(i,true);};
flowActions['select-debt-person']=async(i,v)=>{
  const d=frames[i].d,p=people.find(p=>p.id===Number(v));
  const query=d.name;
  const menu=sections[i].querySelector('[data-debt-person-suggestions]');
  if(!p||!menu||menu.inert)return;
  if(!reducedMotion.matches){
    const from=menu.getBoundingClientRect().height;
    menu.getAnimations().forEach(animation=>animation.cancel());
    menu.inert=true;
    await menu.animate([{height:from+'px',opacity:1},{height:'0px',opacity:0}],{duration:185,easing:settleEase}).finished.catch(()=>{});
  }
  if(frames[i].route==='new'&&frames[i].d===d&&d.name===query){d.personId=p.id;d.name=p.name;d.personError=false;frames[i].error='';render(i,true);}
};
flowActions['clear-debt-person']=i=>{
  frames[i].d.personId=null;
  render(i,true);
  sections[i].querySelector('[data-bind="d.name"]').focus({preventScroll:true});
};
function contactRows(query) {return demoContacts.filter(n=>n.toLowerCase().includes(query.toLowerCase())).map(n=>rowAction('contact-pick',n,n)).join('')||empty('Совпадений нет.');}
flowActions.contacts=(i,v,b)=>showDialog(i,'Контакты',s=>'<p class="note">Демонстрационный список контактов</p>'+entry('sheet.query',s.query,'search','class="search-field" aria-label="Найти контакт" placeholder="Найти по имени"')+'<div class="group" data-contacts>'+contactRows(s.query)+'</div>',b,null,'',{query:''});
flowActions['contact-pick']=(i,v)=>{frames[i].d.name=v;frames[i].d.personError=false;closeSheet(i);};

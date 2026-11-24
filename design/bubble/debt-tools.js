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
function orderedHistory(entries,p,f) {
  const sign=f.historyOrder.endsWith('↑')?1:-1;
  return entries.filter(e=>{
    const l=p.loans.find(l=>l.id===(e.loan||e.id));
    return f.historyFilter==='Все'||(l.principal>0)===(f.historyFilter==='Мне должны');
  }).sort((a,b)=>{
    if(f.historyOrder.startsWith('Сумма'))return sign*(a.cents-b.cents||a.id-b.id);
    if(f.historyOrder.startsWith('Дата создания'))return sign*(a.id-b.id);
    return sign*(a.date.localeCompare(b.date)||a.id-b.id);
  });
}
flowActions['people-sort']=(i,v,b)=>{
  const f=frames[i];
  showDialog(i,'Порядок людей',()=>{
    const remember='<div class="group"><label class="setting order-choice">Запомнить выбор<input type="checkbox" data-field="sheet.rememberOrder"></label></div>';
    const choices=Object.entries(peopleOrders).map(([order,label])=>
      `<button class="setting order-choice" role="radio" aria-checked="${order===f.peopleOrder}" data-act="choose-people-order" data-value="${order}">${label}<span class="order-indicator" aria-hidden="true"></span></button>`
    ).join('');
    return remember+'<div class="group" role="radiogroup" aria-label="Порядок людей">'+choices+'</div>';
  },b);
};
flowActions['choose-people-order']=(i,order)=>{
  const f=frames[i];
  f.peopleOrder=order;
  if(f.sheet.rememberOrder){prefs.peopleOrder=order;persistDemo();}
  closeSheet(i);
};
flowActions['history-sort']=(i,v,b)=>{
  const f=frames[i];showDialog(i,'Фильтр истории',s=>'<div class="group">'+field('settings','Направление долга',picker('sheet.filter',['Все','Мне должны','Я должен'],s.filter,'Направление долга'))+field('history','Порядок',picker('sheet.order',['Дата операции ↓','Дата операции ↑','Дата создания ↓','Дата создания ↑','Сумма ↓','Сумма ↑'],s.order,'Порядок'))+'</div>',b,(_,s)=>{f.historyOrder=s.order;f.historyFilter=s.filter;closeSheet(i);},'Применить',{order:f.historyOrder,filter:f.historyFilter});
};
function personAt(i){return people.find(p=>p.id===frames[i].personId);}
flowActions['person-menu']=(i,v,b)=>showDialog(i,personAt(i).name,()=>'<div class="group">'+rowAction('person-rename','Изменить имя')+rowAction('person-delete','Удалить человека')+'</div>',b);
flowActions['person-rename']=(i,v,b)=>{
  const p=personAt(i);
  showDialog(i,'Изменить имя',s=>field('user','Имя',entry('sheet.name',s.name,'text','maxlength="80"')),b,(_,s)=>{
    if(!s.name.trim())return fail(i,'Введите имя.');p.name=s.name.trim();changed(i,'Имя изменено');
  },'Сохранить',{name:p.name});
};
flowActions['person-delete']=(i,v,b)=>{
  const p=personAt(i);confirmAction(i,b,'Удалить человека?',p.name+' и вся история расчётов будут удалены.',()=>{
    people=people.filter(row=>row.id!==p.id);changed(i,'Человек удалён');
  });
};
flowActions['loan-actions']=(i,v,b)=>{
  const l=findLoan(frames[i],Number(v));
  showDialog(i,l.note||'Долг',()=>'<div class="group">'+(remaining(l)?rowAction('repay','Учесть возврат',v):'')+rowAction('due','Срок и напоминание',v)+(l.reminder&&remaining(l)?rowAction('reminder-preview','Предпросмотр уведомления',v):'')+(!l.returns.length?rowAction('loan-edit','Редактировать запись',v)+rowAction('loan-delete','Удалить запись',v):'')+'</div>'+(l.returns.length?'<p class="note">У записи есть связанные возвраты. Их можно отменить в истории.</p>':''),b);
};
flowActions['loan-edit']=(i,v)=>{
  const f=frames[i],p=personAt(i),l=findLoan(f,Number(v));if(!l||l.returns.length)return;
  f.d={...draft(p.cur),fixed:p.id,editId:l.id,dir:Math.sign(l.principal),name:p.name,note:l.note,sum:moneyInput(Math.abs(l.principal)),date:l.date,due:l.due,remind:!!l.reminder,time:l.reminder||'09:00'};
  go(i,'new');
};
function updateEditedDebt(i,n) {
  const f=frames[i],d=f.d,l=findLoan(f,d.editId);
  if(!l||l.returns.length)return fail(i,'Исходная запись изменилась. Вернитесь в историю.');
  Object.assign(l,{principal:n*d.dir,note:d.note.trim(),date:d.date,due:d.due,reminder:d.remind?d.time:''});pop(i,'Запись изменена');
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
  const p=personAt(i);showDialog(i,'Поделиться историей',()=>'<pre>'+escapeHtml(shareText(p))+'</pre><div class="group">'+rowAction('share-text','Сохранить текст')+rowAction('share-excel','Сохранить таблицу Excel')+'</div>',b);
};
flowActions['share-text']=i=>{const p=personAt(i);downloadFile('SimpleDebt-'+p.name+'.txt','text/plain;charset=utf-8',shareText(p));};
flowActions['share-excel']=i=>{
  const p=personAt(i),rows=[['Дата','Запись','Сумма'],...exportRows(p)];
  const xml=rows.map(row=>'<Row>'+row.map(c=>'<Cell><Data ss:Type="String">'+escapeHtml(c)+'</Data></Cell>').join('')+'</Row>').join('');
  downloadFile('SimpleDebt-'+p.name+'.xml','application/xml','<?xml version="1.0"?><?mso-application progid="Excel.Sheet"?><Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"><Worksheet ss:Name="История"><Table>'+xml+'</Table></Worksheet></Workbook>');
};
const demoContacts=['Алексей Миронов','Анна Соколова','Виктория Ли','Елена Ким','Илья Волков','Михаил Орлов'];
function contactRows(query) {return demoContacts.filter(n=>n.toLowerCase().includes(query.toLowerCase())).map(n=>rowAction('contact-pick',n,n)).join('')||empty('Совпадений нет.');}
flowActions.contacts=(i,v,b)=>showDialog(i,'Контакты',s=>'<p class="note">Демонстрационный список контактов</p>'+entry('sheet.query',s.query,'search','class="search-field" aria-label="Найти контакт" placeholder="Найти по имени"')+'<div class="group" data-contacts>'+contactRows(s.query)+'</div>',b,null,'',{query:''});
flowActions['contact-pick']=(i,v)=>{frames[i].d.name=v;closeSheet(i);};

'use strict';
const themeChoices=['Светлая','Тёмная','Системная'];
const delays=['Сразу','Через 30 секунд','Через минуту','Через 5 минут'];
function defaultPrefs(){return {first:'RUB',second:'USD',defaultCur:'RUB',shareSum:false,theme:'Светлая',delay:delays[0]};}
let prefs=defaultPrefs(), demoPin='',biometric=false,driveSigned=false,cloudCopies=[],previousCopy=null;
function resetDemo(){
  people=freshData();ledger=freshLedger();savings=freshGoals();serial=100;prefs=defaultPrefs();dark=false;
  demoPin='';biometric=false;driveSigned=false;cloudCopies=[];previousCopy=null;notificationAccess='ask';
  try{localStorage.removeItem('simpledebt-review-copies');}catch{}
}
function applyDemoTheme(){dark=prefs.theme==='Тёмная'||(prefs.theme==='Системная'&&window.matchMedia('(prefers-color-scheme: dark)').matches);}
function saveCopies(){
  try{localStorage.setItem('simpledebt-review-copies',JSON.stringify({cloudCopies,previousCopy,driveSigned}));}catch{}
}
function loadDemo() {
  serial=100;
  try {
    const raw=localStorage.getItem('simpledebt-bubble-v2');if(raw){const snapshot=JSON.parse(raw);if(validSnapshot(snapshot))applySnapshot(snapshot);}
    const stored=JSON.parse(localStorage.getItem('simpledebt-review-copies')||'{}');
    previousCopy=validSnapshot(stored.previousCopy)?stored.previousCopy:null;
    cloudCopies=(Array.isArray(stored.cloudCopies)?stored.cloudCopies:[]).filter(c=>typeof c.label==='string'&&validSnapshot(c.data)).slice(0,5);
    driveSigned=stored.driveSigned===true;
  } catch {}
  applyDemoTheme();
}
function validSnapshot(s) {
  const string=v=>typeof v==='string', num=Number.isSafeInteger, positive=n=>num(n)&&n>0;
  const date=v=>string(v)&&/^\d{4}-\d\d-\d\d$/.test(v)&&!isNaN(Date.parse(v))&&isoDay(dateUTC(v))===v;
  const optionalDate=v=>v===''||date(v),cur=v=>currencyCodes.includes(v);
  const rows=(list,test)=>Array.isArray(list)&&new Set(list.map(x=>x?.id)).size===list.length&&list.every(x=>x&&positive(x.id)&&test(x));
  function validGoalHistory(goal) {
    const valid = rows(goal.deposits, entry =>
      num(entry.cents) && entry.cents !== 0 && date(entry.date) &&
      (entry.isInitial === undefined || typeof entry.isInitial === 'boolean') &&
      (!entry.isInitial || (entry.cents > 0 && entry.date === goal.created))
    );
    return valid && goal.deposits.filter(entry => entry.isInitial).length <= 1;
  }
  try{
    if(s?.format!=='SimpleDebt-design-2'||!positive(s.serial)||!s.preferences)return false;
    const p=s.preferences;
    if(![p.first,p.second,p.defaultCur].every(cur)||typeof p.shareSum!=='boolean'||!themeChoices.includes(p.theme)||!delays.includes(p.delay))return false;
    if(!rows(s.people,p=>string(p.name)&&cur(p.cur)&&num(p.art)&&p.art>=0&&rows(p.loans,l=>num(l.principal)&&l.principal!==0&&string(l.note)&&date(l.date)&&optionalDate(l.due)&&string(l.reminder)&&(!l.reminder||(/^([01]\d|2[0-3]):[0-5]\d$/.test(l.reminder)&&l.due))&&rows(l.returns,r=>positive(r.amount)&&date(r.date)&&typeof r.cancelled==='boolean')&&remaining(l)>=0)))return false;
    if(!rows(s.finance.categories,c=>string(c.name)&&directions.includes(c.dir)&&string(c.emoji)&&/^#[0-9a-f]{6}$/i.test(c.color)))return false;
    const cats=new Set(s.finance.categories.map(c=>c.id));
    if(!rows(s.finance.entries,e=>cats.has(e.category)&&positive(e.cents)&&cur(e.cur)&&string(e.note)&&date(e.date)))return false;
    if(!rows(s.goals,g=>string(g.name)&&cur(g.cur)&&positive(g.target)&&num(g.opening)&&date(g.created)&&optionalDate(g.due)&&string(g.photo)&&(!g.photo||/^data:image\/jpeg;base64,[A-Za-z0-9+/=]+$/.test(g.photo))&&validGoalHistory(g)))return false;
    const groups=[s.people,s.finance.categories,s.finance.entries,s.goals,...s.goals.map(g=>g.deposits)];
    for(const p of s.people){groups.push(p.loans);for(const l of p.loans)groups.push(l.returns);}
    return groups.every(group=>group.every(item=>item.id<s.serial));
  }catch{return false;}
}
flowViews.settings=f=>layout(heading('Настройки')+titleRow('Приложение')+'<div class="group">'+rowAction('pref','Первая основная валюта','first',prefs.first)+rowAction('pref','Вторая основная валюта','second',prefs.second)+rowAction('pref','Валюта по умолчанию','defaultCur',prefs.defaultCur)+rowAction('pref','Оформление','theme',prefs.theme)+rowAction('pref-share','Баланс в тексте истории','',prefs.shareSum?'Вкл.':'Выкл.')+'</div>'+titleRow('Данные и защита')+'<div class="group">'+rowAction('backup-open','Резервная копия','','Файл / Drive')+rowAction('security-open','Защита входа','',demoPin?'PIN включён':'Выключена')+'</div>'+titleRow('О приложении')+'<div class="group">'+['Оценить приложение','Написать разработчику','Поддержать проект','Политика конфиденциальности'].map(v=>rowAction('external-preview',v,v)).join('')+'<div class="setting"><span>Версия Android-приложения</span><span>4.6.4</span></div></div>','',nav('settings'));
flowActions.pref=(i,key,b)=>{
  const labels={first:'Первая основная валюта',second:'Вторая основная валюта',defaultCur:'Валюта по умолчанию',theme:'Оформление',delay:'Блокировать после выхода'};
  const values=key==='theme'?themeChoices:key==='delay'?delays:currencyCodes;
  showDialog(i,labels[key],s=>field('settings',labels[key],picker('sheet.choice',values,s.choice,labels[key])),b,(_,s)=>{
    prefs[key]=s.choice;
    if(key==='first')frames.forEach(f=>f.cur=s.choice);
    if(key==='theme')applyDemoTheme();
    changed(i,'Настройка сохранена');
  },'Применить',{choice:prefs[key]});
};
flowActions['pref-share']=i=>{prefs.shareSum=!prefs.shareSum;changed(i,'Настройка сохранена');};
flowActions['external-preview']=(i,v,b)=>{
  if(v==='Оценить приложение')return rateDialog(i,b);
  const descriptions={'Оценить приложение':'Android открывает страницу приложения в магазине.','Написать разработчику':'Android открывает почтовое приложение с адресом разработчика и версией в теме письма.','Поддержать проект':'Android открывает страницу поддержки проекта в браузере.','Политика конфиденциальности':'Android открывает политику приложения.'};
  showDialog(i,v,()=>'<p class="note">'+descriptions[v]+'</p>'+(v==='Политика конфиденциальности'?'<a class="chip" href="../../docs/privacy_policy.html" target="_blank" rel="noopener">Прочитать политику</a>':v==='Написать разработчику'?'<a class="chip" href="mailto:pavlikbrichkin@yandex.ru?subject=SimpleDebt%204.6.4">Открыть почту</a>':'<a class="chip" href="https://pay.cloudtips.ru/p/5c867537" target="_blank" rel="noopener">Открыть страницу поддержки</a>'),b);
};
flowActions['backup-open']=i=>go(i,'backup');
flowViews.backup=()=>layout(backbar('Резервная копия')+titleRow('Копия в файл')+'<div class="group">'+rowAction('backup-save','Сохранить файл')+'<label class="setting file-label"><strong>Восстановить из файла</strong>'+icon('chevron',16)+'<input type="file" accept=".json,application/json" data-file="backup" aria-label="Открыть копию прототипа"></label>'+(previousCopy?rowAction('backup-previous','Вернуть данные до восстановления'):'')+'</div><p class="note">Файл содержит демонстрационные записи прототипа.</p>'+titleRow('Google Drive')+'<div class="group">'+(driveSigned?rowAction('drive-save','Сохранить копию')+rowAction('drive-list','Выбрать копию','',String(cloudCopies.length))+rowAction('drive-logout','Выйти из демоаккаунта'):rowAction('drive-login','Войти в демоаккаунт'))+'</div><p class="note">В прототипе копии Drive хранятся в этом браузере.</p>');
flowActions['backup-save']=i=>{downloadFile('SimpleDebt-demo.json','application/json',JSON.stringify(demoSnapshot(),null,2));toast(i,'Файл копии подготовлен');};
function snapshotCounts(s){return 'Людей: '+s.people.length+' · Долгов: '+s.people.reduce((n,p)=>n+p.loans.length,0)+' · Финансовых операций: '+s.finance.entries.length+' · Целей: '+s.goals.length;}
function restorePreview(i,data,label,b) {
  confirmAction(i,b,'Восстановить копию?',label+'. '+snapshotCounts(data)+'. Текущие записи будут заменены. Их можно вернуть из предыдущей копии.',()=>{
    previousCopy=structuredClone(demoSnapshot());applySnapshot(structuredClone(data));applyDemoTheme();saveCopies();
    frames=startScreens.map(frame);frames[i].route='backup';frames[i].trail=['settings'];changed(i,'Данные восстановлены');
  },'Восстановить');
}
async function readBackupFile(i,file) {
  const opener=sections[i].querySelector('[data-file=backup]');
  try{
    if(file.size>16*1024*1024)throw Error();
    const data=JSON.parse(await file.text());if(!validSnapshot(data))throw Error();
    if(frames[i].route==='backup')restorePreview(i,data,file.name,opener);
  }catch{toast(i,'Файл повреждён или имеет другой формат. Данные сохранены.');}
  opener.value='';
}
flowActions['backup-previous']=(i,v,b)=>{if(previousCopy)restorePreview(i,previousCopy,'Копия до восстановления',b);};
flowActions['drive-login']=(i,v,b)=>confirmAction(i,b,'Демоаккаунт Drive','Вход покажет экраны сохранения и выбора версий на тестовом аккаунте.',()=>{driveSigned=true;saveCopies();changed(i,'Демоаккаунт подключён');},'Продолжить');
flowActions['drive-logout']=(i,v,b)=>confirmAction(i,b,'Выйти из аккаунта?','Записи на устройстве сохранятся.',()=>{driveSigned=false;saveCopies();changed(i,'Вы вышли из демоаккаунта');},'Выйти');
flowActions['drive-save']=i=>{
  const entry={label:'20 ноября · '+new Date().toLocaleTimeString('ru-RU',{hour:'2-digit',minute:'2-digit',second:'2-digit'}),data:structuredClone(demoSnapshot())};
  cloudCopies.unshift(entry);cloudCopies=cloudCopies.slice(0,5);saveCopies();changed(i,'Демокопия сохранена');
};
flowActions['drive-list']=(i,v,b)=>showDialog(i,'Копии в Drive',()=>cloudCopies.length?'<div class="group">'+cloudCopies.map((c,j)=>rowAction('drive-restore',c.label,j,j?'Предыдущая':'Последняя')).join('')+'</div>':empty('Ещё нет сохранённых копий.'),b);
flowActions['drive-restore']=(i,v,b)=>{const copy=cloudCopies[Number(v)];if(copy)restorePreview(i,copy.data,copy.label,b);};
flowActions['security-open']=i=>go(i,'security');
flowViews.security=()=>layout(backbar('Защита входа')+'<div class="group">'+(demoPin?rowAction('pin-start','Проверить вход','unlock')+rowAction('pin-start','Изменить PIN','change')+rowAction('pin-start','Выключить PIN','disable')+rowAction('pref','Блокировать после выхода','delay',prefs.delay)+rowAction('bio-pref','Биометрия','',biometric?'Вкл.':'Выкл.'):rowAction('pin-start','Установить тестовый PIN','setup'))+'</div><p class="note">Демонстрационный PIN действует до перезагрузки вкладки. Используйте тестовые цифры.</p>');
function startPin(i,mode,next=null){
  const f=frames[i];f.auth={mode,stage:mode==='setup'?'new':'verify',digits:'',first:'',next};go(i,'pin');
}
flowActions['pin-start']=(i,v)=>startPin(i,v);
function finishPin(i,msg){
  const f=frames[i],next=f.auth.next;
  if(next){f.personId=next.person;f.trail=[];setRoute(f,'person');render(i);const target=sections[i].querySelector('[data-act=due][data-value="'+next.loan+'"]');target?.scrollIntoView({block:'nearest'});}
  else pop(i);
  renderAll(true);toast(i,msg);
}
flowViews.pin=f=>{
  const a=f.auth,title=a.stage==='new'?'Новый PIN':a.stage==='repeat'?'Повторите PIN':'Введите PIN';
  return layout(backbar(title)+'<p class="note center">Демонстрационный код из четырёх цифр</p><div class="pin-dots" aria-label="Введено цифр: '+a.digits.length+'">'+Array.from({length:4},(_,n)=>n<a.digits.length?'●':'○').join(' ')+'</div><div class="keypad">'+['1','2','3','4','5','6','7','8','9','0','⌫'].map(n=>button('pin-digit',n,n,'key')).join('')+'</div>'+errorText(f)+(a.stage==='verify'?button('pin-recover','Забыли PIN?','','text-action')+(biometric?button('bio-verify','Вход по биометрии','','text-action'):''):''));
};
flowActions['pin-digit']=(i,v)=>{
  const f=frames[i],a=f.auth;f.error='';
  if(v==='⌫')a.digits=a.digits.slice(0,-1);else a.digits+=v;
  if(a.digits.length<4)return render(i,true);
  const digits=a.digits;a.digits='';
  if(a.stage==='verify'){
    if(digits!==demoPin)return fail(i,'Неверный PIN. Попробуйте ещё раз.');
    if(a.mode==='disable'){demoPin='';biometric=false;return finishPin(i,'PIN отключён');}
    if(a.mode==='unlock')return finishPin(i,'Вход выполнен');
    a.stage='new';
  }else if(a.stage==='new'){a.first=digits;a.stage='repeat';}
  else if(digits===a.first){demoPin=digits;return finishPin(i,'Тестовый PIN сохранён');}
  else {a.stage='new';return fail(i,'Коды различаются. Введите новый PIN.');}
  render(i,true);
};
flowActions['bio-pref']=(i,v,b)=>confirmAction(i,b,'Биометрия','Демонстрация системного подтверждения Android.',()=>{biometric=!biometric;changed(i,'Настройка биометрии изменена');},biometric?'Выключить':'Подтвердить');
flowActions['bio-verify']=(i,v,b)=>confirmAction(i,b,'Подтверждение Android','Демонстрация успешной биометрической проверки.',()=>{
  const f=frames[i];f.sheet=null;
  if(f.auth.mode==='unlock')return finishPin(i,'Вход выполнен');
  if(f.auth.mode==='disable'){demoPin='';biometric=false;return finishPin(i,'PIN отключён');}
  f.auth.stage='new';render(i);
},'Подтвердить');
flowActions['pin-recover']=i=>go(i,'recovery');
flowViews.recovery=()=>layout(backbar('Восстановление PIN')+'<div class="group"><p class="note">Подтвердите блокировку устройства, затем дважды введите новый PIN. Записи сохранятся.</p></div>',primary('recover-device','Подтвердить на устройстве','check'));
flowActions['recover-device']=(i,v,b)=>confirmAction(i,b,'Проверка устройства','Демонстрация системного экрана Android. После подтверждения откроется ввод нового PIN.',()=>{const f=frames[i];f.trail.pop();setRoute(f,'pin');f.auth.stage='new';f.auth.digits='';render(i);},'Подтвердить');
// A notification is previewed explicitly in the browser. Android schedules and gates the real entry.
flowActions['reminder-preview']=(i,v,b)=>{
  const f=frames[i],p=personAt(i),loan=Number(v);
  showDialog(i,'Личное напоминание',()=>'<p class="note">Книга долгов · Время проверить запись.</p>'+button('reminder-open','Открыть запись',loan,'btn','bell'),b);
};
flowActions['reminder-open']=(i,v)=>{
  const f=frames[i],next={person:f.personId,loan:Number(v)};f.sheet=null;
  if(demoPin)startPin(i,'unlock',next);
  else{render(i);sections[i].querySelector('[data-act=due][data-value="'+v+'"]')?.scrollIntoView({block:'nearest'});}
};
let hiddenAt=null;
document.addEventListener('visibilitychange',()=>{
  if(document.hidden){hiddenAt=Date.now();return;}
  if(!hiddenAt)return;
  const elapsed=Date.now()-hiddenAt;hiddenAt=null;
  if(!demoPin||elapsed<[0,30000,60000,300000][delays.indexOf(prefs.delay)])return;
  frames.forEach((f,i)=>{if(f.route!=='pin'&&f.route!=='recovery')startPin(i,'unlock');});
});
let notificationAccess='ask';
function permissionPrompt(obj,prefix) {
  if(!obj.askNotify)return '';
  return '<div class="group spaced"><p class="note">Разрешить приложению показывать личные напоминания? Это демонстрация разрешения Android.</p>'+pairButtons(button('notify-allow','Разрешить',prefix,'chip'),button('notify-deny','Только срок',prefix,'chip'))+'</div>';
}
function setPermission(i,scope,allow) {
  notificationAccess=allow?'granted':'denied';
  const f=frames[i],target=scope==='s.'?f.sheet:f.d;
  target.askNotify=false;target.remind=allow;f.error='';render(i,true);
}
flowActions['notify-allow']=(i,v)=>setPermission(i,v,true);
flowActions['notify-deny']=(i,v)=>setPermission(i,v,false);
function rateDialog(i,b){
  showDialog(i,'Оценить приложение',s=>'<div class="rating">'+[1,2,3,4,5].map(n=>`<button aria-label="Оценка ${n}" data-act="choose-rating" data-value="${n}">${n<=s.rating?'★':'☆'}</button>`).join('')+'</div>',b,(_,s)=>{
    if(!s.rating)return fail(i,'Выберите оценку.');
    if(s.rating>=4)return showDialog(i,'Оценка в магазине',()=>'<a class="chip" href="https://play.google.com/store/apps/details?id=com.breckneck.debtbook" target="_blank" rel="noopener">Открыть Google Play</a>',b);
    showDialog(i,'Что можно улучшить?',q=>field('note','Ваш отзыв',entry('sheet.review',q.review,'text','maxlength="800"')),b,(_,q)=>{
      const href='mailto:pavlikbrichkin@yandex.ru?subject=SimpleDebt%204.6.4&body='+encodeURIComponent(q.review);
      showDialog(i,'Письмо разработчику',()=>'<p class="note">'+escapeHtml(q.review)+'</p><a class="chip" href="'+escapeHtml(href)+'">Открыть почту</a>',b);
    },'Подготовить письмо',{review:''});
  },'Продолжить',{rating:0});
}
flowActions['choose-rating']=(i,v)=>{frames[i].sheet.rating=Number(v);render(i,true);};

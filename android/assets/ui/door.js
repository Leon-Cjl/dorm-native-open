'use strict';
const $=id=>document.getElementById(id);
let state={ready:false,preparing:true,auto:false,observing:false},toastTimer,openTimer;
function notice(text){$('toast').textContent=text;$('toast').hidden=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').hidden=true,2400);}
function applyTheme(theme){
 if(theme!=='light'&&theme!=='dark')return;
 document.documentElement.dataset.theme=theme;
 $('theme').setAttribute('aria-pressed',String(theme==='dark'));
 $('theme').setAttribute('aria-label',theme==='dark'?'切换浅色外观':'切换深色外观');
}
function buzz(pattern){
 if(!navigator.vibrate)return;
 try{navigator.vibrate(pattern);}catch(_){}
}
function setStage(current,total,label){
 const wrap=$('stageWrap');
 if(!wrap)return;
 if(current>0){
  wrap.hidden=false;
  const dots=wrap.querySelectorAll('.stage-dot');
  dots.forEach((d,i)=>{d.classList.toggle('on',i<current);d.classList.toggle('now',i===current-1);});
  $('stageLabel').textContent=label||'';
  $('stageCount').textContent=current+' / '+total;
  const pct=total>0?Math.round(current/total*100):0;
  $('stageBar').style.width=pct+'%';
 }else{wrap.hidden=true;}
}
window.updateState=function(s){
 state=s;
 if(s.theme)applyTheme(s.theme);
 const busy=!!s.preparing||!!s.switching||!!s.opening;
 const available=!!s.ready&&!busy&&!s.error;
 $('restorePersonal').hidden=!!s.ready||busy||!s.personal;
 $('connectionCard').hidden=!s.ready;$('quickSection').hidden=!s.ready;$('quickEntries').hidden=!s.ready;
 $('room').textContent=s.room||(s.preparing?'读取宿舍…':'添加你的宿舍');
 $('accountText').textContent=s.switching?'正在切换':s.preparing?'正在准备':s.ready?'已选择宿舍':'未添加宿舍';
 $('readyDot').classList.toggle('ok',available);
 $('doorCard').dataset.status=busy?'preparing':s.error?'error':available?'ready':'inactive';
 $('doorCard').setAttribute('aria-busy',String(busy));
 // Stage progress inside room hint area
 const stageActive=s.stageCurrent&&s.stageCurrent>0;
 if(stageActive)setStage(s.stageCurrent,s.stageTotal||6,s.stageLabel);
 else setStage(0,0,'');
 $('roomHint').textContent=s.error||(s.opening?'正在打开开门面板…':s.switching?'正在切换宿舍…':s.preparing?'正在读取已保存的宿舍…':!s.ready?'导入自己的宿舍授权后，就可以在这里开门。':s.warmReady&&s.accessReady?'连接与开门准备已完成，现在可以点开门。':'蓝牙与网络需开启，请在本人门锁旁使用。');
 $('unlockLabel').textContent=s.opening?'正在开门…':s.switching?'正在切换…':s.preparing?'读取宿舍…':available?'开门':'请先添加宿舍';
 $('retry').hidden=!s.error;
 $('unlock').disabled=!available;
 $('unlock').hidden=!s.ready&&!busy;
 $('firstImport').hidden=!!s.ready||busy;
 $('onboarding').hidden=!!s.ready||busy;
 $('buildPrivacy').textContent='需要本人的有效授权文件，请勿共享授权资料。';
 let connection='idle',title='尚未预连接',hint='打开应用自动尝试，后台继续重连；也可以直接点开门。';
 if(!s.ready){connection='empty';title='先添加自己的宿舍';hint='完成导入后，应用会在开门页提前准备连接。';}
 else if(s.auto||s.arrival||s.observing){connection='monitor';title='后台检测正在运行';hint='手动开门会先结束空闲检测；自动操作进行中时请等待结果。';}
 else if(s.warmReady){connection='ready';title=s.accessReady?'可以快速开门':'门锁已连接';hint=s.connectionRunning?'连接已保持 · 开门成功后结束本次预连接':(s.accessReady?'连接与开门准备已完成':'正在准备本次开门')+' · 连接保留约 '+(s.warmSeconds||1)+' 秒';}
 else if(s.warmState==='connecting'){connection='connecting';title='正在提前连接';hint='正在自动尝试连接，退到后台也会继续；现在点开门可接续。';}
 else if(s.warmState==='expired'){connection='expired';title='预连接已结束';hint='下次打开应用会继续准备，也可直接点开门。';}
 else if(s.warmState==='failed'){connection='failed';title='暂未连接到门锁';hint='请靠近门锁再试；也可以直接点开门。';}
 $('connectionDot').dataset.state=connection;
 $('connectionTitle').textContent=title;$('connectionHint').textContent=hint;
 $('warmButton').disabled=!available||connection==='monitor';
 $('warmButton').textContent=s.connectionRunning?'停止预连接':'开启预连接';
 $('monitorSummary').hidden=!s.auto&&!s.arrival&&!s.observing;
 $('monitorText').textContent=s.observing?'正在观察接近，不自动开门':s.arrival?'本次回宿舍检测正在运行':'长期接近检测正在运行';
 $('autoSwitch').disabled=!available||!!s.observing||!!s.arrival;
 $('stopMonitor').hidden=!s.auto&&!s.observing&&!s.arrival;
 $('arrivalButton').disabled=!available||!!s.auto||!!s.observing||!!s.arrival;
 $('arrivalButton').textContent=s.arrival?'回宿舍模式已开启':'开启回宿舍模式';
 $('arrivalHint').textContent=s.arrival?s.autoDetail||'等待接近本人门锁':'10 分钟有效 · 无需先检测离开';
 $('autoSwitch').setAttribute('aria-checked',String(!!s.auto));
 $('autoDot').classList.toggle('ok',!!s.auto);
 $('autoCard').dataset.mode=s.arrival?'arrival':s.observing?'observe':s.auto?'auto':'off';
 $('autoStatus').textContent=s.observing?'观察模式 · 不自动开门':s.auto||s.arrival?s.autoStatus||'正在启动检测':s.autoStatus&&s.autoStatus!=='未开启'?s.autoStatus:'已关闭';
 $('autoDetail').textContent=s.auto||s.observing||s.arrival?s.autoDetail||'等待检测状态更新…':'打开后先检测离开，再在靠近时尝试开门。';
 $('lastResult').textContent=s.last||'还没有开门记录';
 $('version').textContent=s.version||'';
 $('activeAccount').textContent=s.account||'未选择账号';
 $('accountSummary').textContent=(s.account||'未选择账号')+' · 已保存 '+(s.accountCount||0)+' 个账号';
 $('roomSummary').textContent=(s.room||'未选择宿舍')+' · 此账号已保存 '+(s.roomCount||0)+' 间';
 for(const id of ['roomSwitch','accountsButton','roomsButton','addProfile'])$(id).disabled=busy;
 $('roomSwitch').disabled=busy||!s.ready;
 $('settingsHint').textContent=available?'已保存你的宿舍，日常使用原生蓝牙开门。':s.switching?'正在核对所选账号与宿舍，请稍候。':s.preparing?'正在准备本人房间，请稍候。':'房间尚未准备，可回首页恢复或更新授权。';
};
document.addEventListener('click',event=>{
 const nav=event.target.closest('[data-page]');
 if(nav){
  if(window.Dorm&&typeof Dorm.action==='function')Dorm.action(nav.dataset.page==='home'?'pageHome':'pageSettings');
  document.querySelectorAll('.page').forEach(p=>p.hidden=p.id!==nav.dataset.page);
  document.querySelectorAll('[data-page]').forEach(b=>b.setAttribute('aria-current',b===nav?'page':'false'));
  window.scrollTo(0,0);return;
 }
 const action=event.target.closest('[data-action]');
 if(action&&!action.disabled){
  if(window.Dorm&&typeof Dorm.action==='function')Dorm.action(action.dataset.action);
  else notice('界面预览，不连接门锁');
 }
});



$('theme').addEventListener('click',()=>{
 const dark=document.documentElement.dataset.theme==='dark'||(!document.documentElement.dataset.theme&&matchMedia('(prefers-color-scheme:dark)').matches);
 const next=dark?'light':'dark';applyTheme(next);
 if(window.Dorm&&typeof Dorm.setTheme==='function')Dorm.setTheme(next);
});
$('today').textContent=new Date().toLocaleDateString('zh-CN',{month:'long',day:'numeric'});
if(window.Dorm&&typeof Dorm.ready==='function')Dorm.ready();
else {
 const demo={ready:true,preparing:false,room:'我的宿舍',account:'账号 1 · 预览',accountCount:1,roomCount:2,version:'0.10.0 · 界面预览',auto:false,last:'还没有开门记录',personal:false};
 const preview=typeof URLSearchParams==='function'?new URLSearchParams(window.location.search).get('preview'):'';
 if(preview==='empty'){demo.ready=false;demo.accountCount=0;demo.roomCount=0;demo.room='';demo.account='';}
 if(preview==='connecting'){demo.warmState='connecting';demo.connectionRunning=true;}
 if(preview==='ready'){demo.warmReady=true;demo.accessReady=true;demo.warmSeconds=14;demo.connectionRunning=true;}
 if(preview==='expired')demo.warmState='expired';
 window.updateState(demo);
}

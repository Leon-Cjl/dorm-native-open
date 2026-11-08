'use strict';
// This exports a manually observed OWN session, not an alternative login or an enrollment API.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),https=require('node:https');
const APP='wx5ab2a45f1eba1dcf',HOSTS=['https://gy.bostex.net:5500','https://applets.bostex.net'];
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
function fail(s){throw new Error(s);}
function text(o,k){const v=o?.[k];if(!['string','number'].includes(typeof v))fail('缺少字段：'+k);const s=String(v).trim();if(!s||s==='null'||s.length>180||/[\x00-\x1f]/.test(s))fail('字段格式错误：'+k);return s;}
function parseDate(s){
 if(!/^(?:\d{4}-\d{2}-\d{2}[ T]|\d{4}\/\d{2}\/\d{2} )\d{2}:\d{2}:\d{2}$/.test(s))return NaN;
 const [y,mo,d,h,mi,se]=s.split(/[-/ T:]/).map(Number),utc=Date.UTC(y,mo-1,d,h-8,mi,se),local=new Date(utc+8*3600000);
 return local.getUTCFullYear()===y&&local.getUTCMonth()===mo-1&&local.getUTCDate()===d&&local.getUTCHours()===h&&local.getUTCMinutes()===mi&&local.getUTCSeconds()===se?utc:NaN;
}
function period(start,end,now){if(!start&&!end)return true;const a=parseDate(start),b=parseDate(end);return Number.isFinite(a)&&Number.isFinite(b)&&a<=now&&now<=b;}
function observation(o,base,now=Date.now()){
 if(o?.schema!=='dorm-native-observation/1'||o.source!=='official-pc-visible-session'||o.appId!==APP||o.ownerConsent!==true)fail('需要本人同意后的实际官方会话观测记录');
 const captured=Date.parse(o.capturedAt);if(!Number.isFinite(captured)||captured>now+60000||now-captured>3600000)fail('请重新读取本人当前页面，观测记录只接受一小时内的数据');
 const result={companyName:text(o,'companyName'),staffID:text(o,'staffID'),roomNo:text(o,'roomNo')};
 const entries=o.evidence;if(!Array.isArray(entries)||entries.length<1||entries.length>4)fail('需要1到4份当前官方页面证据');
 const evidence=entries.map(e=>{
  if(typeof e.path!=='string'||!e.path||!/^.+\.(?:png|jpg|jpeg|xml)$/i.test(path.basename(e.path)))fail('需要当前官方页面的 PNG/JPG 或 UI XML 证据文件');
  const evidencePath=path.resolve(base,e.path),st=fs.statSync(evidencePath);if(!st.isFile()||st.size<20||st.size>12*1024*1024)fail('观测证据大小不适配');
  const digest=sha(fs.readFileSync(evidencePath));if(e.sha256!==digest)fail('观测证据摘要不一致');
  return {name:path.basename(evidencePath),sha256:digest};
 });if(new Set(evidence.map(e=>e.name)).size!==evidence.length)fail('证据文件名不能重复');
 return {...result,capturedAt:new Date(captured).toISOString(),evidence};
}
function chooseOrganization(data,obs){
 const rows=Array.isArray(data)?data:data?.data;if(!Array.isArray(rows))fail('组织查询未返回列表');
 const norm=s=>String(s).replace(/\s/g,'');const match=rows.filter(x=>x&&norm(x.companyName)===norm(obs.companyName));
 if(match.length!==1)fail('页面中的学校未唯一匹配，不能猜测组织');const id=text(match[0],'companyID');
 if(!/^\d{1,12}$/.test(id)||id==='10051')fail('该组织格式或额外策略尚未接入');return id;
}
function selectMigration(obs,companyID,people,roles,access,now=Date.now()){
 if(!/^\d{1,12}$/.test(companyID)||companyID==='10051')fail('组织未适配');
 if(!Array.isArray(people?.staff)||!Array.isArray(roles?.data)||!Array.isArray(access?.data))fail('官方响应不完整');
 const matches=people.staff.filter(x=>x&&String(x.staffID)===obs.staffID);if(matches.length!==1)fail('未唯一返回页面中的本人账号');
 const personId=text(matches[0],'uniqueID');if(!/^\d+$/.test(personId)||BigInt(personId)<=0n||BigInt(personId)>0xffffffffn)fail('人员授权标识未适配');
 if(roles.data.length===0||roles.data[0]?.roleType!==0)fail('只支持学生本人权限');
 // Exact room selected from the logged-in UI. Never export the whole returned list.
 const rooms=access.data.filter(x=>x&&String(x.roomNo)===obs.roomNo);if(rooms.length!==1)fail('当前宿舍未唯一匹配，请在官方页面明确选择宿舍');
 const room=rooms[0],status=v=>typeof v==='number'&&Number.isInteger(v)?v:typeof v==='string'&&/^-?\d+$/.test(v)?Number(v):NaN;
 if(!Object.hasOwn(room,'eStatus')||!Number.isFinite(status(room.eStatus))||status(room.eStatus)===1)fail('本人房间授权已停用或状态缺失');
 if(Object.hasOwn(room,'dStatus')&&(!Number.isFinite(status(room.dStatus))||status(room.dStatus)===3))fail('设备停用或状态无效');
 if(!['card','face','fingerprint','pw','qr'].some(k=>status(room.control?.[k])===1))fail('未返回可用开门权限');
 const start=room.effectiveTime==null?'':String(room.effectiveTime),end=room.expiryTime==null?'':String(room.expiryTime);
 if(!period(start,end,now))fail('授权有效期不适配或已失效');
 const mac=text(room,'MAC').toUpperCase();if(!/^(?:[0-9A-F]{2}:){5}[0-9A-F]{2}$/.test(mac)||mac==='00:00:00:00:00:00')fail('需要尚未适配的序列号扫描');
 return {schema:'dorm-native-migration/1',appId:APP,source:'official-pc-visible-session',companyID,staffID:obs.staffID,personId:String(BigInt(personId)),roomID:text(room,'roomID'),deviceID:text(room,'deviceID'),roomNo:obs.roomNo,MAC:mac,sn:room.sn==null?'':String(room.sn),verifiedAt:new Date(now).toISOString(),effectiveTime:start,expiryTime:end};
}
function transport(host,route,body){return new Promise((resolve,reject)=>{
 // Fixed read-only endpoint allowlist. No arbitrary URL, enrollment, settings or unlock commands.
 if(!HOSTS.includes(host)||!['/wxProgram/getCompanyListEncryption','/wxProgram/system/companyEncryption'].includes(route))return reject(Error('非只读查询路径'));
 if(route.includes('system/')&&!['/staff/getStaff','/1075/getStaffRole','/1081/getAccess'].includes(body.url))return reject(Error('非只读组织路径'));
 const cipher=crypto.createCipheriv('aes-128-cbc',Buffer.from('1234567890abcdef'),Buffer.from('abcdef1234567890'));
 const bytes=Buffer.from(JSON.stringify({data:Buffer.concat([cipher.update(JSON.stringify(body),'utf8'),cipher.final()]).toString('base64')}));
 const req=https.request(new URL(route,host),{method:'POST',headers:{'Content-Type':'application/json',language:'zh-CN','Content-Length':bytes.length}},res=>{
  const chunks=[];let size=0;res.on('data',b=>{size+=b.length;if(size>524288)req.destroy(Error('响应过大'));else chunks.push(b);});res.on('error',reject);
  res.on('end',()=>{try{if(res.statusCode!==200)fail('官方查询未成功');const r=JSON.parse(Buffer.concat(chunks).toString('utf8'));if(!Object.hasOwn(r,'code'))return resolve(r);if(String(r.code)!=='200')fail('官方查询被拒绝');const value=Object.hasOwn(r,'body')?r.body:r.data;if(!value||typeof value!=='object')fail('响应格式未适配');resolve(Array.isArray(value)?{data:value}:value);}catch(e){reject(e);}});
 });const timer=setTimeout(()=>req.destroy(Error('查询超时')),8000);req.on('close',()=>clearTimeout(timer));req.on('error',reject);req.end(bytes);
});}
async function collect(obs,send=transport,now=Date.now()){
 for(const k of ['companyName','staffID','roomNo'])if(typeof obs?.[k]!=='string'||text(obs,k)!==obs[k])fail('观测字段必须是完整且格式有效的文字：'+k);
 let host,companyID;for(const h of HOSTS){try{const r=await send(h,'/wxProgram/getCompanyListEncryption',{enable:1,flag:'1'});companyID=chooseOrganization(r,obs);host=h;break;}catch(e){if(h===HOSTS[HOSTS.length-1])throw e;}}
 const company=(url,body)=>send(host,'/wxProgram/system/companyEncryption',{companyID,url,body,method:'POST'});
 // Sequential and bounded: first confirm the observed person before requesting that person's rooms.
 const people=await company('/staff/getStaff',{staffID:obs.staffID,dataType:'simple'});
 const own=people?.staff?.filter(x=>String(x.staffID)===obs.staffID);if(!own||own.length!==1)fail('本人账号不匹配');
 const roles=await company('/1075/getStaffRole',{staffNo:obs.staffID});if(roles?.data?.[0]?.roleType!==0)fail('非学生本人权限');
 const access=await company('/1081/getAccess',{staff:[{staffNo:obs.staffID}],dataType:'simple-device-control'});
 return selectMigration(obs,companyID,people,roles,access,now);
}
function requestFor(obs,migration,now=Date.now()){
 return {schema:'dorm-native-request/1',appId:APP,createdAt:new Date(now).toISOString(),companyName:obs.companyName,
  observation:{capturedAt:obs.capturedAt,evidence:obs.evidence},
  origin:'observed-own-official-session',migration};
}
function validateMigration(m){
 if(m?.schema!=='dorm-native-migration/1'||m.appId!==APP||m.source!=='official-pc-visible-session')fail('迁移记录类型不匹配');
 for(const k of ['companyID','staffID','personId','roomID','deviceID','roomNo','MAC'])if(typeof m[k]!=='string'||text(m,k)!==m[k])fail('迁移字段无效：'+k);
 if(!/^\d{1,12}$/.test(m.companyID)||m.companyID==='10051'||!/^\d+$/.test(m.personId)||BigInt(m.personId)<=0n||BigInt(m.personId)>0xffffffffn||!/^([0-9A-F]{2}:){5}[0-9A-F]{2}$/.test(m.MAC)||m.MAC==='00:00:00:00:00:00')fail('迁移字段未适配');
}
function writeNew(file,data){fs.writeFileSync(file,data,{flag:'wx',mode:0o600});}
async function main(){
 const [input,output]=process.argv.slice(2);if(!input||!output)fail('用法：node session-export.cjs 本人页面观测.json 输出.dormrequest');
 if(path.extname(output)!=='.dormrequest')fail('此步生成待签发 .dormrequest，不能伪装成 .dormkey');
 const inputPath=path.resolve(input);const obs=observation(JSON.parse(fs.readFileSync(inputPath,'utf8')),path.dirname(inputPath));
 const migration=await collect(obs),result=requestFor(obs,migration),bytes=Buffer.from(JSON.stringify(result,null,2));writeNew(output,bytes);
 console.log(JSON.stringify({ok:true,output:path.resolve(output),next:'将此文件和对应页面证据私下交给维护者，其工具会自动查询并签发 .dormkey，无人工逐项确认',officialLoginPerformed:false,lockCommandSent:false,privateFieldsPrinted:false}));
}
module.exports={APP,sha,observation,chooseOrganization,selectMigration,collect,requestFor,period,writeNew,validateMigration};
if(require.main===module)main().catch(e=>{console.error(JSON.stringify({ok:false,error:e.message,lockCommandSent:false}));process.exitCode=1;});

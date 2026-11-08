// Explicitly authorized read-only owner queries (2026-11-06). No lock/settings endpoint.
// Reports types and comparisons, never identity values, full responses or credentials.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),https=require('node:https');
const root=path.resolve(__dirname,'..'),envelope=JSON.parse(fs.readFileSync(path.join(root,'private/本人宿舍授权.dormkey'),'utf8'));
const trust=fs.readFileSync(path.join(root,'android/src/cn/dormnative/app/MigrationTrust.java'),'utf8');
const key=crypto.createPublicKey({key:Buffer.from(trust.match(/PUBLIC_KEY="([^"]+)"/)[1],'base64'),type:'spki',format:'der'});
const bytes=Buffer.from(envelope.payload,'base64');
if(!crypto.verify('sha256',bytes,key,Buffer.from(envelope.signature,'base64')))throw Error('Owner signature invalid');
const seed=JSON.parse(bytes),events=[],hosts=['https://gy.bostex.net:5500','https://applets.bostex.net'];let selected=0;
function request(host,route,body){return new Promise((resolve,reject)=>{
 const cipher=crypto.createCipheriv('aes-128-cbc',Buffer.from('1234567890abcdef'),Buffer.from('abcdef1234567890'));
 const data=Buffer.from(JSON.stringify({data:Buffer.concat([cipher.update(JSON.stringify(body),'utf8'),cipher.final()]).toString('base64')}));
 const req=https.request(new URL(route,host),{method:'POST',headers:{'Content-Type':'application/json',language:'zh-CN','Content-Length':data.length}},res=>{
  let size=0;const chunks=[];res.on('data',c=>{size+=c.length;if(size>524288)req.destroy(Object.assign(Error(),{code:'BODY_LIMIT'}));else chunks.push(c);});
  res.on('end',()=>{try{if(res.statusCode!==200)throw Object.assign(Error(),{code:'HTTP_'+res.statusCode});const r=JSON.parse(Buffer.concat(chunks).toString());if(Object.hasOwn(r,'code')){if(String(r.code)!=='200')throw Object.assign(Error(),{code:'BUSINESS_REJECTED'});const v=Object.hasOwn(r,'body')?r.body:r.data;if(v&&typeof v==='object')resolve(Array.isArray(v)?{data:v}:v);else throw Object.assign(Error(),{code:'BODY_SHAPE'});}else resolve(r);}catch(e){reject(e);}});
 });
 const timer=setTimeout(()=>req.destroy(Object.assign(Error(),{code:'TIMEOUT'})),9000);
 req.on('close',()=>clearTimeout(timer));req.on('error',reject);req.end(data);
});}
async function post(stage,route,body){
 for(let i=0;i<hosts.length;i++){const index=(selected+i)%hosts.length,start=Date.now();try{const r=await request(hosts[index],route,body);events.push({stage,host:new URL(hosts[index]).host,ok:true,ms:Date.now()-start});selected=index;return r;}catch(e){events.push({stage,host:new URL(hosts[index]).host,ok:false,ms:Date.now()-start,error:String(e.code||e.name||'UNKNOWN').replace(/[^A-Z0-9_]/gi,'').slice(0,60)});}}
 throw Error('QUERY_FAILED');
}
const company=(stage,url,body)=>post(stage,'/wxProgram/system/companyEncryption',{companyID:seed.companyID,url,body,method:'POST'});
function shape(v){return v==null?String(v):typeof v==='string'?(v===''?'empty-string':v.replace(/[0-9]/g,'#').replace(/[a-z]/gi,'X')):typeof v;}
function dateInfo(v){const raw=String(v??''),stamp=Date.parse(raw.replace(' ','T')+(/(?:Z|[+-]\d\d:\d\d)$/.test(raw)?'':'+08:00'));return {type:v===null?'null':typeof v,shape:shape(v),oldParserShape:/^\d{4}[-/]\d{2}[-/]\d{2}[ T]\d{1,2}:\d{2}:\d{2}$/.test(raw),finite:Number.isFinite(stamp),beforeNow:stamp<=Date.now(),matchesSeedStart:raw===seed.effectiveTime,matchesSeedEnd:raw===seed.expiryTime};}
async function main(){
 const p=await company('person','/staff/getStaff',{dataType:'simple',staffID:seed.staffID});
 const people=(p.staff||[]).filter(x=>String(x.staffID)===seed.staffID);events.push({stage:'person-match',unique:people.length===1,identifierMatch:people.length===1&&String(people[0].uniqueID)===seed.personId});
 if(people.length!==1||String(people[0].uniqueID)!==seed.personId)throw Error('IDENTITY_MISMATCH');
 const roles=await company('role','/1075/getStaffRole',{staffNo:seed.staffID});events.push({stage:'role-match',student:roles.data?.[0]?.roleType===0});
 const org=await post('organization','/wxProgram/getCompanyListEncryption',{enable:1,companyID:seed.companyID});events.push({stage:'organization-match',found:(org.data||[]).some(x=>String(x.companyID)===seed.companyID),specialTimedPolicy:seed.companyID==='10051'});
 const rooms=await company('access','/1081/getAccess',{staff:[{staffNo:seed.staffID}],dataType:'simple-device-control'});
 const own=(rooms.data||[]).filter(x=>String(x.roomID)===seed.roomID&&String(x.deviceID)===seed.deviceID);
 events.push({stage:'room-match',array:Array.isArray(rooms.data),matches:own.length});
 for(const r of own){const c=r.control||{};events.push({stage:'own-room-fields',fields:Object.keys(r),eStatusType:typeof r.eStatus,eStatus:r.eStatus,dStatusType:typeof r.dStatus,dStatus:r.dStatus,bleControl:['card','face','fingerprint','pw','qr'].some(k=>Number(c[k])===1),controlTypes:Object.fromEntries(['card','face','fingerprint','pw','qr'].map(k=>[k,typeof c[k]])),roomLabelPresent:!!r.roomNo,roomLabelMatches:r.roomNo===seed.roomNo,macType:typeof r.MAC,macValid:/^([0-9a-f]{2}:){5}[0-9a-f]{2}$/i.test(r.MAC||''),macMatches:String(r.MAC).toUpperCase()===seed.MAC,snMatches:r.sn===seed.sn,start:dateInfo(r.effectiveTime),end:dateInfo(r.expiryTime)});}
 // Keep the exact observed owner row locally for a Java regression; never included in source/APK.
 if(own.length===1)fs.writeFileSync(path.join(root,'private/access-row-diagnostic.json'),JSON.stringify({observedAt:new Date().toISOString(),room:own[0]},null,2));
}
main().catch(()=>events.push({stage:'aborted',ok:false})).finally(()=>{
 const result={date:new Date().toISOString(),scope:'desktop read-only owner queries, not phone/door evidence',lockCommandSent:false,privateValuesLogged:false,events};
 fs.writeFileSync(path.join(root,'dist/access-diagnostic.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2));
});

// Read-only queries for the single identity already observed in the official PC mini program.
// All personal inputs and full responses stay in private/. This script never opens a lock.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const https = require('node:https');
const root=path.resolve(__dirname,'..');
const identity=JSON.parse(fs.readFileSync(path.join(root,'private','observed-identity.json'),'utf8'));
if(identity.source!=='official-pc-visible-session' || !identity.staffID || !identity.companyName || !identity.roomNo) throw Error('Missing observed own account context');
const hosts=['https://gy.bostex.net:5500','https://applets.bostex.net'];
function request(host,route,body){
 const cipher=crypto.createCipheriv('aes-128-cbc',Buffer.from('1234567890abcdef'),Buffer.from('abcdef1234567890'));
 const data=JSON.stringify({data:Buffer.concat([cipher.update(JSON.stringify(body),'utf8'),cipher.final()]).toString('base64')});
 return new Promise((resolve,reject)=>{
  const req=https.request(new URL(route,host),{method:'POST',headers:{'Content-Type':'application/json',language:'zh-CN','Content-Length':Buffer.byteLength(data)}},res=>{
   let bytes=0; const parts=[];
   res.on('data',b=>{bytes+=b.length;if(bytes>524288){req.destroy(Error('response limit'));return;}parts.push(b);});
   res.on('end',()=>{try{
    if(res.statusCode!==200) throw Error('HTTP '+res.statusCode);
    const value=JSON.parse(Buffer.concat(parts).toString('utf8'));
    if(value.code && String(value.code)!=='200') throw Error('Vendor rejected request');
    resolve(value.code ? (value.body||value.data) : value);
   }catch(e){reject(e);}});
  });
  req.setTimeout(6000,()=>req.destroy(Error('timeout')));req.on('error',reject);req.end(data);
 });
}
async function main(){
 let host,org;
 for(const candidate of hosts){
  try{
   const response=await request(candidate,'/wxProgram/getCompanyListEncryption',{enable:1,flag:'1'});
   const list=Array.isArray(response)?response:response.data;
   const matches=(list||[]).filter(o=>String(o.companyName).replace(/\s/g,'')===identity.companyName.replace(/\s/g,''));
   if(matches.length!==1) throw Error('Observed organization did not match one record');
   host=candidate;org=matches[0];break;
  }catch(e){console.log(JSON.stringify({stage:'organization-query',host:new URL(candidate).hostname,ok:false,error:e.message}));}
 }
 if(!org) throw Error('Could not verify observed organization');
 const companyID=String(org.companyID);
 const company=async(route,body)=>request(host,'/wxProgram/system/companyEncryption',{companyID,url:route,body,method:'POST'});
 const people=await company('/staff/getStaff',{staffID:identity.staffID,dataType:'simple'});
 const person=(people.staff||[]).filter(p=>String(p.staffID)===identity.staffID);
 if(person.length!==1 || !/^\d+$/.test(String(person[0].uniqueID))) throw Error('Own identity did not match exactly');
 const roles=await company('/1075/getStaffRole',{staffNo:identity.staffID});
 if(!roles.data || roles.data[0]?.roleType!==0) throw Error('Expected student role');
 const response=await company('/1081/getAccess',{staff:[{staffNo:identity.staffID}],dataType:'simple-device-control'});
 const matches=(response.data||[]).filter(r=>r.roomNo===identity.roomNo && r.eStatus!==1 && r.dStatus!==3);
 if(matches.length!==1) throw Error('Observed own room did not match one active authorization');
 const room=matches[0];
 const control=room.control||{};
 if(!['card','face','fingerprint','pw','qr'].some(k=>control[k]===1)) throw Error('No existing BLE permission');
 if(!/^([0-9a-f]{2}:){5}[0-9a-f]{2}$/i.test(room.MAC||'')) throw Error('This lock requires serial-number scanning');
 // These are inputs for a local, signed migration record, not a vendor-issued token.
 const migration={schema:'dorm-native-migration/1',appId:'wx5ab2a45f1eba1dcf',source:'official-pc-visible-session',companyID,staffID:identity.staffID,
  personId:String(person[0].uniqueID),roomID:String(room.roomID),deviceID:String(room.deviceID),roomNo:room.roomNo,MAC:room.MAC.toUpperCase(),sn:room.sn||'',
  verifiedAt:new Date().toISOString(),effectiveTime:room.effectiveTime||'',expiryTime:room.expiryTime||''};
 fs.writeFileSync(path.join(root,'private','verified-own-migration.json'),JSON.stringify(migration,null,2));
 fs.writeFileSync(path.join(root,'private','own-organization-policy.json'),JSON.stringify({companyID,user:org.user,operator:org.operator,smallProgram:org.smallProgram}));
 console.log(JSON.stringify({ok:true,identityMatched:true,roomMatched:true,studentRole:true,blePermission:true,macPresent:true,host:new URL(host).hostname,lockCommandSent:false,personalValuesLogged:false}));
}
main().catch(e=>{console.error(JSON.stringify({ok:false,error:e.message,lockCommandSent:false}));process.exitCode=1;});

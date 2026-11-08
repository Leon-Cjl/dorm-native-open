'use strict';
// Maintainer-local automatic signing. Does not generate keys, replace app trust, or perform a login.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const kit=require('../onboarding/session-export.cjs'),root=path.resolve(__dirname,'..');
function fail(s){throw Error(s);}
function readRequest(file,evidence,now=Date.now()){
 const bytes=fs.readFileSync(file);if(bytes.length>16384)fail('待签发文件过大');const r=JSON.parse(bytes);
 if(r.schema!=='dorm-native-request/1'||r.appId!==kit.APP||r.origin!=='observed-own-official-session')fail('待签发格式不匹配');
 const time=Date.parse(r.createdAt);if(!Number.isFinite(time)||time>now+60000||now-time>86400000)fail('请重新采集，待签发文件只接受24小时内记录');
 const obs=r.observation;if(!obs||!Array.isArray(obs.evidence)||obs.evidence.length<1||obs.evidence.length>4)fail('缺少官方页面证据清单');
 const captureTime=Date.parse(obs.capturedAt);if(!Number.isFinite(captureTime)||captureTime>time+60000||time-captureTime>3660000)fail('观测时间不匹配');
 const evidenceRoot=path.resolve(evidence);
 for(const item of obs.evidence){
  if(typeof item.name!=='string'||item.name!==path.basename(item.name)||!/^[a-f0-9]{64}$/.test(item.sha256))fail('证据名称或摘要格式错误');
  if(kit.sha(fs.readFileSync(path.join(evidenceRoot,item.name)))!==item.sha256)fail('需要与采集时一致的官方页面证据');
 }
 kit.validateMigration(r.migration);
 for(const k of ['companyName'])if(typeof r[k]!=='string'||!r[k].trim()||r[k].length>180)fail('缺少学校');
 return {r,sha256:kit.sha(bytes)};
}
function sign(migration,privateKey,pinnedPublicDer){
 const key=crypto.createPrivateKey(privateKey),pub=crypto.createPublicKey(key).export({format:'der',type:'spki'});
 if(!pub.equals(pinnedPublicDer))fail('签名私钥与当前 APK 信任公钥不匹配；不会改写应用公钥');
 const payload=Buffer.from(JSON.stringify(migration));const signature=crypto.sign('sha256',payload,key);
 if(!crypto.verify('sha256',payload,crypto.createPublicKey(key),signature))fail('签名自校验失败');
 return {schema:'dorm-native-signed/1',payload:payload.toString('base64'),signature:signature.toString('base64')};
}
async function main(){
 const args=process.argv.slice(2),[request,evidence,output]=args;
 if(!request||!evidence||!output||args.length!==3)fail('用法：node tools/issue-dormkey.cjs 待签发.dormrequest 官方证据文件夹 输出.dormkey');
 if(path.extname(output)!=='.dormkey'||fs.existsSync(output))fail('输出须为不存在的 .dormkey 文件');
 const {r}=readRequest(request,evidence);
 // Requery only the observed user's exact room. Never trust supplied person/device/MAC fields.
 const current=await kit.collect({companyName:r.companyName,staffID:r.migration.staffID,roomNo:r.migration.roomNo});
 for(const k of ['companyID','staffID','personId','roomID','deviceID','roomNo','MAC'])if(current[k]!==r.migration[k])fail('在线资料与待签发文件不同，请重新采集');
 const trust=fs.readFileSync(path.join(root,'android/src/cn/dormnative/app/MigrationTrust.java'),'utf8').match(/PUBLIC_KEY="([^"]+)"/);
 if(!trust)fail('没有找到当前应用信任公钥');
 const envelope=sign(current,fs.readFileSync(path.join(root,'private/local-migration-signing.pem')),Buffer.from(trust[1],'base64'));
 kit.writeNew(output,JSON.stringify(envelope));console.log(JSON.stringify({ok:true,output:path.resolve(output),signatureVerified:true,lockCommandSent:false,privateFieldsPrinted:false}));
}
module.exports={readRequest,sign};
if(require.main===module)main().catch(e=>{console.error(JSON.stringify({ok:false,error:e.message,lockCommandSent:false}));process.exitCode=1;});

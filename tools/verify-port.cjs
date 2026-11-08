// The original v53 module is executed only in an isolated VM with fake BLE/storage.
// No vendor JS, personal inputs, or migration signing key are included in the source package.
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),crypto=require('node:crypto'),cp=require('node:child_process');
const root=path.resolve(__dirname,'..'), dist=path.join(root,'dist');
const build=JSON.parse(fs.readFileSync(path.join(dist,'build-result.json'),'utf8').replace(/^\uFEFF/,''));
const classes=path.join(build.buildDirectory,'tests');
const run=cp.spawnSync('D:/APPs/Android Studio/jbr/bin/java.exe',['-cp',classes,'cn.dormnative.app.ProtocolTests','--vectors'],{encoding:'utf8'});
if(run.status!==0)throw Error('Java fixture runner failed');
const vectors=JSON.parse(run.stdout);
const vendor=fs.readFileSync('D:/AI/公寓智锁调研/evidence/v53/modules/main-105-pages_utils_bluetooth.js-afebec72.js','utf8');
let comparisons=0;
for(const vector of vectors){
 const writes=[];
 const fake={
  createBLEConnection:o=>o.success({}),getBLEDeviceServices:o=>o.success({services:[{uuid:'0000FFF0-0000-1000-8000-00805F9B34FB'}]}),
  getBLEDeviceCharacteristics:o=>o.success({characteristics:[{uuid:'0000FFF1-0000-1000-8000-00805F9B34FB'}]}),notifyBLECharacteristicValueChange:o=>o.success({}),
  writeBLECharacteristicValue:o=>{writes.push(Buffer.from(new Uint8Array(o.value)).toString('hex'));o.success({});},
  getStorageSync:k=>k==='staffUniqueID'?String(vector.id):'',setStorageSync:()=>{},onBLECharacteristicValueChange:()=>{},showToast:()=>{},hideLoading:()=>{}
 };
 const context=vm.createContext({module:{exports:{}},console:{log:()=>{},error:()=>{}},setTimeout:f=>{f();return 1;}});
 new vm.Script(vendor).runInContext(context,{timeout:1500});
 const exports={};context.module.exports({},exports,n=>n===2?{default:fake}:{});
 exports.openLock('02:00:00:00:00:01','7E');
 if(writes.length!==2 || writes[0]!=='7e' || writes[1]!==vector.hex)throw Error('Port differs from observed original branch');
 comparisons++;
}
const envelope=JSON.parse(fs.readFileSync(path.join(root,'private','本人宿舍授权.dormkey'),'utf8'));
const trust=fs.readFileSync(path.join(root,'android','src','cn','dormnative','app','MigrationTrust.java'),'utf8');
const publicKey=crypto.createPublicKey({key:Buffer.from(trust.match(/PUBLIC_KEY="([^"]+)"/)[1],'base64'),format:'der',type:'spki'});
const payload=Buffer.from(envelope.payload,'base64'),signature=Buffer.from(envelope.signature,'base64');
if(!crypto.verify('sha256',payload,publicKey,signature))throw Error('Migration signature is invalid');
const tampered=Buffer.from(payload);tampered[20]^=1;
if(crypto.verify('sha256',tampered,publicKey,signature))throw Error('Altered payload accepted');
const badSignature=Buffer.from(signature);badSignature[0]^=1;
if(crypto.verify('sha256',payload,publicKey,badSignature))throw Error('Altered signature accepted');
const migration=JSON.parse(payload);
const apk=fs.readFileSync(build.apk);
if(crypto.createHash('sha256').update(apk).digest('hex')!==build.sha256.toLowerCase())throw Error('APK differs from selected build');
// Inspect the DEX entry directly because APK compression would hide plaintext from an outer byte search.
const dex=fs.readFileSync(path.join(build.buildDirectory,'dex','classes.dex'));
const privateNeedles=[migration.staffID,migration.MAC,migration.roomNo,migration.sn,envelope.signature,'BEGIN PRIVATE KEY'].filter(s=>s&&s.length>=8);
if(privateNeedles.some(s=>dex.includes(Buffer.from(s))))throw Error('Personal authorization data found in DEX');
const result={ok:true,originalModuleOfflineComparisons:comparisons,originalModuleHardwareAccess:false,migrationSignatureVerified:true,tamperedPayloadRejected:true,tamperedSignatureRejected:true,personalValuesAbsentFromDex:true,apkSha256:crypto.createHash('sha256').update(apk).digest('hex'),deviceRuntime:'NOT RUN',realDoorUnlock:'NOT RUN'};
fs.writeFileSync(path.join(dist,'port-and-import-checks.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result));

'use strict';
// Creates a private source copy and its own pinned key. Does not weaken the public APK verifier.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'..'),kit=require('../onboarding/session-export.cjs'),issuer=require('../tools/issue-dormkey.cjs');
const SHA=b=>crypto.createHash('sha256').update(b).digest('hex');
function fail(s){throw Error(s);}
function copyFile(source,dest){fs.mkdirSync(path.dirname(dest),{recursive:true});fs.copyFileSync(source,dest,fs.constants.COPYFILE_EXCL);}
function copyDirectory(source,dest){for(const e of fs.readdirSync(source,{withFileTypes:true})){
 const a=path.join(source,e.name),b=path.join(dest,e.name);if(e.isSymbolicLink())fail('源模板不能包含符号链接');
 if(e.isDirectory())copyDirectory(a,b);else if(e.isFile())copyFile(a,b);
}}
function isolateManifest(xml,publicDer){
 const packageId='cn.dormnative.self.k'+SHA(publicDer).slice(0,12);
 if(!xml.includes('package="cn.dormnative.app"'))fail('源模板应用包名不匹配');
 const updated=xml.replace('package="cn.dormnative.app"','package="'+packageId+'"')
  .replace(/android:name="\.([A-Za-z][A-Za-z0-9_]*)"/g,'android:name="cn.dormnative.app.$1"')
  .replace('android:label="宿舍快开"','android:label="宿舍快开·自助版"')
  .replace('android:label="宿舍开锁"','android:label="宿舍开锁·自助"');
 if(/android:name="\./.test(updated))fail('仍有相对组件名，停止生成');
 return {packageId,xml:updated};
}
async function prepare(requestFile,evidenceDir,outputDir,options={}){
 const template=path.resolve(options.templateRoot||root),dest=path.resolve(outputDir),now=options.now??Date.now();
 if(fs.existsSync(dest))fail('输出目录已存在；请指定新的空目录。更新旧自助版时应保留并使用原目录与密钥。');
 if(dest===template||template.startsWith(dest+path.sep))fail('输出不能覆盖或包含源模板目录');
 const {r}=issuer.readRequest(requestFile,evidenceDir,now),current=await (options.collect||kit.collect)({companyName:r.companyName,staffID:r.migration.staffID,roomNo:r.migration.roomNo});
 kit.validateMigration(current);
 for(const k of ['companyID','staffID','personId','roomID','deviceID','roomNo','MAC'])if(current[k]!==r.migration[k])fail('当前本人权限与采集文件不同，请重新读取本人页面');
 const trustPath='android/src/cn/dormnative/app/MigrationTrust.java',originalTrust=fs.readFileSync(path.join(template,trustPath),'utf8');
 if(!/PUBLIC_KEY="[A-Za-z0-9+/=]+"/.test(originalTrust))fail('模板没有预期的固定公钥验签代码');
 if(fs.existsSync(path.join(template,'android/assets/owner.dormkey')))fail('模板资源含个人授权，不是可公开的干净模板');
 const pair=crypto.generateKeyPairSync('rsa',{modulusLength:3072});
 const privatePem=pair.privateKey.export({format:'pem',type:'pkcs8'}),publicDer=pair.publicKey.export({format:'der',type:'spki'});
 const envelope=issuer.sign(current,privatePem,publicDer);
 const manifest=isolateManifest(fs.readFileSync(path.join(template,'android/AndroidManifest.xml'),'utf8'),publicDer);
 fs.mkdirSync(dest,{recursive:true});
 // Explicit allowlist. Never traverse template/private, build, dist, or android/signing.
 for(const name of ['build.ps1','.gitignore'])copyFile(path.join(template,name),path.join(dest,name));
 for(const name of ['android/src','android/res','android/assets'])copyDirectory(path.join(template,name),path.join(dest,name));
 for(const name of ['session-export.cjs','observation.example.json'])copyFile(path.join(template,'onboarding',name),path.join(dest,'onboarding',name));
 for(const name of ['ProtocolTests.java','AutomationTests.java'])copyFile(path.join(template,'tests',name),path.join(dest,'tests',name));
 copyFile(path.join(template,'tools/issue-dormkey.cjs'),path.join(dest,'tools/issue-dormkey.cjs'));
 fs.writeFileSync(path.join(dest,trustPath),originalTrust.replace(/PUBLIC_KEY="[A-Za-z0-9+/=]+"/,'PUBLIC_KEY="'+publicDer.toString('base64')+'"'));
 fs.writeFileSync(path.join(dest,'android/AndroidManifest.xml'),manifest.xml);
 const mainFile=path.join(dest,'android/src/cn/dormnative/app/MainActivity.java');
 const main=fs.readFileSync(mainFile,'utf8');
 const start=main.indexOf('    private void importHelp(){'),end=main.indexOf('    private void help(){',start);
 if(start<0||end<0)fail('模板导入说明定位失败');
 const help='    private void importHelp(){new AlertDialog.Builder(this).setTitle("导入自己的宿舍")\n'
  +'        .setMessage("本版由你自己的电脑生成，只信任你本机自助目录中那一把签名公钥。\\n\\n选择同一目录生成的 .dormkey，程序自动绑定。不要把自助文件导入原来的公共 APK。\\n\\n换宿舍时，在原自助目录用自己的签发工具处理新采集资料；不用找发布者，也不用重新换密钥。")\n'
  +'        .setNegativeButton("关闭",null).setPositiveButton("选择授权文件",(d,w)->importFile()).show();}\n';
 const next=main.slice(0,start)+help+main.slice(end);
 fs.writeFileSync(mainFile,next.replace('先导入属于你本人的 .dormkey 文件；尚未接入微信一键登录。','请导入本机自助目录生成的 .dormkey。此版无需发布者签发。'));
 fs.mkdirSync(path.join(dest,'private'));fs.mkdirSync(path.join(dest,'dist'));
 fs.writeFileSync(path.join(dest,'private/local-migration-signing.pem'),privatePem,{flag:'wx',mode:0o600});
 fs.writeFileSync(path.join(dest,'dist/本人宿舍授权.dormkey'),JSON.stringify(envelope),{flag:'wx',mode:0o600});
 const report={schema:'dorm-native-self-workspace/1',packageId:manifest.packageId,trustPublicKeySha256:SHA(publicDer),templateTrustUnchanged:fs.readFileSync(path.join(template,trustPath),'utf8')===originalTrust,privateIssuerKeyCopied:false,officialLoginPerformed:false,lockCommandSent:false,publicApkCompatible:false};
 fs.writeFileSync(path.join(dest,'self-workspace.json'),JSON.stringify(report,null,2));
 fs.writeFileSync(path.join(dest,'先读我.txt'),'这是你的独立自助版。运行 PowerShell: .\\build.ps1 -Public。把 dist 中生成的 APK 和“本人宿舍授权.dormkey”保存到手机。安装“宿舍快开·自助版”，选文件自动绑定。原公共 APK 不接受这份自签文件。保留此整个目录；private 中是你自己的签发私钥，android/signing 中是构建后生成的 APK 更新密钥，不要对外发布。');
 return {...report,workspace:dest,dormkey:path.join(dest,'dist/本人宿舍授权.dormkey'),next:'在该目录运行 .\\build.ps1 -Public，安装生成的自助版 APK，再导入同目录 .dormkey'};
}
async function main(){
 const [request,evidence,output,...extra]=process.argv.slice(2);
 if(!request||!evidence||!output||extra.length)fail('用法：node self-service/prepare-self.cjs 本人宿舍.dormrequest 证据文件夹 新的自助目录');
 console.log(JSON.stringify({ok:true,...await prepare(request,evidence,output),privateFieldsPrinted:false}));
}
module.exports={prepare,isolateManifest};
if(require.main===module)main().catch(e=>{console.error(JSON.stringify({ok:false,error:e.message,lockCommandSent:false}));process.exitCode=1;});

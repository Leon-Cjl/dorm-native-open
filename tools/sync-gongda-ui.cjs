// Reuse the user's existing Gongda component stylesheet verbatim; no app/account data is copied.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'..'),source='D:/AI/gongda-source/app/styles.css';
const css=fs.readFileSync(source,'utf8'),marker='/* ---------- 图书馆 ---------- */';
if(!css.includes(marker))throw Error('Gongda common component boundary missing');
const shared=css.split(marker)[0];
fs.mkdirSync(path.join(root,'android/assets/ui'),{recursive:true});
fs.writeFileSync(path.join(root,'android/assets/ui/gongda.css'),shared);
fs.writeFileSync(path.join(root,'android/assets/ui/NOTICE.txt'),'Shared UI CSS copied from user project D:/AI/gongda-source/app/styles.css.\nIncludes color tokens, typography, cards, app bar, navigation, buttons and common components.\nSource SHA-256: '+crypto.createHash('sha256').update(css).digest('hex')+'\nNo campus account data or school business logic copied.\n');
console.log(JSON.stringify({sharedCssBytes:Buffer.byteLength(shared),sourceSha256:crypto.createHash('sha256').update(css).digest('hex')}));

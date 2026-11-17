// usage: node export.js <abs path to laojia_compare.html> <out dir>
const {chromium,devices}=require('playwright');const fs=require('fs');
(async()=>{const b=await chromium.launch();const p=await b.newPage({...devices['iPhone 13']});
await p.goto('file://'+process.argv[2]);await p.waitForTimeout(5000);
await p.click('#bCrop');await p.waitForTimeout(5000);
for(const [id,f] of [['iOld','1974_aligned.jpg'],['iNew','2025_cropped.jpg'],['iMix','overlay_50.jpg']]){
 const d=await p.evaluate(async id=>{const r=await fetch(document.getElementById(id).src);const b=await r.blob();return await new Promise(res=>{const fr=new FileReader();fr.onload=()=>res(fr.result);fr.readAsDataURL(b)})},id);
 fs.writeFileSync(process.argv[3]+'/'+f,Buffer.from(d.split(',')[1],'base64'))}
console.log(await p.textContent('#rSize'));await b.close()})();

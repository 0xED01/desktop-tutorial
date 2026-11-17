const {chromium,devices}=require('playwright');const fs=require('fs');
(async()=>{const b=await chromium.launch();const p=await b.newPage({...devices['iPhone 13']});
await p.goto('file://'+process.argv[2]);await p.waitForFunction(()=>window.S&&S.warp,{timeout:60000}).catch(()=>{});await p.waitForTimeout(3000);
const d=await p.evaluate(()=>S.warp.toDataURL('image/png'));fs.writeFileSync(process.argv[3],Buffer.from(d.split(',')[1],'base64'));await b.close()})();

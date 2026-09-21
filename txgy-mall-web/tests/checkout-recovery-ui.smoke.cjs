// Reuses the isolated real-built UI fixture. No live requests, real orders or private credentials.
const assert=require('node:assert/strict');
const {resolve,join}=require('node:path');
const {mkdir,writeFile}=require('node:fs/promises');
const {chromium}=require('playwright');
const {serve,fixture,reports,setBrowser}=require('./consultation-entry-ui.smoke.cjs');
const output=resolve(process.argv[2]||'E:/txgy/local-backups/mall-checkout-recovery-20260917');
const deferred=()=>{let resolve;const promise=new Promise(done=>resolve=done);return {promise,resolve};};
let browser,page;
async function blocked(test){await test.page.getByText(/订单已创建|订单提交结果需要核实/).first().waitFor();assert.equal(await test.button('提交订单').count(),0);assert.equal(test.calls.filter(c=>c.path==='/trade/order/create').length,1);}
(async()=>{
 await mkdir(output,{recursive:true});browser=await chromium.launch({headless:true,channel:'msedge'});setBrowser(browser);
 const hosts={pc:await serve(resolve(__dirname,'../dist')),h5:await serve(resolve(__dirname,'../../txgy-mall-uniapp-master/dist/build/h5'))};
 try {
  for(const client of ['pc','h5']){
   const host=hosts[client];
   for(const unknown of [false,true]){
    const t=await fixture(client,host,'create then leave then late '+(unknown?'unknown':'success')+' blocks original checkout on return');page=t.page;t.state.createGate=deferred();t.state.unknownCreate=unknown;await t.open('checkout');const original=page.url();await t.button('提交订单').click();while(!t.calls.some(c=>c.path==='/trade/order/create'))await page.waitForTimeout(20);
    if(client==='pc'){await page.locator('.cart-entry').click();await page.waitForURL(url=>url.pathname==='/cart');}else await t.open('cart');
    t.state.createGate.resolve();await page.waitForTimeout(120);
    if(client==='pc')await page.goBack({waitUntil:'networkidle'});else await page.goto(original,{waitUntil:'networkidle'});
    await blocked(t);await page.screenshot({path:join(output,client+'-restored-'+(unknown?'unknown':'success')+'.png'),fullPage:true});await t.finish();
   }
   const reload=await fixture(client,host,'lost acknowledgement persists across hard reload');page=reload.page;reload.state.unknownCreate=true;await reload.open('checkout');await reload.button('提交订单').click();await blocked(reload);await page.reload({waitUntil:'networkidle'});await blocked(reload);await reload.finish();

   const fresh=await fixture(client,host,'intentional new purchase after verification entry gets a fresh draft even with same SKU');page=fresh.page;fresh.state.unknownCreate=true;await fresh.open('checkout');await fresh.button('提交订单').click();await blocked(fresh);await fresh.open('cart');await fresh.button(client==='pc'?'去结算':'去结算').click();await page.waitForURL(url=>url.pathname===(client==='pc'?'/checkout':'/pages/order/confirm'));await fresh.button('提交订单').waitFor();if(client==='h5')assert.match(new URL(page.url()).searchParams.get('checkoutIntent'),/^intent-/);assert.equal(fresh.calls.filter(c=>c.path==='/trade/order/create').length,1);await fresh.finish();

   const reject=await fixture(client,host,'explicit business rejection releases original intent for a corrected retry');page=reject.page;reject.state.rejectionRemaining=1;await reject.open('checkout');await reject.button('提交订单').click();await page.getByText('隔离库存不足',{exact:true}).first().waitFor();await page.reload({waitUntil:'networkidle'});await reject.button('提交订单').waitFor();reject.state.unknownCreate=true;await reject.button('提交订单').click();await page.getByText(/订单提交结果需要核实/).first().waitFor();assert.equal(reject.calls.filter(c=>c.path==='/trade/order/create').length,2);await reject.finish();

   const foreign=await fixture(client,host,'replacement member cannot inherit pending intent or original checkout data after reload');page=foreign.page;foreign.state.unknownCreate=true;await foreign.open('checkout');await foreign.button('提交订单').click();await blocked(foreign);await foreign.change();await page.reload({waitUntil:'networkidle'});assert.equal(await foreign.button('提交订单').count(),0);assert.equal(await page.getByText('隔离下单商品',{exact:true}).count(),0);assert.equal(foreign.calls.filter(c=>c.path==='/trade/order/create').length,1);await foreign.finish();

   const storage=await fixture(client,host,'persisting PENDING failure blocks create before any business request');page=storage.page;await storage.open('checkout');await page.evaluate(()=>{const original=Storage.prototype.setItem;Storage.prototype.setItem=function(key,value){if(String(key).startsWith('mall-checkout-intent:')&&String(value).includes('PENDING'))throw new DOMException('isolated quota','QuotaExceededError');return original.call(this,key,value);};});await storage.button('提交订单').click();await page.getByText(/订单提交结果需要核实/).first().waitFor();assert.equal(storage.calls.filter(c=>c.path==='/trade/order/create').length,0);await storage.finish();
  }
  await writeFile(join(output,'checkout-recovery-browser.json'),JSON.stringify({passed:true,mockOnly:true,realBusinessWrites:0,testedAt:new Date().toISOString(),scenarios:reports},null,2));console.log(JSON.stringify({passed:true,scenarios:reports.length,output}));
 }catch(error){await page?.screenshot({path:join(output,'failure.png'),fullPage:true}).catch(()=>{});await writeFile(join(output,'failure.json'),JSON.stringify({error:String(error),stack:error.stack,reports},null,2));throw error;}
 finally{await browser.close();await Promise.all(Object.values(hosts).map(host=>new Promise(done=>host.server.close(done))));}
})().catch(error=>{console.error(error);process.exitCode=1;});

// Isolated built UI + mock APIs only; never logs in real accounts or writes business data.
const assert=require('node:assert/strict');
const {createServer}=require('node:http');
const {readFile,writeFile,mkdir}=require('node:fs/promises');
const {resolve,extname,join,sep}=require('node:path');
const {chromium}=require('playwright');
const root=resolve(__dirname,'../dist'),output=resolve(process.argv[2]||'E:/txgy/local-backups/mall-business-20260917/pc');
const mime={'.js':'text/javascript','.css':'text/css','.html':'text/html','.svg':'image/svg+xml','.png':'image/png','.woff2':'font/woff2'};
let browser,origin,activePage;const results=[];
const server=createServer(async(req,res)=>{try{
 const pathname=decodeURIComponent(new URL(req.url,'http://localhost').pathname);let file=resolve(root,'.'+pathname);
 if((file!==root&&!file.startsWith(root+sep))||pathname.includes('/app-api/'))return res.writeHead(503).end();
 if(!extname(file))file=join(root,'index.html');res.writeHead(200,{'Content-Type':mime[extname(file)]||'application/octet-stream'}).end(await readFile(file));
}catch{res.writeHead(404).end();}});
const valid={id:1,count:2,selected:false,spu:{id:11,name:'可售测试岩棉'},sku:{id:12,spuId:11,price:1200,stock:20,properties:[]}};
const invalid={id:2,count:3,selected:true,spu:{id:21,name:'商品暂不可购买'},sku:null};
async function fixture(name,opts={}){
 const context=await browser.newContext({viewport:{width:1480,height:1000},serviceWorkers:'block'});
 await context.addInitScript(()=>{localStorage.setItem('mall-pc-access-token','isolated-access');localStorage.setItem('mall-pc-refresh-token','isolated-refresh');});
 const page=activePage=await context.newPage();page.setDefaultTimeout(12000);const calls=[],errors=[];
 const state={cart:{validList:[],invalidList:[structuredClone(invalid)]},failCart:false,failConfig:false,delayApply:false,status:20,config:{afterSaleRefundReasons:['不需要了'],afterSaleReturnReasons:['商品破损','尺寸不符']},...opts};
 page.on('pageerror',error=>errors.push(error.message));
 await page.route('**/*',async route=>{
  const req=route.request(),url=new URL(req.url());if(!url.pathname.includes('/app-api/'))return url.origin===origin||url.protocol==='data:'?route.continue():route.abort();
  const path=url.pathname.slice(url.pathname.indexOf('/app-api/')+8),body=req.postDataJSON();calls.push({path,body,method:req.method(),query:url.search});
  let data=[],code=0,msg='';
  if(path==='/member/mall-tenant/config')data={enabled:false,legacyTenantId:1,tenants:[],capabilities:{}};
  else if(path==='/member/mall-tenant/session')data={tenantId:1,userId:77};
  else if(path==='/member/user/get')data={tenantId:1,id:77,nickname:'隔离测试会员',memberIdentity:'buyer'};
  else if(path==='/product/enterprise-membership/mine')data=null;
  else if(path==='/product/comparison/list')data={version:0,skuIds:[],quantity:1,preference:'PRICE'};
  else if(path==='/trade/order-workbench/summary')data={scopeKey:'1:1:77:BUYER:0',pendingCount:0,unreadCount:0,newCount:0,cursor:'0',resetRequired:false,pollIntervalSeconds:30,entries:[],rowEntries:[],hasMore:false,todoCounts:{},orderStatusCounts:{}};
  else if(path==='/trade/cart/list'){if(state.failCart)return route.abort('failed');data=state.cart;}
  else if(path==='/trade/cart/delete'){const ids=url.searchParams.get('ids').split(',');for(const key of ['validList','invalidList'])state.cart[key]=state.cart[key].filter(item=>!ids.includes(String(item.id)));data=true;}
  else if(path==='/trade/cart/update-selected'){for(const item of state.cart.validList)if(body.ids.includes(item.id))item.selected=body.selected;data=true;}
  else if(path==='/trade/order/get-detail')data={id:55,no:'MOCK-55',status:state.status,payPrice:2400,items:[{id:111,spuName:'隔离测试岩棉',price:1200,payPrice:2400,count:2,properties:[]}]};
  else if(path==='/trade/config/get'){if(state.failConfig)return route.abort('failed');data=state.config;}
  else if(path==='/trade/after-sale/create'){if(state.delayApply)await new Promise(done=>setTimeout(done,250));data=501;}
  else if(path==='/trade/after-sale/page')data={list:[],total:0};
  else if(req.method()!=='GET'&&path!=='/trade/order-workbench/ack')throw new Error('Unexpected write: '+path);
  await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({code,data,msg})});
 });
 return {page,state,calls,async finish(){assert.deepEqual(errors,[]);results.push({name,passed:true});await context.close();}};
}
async function reason(test,text){await test.page.locator('.el-select').click();await test.page.getByRole('option',{name:text,exact:true}).click();}
(async()=>{await mkdir(output,{recursive:true});await new Promise(done=>server.listen(0,'127.0.0.1',done));origin='http://127.0.0.1:'+server.address().port;browser=await chromium.launch({channel:'msedge',headless:true});try{
 const invalidOnly=await fixture('all-invalid cart stays visible and safely removable with missing SKU metadata');
 await invalidOnly.page.goto(origin+'/cart',{waitUntil:'networkidle'});await invalidOnly.page.getByRole('heading',{name:'暂不可购买（1）'}).waitFor();
 assert.equal(await invalidOnly.page.getByText('购物车还是空的',{exact:true}).count(),0);assert.equal(await invalidOnly.page.getByRole('button',{name:'去结算',exact:true}).count(),0);
 assert.equal(await invalidOnly.page.locator('.invalid-cart img,.invalid-cart a').count(),0);assert.equal(await invalidOnly.page.locator('.invalid-cart').innerText().then(text=>text.includes('¥')),false);
 await invalidOnly.page.screenshot({path:join(output,'pc-invalid-cart.png'),fullPage:true});
 await invalidOnly.page.getByRole('button',{name:'清理失效商品'}).click();await invalidOnly.page.getByRole('button',{name:'确定删除',exact:true}).click();
 await invalidOnly.page.getByText('购物车还是空的',{exact:true}).waitFor();assert(invalidOnly.calls.find(call=>call.path==='/trade/cart/delete').query.includes('ids=2'));await invalidOnly.finish();

 const mixed=await fixture('select-all excludes invalid lines from server writes and checkout subtotal',{cart:{validList:[structuredClone(valid)],invalidList:[structuredClone(invalid)]}});
 await mixed.page.goto(origin+'/cart',{waitUntil:'networkidle'});await mixed.page.getByText('全选',{exact:true}).click();
 await mixed.page.locator('.cart-summary strong').getByText('¥24.00',{exact:true}).waitFor();assert.deepEqual(mixed.calls.find(call=>call.path==='/trade/cart/update-selected').body.ids,[1]);
 assert.equal(await mixed.page.locator('.invalid-cart input[type=checkbox]').count(),0);await mixed.finish();

 const failed=await fixture('load failure is not mislabeled as empty and retry recovers invalid entries',{failCart:true});
 await failed.page.goto(origin+'/cart',{waitUntil:'networkidle'});await failed.page.getByText('购物车加载失败',{exact:true}).waitFor();assert.equal(await failed.page.getByText('购物车还是空的',{exact:true}).count(),0);
 failed.state.failCart=false;await failed.page.getByRole('button',{name:'重新加载'}).click();await failed.page.getByRole('heading',{name:'暂不可购买（1）'}).waitFor();await failed.finish();

 const apply=await fixture('changing refund type clears the old reason and submits only the return dictionary',{delayApply:true});
 await apply.page.goto(origin+'/member/after-sales/apply?orderId=55&itemId=111',{waitUntil:'networkidle'});await reason(apply,'不需要了');
 await apply.page.getByText('退货退款',{exact:true}).click();assert.equal(await apply.page.locator('.el-select__placeholder').innerText(),'请选择原因');
 await apply.page.locator('.el-select').click();await apply.page.getByRole('option',{name:'商品破损',exact:true}).waitFor();assert.equal(await apply.page.getByRole('option',{name:'不需要了',exact:true}).count(),0);
 await apply.page.getByRole('option',{name:'商品破损',exact:true}).click();await apply.page.getByRole('option',{name:'商品破损',exact:true}).waitFor({state:'hidden'});await apply.page.screenshot({path:join(output,'pc-return-reasons.png'),fullPage:true,animations:'disabled'});
 await apply.page.getByRole('button',{name:'提交售后申请',exact:true}).dblclick();await apply.page.waitForURL(/\/member\/after-sales(?:\?|$)/);
 const writes=apply.calls.filter(call=>call.path==='/trade/after-sale/create');assert.equal(writes.length,1);assert.equal(writes[0].body.way,20);assert.equal(writes[0].body.applyReason,'商品破损');await apply.finish();

 const fallback=await fixture('missing return dictionary offers other rather than reusing refund reasons',{config:{afterSaleRefundReasons:['不需要了']}});
 await fallback.page.goto(origin+'/member/after-sales/apply?orderId=55&itemId=111',{waitUntil:'networkidle'});await fallback.page.getByText('退货退款',{exact:true}).click();await reason(fallback,'其他');assert.equal(fallback.calls.filter(call=>call.path==='/trade/after-sale/create').length,0);await fallback.finish();

 const unshipped=await fixture('unshipped order does not offer return-refund',{status:10});await unshipped.page.goto(origin+'/member/after-sales/apply?orderId=55&itemId=111',{waitUntil:'networkidle'});await unshipped.page.getByText('仅退款',{exact:true}).waitFor();assert.equal(await unshipped.page.getByText('退货退款',{exact:true}).count(),0);await unshipped.finish();

 const configFailed=await fixture('configuration error blocks submission and can be retried',{failConfig:true});await configFailed.page.goto(origin+'/member/after-sales/apply?orderId=55&itemId=111',{waitUntil:'networkidle'});await configFailed.page.getByText('暂时无法申请售后',{exact:true}).waitFor();assert.equal(await configFailed.page.getByRole('button',{name:'提交售后申请',exact:true}).count(),0);configFailed.state.failConfig=false;await configFailed.page.getByRole('button',{name:'重新加载'}).click();await configFailed.page.getByRole('button',{name:'提交售后申请',exact:true}).waitFor();await configFailed.finish();
 await writeFile(join(output,'pc-cart-aftersale-browser.json'),JSON.stringify({passed:true,mockOnly:true,realBusinessWrites:0,testedAt:new Date().toISOString(),scenarios:results},null,2));console.log('PASS '+results.length+' PC cart/aftersale browser scenarios');
}catch(error){await activePage?.screenshot({path:join(output,'failure.png'),fullPage:true});await writeFile(join(output,'failure.json'),JSON.stringify({error:error.message,results},null,2));throw error;}finally{await browser.close();await new Promise(done=>server.close(done));}})().catch(error=>{console.error(error);process.exitCode=1;});

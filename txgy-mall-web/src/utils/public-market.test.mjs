import { test } from 'vitest';
import assert from 'node:assert/strict';
import { parsePublicMarkets, publicMarketRequest, createPublicMarketClient, publicMarketState, createPublicMarketBrowser, publicPrice } from './public-market.mjs';
const catalog = () => ({ enabled: true, publicTenantId: 9001, tenants: [{ tenantId: 9001, name: '公共商城' }, { tenantId: 9002, name: '企业商城' }], capabilities: { browsePreview: true, browseSwitch: false } });
const envelope = data => ({ ok: true, json: async () => ({ code: 0, data }) });
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };
test('preview requires its own capability and does not trust full switch flags', () => {
  for (const value of [{ ...catalog(), enabled: false }, { ...catalog(), capabilities: { browseSwitch: true } }]) assert.equal(parsePublicMarkets(value).enabled, false);
});
test('directory exposes minimal fields and validates public tenant membership', () => {
  const value = parsePublicMarkets(catalog()); assert.deepEqual(value.tenants[0], {tenantId:'9001',name:'公共商城'});
  assert(Object.isFrozen(value.tenants)); assert.equal(value.publicTenantId,'9001');
});
test('malformed, duplicate and unlisted public identities are rejected', () => {
  for (const value of [null, {}, {...catalog(),publicTenantId:1}, {...catalog(),tenants:[{tenantId:9001,name:''}]}, {...catalog(),tenants:[{tenantId:9001,name:'A'},{tenantId:9001,name:'B'}]}, {...catalog(),publicTenantId:'9007199254740999'}]) assert.throws(()=>parsePublicMarkets(value));
});
test('no arbitrary endpoint, private route or mutating operation can be requested', () => {
  for (const kind of ['login','/member/user/get','/trade/cart/add','__proto__','constructor']) assert.throws(()=>publicMarketRequest('9001',kind));
});
test('params cannot inject tenant, authorization, arbitrary page size or query fields', () => {
  const request=publicMarketRequest('9002','products',{tenantId:1,Authorization:'secret',pageSize:1000,pageNo:2,shopId:7,categoryId:8,keyword:'  材料  ',url:'https://example.invalid'});
  assert.deepEqual(request.params,{pageNo:2,pageSize:12,shopId:'7',categoryId:'8',keyword:'材料'});assert.equal(request.headers['tenant-id'],'9002');assert.equal(request.headers.Authorization,undefined);
});
test('IDs and pages reject malformed and unsafe numbers',()=>{
  for (const id of ['0','-1',' 9001','9001,9002','9007199254740999']) assert.throws(()=>publicMarketRequest(id,'shops'));
  for (const pageNo of [0,-1,1.1,'bad']) assert.throws(()=>publicMarketRequest('9001','products',{pageNo}));
  assert.throws(()=>publicMarketRequest('9001','detail',{id:'1&tenantId=2'}));
});
test('directory and target requests are anonymous and never refresh or redirect',async()=>{
  const calls=[];const client=createPublicMarketClient('/app-api',async(url,options)=>{calls.push({url,options});return envelope(url.includes('/config')?catalog():{list:[],total:0});});
  await client.read('9002','products',{keyword:'a&b'});
  assert.equal(calls.length,2);assert.equal(calls[0].options.headers['tenant-id'],undefined);
  assert.equal(calls[1].options.headers['tenant-id'],'9002');assert.match(calls[1].url,/keyword=a%26b/);
  for(const {options} of calls){assert.equal(options.method,'GET');assert.equal(options.credentials,'omit');assert.equal(options.redirect,'error');assert.equal(options.cache,'no-store');assert.equal(options.headers.Authorization,undefined);}
});
test('hidden tenant is rejected before target request',async()=>{
  let calls=0;const client=createPublicMarketClient('/app-api',async()=>{calls++;return envelope(catalog());});
  await assert.rejects(client.read('9003','shops'));assert.equal(calls,1);
});
test('revoked capability is checked again on each query',async()=>{
  let enabled=true,calls=0;const client=createPublicMarketClient('/app-api',async url=>{calls++;return envelope(url.includes('/config')?{...catalog(),enabled}:[]);});
  await client.read('9001','shops');enabled=false;await assert.rejects(client.read('9001','shops'));assert.equal(calls,3);
});
test('failed directory never falls back to original tenant',async()=>{
  const calls=[];const client=createPublicMarketClient('/app-api',async url=>{calls.push(url);return {ok:false};});
  await assert.rejects(client.read('9001','products'));assert.deepEqual(calls,['/app-api/member/mall-tenant/config']);
});
test('unknown product failures do not leak internal server messages',async()=>{
  const client=createPublicMarketClient('/app-api',async url=>url.includes('/config')?envelope(catalog()):{ok:true,json:async()=>({code:500,msg:'private secret'})});
  await assert.rejects(client.read('9001','detail',{id:9}),error=>!error.message.includes('private secret'));
});
function fixture(read) { const state=publicMarketState(); const client={directory:async()=>parsePublicMarkets(catalog()),read:read|| (async(t,kind)=>kind==='products'?{list:[{id:1,name:t}],total:1}:[])};return {state,browser:createPublicMarketBrowser(state,client),client};}
test('default public market and explicit enterprise market load independent records',async()=>{
  const {state,browser}=fixture();await browser.open();assert.equal(state.tenantId,'9001');await browser.open('9002');assert.equal(state.products[0].name,'9002');assert.equal(state.loading,false);
});
test('unknown target never silently opens public or original tenant',async()=>{
  const {state,browser}=fixture();await browser.open('9999');assert(state.error);assert.equal(state.tenantId,'');assert.deepEqual(state.products,[]);
});
test('switch clears old filters, rows and detail before network completion',async()=>{
  const {state,browser}=fixture();await browser.open('9001');state.keyword='private';state.shopId='7';state.categoryId='8';state.detail={id:1};
  const pending=browser.open('9002');assert.deepEqual(state.products,[]);assert.equal(state.detail,null);assert.equal(state.shopId,'');assert.equal(state.keyword,'');await pending;
});
test('slow A response cannot overwrite B even when switching A-B-A',async()=>{
  const pending=deferred();let first=true;
  const {state,browser}=fixture(async(t,k)=>{if(k!=='products')return [];if(first){first=false;return pending.promise;}return {list:[{id:2,name:t}],total:1};});
  const a=browser.open('9001');await new Promise(r=>setTimeout(r,0));await browser.open('9002');await browser.open('9001');
  pending.resolve({list:[{id:1,name:'obsolete'}],total:1});await a;assert.equal(state.products[0].id,2);
});
test('newer search wins over late search and old details',async()=>{
  const {state,browser,client}=fixture();await browser.open();const pending=deferred();client.read=async(t,k,p)=>p?.keyword==='old'?pending.promise:{list:[{id:2,name:'最新商品'}],total:1};
  state.keyword='old';const old=browser.search();state.keyword='new';await browser.search();pending.resolve({list:[{id:1}],total:1});await old;assert.equal(state.products[0].id,2);
});
test('empty response and failure have different states, no placeholder goods',async()=>{
  const {state,browser,client}=fixture(async(t,k)=>k==='products'?{list:[],total:0}:[]);await browser.open();assert.equal(state.error,'');assert.equal(state.total,0);
  client.read=async()=>{throw new Error('offline');};await browser.search();assert.equal(state.error,'offline');assert.deepEqual(state.products,[]);
});
test('malformed product response is not treated as empty',async()=>{
  const {state,browser}=fixture(async(t,k)=>k==='products'?{list:null,total:0}:[]);await browser.open();assert(state.error);assert.deepEqual(state.products,[]);
});
test('detail is tied to requested product and market',async()=>{
  const {state,browser,client}=fixture();await browser.open();client.read=async()=>({id:8});await browser.detail(9);assert.equal(state.detail,null);assert(state.detailError);
});
test('closing detail discards pending response',async()=>{
  const {state,browser,client}=fixture();await browser.open();const pending=deferred();client.read=()=>pending.promise;
  const task=browser.detail(9);browser.closeDetail();pending.resolve({id:9});await task;assert.equal(state.detail,null);assert.equal(state.detailLoading,false);
});
test('leaving browser rejects old responses without changing original session',async()=>{
  const {state,browser,client}=fixture();await browser.open();const pending=deferred();client.read=()=>pending.promise;
  const task=browser.detail(9);browser.dispose();pending.resolve({id:9});await task;assert.equal(state.detail,null);assert.deepEqual(state.products,[]);
});
test('unknown prices never render as zero',()=>{
  for (const price of [null,undefined,-1,'100',NaN]) assert.equal(publicPrice(price),'价格待确认');
  assert.equal(publicPrice(0),'¥0.00');assert.equal(publicPrice(12345),'¥123.45');
});

test('malformed list rows fail as an error without rendering null records',async()=>{
  for(const row of [null,{}, {id:1,name:''},{id:[1],name:'invalid'}]){
    const {state,browser}=fixture(async(t,k)=>k==='products'?{list:[row],total:1}:[]);await browser.open();assert(state.error);assert.deepEqual(state.products,[]);
  }
});
test('malformed SKU cannot crash the detail renderer',async()=>{
  const {state,browser,client}=fixture();await browser.open();client.read=async()=>({id:9,name:'商品',skus:[null]});
  await browser.detail(9);assert.equal(state.detail,null);assert(state.detailError);
});
test('disposed browser cannot start another directory request',async()=>{
  const {browser,client}=fixture();let calls=0;client.directory=async()=>{calls++;return parsePublicMarkets(catalog());};browser.dispose();await browser.open();assert.equal(calls,0);
});

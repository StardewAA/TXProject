import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { logisticsMessages, normalizeLogistics, createLatestTask, normalizeLocationCapability,
  manualMapMessage, locationSummary, clearAddressLocation, fulfillmentTime, logisticsMapQuery,
  logisticsMapQueries, logisticsReceiverPoint } from '../sheep/helper/fulfillment-view.mjs';

for (const queryStatus of Object.keys(logisticsMessages)) {
  test(`logistics ${queryStatus} uses explicit truthful explanation and creates no events`, () => {
    const value = normalizeLogistics({ queryStatus, message: 'untrusted raw upstream payload' });
    assert.equal(value.message, logisticsMessages[queryStatus]);
    assert.equal(value.transportStatus, 'UNKNOWN'); assert.deepEqual(value.events, []);
  });
}
test('unknown transport status or text containing signed never fabricates a signed state', () => {
  const info = normalizeLogistics({ queryStatus: 'AVAILABLE', transportStatus: '12345',
    events: [{ content: '疑似签收，核实中', time: '2026-09-17 10:00:00' }] });
  assert.equal(info.transportStatus, 'UNKNOWN'); assert.equal(info.events[0].statusLabel, '物流更新');
});
test('malformed logistics fails instead of showing an empty successful tracking list', () => {
  for (const value of [null, [], {}, { queryStatus: '__proto__' }]) assert.throws(() => normalizeLogistics(value));
});
test('provider error can display cached stale events and server cooldown', () => {
  const value = normalizeLogistics({ queryStatus: 'PROVIDER_ERROR', stale: true, cached: true,
    refreshAfterSeconds: 30, events: [{ content: '物流扫描信息', status: 'IN_TRANSIT' }] });
  assert.equal(value.events[0].statusLabel, '运输中'); assert.equal(value.refreshAfterSeconds, 30);
  assert.equal(value.stale, true); assert.equal(value.cached, true);
});
test('logistics map uses only provider place text, chronological unique nodes and a valid receiver snapshot', () => {
  assert.equal(logisticsMapQuery('【上海市浦东新区】快件已发出'), '上海市浦东新区');
  assert.equal(logisticsMapQuery('快件已到达西安市未央区分拨中心'), '西安市');
  assert.equal(logisticsMapQuery('运输中，请耐心等待'), '');
  assert.deepEqual(logisticsMapQueries([
    {time:'2026-09-18 12:00:00',content:'到达北京市转运中心'},
    {time:'2026-09-18 10:00:00',content:'【西安市未央区】已揽收'},
    {time:'2026-09-18 13:00:00',content:'离开北京市分拨中心'},
  ]).map(item => item.query), ['西安市未央区', '北京市']);
  assert.deepEqual(logisticsReceiverPoint({receiverLongitude:108.9,receiverLatitude:34.3,receiverCoordSystem:'GCJ02'}), [108.9,34.3]);
  assert.equal(logisticsReceiverPoint({receiverLongitude:108.9,receiverLatitude:34.3,receiverCoordSystem:'WGS84'}), null);
});
test('newer request, page hide, and account changes reject obsolete responses', () => {
  let active = true; const task = createLatestTask(() => active);
  const old = task.begin(), current = task.begin(); assert.equal(old(), false); assert.equal(current(), true);
  task.invalidate(); assert.equal(current(), false);
  const last = task.begin(); active = false; assert.equal(last(), false);
});
test('map disabled, failed or unknown config always leaves manual input available', () => {
  for (const value of [null, {status:'NOT_CONFIGURED'}, {enabled:true,scriptUrl:'https://untrusted.test'}]) {
    const result = normalizeLocationCapability(value);
    assert.equal(result.enabled, false); assert.equal(result.manualInputEnabled, true);
    assert.equal(result.message, manualMapMessage); assert.equal(result.scriptUrl, undefined);
  }
});
test('old null/empty/invalid/wrong-system coordinates do not display fake zero location', () => {
  for (const value of [{}, {longitude:null,latitude:null}, {longitude:'',latitude:''},
    {longitude:181,latitude:20,coordSystem:'GCJ02'}, {longitude:100,latitude:20,coordSystem:'WGS84'}]) {
    assert.equal(locationSummary(value), '');
  }
});
test('valid zero coordinate and immutable receiver snapshot display as reference not GPS', () => {
  assert.match(locationSummary({longitude:0,latitude:0,coordSystem:'GCJ02'}), /0\.000000/);
  assert.match(locationSummary({receiverLongitude:100,receiverLatitude:20,receiverCoordSystem:'GCJ02'}, 'receiver'), /不是车辆实时位置/);
});
test('coordinate clearing keeps textual delivery data, removes point provenance and never mutates input', () => {
  const original={name:'测试',detailAddress:'门牌',areaId:1,longitude:108,latitude:34,coordSystem:'GCJ02',locationSource:'MAP_PICKER'};
  const updated=clearAddressLocation(original);
  assert.equal(updated.detailAddress,'门牌'); assert.equal(updated.name,'测试');
  assert.equal(updated.longitude,null); assert.equal(updated.locationSource,null); assert.equal(updated.clearLocation,true);
  assert.equal(original.longitude,108);
});

function addressHarness(validate = () => Promise.resolve(true)) {
  let alive=true, expire; const calls=[], hooks={};
  const source=readFileSync(new URL('../pages/user/address/edit.vue',import.meta.url),'utf8')
    .split('<script setup>')[1].split('</script>')[0].replace(/^\s*import[^\n]+\r?\n/gm,'');
  const dependencies={ref:value=>({value}),reactive:value=>value,unref:()=>({validate}),computed:fn=>({get value(){return fn();}}),watch:()=>{},
    sheep:{$router:{back:()=>calls.push('back')},$helper:{toast:()=>{}}},
    onLoad:fn=>hooks.load=fn,onHide:fn=>hooks.hide=fn,onShow:fn=>hooks.show=fn,
    isEmpty:()=>true,mobile:{},AreaApi:{getAreaTree:()=>Promise.resolve({code:0,data:[]})},
    AddressApi:{createAddress:async data=>(calls.push(data),{code:0}),updateAddress:async data=>(calls.push(data),{code:0}),
      getAddress:async id=>({code:0,data:{id:Number(id),name:'旧地址',areaId:1,detailAddress:'门牌'}}),
      getLocationCapability:async()=>({code:0,data:{status:'NOT_CONFIGURED'}}),deleteAddress:async id=>(calls.push(id),{code:0})},
    useTenantTask:fn=>(expire=fn,{current:()=>alive,capture:()=>()=>alive,invalidate:()=>{}}),
    manualMapMessage,normalizeLocationCapability,locationSummary,clearAddressLocation,createLatestTask,
    uni:{getStorageSync:()=>[],setStorageSync:()=>{},showModal:options=>hooks.modal=options}};
  const result = new Function(...Object.keys(dependencies), source+';return {state,onSave,onDelete,openMap,loadAddress};')(...Object.values(dependencies));
  return {...result,calls,hooks,dependencies,change(){alive=false;expire();}};
}
test('manual address saves with map unavailable and no coordinate requirement',async()=>{
  const h=addressHarness(); h.state.model.name='测试'; h.state.model.detailAddress='门牌';
  await h.openMap(); await h.onSave(); assert.equal(h.calls.length,2); assert.equal(h.calls[0].name,'测试'); assert.equal(h.calls[1],'back');
});
test('account change during validation prevents address submission',async()=>{
  let done;const h=addressHarness(()=>new Promise(resolve=>done=resolve));
  const work=h.onSave();h.change();done(true);await work;assert.deepEqual(h.calls,[]);assert.deepEqual(h.state.model,{});
});
test('delete confirmation cannot act as a newly logged in user',async()=>{
  const h=addressHarness();h.state.model.id=7;h.onDelete();h.change();await h.hooks.modal.success({confirm:true});assert.deepEqual(h.calls,[]);
});
test('failed existing-address load cannot become a new-address save',async()=>{
  const h=addressHarness();h.state.addressId='7';await h.onSave();assert.deepEqual(h.calls,[]);
});
test('wrong order response and inherited status names cannot render misleading data',()=>{
  assert.throws(()=>normalizeLogistics({orderId:8,queryStatus:'AVAILABLE'},7));
  assert.equal(normalizeLogistics({orderId:7,queryStatus:'AVAILABLE',events:[{content:'节点',status:'constructor'}]},'7').events[0].statusLabel,'物流更新');
});
test('blocked ERP or unconfigured responses discard any contradictory events/status',()=>{
  for(const queryStatus of ['ERP_PENDING','ERP_REJECTED','NOT_CONFIGURED','NOT_SHIPPED']){
    const info=normalizeLogistics({queryStatus,transportStatus:'SIGNED',events:[{content:'错误数据'}]});
    assert.deepEqual(info.events,[]);assert.equal(info.transportStatus,'UNKNOWN');
  }
});
test('rapid repeated save while validation pending emits one command',async()=>{
  let finish;const h=addressHarness(()=>new Promise(resolve=>finish=resolve));
  const first=h.onSave();await h.onSave();finish(true);await first;
  assert.equal(h.calls.filter(value=>typeof value==='object').length,1);
});
test('logistics timestamps accept backend epoch milliseconds and ISO/space fixtures without NaN',()=>{
  for(const value of [1789610400000,'1789610400000','2026-09-17T10:00:00','2026-09-17 10:00:00']){
    assert.match(fulfillmentTime(value),/^2026-09-17 \d{2}:\d{2}:\d{2}$/);
  }
  for(const value of [null,undefined,{},'', 'not-a-date'])assert.equal(fulfillmentTime(value),'时间未提供');
});
test('returning while address load is pending starts fresh request; old finally cannot hide current loading',async()=>{
  const h=addressHarness(), pending=[];
  h.dependencies.AddressApi.getAddress=()=>new Promise(resolve=>pending.push(resolve));
  h.state.addressId='7'; const old=h.loadAddress();h.hooks.hide();h.hooks.show();assert.equal(pending.length,2);
  pending[0]({code:0,data:{id:7,name:'过时地址'}});await old;
  assert.equal(h.state.loading,true);assert.equal(h.state.loaded,false);
  pending[1]({code:0,data:{id:7,name:'当前地址',areaId:1,detailAddress:'门牌'}});
  await Promise.resolve();await Promise.resolve();assert.equal(h.state.loaded,true);assert.equal(h.state.model.name,'当前地址');
});
test('map service business failure explains unavailable rather than pretending unconfigured success',async()=>{
  const h=addressHarness();h.dependencies.AddressApi.getLocationCapability=async()=>({code:500});
  await h.openMap();assert.match(h.state.mapMessage,/暂不可用/);assert.match(h.state.mapMessage,/手工/);
});

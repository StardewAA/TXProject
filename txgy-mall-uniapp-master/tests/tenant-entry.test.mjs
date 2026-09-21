import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ENTRY_KEY, entryId, entryHint, requireEntry, scopedTokenKey, selectEntry } from '../sheep/helper/tenant-entry.mjs';
const fixture = () => ({ enabled: true, legacyTenantId: 1, tenants: [{tenantId:9001,name:'公共测试商城'},{tenantId:9002,name:'企业测试商城'}], capabilities:{ scopedEntry:true } });
const storage = () => { const values = new Map(); return { values, getItem:key=>values.get(key)??null, setItem:(key,value)=>values.set(key,value) }; };
test('entry is a per-tab hint only and token keys preserve legacy while isolating other tenants', () => {
  const s=storage(); assert.equal(entryHint(s,'1').tenantId,'1');
  s.setItem(ENTRY_KEY,'9001'); assert.equal(entryHint(s,'1').tenantId,'9001');
  assert.equal(scopedTokenKey('token','1','1'),'token');
  assert.equal(scopedTokenKey('token','9001','1'),'token:tenant:9001');
  assert.notEqual(scopedTokenKey('token','9001','1'),scopedTokenKey('token','9002','1'));
});
test('full reload destinations are local and carry tenant but never token or prior product id', () => {
  const s=storage();
  assert.equal(selectEntry(s,fixture(),'9001','1','pc','register'),'/register?tenantId=9001&registrationTenant=9001');
  assert.equal(selectEntry(s,fixture(),'9002','1','h5'),'/pages/index/index?tenantId=9002');
  assert.equal(s.getItem(ENTRY_KEY),'9002');
});
test('disabled, missing and duplicate directories cannot authorize target entry', () => {
  for (const config of [{...fixture(),enabled:false},{...fixture(),capabilities:{}},{...fixture(),tenants:[]},{...fixture(),legacyTenantId:2},{...fixture(),tenants:[{tenantId:9001,name:'A'},{tenantId:9001,name:'B'}]}]) {
    const s=storage(); assert.throws(()=>selectEntry(s,config,'9001','1','pc')); assert.equal(s.getItem(ENTRY_KEY),null);
  }
});
test('unknown and unsafe ids cannot silently select legacy', () => {
  for(const id of ['','01','-1','0','abc','1e3','9007199254740992','//evil',{},null]) assert.throws(()=>entryId(id));
  assert.throws(()=>requireEntry(fixture(),'9999','1'));
});
test('URL aliases select one validated hint; conflicts and malformed hints fail closed', () => {
  for(const href of ['https://test/?tenantId=9001','https://test/#/goods?id=7&tenant-id=9001','https://test/?tenant_id=9001']) assert.deepEqual(entryHint(storage(),'1',href),{tenantId:'9001',invalid:false});
  for(const href of ['https://test/?tenantId=1&tenantId=9001','https://test/?tenantId=1#/goods?tenantId=9001','https://test/?tenant_id=x','https://test/?tenantId=']) assert.equal(entryHint(storage(),'1',href).invalid,true);
});
test('entry never grants authority when directory revoked after tab hint is stored', () => {
  const s=storage(); selectEntry(s,fixture(),'9001','1','pc');
  assert.equal(entryHint(s,'1').tenantId,'9001');
  assert.throws(()=>requireEntry({...fixture(),capabilities:{scopedEntry:false}},entryHint(s,'1').tenantId,'1'));
});
test('blocked storage fails clearly; failed switch does not produce a usable destination', () => {
  assert.equal(entryHint({getItem(){throw new Error('denied')}},'1').invalid,true);
  assert.throws(()=>selectEntry({setItem(){throw new Error('denied')}},fixture(),'9001','1','pc'));
});

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { installTenantNavigation } from '../sheep/helper/tenant-navigation-runtime.mjs';

function harness() {
  const hooks = new Map(), messages = []; let stamps = 0, registrations = 0;
  const api = { addInterceptor(method, options) { registrations++; hooks.set(method, options); }, showToast(value) { messages.push(value); } };
  const install = () => installTenantNavigation(api, () => '9001', () => stamps++);
  install();
  return { hooks, messages, install, stamps: () => stamps, registrations: () => registrations };
}
for (const method of ['navigateTo','redirectTo','reLaunch']) test(method+' adds tenant before dispatch, stamps only on success', () => {
  const h=harness(), options={url:'/pages/order/detail?id=7'};
  h.hooks.get(method).invoke(options);
  assert.equal(options.url,'/pages/order/detail?id=7&tenantId=9001');assert.equal(h.stamps(),0);
  h.hooks.get(method).success();assert.equal(h.stamps(),1);
});
test('switchTab validates tenant before stripping query and stamps committed browser URL',()=>{
  const h=harness(), options={url:'/pages/index/user?tenantId=9001'};
  h.hooks.get('switchTab').invoke(options);assert.equal(options.url,'/pages/index/user');
  h.hooks.get('switchTab').success();assert.equal(h.stamps(),1);
});
test('all navigation entry points reject wrong tenant and complete failed callbacks',()=>{
  const h=harness();
  for(const method of ['navigateTo','redirectTo','reLaunch','switchTab']) {
    let failed=0, completed=0;
    assert.equal(h.hooks.get(method).invoke({url:'/pages/index/user?tenantId=1', fail(){failed++;},complete(){completed++;}}),false);
    assert.equal(failed,1);assert.equal(completed,1);
  }
  assert.equal(h.stamps(),0);assert.equal(h.messages.length,4);
});
test('back navigation stamps without rewriting its history; installer runs once',()=>{
  const h=harness();h.install();assert.equal(h.registrations(),5);
  h.hooks.get('navigateBack').success();assert.equal(h.stamps(),1);
});

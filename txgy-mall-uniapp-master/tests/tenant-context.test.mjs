import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createTenantContext, tenantHints } from '../sheep/helper/tenant-context.mjs';

test('disabled mode keeps the configured legacy entry despite public and stored URL tenants', () => {
  const context = createTenantContext('8');
  context.configure({ enabled: false, legacyTenantId: 8, publicTenantId: 99, tenants: [{ tenantId: 99 }], capabilities: { browseSwitch: true } });
  assert.equal(context.state().browseTenantId, '8');
  assert.equal(context.state().config.capabilities.browseSwitch, false);
  assert.equal(context.validateEntry(['99']), false);
  assert.equal(context.state().browseTenantId, '8');
  assert.equal(context.validateEntry(['8']), true);
});

test('explicit invalid, hash, conflicting and nested deep links cannot fall back to legacy resources', () => {
  for (const href of ['https://mall/?tenantId=2', 'https://mall/#/pages/goods/index?id=7&tenantId=2',
    'https://mall/?tenantId=1&tenantId=2', 'https://mall/?tenant-id=abc', 'https://mall/?tenantId=',
    'https://mall/?page=%2Fpages%2Fgoods%2Findex%3Fid%3D7%26tenantId%3D2']) {
    const context = createTenantContext('1');
    assert.equal(context.validateEntry(tenantHints(href)), false, href);
    assert.throws(() => context.headers(context.capture('token-A')), /入口尚未开放/);
  }
});

test('account identity is independent, and mismatched upload/private headers are refused', () => {
  const context = createTenantContext('1'); context.setAccount(2, 7);
  const snapshot = context.capture('token-B');
  assert.equal(context.state().browseTenantId, '1');
  assert.throws(() => context.headers(snapshot), /账号不属于/);
  assert.deepEqual(context.headers(snapshot, false), { 'tenant-id': '1' });
  assert.deepEqual(context.accountHeaders(snapshot), { 'tenant-id': '2', Authorization: 'token-B' });
});

test('both successful and failed work from an old epoch are stale even if token text is reused', () => {
  const context = createTenantContext('1'); context.setAccount(1, 7);
  const snapshot = context.capture('token-A', 'refresh-A');
  context.invalidateAccount(); context.setAccount(1, 8);
  assert.equal(context.current(snapshot, 'token-A'), false);
  assert.throws(() => context.headers(snapshot), /上下文已变化/);
});

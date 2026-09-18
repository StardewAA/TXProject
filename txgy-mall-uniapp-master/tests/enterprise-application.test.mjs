import { test } from 'node:test';
import assert from 'node:assert/strict';
import { emptyEnterpriseApplication, enterpriseApplicationPayload } from '../sheep/helper/enterprise-application.mjs';
const valid = () => ({ name:' 测试企业 ', creditCode:'123456789012345678', licenseData:'data:image/png;base64,aGVsbG8=', contactName:'张三', contactMobile:'19900000000', address:'测试地址', introduction:'' });
test('enterprise payload is trimmed and excludes caller-supplied tenant, member, privilege and approval fields', () => {
  const data = enterpriseApplicationPayload({ ...valid(), tenantId:1, memberId:7, enterpriseId:9, status:1, identity:'supplier', id:99 });
  assert.equal(data.name, '测试企业'); assert.deepEqual(Object.keys(data), Object.keys(emptyEnterpriseApplication()));
});
test('required fields, malformed credit code/mobile and length limits reject before request', () => {
  for (const patch of [{name:''},{name:'a'.repeat(101)},{creditCode:'x'},{contactName:''},{contactMobile:'123'},{address:''},{introduction:'a'.repeat(1025)}]) assert.throws(() => enterpriseApplicationPayload({...valid(),...patch}));
});
test('license must be bounded image data, never an external URL or executable SVG', () => {
  for (const licenseData of ['', 'https://test/license.png', 'data:image/svg+xml;base64,aGVsbG8=', 'data:image/png;base64,'+'a'.repeat(1500000)]) assert.throws(() => enterpriseApplicationPayload({...valid(),licenseData}));
});

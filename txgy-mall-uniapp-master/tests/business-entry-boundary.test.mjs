import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const root = new URL('../', import.meta.url);
const home = readFileSync(new URL('pages/index/index.vue', root), 'utf8');
const user = readFileSync(new URL('pages/index/user.vue', root), 'utf8');
const pages = JSON.parse(readFileSync(new URL('pages.json', root), 'utf8'));

test('H5 保留普通购物、租户内公开询价、本人订单、售后和客服入口', () => {
  assert.match(home, /\/pages\/order\/list/);
  assert.match(home, /\/pages\/order\/aftersale\/list/);
  assert.match(home, /\/pages\/chat\/service/);
  assert.match(home, /\/pages\/inquiry\/index/);
  assert.doesNotMatch(home, /采购中心|销售中心/);
  assert.doesNotMatch(user, /采购中心|销售中心/);
  const inquiry = pages.subPackages.find((item) => item.root === 'pages/inquiry');
  assert.deepEqual(inquiry.pages.map((item) => item.path), ['index', 'detail']);
  assert.equal(inquiry.pages.every((item) => item.meta.auth === false), true);
});

test('H5 可保留企业资料申请，但不因此开放经营中心', () => {
  assert.match(user, /\/pages\/user\/enterprise-profile/);
  assert.match(user, /提交资料与查看审核进度/);
});

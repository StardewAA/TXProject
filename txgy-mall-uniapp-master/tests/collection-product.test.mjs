import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { collectionCanOpen, collectionUnavailable, collectionUnavailableReason } from '../sheep/helper/collection-product.mjs';

test('explicit unavailable product cannot open details or show its price card', () => {
  const item = { id: 11, spuId: 99, saleable: false, price: null, unavailableReason: '商品待审核' };
  assert.equal(collectionUnavailable(item), true);
  assert.equal(collectionCanOpen(item), false);
  assert.equal(collectionUnavailableReason(item), '商品待审核');
  assert.equal(item.spuId, 99);
});
test('old APIs without a flag keep their previous opening behaviour', () => {
  assert.equal(collectionCanOpen({ spuId: 99 }), true);
  assert.equal(collectionCanOpen({ spuId: 99, saleable: true }), true);
  assert.equal(collectionUnavailable({ spuId: 99 }), false);
});
test('invalid route and unavailable reason are handled without fabricating a price', () => {
  assert.equal(collectionCanOpen(null), false);
  assert.equal(collectionCanOpen({}), false);
  assert.equal(collectionUnavailableReason({ saleable: false }), '商品已下架、待审核或不可购买，可编辑删除此记录');
});
for (const file of ['goods-collect.vue', 'goods-log.vue']) {
  test(`${file} uses page-local unavailable placeholder and retains delete selection`, async () => {
    const source = await readFile(new URL(`../pages/user/${file}`, import.meta.url), 'utf8');
    assert.match(source, /v-if="collectionUnavailable\(item\)"/);
    assert.match(source, /<s-goods-item\s+v-else/);
    assert.match(source, /!collectionCanOpen\(item\)/);
    assert.match(source, /@tap="onSelect\(item\.spuId\)"/);
  });
}

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { test } from 'node:test';
import { runInNewContext } from 'node:vm';

const require = createRequire(new URL('../packages/@core/ui-kit/form-ui/package.json', import.meta.url));
const { validate } = require('vee-validate');
const source = readFileSync(new URL('../apps/web-antd/src/views/wms/md/item/data.ts', import.meta.url), 'utf8');
// Exercise the actual select configuration with the same validation engine used by the form.
const match = source.match(/\{\s*fieldName: 'materialType',[\s\S]*?\n    \},/);
assert.ok(match, '商品表单必须包含物料类型');
const field = runInNewContext(`(${match[0].slice(0, -1)})`);

test('商品展开为 SKU 行后保留主数据物料类型', () => {
  const page = readFileSync(new URL('../apps/web-antd/src/views/wms/md/item/index.vue', import.meta.url), 'utf8');
  const fn = page.match(/function buildItemSkuRows\(items: WmsItemApi.Item\[\]\) \{[\s\S]*?\n\}/);
  assert.ok(fn);
  const buildRows = runInNewContext(`(${fn[0].replace('items: WmsItemApi.Item[]', 'items')})`, { CommonStatusEnum: { ENABLE: 0 } });
  for (const materialType of ['RAW_MATERIAL', 'FINISHED_GOODS']) {
    const rows = buildRows([{ id: 1, materialType, skus: [{ id: 11 }, { id: 12 }] }]);
    assert.equal(rows.length, 2);
    assert.ok(rows.every(row => row.materialType === materialType));
  }
});

for (const value of ['RAW_MATERIAL', 'FINISHED_GOODS', undefined, null]) {
  test(`物料类型 ${String(value)} 不应中断商品保存校验`, async () => {
    const result = await validate(value, field.rules || {});
    assert.equal(result.valid, true);
  });
}

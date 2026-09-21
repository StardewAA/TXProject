import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

const source = readFileSync(
  new URL('../apps/web-antd/src/views/wms/md/warehouse/data.ts', import.meta.url),
  'utf8',
);

test('仓库员工选项显示用户编号和名称，并使用启用用户接口结果', () => {
  assert.match(source, /\$\{user\.id\} · \$\{user\.nickname/);
  assert.doesNotMatch(source, /deptName === '仓储物流部'/);
  assert.doesNotMatch(source, /user\.status === 0 && logisticsDeptIds/);
  assert.match(source, /optionFilterProp: 'label'/);
});

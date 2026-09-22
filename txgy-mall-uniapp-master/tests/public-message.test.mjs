import assert from 'node:assert/strict';
import test from 'node:test';
import { publicApiMessage } from '../sheep/helper/public-message.mjs';

test('hides internal ERP rollout details from mobile users', () => {
  assert.equal(publicApiMessage('ERP 暂未对接，请联系管理员'), '相关服务暂不可用，请稍后重试');
  assert.equal(publicApiMessage('当前erp尚未配置'), '相关服务暂不可用，请稍后重试');
  assert.equal(publicApiMessage('真实 ERP 等系统对接暂未启用'), '相关服务暂不可用，请稍后重试');
});

test('keeps ordinary actionable business messages', () => {
  assert.equal(publicApiMessage('库存不足，请调整购买数量'), '库存不足，请调整购买数量');
  assert.equal(publicApiMessage('', '操作失败'), '操作失败');
});

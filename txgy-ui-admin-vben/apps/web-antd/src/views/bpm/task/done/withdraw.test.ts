import { describe, expect, it } from 'vitest';

import { BpmProcessInstanceStatus } from '@vben/constants';

import { canShowWithdrawTask } from './withdraw';

describe('已办任务撤回按钮', () => {
  it('流程运行中才显示撤回', () => {
    expect(canShowWithdrawTask({ processInstance: { status: BpmProcessInstanceStatus.RUNNING } })).toBe(true);
  });

  it('业务入口禁用撤回时始终隐藏', () => {
    expect(canShowWithdrawTask(
      { processInstance: { status: BpmProcessInstanceStatus.RUNNING } },
      false,
    )).toBe(false);
  });

  it.each([
    BpmProcessInstanceStatus.APPROVE,
    BpmProcessInstanceStatus.REJECT,
    BpmProcessInstanceStatus.CANCEL,
  ])('流程已结束时隐藏撤回（状态 %s）', (status) => {
    expect(canShowWithdrawTask({ processInstance: { status } })).toBe(false);
  });
});

import { BpmProcessInstanceStatus } from '@vben/constants';

type DoneTask = { processInstance?: { status?: number } };

export function canShowWithdrawTask(
  task: DoneTask,
  withdrawEnabled = true,
): boolean {
  return withdrawEnabled
    && task.processInstance?.status === BpmProcessInstanceStatus.RUNNING;
}

import type { PageParam, PageResult } from '@vben/request';

import type { BpmProcessInstanceApi } from '../processInstance';

import { requestClient } from '#/api/request';

export namespace BpmTaskApi {
  /** 流程任务 */
  export interface Task {
    id: string; // 编号
    name: string; // 任务名字
    status: number; // 任务状态
    createTime: number; // 创建时间
    endTime: number; // 结束时间
    durationInMillis: number; // 持续时间
    reason: string; // 审批理由
    attachments?: string[]; // 审批附件
    signPicUrl?: string; // 签名图片
    ownerUser: any; // 负责人
    assigneeUser: any; // 处理人
    taskDefinitionKey: string; // 任务定义的标识
    processInstanceId: string; // 流程实例id
    processInstance: BpmProcessInstanceApi.ProcessInstance; // 流程实例
    parentTaskId: any; // 父任务id
    children: any; // 子任务
    formId: any; // 表单id
    formName: any; // 表单名称
    formConf: any; // 表单配置
    formFields: any; // 表单字段
    formVariables: any; // 表单变量
    buttonsSetting: any; // 按钮设置
    signEnable: any; // 签名设置
    reasonRequire: any; // 原因设置
    nodeType: any; // 节点类型
  }
}

/** 查询待办任务分页 */
export async function getTaskTodoPage(params: PageParam) {
  return requestClient.get<PageResult<BpmTaskApi.Task>>('/bpm/task/todo-page', {
    params,
  });
}

/** 查询已办任务分页 */
export async function getTaskDonePage(params: PageParam) {
  return requestClient.get<PageResult<BpmTaskApi.Task>>('/bpm/task/done-page', {
    params,
  });
}

/** 查询任务管理分页 */
export async function getTaskManagerPage(params: PageParam) {
  return requestClient.get<PageResult<BpmTaskApi.Task>>(
    '/bpm/task/manager-page',
    { params },
  );
}

/** 审批任务 */
export const approveTask = async (data: any) => {
  return await requestClient.put('/bpm/task/approve', data);
};

/** 驳回任务 */
export const rejectTask = async (data: any) => {
  return await requestClient.put('/bpm/task/reject', data);
};

/** 根据流程实例 ID 查询任务列表 */
export const getTaskListByProcessInstanceId = async (id: string) => {
  return await requestClient.get(
    `/bpm/task/list-by-process-instance-id?processInstanceId=${id}`,
  );
};

/** 获取所有可退回的节点 */
export const getTaskListByReturn = async (id: string) => {
  return await requestClient.get(`/bpm/task/list-by-return?id=${id}`);
};

/** 退回任务 */
export const returnTask = async (data: any) => {
  return await requestClient.put('/bpm/task/return', data);
};

/** 委派任务 */
export const delegateTask = async (data: any) => {
  return await requestClient.put('/bpm/task/delegate', data);
};

export const getDelegateCandidates = async (taskId: string): Promise<number[]> => {
  return await requestClient.get<number[]>('/bpm/task/delegate-candidates', { params: { taskId } });
};

/** 转派任务 */
export const transferTask = async (data: any) => {
  return await requestClient.put('/bpm/task/transfer', data);
};

/** 加签任务 */
export const signCreateTask = async (data: any) => {
  return await requestClient.put('/bpm/task/create-sign', data);
};

/** 减签任务 */
export const signDeleteTask = async (data: any) => {
  return await requestClient.delete('/bpm/task/delete-sign', data);
};

/** 抄送任务 */
export const copyTask = async (data: any) => {
  return await requestClient.put('/bpm/task/copy', data);
};

/** 获取加签任务列表 */
export const getChildrenTaskList = async (id: string) => {
  return await requestClient.get(
    `/bpm/task/list-by-parent-task-id?parentTaskId=${id}`,
  );
};

/** 撤回任务 */
export const withdrawTask = async (taskId: string) => {
  return await requestClient.put('/bpm/task/withdraw', null, {
    params: { taskId },
  });
};

/**
 * ERP 审批页「认领任务」。
 *
 * 说明（2026-09-20 合并远程 master 时补）：
 * 远程 master 的 views/erp/approval/index.vue 引用了本导出，但 api 文件里从未实现，
 * 后端 BpmTaskController 也没有提供任何 claim / 认领接口，导致整个前端构建报
 * `MISSING_EXPORT: "claimErpApprovalTask" is not exported by src/api/bpm/task/index.ts` 而失败。
 * 该导出在 origin 的所有分支（含 ERP 分支）里同样不存在，属远程自身的构建缺陷。
 *
 * 这里按后端实际能力实现：yudao 的 BPM 待办任务没有「先认领再办理」这一步，
 * 待办列表拿到的任务直接可审批；ERP 审批页在该调用之后也是直接打开流程详情页办理，
 * 因此这里不发起任何无对应后端的请求，仅保留调用点与返回结构。
 */
export async function claimErpApprovalTask(taskId: string | number) {
  return Promise.resolve({ taskId });
}

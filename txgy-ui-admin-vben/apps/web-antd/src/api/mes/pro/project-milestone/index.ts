import { requestClient } from '#/api/request';

export namespace MesProProjectMilestoneApi {
  /** MES 项目里程碑 */
  export interface Milestone {
    id?: number; // 编号
    projectId?: number; // 项目编号
    name?: string; // 里程碑名称
    planDate?: string; // 计划完成日期
    actualDate?: string; // 实际完成日期
    status?: number; // 里程碑状态：0未开始 1进行中 2已完成
    sort?: number; // 排序
    remark?: string; // 备注
  }
}

/** 查询项目里程碑列表 */
export function getMilestoneList(projectId: number) {
  return requestClient.get<MesProProjectMilestoneApi.Milestone[]>(
    `/mes/pro-project-milestone/list?projectId=${projectId}`,
  );
}

/** 新增项目里程碑 */
export function createMilestone(
  data: MesProProjectMilestoneApi.Milestone,
) {
  return requestClient.post<number>('/mes/pro-project-milestone/create', data);
}

/** 修改项目里程碑 */
export function updateMilestone(
  data: MesProProjectMilestoneApi.Milestone,
) {
  return requestClient.put('/mes/pro-project-milestone/update', data);
}

/** 删除项目里程碑 */
export function deleteMilestone(id: number) {
  return requestClient.delete(`/mes/pro-project-milestone/delete?id=${id}`);
}

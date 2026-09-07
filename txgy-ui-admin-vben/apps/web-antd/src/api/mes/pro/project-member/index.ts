import { requestClient } from '#/api/request';

export namespace MesProProjectMemberApi {
  /** MES 项目成员 */
  export interface ProjectMember {
    id?: number; // 编号
    projectId?: number; // 项目编号
    userId?: number; // 成员用户编号
    userName?: string; // 成员姓名
    role?: number; // 项目角色
    remark?: string; // 备注
  }
}

/** 查询项目成员列表 */
export function getProjectMemberList(projectId: number) {
  return requestClient.get<MesProProjectMemberApi.ProjectMember[]>(
    `/mes/pro-project-member/list?projectId=${projectId}`,
  );
}

/** 新增项目成员 */
export function createProjectMember(
  data: MesProProjectMemberApi.ProjectMember,
) {
  return requestClient.post<number>('/mes/pro-project-member/create', data);
}

/** 修改项目成员 */
export function updateProjectMember(
  data: MesProProjectMemberApi.ProjectMember,
) {
  return requestClient.put('/mes/pro-project-member/update', data);
}

/** 删除项目成员 */
export function deleteProjectMember(id: number) {
  return requestClient.delete(`/mes/pro-project-member/delete?id=${id}`);
}

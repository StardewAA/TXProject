import type { HrmEmployeeWorkExperienceApi } from '#/api/hrm/employee/work-experience';

import { requestClient } from '#/api/request';

/** 获得当前员工的工作经历列表 */
export function getEmployeeWorkExperienceList() {
  return requestClient.get<
    HrmEmployeeWorkExperienceApi.EmployeeWorkExperience[]
  >('/hrm/portal/employee/work-experience/list');
}

/** 新增我的工作经历（员工自助完善档案，保存后 HR 后台立即可见） */
export function createMyWorkExperience(
  data: HrmEmployeeWorkExperienceApi.EmployeeWorkExperience,
) {
  return requestClient.post<number>(
    '/hrm/portal/employee/work-experience/create',
    data,
  );
}

/** 修改我的工作经历（仅限本人记录） */
export function updateMyWorkExperience(
  data: HrmEmployeeWorkExperienceApi.EmployeeWorkExperience,
) {
  return requestClient.put<boolean>(
    '/hrm/portal/employee/work-experience/update',
    data,
  );
}

/** 删除我的工作经历（仅限本人记录） */
export function deleteMyWorkExperience(id: number) {
  return requestClient.delete<boolean>(
    '/hrm/portal/employee/work-experience/delete',
    { params: { id } },
  );
}

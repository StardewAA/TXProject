import type { HrmEmployeeEducationExperienceApi } from '#/api/hrm/employee/education-experience';

import { requestClient } from '#/api/request';

/** 获得当前员工的教育经历列表 */
export function getEmployeeEducationExperienceList() {
  return requestClient.get<
    HrmEmployeeEducationExperienceApi.EmployeeEducationExperience[]
  >('/hrm/portal/employee/education-experience/list');
}

/**
 * 新增我的教育经历（员工自助完善档案）。
 *
 * 保存后写的就是后台同一条员工记录，HR 立即可见，并会收到复核提醒。
 */
export function createMyEducationExperience(
  data: HrmEmployeeEducationExperienceApi.EmployeeEducationExperience,
) {
  return requestClient.post<number>(
    '/hrm/portal/employee/education-experience/create',
    data,
  );
}

/** 修改我的教育经历（仅限本人记录） */
export function updateMyEducationExperience(
  data: HrmEmployeeEducationExperienceApi.EmployeeEducationExperience,
) {
  return requestClient.put<boolean>(
    '/hrm/portal/employee/education-experience/update',
    data,
  );
}

/** 删除我的教育经历（仅限本人记录） */
export function deleteMyEducationExperience(id: number) {
  return requestClient.delete<boolean>(
    '/hrm/portal/employee/education-experience/delete',
    { params: { id } },
  );
}

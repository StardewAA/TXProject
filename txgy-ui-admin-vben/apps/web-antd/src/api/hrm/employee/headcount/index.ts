import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace HrmDeptHeadcountApi {
  /** 部门编制 */
  export interface DeptHeadcount {
    id?: number;
    deptId?: number;
    deptName?: string;
    headcount?: number;
    onboardCount?: number;
    gapCount?: number;
    overStaffed?: boolean;
    remark?: string;
    createTime?: string;
  }
}

/** 部门编制分页 */
export function getDeptHeadcountPage(
  params: PageParam & { deptId?: number },
) {
  return requestClient.get<PageResult<HrmDeptHeadcountApi.DeptHeadcount>>(
    '/hrm/employee/headcount/page',
    { params },
  );
}

export function getDeptHeadcount(id: number) {
  return requestClient.get<HrmDeptHeadcountApi.DeptHeadcount>(
    '/hrm/employee/headcount/get',
    { params: { id } },
  );
}

export function createDeptHeadcount(data: HrmDeptHeadcountApi.DeptHeadcount) {
  return requestClient.post<number>('/hrm/employee/headcount/create', data);
}

export function updateDeptHeadcount(data: HrmDeptHeadcountApi.DeptHeadcount) {
  return requestClient.put<boolean>('/hrm/employee/headcount/update', data);
}

export function deleteDeptHeadcount(id: number) {
  return requestClient.delete<boolean>('/hrm/employee/headcount/delete', {
    params: { id },
  });
}

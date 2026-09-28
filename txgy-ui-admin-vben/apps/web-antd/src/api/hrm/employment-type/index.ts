import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

/** 计薪方式枚举（对齐后端 HrmSalaryModeEnum） */
export const HrmSalaryMode = {
  MONTHLY: 1, // 月薪
  DAILY: 2, // 日薪
  PIECE: 3, // 计件
  MIXED: 4, // 月薪+计件
} as const;

/** 计薪方式选项 */
export const HrmSalaryModeOptions = [
  { label: '月薪', value: HrmSalaryMode.MONTHLY },
  { label: '日薪', value: HrmSalaryMode.DAILY },
  { label: '计件', value: HrmSalaryMode.PIECE },
  { label: '月薪+计件', value: HrmSalaryMode.MIXED },
] as const;

export namespace HrmEmploymentTypeApi {
  /** 用工形式 */
  export interface EmploymentType {
    id?: number; // 编号
    name?: string; // 用工形式名称
    code?: string; // 用工形式编码
    contractTemplateId?: number; // 默认合同模板编号
    contractTemplateName?: string; // 默认合同模板名称
    salaryMode?: number; // 默认计薪方式：1 月薪，2 日薪，3 计件，4 月薪+计件
    needWorkDay?: boolean; // 是否按工日统计出勤
    needFaceClock?: boolean; // 是否要求人脸打卡
    joinRegular?: boolean; // 是否参与自动转正
    sort?: number; // 显示顺序
    status?: number; // 状态：0 启用，1 停用
    remark?: string; // 备注
    createTime?: Date; // 创建时间
  }

  /** 分页查询参数 */
  export interface EmploymentTypePageParams extends PageParam {
    name?: string;
    code?: string;
    salaryMode?: number;
    status?: number;
  }
}

/** 查询用工形式分页 */
export function getEmploymentTypePage(
  params: HrmEmploymentTypeApi.EmploymentTypePageParams,
) {
  return requestClient.get<PageResult<HrmEmploymentTypeApi.EmploymentType>>(
    '/hrm/employment-type/page',
    { params },
  );
}

/** 查询用工形式列表（含停用） */
export function getEmploymentTypeList() {
  return requestClient.get<HrmEmploymentTypeApi.EmploymentType[]>(
    '/hrm/employment-type/list',
  );
}

/** 查询启用中的用工形式列表（下拉用） */
export function getEnabledEmploymentTypeList() {
  return requestClient.get<HrmEmploymentTypeApi.EmploymentType[]>(
    '/hrm/employment-type/simple-list',
  );
}

/** 查询用工形式详情 */
export function getEmploymentType(id: number) {
  return requestClient.get<HrmEmploymentTypeApi.EmploymentType>(
    '/hrm/employment-type/get',
    { params: { id } },
  );
}

/** 新增用工形式 */
export function createEmploymentType(
  data: HrmEmploymentTypeApi.EmploymentType,
) {
  return requestClient.post<number>('/hrm/employment-type/create', data);
}

/** 修改用工形式 */
export function updateEmploymentType(
  data: HrmEmploymentTypeApi.EmploymentType,
) {
  return requestClient.put<boolean>('/hrm/employment-type/update', data);
}

/** 删除用工形式 */
export function deleteEmploymentType(id: number) {
  return requestClient.delete<boolean>('/hrm/employment-type/delete', {
    params: { id },
  });
}

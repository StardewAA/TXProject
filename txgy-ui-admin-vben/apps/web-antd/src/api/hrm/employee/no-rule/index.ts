import { requestClient } from '#/api/request';

export namespace HrmEmployeeNoRuleApi {
  /** 工号规则 */
  export interface NoRule {
    id?: number; // 规则编号
    name?: string; // 规则名称
    tenantAbbr?: string; // 租户缩写（工号前缀）
    serialLength?: number; // 顺序号位数（左补零）
    prefixSeparator?: string; // 分隔符
    resetPeriod?: number; // 重置周期：0 不重置，1 按年重置
    allowManual?: boolean; // 是否允许人工指定工号
    status?: number; // 状态：0 启用，1 停用
    remark?: string; // 备注
    sampleJobNumber?: string; // 工号样例
    createTime?: Date; // 创建时间
  }
}

/** 查询工号规则列表 */
export function getNoRuleList() {
  return requestClient.get<HrmEmployeeNoRuleApi.NoRule[]>(
    '/hrm/employee/no-rule/list',
  );
}

/** 查询工号规则 */
export function getNoRule(id: number) {
  return requestClient.get<HrmEmployeeNoRuleApi.NoRule>(
    '/hrm/employee/no-rule/get',
    { params: { id } },
  );
}

/** 保存工号规则（无编号新增、有编号修改） */
export function saveNoRule(data: HrmEmployeeNoRuleApi.NoRule) {
  return requestClient.post<number>('/hrm/employee/no-rule/save', data);
}

/** 删除工号规则 */
export function deleteNoRule(id: number) {
  return requestClient.delete<boolean>('/hrm/employee/no-rule/delete', {
    params: { id },
  });
}

/** 预览下一个工号 */
export function previewNextJobNumber() {
  return requestClient.get<string>('/hrm/employee/no-rule/preview');
}

/** 按给定配置预览工号样例 */
export function previewSampleJobNumber(data: HrmEmployeeNoRuleApi.NoRule) {
  return requestClient.post<string>(
    '/hrm/employee/no-rule/preview-sample',
    data,
  );
}

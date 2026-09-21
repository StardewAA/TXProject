import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace HrmSalaryAttendanceRuleApi {
  /** 考勤计薪规则 */
  export interface AttendanceRule {
    id?: number;
    name?: string;
    fullAttendanceDays?: number;
    dailyWageMode?: number;
    absenceDeductRate?: number;
    fullAttendanceBonusRate?: number;
    personalLeaveRate?: number;
    sickLeaveRate?: number;
    lateToAbsenceTimes?: number;
    defaultFlag?: boolean;
    status?: number;
    remark?: string;
  }

  /** 计薪试算结果 */
  export interface CalcResult {
    ruleId?: number;
    ruleName?: string;
    fullAttendanceDays?: number;
    dailyWage?: number;
    absentDaysTotal?: number;
    absenceDeductAmount?: number;
    personalLeaveDeductAmount?: number;
    sickLeaveDeductAmount?: number;
    totalDeductAmount?: number;
    fullAttendanceBonusAmount?: number;
    fullAttendance?: boolean;
    payableSalary?: number;
    remark?: string;
  }
}

export function getAttendanceRulePage(
  params: PageParam & { name?: string; status?: number },
) {
  return requestClient.get<PageResult<HrmSalaryAttendanceRuleApi.AttendanceRule>>(
    '/hrm/salary/attendance-rule/page',
    { params },
  );
}

export function getAttendanceRule(id: number) {
  return requestClient.get<HrmSalaryAttendanceRuleApi.AttendanceRule>(
    '/hrm/salary/attendance-rule/get',
    { params: { id } },
  );
}

export function getDefaultAttendanceRule() {
  return requestClient.get<HrmSalaryAttendanceRuleApi.AttendanceRule>(
    '/hrm/salary/attendance-rule/default',
  );
}

export function createAttendanceRule(
  data: HrmSalaryAttendanceRuleApi.AttendanceRule,
) {
  return requestClient.post<number>('/hrm/salary/attendance-rule/create', data);
}

export function updateAttendanceRule(
  data: HrmSalaryAttendanceRuleApi.AttendanceRule,
) {
  return requestClient.put<boolean>('/hrm/salary/attendance-rule/update', data);
}

export function deleteAttendanceRule(id: number) {
  return requestClient.delete<boolean>('/hrm/salary/attendance-rule/delete', {
    params: { id },
  });
}

/** 计薪试算：把出勤/缺勤/请假折算成扣款与满勤奖 */
export function calcAttendanceSalary(data: {
  ruleId?: number;
  baseSalary: number;
  absentDays?: number;
  attendanceDays?: number;
  lateTimes?: number;
  personalLeaveDays?: number;
  sickLeaveDays?: number;
}) {
  return requestClient.post<HrmSalaryAttendanceRuleApi.CalcResult>(
    '/hrm/salary/attendance-rule/calc',
    data,
  );
}

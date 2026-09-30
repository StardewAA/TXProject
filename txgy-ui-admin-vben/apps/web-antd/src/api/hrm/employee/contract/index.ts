import { requestClient } from '#/api/request';
import { useAccessStore } from '@vben/stores';

export namespace HrmEmployeeContractApi {
  /** 员工合同 */
  export interface EmployeeContract {
    id?: number; // 合同编号
    employeeId?: number; // 员工编号
    no?: string; // 合同编码
    type?: number; // 合同类型
    startTime?: number; // 合同开始日期
    endTime?: number; // 合同结束日期
    term?: number; // 合同期限
    status?: number; // 合同状态
    signCompany?: string; // 签约公司
    signTime?: number; // 合同签订日期
    signStatus?: number; // 签署状态：0 待签，1 已签，2 已作废
    signStatusName?: string; // 签署状态名称
    remark?: string; // 备注
    expireRemind?: boolean; // 是否到期提醒
    fileUrls?: string[]; // 附件地址数组
    sort?: number; // 排序
    templateId?: number; // 合同模板编号
    sourceOfferId?: number; // 来源 Offer 编号
    workContent?: string; // 工作内容
    workLocation?: string; // 工作地点
    workTask?: string; // 工作任务
    payMode?: number; // 计薪方式：1 月薪，2 日薪，3 计件
    payAmount?: number; // 计薪金额/单价
    payUnit?: string; // 计薪单位
    payDay?: number; // 每月发薪日
    commercialInsurance?: string; // 商业保险说明
    attendanceMode?: string; // 考勤方式
    createTime?: Date; // 创建时间
  }
}

/** 查询员工合同列表 */
export function getEmployeeContractList(employeeId: number) {
  return requestClient.get<HrmEmployeeContractApi.EmployeeContract[]>(
    '/hrm/employee/contract/list',
    { params: { employeeId } },
  );
}

/** 新增员工合同 */
export function createEmployeeContract(
  data: HrmEmployeeContractApi.EmployeeContract,
) {
  return requestClient.post<number>('/hrm/employee/contract/create', data);
}

/** 修改员工合同 */
export function updateEmployeeContract(
  data: HrmEmployeeContractApi.EmployeeContract,
) {
  return requestClient.put<boolean>('/hrm/employee/contract/update', data);
}

/** 删除员工合同 */
export function deleteEmployeeContract(id: number) {
  return requestClient.delete<boolean>('/hrm/employee/contract/delete', {
    params: { id },
  });
}

/** 导出合同文件（按模板自动带入公司信息与员工信息） */
export function exportEmployeeContract(id: number) {
  return requestClient.download('/hrm/employee/contract/export', {
    params: { id },
  });
}

/**
 * 在线预览合同文件（返回二进制流）
 *
 * 注意：不能直接用 `window.open(url)` —— 新窗口不会携带 Authorization 头，后端会返回 401「账号未登录」。
 * 必须走 requestClient（会自动带 token），拿到 Blob 后再用 URL.createObjectURL 打开预览。
 */
export function previewEmployeeContract(id: number) {
  // 说明：合同预览返回的是二进制流，不能用 requestClient 的任何方法（含底层 instance）——
  // 全局响应拦截器会把响应体当 JSON 解析并抛业务错误对象，前端就表现为「[object Object]」。
  // 这里直接用 fetch 取流，并手动带上与请求拦截器一致的鉴权头与租户头。
  const accessStore = useAccessStore();
  const base = (import.meta.env.VITE_GLOB_API_URL as string) || '/admin-api';
  const tenantId = String(
    accessStore.tenantId ?? Number(import.meta.env.VITE_APP_DEFAULT_TENANT_ID || 1),
  );
  const url = `${base}/hrm/employee/contract/preview?id=${id}`;
  return fetch(url, {
    headers: {
      Authorization: `Bearer ${accessStore.accessToken ?? ''}`,
      'tenant-id': tenantId,
      'visit-tenant-id': tenantId,
    },
    method: 'GET',
  }).then(async (res) => {
    if (!res.ok) {
      throw new Error(`HTTP ${res.status} ${res.statusText}`);
    }
    return res.blob();
  });
}

/** 合同文件台账条目（生成稿 / 签署件） */
export interface HrmEmployeeContractFile {
  id?: number;
  contractId?: number;
  employeeId?: number;
  fileType?: number;
  version?: number;
  fileName?: string;
  fileUrl?: string;
  templateId?: number;
  signRecordId?: number;
  createTime?: string;
}

/** 生成待签合同：按「员工姓名-合同类型.docx」渲染归档，并自动登记待签签署记录 */
export function generatePendingEmployeeContract(id: number) {
  return requestClient.post<number>('/hrm/employee/contract/generate', null, {
    params: { id },
  });
}

/**
 * 合同续签：按原合同生成新周期合同 + 待签文件 + 待签签署记录
 *
 * @param templateId 可指定续签使用的合同模板（不传则沿用原合同模板）
 * @param termYears  续签年限（不传则沿用原合同期限）
 */
export function renewEmployeeContract(
  id: number,
  termYears?: number,
  templateId?: number,
) {
  return requestClient.post<number>('/hrm/employee/contract/renew', null, {
    params: { id, termYears, templateId },
  });
}

/** 合同文件台账（系统生成稿 / 签署件） */
export function getEmployeeContractFileList(contractId: number) {
  return requestClient.get<HrmEmployeeContractFile[]>(
    '/hrm/employee/contract/file/list',
    { params: { contractId } },
  );
}

/** 合同看板：待签署 / 已签署 / 30 天内到期 */
export function getContractStatisticsSummary() {
  return requestClient.get<{
    expireIn30DaysCount: number;
    pendingSignCount: number;
    signedCount: number;
  }>('/hrm/employee/contract/statistics/summary');
}

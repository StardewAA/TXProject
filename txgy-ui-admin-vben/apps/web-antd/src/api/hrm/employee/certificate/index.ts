import { requestClient } from '#/api/request';

export namespace HrmEmployeeCertificateApi {
  /** 员工证书 */
  export interface EmployeeCertificate {
    id?: number; // 证书编号
    employeeId?: number; // 员工编号
    typeId?: number; // 证照类型编号
    typeName?: string; // 证照类型名称
    specialOperation?: boolean; // 是否特种作业
    mandatory?: boolean; // 是否必持
    name?: string; // 证书名称
    level?: string; // 证书级别
    no?: string; // 证书编码
    startTime?: number; // 有效开始日期
    endTime?: number; // 有效结束日期
    reviewDate?: string; // 复审日期
    fileUrls?: string[]; // 证件扫描件
    verifyStatus?: number; // 核验状态
    verifyStatusName?: string; // 核验状态名称
    verifyTime?: Date; // 核验时间
    expireStatus?: number; // 到期状态
    expireStatusName?: string; // 到期状态名称
    remainDays?: number; // 距到期天数
    issuingAuthority?: string; // 发证机构
    issuingTime?: number; // 发证日期
    remark?: string; // 备注
    sort?: number; // 排序
    createTime?: Date; // 创建时间
  }
}

/** 查询员工证书列表 */
export function getEmployeeCertificateList(employeeId: number) {
  return requestClient.get<HrmEmployeeCertificateApi.EmployeeCertificate[]>(
    '/hrm/employee/certificate/list',
    { params: { employeeId } },
  );
}

/** 新增员工证书 */
export function createEmployeeCertificate(
  data: HrmEmployeeCertificateApi.EmployeeCertificate,
) {
  return requestClient.post<number>('/hrm/employee/certificate/create', data);
}

/** 修改员工证书 */
export function updateEmployeeCertificate(
  data: HrmEmployeeCertificateApi.EmployeeCertificate,
) {
  return requestClient.put<boolean>('/hrm/employee/certificate/update', data);
}

/** 删除员工证书 */
export function deleteEmployeeCertificate(id: number) {
  return requestClient.delete<boolean>('/hrm/employee/certificate/delete', {
    params: { id },
  });
}

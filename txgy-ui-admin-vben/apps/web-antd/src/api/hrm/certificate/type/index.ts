import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace HrmCertificateTypeApi {
  /** 证件类型 */
  export interface CertificateType {
    id?: number; // 证件类型编号
    name?: string; // 证件类型名称
    code?: string; // 证件类型编码
    specialOperation?: boolean; // 是否特种作业
    needReview?: boolean; // 是否需要复审
    reviewCycleMonths?: number; // 复审周期（月）
    advanceDays?: number; // 默认提前预警天数
    requireAttachment?: boolean; // 是否必须上传证件附件
    status?: number; // 状态，参见 common_status
    sort?: number; // 显示顺序
    remark?: string; // 备注
    createTime?: Date; // 创建时间
  }
}

/** 查询证件类型分页 */
export function getCertificateTypePage(params: PageParam) {
  return requestClient.get<PageResult<HrmCertificateTypeApi.CertificateType>>(
    '/hrm/certificate-type/page',
    { params },
  );
}

/** 查询证件类型列表 */
export function getCertificateTypeList(status?: number) {
  return requestClient.get<HrmCertificateTypeApi.CertificateType[]>(
    '/hrm/certificate-type/list',
    { params: { status } },
  );
}

/** 查询证件类型详情 */
export function getCertificateType(id: number) {
  return requestClient.get<HrmCertificateTypeApi.CertificateType>(
    `/hrm/certificate-type/get?id=${id}`,
  );
}

/** 新增证件类型 */
export function createCertificateType(
  data: HrmCertificateTypeApi.CertificateType,
) {
  return requestClient.post<number>('/hrm/certificate-type/create', data);
}

/** 修改证件类型 */
export function updateCertificateType(
  data: HrmCertificateTypeApi.CertificateType,
) {
  return requestClient.put<boolean>('/hrm/certificate-type/update', data);
}

/** 删除证件类型 */
export function deleteCertificateType(id: number) {
  return requestClient.delete<boolean>('/hrm/certificate-type/delete', {
    params: { id },
  });
}

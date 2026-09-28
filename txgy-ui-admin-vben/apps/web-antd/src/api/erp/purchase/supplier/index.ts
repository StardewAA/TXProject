import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace ErpSupplierApi {
  /** 供应商信息 */
  export interface Supplier {
    id?: number; // 供应商编号
    name: string; // 供应商名称
    userId?: number; // 绑定的登录用户编号（供应商门户账号）
    contact: string; // 联系人
    mobile: string; // 手机号码
    telephone: string; // 联系电话
    email: string; // 电子邮箱
    fax: string; // 传真
    remark: string; // 备注
    status: number; // 开启状态
    sort: number; // 排序
    taxNo: string; // 纳税人识别号
    taxPercent: number; // 税率
    bankName: string; // 开户行
    bankAccount: string; // 开户账号
    bankAddress: string; // 开户地址
    authStatus?: string;
    qualificationStatus?: string;
    riskLevel?: string;
    purchaseLimitStatus?: string;
  }

  export interface SupplierCredit {
    supplierId: number;
    authStatus: string;
    qualificationStatus: string;
    riskLevel: string;
    purchaseLimitStatus: string;
    freezeReason?: string;
    snapshotVersion?: number;
    lastSyncTime?: string;
    qualifications: SupplierQualification[];
    admissionCategories: SupplierAdmissionCategory[];
    riskEvents: SupplierRiskEvent[];
  }

  export type SupplierCreditStatus = Pick<
    SupplierCredit,
    | 'supplierId'
    | 'snapshotVersion'
    | 'authStatus'
    | 'qualificationStatus'
    | 'riskLevel'
    | 'purchaseLimitStatus'
    | 'freezeReason'
  >;

  export interface SupplierRiskEventCreate {
    supplierId: number;
    eventType: string;
    severity?: number;
    blocksPurchase?: boolean;
    occurredAt?: string;
    description?: string;
  }

  export interface SupplierQualification {
    id?: number;
    key?: string;
    qualificationName: string;
    qualificationNo?: string;
    validFrom?: string;
    validTo?: string;
    status: string;
    requiredFlag?: boolean;
    remark?: string;
  }

  export interface SupplierAdmissionCategory {
    id?: number;
    key?: string;
    categoryId?: number;
    categoryName: string;
    validFrom?: string;
    validTo?: string;
    status: string;
    remark?: string;
  }

  export interface SupplierAdmissionCategorySave {
    supplierId: number;
    items: Omit<SupplierAdmissionCategory, 'id' | 'key'>[];
  }

  export interface SupplierQualificationSave {
    supplierId: number;
    items: Omit<SupplierQualification, 'id' | 'key'>[];
  }

  export interface SupplierRiskEvent {
    id?: number;
    eventType: string;
    status: string;
    severity?: number;
    blocksPurchase?: boolean;
    occurredAt?: string;
    resolvedAt?: string;
    description?: string;
    resolution?: string;
  }
}

/** 查询供应商分页 */
export function getSupplierPage(params: PageParam) {
  return requestClient.get<PageResult<ErpSupplierApi.Supplier>>(
    '/erp/supplier/page',
    { params },
  );
}

/** 获得供应商精简列表 */
export function getSupplierSimpleList() {
  return requestClient.get<ErpSupplierApi.Supplier[]>(
    '/erp/supplier/simple-list',
  );
}

/** 查询供应商详情 */
export function getSupplier(id: number) {
  return requestClient.get<ErpSupplierApi.Supplier>(
    `/erp/supplier/get?id=${id}`,
  );
}

/** 查询供应商资信详情 */
export function getSupplierCredit(supplierId: number) {
  return requestClient.get<ErpSupplierApi.SupplierCredit>(
    `/erp/supplier/credit/get?supplierId=${supplierId}`,
  );
}

/** 更新供应商资信状态 */
export function updateSupplierCreditStatus(
  data: ErpSupplierApi.SupplierCreditStatus,
) {
  return requestClient.post('/erp/supplier/credit/status', data);
}

/** 更新供应商准入品类 */
export function replaceSupplierAdmissionCategories(
  data: ErpSupplierApi.SupplierAdmissionCategorySave,
) {
  return requestClient.post('/erp/supplier/credit/admission-categories', data);
}

/** 更新供应商资质 */
export function replaceSupplierQualifications(
  data: ErpSupplierApi.SupplierQualificationSave,
) {
  return requestClient.post('/erp/supplier/credit/qualifications', data);
}

/** 新增供应商风险事件 */
export function createSupplierRiskEvent(
  data: ErpSupplierApi.SupplierRiskEventCreate,
) {
  return requestClient.post<number>('/erp/supplier/credit/risk-events', data);
}

/** 更新供应商风险事件状态 */
export function updateSupplierRiskEventStatus(
  id: number,
  status: string,
  resolution?: string,
) {
  return requestClient.put('/erp/supplier/credit/risk-events/status', null, {
    params: { id, status, resolution },
  });
}

/** 新增供应商 */
export function createSupplier(data: ErpSupplierApi.Supplier) {
  return requestClient.post('/erp/supplier/create', data);
}

/** 修改供应商 */
export function updateSupplier(data: ErpSupplierApi.Supplier) {
  return requestClient.put('/erp/supplier/update', data);
}

/** 删除供应商 */
export function deleteSupplier(id: number) {
  return requestClient.delete(`/erp/supplier/delete?id=${id}`);
}

/** 导出供应商 Excel */
export function exportSupplier(params: any) {
  return requestClient.download('/erp/supplier/export-excel', { params });
}

/** 导出供应商 PDF */
export function exportSupplierPdf(params: any) {
  return requestClient.download('/erp/supplier/export-pdf', { params });
}

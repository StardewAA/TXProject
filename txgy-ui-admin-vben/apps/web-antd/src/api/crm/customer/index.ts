import type { PageParam, PageResult } from '@vben/request';

import type { CrmPermissionApi } from '#/api/crm/permission';

import { requestClient } from '#/api/request';

export namespace CrmCustomerApi {
  /** 客户信息 */
  export interface Customer {
    id: number; // 编号
    name: string; // 客户名称
    contactName?: string; // 联系人
    followUpStatus: boolean; // 跟进状态
    followUpCount?: number; // 累计跟进次数（每写一次跟进 +1，删除跟进 -1）
    contactLastTime: Date; // 最后跟进时间
    contactLastContent: string; // 最后跟进内容
    contactNextTime: Date; // 下次联系时间
    ownerUserId: number; // 负责人的用户编号
    ownerUserName?: string; // 负责人的用户名称
    ownerUserDept?: string; // 负责人的部门名称
    ownerUserDeptName?: string; // 负责人的部门名称
    lockStatus?: boolean;
    dealStatus?: boolean;
    mobile: string; // 手机号
    telephone: string; // 电话
    qq: string; // QQ
    wechat: string; // wechat
    email: string; // email
    areaId: number; // 所在地
    areaName?: string; // 所在地名称
    detailAddress: string; // 详细地址
    industryId: number; // 所属行业
    customerType: number; // 客户类别 1=下游客户 2=上游供应商
    level: number; // 客户等级
    source: number; // 客户来源
    remark: string; // 备注
    creator: string; // 创建人
    creatorName?: string; // 创建人名称
    createTime: Date; // 创建时间
    updateTime: Date; // 更新时间
    poolDay?: number; // 距离进入公海天数
  }

  /** 客户导入请求 */
  export interface CustomerImportReqVO {
    ownerUserId?: number; // 负责人（不传则进入公海）
    file: File;
    updateSupport: boolean;
  }

  /** 供应商 Word 导入明细条目 */
  export interface SupplierImportItem {
    rowNo: number; // 行号
    name?: string; // 供应商名称
    rawContent?: string; // 原始行内容
    reason?: string; // 原因
  }

  /** 供应商 Word 导入结果 */
  export interface SupplierImportResult {
    totalCount: number; // 识别到的条目总数
    successCount: number; // 新增成功
    updateCount: number; // 更新成功
    duplicateCount: number; // 已存在（未更新）
    failCount: number; // 解析失败
    successNames?: string[];
    updateNames?: string[];
    duplicates?: SupplierImportItem[];
    failures?: SupplierImportItem[];
  }

  /** 查重命中项 */
  export interface DuplicateCheckItem {
    bizType: 'clue' | 'contact' | 'customer'; // 业务类型
    bizTypeName: string; // 业务类型名
    id: number; // 记录编号
    name?: string; // 名称
    mobile?: string; // 手机号
    ownerUserId?: number; // 负责人编号
    inPool?: boolean; // 是否在公海（仅客户有效）
    matchReason?: string; // 命中原因
  }

  /** 查重结果 */
  export interface DuplicateCheckRespVO {
    duplicated: boolean; // 是否存在重复记录
    items: DuplicateCheckItem[]; // 命中的记录列表
  }

}

/** 查询客户列表 */
export function getCustomerPage(params: PageParam) {
  return requestClient.get<PageResult<CrmCustomerApi.Customer>>(
    '/crm/customer/page',
    { params },
  );
}

/**
 * 查询客户详情
 *
 * @param id 客户编号
 * @param silent 为 true 时不弹全局错误提示。用于「客户信息速览」这类可选增强，
 *               避免客户无数据权限时（CRM_PERMISSION_DENIED）弹出误导性报错。
 */
export function getCustomer(id: number, silent = false) {
  return requestClient.get<CrmCustomerApi.Customer>(
    `/crm/customer/get?id=${id}`,
    silent ? { headers: { 'X-Silent-Error': 'true' } } : {},
  );
}

/** 新增客户 */
export function createCustomer(data: CrmCustomerApi.Customer) {
  return requestClient.post('/crm/customer/create', data);
}

/** 修改客户 */
export function updateCustomer(data: CrmCustomerApi.Customer) {
  return requestClient.put('/crm/customer/update', data);
}

/** 删除客户 */
export function deleteCustomer(id: number) {
  return requestClient.delete(`/crm/customer/delete?id=${id}`);
}

/** 导出客户 */
export function exportCustomer(params: any) {
  return requestClient.download('/crm/customer/export-excel', { params });
}

/** 下载客户导入模板 */
export function importCustomerTemplate() {
  return requestClient.download('/crm/customer/get-import-template');
}

/** 导入客户 */
export function importCustomer(data: CrmCustomerApi.CustomerImportReqVO) {
  return requestClient.upload('/crm/customer/import', data);
}

/** 获取客户精简信息列表 */
export function getCustomerSimpleList() {
  return requestClient.get<CrmCustomerApi.Customer[]>(
    '/crm/customer/simple-list',
  );
}

/**
 * 获取客户精简信息列表（可按客户类别过滤）
 *
 * @param customerType 客户类别：1=下游客户 2=上游供应商；不传则不过滤
 */
export function getCustomerSimpleListByType(customerType?: number) {
  return requestClient.get<CrmCustomerApi.Customer[]>(
    '/crm/customer/simple-list',
    { params: { customerType } },
  );
}

/**
 * 获得供应商基础库（客户类别=上游供应商）精简列表
 *
 * 供应商属于租户级共享主数据，后端不做数据权限过滤，含联系人/电话/地址
 */
export function getSupplierSimpleList() {
  return requestClient.get<CrmCustomerApi.Customer[]>(
    '/crm/customer/supplier-simple-list',
  );
}

/**
 * 从 Word（.docx）导入供应商
 *
 * @param file 文档
 * @param updateExisting 是否更新已存在的供应商（false=只新增，重复的跳过）
 */
export function importSupplierWord(file: File, updateExisting = false) {
  return requestClient.upload<CrmCustomerApi.SupplierImportResult>(
    '/crm/customer/import-supplier-word',
    { file, updateExisting },
  );
}

/** 获得相似客户列表（同客户类别、同行业） */
export function getSimilarCustomerList(customerId: number, limit = 10) {
  return requestClient.get<CrmCustomerApi.Customer[]>(
    '/crm/customer/similar-list',
    { params: { customerId, limit } },
  );
}

/** 客户转移 */
export function transferCustomer(data: CrmPermissionApi.BusinessTransferReqVO) {
  return requestClient.put('/crm/customer/transfer', data);
}

/** 锁定/解锁客户 */
export function lockCustomer(id: number, lockStatus: boolean) {
  return requestClient.put('/crm/customer/lock', { id, lockStatus });
}

/** 领取公海客户（后端为 @RequestParam("ids")，必须以 query 参数传递） */
export function receiveCustomer(ids: number[]) {
  return requestClient.put('/crm/customer/receive', undefined, {
    params: { ids: ids.join(',') },
  });
}

/** 分配公海给对应负责人 */
export function distributeCustomer(ids: number[], ownerUserId: number) {
  return requestClient.put('/crm/customer/distribute', { ids, ownerUserId });
}

/** 客户放入公海 */
export function putCustomerPool(id: number) {
  return requestClient.put(`/crm/customer/put-pool?id=${id}`);
}

/** 更新客户的成交状态 */
export function updateCustomerDealStatus(id: number, dealStatus: boolean) {
  return requestClient.put(
    `/crm/customer/update-deal-status?id=${id}&dealStatus=${dealStatus}`,
  );
}

/** 进入公海客户提醒的客户列表 */
export function getPutPoolRemindCustomerPage(params: PageParam) {
  return requestClient.get<PageResult<CrmCustomerApi.Customer>>(
    '/crm/customer/put-pool-remind-page',
    { params },
  );
}

/** 获得待进入公海客户数量 */
export function getPutPoolRemindCustomerCount() {
  return requestClient.get<number>('/crm/customer/put-pool-remind-count');
}

/** 获得今日需联系客户数量 */
export function getTodayContactCustomerCount() {
  return requestClient.get<number>('/crm/customer/today-contact-count');
}

/** 获得分配给我、待跟进的线索数量的客户数量 */
export function getFollowCustomerCount() {
  return requestClient.get<number>('/crm/customer/follow-count');
}

/** 查重：根据手机号、名称匹配已存在的客户、线索、联系人 */
export function checkDuplicate(params: { mobile?: string; name?: string }) {
  return requestClient.get<CrmCustomerApi.DuplicateCheckRespVO>(
    '/crm/customer/check-duplicate',
    { params },
  );
}


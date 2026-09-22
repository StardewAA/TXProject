import type { PageParam, PageResult } from '@vben/request';

import type { CrmPermissionApi } from '#/api/crm/permission';

import { requestClient } from '#/api/request';

export namespace CrmBusinessApi {
  /** 商机信息 */
  export interface Business {
    id: number;
    name: string;
    customerId: number;
    customerName?: string;
    followUpStatus: boolean;
    contactLastTime: Date;
    contactNextTime: Date;
    ownerUserId: number;
    ownerUserName?: string; // 负责人的用户名称
    ownerUserDept?: string; // 负责人的部门名称
    statusTypeId: number;
    statusTypeName?: string;
    statusId: number;
    statusName?: string;
    /** 商机阶段：1商机待评估 2初步接洽 3需求确认 4方案评估 5商务谈判 */
    stage?: number;
    stageName?: string;
    /** 商机状态组：1未开始 2进行中 3已赢单 4已丢单（只读派生） */
    businessState?: number;
    businessStateName?: string;
    /** 预估赢单率(%)（只读派生：5/10/40/60/70，赢单=100、丢单=0） */
    winRate?: number;
    /** 丢单原因：1..6（仅输单时填写） */
    lostReason?: number;
    lostReasonName?: string;
    lostRemark?: string;
    /** 签约日期（赢单时填写） */
    signDate?: Date;
    /** 签约金额（赢单时填写） */
    signAmount?: number;
    /** 关闭时间 */
    closedAt?: Date;
    /** 关闭操作人 */
    closedBy?: number;
    closedByName?: string;
    endStatus: number;
    endRemark: string;
    dealTime: Date;
    totalProductPrice: number;
    totalPrice: number;
    discountPercent: number;
    status?: number;
    remark: string;
    creator: string; // 创建人
    creatorName?: string; // 创建人名称
    createTime: Date; // 创建时间
    updateTime: Date; // 更新时间
    products?: BusinessProduct[];
  }

  /** 商机产品信息 */
  export interface BusinessProduct {
    id: number;
    productId: number;
    productName: string;
    productNo: string;
    productUnit: number;
    productPrice: number;
    businessPrice: number;
    count: number;
    totalPrice: number;
  }

  /** 商机更新状态请求（历史字段，已由「赢单/输单」按钮替代） */
  export interface BusinessUpdateStatusReqVO {
    id: number;
    statusId: number | undefined;
    endStatus: number | undefined;
  }

  /** 商机赢单请求：签约日期、签约金额必填 */
  export interface BusinessWinReqVO {
    id: number;
    signDate: number; // 签约日期（毫秒时间戳）
    signAmount: number; // 签约金额（元）
    remark?: string; // 赢单备注
  }

  /** 商机输单请求：丢单原因必填、丢单备注选填 */
  export interface BusinessLoseReqVO {
    id: number;
    lostReason: number; // 丢单原因
    lostRemark?: string; // 丢单备注
  }

  /** 商机状态组调整请求（仅管理员：重新开启/纠正为 未开始/进行中） */
  export interface BusinessUpdateStateReqVO {
    id: number;
    businessState: number;
    reason?: string; // 调整说明（写入操作日志）
  }
}

/** 查询商机列表 */
export function getBusinessPage(params: PageParam) {
  return requestClient.get<PageResult<CrmBusinessApi.Business>>(
    '/crm/business/page',
    { params },
  );
}

/** 查询商机列表，基于指定客户 */
export function getBusinessPageByCustomer(params: PageParam) {
  return requestClient.get<PageResult<CrmBusinessApi.Business>>(
    '/crm/business/page-by-customer',
    { params },
  );
}

/** 查询商机详情 */
export function getBusiness(id: number) {
  return requestClient.get<CrmBusinessApi.Business>(
    `/crm/business/get?id=${id}`,
  );
}

/** 获得商机列表（精简） */
export function getSimpleBusinessList() {
  return requestClient.get<CrmBusinessApi.Business[]>(
    '/crm/business/simple-all-list',
  );
}

/** 新增商机 */
export function createBusiness(data: CrmBusinessApi.Business) {
  return requestClient.post('/crm/business/create', data);
}

/** 修改商机 */
export function updateBusiness(data: CrmBusinessApi.Business) {
  return requestClient.put('/crm/business/update', data);
}

/** 修改商机状态（历史接口，商机阶段请用 updateBusinessStage） */
export function updateBusinessStatus(
  data: CrmBusinessApi.BusinessUpdateStatusReqVO,
) {
  return requestClient.put('/crm/business/update-status', data);
}

/** 推进商机阶段（状态组与预估赢单率由后端自动派生） */
export function updateBusinessStage(id: number, stage: number) {
  return requestClient.put(`/crm/business/update-stage?id=${id}&stage=${stage}`, {});
}

/** 商机赢单（签约日期、签约金额必填） */
export function winBusiness(data: CrmBusinessApi.BusinessWinReqVO) {
  return requestClient.put('/crm/business/win', data);
}

/** 商机输单（丢单原因必填、丢单备注选填） */
export function loseBusiness(data: CrmBusinessApi.BusinessLoseReqVO) {
  return requestClient.put('/crm/business/lose', data);
}

/** 调整商机状态组（仅管理员：重新开启/纠正，需要 crm:business:modify-state 权限） */
export function updateBusinessState(
  data: CrmBusinessApi.BusinessUpdateStateReqVO,
) {
  return requestClient.put('/crm/business/update-state', data);
}

/** 删除商机 */
export function deleteBusiness(id: number) {
  return requestClient.delete(`/crm/business/delete?id=${id}`);
}

/** 导出商机 */
export function exportBusiness(params: any) {
  return requestClient.download('/crm/business/export-excel', { params });
}

/** 联系人关联商机列表 */
export function getBusinessPageByContact(params: PageParam) {
  return requestClient.get<PageResult<CrmBusinessApi.Business>>(
    '/crm/business/page-by-contact',
    { params },
  );
}

/** 商机转移 */
export function transferBusiness(data: CrmPermissionApi.BusinessTransferReqVO) {
  return requestClient.put('/crm/business/transfer', data);
}

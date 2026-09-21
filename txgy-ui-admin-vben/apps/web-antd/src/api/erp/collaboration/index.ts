import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace ErpCollaborationApi {
  /** 供应链协同单 */
  export interface CollaborationOrder {
    id: number;
    collaborationNo: string; // 协同单号
    collaborationType: string; // 协同单类型
    businessChainNo: string; // 业务链号
    sourceModule?: string; // 来源系统模块
    sourceOrderNo?: string; // 通用来源单号
    sourceEnterpriseCode?: string; // 发起方企业编码
    targetEnterpriseCode?: string; // 接收方企业编码
    currency?: string; // 币种
    sourceProductionOrderNo?: string; // 来源生产工单号
    sourceCollaborationGNo?: string; // 来源协作单 G 编号
    sourceSaleOrderNo?: string; // 来源销售订单号
    technicalStatus: string; // 技术状态
    businessStatus: string; // 业务状态
    eventVersion: number; // 事件版本
    requiredAt?: string; // 期望交期
    reason?: string; // 原因/补充说明
    confirmUserId?: number; // 确认人编号
    confirmUserName?: string; // 确认人
    confirmTime?: string; // 确认时间
    purchaseOrderId?: number; // 关联采购订单编号
    purchaseOrderNo?: string; // 关联采购订单单号
    createTime?: string;
  }

  /** 协同单明细 */
  export interface CollaborationItem {
    lineNo: number;
    sourceLineNo?: number;
    materialCode: string;
    skuCode?: string;
    materialNameSnapshot?: string;
    specificationSnapshot?: string;
    unitCode?: string;
    unitNameSnapshot?: string;
    shortageQuantity?: number;
    referencePrice?: number;
    taxRate?: number;
    deliveryAddress?: string;
  }

  /** 确认日志 */
  export interface ConfirmLog {
    action: string;
    reason?: string;
    confirmUserId?: number;
    confirmUserName?: string;
    createTime?: string;
  }

  /** 协同单详情 */
  export interface CollaborationDetail extends CollaborationOrder {
    purchaseOrderStatus?: number; // 关联采购订单审批状态（实时快照）
    items: CollaborationItem[];
    confirmLogs: ConfirmLog[];
  }

  /** 业务确认请求 */
  export interface ConfirmReq {
    id: number;
    action: 'ACCEPTED' | 'REJECTED' | 'NEED_SUPPLEMENT';
    supplierId?: number;
    accountId?: number;
    orderTime?: string;
    reason?: string;
    remark?: string;
    items?: ConfirmItem[];
  }

  export interface ConfirmItem {
    lineNo: number;
    productPrice: number;
    taxPercent?: number;
    remark?: string;
  }
}

const BASE_URL = '/erp/wms-collaboration-b';

/** WMS 集成申请受理结果 */
export interface WmsIntegrationAcceptedResp {
  accepted: boolean;
  requestId: string;
  businessStatus: string;
  processedVersion?: number;
  targetOrderNo?: string;
}

/**
 * 手动申请入库到 WMS（基于已审批的 ERP 采购入库单）
 *
 * 后端端点 POST /erp/wms-collaboration-b/apply-inbound，按钮触发，
 * 入库单必须 status=20(已审批) 且 wmsStatus ∈ {null, NOT_SUBMITTED, FAILED}。
 */
export function applyInboundToWms(data: {
  purchaseInId: number;
  targetErpWarehouseId: number;
  remark?: string;
}) {
  return requestClient.post<WmsIntegrationAcceptedResp>(
    `${BASE_URL}/apply-inbound`,
    data,
  );
}

/** 查询供应链协同单分页 */
export function getCollaborationPage(params: PageParam) {
  return requestClient.get<PageResult<ErpCollaborationApi.CollaborationOrder>>(
    `${BASE_URL}/page`,
    { params },
  );
}

/** 查询供应链协同单详情 */
export function getCollaborationDetail(id: number) {
  return requestClient.get<ErpCollaborationApi.CollaborationDetail>(
    `${BASE_URL}/get?id=${id}`,
  );
}

/** 业务确认：接受 / 拒绝 / 要求补充资料 */
export function confirmCollaboration(data: ErpCollaborationApi.ConfirmReq) {
  return requestClient.put(`${BASE_URL}/confirm`, data);
}

/** 协同单类型映射 */
export const COLLABORATION_TYPE_OPTIONS = [
  { label: '采购协同单', value: 'PURCHASE_COLLABORATION' },
  { label: '销售协同单', value: 'SALE_COLLABORATION' },
  { label: '定标结果协同单', value: 'AWARD_COLLABORATION' },
  { label: '缺料采购协同单', value: 'SHORTAGE_COLLABORATION' },
];

/** 业务状态映射 */
export const BUSINESS_STATUS_OPTIONS = [
  { label: '待业务确认', value: 'PENDING_CONFIRMATION' },
  { label: '待补充资料', value: 'NEED_SUPPLEMENT' },
  { label: '订单生成中', value: 'CONFIRMING' },
  { label: '已生成订单', value: 'ACCEPTED' },
  { label: '已拒绝', value: 'REJECTED' },
];

export function getBusinessStatusText(status?: string) {
  return BUSINESS_STATUS_OPTIONS.find((item) => item.value === status)?.label
    ?? status
    ?? '';
}

export function getCollaborationTypeText(type?: string) {
  return COLLABORATION_TYPE_OPTIONS.find((item) => item.value === type)?.label
    ?? type
    ?? '';
}

export function getBusinessStatusColor(status?: string) {
  switch (status) {
    case 'PENDING_CONFIRMATION': {
      return 'processing';
    }
    case 'NEED_SUPPLEMENT': {
      return 'warning';
    }
    case 'CONFIRMING': {
      return 'processing';
    }
    case 'ACCEPTED': {
      return 'success';
    }
    case 'REJECTED': {
      return 'error';
    }
    default: {
      return 'default';
    }
  }
}

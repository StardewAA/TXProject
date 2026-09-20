import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace CrmAfterSaleWarrantyApi {
  /** 售后质保档案 */
  export interface Warranty {
    id?: number;
    no?: string;
    contractId?: number;
    customerId?: number;
    contractProductId?: number;
    projectName?: string;
    projectAcceptDate?: string;
    warrantyYears?: number;
    warrantyEndDate?: string;
    warrantyScope?: string;
    status?: number;
    ownerUserId?: number;
    serviceUserId?: number;
    lastVisitTime?: string;
    nextVisitTime?: string;
    remindSentDate?: string;
    remark?: string;
    createTime?: string;
    updateTime?: string;
    /** 扩展：合同编号 */
    contractNo?: string;
    /** 扩展：合同名称 */
    contractName?: string;
    /** 扩展：客户名称 */
    customerName?: string;
    /** 扩展：质保剩余天数，负数表示已到期 */
    remainDays?: number;
  }

  /** 分页查询参数 */
  export interface PageReq extends PageParam, Partial<Warranty> {
    warrantyEndDate?: string[];
    /** 到期类型：1=即将到期（待办「质保即将到期」，实时查询且限本人及下属负责） */
    expiryType?: number;
  }

  /** 合同的质保档案状态（用于「一个合同仅允许一份质保档案」的识别） */
  export interface ContractWarrantyStatus {
    /** 合同编号 */
    contractId: number;
    /** 质保档案编号；为空表示该合同尚未创建质保档案（可发起创建） */
    warrantyId?: number;
    /** 质保编号 */
    warrantyNo?: string;
    /** 工程项目名称 */
    projectName?: string;
    /** 质保状态：1质保中 2即将到期 3已到期终止 */
    status?: number;
    /** 质保到期日期 */
    warrantyEndDate?: string;
  }

  /**
   * 更新质保档案的响应：保存后质保状态自动重算的结果
   *
   * 编辑时若「完工验收日期 / 质保年限 / 质保到期日期」发生变化，后端会按最新到期日
   * 重算质保状态（质保中 / 即将到期 / 已到期终止），并把结果放在这里供界面实时展示。
   */
  export interface UpdateResp {
    /** 本次保存是否触发了质保状态变更 */
    statusChanged?: boolean;
    /** 变更前的质保状态 */
    oldStatus?: number;
    /** 变更后的质保状态（最终落库值） */
    status?: number;
    /** 重算后的质保到期日期 */
    warrantyEndDate?: string;
    /** 状态变更说明，可直接用于提示用户 */
    message?: string;
    /**
     * 是否需要人工确认
     *
     * 状态由「已到期终止」回退为有效状态时为 true：
     * 此前因到期被停用的回访计划不会自动恢复，需要用户人工确认是否重新启用。
     */
    needManualConfirm?: boolean;
  }
}

/** 到期类型：1=即将到期（待办「质保即将到期」使用，与后端 EXPIRY_TYPE_ABOUT_TO_EXPIRE 保持一致） */
export const AFTER_SALE_WARRANTY_EXPIRY_TYPE_ABOUT_TO_EXPIRE = 1;

/** 获得「质保即将到期」待办数量（实时查询，不落库） */
export function getAfterSaleWarrantyRemindCount() {
  return requestClient.get<number>('/crm/after-sale/warranty/remind-count');
}

/** 查询质保档案分页 */
export function getAfterSaleWarrantyPage(
  params: CrmAfterSaleWarrantyApi.PageReq,
) {
  return requestClient.get<PageResult<CrmAfterSaleWarrantyApi.Warranty>>(
    '/crm/after-sale/warranty/page',
    { params },
  );
}

/** 查询质保档案详情 */
export function getAfterSaleWarranty(id: number) {
  return requestClient.get<CrmAfterSaleWarrantyApi.Warranty>(
    `/crm/after-sale/warranty/get?id=${id}`,
  );
}

/** 新增质保档案 */
export function createAfterSaleWarranty(
  data: CrmAfterSaleWarrantyApi.Warranty,
) {
  return requestClient.post('/crm/after-sale/warranty/create', data);
}

/**
 * 批量查询合同的质保档案状态
 *
 * 用于「一个合同仅允许创建一份质保档案」的识别：
 * 返回结果中 warrantyId 有值，表示该合同已创建质保档案，创建入口应禁止选择。
 */
export async function getAfterSaleWarrantyContractStatus(
  contractIds: number[],
): Promise<CrmAfterSaleWarrantyApi.ContractWarrantyStatus[]> {
  if (!contractIds || contractIds.length === 0) {
    return [];
  }
  return requestClient.get<CrmAfterSaleWarrantyApi.ContractWarrantyStatus[]>(
    '/crm/after-sale/warranty/contract-status',
    { params: { contractIds: contractIds.join(',') } },
  );
}

/**
 * 修改质保档案
 *
 * 返回值为保存后质保状态的自动重算结果（是否变更、原状态、新状态、提示文案），
 * 调用方应据此提示用户，让状态变化实时可见。
 */
export function updateAfterSaleWarranty(
  data: CrmAfterSaleWarrantyApi.Warranty,
) {
  return requestClient.put<CrmAfterSaleWarrantyApi.UpdateResp>(
    '/crm/after-sale/warranty/update',
    data,
  );
}

/** 删除质保档案 */
export function deleteAfterSaleWarranty(id: number) {
  return requestClient.delete(`/crm/after-sale/warranty/delete?id=${id}`);
}

/** 质保档案状态：1=质保中（与后端 CrmAfterSaleWarrantyStatusEnum.IN_WARRANTY 保持一致） */
export const AFTER_SALE_WARRANTY_STATUS_IN_WARRANTY = 1;

/** 质保档案状态：2=即将到期（与后端 CrmAfterSaleWarrantyStatusEnum.EXPIRING 保持一致） */
export const AFTER_SALE_WARRANTY_STATUS_EXPIRING = 2;

/** 质保档案状态：3=已到期终止（与后端 CrmAfterSaleWarrantyStatusEnum.EXPIRED 保持一致） */
export const AFTER_SALE_WARRANTY_STATUS_EXPIRED = 3;

/** 质保档案下拉选项 */
export interface WarrantyOption {
  disabled?: boolean;
  label: string;
  value: number | undefined;
}

/** 质保档案 → 下拉选项（统一文案口径：编号 - 工程项目名称） */
function toWarrantyOption(
  item: CrmAfterSaleWarrantyApi.Warranty,
): WarrantyOption {
  return {
    label: item.no
      ? `${item.no} - ${item.projectName ?? ''}`
      : (item.projectName ?? ''),
    value: item.id,
  };
}

/** 获取质保档案下拉数据（基于分页接口，最多 200 条） */
export async function getAfterSaleWarrantyOptions() {
  const data = await getAfterSaleWarrantyPage({ pageNo: 1, pageSize: 200 });
  return (data?.list ?? []).map(toWarrantyOption);
}

/**
 * 获取指定客户名下「质保中」的质保档案下拉数据（最多 200 条）
 *
 * 用于新增回访计划时按客户联动筛选：只返回质保状态 = 质保中的档案，
 * 已到期终止 / 即将到期的档案不参与新建计划；未选择客户时不发起查询。
 */
export async function getInWarrantyOptionsByCustomer(
  customerId?: number,
): Promise<WarrantyOption[]> {
  if (!customerId) {
    return [];
  }
  const data = await getAfterSaleWarrantyPage({
    pageNo: 1,
    pageSize: 200,
    customerId,
    status: AFTER_SALE_WARRANTY_STATUS_IN_WARRANTY,
  });
  return (data?.list ?? []).map(toWarrantyOption);
}

import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace CrmBusinessStatusApi {
  /** 商机状态组信息 */
  export interface BusinessStatus {
    id?: number;
    name: string;
    deptIds?: number[];
    deptNames?: string[];
    creator?: string;
    createTime?: Date;
    statuses: BusinessStatusType[];
  }

  /** 商机状态信息 */
  export interface BusinessStatusType {
    id?: number;
    name: string;
    percent?: number;
    endStatus?: number;
    key?: string;
  }
}

/**
 * 标准商机阶段（与后端 CrmBusinessStageEnum 保持一致）
 *
 * 说明：商机阶段已标准化为固定 5 阶段；赢单/输单不再是阶段，
 * 改由商机列表的「赢单」「输单」按钮驱动（只改商机状态组），因此这里不再包含结束阶段。
 */
export const STANDARD_STAGES = [
  { name: '商机待评估', percent: 5 },
  { name: '初步接洽', percent: 10 },
  { name: '需求确认', percent: 40 },
  { name: '方案评估', percent: 60 },
  { name: '商务谈判', percent: 70 },
] satisfies CrmBusinessStatusApi.BusinessStatusType[];

/** 默认商机状态：阶段已标准化，不再追加「赢单/输单/无效」固定结束行 */
export const DEFAULT_STATUSES: CrmBusinessStatusApi.BusinessStatusType[] = [];

/** 查询商机状态组列表 */
export function getBusinessStatusPage(params: PageParam) {
  return requestClient.get<PageResult<CrmBusinessStatusApi.BusinessStatus>>(
    '/crm/business-status/page',
    { params },
  );
}

/** 新增商机状态组 */
export function createBusinessStatus(
  data: CrmBusinessStatusApi.BusinessStatus,
) {
  return requestClient.post('/crm/business-status/create', data);
}

/** 修改商机状态组 */
export function updateBusinessStatus(
  data: CrmBusinessStatusApi.BusinessStatus,
) {
  return requestClient.put('/crm/business-status/update', data);
}

/** 查询商机状态类型详情 */
export function getBusinessStatus(id: number) {
  return requestClient.get<CrmBusinessStatusApi.BusinessStatus>(
    `/crm/business-status/get?id=${id}`,
  );
}

/** 删除商机状态 */
export function deleteBusinessStatus(id: number) {
  return requestClient.delete(`/crm/business-status/delete?id=${id}`);
}

/** 获得商机状态组列表 */
export function getBusinessStatusTypeSimpleList() {
  return requestClient.get<CrmBusinessStatusApi.BusinessStatus[]>(
    '/crm/business-status/type-simple-list',
  );
}

/** 获得商机阶段列表 */
export function getBusinessStatusSimpleList(typeId: number) {
  return requestClient.get<CrmBusinessStatusApi.BusinessStatusType[]>(
    '/crm/business-status/status-simple-list',
    { params: { typeId } },
  );
}

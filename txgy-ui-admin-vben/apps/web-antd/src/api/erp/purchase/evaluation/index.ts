import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace ErpSupplierEvaluationApi {
  /** 供应商评价信息 */
  export interface SupplierEvaluation {
    id?: number; // 评价编号
    no?: string; // 评价单号
    supplierId?: number; // 供应商编号
    supplierName?: string; // 供应商名称
    evalPeriod?: string; // 评价周期
    evalDate?: number; // 评价日期
    qualityScore?: number; // 质量得分
    deliveryScore?: number; // 交付得分
    priceScore?: number; // 价格得分
    serviceScore?: number; // 服务得分
    totalScore?: number; // 综合得分
    grade?: string; // 评价等级：A / B / C / D
    evaluatorId?: number; // 评价人编号
    comment?: string; // 评价说明
    status?: number; // 评价状态
    remark?: string; // 备注
    creator?: string; // 创建人编号
    creatorName?: string; // 创建人名称
    createTime?: Date; // 创建时间
  }
}

/** 查询供应商评价分页 */
export function getSupplierEvaluationPage(params: PageParam) {
  return requestClient.get<
    PageResult<ErpSupplierEvaluationApi.SupplierEvaluation>
  >('/erp/purchase-evaluation/page', { params });
}

/** 查询供应商评价详情 */
export function getSupplierEvaluation(id: number) {
  return requestClient.get<ErpSupplierEvaluationApi.SupplierEvaluation>(
    '/erp/purchase-evaluation/get',
    { params: { id } },
  );
}

/** 新增供应商评价 */
export function createSupplierEvaluation(
  data: ErpSupplierEvaluationApi.SupplierEvaluation,
) {
  return requestClient.post('/erp/purchase-evaluation/create', data);
}

/** 修改供应商评价 */
export function updateSupplierEvaluation(
  data: ErpSupplierEvaluationApi.SupplierEvaluation,
) {
  return requestClient.put('/erp/purchase-evaluation/update', data);
}

/** 删除供应商评价 */
export function deleteSupplierEvaluation(ids: number[]) {
  return requestClient.delete('/erp/purchase-evaluation/delete', {
    params: { ids: ids.join(',') },
  });
}

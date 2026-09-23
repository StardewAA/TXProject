import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace MallSpuApi {
  /** 商品 SPU */
  export interface Spu {
    id?: number; // 商品编号
    shopId?: number; // 所属企业店铺
    name?: string; // 商品名称
    categoryId?: number; // 商品分类
    keyword?: string; // 关键字
    unit?: number; // 单位
    picUrl?: string; // 商品封面图
    sliderPicUrls?: string[]; // 商品轮播图
    introduction?: string; // 商品简介
    deliveryTypes?: number[]; // 配送方式
    deliveryTemplateId?: number; // 运费模版
    brandId?: number; // 商品品牌编号
    specType?: boolean; // 商品规格
    subCommissionType?: boolean; // 分销类型
    skus?: Sku[]; // sku数组
    description?: string; // 商品详情
    sort?: number; // 商品排序
    giveIntegral?: number; // 赠送积分
    virtualSalesCount?: number; // 虚拟销量
    price?: number; // 商品价格
    combinationPrice?: number; // 商品拼团价格
    seckillPrice?: number; // 商品秒杀价格
    salesCount?: number; // 商品销量
    marketPrice?: number; // 市场价
    costPrice?: number; // 成本价
    stock?: number; // 商品库存
    createTime?: Date; // 商品创建时间
    status?: number; // 商品状态
    browseCount?: number; // 浏览量
    reviewStatus?: number | null; // null 仅表示历史过渡，不能当作审核通过
    contentVersion?: number;
    approvedVersion?: number | null;
    submissionId?: string | null;
    reviewRemark?: string;
    expectedVersion?: number; // 后台保存时用于乐观锁校验
  }

  /** 商品 SKU */
  export interface Sku {
    id?: number; // 商品 SKU 编号
    name?: string; // 商品 SKU 名称
    spuId?: number; // SPU 编号
    properties?: Property[]; // 属性数组
    price?: number | string; // 商品价格
    marketPrice?: number | string; // 市场价
    costPrice?: number | string; // 成本价
    barCode?: string; // 商品条码
    picUrl?: string; // 图片地址
    stock?: number; // 库存
    wmsSkuId?: number; // WMS 成品 SKU 编号
    wmsWarehouseId?: number; // WMS 成品仓编号
    weight?: number; // 商品重量，单位：kg 千克
    volume?: number; // 商品体积，单位：m^3 平米
    firstBrokeragePrice?: number | string; // 一级分销的佣金
    secondBrokeragePrice?: number | string; // 二级分销的佣金
    salesCount?: number; // 商品销量
  }

  /** 商品属性 */
  export interface Property {
    propertyId?: number; // 属性编号
    propertyName?: string; // 属性名称
    valueId?: number; // 属性值编号
    valueName?: string; // 属性值名称
  }

  /** 商品状态更新请求 */
  export interface SpuStatusUpdateReqVO {
    id: number; // 商品编号
    status: number; // 商品状态
    expectedVersion: number;
  }

  export interface ReviewPolicy {
    adminWriteEnabled: boolean;
    adminWriteLockedShopIds?: number[];
    legacyStrategy: string;
    reviewEnabled: boolean;
  }

  export interface ReviewRecord {
    id: number;
    spuId: number;
    shopId?: number;
    contentVersion: number;
    submissionId?: string | null;
    action: string;
    actorType: number; // 1 会员，2 后台账号
    actorId?: number;
    remark?: string;
    snapshot: string;
    createTime: string | number;
  }

  export interface ReviewDecision {
    id: number;
    expectedVersion: number;
    submissionId: string;
    approved: boolean;
    reason: string;
  }
}

/** 后台经营写入策略；加载失败时页面保持只读。 */
export function getSpuReviewPolicy() {
  return requestClient.get<MallSpuApi.ReviewPolicy>('/product/spu-review/policy');
}

export function getSpuReviewHistory(id: number) {
  return requestClient.get<MallSpuApi.ReviewRecord[]>('/product/spu-review/history', {
    params: { id },
  });
}

export function decideSpuReview(data: MallSpuApi.ReviewDecision) {
  return requestClient.post<boolean>('/product/spu-review/decision', data);
}

/** 后台经营账号提交商品审核；企业、店铺范围由服务端校验。 */
export function submitSpuReview(data: { id: number; expectedVersion: number }) {
  return requestClient.post<boolean>('/product/spu-review/submit', data);
}

export function withdrawSpuReview(data: { id: number; expectedVersion: number; submissionId: string }) {
  return requestClient.post<boolean>('/product/spu-review/withdraw', data);
}

/** 获得商品 SPU 列表 */
export function getSpuPage(params: PageParam) {
  return requestClient.get<PageResult<MallSpuApi.Spu>>('/product/spu/page', {
    params,
  });
}

/** 获得商品 SPU 列表 tabsCount（支持按 name/categoryId/createTime 筛选） */
export function getTabsCount(params?: Record<string, any>) {
  return requestClient.get<Record<string, number>>('/product/spu/get-count', {
    params,
  });
}

/** 从 WMS 重新同步商城商品库存缓存 */
export function syncWmsStockCache() {
  return requestClient.put<number>('/product/spu/sync-wms-stock-cache');
}

/** 创建商品 SPU */
export function createSpu(data: MallSpuApi.Spu) {
  return requestClient.post('/product/spu/create', data);
}

/** 更新商品 SPU */
export function updateSpu(data: MallSpuApi.Spu) {
  return requestClient.put('/product/spu/update', data);
}

/** 更新商品 SPU 状态 */
export function updateStatus(data: MallSpuApi.SpuStatusUpdateReqVO) {
  return requestClient.put('/product/spu/update-status', data);
}

/** 获得商品 SPU */
export function getSpu(id: number) {
  return requestClient.get<MallSpuApi.Spu>(`/product/spu/get-detail?id=${id}`);
}

/** 获得商品 SPU 详情列表 */
export function getSpuDetailList(ids: number[]) {
  return requestClient.get<MallSpuApi.Spu[]>(`/product/spu/list?spuIds=${ids}`);
}

/** 删除商品 SPU */
export function deleteSpu(id: number, expectedVersion: number) {
  if (!Number.isSafeInteger(id) || id <= 0
    || !Number.isSafeInteger(expectedVersion) || expectedVersion < 0) {
    throw new Error('商品编号或内容版本无效，请刷新后重试。');
  }
  return requestClient.delete('/product/spu/delete', {
    params: { id, expectedVersion },
  });
}

/** 导出商品 SPU Excel */
export function exportSpu(params: PageParam) {
  return requestClient.download('/product/spu/export-excel', { params });
}

/** 获得商品 SPU 精简列表 */
export function getSpuSimpleList() {
  return requestClient.get<MallSpuApi.Spu[]>('/product/spu/list-all-simple');
}

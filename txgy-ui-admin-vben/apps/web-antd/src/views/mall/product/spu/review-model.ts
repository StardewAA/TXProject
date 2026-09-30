import type { MallSpuApi } from '#/api/mall/product/spu';

export const reviewStatusOptions = [
  { label: '历史过渡（未补审）', value: -1 },
  { label: '草稿', value: 0 },
  { label: '待审核', value: 10 },
  { label: '审核通过', value: 20 },
  { label: '审核驳回', value: 30 },
];

/** 策略未知或店铺被锁定时 fail closed；变更所属店铺不能绕过源店限制。 */
export function canAdminMaintain(
  policy: MallSpuApi.ReviewPolicy | undefined,
  shopId?: number | null,
) {
  return policy?.adminWriteEnabled === true
    && Array.isArray(policy.adminWriteLockedShopIds)
    && !policy.adminWriteLockedShopIds.includes(shopId ?? 0);
}

/** 审核职责与企业经营职责互斥；角色误配时前端保持只读。 */
export function canOperateProduct(
  policy: MallSpuApi.ReviewPolicy | undefined,
  shopId: number | null | undefined,
  hasReviewPermission: boolean,
) {
  return !hasReviewPermission && canAdminMaintain(policy, shopId);
}

export function reviewStatusText(value?: number | null) {
  if (value === null || value === undefined) return '历史过渡（未补审）';
  return reviewStatusOptions.find((item) => item.value === value)?.label ?? '未知审核状态';
}

export function reviewActionText(value: string) {
  return ({
    APPROVE: '审核通过',
    CREATE: '创建草稿',
    EDIT: '修改资料',
    REJECT: '审核驳回',
    RESTORE: '恢复为草稿',
    SAVE: '保存草稿',
    SUBMIT: '提交审核',
    UPDATE: '修改资料',
    WITHDRAW: '撤回审核',
  } as Record<string, string>)[value] ?? value;
}

/** 不接受解析失败的快照，更不能用当前可变详情替代待审内容。 */
export function parseReviewSnapshot(value?: string): MallSpuApi.Spu | null {
  if (!value) return null;
  try {
    const data: unknown = JSON.parse(value);
    if (!data || typeof data !== 'object' || Array.isArray(data)) return null;
    const product = data as MallSpuApi.Spu;
    if (typeof product.name !== 'string' || !Array.isArray(product.skus)) return null;
    return product;
  } catch {
    return null;
  }
}

export function currentSubmission(
  product: MallSpuApi.Spu | undefined,
  records: MallSpuApi.ReviewRecord[],
) {
  if (!product?.submissionId || !Number.isSafeInteger(product.contentVersion)) return undefined;
  return records.find((record) =>
    record.action === 'SUBMIT'
    && String(record.submissionId) === String(product.submissionId)
    && record.contentVersion === product.contentVersion
    && record.spuId === product.id,
  );
}

export function canDecideReview(
  product: MallSpuApi.Spu | undefined,
  submission: MallSpuApi.ReviewRecord | undefined,
  policy: MallSpuApi.ReviewPolicy | undefined,
) {
  return policy?.reviewEnabled === true
    && product?.reviewStatus === 10
    && !!submission
    && submission.action === 'SUBMIT'
    && submission.spuId === product.id
    && String(submission.submissionId) === String(product.submissionId)
    && submission.contentVersion === product.contentVersion
    && !!parseReviewSnapshot(submission.snapshot);
}

const contentLabels: Record<string, string> = {
  name: '商品名称', keyword: '关键词', introduction: '商品简介',
  categoryId: '分类编号', brandId: '品牌编号', shopId: '店铺编号', unit: '计量单位',
  picUrl: '封面图', sliderPicUrls: '轮播图', description: '商品描述',
  specType: '规格类型', subCommissionType: '分销类型', skus: 'SKU / 规格价格',
  deliveryTypes: '配送方式', deliveryTemplateId: '运费模板编号',
  sort: '排序', giveIntegral: '赠送积分', virtualSalesCount: '虚拟销量',
};

/** 审核差异只比较经营内容；实时库存、销量、ERP 映射不进入审核变更。 */
const dynamicFields = new Set([
  'stock', 'salesCount', 'browseCount', 'createTime', 'updateTime',
  'wmsProductId', 'wmsSkuId', 'erpProductId', 'erpSkuId', 'erpInventoryStatus',
]);

function comparable(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(comparable);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).filter(([key]) =>
      !dynamicFields.has(key) && !/^(erp|wms)/i.test(key),
    ).sort(([left], [right]) => left.localeCompare(right)).map(([key, child]) => [key, comparable(child)]));
  }
  return value;
}

export function snapshotChanges(current: MallSpuApi.Spu, previous: MallSpuApi.Spu | null) {
  const left = previous as Record<string, unknown> | null;
  const right = current as Record<string, unknown>;
  return Object.entries(contentLabels).flatMap(([key, label]) => {
    const before = comparable(left?.[key]);
    const after = comparable(right[key]);
    if (JSON.stringify(before) === JSON.stringify(after)) return [];
    return [{ key, label, before: displaySnapshotValue(before), after: displaySnapshotValue(after) }];
  });
}

export function displaySnapshotValue(value: unknown): string {
  if (value === undefined || value === null || value === '') return '—';
  return typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value);
}

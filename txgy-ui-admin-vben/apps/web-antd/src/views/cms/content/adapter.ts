import type { CmsApiType, CmsRecord } from './config';

import { requestClient } from '#/api/request';

/** 把后端返回的行转换为前端表格行（字段名对齐 config.ts 的列定义） */
type RowMapper = (data: any) => CmsRecord;

function fmtTime(value: any): string {
  if (!value) return '';
  if (Array.isArray(value)) return value.join('-');
  if (typeof value === 'number') {
    const d = new Date(value);
    const p = (n: number) => String(n).padStart(2, '0')!;
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
  }
  return String(value).slice(0, 10);
}

function fmtArray(value: any): string {
  if (Array.isArray(value)) return value.join('/');
  return value ?? '';
}

/** 审核状态 + 发布状态 → 中文展示状态 */
function toStatus(auditStatus: any, publishStatus: any): string {
  if (publishStatus === 1) return '已发布';
  switch (auditStatus) {
    case 0: return '待审核';
    case 2: return '已驳回';
    case 3: return '草稿';
    case 1: return '待发布';
    default: return '草稿';
  }
}

/** 中文展示状态 → 提交用的状态字段 */
export function fromStatus(status: string): { auditStatus: number; publishStatus: number } {
  switch (status) {
    case '已发布': return { auditStatus: 1, publishStatus: 1 };
    case '待发布': return { auditStatus: 1, publishStatus: 0 };
    case '待审核': return { auditStatus: 0, publishStatus: 0 };
    case '已驳回': return { auditStatus: 2, publishStatus: 0 };
    default: return { auditStatus: 3, publishStatus: 0 };
  }
}

const rowMappers: Record<CmsApiType, RowMapper> = {
  standard: (d) => ({
    id: d.standardId,
    title: d.standardName,
    type: '标准',
    status: toStatus(d.auditStatus, d.publishStatus),
    updatedAt: fmtTime(d.updateTime ?? d.createTime),
    content: d.summary ?? '',
    attachmentUrls: d.coverImage ? [d.coverImage] : [],
  }),
  policy: (d) => ({
    id: d.policyId,
    title: d.title,
    type: d.content_type === '法律法规' ? '法律法规' : '金融政策',
    status: toStatus(d.auditStatus, d.publishStatus),
    updatedAt: fmtTime(d.updateTime ?? d.createTime),
    content: d.content ?? d.summary ?? '',
    attachmentUrls: d.attachmentUrls ?? [],
    sourceUrl: d.sourceUrl,
  }),
};

const base = '/cms';

export async function fetchCmsRows(apiType: CmsApiType): Promise<CmsRecord[]> {
  const data = await requestClient.get<any>(`${base}/${apiType}/page`, {
    params: { pageNo: 1, pageSize: 100 },
  });
  const list = data?.list ?? [];
  return list.map(rowMappers[apiType]);
}

/** 详情接口：单独查全文（列表接口已排除 content 大字段） */
export async function fetchCmsDetail(apiType: CmsApiType, id: number): Promise<CmsRecord | null> {
  const d = await requestClient.get<any>(`${base}/${apiType}/get`, {
    params: { id },
  });
  return d ? rowMappers[apiType](d) : null;
}

/** 按内容类型查政策库（二期两大类：法律法规/金融政策） */
export async function fetchPolicyRows(contentType: 'law' | 'finance'): Promise<CmsRecord[]> {
  const contentTypeName = contentType === 'law' ? '法律法规' : '金融政策';
  const d = await requestClient.get<any>(`${base}/policy/page`, {
    params: { pageNo: 1, pageSize: 100, contentType: contentTypeName },
  });
  return (d?.list ?? []).map(rowMappers.policy);
}

export async function createCmsRow(apiType: CmsApiType, form: Record<string, any>) {
  return requestClient.post<number>(`${base}/${apiType}/create`, toPayload(apiType, form));
}

export async function updateCmsRow(apiType: CmsApiType, form: Record<string, any>, id: number) {
  return requestClient.put<boolean>(`${base}/${apiType}/update`, toPayload(apiType, form, id));
}

export async function deleteCmsRow(apiType: CmsApiType, id: number) {
  return requestClient.delete<boolean>(`${base}/${apiType}/delete?id=${id}`);
}

/** 提交审核：草稿/驳回 → 待审核 */
export async function submitCmsAudit(apiType: CmsApiType, id: number) {
  return requestClient.put<boolean>(`${base}/content/audit`, {
    auditRemark: '',
    auditStatus: 0,
    contentId: id,
    contentType: apiType,
  });
}

const pkField: Record<CmsApiType, string> = {
  material: 'materialId',
  policy: 'policyId',
  process: 'resourceId',
  quota: 'quotaId',
  standard: 'standardId',
};

/** 表单模型（中文状态等）→ 后端 SaveReqVO */
function toPayload(apiType: CmsApiType, form: Record<string, any>, id?: number): Record<string, any> {
  const { status, ...rest } = form;
  const state = fromStatus(status ?? '草稿');
  const payload: Record<string, any> = { ...rest, ...state };
  if (id) payload[pkField[apiType]] = id;
  ['standardType', 'materialCategory', 'resourceType', 'difficulty', 'quotaType', 'policyLevel'].forEach((k) => {
    if (payload[k] !== undefined && payload[k] !== '') payload[k] = Number(payload[k]);
  });
  ['industryField', 'protectObject', 'scene'].forEach((k) => {
    if (typeof payload[k] === 'string' && payload[k].trim()) {
      payload[k] = payload[k].split(/[,，/]/).map((s: string) => s.trim()).filter(Boolean);
    } else if (payload[k] === '') {
      payload[k] = [];
    }
  });
  if (apiType === 'standard') {
    payload.standardName = payload.name;
    delete payload.name;
  } else if (apiType === 'material') {
    payload.materialName = payload.name;
    payload.materialSubcategory = payload.subcategory;
    payload.applicableCondition = payload.applicableCondition || 'general';
    delete payload.name;
    delete payload.subcategory;
  } else if (apiType === 'process') {
    payload.content = payload.content || payload.summary || '<p>-</p>';
  } else if (apiType === 'policy') {
    payload.publishDept = payload.department;
    payload.content = payload.content || payload.summary || '-';
    delete payload.department;
  }
  return payload;
}

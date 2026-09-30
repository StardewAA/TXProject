import { requestClient } from '#/api/request';

export interface ContentPage<T> {
  list: T[];
  total: number;
}

export interface CmsCategory {
  children?: CmsCategory[];
  code: string;
  contentType?: string;
  id: number;
  name: string;
  parentId: number;
  remark?: string;
  sort: number;
  status: number;
  visible: boolean;
  visibleRoleCodes?: string;
}

export interface CmsAsset {
  assetType: string;
  categoryId?: number;
  createTime?: string;
  creator?: string;
  id: number;
  mime?: string;
  name: string;
  size?: number;
  tags?: string;
  url: string;
  usageCount: number;
}

export interface CmsRecommendSlot {
  code: string;
  contentId?: number;
  contentType?: string;
  endTime?: string;
  id: number;
  imageUrl?: string;
  name: string;
  position: string;
  remark?: string;
  sort: number;
  startTime?: string;
  status: number;
  targetUrl?: string;
  title?: string;
}

export interface CmsSeoMeta {
  canonicalUrl?: string;
  contentId?: number;
  contentType?: string;
  id?: number;
  seoDescription?: string;
  seoKeywords?: string;
  seoTitle?: string;
}

export interface CmsReviewTask {
  assigneeId?: number;
  assigneeName?: string;
  createTime?: string;
  dueTime?: string;
  finishedTime?: string;
  id: number;
  overdue?: boolean;
  priority: number;
  remark?: string;
  reviewId: string;
  status: string;
  title?: string;
}

// ---------- 栏目树 ----------
export function getCategoryTree() {
  return requestClient.get<CmsCategory[]>('/cms/content-ext/category/tree');
}

export function saveCategory(data: Partial<CmsCategory> & { visibleRoleCodes?: string[] | string }) {
  return requestClient.post<number>('/cms/content-ext/category/save', data);
}

export function deleteCategory(id: number) {
  return requestClient.delete<boolean>('/cms/content-ext/category/delete', { params: { id } });
}

// ---------- 素材库 ----------
export function getAssetPage(params: Record<string, any>) {
  return requestClient.get<ContentPage<CmsAsset>>('/cms/content-ext/asset/page', { params });
}

export function saveAsset(data: Partial<CmsAsset>) {
  return requestClient.post<number>('/cms/content-ext/asset/save', data);
}

export function deleteAsset(id: number) {
  return requestClient.delete<boolean>('/cms/content-ext/asset/delete', { params: { id } });
}

// ---------- 推荐位 ----------
export function getRecommendPage(params: Record<string, any>) {
  return requestClient.get<ContentPage<CmsRecommendSlot>>('/cms/content-ext/recommend/page', { params });
}

export function saveRecommend(data: Partial<CmsRecommendSlot>) {
  return requestClient.post<number>('/cms/content-ext/recommend/save', data);
}

export function deleteRecommend(id: number) {
  return requestClient.delete<boolean>('/cms/content-ext/recommend/delete', { params: { id } });
}

// ---------- SEO ----------
export function getSeoMeta(contentType: string, contentId: number) {
  return requestClient.get<CmsSeoMeta>('/cms/content-ext/seo/get', {
    params: { contentId, contentType },
  });
}

export function saveSeoMeta(data: CmsSeoMeta) {
  return requestClient.post<number>('/cms/content-ext/seo/save', data);
}

// ---------- 审查任务 ----------
export function getReviewTaskPage(params: Record<string, any>) {
  return requestClient.get<ContentPage<CmsReviewTask>>('/cms/content-ext/review-task/page', { params });
}

export function assignReviewTask(data: {
  assigneeId?: number;
  assigneeName?: string;
  dueTime?: string;
  priority?: number;
  remark?: string;
  reviewId: string;
  title?: string;
}) {
  return requestClient.post<number>('/cms/content-ext/review-task/assign', data);
}

export function completeReviewTask(taskId: number) {
  return requestClient.post<boolean>('/cms/content-ext/review-task/complete', null, { params: { taskId } });
}

export function scanOverdueTask() {
  return requestClient.get<number>('/cms/content-ext/review-task/overdue-scan');
}
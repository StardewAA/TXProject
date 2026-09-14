import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

/** 资源中心（文档库/视频库/题库）接口定义 */

export namespace TrainingResourceApi {
  /** 资源分类树节点 */
  export interface CategoryNode {
    id?: number;
    parentId?: number;
    code?: string;
    name?: string;
    sort?: number;
    level?: number;
    children?: CategoryNode[];
  }

  /** 分类保存 */
  export interface CategorySave {
    id?: number;
    /** 分类类型：doc 文档 / video 视频 / question 试题 */
    type?: string;
    name?: string;
    /** 父分类 ID（0=根，最多支持三级） */
    parentId?: number;
    sort?: number;
  }

  /** 资源标签 */
  export interface ResourceTag {
    id?: number;
    name?: string;
    dimension?: string;
    sort?: number;
    createTime?: string;
  }

  /** 文档资源 */
  export interface DocumentResource {
    id?: number;
    title?: string;
    categoryId?: number;
    fileName?: string;
    filePath?: string;
    fileType?: string;
    fileSize?: number;
    /** 文档总页数（上传时自动识别） */
    pageCount?: number;
    bizCategory?: string;
    tags?: string;
    estMinutes?: number;
    /** 状态：1 草稿 2 上线 3 下架 4 归档 */
    status?: number;
    viewCount?: number;
    readSeconds?: number;
    uploaderName?: string;
    createTime?: string;
  }

  /** 视频资源 */
  export interface VideoResource {
    id?: number;
    title?: string;
    categoryId?: number;
    /** 视频类型：1 短视频 2 录播 */
    videoType?: number;
    coverUrl?: string;
    fileName?: string;
    filePath?: string;
    fileType?: string;
    fileSize?: number;
    durationSeconds?: number;
    /** 原始清晰度：2160P / 1080P / 720P / 480P / 360P */
    definition?: string;
    /** 可播放清晰度列表（JSON 字符串） */
    definitionOptions?: string;
    /** 转码状态：0 未转码 1 排队 2 处理中 3 成功 4 失败 */
    transcodeStatus?: number;
    creditHours?: number;
    tags?: string;
    /** 状态：1 草稿 2 上线 3 下架 4 归档 */
    status?: number;
    viewCount?: number;
    uploaderName?: string;
    createTime?: string;
  }

  /** 试题 */
  export interface Question {
    id?: number;
    /** 题型：1 单选 2 多选 3 判断 4 实操问答 5 案例分析 */
    qtype?: number;
    stem?: string;
    optionsJson?: string;
    answer?: string;
    analysis?: string;
    /** 难度：1 易 2 中 3 难 */
    difficulty?: number;
    /** 所属分类 ID（资源分类 question 类型，支持三级） */
    categoryId?: number;
    /** 状态：1 启用 2 停用 */
    status?: number;
    creatorName?: string;
    tagIds?: number[];
    tagNames?: string;
    createTime?: string;
  }

  /** 文档页数识别结果 */
  export interface DocumentPageCount {
    pageCount?: number;
    estMinutes?: number;
    minutesPerPage?: number;
    precise?: boolean;
  }

  /** 操作日志 */
  export interface OperationLog {
    id?: number;
    userId?: number;
    userName?: string;
    action?: string;
    targetType?: string;
    targetId?: number;
    detail?: string;
    createTime?: string;
  }

  /** 视频清晰度选项（弱网可切换低清晰度） */
  export interface VideoDefinitionOption {
    definition?: string;
    url?: string;
    /** 是否已完成转码（false 表示暂按原始清晰度播放） */
    transcoded?: boolean;
    original?: boolean;
  }

  /** AI 识别的试题项 */
  export interface AiQuestionItem {
    qtype?: number;
    stem?: string;
    options?: { key?: string; text?: string; isCorrect?: boolean }[];
    answer?: string;
    analysis?: string;
    difficulty?: number;
    confidence?: number;
  }

  /** AI 文档解析结果（预览） */
  export interface AiQuestionParseResult {
    items?: AiQuestionItem[];
    aiEnabled?: boolean;
    fallback?: boolean;
    message?: string;
    textLength?: number;
    fileName?: string;
    fileType?: string;
  }
}

// ========== 资源分类 ==========

/** 分类树 */
export function getResourceCategoryTree(type: string) {
  return requestClient.get<TrainingResourceApi.CategoryNode[]>(
    `/training/resource-category/tree?type=${type}`,
  );
}

/** 新增分类 */
export function createResourceCategory(data: TrainingResourceApi.CategorySave) {
  return requestClient.post<number>('/training/resource-category/create', data);
}

/** 修改分类 */
export function updateResourceCategory(data: TrainingResourceApi.CategorySave) {
  return requestClient.put<boolean>('/training/resource-category/update', data);
}

/** 删除分类 */
export function deleteResourceCategory(id: number) {
  return requestClient.delete<boolean>(`/training/resource-category/delete?id=${id}`);
}

// ========== 资源标签 ==========

/** 标签列表（按维度过滤） */
export function getResourceTagList(dimension?: string) {
  const query = dimension ? `?dimension=${dimension}` : '';
  return requestClient.get<TrainingResourceApi.ResourceTag[]>(
    `/training/resource-tag/list${query}`,
  );
}

/** 标签分页 */
export function getResourceTagPage(params: PageParam & Record<string, any>) {
  return requestClient.get<PageResult<TrainingResourceApi.ResourceTag>>(
    '/training/resource-tag/page',
    { params },
  );
}

/** 新增标签 */
export function createResourceTag(data: Partial<TrainingResourceApi.ResourceTag>) {
  return requestClient.post<number>('/training/resource-tag/create', data);
}

/** 修改标签 */
export function updateResourceTag(data: Partial<TrainingResourceApi.ResourceTag>) {
  return requestClient.put<boolean>('/training/resource-tag/update', data);
}

/** 删除标签 */
export function deleteResourceTag(id: number) {
  return requestClient.delete<boolean>(`/training/resource-tag/delete?id=${id}`);
}

// ========== 文档资源库 ==========

/** 文档分页 */
export function getDocumentPage(params: PageParam & Record<string, any>) {
  return requestClient.get<PageResult<TrainingResourceApi.DocumentResource>>(
    '/training/document/page',
    { params },
  );
}

/** 上传文档（multipart，file + 元数据） */
export function uploadDocument(data: FormData) {
  return requestClient.post<number>('/training/document/upload', data, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/** 识别文档页数（自动换算预估阅读时长 = 页数 × 10 分钟/页） */
export function countDocumentPages(file: File) {
  const data = new FormData();
  data.append('file', file);
  return requestClient.post<TrainingResourceApi.DocumentPageCount>(
    '/training/document/page-count',
    data,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  );
}

/** 修改文档元数据 */
export function updateDocument(params: Record<string, any>) {
  return requestClient.put<boolean>('/training/document/update', undefined, {
    params,
  });
}

/** 文档状态流转：ONLINE/OFFLINE/ARCHIVE/DRAFT */
export function changeDocumentStatus(id: number, action: string) {
  return requestClient.post<boolean>(
    `/training/document/status?id=${id}&action=${action}`,
  );
}

/** 阅读上报 */
export function reportDocumentReading(id: number, seconds?: number) {
  const query = seconds ? `id=${id}&seconds=${seconds}` : `id=${id}`;
  return requestClient.post<boolean>(`/training/document/reading?${query}`);
}

/** 删除文档 */
export function deleteDocument(id: number) {
  return requestClient.delete<boolean>(`/training/document/delete?id=${id}`);
}

/** 文档在线预览地址（iframe 直出） */
export function buildDocumentPreviewUrl(id: number) {
  return `/admin-api/training/document/preview?id=${id}`;
}

// ========== 视频资源库 ==========

/** 视频分页 */
export function getVideoPage(params: PageParam & Record<string, any>) {
  return requestClient.get<PageResult<TrainingResourceApi.VideoResource>>(
    '/training/video/page',
    { params },
  );
}

/** 上传视频（multipart） */
export function uploadVideo(data: FormData) {
  return requestClient.post<number>('/training/video/upload', data, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/** 修改视频元数据 */
export function updateVideo(params: Record<string, any>) {
  return requestClient.put<boolean>('/training/video/update', undefined, {
    params,
  });
}

/** 视频状态流转：ONLINE/OFFLINE/ARCHIVE/DRAFT */
export function changeVideoStatus(id: number, action: string) {
  return requestClient.post<boolean>(
    `/training/video/status?id=${id}&action=${action}`,
  );
}

/** 标记转码成功（易版占位） */
export function markVideoTranscodeSuccess(id: number) {
  return requestClient.post<boolean>(`/training/video/transcode?id=${id}`);
}

/** 配置学时值 */
export function setVideoCreditHours(id: number, creditHours: number) {
  return requestClient.post<boolean>(
    `/training/video/credit-hours?id=${id}&creditHours=${creditHours}`,
  );
}

/** 删除视频 */
export function deleteVideo(id: number) {
  return requestClient.delete<boolean>(`/training/video/delete?id=${id}`);
}

/** 视频播放地址（Range 流） */
export function buildVideoPlayUrl(id: number) {
  return `/admin-api/training/video/play?id=${id}`;
}

// ========== 考试题库 ==========

/** 试题分页 */
export function getQuestionPage(params: PageParam & Record<string, any>) {
  return requestClient.get<PageResult<TrainingResourceApi.Question>>(
    '/training/question/page',
    { params },
  );
}

/** 试题详情 */
export function getQuestion(id: number) {
  return requestClient.get<TrainingResourceApi.Question>(
    `/training/question/get?id=${id}`,
  );
}

/** 新增试题 */
export function createQuestion(data: Partial<TrainingResourceApi.Question>) {
  return requestClient.post<number>('/training/question/create', data);
}

/** 修改试题 */
export function updateQuestion(data: Partial<TrainingResourceApi.Question>) {
  return requestClient.put<boolean>('/training/question/update', data);
}

/** 试题启用/停用 */
export function changeQuestionStatus(id: number, action: string) {
  return requestClient.post<boolean>(
    `/training/question/status?id=${id}&action=${action}`,
  );
}

/** 删除试题 */
export function deleteQuestion(id: number) {
  return requestClient.delete<boolean>(`/training/question/delete?id=${id}`);
}

/** Excel 批量导入试题 */
export function importQuestions(file: File) {
  const data = new FormData();
  data.append('file', file);
  return requestClient.post<{
    successCount?: number;
    errors?: { rowNo: number; error: string }[];
  }>('/training/question/import', data, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

// ========== AI 智能导入（文档 → 试题）==========

/** AI 解析文档试题（仅预览，不入库） */
export function parseQuestionByAi(file: File) {
  const data = new FormData();
  data.append('file', file);
  return requestClient.post<TrainingResourceApi.AiQuestionParseResult>(
    '/training/question/ai/parse',
    data,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  );
}

/** AI 试题导入确认参数（整批统一分类与标签） */
export interface AiQuestionImportReq {
  items: TrainingResourceApi.AiQuestionItem[];
  categoryId?: number;
  tagIds?: number[];
}

/** 确认导入 AI 识别的试题（整批统一归入所选分类、打上所选标签） */
export function importQuestionsByAi(data: AiQuestionImportReq) {
  return requestClient.post<number>('/training/question/ai/import', data);
}

// ========== 视频清晰度 ==========

/** 查询可播放清晰度列表（弱网可切换低清晰度） */
export function getVideoDefinitionOptions(id: number) {
  return requestClient.get<TrainingResourceApi.VideoDefinitionOption[]>(
    `/training/video/definitions?id=${id}`,
  );
}

// ========== 操作日志 ==========

/** 操作日志分页 */
export function getOperationLogPage(params: PageParam & Record<string, any>) {
  return requestClient.get<PageResult<TrainingResourceApi.OperationLog>>(
    '/training/resource-log/page',
    { params },
  );
}

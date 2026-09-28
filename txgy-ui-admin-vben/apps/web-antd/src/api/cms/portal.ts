import { requestClient } from '#/api/request';

export interface PortalOverview {
  experts: number;
  financeChunks: number;
  financeDocuments: number;
  lawChunks: number;
  lawDocuments: number;
  standards: number;
}

/** 门户概览：知识库规模统计 */
export function getPortalOverview() {
  return requestClient.get<PortalOverview>('/cms/portal/overview');
}

export interface PortalHighlightItem {
  contentType?: string;
  date?: string;
  department?: string;
  id: number;
  title: string;
}

export interface PortalHighlights {
  notices: PortalHighlightItem[];
  policies: PortalHighlightItem[];
}

/** 门户首页：通知公告与政策动态 */
export function getPortalHighlights() {
  return requestClient.get<PortalHighlights>('/cms/portal/highlights');
}

export interface AiModelOption {
  id: number;
  model: string;
  name: string;
  platform: string;
}

export interface AiChatHistoryItem {
  answer: string;
  question: string;
}

export interface AiChatResult {
  answer: string;
  model: string;
  modelName?: string;
}

/** 门户 AI 助手：可选大模型列表 */
export function getAiModels() {
  return requestClient.get<AiModelOption[]>('/cms/portal/ai/models');
}

/** 门户 AI 助手：大模型直答（无知识库引用） */
export function chatWithModel(
  question: string,
  modelId?: number,
  history: AiChatHistoryItem[] = [],
) {
  return requestClient.post<AiChatResult>(
    '/cms/portal/ai/chat',
    { history: history.slice(-3), modelId, question },
    { timeout: 120_000 },
  );
}

export interface PortalSectionItem {
  category?: string;
  imageCount?: number;
  name: string;
  size?: number;
  tags?: string;
  title: string;
  url: string;
}

export interface PortalHomeSections {
  about: PortalSectionItem[];
  banners: PortalSectionItem[];
  news: PortalSectionItem[];
  notices: PortalSectionItem[];
  products: PortalSectionItem[];
  techDocs: PortalSectionItem[];
}

/** 门户首页内容区块（推荐位/产品/新闻/公告/关于我们/技术资料） */
export function getPortalHomeSections() {
  return requestClient.get<PortalHomeSections>('/cms/portal/home-sections');
}

export interface PortalLibraryItem {
  date?: string;
  docCategory?: string;
  issuer?: string;
  reviewId?: string;
  sourceUrl?: string;
  title: string;
}

/** 门户法规库 / 政策库：按类型列出知识库文档（无需关键词） */
export function getPortalLibrary(docCategory: 'FINANCE_POLICY' | 'LAW' = 'LAW', limit = 6) {
  return requestClient.get<PortalLibraryItem[]>('/cms/portal/law/library', {
    params: { docCategory, limit },
  });
}

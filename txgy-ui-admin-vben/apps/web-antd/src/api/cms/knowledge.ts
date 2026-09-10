import { requestClient } from '#/api/request';

export interface CmsKnowledgeItem {
  chunkCount: number;
  createdAt: string;
  published: boolean;
  reviewId: string;
  sourceUrl: string;
  textHash: string;
  title: string;
  validity: string;
}

export interface CmsPublishResult {
  keywordChunks: number;
  published: boolean;
  reviewId: string;
  vectorRebuildRequired: boolean;
}

export interface CmsReindexResult {
  embeddingModel: string;
  keywordChunks: number;
  vectorChunks: number;
}

export const listCmsKnowledge = () =>
  requestClient.get<CmsKnowledgeItem[]>('/cms/knowledge/list');

export const publishCmsKnowledge = (reviewId: string) =>
  requestClient.post<CmsPublishResult>('/cms/knowledge/publish', undefined, {
    params: { reviewId },
    timeout: 120_000,
  });

export const unpublishCmsKnowledge = (reviewId: string) =>
  requestClient.delete<boolean>('/cms/knowledge/unpublish', {
    params: { reviewId },
  });

export const reindexCmsKnowledge = () =>
  requestClient.post<CmsReindexResult>('/cms/knowledge/reindex', undefined, {
    timeout: 600_000,
  });

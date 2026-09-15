import { requestClient } from '#/api/request';

import type { ReviewDetail, ReviewMetadata, ReviewReport } from './review';

export interface PublicationSubmission {
  published: boolean;
  report: ReviewReport;
}

export interface PublicationUrlParseResult {
  llmExtracted: boolean;
  metadata: ReviewMetadata;
  text: string;
  warning?: string;
}

export const parsePublicationUrl = (url: string, portal = false) =>
  requestClient.post<PublicationUrlParseResult>(portal ? '/cms/portal/submission/url/parse' : '/cms/publication/url/parse', { url }, { timeout: 120_000 });

export const checkPublicationDuplicate = (metadata: ReviewMetadata, text: string) =>
  requestClient.post<{ duplicate: boolean; matchedReviewId?: string; reason?: string }>(
    '/cms/portal/submission/duplicate-check', { metadata, text },
  );

export const submitPublicationText = (metadata: ReviewMetadata, text: string) =>
  requestClient.post<ReviewReport>('/cms/portal/submission/text', {
    metadata,
    text,
  });

export const getMyPublicationSubmission = (id: string) =>
  requestClient.get<ReviewDetail>('/cms/portal/submission/get', {
    params: { id },
  });

export const resubmitPublicationText = (
  id: string,
  metadata: ReviewMetadata,
  text: string,
) =>
  requestClient.post<ReviewReport>(
    '/cms/portal/submission/resubmit',
    { metadata, text },
    { params: { id } },
  );

export function submitPublicationFile(metadata: ReviewMetadata, file: File) {
  const data = new FormData();
  data.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }));
  data.append('file', file);
  return requestClient.post<ReviewReport>('/cms/portal/submission/upload', data, {
    timeout: 120_000,
  });
}

export const listMyPublicationSubmissions = (limit = 100) =>
  requestClient.get<PublicationSubmission[]>('/cms/portal/submission/mine', {
    params: { limit },
  });

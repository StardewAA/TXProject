import { requestClient } from '#/api/request';

import type { ReviewMetadata, ReviewReport } from './review';

export interface PublicationSubmission {
  published: boolean;
  report: ReviewReport;
}

export const submitPublicationText = (metadata: ReviewMetadata, text: string) =>
  requestClient.post<ReviewReport>('/cms/portal/submission/text', {
    metadata,
    text,
  });

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

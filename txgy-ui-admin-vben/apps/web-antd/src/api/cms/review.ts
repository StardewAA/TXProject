import { requestClient } from '#/api/request';

export type Validity = 'EFFECTIVE' | 'NOT_YET_EFFECTIVE' | 'REPEALED' | 'EXPIRED' | 'UNKNOWN';
export interface ReviewMetadata {
  title: string;
  documentNo: string;
  issuer: string;
  sourceUrl: string;
  kind: 'LAW' | 'STANDARD' | 'POLICY';
  validity: Validity;
  effectiveDate: string | null;
  expiryDate: string | null;
  historical: boolean;
}
export interface Evidence {
  officialMetadata: ReviewMetadata;
  officialTextHash: string;
  validitySourceUrl: string;
  note: string;
  reviewerId: number;
  checkedOn: string;
}
export interface ReviewReport {
  id: string;
  createdAt: string;
  metadata: ReviewMetadata;
  textHash: string;
  decision: 'PASS' | 'WARNING' | 'FAIL';
  score: number;
  scores: Record<string, number>;
  issues: { dimension: string; severity: 'WARNING' | 'ERROR'; code: string; location: string; message: string; suggestion: string }[];
  evidence?: Evidence;
  admitted: boolean;
  chunkCount: number;
}
export interface ReviewDetail {
  report: ReviewReport;
  text: string;
  filename: string;
  evidenceHistory: Evidence[];
  chunks: { ordinal: number; startOffset: number; endOffset: number; text: string }[];
}
export interface BatchReviewItem {
  file: File;
  metadata: ReviewMetadata;
}
export interface BatchReviewResult {
  batchId: string;
  total: number;
  items: { fileName: string; report: ReviewReport }[];
}
export const listReviews = () => requestClient.get<ReviewReport[]>('/cms/review/list');
export const getReview = (id: string) => requestClient.get<ReviewDetail>('/cms/review/get', { params: { id } });
export const submitText = (metadata: ReviewMetadata, text: string, previousId?: string) =>
  requestClient.post<ReviewReport>(previousId ? '/cms/review/resubmit' : '/cms/review/text', { metadata, text }, { params: previousId ? { id: previousId } : {} });
export function uploadReview(metadata: ReviewMetadata, file: File) {
  const data = new FormData();
  data.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }));
  data.append('file', file);
  return requestClient.post<ReviewReport>('/cms/review/upload', data, { timeout: 120000 });
}
export function batchUploadReviews(items: BatchReviewItem[]) {
  const data = new FormData();
  const manifest = { items: items.map(({ file, metadata }) => ({ fileName: file.name, metadata })) };
  data.append('manifest', new Blob([JSON.stringify(manifest)], { type: 'application/json' }));
  for (const item of items) data.append('files', item.file, item.file.name);
  return requestClient.post<BatchReviewResult>('/cms/review/batch-upload', data, { timeout: 300000 });
}
export const verifyReview = (id: string, officialMetadata: ReviewMetadata, officialText: string, validitySourceUrl: string, note: string) =>
  requestClient.put<ReviewReport>('/cms/review/verify', { officialMetadata, officialText, validitySourceUrl, note }, { params: { id }, timeout: 120000 });

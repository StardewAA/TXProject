import type { ReviewDetail, ReviewMetadata, ReviewReport } from '../../apps/web-antd/src/api/cms/review';
const records = new Map<string, ReviewDetail>();
export async function listReviews() { return [...records.values()].map((d) => d.report); }
export async function getReview(id: string) { return records.get(id)!; }
export async function submitText(metadata: ReviewMetadata, text: string) {
  const id = crypto.randomUUID();
  const report: ReviewReport = { id, createdAt: new Date().toISOString(), metadata: { ...metadata }, textHash: 'preview', decision: 'FAIL', score: 87, scores: { ENCODING: 100, AUTHENTICITY: 50, FORMAT: 100, QUALITY: 100 }, admitted: false, chunkCount: 0, issues: [{ code: 'SOURCE_UNVERIFIED', dimension: 'AUTHENTICITY', severity: 'ERROR', location: '来源与效力', message: '尚无官方核验依据（模拟）', suggestion: '由核验人员对照官方原文后登记依据。' }] };
  records.set(id, { report, text, filename: '预览.txt', evidenceHistory: [], chunks: [] });
  return report;
}
export async function uploadReview(metadata: ReviewMetadata, file: File) { return submitText(metadata, await file.text()); }
export async function verifyReview(id: string, officialMetadata: ReviewMetadata, officialText: string, validitySourceUrl: string, note: string) {
  const detail = records.get(id)!;
  if (officialText === detail.text && officialMetadata.validity === 'EFFECTIVE') {
    detail.report = { ...detail.report, score: 100, decision: 'PASS', scores: { ENCODING: 100, AUTHENTICITY: 100, FORMAT: 100, QUALITY: 100 }, issues: [], admitted: true, chunkCount: 1, evidence: { officialMetadata, officialTextHash: 'preview', validitySourceUrl, note, reviewerId: 2, checkedOn: '2026-09-08' } };
    detail.chunks = [{ ordinal: 1, startOffset: 0, endOffset: officialText.length, text: officialText }];
  }
  return detail.report;
}

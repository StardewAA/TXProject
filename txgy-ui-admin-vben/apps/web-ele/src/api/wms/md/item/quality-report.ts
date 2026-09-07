import { requestClient } from '#/api/request';

export enum QualityStatus {
  PENDING = 0,
  QUALIFIED = 1,
  ABNORMAL = 2,
}

export interface QualityReport {
  id?: number;
  itemId?: number;
  status: QualityStatus;
  imageUrls: string[];
  remark?: string;
  current?: boolean;
  creator?: string;
  creatorName?: string;
  createTime?: string;
}

export function createQualityReport(data: QualityReport) {
  return requestClient.post<number>('/wms/item-quality-report/create', data);
}

export function getQualityReportList(itemId: number) {
  return requestClient.get<QualityReport[]>('/wms/item-quality-report/list', {
    params: { itemId },
  });
}

export function getCurrentQualityReports(itemIds: number[]) {
  if (itemIds.length === 0) return Promise.resolve([] as QualityReport[]);
  return requestClient.get<QualityReport[]>(
    '/wms/item-quality-report/current-batch',
    { params: { itemIds: itemIds.join(',') } },
  );
}

import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace ErpRecognitionApi {
  export type DateTimeValue = string | number[] | Date;

  export interface Item {
    productName?: string;
    productCode?: string;
    productId?: number;
    productUnitId?: number;
    orderItemId?: number;
    warehouseId?: number;
    count?: number;
    productPrice?: number;
    taxPercent?: number;
    remark?: string;
    confidence?: number;
  }

  export interface Document {
    documentType?: string;
    documentNo?: string;
    documentDate?: string;
    orderNo?: string;
    returnTime?: string;
    accountId?: number;
    accountName?: string;
    discountPercent?: number;
    supplierName?: string;
    supplierId?: number;
    customerName?: string;
    customerId?: number;
    saleUserId?: number;
    contact?: string;
    mobile?: string;
    telephone?: string;
    email?: string;
    fax?: string;
    taxNo?: string;
    taxPercent?: number;
    bankName?: string;
    bankAccount?: string;
    bankAddress?: string;
    remark?: string;
    finalConfidence?: number;
    items?: Item[];
  }

  export interface Validation {
    passed?: boolean;
    errors?: string[];
    warnings?: string[];
  }

  export interface Task {
    id?: number;
    bizType?: string;
    status?: string;
    sourceFileUrl?: string;
    documentType?: string;
    providerType?: string;
    finalConfidence?: number;
    errorMessage?: string;
    resultVersion?: number;
    importedTargetId?: number;
    startedAt?: DateTimeValue;
    finishedAt?: DateTimeValue;
    reviewer?: string;
    createTime?: DateTimeValue;
    document?: Document;
    validation?: Validation;
  }

  export interface TaskCreate {
    bizType: string;
    sourceFileUrl: string;
  }

  export interface ResultCorrect {
    taskId: number;
    resultVersion: number;
    documentNo?: string;
    documentDate?: string;
    orderNo?: string;
    returnTime?: string;
    accountId?: number;
    discountPercent?: number;
    supplierId?: number;
    supplierName?: string;
    customerId?: number;
    customerName?: string;
    saleUserId?: number;
    contact?: string;
    mobile?: string;
    telephone?: string;
    email?: string;
    fax?: string;
    taxNo?: string;
    taxPercent?: number;
    bankName?: string;
    bankAccount?: string;
    bankAddress?: string;
    remark?: string;
    items: Item[];
  }

  export interface TaskAction {
    taskId: number;
    resultVersion: number;
  }
}

export function getRecognitionTaskPage(params: PageParam) {
  return requestClient.get<PageResult<ErpRecognitionApi.Task>>(
    '/erp/recognition/task/page',
    { params },
  );
}

export function getRecognitionTask(taskId: number) {
  return requestClient.get<ErpRecognitionApi.Task>(
    `/erp/recognition/task/get?taskId=${taskId}`,
  );
}

export function createRecognitionTask(data: ErpRecognitionApi.TaskCreate) {
  return requestClient.post<number>('/erp/recognition/task/create', data);
}

export function importRecognitionExcel(
  file: File,
  bizType: string,
  sourceFileUrl: string,
) {
  return requestClient.upload<number>('/erp/recognition/task/import-excel', {
    file,
    bizType,
    sourceFileUrl,
  });
}

export function downloadRecognitionTemplate(bizType: string) {
  const template =
    bizType === 'SALE_ORDER' ? 'sale-order' : 'purchase-order';
  return requestClient.download(`/erp/recognition/template/${template}`);
}

export function retryRecognitionTask(taskId: number) {
  return requestClient.post('/erp/recognition/task/retry', null, {
    params: { taskId },
  });
}

export function deleteRecognitionTask(taskId: number) {
  return requestClient.delete('/erp/recognition/task/delete', {
    params: { taskId },
  });
}

export function correctRecognitionResult(
  data: ErpRecognitionApi.ResultCorrect,
) {
  return requestClient.put('/erp/recognition/result/correct', data);
}

export function confirmRecognitionTask(data: ErpRecognitionApi.TaskAction) {
  return requestClient.post('/erp/recognition/task/confirm', data);
}

export function importRecognitionTask(data: ErpRecognitionApi.TaskAction) {
  return requestClient.post<number>('/erp/recognition/task/import', data);
}

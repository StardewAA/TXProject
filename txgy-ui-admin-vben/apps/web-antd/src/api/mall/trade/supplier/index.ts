import type { PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace SupplierTradeApi {
  export interface Shop {
    id: number;
    enterpriseId: number;
    name: string;
    status: number;
  }

  export interface OrderRow {
    id: number;
    no: string;
    status: number;
    payStatus: boolean;
    payPrice: number;
    refundPrice: number;
    deliveryType: number;
    createTime?: number | string;
    deliverable: boolean;
    deliveryBlockReason?: string;
    erpStatus?: string;
    wmsFailureReason?: string;
    wmsShipmentCount?: number;
    wmsStatus?: string;
  }

  export interface OrderItem {
    id: number;
    name: string;
    picUrl?: string;
    specification?: string;
    count: number;
    price: number;
    payPrice: number;
    afterSaleStatus: number;
  }

  export interface OrderDetail {
    order: OrderRow;
    receiverName?: string;
    receiverMobile?: string;
    address?: string;
    userRemark?: string;
    logisticsId?: number;
    logisticsNo?: string;
    deliveryTime?: number | string;
    deliverable: boolean;
    items: OrderItem[];
    wmsShipments?: WmsShipment[];
    erpInventory?: unknown[];
  }

  export interface WmsShipment {
    acceptedAt?: number | string;
    failureReason?: string;
    id: number;
    lastSyncedAt?: number | string;
    requestedAt?: number | string;
    sourceOrderNo?: string;
    status: string;
    targetOrderNo?: string;
    warehouseCode?: string;
    warehouseId?: number;
  }

  export interface Express {
    id: number;
    name: string;
  }

  export interface AfterSaleRow {
    id: number;
    no: string;
    orderId: number;
    orderNo: string;
    spuName: string;
    count: number;
    status: number;
    way: number;
    refundPrice: number;
    applyReason?: string;
    applyDescription?: string;
    auditReason?: string;
    logisticsId?: number;
    logisticsNo?: string;
    createTime?: number | string;
    processable: boolean;
    processBlockReason?: string;
    erpStatus?: string;
  }

  export interface AfterSaleDetail {
    row: AfterSaleRow;
    pictures?: string[];
    receiveReason?: string;
    returnReceiverName?: string;
    returnReceiverMobile?: string;
    returnAddress?: string;
    erpInventory?: unknown[];
  }

  export type AfterSaleAction = 'AGREE' | 'RECEIVE' | 'REFUSE' | 'REJECT';

  export interface SettlementBill {
    id: number;
    shopId: number;
    shopName: string;
    billMonth: string;
    no: string;
    version: number;
    status: number;
    commissionRate: number;
    orderCount: number;
    orderPayAmount: number;
    refundAmount: number;
    commissionAmount: number;
    adjustmentAmount: number;
    carryAmount: number;
    settlementAmount: number;
    auditRemark?: string;
    paymentReference?: string;
    paymentTime?: number | string;
  }

  export interface SettlementDetail {
    bill: SettlementBill;
    items: Array<{
      amount: number;
      commissionAmount: number;
      deliveryAmount: number;
      id: number;
      kind: number;
      orderNo: string;
      payAmount: number;
      refundTotal: number;
    }>;
    logs: Array<{
      action: string;
      createTime?: number | string;
      remark?: string;
      version: number;
    }>;
  }
}

const supplierPath = '/trade/supplier';
const settlementPath = '/trade/supplier-settlement';

/** 仅返回当前 system_user 获授权的店铺；服务端按实际业务权限再次求交。 */
export function getSupplierTradeShops() {
  return requestClient.get<SupplierTradeApi.Shop[]>(
    '/product/admin-business-scope/shops',
  );
}

export function getSupplierOrderPage(params: {
  no?: string;
  pageNo: number;
  pageSize: number;
  shopId: number;
  status?: number;
}) {
  return requestClient.get<PageResult<SupplierTradeApi.OrderRow>>(
    `${supplierPath}/order/page`,
    { params },
  );
}

export function getSupplierOrder(shopId: number, id: number) {
  return requestClient.get<SupplierTradeApi.OrderDetail>(
    `${supplierPath}/order/get`,
    { params: { id, shopId } },
  );
}

export function getSupplierExpressList(shopId: number) {
  return requestClient.get<SupplierTradeApi.Express[]>(
    `${supplierPath}/express/list`,
    { params: { shopId } },
  );
}

export function deliverSupplierOrder(data: {
  id: number;
  logisticsId: number;
  logisticsNo: string;
  shopId: number;
}) {
  return requestClient.put<boolean>(`${supplierPath}/order/deliver`, data);
}

export function getSupplierAfterSalePage(params: {
  no?: string;
  pageNo: number;
  pageSize: number;
  shopId: number;
  status?: number;
}) {
  return requestClient.get<PageResult<SupplierTradeApi.AfterSaleRow>>(
    `${supplierPath}/after-sale/page`,
    { params },
  );
}

export function getSupplierAfterSale(shopId: number, id: number) {
  return requestClient.get<SupplierTradeApi.AfterSaleDetail>(
    `${supplierPath}/after-sale/get`,
    { params: { id, shopId } },
  );
}

export function processSupplierAfterSale(data: {
  action: SupplierTradeApi.AfterSaleAction;
  id: number;
  reason?: string;
  shopId: number;
}) {
  return requestClient.put<boolean>(`${supplierPath}/after-sale/process`, data);
}

export function getSupplierSettlementPage(params: {
  pageNo: number;
  pageSize: number;
  shopId: number;
  status?: number;
}) {
  return requestClient.get<PageResult<SupplierTradeApi.SettlementBill>>(
    `${settlementPath}/page`,
    { params },
  );
}

export function getSupplierSettlement(shopId: number, id: number) {
  return requestClient.get<SupplierTradeApi.SettlementDetail>(
    `${settlementPath}/get`,
    { params: { id, shopId } },
  );
}

export function confirmSupplierSettlement(data: {
  id: number;
  shopId: number;
  version: number;
}) {
  return requestClient.put<boolean>(`${settlementPath}/confirm`, data);
}

export function getSupplierSettlementProof(shopId: number, id: number) {
  return requestClient.get<string>(`${settlementPath}/proof`, {
    params: { id, shopId },
  });
}

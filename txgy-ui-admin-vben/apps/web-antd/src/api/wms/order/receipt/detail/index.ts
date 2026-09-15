export namespace WmsReceiptOrderDetailApi {
  /** WMS 入库单明细 */
  export interface ReceiptOrderDetail {
    id?: number;
    orderId?: number;
    itemId?: number;
    itemCode?: string;
    itemName?: string;
    unit?: string;
    skuId?: number;
    skuCode?: string;
    skuName?: string;
    warehouseId?: number;
    warehouseName?: string;
    quantity?: number;
    actualQuantity?: number;
    qualifiedQuantity?: number;
    unqualifiedQuantity?: number;
    qualityImages?: string[];
    qualityRemark?: string;
    qualityRequiredSnapshot?: boolean | null;
    sourceSnapshot?: {
      materialNameSnapshot?: string;
      specificationSnapshot?: string;
      unitNameSnapshot?: string;
      batchNo?: string;
      qualityRequiredSnapshot?: boolean | null;
      attachments?: { url?: string; name?: string; fileUrl?: string; fileName?: string }[];
    };
    price?: number;
    totalPrice?: number;
    createTime?: Date;
  }
}

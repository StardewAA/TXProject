import { request } from './http'
import type { PageResult } from '@/types/mall'
export interface StockAdjust {
  shopId:number; spuId:number; skuId:number; expectedStock:number; delta:number; reason:string; requestKey:string
}
export interface StockEntry {
  id:number; skuId:number; memberId:number; delta:number; beforeStock:number; afterStock:number; reason:string; createTime:string|number
}
export const supplierStockApi = {
  adjust:(data:StockAdjust)=>request<StockEntry>({url:'/product/supplier-stock/adjust',method:'POST',data}),
  page:(shopId:number,spuId:number,pageNo=1)=>request<PageResult<StockEntry>>({url:'/product/supplier-stock/page',params:{shopId,spuId,pageNo,pageSize:10}}),
}

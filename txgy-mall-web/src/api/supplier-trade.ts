import { request } from './http'
import type { ErpEntry } from '@/components/ErpInventoryStatus.vue'
import type { PageResult } from '@/types/mall'
export interface SalesOrder { id: number; no: string; status: number; payStatus: boolean; payPrice: number; refundPrice: number; deliveryType: number; createTime: string; deliverable?: boolean; deliveryBlockReason?: string; erpStatus?: string }
export interface SalesOrderDetail { erpInventory?: ErpEntry[]; order: SalesOrder; receiverName: string; receiverMobile: string; address: string; userRemark: string; logisticsId?: number; logisticsNo?: string; deliveryTime?: string; deliverable: boolean; items: {id:number;name:string;picUrl:string;specification:string;count:number;price:number;payPrice:number;afterSaleStatus:number}[] }
export interface SalesAfterSale { id:number;no:string;orderId:number;orderNo:string;spuName:string;count:number;status:number;way:number;refundPrice:number;applyReason:string;applyDescription:string;auditReason:string;logisticsId?:number;logisticsNo?:string;createTime:string;processable?:boolean;processBlockReason?:string;erpStatus?:string }
const path='/trade/supplier'
export type SalesAfterSaleAction = 'AGREE' | 'REJECT' | 'RECEIVE' | 'REFUSE'
export interface SalesAfterSaleDetail { erpInventory?: ErpEntry[]; row:SalesAfterSale; pictures?:string[]; receiveReason?:string;returnReceiverName?:string;returnReceiverMobile?:string;returnAddress?:string }
export const supplierTradeApi={
 page:(params:Record<string,unknown>)=>request<PageResult<SalesOrder>>({url:path+'/order/page',params}),
 detail:(shopId:number,id:number)=>request<SalesOrderDetail>({url:path+'/order/get',params:{shopId,id}}),
 express:(shopId:number)=>request<{id:number;name:string}[]>({url:path+'/express/list',params:{shopId}}),
 deliver:(data:{shopId:number;id:number;logisticsId:number;logisticsNo:string})=>request<boolean>({url:path+'/order/deliver',method:'PUT',data}),
 afterSales:(params:Record<string,unknown>)=>request<PageResult<SalesAfterSale>>({url:path+'/after-sale/page',params}),
 afterSaleDetail:(shopId:number,id:number)=>request<SalesAfterSaleDetail>({url:path+'/after-sale/get',params:{shopId,id}}),
 processAfterSale:(data:{shopId:number;id:number;action:SalesAfterSaleAction;reason?:string})=>request<boolean>({url:path+'/after-sale/process',method:'PUT',data}),
}

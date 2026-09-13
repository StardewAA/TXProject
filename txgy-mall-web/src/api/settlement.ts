import { request } from './http'
export interface SettlementBill {
 id: number; shopId: number; shopName: string; no: string; billMonth: string; version: number; status: number; commissionRate: number; orderCount: number;
 orderPayAmount: number; refundAmount: number; commissionAmount: number; adjustmentAmount: number; carryAmount: number; settlementAmount: number; auditRemark?: string; paymentReference?: string; paymentTime?: string
}
export interface SettlementItem { id: number; orderId: number; orderNo: string; kind: number; originalBillId?: number; payAmount: number; deliveryAmount: number; refundBefore: number; refundTotal: number; commissionRate: number; commissionAmount: number; amount: number }
export interface SettlementDetail { bill: SettlementBill; items: SettlementItem[]; logs: { version: number; action: string; actorId: number; actorType: number; remark: string; createTime: string }[] }
export const settlementStatus: Record<number,string> = { 10:'待平台审核',20:'待店铺确认',30:'待付款登记',40:'已登记线下付款',50:'已退回待重算',60:'已结转（无需付款）' }
export const settlementApi = {
 page: (shopId:number,pageNo:number) => request<{list:SettlementBill[];total:number}>({url:'/trade/supplier-settlement/page',params:{shopId,pageNo,pageSize:10}}),
 get: (shopId:number,id:number) => request<SettlementDetail>({url:'/trade/supplier-settlement/get',params:{shopId,id}}),
 confirm: (shopId:number,id:number,version:number) => request<boolean>({url:'/trade/supplier-settlement/confirm',method:'PUT',data:{shopId,id,version}}),
 proof: (shopId:number,id:number) => request<string>({url:'/trade/supplier-settlement/proof',params:{shopId,id}}),
}

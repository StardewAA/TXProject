import { requestClient } from '#/api/request';
export interface ShopSettlement {id:number;shopId:number;shopName:string;no:string;billMonth:string;version:number;status:number;commissionRate:number;orderCount:number;orderPayAmount:number;refundAmount:number;commissionAmount:number;adjustmentAmount:number;carryAmount:number;settlementAmount:number;auditRemark?:string;paymentReference?:string;paymentTime?:string}
export interface SettlementDetail {bill:ShopSettlement;items:{id:number;orderNo:string;kind:number;payAmount:number;deliveryAmount:number;refundTotal:number;commissionAmount:number;amount:number}[];logs:{version:number;action:string;remark:string;createTime:string}[]}
export const settlementStatus:Record<number,string>={10:'待平台审核',20:'待店铺确认',30:'待付款登记',40:'已登记线下付款',50:'已退回待重算',60:'已结转（无需付款）'};
export function getShopSettlementPage(params:{pageNo:number;pageSize:number;shopId?:number;status?:number}){return requestClient.get<{list:ShopSettlement[];total:number}>('/trade/shop-settlement/page',{params})}
export function getShopSettlement(id:number){return requestClient.get<SettlementDetail>('/trade/shop-settlement/get',{params:{id}})}
export function generateShopSettlement(data:{shopId:number;month:string;commissionRate:number}){return requestClient.post<ShopSettlement>('/trade/shop-settlement/generate',data)}
export function auditShopSettlement(data:{id:number;version:number;approved:boolean;remark:string}){return requestClient.put('/trade/shop-settlement/audit',data)}
export function payShopSettlement(data:{id:number;version:number;amount:number;reference:string;paidAt:number;proof:string}){return requestClient.put('/trade/shop-settlement/pay',data)}
export function carryShopSettlement(id:number,version:number){return requestClient.put('/trade/shop-settlement/carry',{id,version,approved:true,remark:'确认余额结转'})}
export function getSettlementProof(id:number){return requestClient.get<string>('/trade/shop-settlement/proof',{params:{id}})}

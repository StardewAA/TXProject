import { inquiryApi } from './inquiry'
import { tradeApi } from './mall'
import { supplierApi } from './supplier'
import { supplierTradeApi } from './supplier-trade'

export interface SummaryMetric { key:string;label:string;value:number|null }
type Counter = [string,string,()=>Promise<{total:number}>]
export async function collectCounts(counters:Counter[]):Promise<SummaryMetric[]> {
 return Promise.all(counters.map(async([key,label,query])=>{
  try{const {total}=await query();return {key,label,value:Number.isSafeInteger(total)&&total>=0?total:null}}
  catch{return {key,label,value:null}}
 }))
}
export function loadBusinessSummary(shopId?:number){
 const page={pageNo:1,pageSize:1}
 if(shopId!==undefined){
  if(!Number.isSafeInteger(shopId)||shopId<=0)return Promise.reject(new Error('店铺编号无效'))
  return collectCounts([
   ['productsOn','在售商品',()=>supplierApi.page({...page,shopId,status:1})],
   ['productsOff','下架商品',()=>supplierApi.page({...page,shopId,status:0})],
   ['ordersWaiting','待发货订单',()=>supplierTradeApi.page({...page,shopId,status:10})],
   ['ordersShipped','待买家收货',()=>supplierTradeApi.page({...page,shopId,status:20})],
   ['afterApply','待审核售后',()=>supplierTradeApi.afterSales({...page,shopId,status:10})],
   ['afterReturn','待验收退货',()=>supplierTradeApi.afterSales({...page,shopId,status:30})],
  ])
 }
 return collectCounts([
  ['inquiryDraft','询价草稿',()=>inquiryApi.page({...page,status:0})],
  ['inquiryActive','已提交未截止询价',()=>inquiryApi.page({...page,status:1})],
  ['inquiryExpired','已截止询价',()=>inquiryApi.page({...page,status:2})],
  ['ordersUnpaid','待付款订单',()=>tradeApi.getOrders({...page,status:0})],
  ['ordersWaiting','待商家发货',()=>tradeApi.getOrders({...page,status:10})],
  ['ordersShipped','待确认收货',()=>tradeApi.getOrders({...page,status:20})],
 ])
}

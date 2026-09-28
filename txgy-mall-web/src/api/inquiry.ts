import { request, requestBlob } from './http'
import type { PageResult } from '@/types/mall'
export interface InquiryItem { picUrl?:string|null; skuId?: number; code: string; name: string; category: string; specification: string; quantity: number; unit: string; remark: string }
export type InquiryVisibility = 'PLATFORM_PUBLIC' | 'TENANT_PUBLIC' | 'INVITED_SHOPS'
export type InquiryInvitationTargetType = 'TENANT' | 'ENTERPRISE' | 'SHOP'
export interface InquiryInvitationScope { targetType: InquiryInvitationTargetType; targetTenantId: number; targetEnterpriseId?: number|null; targetShopId?: number|null }
export interface Inquiry { id?: number; no?: string; requestKey: string; title: string; contactName: string; contactMobile: string; deliveryAreaId?: number; address: string; remark: string; deadline: number; expectedDate?: number; status?: number; visibility?: InquiryVisibility; invitedShopIds?: number[]; invitedScopes?: InquiryInvitationScope[] }
export interface InquirySupplierShopOption { id: number; enterpriseId: number; name: string; enterpriseName: string; logoUrl?: string }
export interface InquiryInvitationOption { tenantId: number; tenantName: string; enterpriseId: number; enterpriseName: string; shopId: number; shopName: string }
export interface ImportRow { rowNumber: number; item: Omit<InquiryItem,'quantity'> & {quantity:string}; skuId?: number; matchStatus: string; errors: string[]; warnings: string[] }
const path='/product/purchase-inquiry'
export const inquiryApi={
 uploadImage:(file:File)=>{const data=new FormData();data.append('file',file);data.append('directory','mall/inquiry');return request<string>({url:'/infra/file/upload',method:'POST',data,headers:{'Content-Type':'multipart/form-data'},timeout:60000})},
 page:(params:Record<string,unknown>)=>request<PageResult<Inquiry>>({url:path+'/page',params}),
 detail:(id:number)=>request<{order:Inquiry;items:InquiryItem[];invitedShopIds?:number[];invitedScopes?:InquiryInvitationScope[]}>({url:path+'/get',params:{id}}),
 supplierShopOptions:()=>request<InquirySupplierShopOption[]>({url:path+'/supplier-shop-options'}),
 invitationOptions:(params?:{keyword?:string;limit?:number})=>request<InquiryInvitationOption[]>({url:path+'/invitation-options',params}),
 save:(data:Inquiry & {items:InquiryItem[]})=>request<number>({url:path+'/save',method:'POST',data}),
 submit:(id:number)=>request<boolean>({url:path+'/submit',method:'PUT',params:{id}}),
 close:(id:number)=>request<boolean>({url:path+'/close',method:'PUT',params:{id}}),
 preview:(file:File)=>{const data=new FormData();data.append('file',file);return request<ImportRow[]>({url:path+'/import-preview',method:'POST',data,headers:{'Content-Type':'multipart/form-data'}})},
 async template(current:()=>boolean){
  if(!current()) return
  const blob=await requestBlob({url:path+'/template'})
  if(!current()) return
  if(!(blob instanceof Blob)) throw new Error('模板下载失败，请确认登录和企业审核状态')
  const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download='采购商品导入模板.xlsx';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)
 }
}

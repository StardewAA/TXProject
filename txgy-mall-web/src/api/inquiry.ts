import { request, tokenStorage } from './http'
import type { PageResult } from '@/types/mall'
export interface InquiryItem { picUrl?:string|null; skuId?: number; code: string; name: string; category: string; specification: string; quantity: number; unit: string; remark: string }
export interface Inquiry { id?: number; no?: string; requestKey: string; title: string; contactName: string; contactMobile: string; address: string; remark: string; deadline: number; expectedDate?: number; status?: number; erpStatus?: string }
export interface ImportRow { rowNumber: number; item: Omit<InquiryItem,'quantity'> & {quantity:string}; skuId?: number; matchStatus: string; errors: string[]; warnings: string[] }
const path='/product/purchase-inquiry'
export const inquiryApi={
 uploadImage:(file:File)=>{const data=new FormData();data.append('file',file);data.append('directory','mall/inquiry');return request<string>({url:'/infra/file/upload',method:'POST',data,headers:{'Content-Type':'multipart/form-data'},timeout:60000})},
 page:(params:Record<string,unknown>)=>request<PageResult<Inquiry>>({url:path+'/page',params}),
 detail:(id:number)=>request<{order:Inquiry;items:InquiryItem[]}>({url:path+'/get',params:{id}}),
 save:(data:Inquiry & {items:InquiryItem[]})=>request<number>({url:path+'/save',method:'POST',data}),
 submit:(id:number)=>request<boolean>({url:path+'/submit',method:'PUT',params:{id}}),
 close:(id:number)=>request<boolean>({url:path+'/close',method:'PUT',params:{id}}),
 preview:(file:File)=>{const data=new FormData();data.append('file',file);return request<ImportRow[]>({url:path+'/import-preview',method:'POST',data,headers:{'Content-Type':'multipart/form-data'}})},
 async template(){
  const base=`${import.meta.env.VITE_API_BASE_URL||''}${import.meta.env.VITE_API_PREFIX||'/app-api'}`
  const response=await fetch(base+path+'/template',{headers:{Authorization:`Bearer ${tokenStorage.getAccess().replace(/^Bearer /,'')}`,'tenant-id':String(import.meta.env.VITE_TENANT_ID||'1')}})
  if(!response.ok || response.headers.get('content-type')?.includes('json')) throw new Error('模板下载失败，请确认登录和企业审核状态')
  const url=URL.createObjectURL(await response.blob());const a=document.createElement('a');a.href=url;a.download='采购商品导入模板.xlsx';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)
 }
}

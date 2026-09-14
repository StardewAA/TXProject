import { request } from './http'
export interface ReturnAddress { receiverName:string; receiverMobile:string; address:string }
export interface SupplierShop { shopId:number;name:string;logoUrl:string;contactName:string;contactMobile:string;introduction:string;returnAddress:ReturnAddress|null }
export const supplierShopApi={
 uploadLogo:(file:File)=>{
  const data=new FormData();data.append('file',file);data.append('directory','mall/shop-logo')
  return request<string>({url:'/infra/file/upload',method:'POST',data,headers:{'Content-Type':'multipart/form-data'},timeout:60000})
 },
 get:(shopId:number)=>request<SupplierShop>({url:'/product/supplier-shop/get',params:{shopId}}),
 save:(data:SupplierShop)=>request<boolean>({url:'/product/supplier-shop/save',method:'PUT',data}),
}

import { normalizeImage, productImage } from './format'
/** 独立规格图优先；共用主图时使用商品图片的 OSS/历史回退规则。 */
export function skuImage(productId?:string|number,main?:string,sku?:string){
 return sku&&sku!==main?normalizeImage(sku):productImage(productId,main)
}

import { normalizeImage, productImage } from './format'
/** 独立规格图优先；共用主图时保留原商品的本地资源回退。 */
export function skuImage(productId?:string|number,main?:string,sku?:string){
 return sku&&sku!==main?normalizeImage(sku):productImage(productId,main)
}

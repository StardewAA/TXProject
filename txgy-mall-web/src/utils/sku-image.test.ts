import { describe,it,expect } from 'vitest'
import { skuImage } from './sku-image'
describe('规格图片回退',()=>{
 it('独立图片优先，不被本地商品图片覆盖',()=>expect(skuImage(910001,'https://example.com/main.png','https://example.com/sku.png')).toBe('https://example.com/sku.png'))
 it('未设置或共用主图保留既有本地资源',()=>{expect(skuImage(910001,'https://example.com/main.png')).toBe('/product-images/910001.jpg');expect(skuImage(910001,'https://example.com/main.png','https://example.com/main.png')).toBe('/product-images/910001.jpg')})
 it('一般商品缺少规格图回退主图，无图片为空',()=>{expect(skuImage(50,'https://example.com/main.png','')).toBe('https://example.com/main.png');expect(skuImage(50)).toBe('')})
})

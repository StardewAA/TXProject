import { afterEach,describe,it,expect,vi } from 'vitest'
import { skuImage } from './sku-image'
afterEach(() => vi.unstubAllEnvs())
describe('规格图片回退',()=>{
 it('已迁移 OSS 主图优先于历史固定图片，独立规格图仍优先',()=>{
  vi.stubEnv('VITE_PUBLIC_IMAGE_ORIGIN','https://images.example.com/')
  const main='https://images.example.com/mall-demo/v1/product-images/new.png'
  const spec='https://images.example.com/mall-demo/v1/product-images/spec.png'
  expect(skuImage(940215,main)).toBe(main)
  expect(skuImage(910001,main,main)).toBe(main)
  expect(skuImage(910001,main,spec)).toBe(spec)
  expect(skuImage(940215,'https://images.example.com.evil.test/image.png')).toBe('/product-images/940215-chlorinated-rubber-topcoat.png')
 })
 it('独立图片优先，不被本地商品图片覆盖',()=>expect(skuImage(910001,'https://example.com/main.png','https://example.com/sku.png')).toBe('https://example.com/sku.png'))
 it('未设置或共用主图保留既有本地资源',()=>{expect(skuImage(910001,'https://example.com/main.png')).toBe('/product-images/910001.jpg');expect(skuImage(910001,'https://example.com/main.png','https://example.com/main.png')).toBe('/product-images/910001.jpg')})
 it('一般商品缺少规格图回退主图，无图片为空',()=>{expect(skuImage(50,'https://example.com/main.png','')).toBe('https://example.com/main.png');expect(skuImage(50)).toBe('')})
})

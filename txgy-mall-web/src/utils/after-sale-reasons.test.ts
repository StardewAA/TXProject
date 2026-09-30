import { expect, it } from 'vitest'
import { afterSaleReasons } from './after-sale-reasons'
const config = { afterSaleRefundReasons: ['不需要了'], afterSaleReturnReasons: ['商品破损', '尺寸不符'] }
it('uses distinct refund and return reasons from the same server snapshot', () => {
  expect(afterSaleReasons(config,10)).toEqual(['不需要了'])
  expect(afterSaleReasons(config,20)).toEqual(['商品破损','尺寸不符'])
})
it('does not copy refund reasons into an empty return dictionary', () => {
  expect(afterSaleReasons({ afterSaleRefundReasons:['仅退款原因'] },20)).toEqual(['其他'])
})
it('does not invent choices before configuration is loaded or for unknown types', () => {
  expect(afterSaleReasons(null,10)).toEqual([])
  expect(afterSaleReasons(config,99)).toEqual([])
})
it('normalizes only actual strings and removes blanks and duplicates', () => {
  expect(afterSaleReasons({ afterSaleReturnReasons:[' 原因 ','原因','',null,{},1] },20)).toEqual(['原因'])
})

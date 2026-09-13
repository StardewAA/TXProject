import { describe, expect, it } from 'vitest'
import { getPaymentOptions } from './payment'

describe('收银台支付渠道', () => {
  it('预览入口带独立标记，不冒充真实或模拟支付渠道', () => {
    const options = getPaymentOptions(['mock', 'wallet', 'alipay_pc', 'wx_native'], true, false)
    expect(options.map((option) => option.code)).toEqual(['preview_alipay', 'preview_wechat', 'mock', 'wallet'])
    expect(options.filter((option) => option.preview).map((option) => option.channelCode)).toEqual(['alipay_pc', 'wx_native'])
  })

  it('尚未配置任何渠道也可以预览第三方扫码页面，但不能模拟付款', () => {
    const options = getPaymentOptions([], true, false)
    expect(options).toHaveLength(2)
    expect(options.every((option) => option.preview && option.channelCode !== 'mock')).toBe(true)
  })

  it('关闭预览后只展示后台启用的真实渠道，不将失败降级为模拟付款', () => {
    expect(getPaymentOptions(['alipay_pc', 'wx_native'], false, false).map((option) => option.channelCode)).toEqual(['alipay_pc', 'wx_native'])
  })

  it('钱包充值不能使用钱包自身余额支付', () => {
    expect(getPaymentOptions(['mock', 'wallet'], true, true).some((option) => option.channelCode === 'wallet')).toBe(false)
    expect(getPaymentOptions(['wallet'], false, true)).toEqual([])
  })
})

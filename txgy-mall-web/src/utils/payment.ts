export interface PaymentOption {
  code: string
  channelCode: string
  label: string
  description: string
  icon: string
  tone: string
  preview?: boolean
}

const options: PaymentOption[] = [
  { code: 'preview_alipay', channelCode: 'alipay_pc', label: '支付宝', description: '扫码支付 · 暂未接通', icon: '支', tone: 'alipay', preview: true },
  { code: 'preview_wechat', channelCode: 'wx_native', label: '微信支付', description: '扫码支付 · 暂未接通', icon: '微', tone: 'wechat', preview: true },
  { code: 'mock', channelCode: 'mock', label: '模拟支付（测试）', description: '测试订单付款，不会真实扣款', icon: '测', tone: 'mock' },
  { code: 'wallet', channelCode: 'wallet', label: '账户余额支付', description: '从商城账户余额中扣款', icon: '¥', tone: 'wallet' },
  { code: 'alipay_pc', channelCode: 'alipay_pc', label: '支付宝电脑支付', description: '跳转支付宝电脑网站完成支付', icon: '支', tone: 'alipay' },
  { code: 'wx_native', channelCode: 'wx_native', label: '微信扫码支付', description: '使用微信扫描二维码完成支付', icon: '微', tone: 'wechat' },
]

// 第三方预览只是界面，不要求后端开通，也不能提交到真实或 mock 渠道。
export function getPaymentOptions(enabled: string[], preview: boolean, recharge: boolean): PaymentOption[] {
  return options.filter((option) => {
    if (option.preview) return preview
    if (preview && ['alipay_pc', 'wx_native'].includes(option.code)) return false
    return enabled.includes(option.channelCode) && !(recharge && option.channelCode === 'wallet')
  })
}

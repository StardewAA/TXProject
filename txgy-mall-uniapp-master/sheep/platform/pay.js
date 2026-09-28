import sheep from '@/sheep';
// #ifdef H5
import $wxsdk from '@/sheep/libs/sdk-h5-weixin';
// #endif
import { getRootUrl } from '@/sheep/helper';
import PayOrderApi from '@/sheep/api/pay/order';

function parseJsonContent(content) {
  if (content && typeof content === 'object') {
    return content;
  }
  if (typeof content !== 'string' || !content.trim()) {
    return null;
  }
  try {
    const parsed = JSON.parse(content);
    return parsed && typeof parsed === 'object' ? parsed : null;
  } catch (error) {
    return null;
  }
}

/**
 * 支付
 *
 * @param {String} payment = ['wechat','alipay','wallet','mock']  	- 支付方式
 * @param {String} orderType = ['goods','recharge','groupon']  	- 订单类型
 * @param {String} id					- 订单号
 */

export default class SheepPay {
  constructor(payment, orderType, id) {
    this.payment = payment;
    this.id = id;
    this.orderType = orderType;
    // Expose the existing async initiation so the cashier can suppress duplicate submissions.
    this.pending = this.payAction();
  }

  payAction() {
    const payAction = {
      WechatOfficialAccount: {
        wechat: () => {
          return this.wechatOfficialAccountPay();
        },
        alipay: () => {
          return this.redirectPay(); // 现在公众号可以直接跳转支付宝页面
        },
        wallet: () => {
          return this.walletPay();
        },
        mock: () => {
          return this.mockPay();
        },
      },
      WechatMiniProgram: {
        wechat: () => {
          return this.wechatMiniProgramPay();
        },
        alipay: () => {
          return this.copyPayLink();
        },
        wallet: () => {
          return this.walletPay();
        },
        mock: () => {
          return this.mockPay();
        },
      },
      App: {
        wechat: () => {
          return this.wechatAppPay();
        },
        alipay: () => {
          return this.alipay();
        },
        wallet: () => {
          return this.walletPay();
        },
        mock: () => {
          return this.mockPay();
        },
      },
      H5: {
        wechat: () => {
          return this.wechatWapPay();
        },
        alipay: () => {
          return this.redirectPay();
        },
        wallet: () => {
          return this.walletPay();
        },
        mock: () => {
          return this.mockPay();
        },
      },
    };
    const platformActions = payAction[sheep.$platform.name];
    const action = platformActions?.[this.payment];
    if (!action) {
      sheep.$helper.toast('当前平台暂不支持该支付方式');
      return Promise.resolve(false);
    }
    return action();
  }

  // 预支付
  async prepay(channel) {
    if (!channel) {
      return { code: 1, data: null, msg: '支付渠道不能为空' };
    }

    const data = {
      id: this.id,
      channelCode: channel,
      channelExtras: {},
    };

    try {
      // 特殊逻辑：微信公众号、小程序支付时，必须传入 openid
      if (['wx_pub', 'wx_lite'].includes(channel)) {
        const wechat = sheep.$platform.useProvider('wechat');
        const openid = wechat ? await wechat.getOpenid(true) : null;
        // 如果获取不到 openid，微信无法发起支付，此时需要引导
        if (!openid) {
          this.bindWeixin();
          return { code: 1, data: null, msg: '请先绑定微信再使用微信支付' };
        }
        data.channelExtras.openid = openid;
      }

      // 发起预支付 API 调用。request 在网络异常时会返回 false，统一转换为可消费的结果，
      // 避免收银台一直等待一个永远不会 resolve 的 Promise。
      const response = await PayOrderApi.submitOrder(data);
      const res =
        response && typeof response === 'object'
          ? response
          : { code: 1, data: null, msg: '支付请求失败' };

      if (res.code !== 0) {
        const message = String(res.msg || '');
        // 特殊逻辑：微信公众号、小程序支付时，必须传入正确的 openid
        if (message.includes('无效的openid') || message.includes('下单账号与支付账号不一致')) {
          // https://developers.weixin.qq.com/community/develop/doc/00008c53c347804beec82aed051c00
          this.bindWeixin();
        }
      }
      return res;
    } catch (error) {
      // getOpenid 或支付接口抛出异常时，也要让所有支付调用方正常结束
      return {
        code: 1,
        data: null,
        msg: error?.msg || error?.message || '支付请求失败',
      };
    }
  }
  // #ifdef H5
  // 微信公众号 JSSDK 支付
  async wechatOfficialAccountPay() {
    let { code, data } = await this.prepay('wx_pub');
    if (code !== 0 || !data?.displayContent) {
      if (code === 0) sheep.$helper.toast('获取微信支付信息失败');
      return;
    }
    const payConfig = parseJsonContent(data.displayContent);
    if (!payConfig) {
      sheep.$helper.toast('微信支付参数无效');
      return;
    }
    $wxsdk.wxpay(payConfig, {
      success: () => {
        this.payResult('success');
      },
      cancel: () => {
        sheep.$helper.toast('支付已手动取消');
      },
      fail: (error) => {
        if (error.errMsg.indexOf('chooseWXPay:没有此SDK或暂不支持此SDK模拟') >= 0) {
          sheep.$helper.toast(
            '发起微信支付失败，原因：可能是微信开发者工具不支持，建议使用微信打开网页后支付',
          );
          return;
        }
        this.payResult('fail');
      },
    });
  }

  // 浏览器微信 H5 支付 TODO 芋艿：待接入（注意：H5 支付是给普通浏览器，不是微信公众号的支付，绝大多数人用不到，可以忽略）
  async wechatWapPay() {
    const { code, data } = await this.prepay(getWeixinPayChannelCode());
    if (code !== 0 || !data) {
      return;
    }

    // WxWapPayClient 返回的是 displayContent 字符串（微信 mweb_url），兼容旧版
    // 返回的 pay_data.h5_url 结构，避免前后端升级时再次出现白屏/无跳转。
    let h5Url = data.displayContent;
    if (typeof h5Url === 'string') {
      try {
        const parsed = JSON.parse(h5Url);
        h5Url =
          parsed?.h5_url ||
          parsed?.h5Url ||
          parsed?.mweb_url ||
          parsed?.mwebUrl ||
          parsed?.url ||
          (typeof parsed === 'string' ? parsed : undefined);
      } catch (error) {
        // displayContent 本身就是 URL 时无需解析
      }
    }
    h5Url = h5Url || data.pay_data?.h5_url || data.pay_data?.h5Url;
    if (!h5Url) {
      sheep.$helper.toast('获取微信支付链接失败');
      return;
    }

    const redirect_url = `${getRootUrl()}pages/pay/result?id=${this.id}&payment=${
      this.payment
    }&orderType=${this.orderType}`;
    const separator = String(h5Url).includes('?') ? '&' : '?';
    location.href = `${h5Url}${separator}redirect_url=${encodeURIComponent(redirect_url)}`;
  }

  // 支付链接(支付宝 wap 支付)
  async redirectPay() {
    let { code, data } = await this.prepay('alipay_wap');
    if (code !== 0 || !data?.displayContent) {
      if (code === 0) sheep.$helper.toast('获取支付宝支付链接失败');
      return;
    }
    location.href = data.displayContent;
  }

  // #endif

  // 微信小程序支付
  async wechatMiniProgramPay() {
    // let that = this;
    let { code, data } = await this.prepay('wx_lite');
    if (code !== 0 || !data?.displayContent) {
      if (code === 0) sheep.$helper.toast('获取微信支付信息失败');
      return;
    }
    // 调用微信小程序支付
    const payConfig = parseJsonContent(data.displayContent);
    if (!payConfig) {
      sheep.$helper.toast('微信支付参数无效');
      return;
    }
    uni.requestPayment({
      provider: 'wxpay',
      timeStamp: payConfig.timeStamp,
      nonceStr: payConfig.nonceStr,
      package: payConfig.packageValue,
      signType: payConfig.signType,
      paySign: payConfig.paySign,
      success: (res) => {
        this.payResult('success');
      },
      fail: (err) => {
        if (err.errMsg === 'requestPayment:fail cancel') {
          sheep.$helper.toast('支付已手动取消');
        } else {
          this.payResult('fail');
        }
      },
    });
  }

  // 余额支付
  async walletPay() {
    const response = await this.prepay('wallet');
    response?.code === 0 && this.payResult('success');
  }

  // 模拟支付
  async mockPay() {
    const response = await this.prepay('mock');
    response?.code === 0 && this.payResult('success');
  }

  // 支付宝复制链接支付（通过支付宝 wap 支付实现）
  async copyPayLink() {
    let { code, data } = await this.prepay('alipay_wap');
    if (code !== 0 || !data?.displayContent) {
      if (code === 0) sheep.$helper.toast('获取支付宝支付链接失败');
      return;
    }
    // 引入 showModal 点击确认：复制链接；
    uni.showModal({
      title: '支付宝支付',
      content: '复制链接到外部浏览器',
      confirmText: '复制链接',
      success: (res) => {
        if (res.confirm) {
          sheep.$helper.copyText(data.displayContent);
        }
      },
    });
  }

  // 支付宝支付（App）
  async alipay() {
    let that = this;
    const { code, data } = await this.prepay('alipay_app');
    if (code !== 0 || !data?.displayContent) {
      if (code === 0) sheep.$helper.toast('获取支付宝支付信息失败');
      return;
    }

    // TODO @芋艿：【可优化】如果是沙箱支付，EnvUtils.setEnv(EnvUtils.EnvEnum.SANDBOX)，相关 https://t.zsxq.com/yjyJQ
    uni.requestPayment({
      provider: 'alipay',
      orderInfo: data.displayContent, // 直接使用返回的支付参数
      success: (res) => {
        that.payResult('success');
      },
      fail: (err) => {
        if (err.errMsg === 'requestPayment:fail [paymentAlipay:62001]user cancel') {
          sheep.$helper.toast('支付已手动取消');
        } else {
          that.payResult('fail');
        }
      },
    });
  }

  // 微信支付（App）
  async wechatAppPay() {
    // 获取预支付信息
    const { code, data } = await this.prepay('wx_app');
    if (code !== 0 || !data?.displayContent) {
      sheep.$helper.toast('获取支付信息失败');
      return;
    }

    // App 微信支付与小程序的参数结构不同：Android、iOS 必须通过 orderInfo 传递，且键名需全小写
    // 后端微信支付 V2、V3 返回的字段命名不一致，因此在此统一转换
    // 参考：https://uniapp.dcloud.net.cn/api/plugins/payment.html#%E5%BE%AE%E4%BF%A1app%E6%94%AF%E4%BB%98
    const payConfig = parseJsonContent(data.displayContent);
    if (!payConfig) {
      sheep.$helper.toast('微信支付参数无效');
      return;
    }
    const orderInfo = {
      appid: payConfig.appid || payConfig.appId,
      partnerid: payConfig.partnerid || payConfig.partnerId,
      prepayid: payConfig.prepayid || payConfig.prepayId,
      package: payConfig.package || payConfig.packageValue || 'Sign=WXPay',
      noncestr: payConfig.noncestr || payConfig.nonceStr,
      timestamp: Number(payConfig.timestamp || payConfig.timeStamp),
      sign: payConfig.sign,
    };
    // 调用微信支付
    uni.requestPayment({
      provider: 'wxpay',
      orderInfo,
      success: () => {
        this.payResult('success');
      },
      fail: (err) => {
        if (err.errMsg === 'requestPayment:fail cancel') {
          sheep.$helper.toast('支付已手动取消');
        } else {
          sheep.$helper.toast('支付失败：' + err.errMsg);
          this.payResult('fail');
        }
      },
    });
  }

  // 支付结果跳转,success:成功，fail:失败
  payResult(resultType) {
    goPayResult(this.id, this.orderType, resultType);
  }

  // 引导绑定微信
  bindWeixin() {
    goBindWeixin();
  }
}

export function getPayMethods(channels) {
  // 后端返回 Set 序列化后的数组；网络异常或空响应时按没有可用渠道处理
  const channelList = Array.isArray(channels) ? channels : [];
  const payMethods = [
    {
      icon: '/static/img/shop/pay/wechat.png',
      title: '微信支付',
      value: 'wechat',
      disabled: true,
    },
    {
      icon: '/static/img/shop/pay/alipay.png',
      title: '支付宝支付',
      value: 'alipay',
      disabled: true,
    },
    {
      icon: '/static/img/shop/pay/wallet.png',
      title: '余额支付',
      value: 'wallet',
      disabled: true,
    },
    {
      icon: '/static/img/shop/pay/apple.png',
      title: 'Apple Pay',
      value: 'apple',
      disabled: true,
    },
    {
      icon: '/static/img/shop/pay/wallet.png',
      title: '模拟支付',
      value: 'mock',
      disabled: true,
    },
  ];
  const platform = sheep.$platform.name;

  // 1. 处理【微信支付】
  const wechatMethod = payMethods[0];
  if (
    (platform === 'WechatOfficialAccount' && channelList.includes('wx_pub')) ||
    (platform === 'WechatMiniProgram' && channelList.includes('wx_lite')) ||
    (platform === 'App' && channelList.includes('wx_app')) ||
    (platform === 'H5' && channelList.includes('wx_wap'))
  ) {
    wechatMethod.disabled = false;
  }

  // 2. 处理【支付宝支付】
  const alipayMethod = payMethods[1];
  if (
    (platform === 'H5' && channelList.includes('alipay_wap')) ||
    (platform === 'WechatOfficialAccount' && channelList.includes('alipay_wap')) ||
    (platform === 'WechatMiniProgram' && channelList.includes('alipay_wap')) ||
    (platform === 'App' && channelList.includes('alipay_app'))
  ) {
    alipayMethod.disabled = false;
  }
  // 3. 处理【余额支付】
  const walletMethod = payMethods[2];
  if (channelList.includes('wallet')) {
    walletMethod.disabled = false;
  }
  // 4. 处理【苹果支付】TODO 芋艿：未来接入
  // 5. 处理【模拟支付】
  const mockMethod = payMethods[4];
  if (channelList.includes('mock')) {
    mockMethod.disabled = false;
  }
  return payMethods;
}

// 支付结果跳转,success:成功，fail:失败
export function goPayResult(id, orderType, resultType) {
  sheep.$router.redirect('/pages/pay/result', {
    id,
    orderType,
    payState: resultType,
  });
}

export function goBindWeixin() {
  uni.showModal({
    title: '微信支付',
    content: '请先绑定微信再使用微信支付',
    success: function (res) {
      if (res.confirm) {
        sheep.$platform.useProvider('wechat').bind();
      }
    },
  });
}

// 获取微信支付渠道码
export function getWeixinPayChannelCode() {
  const platform = sheep.$platform.name;
  switch (platform) {
    case 'WechatOfficialAccount':
      return 'wx_pub';
    case 'WechatMiniProgram':
      return 'wx_lite';
    case 'App':
      return 'wx_app';
    case 'H5':
      return 'wx_wap';
    default:
      return '';
  }
}

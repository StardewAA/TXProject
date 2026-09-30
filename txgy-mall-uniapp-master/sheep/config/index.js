import packageInfo from '@/package.json';
import { getWxEnvVersion } from '@/sheep/helper/env';

const { version } = packageInfo;

// 开发环境配置
export let baseUrl;
if (process.env.NODE_ENV === 'development') {
  baseUrl = import.meta.env.SHOPRO_DEV_BASE_URL;
} else {
  // 非本地 dev 模式开发环境，判断是体验版还是正式版
  const wxEnvVersion = getWxEnvVersion();

  if (wxEnvVersion === 'trial') {
    // 体验版使用体验版服务器地址
    baseUrl = import.meta.env.SHOPRO_TRIAL_BASE_URL || import.meta.env.SHOPRO_BASE_URL;
    console.log('当前运行环境：体验版');
  } else if (wxEnvVersion === 'release') {
    // 正式版使用生产服务器地址
    baseUrl = import.meta.env.SHOPRO_BASE_URL;
    console.log('当前运行环境：正式版');
  } else {
    // 其他平台或获取失败，使用默认地址
    baseUrl = import.meta.env.SHOPRO_BASE_URL;
  }
}
if (typeof baseUrl === 'undefined') {
  console.error('请检查.env配置文件是否存在');
} else {
  console.log(`[芋道商城 ${version}]  https://doc.iocoder.cn`);
}

// H5 同源部署时 SHOPRO_BASE_URL 可能是空串。留空会让所有请求退化成"裸相对路径"，
// 一旦 document base 不是 http(s)（代理改写、被内嵌、构建期变量被污染成文件系统路径等），
// 请求就会解析成 file:// 并被浏览器直接拒绝——实测表现为入口核验请求收不到、页面报
// "商城入口暂不可用"。这里显式回落到 location.origin，保证请求地址始终是绝对 http(s)。
// #ifdef H5
if (!baseUrl && typeof window !== 'undefined' && window.location && window.location.origin) {
  baseUrl = window.location.origin;
}
// #endif

export const apiPath = import.meta.env.SHOPRO_API_PATH;
export const staticUrl = import.meta.env.SHOPRO_STATIC_URL;
export const tenantId = import.meta.env.SHOPRO_TENANT_ID;
// WebSocket 默认由网关暴露在 /infra/ws。保留环境变量覆盖能力，避免未配置时拼出
// "undefined" 导致 uni.connectSocket 连接到错误地址。
export const websocketPath = import.meta.env.SHOPRO_WEBSOCKET_PATH || '/infra/ws';
export const h5Url = import.meta.env.SHOPRO_H5_URL;

export default {
  baseUrl,
  apiPath,
  staticUrl,
  tenantId,
  websocketPath,
  h5Url,
};

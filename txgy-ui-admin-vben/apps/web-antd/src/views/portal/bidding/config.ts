/**
 * 招投标门户（外部公告域 notice-external）联调配置
 *
 * 路由说明：
 *  - 开发态（DEV）：走 /bidding-api 本地代理（见 apps/web-antd/vite.config.ts），
 *    转发到 127.0.0.1:48110/admin-api —— 绕开「模块直跑未注册网关 + 无 CORS 头」两个问题；
 *  - 生产/网关态：前缀 /admin-api/bidding 同源直连（bidding 模块注册进网关后生效）。
 *  - 需要改前缀/地址时用环境变量：VITE_BIDDING_API_BASE / VITE_BIDDING_API_PREFIX。
 *
 * Mock 开关：默认关闭（真实联调，页面所见即后端返回）；需要看内置演示数据时设 VITE_BIDDING_USE_MOCK=1。
 */
const RAW_BASE = import.meta.env.VITE_BIDDING_API_BASE as string | undefined;
const RAW_PREFIX = import.meta.env.VITE_BIDDING_API_PREFIX as string | undefined;
const RAW_MOCK = import.meta.env.VITE_BIDDING_USE_MOCK as string | undefined;

export const biddingConfig = {
  /** 同源代理/网关模式下留空 */
  baseUrl: RAW_BASE || '',
  /** notice-external 资源前缀（不含 /notice-external） */
  apiPrefix:
    RAW_PREFIX || (import.meta.env.DEV ? '/bidding-api/bidding' : '/admin-api/bidding'),
  /** 默认接真实接口；仅当显式设 VITE_BIDDING_USE_MOCK=1 时启用内置 Mock */
  useMock: RAW_MOCK ? RAW_MOCK === '1' : false,
};

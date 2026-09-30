/**
 * 职责：招投标门户接口联调/网关配置（baseUrl、apiPrefix、useMock），由 portal/bidding/api.ts 拼装
 * notice-external / notice-follow / notice 各服务层请求前缀（env: VITE_BIDDING_API_BASE/PREFIX/USE_MOCK）。
 *
 * 路由说明（2026-09-20 更新）：
 *  - 开发态（DEV）：相对路径 /admin-api/bidding，经 vite 代理转发到网关 127.0.0.1:48080；
 *  - 生产/网关态：前缀 /admin-api/bidding 同源直连（bidding 模块已注册进网关）。
 *    历史形态是 dev 走 /bidding-api 代理到宿主机 48110（「模块直跑、未注册网关」时期），
 *    该代理与 `/admin-api/bidding → 48110` 直连规则均已废弃删除——bidding 现跑在 Docker 里、
 *    只经网关暴露，48110 无人监听。相关改动见 vite.config.ts 的【本机改动 2026-09-20】注释。
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
    RAW_PREFIX || '/admin-api/bidding',
  /** 默认接真实接口；仅当显式设 VITE_BIDDING_USE_MOCK=1 时启用内置 Mock */
  useMock: RAW_MOCK ? RAW_MOCK === '1' : false,
};

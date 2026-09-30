/**
 * 工业品商城的营销开关。
 *
 * 当前版本只保留普通商品、采购优惠（优惠券/满减）和客服咨询。
 * 秒杀、砍价、拼团、积分商城以及分销页面仍保留在工程中，方便以后恢复，
 * 但不应再从装修菜单或旧分享链接进入。
 */

/**
 * 需要暂时关闭的用户端路径前缀。
 * 使用前缀匹配是为了同时覆盖列表、详情、订单等子页面。
 */
export const DISABLED_MARKETING_PATHS = Object.freeze([
  '/pages/goods/groupon',
  '/pages/goods/seckill',
  '/pages/goods/point',
  '/pages/goods/bargain',
  '/pages/activity/groupon',
  '/pages/activity/seckill',
  '/pages/activity/point',
  '/pages/activity/bargain',
  // 分销属于消费型裂变营销，工业品商城暂不开放。
  '/pages/commission',
]);

/** 暂时不在装修模板中渲染的营销组件类型。 */
export const DISABLED_MARKETING_BLOCK_TYPES = Object.freeze([
  'PromotionCombination',
  'PromotionSeckill',
  'PromotionPoint',
]);

/**
 * 当前是否启用“简化营销”模式。
 *
 * 这个开关集中放在 helper 中，路由、装修组件和业务页面都从这里读取，
 * 避免各处维护一份活动白名单/黑名单。后续需要恢复完整营销时只需将这里
 * 改为配置项（或返回 false），无需逐个改页面。
 */
export function isSimpleMarketingEnabled() {
  return true;
}

/**
 * 从装修链接中取出页面路径。
 * 外链、action 和空值不做拦截，避免误伤正常内容。
 */
function getPagePath(value) {
  if (typeof value !== 'string') {
    return '';
  }
  const path = value.trim();
  // DIY 旧数据有时省略开头的斜杠，统一补上后再比较。
  const normalized = path.startsWith('pages/') ? `/${path}` : path;
  if (!normalized.startsWith('/pages/')) {
    return '';
  }
  return normalized.split(/[?#]/, 1)[0].replace(/\/+$/, '') || '/';
}

/** 判断一个页面链接是否属于暂未开放的营销功能。 */
export function isDisabledMarketingPath(value) {
  if (!isSimpleMarketingEnabled()) {
    return false;
  }
  const pagePath = getPagePath(value);
  return DISABLED_MARKETING_PATHS.some(
    (disabledPath) => pagePath === disabledPath || pagePath.startsWith(`${disabledPath}/`),
  );
}

/** 过滤装修菜单中的复杂营销入口。 */
export function filterMarketingMenuItems(items) {
  if (!Array.isArray(items)) {
    return [];
  }
  return items.filter((item) => {
    // 兼容旧版装修结构使用的 link/path 字段；新结构统一使用 url。
    const links = [item?.url, item?.link, item?.path];
    return !links.some((link) => isDisabledMarketingPath(link));
  });
}

/** 判断装修组件是否属于已关闭的复杂营销组件。 */
export function isDisabledMarketingBlock(type) {
  return isSimpleMarketingEnabled() && DISABLED_MARKETING_BLOCK_TYPES.includes(type);
}

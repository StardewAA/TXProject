import $store from '@/sheep/store';
import { showAuthModal, showShareModal } from '@/sheep/hooks/useModal';
import { isNumber, isString, isEmpty, startsWith, isObject, isNil, clone } from 'lodash-es';
import throttle from '@/sheep/helper/throttle';
import {
  isPlatformPortalAddress,
  platformPublicPath,
  scopeLocalPath,
} from '@/sheep/helper/tenant-navigation.mjs';
import { getControlledTenantId } from '@/sheep/helper/tenant-runtime';
import { checkoutIntentId } from '@/sheep/helper/checkout-intent.mjs';
import {
  isSimpleMarketingEnabled,
  isDisabledMarketingPath,
} from '@/sheep/helper/simplified-marketing';

const _go = (
  path,
  params = {},
  options = {
    redirect: false,
  },
) => {
  let page = ''; // 跳转页面
  let query = ''; // 页面参数
  let url = ''; // 跳转页面完整路径

  if (isString(path)) {
    // 判断跳转类型是 path ｜ 还是http
    if (startsWith(path, 'http')) {
      // #ifdef H5
      try {
        const destination = new URL(path);
        // External links are not mall API calls. Never append credentials or tenant to them.
        window.location.assign(destination.origin === window.location.origin
          ? scopeLocalPath(destination.pathname + destination.search + destination.hash, getControlledTenantId())
          : destination.href);
      } catch (error) { uni.showToast({ title: error.message || '链接无效', icon: 'none' }); }
      return;
      // #endif
      // #ifndef H5
      page = `/pages/public/webview`;
      query = `url=${encodeURIComponent(path)}`;
      // #endif
    } else if (startsWith(path, 'action:')) {
      handleAction(path);
      return;
    } else {
      [page, query] = path.split('?');
    }
    if (!isEmpty(params)) {
      let query2 = paramsToQuery(params);
      if (isEmpty(query)) {
        query = query2;
      } else {
        query += '&' + query2;
      }
    }
  }

  if (isObject(path)) {
    page = path.url;
    if (!isNil(path.params)) {
      query = paramsToQuery(path.params);
    }
  }

  // 统一由 simplified-marketing helper 决定活动是否开放，避免路由再维护一份黑名单。
  // 在简化模式下，即使是旧分享链接或活动详情子路径，也只提示并留在当前页面。
  if (isSimpleMarketingEnabled() && isDisabledMarketingPath(page)) {
    uni.showToast({
      title: '该营销活动暂未开放',
      icon: 'none',
    });
    return;
  }

  const nextRoute = ROUTES_MAP[page];

  // 未找到指定跳转页面
  // mark: 跳转404页
  if (!nextRoute) {
    console.log(`%c跳转路径参数错误<${page || 'EMPTY'}>`, 'color:red;background:yellow');
    return;
  }

  // 页面登录拦截
  if (nextRoute.meta?.auth && !$store('user').isLogin) {
    showAuthModal();
    return;
  }

  // A deliberate new purchase gets a new intent; refresh/back restores the URL's existing one.
  // No token, personal data or authorization is encoded in this browser recovery identifier.
  if (page === '/pages/order/confirm') {
    query = paramsToQuery({ ...queryToParams(query || ''), checkoutIntent: checkoutIntentId() });
  }
  url = page;
  if (!isEmpty(query)) {
    url += `?${query}`;
  }

  // Validate before a tab route drops its query; conflicting aliases must not disappear.
  // #ifdef H5
  try {
    url = isPlatformPortalAddress(url)
      ? platformPublicPath(url)
      : scopeLocalPath(url, getControlledTenantId());
  }
  catch (error) { uni.showToast({ title: error.message || '商城链接无效', icon: 'none' }); return; }
  // #endif

  // 跳转底部导航
  if (TABBAR.includes(page)) {
    // wx.switchTab: url 不支持 queryString
    // 设置全局变量
    const params = queryToParams(query);
    $store('app').setParamsForTabbar(params);
    // 请记得在业务代码里使用完后，清理掉全局状态，避免影响下次跳转
    uni.switchTab({
      url: page,
    });
    return;
  }

  // 使用redirect跳转
  if (options.redirect) {
    uni.redirectTo({
      url,
    });
    return;
  }

  uni.navigateTo({
    url,
  });
};

// 限流 防止重复点击跳转
function go(...args) {
  throttle(() => {
    _go(...args);
  });
}

function paramsToQuery(params) {
  if (isEmpty(params)) {
    return '';
  }
  // return new URLSearchParams(Object.entries(params)).toString();
  let query = [];
  for (let key in params) {
    // Encode query values so Chinese titles (例如“关于我们”“隐私协议”) are
    // preserved when uni.navigateTo/switchTab serialises the URL on H5.
    query.push(encodeURIComponent(key) + '=' + encodeURIComponent(params[key]));
  }

  return query.join('&');
}

function queryToParams(query) {
  if (isEmpty(query)) {
    return {};
  }
  let params = {};
  let pairs = query.split('&');
  for (let i = 0; i < pairs.length; i++) {
    let pair = pairs[i].split('=');
    params[decodeURIComponent(pair[0])] = decodeURIComponent(pair[1] || '');
  }
  return params;
}

function back() {
  // #ifdef H5
  history.back();
  // #endif

  // #ifndef H5
  uni.navigateBack();
  // #endif
}

function redirect(path, params = {}) {
  go(path, params, {
    redirect: true,
  });
}

// 检测是否有浏览器历史
function hasHistory() {
  // #ifndef H5
  const pages = getCurrentPages();
  if (pages.length > 1) {
    return true;
  }
  return false;
  // #endif

  // #ifdef H5
  return !!history.state.back;
  // #endif
}

function getCurrentRoute(field = '') {
  let currentPage = getCurrentPage();
  // #ifdef MP
  currentPage.$page['route'] = currentPage.route;
  currentPage.$page['options'] = currentPage.options;
  // #endif
  if (field !== '') {
    return currentPage.$page[field];
  } else {
    return currentPage.$page;
  }
}

function getCurrentPage() {
  let pages = getCurrentPages();
  return pages[pages.length - 1];
}

function handleAction(path) {
  const action = path.split(':');
  switch (action[1]) {
    case 'showShareModal':
      showShareModal();
      break;
  }
}

function error(errCode, errMsg = '') {
  redirect('/pages/public/error', {
    errCode,
    errMsg,
  });
}

export default {
  go,
  back,
  hasHistory,
  redirect,
  getCurrentPage,
  getCurrentRoute,
  error,
};

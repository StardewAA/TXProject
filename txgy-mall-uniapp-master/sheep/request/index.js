/**
 * Shopro-request
 * @description api模块管理，loading配置，请求拦截，错误处理
 */

import Request from 'luch-request';
import { apiPath, baseUrl, tenantId } from '@/sheep/config';
import $store from '@/sheep/store';
import $platform from '@/sheep/platform';
import { showAuthModal } from '@/sheep/hooks/useModal';
import { getTerminal } from '@/sheep/helper/const';
import { createTenantAuthClient } from '@/sheep/helper/tenant-auth-client.mjs';
import { publicApiMessage } from '@/sheep/helper/public-message.mjs';
import { readTenantToken } from '@/sheep/helper/tenant-runtime';
import { tenantContext, ensureTenantReady, ensureAccountSession, captureTenantRequest,
  isTenantRequestCurrent, captureTenantOperation, isTenantOperationCurrent,
  getControlledTenantId, getAccountTenantId, tenantTransport, verifyPlatformSession } from '@/sheep/helper/tenant-runtime';

const options = {
  // 显示操作成功消息 默认不显示
  showSuccess: false,
  // 成功提醒 默认使用后端返回值
  successMsg: '',
  // 显示失败消息 默认显示
  showError: true,
  // 失败提醒 默认使用后端返回信息
  errorMsg: '',
  // 显示请求时loading模态框 默认显示
  showLoading: true,
  // loading提醒文字
  loadingMsg: '加载中',
  // 需要授权才能请求 默认放开
  auth: false,
  // 是否传递 token
  isToken: true,
};

// Platform APIs are an explicit trust boundary: they may keep the signed-in
// member token, but they must never inherit the browser's tenant header.
const isPlatformScope = (config) => config?.custom?.scope === 'platform';
const isPlatformAccountScope = (config) => config?.custom?.scope === 'platform-account';
const isBrowseScope = (config) => config?.custom?.scope === 'browse';
const usesPlatformSession = (config) => isPlatformScope(config) || isPlatformAccountScope(config);
const hasVerifiedPlatformSession = (snapshot) => Boolean(snapshot?.token
  && snapshot.accountTenantId && snapshot.userId
  && snapshot.accountTenantId === String(snapshot.platformCarrierTenantId || '')
  && String(snapshot.userId) === String(snapshot.platformMemberId || ''));

// Loading全局实例
let LoadingInstance = {
  target: null,
  count: 0,
};

/**
 * 关闭loading
 */
function closeLoading() {
  if (LoadingInstance.count > 0) LoadingInstance.count--;
  if (LoadingInstance.count === 0) uni.hideLoading();
}

/**
 * @description 请求基础配置 可直接使用访问自定义请求
 */
const http = new Request({
  baseURL: baseUrl + apiPath,
  timeout: 8000,
  method: 'GET',
  header: {
    Accept: 'text/json',
    'Content-Type': 'application/json;charset=UTF-8',
    platform: $platform.name,
  },
  // #ifdef APP-PLUS
  sslVerify: false,
  // #endif
  // #ifdef H5
  // 跨域请求时是否携带凭证（cookies）仅H5支持（HBuilderX 2.6.15+）
  withCredentials: false,
  // #endif
  custom: options,
});

/**
 * @description 请求拦截器
 */
http.interceptors.request.use(
  (config) => {
    // 自定义处理【auth 授权】：必须登录的接口，则跳出 AuthModal 登录弹窗
    if (config.custom.auth && !$store('user').isLogin) {
      showAuthModal();
      return Promise.reject();
    }

    const snapshot = config.custom.tenantSnapshot;
    if (!isTenantRequestCurrent(snapshot)) return Promise.reject(new Error('登录状态已变化'));
    const platformScope = isPlatformScope(config);
    const platformAccountScope = isPlatformAccountScope(config);
    const browseScope = isBrowseScope(config);
    let scopedHeaders;
    if (platformScope) {
      scopedHeaders = config.custom.isToken && snapshot.token ? { Authorization: snapshot.token } : {};
    } else if (platformAccountScope) {
      const carrier = String(snapshot.platformCarrierTenantId || '');
      if (!snapshot.token || !snapshot.accountTenantId || snapshot.accountTenantId !== carrier) {
        throw new Error('平台账号来源商城尚未核验，请重新登录');
      }
      scopedHeaders = { 'tenant-id': carrier, Authorization: snapshot.token };
    } else if (browseScope) {
      scopedHeaders = tenantContext.headers(snapshot, config.custom.isToken);
    } else {
      scopedHeaders = tenantContext.accountHeaders(snapshot, config.custom.isToken);
    }

    // 自定义处理【loading 加载中】：如果需要显示 loading，则显示 loading
    if (config.custom.showLoading) {
      LoadingInstance.count++;
      LoadingInstance.count === 1 &&
        uni.showLoading({
          title: config.custom.loadingMsg,
          mask: true,
          fail: () => {
            uni.hideLoading();
          },
        });
    }

    // 增加 token 令牌、terminal 终端、tenant 租户的请求头
    delete config.header.Authorization;
    if (config.custom.isToken && scopedHeaders.Authorization) config.header.Authorization = scopedHeaders.Authorization;
    config.header['terminal'] = getTerminal();

    config.header['Accept'] = '*/*';
    if (platformScope) delete config.header['tenant-id'];
    else config.header['tenant-id'] = scopedHeaders['tenant-id'];
    return config;
  },
  (error) => {
    return Promise.reject(error);
  },
);

/**
 * @description 响应拦截器
 */
http.interceptors.response.use(
  (response) => {
    const snapshot = response.config.custom.tenantSnapshot;
    if (!isTenantRequestCurrent(snapshot)) {
      response.config.custom.showLoading && closeLoading();
      return Promise.reject(new Error('商城或登录状态已变化，请重试'));
    }
    // 防止网关/网络异常返回空响应时，直接访问 response.data.code 导致页面白屏
    const responseData =
      response?.data && typeof response.data === 'object'
        ? response.data
        : { code: -1, msg: '服务器返回数据为空' };
    // 不向移动端消费者暴露 ERP 接入状态等内部实施信息。
    if (Object.hasOwn(responseData, 'msg')) responseData.msg = publicApiMessage(responseData.msg, '');
    // 约定：如果是 /auth/ 下的 URL 地址，并且返回了 accessToken 说明是登录相关的接口，则自动设置登陆令牌
    if (responseData.code === 0 && !response.config.custom.deferAuthToken && response.config.url.indexOf('/member/auth/') >= 0 && responseData.data?.accessToken) {
      const identity = responseData.data;
      if (identity.tenantId && String(identity.tenantId) !== snapshot.browseTenantId) {
        response.config.custom.showLoading && closeLoading();
        return Promise.reject(new Error('登录返回的账号商城与当前入口不一致'));
      }
      $store('user').setToken(identity.accessToken, identity.refreshToken, identity.tenantId ? identity : undefined);
    }

    // 自定处理【loading 加载中】：如果需要显示 loading，则关闭 loading
    response.config.custom.showLoading && closeLoading();

    // 自定义处理【error 错误提示】：如果需要显示错误提示，则显示错误提示
    if (responseData.code !== 0) {
      // 特殊：如果 401 错误码，则跳转到登录页 or 刷新令牌
      if (responseData.code === 401 && response.config.custom.isToken && !response.config.custom.authEntry) {
        return refreshToken(response.config);
      }
      // 特殊：处理分销用户绑定失败的提示
      if ((responseData.code + '').includes('1011007')) {
        console.error(`分销用户绑定失败，原因：${responseData.msg}`);
      } else if (response.config.custom.showError) {
        // 错误提示
        uni.showToast({
          title: responseData.msg || '服务器开小差啦,请稍后再试~',
          icon: 'none',
          mask: true,
        });
      }
    }

    // 自定义处理【showSuccess 成功提示】：如果需要显示成功提示，则显示成功提示
    if (
      response.config.custom.showSuccess &&
      response.config.custom.successMsg !== '' &&
      responseData.code === 0
    ) {
      uni.showToast({
        title: response.config.custom.successMsg,
        icon: 'none',
      });
    }

    // 返回结果：包括 code + data + msg
    return Promise.resolve(responseData);
  },
  (error) => {
    const userStore = $store('user');
    const isLogin = userStore.isLogin;
    let errorMessage = '网络请求出错';
    if (error !== undefined) {
      switch (error.statusCode) {
        case 400:
          errorMessage = '请求错误';
          break;
        case 401:
          errorMessage = isLogin ? '您的登陆已过期' : '请先登录';
          // 正常情况下，后端不会返回 401 错误，所以这里不处理 handleAuthorized
          break;
        case 403:
          errorMessage = '拒绝访问';
          break;
        case 404:
          errorMessage = '请求出错';
          break;
        case 408:
          errorMessage = '请求超时';
          break;
        case 429:
          errorMessage = '请求频繁, 请稍后再访问';
          break;
        case 500:
          errorMessage = '服务器开小差啦,请稍后再试~';
          break;
        case 501:
          errorMessage = '服务未实现';
          break;
        case 502:
          errorMessage = '网络错误';
          break;
        case 503:
          errorMessage = '服务不可用';
          break;
        case 504:
          errorMessage = '网络超时';
          break;
        case 505:
          errorMessage = 'HTTP 版本不受支持';
          break;
      }
      if (error?.errMsg?.includes('timeout')) errorMessage = '请求超时';
      // #ifdef H5
      if (error?.errMsg?.includes('Network'))
        errorMessage = window.navigator.onLine ? '服务器异常' : '请检查您的网络连接';
      // #endif
    }

    if (error && error.config) {
      if (error.config.custom.showError) {
        uni.showToast({
          title: publicApiMessage(error.data?.msg, errorMessage),
          icon: 'none',
          mask: true,
        });
      }
      error.config.custom.showLoading && closeLoading();
    }

    return false;
  },
);

// Axios 无感知刷新令牌，参考 https://www.dashingdog.cn/article/11 与 https://segmentfault.com/a/1190000020210980 实现
const refreshes = new Map();
const refreshToken = async (config) => {
  const snapshot = config.custom.tenantSnapshot;
  const platformScope = usesPlatformSession(config) || hasVerifiedPlatformSession(snapshot);
  if (!isTenantRequestCurrent(snapshot)) throw new Error('登录状态已变化');
  if (config.custom.tenantRetried || !config.custom.isToken || !snapshot.refreshToken
      || config.url.includes('/member/auth/')) return handleAuthorized(snapshot);
  const key = `${platformScope ? 'platform:' + (snapshot.platformCarrierTenantId || '') : snapshot.browseTenantId}:${snapshot.epoch}:${snapshot.token}:${snapshot.refreshToken}`;
  if (!refreshes.has(key)) {
    const pending = (async () => {
      try {
        if (platformScope) {
          const carrier = String(snapshot.platformCarrierTenantId || '');
          const memberId = String(snapshot.platformMemberId || '');
          if (!/^[1-9]\d*$/.test(carrier) || !/^[1-9]\d*$/.test(memberId)) {
            throw new Error('平台登录缺少可信凭据，请重新登录');
          }
          const result = await tenantTransport('/member/auth/refresh-token?refreshToken=' + encodeURIComponent(snapshot.refreshToken), {
            method: 'POST', header: { 'tenant-id': carrier, terminal: '20', Accept: 'application/json' },
          });
          if (!isTenantRequestCurrent(snapshot)) throw new Error('登录状态已变化');
          const identity = result?.code === 0 ? result.data : null;
          if (!identity?.accessToken || !identity?.refreshToken
              || String(identity.tenantId) !== carrier || String(identity.userId) !== memberId) {
            throw new Error('平台登录刷新失败');
          }
          const verified = await verifyPlatformSession(identity, () => isTenantRequestCurrent(snapshot));
          if (!isTenantRequestCurrent(snapshot)) throw new Error('登录状态已变化');
          $store('user').setPlatformToken(verified.accessToken, verified.refreshToken, verified, true);
          return captureTenantRequest();
        }
        const result = await tenantTransport('/member/auth/refresh-token?refreshToken=' + encodeURIComponent(snapshot.refreshToken), {
          method: 'POST', header: tenantContext.accountHeaders(snapshot),
        });
        if (!isTenantRequestCurrent(snapshot)) throw new Error('登录状态已变化');
        if (result?.code !== 0 || !result.data?.accessToken || !result.data?.refreshToken) throw new Error('刷新令牌失败');
        const identity = result.data;
        if (identity.tenantId && String(identity.tenantId) !== String(snapshot.accountTenantId || snapshot.browseTenantId)) throw new Error('账号商城不一致');
        if (identity.userId && snapshot.userId && String(identity.userId) !== String(snapshot.userId)) throw new Error('刷新账号不一致');
        if (snapshot.accountTenantId && snapshot.userId) {
          const verifier = createTenantAuthClient(({ path, method, headers, data }) =>
            tenantTransport(path, { method, header: headers, data }));
          await verifier.verifySession(identity, snapshot.accountTenantId, () => isTenantRequestCurrent(snapshot), snapshot.userId);
        }
        if (!isTenantRequestCurrent(snapshot)) throw new Error('登录状态已变化');
        $store('user').setToken(identity.accessToken, identity.refreshToken,
          { tenantId: identity.tenantId || snapshot.accountTenantId, userId: identity.userId || snapshot.userId }, true);
        await ensureAccountSession();
        if (getAccessToken() !== identity.accessToken) throw new Error('登录状态已变化');
        return captureTenantRequest();
      } catch (error) {
        // An old failure must never log out a newer login/session.
        if (isTenantRequestCurrent(snapshot)) return handleAuthorized(snapshot);
        throw error;
      } finally { refreshes.delete(key); }
    })();
    refreshes.set(key, pending);
  }
  const refreshed = await refreshes.get(key);
  if (!isTenantRequestCurrent(refreshed)) throw new Error('登录状态已变化');
  return request({ ...config, custom: { ...config.custom, tenantRetried: true, tenantSnapshot: refreshed } });
};

/**
 * 处理 401 未登录的错误
 */
const handleAuthorized = (snapshot) => {
  if (!isTenantRequestCurrent(snapshot)) return Promise.reject(new Error('登录状态已变化'));
  const userStore = $store('user');
  userStore.logout(true);
  showAuthModal();
  // 登录超时
  return Promise.reject({
    code: 401,
    msg: userStore.isLogin ? '您的登陆已过期' : '请先登录',
  });
};

/** 获得访问令牌 */
export const getAccessToken = () => {
  return readTenantToken('token');
};

/** 获得刷新令牌 */
export const getRefreshToken = () => {
  return readTenantToken('refresh-token');
};

/** 获得租户编号 */
export const getTenantId = () => {
  return getControlledTenantId();
};
export { getAccountTenantId };

const request = async (config) => {
  const operation = captureTenantOperation();
  const platformScope = isPlatformScope(config);
  const browseScope = isBrowseScope(config);
  const explicitPlatformSession = usesPlatformSession(config);
  if (!explicitPlatformSession) {
    try { await ensureTenantReady(); }
    catch (error) {
      // App displays the blocked-entry modal. A settled API failure also protects callers without catch handlers.
      if (tenantContext.state().blocked) return { code: 403, data: null, msg: error.message };
      throw error;
    }
  }
  if (!isTenantOperationCurrent(operation)) throw new Error('登录状态已变化');
  const authEntry = /^\/member\/auth\/(login|register|sms-login|social-auth-redirect|social-login|weixin-mini-app-login)$/.test(config.url)
    || (config.url === '/member/auth/send-sms-code' && [1, 4].includes(Number(config.data?.scene)))
    || config.url === '/member/user/reset-password';
  if (authEntry && getControlledTenantId() !== String(tenantId)) return { code: 403, data: null, msg: '此商城请使用租户绑定的手机号认证入口，旧版及第三方登录尚未开放' };
  // Only known public reads may omit an unverified stored token; writes/private reads must wait.
  const publicRead = (!config.method || config.method.toUpperCase() === 'GET') && [
    '/promotion/diy-template/used', '/promotion/diy-template/get', '/promotion/diy-page/get',
    '/product/category/list', '/product/category/list-by-ids', '/product/spu/page',
    '/product/spu/list-by-ids', '/product/spu/get-detail', '/product/shop/list',
    '/product/shop/get', '/product/comment/page',
  ].includes(config.url);
  const browseAnonymous = browseScope && publicRead
    && getAccountTenantId() !== getControlledTenantId();
  const anonymous = authEntry || config.custom?.isToken === false || browseAnonymous
    || (!explicitPlatformSession && publicRead && !getAccountTenantId());
  // Public platform reads keep a present token but do not make their availability
  // depend on the legacy tenant-session verifier. Private platform APIs opt in with auth:true.
  const verifyAccount = !anonymous && (!explicitPlatformSession || config.custom?.auth === true);
  if (verifyAccount) {
    try { await ensureAccountSession(); }
    catch (error) {
      if (isTenantOperationCurrent(operation) && config.custom?.showError !== false) {
        uni.showToast({ title: error.message || '登录状态暂无法核验，请重试', icon: 'none' });
      }
      return { code: error.kind === 'unavailable' ? 503 : 401, data: null, msg: error.message || '登录状态暂无法核验，请重试' };
    }
  }
  if (!isTenantOperationCurrent(operation)) throw new Error('登录状态已变化');
  const snapshot = config.custom?.tenantSnapshot || captureTenantRequest();
  if (!isTenantRequestCurrent(snapshot)) throw new Error('商城或登录状态已变化，请重试');
  return http.middleware({ ...config, custom: { ...config.custom,
    ...(anonymous && !platformScope ? { isToken: false } : {}), authEntry, tenantSnapshot: snapshot } });
};

export default request;

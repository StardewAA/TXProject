/**
 * 私有文件（/infra/file/）的带登录态读取。
 *
 * 背景：文件服务签发的 URL 形如 `{domain}/admin-api/infra/file/{configId}/get/{path}`，
 * 私有资源直接放进 `<image src>` 是**匿名请求**，必然 401 破图：
 * HTML 的 img / uni-app 的 image 都无法携带 Authorization。
 *
 * 因此私有图必须走「带 token 取流 → 得到本地/blob 路径 → 交给 image 渲染 → 用完释放」，
 * 本模块提供这套能力，并统一处理四件事：
 *   1. 前缀改写：`/admin-api/...` → `/app-api/...`（框架按 URL 前缀强校验 userType，
 *      会员必须走 /app-api，否则会被判「错误的用户类型」）；
 *   2. 登录态：复用 tenant-runtime 的会话快照与请求头构造，保证租户/身份切换后不会串号；
 *   3. **会话隔离**：缓存键包含「会话作用域」（租户 + 账号 + 会话代数 + token 指纹），
 *      会话一变就整体作废并通知渲染方重新取图 —— 否则「账号 A 下载图 → 切到账号 B →
 *      B 读同一地址」会命中 A 的缓存，B 未经鉴权就看到了 A 的图片；
 *   4. 引用计数缓存：同一 URL 被多处渲染时只下载一次，全部释放后才回收 blob。
 */
import { baseUrl, apiPath } from '@/sheep/config';
import {
  captureTenantOperation,
  captureTenantRequest,
  isTenantOperationCurrent,
  tenantContext,
} from '@/sheep/helper/tenant-runtime';

const FILE_API_MARK = '/infra/file/';
const SCOPE_SEPARATOR = '\u0000';

/** 缓存：`${scope}${SEP}${url}` -> { refs, path, promise } */
const cache = new Map();
/** 会话作用域监听者（渲染方用它丢弃已渲染的本地路径并重新取图） */
const listeners = new Set();
/** 最近一次使用的作用域；变化即整体作废缓存 */
let activeScope = '';
/** 作用域变更代数：监听者据此丢弃过期回调 */
let scopeRevision = 0;

/**
 * 该地址是否由文件服务签发（可能是私有资源）。
 *
 * 公开资源同样走这条通道也没有副作用（公开资源匿名可读，带 token 也能读），
 * 因此这里只做「是否文件服务地址」的判定，不需要也不应该去猜公开/私有。
 */
export function isFileServiceUrl(url) {
  return typeof url === 'string' && url.indexOf(FILE_API_MARK) >= 0;
}

/** 归一化为当前端的会员读取通道地址 */
export function toMemberFileUrl(url) {
  const index = url.indexOf(FILE_API_MARK);
  if (index < 0) return url;
  return baseUrl + apiPath + url.slice(index);
}

/**
 * 当前会话作用域。
 *
 * 只要它不同，就说明「登录账号 / 租户 / 会话代数」发生了任何变化，
 * 旧缓存一律不能再复用。token 只取指纹，避免明文进入键与错误信息。
 */
function sessionScope() {
  const snapshot = captureTenantRequest() || {};
  return [
    snapshot.sessionEpoch,
    snapshot.epoch,
    snapshot.browseTenantId,
    snapshot.accountTenantId,
    snapshot.userId,
    fingerprint(snapshot.token),
  ].join('|');
}

function fingerprint(value) {
  const text = String(value || '');
  let hash = 5381;
  for (let i = 0; i < text.length; i += 1) {
    hash = ((hash << 5) + hash + text.charCodeAt(i)) >>> 0;
  }
  return hash.toString(36);
}

function download(url, header) {
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url: toMemberFileUrl(url),
      header,
      timeout: 30000,
      success: (response) => {
        const status = Number(response.statusCode);
        if (status >= 200 && status < 300 && response.tempFilePath) {
          resolve(response.tempFilePath);
        } else {
          reject(new Error('私有图片读取失败'));
        }
      },
      fail: () => reject(new Error('私有图片读取失败')),
    });
  });
}

/**
 * 取一个可直接放进 `<image src>` 的本地路径。
 *
 * @param {string} url 文件服务地址（也接受已经是本地/blob 的地址）
 * @returns {Promise<string>} 本地或 blob 路径
 */
export function loadPrivateImage(url) {
  if (!isFileServiceUrl(url)) {
    return Promise.resolve(url);
  }
  const scope = sessionScope();
  if (scope !== activeScope) {
    // 会话已变化：旧缓存里的图片属于**上一个账号/租户**，必须先作废再取图。
    activeScope = scope;
    invalidateCache('会话已变化');
  }

  const key = scope + SCOPE_SEPARATOR + url;
  const cached = cache.get(key);
  if (cached) {
    cached.refs += 1;
    return cached.promise;
  }

  const entry = { refs: 1, path: '', promise: null, key };
  const revision = scopeRevision;
  entry.promise = (async () => {
    const operation = captureTenantOperation();
    const snapshot = captureTenantRequest();
    if (!snapshot.token) {
      // 未登录时不要发无意义的匿名请求：私有图必然 401，公开图也不需要本通道。
      throw new Error('未登录');
    }
    const header = tenantContext.accountHeaders(snapshot);
    if (!isTenantOperationCurrent(operation)) {
      throw new Error('商城上下文已变化');
    }
    const path = await download(url, header);
    if (revision !== scopeRevision || !isTenantOperationCurrent(operation)) {
      // 下载期间发生了会话/租户切换，或本作用域已被整体作废：丢弃结果，
      // 避免把上一个账号的图片渲染到当前页面。
      revokeLocalPath(path);
      throw new Error('商城上下文已变化');
    }
    entry.path = path;
    return path;
  })();

  cache.set(key, entry);
  // 失败不缓存，允许下次重试
  entry.promise.catch(() => {
    if (cache.get(key) === entry) cache.delete(key);
  });
  return entry.promise;
}

/** 释放一次引用；引用归零后回收 blob 并移除缓存 */
export function releasePrivateImage(url) {
  if (!isFileServiceUrl(url)) return;
  // 作用域已变化时旧条目已整体作废，这里只需按当前作用域清理
  for (const [key, entry] of cache) {
    if (key.slice(key.indexOf(SCOPE_SEPARATOR) + 1) !== url) continue;
    entry.refs -= 1;
    if (entry.refs > 0) continue;
    cache.delete(key);
    entry.promise.then((path) => revokeLocalPath(path)).catch(() => {});
  }
}

/**
 * 丢弃全部缓存（用于会话切换 / 退出登录 / 切换账号）。
 *
 * 渲染方会收到通知，从而清掉已经落到 DOM 的旧图并重新取图——
 * 否则即使缓存清了，页面上仍挂着上一个账号的图片。
 */
export function invalidateCache(reason = 'session-changed') {
  scopeRevision += 1;
  for (const entry of cache.values()) {
    entry.promise.then((path) => revokeLocalPath(path)).catch(() => {});
  }
  cache.clear();
  listeners.forEach((listener) => {
    try {
      listener(reason);
    } catch (error) {
      console.warn('私有图片缓存失效回调失败', error);
    }
  });
}

/**
 * 注册会话失效监听者。
 *
 * @param {(reason: string) => void} listener
 * @returns {() => void} 取消注册
 */
export function subscribePrivateImageSession(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

// 会话一变化（切换账号 / 退出登录 / 跨标签页换号 / 会话校验失败）立即作废缓存。
// tenant-runtime 在 token 被替换、清除、或被判定与会员资料不一致时都会发这个事件。
uni.$on?.('mall:session-changed', () => {
  activeScope = '';
  invalidateCache('mall:session-changed');
});

function revokeLocalPath(path) {
  // #ifdef H5
  if (path && String(path).indexOf('blob:') === 0 && typeof URL !== 'undefined') {
    URL.revokeObjectURL(path);
  }
  // #endif
}

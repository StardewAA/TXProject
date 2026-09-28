/**
 * 百度地图 SDK 加载工具
 */

// 扩展 Window 接口以包含百度地图 GL API
declare global {
  interface Window {
    BMapGL: any;
  }
}

// 全局回调名称
const CALLBACK_NAME = '__BAIDU_MAP_LOAD_CALLBACK__';

// SDK 加载状态
let loadPromise: null | Promise<void> = null;

/**
 * 解析百度地图 AK（问题 2/3）
 *
 * <p>取值优先级（越靠前越优先，便于**不重新打包**就补上密钥）：</p>
 * <ol>
 *   <li>{@code window.__BAIDU_MAP_AK__}：部署时由 HTML/nginx 注入（推荐生产使用）；</li>
 *   <li>运行时配置 {@code window._VBEN_ADMIN_PRO_APP_CONF_.VITE_GLOB_BAIDU_MAP_KEY}；</li>
 *   <li>构建期环境变量 {@code VITE_BAIDU_MAP_KEY}（.env 文件）。</li>
 * </ol>
 */
export function resolveBaiduMapAk(): string {
  const injected = (globalThis as any).__BAIDU_MAP_AK__;
  if (typeof injected === 'string' && injected.trim()) {
    return injected.trim();
  }
  const runtimeConfig = (globalThis as any)._VBEN_ADMIN_PRO_APP_CONF_;
  const runtimeKey = runtimeConfig?.VITE_GLOB_BAIDU_MAP_KEY ?? runtimeConfig?.VITE_BAIDU_MAP_KEY;
  if (typeof runtimeKey === 'string' && runtimeKey.trim()) {
    return runtimeKey.trim();
  }
  const envKey = import.meta.env.VITE_BAIDU_MAP_KEY;
  return typeof envKey === 'string' ? envKey.trim() : '';
}

/** 是否已配置百度地图 AK（未配置时界面走手动填写 / 外部地图链接的降级路径） */
export function hasBaiduMapAk(): boolean {
  return resolveBaiduMapAk().length > 0;
}

/**
 * 加载百度地图 GL SDK
 * @param timeout 超时时间（毫秒），默认 10000
 * @returns Promise<void>
 */
export const loadBaiduMapSdk = (timeout = 10_000): Promise<void> => {
  // 已加载完成
  if (window.BMapGL) {
    return Promise.resolve();
  }

  const mapKey = resolveBaiduMapAk();
  if (!mapKey) {
    return Promise.reject(new Error('未配置百度地图 AK（VITE_BAIDU_MAP_KEY 或 window.__BAIDU_MAP_AK__）'));
  }

  // 正在加载中，返回同一个 Promise
  if (loadPromise) {
    return loadPromise;
  }

  loadPromise = new Promise((resolve, reject) => {
    let settled = false;
    const script = document.createElement('script');
    const finish = (error?: Error) => {
      if (settled) return;
      settled = true;
      clearTimeout(timeoutId);
      delete (window as any)[CALLBACK_NAME];
      if (error) {
        script.remove();
        loadPromise = null;
        reject(error);
        return;
      }
      resolve();
    };
    const timeoutId = setTimeout(() => {
      finish(new Error('百度地图 SDK 加载超时'));
    }, timeout);

    (window as any)[CALLBACK_NAME] = () => {
      if (!window.BMapGL) {
        finish(new Error('百度地图 SDK 未初始化'));
        return;
      }
      finish();
    };

    script.src = `https://api.map.baidu.com/api?v=1.0&type=webgl&ak=${encodeURIComponent(mapKey)}&callback=${CALLBACK_NAME}`;
    script.addEventListener('error', () => finish(new Error('百度地图 SDK 加载失败')));
    document.body.append(script);
  });

  return loadPromise;
};

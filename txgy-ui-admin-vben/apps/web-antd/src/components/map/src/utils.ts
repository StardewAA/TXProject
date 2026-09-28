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
 * 加载百度地图 GL SDK
 * @param timeout 超时时间（毫秒），默认 10000
 * @returns Promise<void>
 */
export const loadBaiduMapSdk = (timeout = 10_000): Promise<void> => {
  // 已加载完成
  if (window.BMapGL) {
    return Promise.resolve();
  }

  const mapKey = import.meta.env.VITE_BAIDU_MAP_KEY;
  if (!mapKey) {
    return Promise.reject(new Error('未配置百度地图 AK'));
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

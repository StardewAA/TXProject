const key = String(import.meta.env.SHOPRO_AMAP_KEY || '').trim();
const securityCode = String(import.meta.env.SHOPRO_AMAP_SECURITY_CODE || '').trim();
let sdkPromise;

export const isAmapConfigured = () => Boolean(key && securityCode);

function loadLoader() {
  if (globalThis.AMapLoader) return Promise.resolve(globalThis.AMapLoader);
  return new Promise((resolve, reject) => {
    const existing = document.querySelector('script[data-amap-loader]');
    const script = existing || document.createElement('script');
    const done = () => globalThis.AMapLoader ? resolve(globalThis.AMapLoader) : reject(new Error('高德地图加载器不可用'));
    script.addEventListener('load', done, { once: true });
    script.addEventListener('error', () => reject(new Error('高德地图加载失败')), { once: true });
    if (!existing) {
      script.src = 'https://webapi.amap.com/loader.js';
      script.async = true;
      script.dataset.amapLoader = 'true';
      document.head.appendChild(script);
    }
  });
}

export async function loadAmap() {
  if (!isAmapConfigured()) throw new Error('本机尚未配置高德地图 Key');
  if (!sdkPromise) {
    globalThis._AMapSecurityConfig = { securityJsCode: securityCode };
    sdkPromise = loadLoader().then(loader => loader.load({ key, version: '2.0', plugins: ['AMap.Geocoder'] }))
      .catch(error => { sdkPromise = undefined; throw error; });
  }
  return sdkPromise;
}

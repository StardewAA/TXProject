interface AMapLoaderApi {
  load(options: { key: string; version: string; plugins: string[] }): Promise<AmapApi>
}

export interface AmapApi {
  Map: new (container: string | HTMLElement, options: Record<string, unknown>) => AmapMap
  Marker: new (options: Record<string, unknown>) => AmapMarker
  Geocoder: new (options?: Record<string, unknown>) => AmapGeocoder
  Polyline: new (options: Record<string, unknown>) => AmapOverlay
}

export interface AmapPosition { getLng(): number; getLat(): number }
export interface AmapMap {
  add(overlay: AmapOverlay | AmapOverlay[]): void
  on(event: 'click', callback: (event: { lnglat: AmapPosition }) => void): void
  setCenter(position: [number, number]): void
  setZoom(zoom: number): void
  setFitView(overlays?: AmapOverlay[], immediately?: boolean, avoid?: [number, number, number, number], maxZoom?: number): void
  destroy(): void
}
export interface AmapOverlay {}
export interface AmapMarker extends AmapOverlay { setPosition(position: [number, number]): void }
export interface AmapGeocoder {
  getAddress(position: [number, number], callback: (status: string, result: AmapRegeoResult) => void): void
  getLocation(address: string, callback: (status: string, result: AmapGeoResult) => void): void
}
export interface AmapRegeoResult {
  regeocode?: { formattedAddress?: string; addressComponent?: { province?: string; city?: string | string[]; district?: string; adcode?: string } }
}
export interface AmapGeoResult { geocodes?: Array<{ location?: AmapPosition; formattedAddress?: string; adcode?: string }> }

declare global {
  interface Window {
    _AMapSecurityConfig?: { securityJsCode: string }
    AMapLoader?: AMapLoaderApi
  }
}

const key = String(import.meta.env.VITE_AMAP_KEY || '').trim()
const securityJsCode = String(import.meta.env.VITE_AMAP_SECURITY_CODE || '').trim()
let sdkPromise: Promise<AmapApi> | undefined

export function isAmapConfigured() { return Boolean(key && securityJsCode) }

function loadLoader(): Promise<AMapLoaderApi> {
  if (window.AMapLoader) return Promise.resolve(window.AMapLoader)
  return new Promise((resolve, reject) => {
    const previous = document.getElementById('amap-jsapi-loader') as HTMLScriptElement | null
    const script = previous || document.createElement('script')
    const done = () => window.AMapLoader ? resolve(window.AMapLoader) : reject(new Error('高德地图加载器不可用'))
    if (previous) {
      previous.addEventListener('load', done, { once: true })
      previous.addEventListener('error', () => reject(new Error('高德地图加载器加载失败')), { once: true })
      return
    }
    script.id = 'amap-jsapi-loader'
    script.src = 'https://webapi.amap.com/loader.js'
    script.async = true
    script.onload = done
    script.onerror = () => reject(new Error('高德地图加载器加载失败'))
    document.head.appendChild(script)
  })
}

export function loadAmap(): Promise<AmapApi> {
  if (!isAmapConfigured()) return Promise.reject(new Error('本机尚未配置高德地图 Key'))
  if (!sdkPromise) {
    window._AMapSecurityConfig = { securityJsCode }
    sdkPromise = loadLoader().then(loader => loader.load({ key, version: '2.0', plugins: ['AMap.Geocoder'] }))
      .catch(error => { sdkPromise = undefined; throw error })
  }
  return sdkPromise
}

export interface MobileReleaseConfig {
  landingUrl: string | null
  apkUrl: string | null
  version: string
  fileSize: string
  releaseDate: string
  minimumAndroidVersion: string
  sha256: string
  permissionsSummary: string
  feedbackUrl: string | null
  ready: boolean
}

type ReleaseEnvironment = Record<string, string | undefined>

function safeHttpUrl(value: string | undefined, production: boolean, stable = false, demo = false): string | null {
  if (!value?.trim()) return null
  try {
    const url = new URL(value.trim())
    if (!['https:', 'http:'].includes(url.protocol) || url.username || url.password) return null
    if (production && (url.protocol !== 'https:' || isLocalHost(url.hostname)) && !(demo && isLocalHost(url.hostname))) return null
    if (stable && (url.search || url.hash)) return null
    return url.href
  } catch {
    return null
  }
}

function isLocalHost(hostname: string): boolean {
  const host = hostname.toLowerCase()
  return host === 'localhost' || host.endsWith('.localhost') || host.endsWith('.local') ||
    isPrivateIpv4(host) || isLocalIpv6(host)
}

function isPrivateIpv4(host: string): boolean {
  const octets = host.split('.').map(Number)
  if (octets.length !== 4 || octets.some((octet) => !Number.isInteger(octet) || octet < 0 || octet > 255)) {
    return false
  }
  const first = octets[0]!
  const second = octets[1]!
  return first === 0 || first === 10 || first === 127 ||
    (first === 169 && second === 254) ||
    (first === 172 && second >= 16 && second <= 31) ||
    (first === 192 && second === 168)
}

function isLocalIpv6(host: string): boolean {
  if (!host.startsWith('[') || !host.endsWith(']')) return false
  const address = host.slice(1, -1)
  return address === '::' || address === '::1' || /^f[cd][0-9a-f]{2}:/i.test(address) ||
    /^fe[89ab][0-9a-f]:/i.test(address)
}

export function createMobileReleaseConfig(
  env: ReleaseEnvironment,
  currentOrigin: string,
  production: boolean,
  manifest?: unknown,
): MobileReleaseConfig {
  const demo = env.VITE_MALL_DEMO_MODE === 'true'
  const configuredBase = safeHttpUrl(env.VITE_MALL_PUBLIC_BASE_URL, production, true, demo)
  const currentBase = safeHttpUrl(currentOrigin, production, true, demo)
  // A localhost build-time setting must not send a phone back to its own localhost.
  const base = configuredBase && !isLoopbackUrl(configuredBase) ? configuredBase : currentBase
  const landingUrl = base ? new URL('/download/mobile', base).href : null
  const bundled = !env.VITE_ANDROID_APK_URL?.trim() ? readBundledRelease(manifest) : null
  const apkUrl = safeHttpUrl(bundled && currentBase ? new URL(bundled.apkUrl, currentBase).href : env.VITE_ANDROID_APK_URL, production, true, demo)
  const version = bundled?.version || env.VITE_ANDROID_APP_VERSION?.trim() || ''
  const fileSize = bundled ? `${(bundled.fileSizeBytes / 1024 / 1024).toFixed(2)} MB` : env.VITE_ANDROID_FILE_SIZE?.trim() || ''
  const releaseDate = bundled?.releaseDate || env.VITE_ANDROID_RELEASE_DATE?.trim() || ''
  const minimumAndroidVersion = bundled?.minimumAndroidVersion || env.VITE_ANDROID_MIN_VERSION?.trim() || ''
  const sha256 = bundled?.sha256 || env.VITE_ANDROID_SHA256?.trim().toLowerCase() || ''
  const permissionsSummary = bundled?.permissionsSummary || env.VITE_ANDROID_PERMISSIONS_SUMMARY?.trim() || ''
  const feedbackUrl = safeHttpUrl(env.VITE_MOBILE_FEEDBACK_URL, production, false, demo)
  const ready = Boolean(landingUrl && apkUrl && version && fileSize && releaseDate && minimumAndroidVersion &&
    /^[a-f\d]{64}$/.test(sha256) && permissionsSummary)

  return {
    landingUrl,
    apkUrl: ready ? apkUrl : null,
    version,
    fileSize,
    releaseDate,
    minimumAndroidVersion,
    sha256,
    permissionsSummary,
    feedbackUrl,
    ready,
  }
}

interface BundledRelease {
  apkUrl: string
  version: string
  fileSizeBytes: number
  releaseDate: string
  minimumAndroidVersion: string
  sha256: string
  permissionsSummary: string
}

function readBundledRelease(value: unknown): BundledRelease | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null
  const data = value as Record<string, unknown>
  if (data.schemaVersion !== 1 || data.packageName !== 'cn.txgy.mall' || data.distribution !== 'classroom-demo') return null
  for (const key of ['apkUrl', 'version', 'releaseDate', 'minimumAndroidVersion', 'sha256', 'permissionsSummary']) {
    if (typeof data[key] !== 'string' || !(data[key] as string).trim() || (data[key] as string).length > 512) return null
  }
  if (!/^\d+\.\d+\.\d+$/.test(data.version as string) || !/^[a-f\d]{64}$/.test(data.sha256 as string)) return null
  if (data.apkUrl !== `/downloads/txgy-mall-${data.version}-${(data.sha256 as string).slice(0, 12)}.apk`) return null
  if (typeof data.fileSizeBytes !== 'number' || !Number.isSafeInteger(data.fileSizeBytes) || data.fileSizeBytes <= 0 || data.fileSizeBytes > 200 * 1024 * 1024) return null
  if (!/^\d{4}-\d{2}-\d{2}$/.test(data.releaseDate as string)) return null
  return data as unknown as BundledRelease
}

export function isLoopbackUrl(value: string): boolean {
  try {
    const host = new URL(value).hostname
    return host === 'localhost' || host.endsWith('.localhost') || host === '[::1]' || /^(127|0)\./.test(host)
  } catch { return true }
}

/** Keep consistent with the native ConnectionTarget; never carry session/query data. */
export function normalizeMobileMallUrl(value: string): string | null {
  const raw = value.trim()
  if (raw.length > 2048 || /[\s\\\u0000-\u001f]/.test(raw)) return null
  const match = /^https?:\/\/([^/?#]+)(\/[^?#]*)?$/i.exec(raw)
  if (!match) return null
  const authority = match[1]!
  const hostPort = /^([a-z\d.-]+)(?::([1-9]\d{0,4}))?$/i.exec(authority)
  if (!hostPort || (hostPort[2] && Number(hostPort[2]) > 65535)) return null
  const host = hostPort[1]!.toLowerCase()
  if (!host.split('.').every((part) => /^[a-z\d](?:[a-z\d-]*[a-z\d])?$/i.test(part))) return null
  if (/^[\d.]+$/.test(host) && !host.split('.').every((part) => /^(0|[1-9]\d{0,2})$/.test(part) && Number(part) <= 255)) return null
  const path = match[2] || '/'
  if (!/^\/[a-z\d/_~.-]*$/i.test(path) || path.includes('//') || path.split('/').some((part) => part === '.' || part === '..')) return null
  try {
    const url = new URL(raw)
    // WHATWG URL otherwise accepts shortened/hex/octal IPv4 forms unlike Android URI.
    if (url.hostname !== host || isLoopbackUrl(url.href)) return null
    const first = Number(host.split('.')[0])
    if (/^169\.254\./.test(host) || first >= 224) return null
    if (url.protocol === 'http:' && !isPrivateIpv4(host) && !host.endsWith('.local')) return null
    if (!url.pathname.endsWith('/')) url.pathname += '/'
    return url.href
  } catch { return null }
}

export function mobileConnectionLinks(pcAddress: string, h5Address: string) {
  const pc = normalizeMobileMallUrl(pcAddress)
  const h5 = normalizeMobileMallUrl(h5Address)
  if (!pc || !h5) return null
  const landing = new URL('/download/mobile', pc)
  landing.searchParams.set('h5', h5)
  return { landingUrl: landing.href, appUrl: `txgymall://connect?url=${encodeURIComponent(h5)}`, h5Url: h5 }
}

export function defaultMobileMallUrl(pcOrigin: string, configured: string | undefined, port = '3000'): string {
  if (configured?.trim() && !isLoopbackUrl(configured)) return normalizeMobileMallUrl(configured) || ''
  try {
    const url = new URL(pcOrigin)
    url.port = /^\d+$/.test(port) && +port > 0 && +port <= 65535 ? port : '3000'
    url.pathname = '/'; url.search = ''; url.hash = ''
    return url.href
  } catch { return '' }
}

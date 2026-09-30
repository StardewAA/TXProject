import { describe, expect, it } from 'vitest'
import { createMobileReleaseConfig, defaultMobileMallUrl, mobileConnectionLinks, normalizeMobileMallUrl } from './mobile-release'

const release = {
  VITE_ANDROID_APK_URL: 'https://download.example.com/releases/shop-v1.2.0.apk',
  VITE_ANDROID_APP_VERSION: '1.2.0',
  VITE_ANDROID_FILE_SIZE: '38 MB',
  VITE_ANDROID_RELEASE_DATE: '2026-09-24',
  VITE_ANDROID_MIN_VERSION: 'Android 8.0',
  VITE_ANDROID_SHA256: 'a'.repeat(64),
  VITE_ANDROID_PERMISSIONS_SUMMARY: '网络访问、相机扫码（使用时申请）',
}

describe('mobile release configuration', () => {
  it('builds the landing page from the currently open HTTPS address', () => {
    const result = createMobileReleaseConfig({
      ...release,
    }, 'https://shop.example.com:15443', true)

    expect(result.ready).toBe(true)
    expect(result.landingUrl).toBe('https://shop.example.com:15443/download/mobile')
    expect(result.apkUrl).toBe(release.VITE_ANDROID_APK_URL)
  })

  it('keeps the existing HTTPS port valid but rejects HTTP and expiring signed package URLs', () => {
    for (const apkUrl of [
      'http://download.example.com/app.apk',
      'https://192.168.1.20/app.apk',
      'https://download.example.com/app.apk?signature=temporary',
    ]) {
      const result = createMobileReleaseConfig({
        ...release,
        VITE_MALL_PUBLIC_BASE_URL: 'https://shop.example.com',
        VITE_ANDROID_APK_URL: apkUrl,
      }, 'https://admin.example.com', true)

      expect(result.ready).toBe(false)
      expect(result.apkUrl).toBeNull()
    }

    const existingAddress = createMobileReleaseConfig({
      ...release,
      VITE_MALL_PUBLIC_BASE_URL: 'https://shop.example.com:15443',
      VITE_ANDROID_APK_URL: 'https://download.example.com:15444/releases/shop.apk',
    }, 'https://admin.example.com', true)
    expect(existingAddress.ready).toBe(true)
    expect(existingAddress.landingUrl).toBe('https://shop.example.com:15443/download/mobile')
    expect(existingAddress.apkUrl).toBe('https://download.example.com:15444/releases/shop.apk')

    const publicIpAddress = createMobileReleaseConfig({
      ...release,
      VITE_MALL_PUBLIC_BASE_URL: 'https://shop.example.com:15443',
      VITE_ANDROID_APK_URL: 'https://8.8.8.8/releases/shop.apk',
    }, 'https://admin.example.com', true)
    expect(publicIpAddress.ready).toBe(true)
  })

  it('uses the local origin only in development and leaves unpublished packages disabled', () => {
    const result = createMobileReleaseConfig({}, 'http://localhost:5175', false)

    expect(result.landingUrl).toBe('http://localhost:5175/download/mobile')
    expect(result.ready).toBe(false)
    expect(result.apkUrl).toBeNull()
  })

  it('does not treat an incomplete package manifest as publishable', () => {
    const result = createMobileReleaseConfig({
      ...release,
      VITE_MALL_PUBLIC_BASE_URL: 'https://shop.example.com',
      VITE_ANDROID_SHA256: 'not-a-sha256',
    }, 'https://admin.example.com', true)

    expect(result.ready).toBe(false)
    expect(result.apkUrl).toBeNull()
  })

  it('uses the current hosted address when no separate public base is configured', () => {
    const result = createMobileReleaseConfig(release, 'https://admin.example.com', true)

    expect(result.landingUrl).toBe('https://admin.example.com/download/mobile')
    expect(result.ready).toBe(true)
    expect(result.apkUrl).toBe(release.VITE_ANDROID_APK_URL)
  })
})

const bundled = {
  schemaVersion: 1, packageName: 'cn.txgy.mall', distribution: 'classroom-demo',
  version: '1.0.0', versionCode: 100, sha256: 'a'.repeat(64),
  apkUrl: '/downloads/txgy-mall-1.0.0-aaaaaaaaaaaa.apk', fileSizeBytes: 65536,
  releaseDate: '2026-09-24', minimumAndroidVersion: 'Android 6.0', permissionsSummary: '网络访问、网络状态',
}

describe('bundled classroom Android release', () => {
  it('supports existing LAN ports only with explicit demo mode', () => {
    expect(createMobileReleaseConfig({}, 'http://192.168.1.20:15175', true, bundled).ready).toBe(false)
    const result = createMobileReleaseConfig({ VITE_MALL_DEMO_MODE: 'true', VITE_MALL_PUBLIC_BASE_URL: 'http://localhost:15175' }, 'http://192.168.1.20:15175', true, bundled)
    expect(result.ready).toBe(true)
    expect(result.landingUrl).toBe('http://192.168.1.20:15175/download/mobile')
    expect(result.apkUrl).toBe('http://192.168.1.20:15175' + bundled.apkUrl)
    expect(result.fileSize).toBe('0.06 MB')
  })
  it('does not enable public HTTP even in classroom mode', () => {
    expect(createMobileReleaseConfig({ VITE_MALL_DEMO_MODE: 'true' }, 'http://public.example.com', true, bundled).ready).toBe(false)
  })
  it('rejects malformed, cross-origin, incomplete or inconsistent release metadata', () => {
    for (const bad of [null, [], {}, {...bundled, schemaVersion: 2}, {...bundled, apkUrl: 'https://evil.test/app.apk'}, {...bundled, packageName: 'another.app'}, {...bundled, sha256: 'x'.repeat(64)}, {...bundled, apkUrl: '/downloads/../index.html'}, {...bundled, version: '2.0.0'}, {...bundled, fileSizeBytes: -1}, {...bundled, permissionsSummary: ''}]) {
      expect(createMobileReleaseConfig({}, 'https://mall.example.com', true, bad).ready).toBe(false)
    }
  })
  it('does not silently use the bundled version when an external release override is incomplete', () => {
    expect(createMobileReleaseConfig({VITE_ANDROID_APK_URL: 'https://example.com/app.apk'}, 'https://mall.example.com', true, bundled).ready).toBe(false)
  })
})

describe('phone connection links', () => {
  it('encodes only the selected H5 entry, not browser tokens or tenant sessions', () => {
    const result = mobileConnectionLinks('http://192.168.1.20:15175', 'http://192.168.1.20:3000')!
    expect(new URL(result.landingUrl).searchParams.get('h5')).toBe('http://192.168.1.20:3000/')
    expect(new URL(result.appUrl).searchParams.get('url')).toBe('http://192.168.1.20:3000/')
    expect(result.appUrl.startsWith('txgymall://connect?url=')).toBe(true)
  })
  it('matches native validation for LAN/HTTPS and rejects ambiguous or unsafe URLs', () => {
    expect(normalizeMobileMallUrl('HTTPS://mall.example.com:443/mobile')).toBe('https://mall.example.com/mobile/')
    expect(normalizeMobileMallUrl('http://172.19.83.237:3000')).toBe('http://172.19.83.237:3000/')
    for (const url of ['http://localhost:3000', 'http://127.0.0.1:3000', 'http://0.0.0.0', 'http://169.254.1.1', 'http://8.8.8.8', 'http://example.com', 'https://user:pass@example.com', 'https://example.com/?token=secret', 'https://example.com/#code', 'https://127.1', 'https://0177.0.0.1', 'https://0x7f000001', 'https://10.0.0.999', 'http://192.168.1.1:65536', 'http://192.168.1.1:0', 'https://example.com/../', 'https://example.com/%2fsecret', 'https://example.com\\@evil.com', 'javascript:alert(1)', 'file:///data/test', 'https://bad..example.com']) {
      expect(normalizeMobileMallUrl(url), url).toBeNull()
    }
  })
  it('keeps invalid localhost out of QR codes, while suggesting the configured H5 port', () => {
    expect(mobileConnectionLinks('http://localhost:15175', 'http://192.168.1.20:3000')).toBeNull()
    expect(defaultMobileMallUrl('http://192.168.1.20:15175', '', '3001')).toBe('http://192.168.1.20:3001/')
    expect(defaultMobileMallUrl('http://192.168.1.20:15175', 'http://localhost:3000', '3001')).toBe('http://192.168.1.20:3001/')
    expect(defaultMobileMallUrl('http://192.168.1.20:15175', 'https://mobile.example.com/sub')).toBe('https://mobile.example.com/sub/')
  })
})

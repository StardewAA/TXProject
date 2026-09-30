/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_APP_TITLE: string
  readonly VITE_API_BASE_URL: string
  readonly VITE_API_PREFIX: string
  readonly VITE_TENANT_ID: string
  readonly VITE_STATIC_BASE_URL: string
  readonly VITE_PUBLIC_IMAGE_ORIGIN?: string
  readonly VITE_PAYMENT_PREVIEW?: string
  readonly VITE_AMAP_KEY?: string
  readonly VITE_AMAP_SECURITY_CODE?: string
  readonly VITE_MALL_PUBLIC_BASE_URL?: string
  readonly VITE_MALL_DEMO_MODE?: string
  readonly VITE_MALL_H5_URL?: string
  readonly VITE_MALL_H5_PORT?: string
  readonly VITE_ANDROID_APK_URL?: string
  readonly VITE_ANDROID_APP_VERSION?: string
  readonly VITE_ANDROID_FILE_SIZE?: string
  readonly VITE_ANDROID_RELEASE_DATE?: string
  readonly VITE_ANDROID_MIN_VERSION?: string
  readonly VITE_ANDROID_SHA256?: string
  readonly VITE_ANDROID_PERMISSIONS_SUMMARY?: string
  readonly VITE_MOBILE_FEEDBACK_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

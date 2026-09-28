const defaultFileConfigId = 22;
const fileConfigIdFromEnv = Number(import.meta.env.VITE_HRM_FILE_CONFIG_ID);
const hrmFileConfigId = Number.isInteger(fileConfigIdFromEnv) && fileConfigIdFromEnv > 0
  ? fileConfigIdFromEnv
  : defaultFileConfigId;
const legacyFileStorageHost =
  import.meta.env.VITE_HRM_FILE_STORAGE_HOST || 'test.txgy.iocoder.cn';
const apiBaseUrl = (import.meta.env.VITE_GLOB_API_URL || '/admin-api').replace(/\/$/, '');

export function getHrmRecruitFilePreviewUrl(fileUrl?: string) {
  if (!fileUrl) return '';
  const value = fileUrl.trim();
  if (!value) return '';

  try {
    const url = new URL(value, window.location.origin);
    if (url.pathname.includes('/infra/file/')) {
      const previewPath = url.pathname.replace(/\/(?:get|preview)\//, '/preview/');
      const previewUrl = new URL(previewPath, window.location.origin);
      previewUrl.protocol = window.location.protocol;
      previewUrl.host = window.location.host;
      return previewUrl.toString();
    }

    if (url.hostname === legacyFileStorageHost) {
      return new URL(
        `${apiBaseUrl}/infra/file/${hrmFileConfigId}/preview${url.pathname}`,
        window.location.origin,
      ).toString();
    }

    return url.toString();
  } catch {
    return value.replace(/\/(?:get|preview)\//, '/preview/');
  }
}

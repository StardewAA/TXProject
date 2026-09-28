import { requestClient } from '#/api/request';

/**
 * 私有图片读取支持（管理端）。
 *
 * 售后凭证等私有资源的读接口要求后台登录态，而 `<img src>` 无法携带 Authorization 请求头，
 * 直接渲染地址只会得到 403。因此必须先带 token 取回二进制内容，再以 `blob:` 地址渲染，
 * 并在组件卸载或图片变更时 `URL.revokeObjectURL` 释放。
 */

const FILE_PATH_MARKER = '/infra/file/';
const API_PREFIXES = ['/admin-api', '/app-api'];

/**
 * 把服务端保存的文件访问地址归一化成「相对 API 前缀」的路径，例如 `/infra/file/4/get/mall/aftersale/xxx.png`。
 *
 * 返回空串表示该地址不是本平台文件服务的访问地址（例如 OSS 直链、外部图片），调用方应直接渲染原地址。
 */
export function toApiFilePath(url?: null | string): string {
  const value = String(url ?? '').trim();
  if (!value) {
    return '';
  }
  for (const prefix of API_PREFIXES) {
    const index = value.indexOf(`${prefix}${FILE_PATH_MARKER}`);
    if (index >= 0) {
      return value.slice(index + prefix.length);
    }
  }
  return '';
}

/** 该地址是否需要走「带 token 读取」的私有通道。 */
export function isPrivateImageUrl(url?: null | string): boolean {
  return toApiFilePath(url) !== '';
}

/**
 * 带登录态读取私有图片，返回可直接渲染的 `blob:` 地址；读取失败返回空串。
 */
export async function loadPrivateImageObjectUrl(
  url?: null | string,
): Promise<string> {
  const path = toApiFilePath(url);
  if (!path) {
    return '';
  }
  const blob = await requestClient.download<Blob>(path, {
    headers: { 'x-skip-error-toast': 'true' },
  });
  return URL.createObjectURL(blob);
}

/** 释放 `blob:` 地址，幂等。 */
export function revokeObjectUrl(objectUrl?: null | string): void {
  if (objectUrl && objectUrl.startsWith('blob:')) {
    URL.revokeObjectURL(objectUrl);
  }
}

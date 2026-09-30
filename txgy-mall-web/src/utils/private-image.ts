import { requestBlob } from '@/api/http'

/**
 * 私有图片读取支持。
 *
 * 背景：文件服务读接口要求登录态，而 `<img src>` 无法携带 Authorization 请求头，
 * 直接渲染私有图片地址会得到 403（或 401）。因此私有图片必须：
 *   1. 用带 token 的请求取回二进制内容；
 *   2. 转成 `blob:` 地址渲染；
 *   3. 组件卸载或图片变更时 `URL.revokeObjectURL` 释放，避免内存泄漏。
 *
 * 另外，数据库里保存的访问地址一律是 `/admin-api/infra/file/{configId}/get/{path}`，
 * 但网关按 URL 前缀强校验用户类型：会员 token 打 `/admin-api` 会被拒绝。
 * 因此会员端必须改走 `/app-api` 通道（同一服务端鉴权逻辑，仅前缀不同）。
 */

const FILE_PATH_MARKER = '/infra/file/'
const API_PREFIXES = ['/admin-api', '/app-api']

/**
 * 把服务端保存的文件访问地址归一化成「相对 API 前缀」的路径，例如 `/infra/file/4/get/mall/inquiry/xxx.png`。
 *
 * 返回空串表示该地址不是本平台文件服务的访问地址（例如 OSS 直链、外部图片），
 * 调用方应直接渲染原地址。
 */
export function toAppFileApiPath(url?: string | null): string {
  const value = String(url || '').trim()
  if (!value) return ''
  for (const prefix of API_PREFIXES) {
    const index = value.indexOf(`${prefix}${FILE_PATH_MARKER}`)
    if (index >= 0) return value.slice(index + prefix.length)
  }
  // 兼容只保存了 `/infra/file/...` 的历史数据
  const bare = value.indexOf(FILE_PATH_MARKER)
  return bare >= 0 && value.startsWith('/') ? value.slice(bare) : ''
}

/** 该地址是否需要走「带 token 读取」的私有通道。 */
export function isPrivateImageUrl(url?: string | null): boolean {
  return toAppFileApiPath(url) !== ''
}

/**
 * 带登录态读取私有图片，返回可直接渲染的 `blob:` 地址。
 *
 * @param url 服务端保存的文件访问地址
 * @returns `blob:` 地址；读取失败返回空串（调用方展示占位）
 */
export async function loadPrivateImageObjectUrl(url?: string | null): Promise<string> {
  const path = toAppFileApiPath(url)
  if (!path) return ''
  const blob = await requestBlob({ url: path, scope: 'account', headers: { 'x-silent-error': true } })
  return URL.createObjectURL(blob)
}

/** 释放 `blob:` 地址，幂等。 */
export function revokeObjectUrl(objectUrl?: string | null): void {
  if (objectUrl && objectUrl.startsWith('blob:')) {
    URL.revokeObjectURL(objectUrl)
  }
}

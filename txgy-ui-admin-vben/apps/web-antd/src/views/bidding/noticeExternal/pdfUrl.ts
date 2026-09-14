/* 外部公告 PDF 地址解析——**单一真源**，门户详情与后台审核详情共用。
 *
 * ⚠️ 预览 ≠ 下载（用户 2026-09-14 指出：此前混为一谈，导致"从列表点进 PDF 公告只会触发下载、看不到内容"）：
 *   · **预览**必须用「可内联渲染」的地址：后端字节流代理 GET /bidding/notice-external/pdf?id={公告id}
 *     —— `@PermitAll @TenantIgnore`，返回 Content-Type: application/pdf + **Content-Disposition: inline**，
 *     因此 iframe 能直接渲染（后端注释原话："前端 iframe/embed src 直接用本端点"）。
 *   · **下载**用 FileApi 签名地址 pdfDownloadUrl（回填后可用，语义就是另存），
 *     并在 <a> 上加 download 属性；它**不该**被塞进 iframe——签名地址是下载型的，
 *     iframe 渲染不出来时浏览器会直接触发下载，这正是"PDF 点进去只下载"的原因。
 *
 * 为什么预览必须以代理为先：pdfDownloadUrl 可能为空（存量数据需人工回填），而公告 id 一定有；
 *   反过来（签名地址优先）则会在有签名地址时把 iframe 指向下载型 URL —— 之前就是这么错的。
 *
 * 纯函数：apiURL 由调用方传入（useAppConfig 需在 setup 上下文内调用，不能藏在工具函数里）。 */

/** 拼接后端 PDF 字节流代理地址（inline 预览用）；无公告 id 时返回空串 */
function proxyUrl(apiURL: string | undefined, noticeId: null | number | string | undefined): string {
  if (noticeId === null || noticeId === undefined || noticeId === '') return '';
  return String(apiURL ?? '').replace(/\/$/, '') + '/bidding/notice-external/pdf?id=' + encodeURIComponent(String(noticeId));
}

/** 去掉首尾空白后的签名地址（未回填则空串） */
function signedUrl(pdfDownloadUrl?: null | string): string {
  return typeof pdfDownloadUrl === 'string' ? pdfDownloadUrl.trim() : '';
}

/**
 * **预览地址**（iframe src / 新窗口打开）：代理优先（inline），代理不可用时才退回签名地址。
 * 注意：退回签名地址只是"有比没有好"，能否内联渲染取决于文件服务的响应头。
 */
export function buildExternalNoticePdfPreviewUrl(
  apiURL: string | undefined,
  noticeId: null | number | string | undefined,
  pdfDownloadUrl?: null | string,
): string {
  return proxyUrl(apiURL, noticeId) || signedUrl(pdfDownloadUrl);
}

/**
 * **下载地址**（<a download>）：签名地址优先（本就是另存语义），否则退回代理（配合 download 属性强制另存）。
 */
export function buildExternalNoticePdfDownloadUrl(
  apiURL: string | undefined,
  noticeId: null | number | string | undefined,
  pdfDownloadUrl?: null | string,
): string {
  return signedUrl(pdfDownloadUrl) || proxyUrl(apiURL, noticeId);
}

/**
 * @deprecated 语义含混（"可访问地址"既可能是预览也可能是下载，正是本次拆分要消除的歧义）。
 *   新代码请按用途选 buildExternalNoticePdfPreviewUrl / buildExternalNoticePdfDownloadUrl。
 *   保留仅为兼容尚未迁移的调用点，行为等价于"下载地址优先"（拆分前的旧行为）。
 */
export function buildExternalNoticePdfUrl(
  apiURL: string | undefined,
  noticeId: null | number | string | undefined,
  pdfDownloadUrl?: null | string,
): string {
  return signedUrl(pdfDownloadUrl) || proxyUrl(apiURL, noticeId);
}

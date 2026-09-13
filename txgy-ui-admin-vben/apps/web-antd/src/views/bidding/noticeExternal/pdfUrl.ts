/* 外部公告 PDF 可访问地址解析——**单一真源**，门户详情与后台审核详情共用。
 *
 * 为什么必须共用：曾出现"门户能预览、后台审核详情显示'源文件整理中'"的分叉——
 * 门户实现了两级回退（签名地址 → 后端字节流代理），后台只认 pdfDownloadUrl，
 * 结果**审核人在该看到 PDF 的位置只看到一句过时文案**（审核看不到最终件 = 功能缺陷）。
 *
 * 解析顺序（与后端契约一致）：
 *   ① pdfDownloadUrl：FileApi 签名地址，回填后优先（04-契约 §1）；
 *   ② 后端字节流代理 GET /bidding/notice-external/pdf?id={公告id}：公开免登（@PermitAll @TenantIgnore），
 *      由后端转发归档 PDF 字节流；后端注释明确"避免前端直连 infra 文件 URL（内网 host 不可达）"。
 * 为什么需要 ②：存量数据的 pdf_download_url 可能为空（需人工触发回填），而公告 id 一定有；
 *   只认 ① 会让"其实能拿到 PDF"的公告长期显示整理中。
 *
 * 纯函数：apiURL 由调用方传入（useAppConfig 需在 setup 上下文内调用，不能藏在工具函数里）。 */
export function buildExternalNoticePdfUrl(
  apiURL: string | undefined,
  noticeId: null | number | string | undefined,
  pdfDownloadUrl?: null | string,
): string {
  const signed = typeof pdfDownloadUrl === 'string' ? pdfDownloadUrl.trim() : '';
  if (signed) return signed;
  if (noticeId === null || noticeId === undefined || noticeId === '') return '';
  return String(apiURL ?? '').replace(/\/$/, '') + '/bidding/notice-external/pdf?id=' + encodeURIComponent(String(noticeId));
}

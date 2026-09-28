import { useAccessStore } from '@vben/stores';

import { requestClient } from '#/api/request';

/**
 * 上传/补登记的请求前缀。
 *
 * 默认走平台统一前缀（空串 = 相对 /admin-api，与其它 bidding 接口一致）：
 * dev 下经 Vite 代理 → 网关 48080 → bidding-server，生产同源直连网关。
 *
 * 历史背景：早期 bidding 只在 IDEA 里直跑宿主机 48110、未注册网关，且团队记录过
 * 「经 5666 代理上传时后端收到残缺 body（code=500 系统异常）」，于是 dev 下改成直连
 * `<当前页面主机>:48110/admin-api`。现在本模块已接网关、bidding 跑在 Docker 里（宿主机
 * 只暴露网关 48080，**48110 无人监听**），硬编码 48110 会让上传直接 Network Error。
 *
 * 逃生口：若确认需要绕过 Vite 代理直连（例如 bidding 未接网关、在宿主机 48110 直跑），
 * 在 apps/web-antd/.env.development 里设
 *   VITE_BIDDING_DIRECT_BASE=http://localhost:48110/admin-api
 * 即可，无需改代码。
 */
const DEV_DIRECT_BASE: string =
  (import.meta.env.VITE_BIDDING_DIRECT_BASE as string | undefined) ?? '';

/** 契约来源：txgy-module-bidding BiddingFileController + 《前端规范-全域接口说明/11-文件上传与file_id契约-2026-09-10.md》§2
 *  POST /bidding/file/upload          → { fileId, url, name, type, size, sha256 }
 *  POST /bidding/file/register-by-url → 按已上传地址补登记 file_id（历史"只有 URL"数据修复通道；sha256 为 null）
 *  口径（规范 §5 三-5 / 红线 8）：fileId 是业务表落库关联键（权威，三件套之一），URL 非一致性依据、仅用于即时预览；
 *    后端保证"查不到 file_id 就报错、绝不返回只有 URL 的成功响应"。
 *  错误码（规范 §2.1）：1_060_101_013 文件为空 / 1_060_101_014 文件注册失败，请重试 / 1_060_101_015 文件地址无效。
 *  鉴权：登录即可（@PreAuthorize("isAuthenticated()") + @Idempotent）。
 *  幂等键：后端按「文件名 + 大小」取（框架默认解析器会忽略 MultipartFile 参数，
 *    否则 1 秒内批量上传不同文件会被误判为重复请求）。  * 契约来源：BiddingFileRespVO
 */
export interface BiddingFileResp {
  /** 文件编号（全局唯一，infra_file.id；业务表落库关联键） */
  fileId: number;
  /** 文件访问地址（平台文件服务 URL，仅用于上传后即时预览；落库以 fileId 为准） */
  url: string;
  name?: string;
  type?: string;
  /** 文件大小（字节） */
  size?: number;
  /** 文件 SHA-256（红线 8 三件套之一；按 URL 补登记时为 null） */
  sha256?: string;
}

/** 上传文件（响应体必带 fileId；缺失时后端已返回「文件注册失败，请重试」） */
export function uploadBiddingFile(file: File, directory?: string) {
  const data: { directory?: string; file: File } = { file };
  if (directory) data.directory = directory;
  return requestClient.upload<BiddingFileResp>(`${DEV_DIRECT_BASE}/bidding/file/upload`, data);
}

/**
 * 业务受控的文件读出口（**内联预览**，非下载）：GET /admin-api/bidding/file/download?id=
 *
 * 契约依据：《后端变更通知-电子证照域字典治理-2026-09-20》§六-5、《电子证照域-权限与操作全表》§三。
 * 为什么不再用 /infra/file/get：① 需 infra:file:query 权限，业务账号通常没有；② 该端点 Content-Disposition: attachment，**强制下载**不是预览。
 * 后端在文件类出参里也会直接下发 previewUrl（同址）；能拿到出参值时优先用它，本函数用于「只有 fileId」的历史数据兜底。
 *
 * ⚠️ 前缀必须落到 `/admin-api`：本函数返回值是给 **iframe/img/a/fetch 直吃**的完整路径，
 *   dev 下只有 `/admin-api` 有 Vite 代理、生产也只有它被网关路由，缺前缀会直接 404（页面空白）。
 *   注意不能与 upload/register 共用前缀——那两个走 requestClient，baseURL 会自动补 `/admin-api`，
 *   补两次会变成 `/admin-api/admin-api/...`。
 */
export function buildBiddingFileDownloadUrl(fileId: number | string): string {
  return `${DEV_DIRECT_BASE || '/admin-api'}/bidding/file/download?id=${encodeURIComponent(String(fileId))}`;
}

/**
 * 给「需登录的文件出口」补上 `token` 查询参数 —— 专治**浏览器自己发请求**的场景。
 *
 * 为什么需要：`<a target="_blank">`、`<iframe src>`、`window.open()` 都由浏览器发起，
 * **不会带 Authorization 请求头**；而 `/bidding/file/download` 要求 isAuthenticated()
 * ⇒ 一律 401，页面上表现为「点开是空白 / 一串 JSON」（本框架失败响应是 HTTP 200 + body.code=401，
 * 见《后端变更通知-给前端》§33）。
 * 框架的 TokenAuthenticationFilter 支持从查询参数取令牌（`SecurityProperties.tokenParameter = "token"`），
 * 所以这类场景统一用它拼 URL。**实测**（2026-09-21，fileId=6263）：
 *   裸 URL → `{"code":401,"msg":"账号未登录"}`；`?token=<有效令牌>` → HTTP 200 + application/pdf + 641467 字节。
 *
 * 用法边界：只给"浏览器原生跳转/内联"用；走 requestClient / fetch 的调用继续用 Authorization 头
 * （或用 utils/fetchFile.ts 的 fetchFileBlob，它自带登录态且能识别错误包体）。
 */
export function withBiddingFileToken(url: string): string {
  if (!url) return url;
  let token: string | undefined;
  try {
    token = useAccessStore().accessToken ?? undefined;
  } catch {
    return url; // pinia 未就绪：按匿名处理（匿名能读的地址本就不需要 token）
  }
  if (!token) return url;
  // 只给「本站 admin-api 出口」补 token：对象存储 / 预签名 URL 追加查询参数会**破坏签名**，
  // 外站链接更不该带本站令牌 —— 这类地址原样返回。
  if (!url.includes('/admin-api/')) return url;
  const hashIndex = url.indexOf('#');
  const base = hashIndex >= 0 ? url.slice(0, hashIndex) : url;
  const hash = hashIndex >= 0 ? url.slice(hashIndex) : '';
  if (/[?&]token=/.test(base)) return url; // 已带 token，避免重复拼接
  const sep = base.includes('?') ? '&' : '?';
  return `${base}${sep}token=${encodeURIComponent(token)}${hash}`;
}

/** 按已上传地址补登记 file_id（历史"只有 URL 没有 file_id"数据的修复通道） */
export function registerBiddingFileByUrl(url: string) {
  return requestClient.post<BiddingFileResp>(`${DEV_DIRECT_BASE}/bidding/file/register-by-url`, null, {
    params: { url },
  });
}

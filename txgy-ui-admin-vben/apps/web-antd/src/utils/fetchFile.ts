/**
 * 取文件字节（裸 fetch 场景）—— 把「框架错误包体」与「真实文件字节」分开。
 *
 * 背景（本框架统一口径，2026-09-20 后端勘误）：
 *   失败响应是 **HTTP 200 + body {code,msg}**；鉴权失败（账号未登录）也只是 code=401，
 *   HTTP 状态照样是 200。所以 res.ok / res.status **不能**用来判断「取文件成功」——
 *   否则会把 {"code":401,"msg":"账号未登录"} 这段 JSON 当成 PDF/Excel/docx 的字节，
 *   送去盖章、落库、下载或渲染（症状：盖章件是坏 PDF、下载的 xlsx 打不开、预览空白）。
 *
 * 适用：确实需要拿到 Blob 的地方。<iframe>/<img>/<a href> 直接吃 url 的场景不经过本函数。
 *   走 requestClient 的接口请继续用 requestClient.download —— 那条链路的
 *   「Blob → JSON 错误还原」拦截器已在 #/api/request 里统一实现，无需重复。
 */
export interface FetchFileBlobOptions {
  /**
   * 目标本身就是 JSON 文件（.json 附件）时传 true，跳过错误包体嗅探。
   * 默认 false：content-type 含 json 一律按框架错误包体抛错。
   */
  allowJson?: boolean;
  /** 是否附加登录态；默认自动判断（含 /admin-api 或同源时附加；附加到 @PermitAll 端点无副作用） */
  auth?: boolean;
}

/** 框架统一响应包体（失败时 code ≠ 0） */
interface FrameworkBody {
  code?: number;
  msg?: string;
}

/** 地址是否需要登录态：/admin-api 出口，或同源请求（对象存储等第三方裸地址不附加） */
function shouldAttachAuth(url: string): boolean {
  if (url.includes('/admin-api')) return true;
  try {
    return new URL(url, window.location.href).origin === window.location.origin;
  } catch {
    return false;
  }
}

/**
 * 取文件字节；失败一律 throw（message 为后端 msg，可直接展示给用户）。
 * @param url 文件地址（业务受控出口 /admin-api/bidding/file/download?id= 或平台文件 url）
 */
export async function fetchFileBlob(
  url: string,
  options: FetchFileBlobOptions = {},
): Promise<Blob> {
  const headers: Record<string, string> = { Accept: '*/*' };
  if (options.auth ?? shouldAttachAuth(url)) {
    try {
      const { useAccessStore } = await import('@vben/stores');
      const accessStore = useAccessStore();
      const token = accessStore.accessToken;
      const tenantId = accessStore.tenantId;
      if (token) headers.Authorization = `Bearer ${token}`;
      if (tenantId !== null && tenantId !== undefined) {
        headers['tenant-id'] = String(tenantId);
      }
    } catch {
      /* stores 未就绪：按匿名取件（@PermitAll 端点仍可读） */
    }
  }

  const res = await fetch(url, { headers });
  // HTTP 层失败（网关 502/504、Nginx 404）：只有这时状态码才有意义
  if (!res.ok) throw new Error(`HTTP ${res.status}`);

  // 关键一步：HTTP 200 也可能是 {code:401,msg:'账号未登录'} 这类错误包体，
  // 必须按 content-type 嗅探，绝不能直接当文件字节用
  const contentType = (res.headers.get('content-type') || '').toLowerCase();
  if (!options.allowJson && contentType.includes('json')) {
    let body: FrameworkBody | null = null;
    try {
      body = (await res.json()) as FrameworkBody;
    } catch {
      /* 包体不是 JSON：body 保持 null，按未知错误码处理 */
    }
    const code = body?.code;
    throw new Error(
      body?.msg ||
        (code === 401
          ? '账号未登录（请重新登录后再试）'
          : `接口返回错误包体，code=${code ?? '未知'}`),
    );
  }

  const blob = await res.blob();
  if (blob.size === 0) throw new Error('取到的文件为空（0 字节）');
  return blob;
}

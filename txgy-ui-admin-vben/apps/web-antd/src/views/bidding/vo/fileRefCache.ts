/* 上传回执缓存（fileId → { url, name }）
 *
 * 为什么需要它：
 *   · 业务上传统一走 POST /bidding/file/upload，回执含 { fileId, url, name, type, size, sha256 }；
 *     但**保存进业务表后只留 fileId**（如 cert_lifecycle.evidenceFileIds），详情出参也只有 fileId。
 *   · 后端 CertLifecycleFileRespVO 注释已说明：佐证材料是 FileApi 主键，infra 未开放「按 id 查元数据」（仅按 URL），
 *     故本地台账解析不出 fileName/url —— 前端可经「上传回执缓存」补齐展示（对账文档 31 号 §三 迁移顺序）。
 *   · 按 id 换 URL 的既有兜底（GET /infra/file/get）需 infra:file:query，业务账号通常没有，因此缓存是首选通道。
 *
 * 口径：
 *   · 仅缓存**本浏览器上传过**的文件（上传成功即写入）；未命中则回退 PdfPreview 的 fileId 分支查询；
 *   · key 用固定前缀 + fileId，值为 { url, name, at }（at 用于淘汰，保留最近 500 条）；
 *   · 隐私模式/容量不足时静默忽略（不影响上传主流程）。
 * 定位：待后端提供「fileId → {url,name,size,sha256,virusStatus}」的 bidding 读接口（对账文档 31 号建议 P0）后，
 *   本文件可删除或降级为纯离线兜底。
 */

export interface FileRef {
  /** 可直接访问的 URL（上传回执原样保存） */
  url: string;
  /** 文件名（上传回执 name，或调用方给的本地名） */
  name?: string;
  /** 写入时间戳（毫秒），用于容量淘汰 */
  at: number;
}

const PREFIX = 'bid-fileref-v1:';
const MAX_ENTRIES = 500;

function key(fileId: number | string): string {
  return PREFIX + String(fileId);
}

/** 记录一次上传回执（上传成功后调用） */
export function rememberFileRef(fileId: number | string | null | undefined, url: string | null | undefined, name?: string): void {
  if (fileId === null || fileId === undefined || fileId === '' || !url) return;
  try {
    localStorage.setItem(key(fileId), JSON.stringify({ url, name: name || undefined, at: Date.now() } satisfies FileRef));
    pruneIfNeeded();
  } catch {
    /* 隐私模式/容量不足：忽略，不影响上传主流程 */
  }
}

/** 读取某个 fileId 的回执（未命中返回 null） */
export function recallFileRef(fileId: number | string | null | undefined): FileRef | null {
  if (fileId === null || fileId === undefined || fileId === '') return null;
  try {
    const raw = localStorage.getItem(key(fileId));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as FileRef;
    return parsed && typeof parsed.url === 'string' && parsed.url ? parsed : null;
  } catch {
    return null;
  }
}

/** 批量回执（附件行渲染用：一次取多份，避免逐条 parse） */
export function recallFileRefs(fileIds: Array<number | string> | null | undefined): Map<string, FileRef> {
  const out = new Map<string, FileRef>();
  for (const id of fileIds ?? []) {
    const hit = recallFileRef(id);
    if (hit) out.set(String(id), hit);
  }
  return out;
}

/** 超过上限时按写入时间淘汰最旧的一半（避免 localStorage 无限增长） */
function pruneIfNeeded(): void {
  try {
    const entries: Array<{ k: string; at: number }> = [];
    for (let i = 0; i < localStorage.length; i += 1) {
      const k = localStorage.key(i);
      if (!k || !k.startsWith(PREFIX)) continue;
      let at = 0;
      try {
        at = (JSON.parse(localStorage.getItem(k) || '{}') as FileRef).at || 0;
      } catch {
        at = 0;
      }
      entries.push({ k, at });
    }
    if (entries.length <= MAX_ENTRIES) return;
    entries.sort((a, b) => a.at - b.at);
    for (const e of entries.slice(0, entries.length - MAX_ENTRIES)) localStorage.removeItem(e.k);
  } catch {
    /* 忽略 */
  }
}

/* 附件上传（VO 无关的传输层）：返回 { url, fileId, sha256?, name, type, size }
 * 规范依据：《11-文件上传与file_id契约-2026-09-10.md》§3.2 —— 单一链路，禁止"仅 URL"回退。
 *   · fileId 由类型保证必填（拿不到就抛错），从类型上杜绝"没有 fileId 也算上传成功"；
 *   · 首选 POST /bidding/file/upload（后端保证响应必带 fileId + url + sha256/size）；
 *   · 预签名直传（/infra/file/presigned-url → PUT → /infra/file/create）作为可选降级保留，
 *     但同样必须先拿到 file_id 才允许返回；
 *   · 两条链路都拿不到 file_id ⇒ 统一抛「文件注册失败，请重试」，绝不回退到让用户手填 ID。
 * 补充：原「/infra/file/upload 只回 URL」的兜底链路已按规范删除（该响应不含 file_id，属退化来源）。 */
import { uploadBiddingFile } from '#/api/bidding/file';
import { baseRequestClient } from '#/api/request';
import { createFile, getFilePresignedUrl } from '#/api/infra/file';

export interface UploadedFile {
  /** 附件 URL（VO AttachmentSetVO.URL，必填）——仅用于预览/展示 */
  url: string;
  /** 文件服务 file_id（业务落库关联键，红线 8 三件套）；规范 §3.2 要求必填 */
  fileId: number;
  /** 文件 SHA-256（上传接口返回；按 URL 补登记时为 null） */
  sha256?: string;
  name: string;
  type: string;
  size: number;
}

/** 统一失败文案（规范 §2.1 错误码 1_060_101_014 同口径） */
const REGISTER_FAILED = '文件注册失败，请重试';

/** 生成稳定文件名（与既有上传组件同法：随机 + 原扩展名，避免中文/重名） */
function safeName(file: File): string {
  const dot = file.name.lastIndexOf('.');
  const ext = dot >= 0 ? file.name.slice(dot) : '';
  const rand = Math.random().toString(36).slice(2, 10);
  const ts = Date.now().toString(36);
  return 'att-' + ts + '-' + rand + ext;
}

function fileIdOf(v: unknown): number | undefined {
  return typeof v === 'number' && Number.isFinite(v) ? v : undefined;
}

/** 上传单个文件：主链路 /bidding/file/upload；降级链路预签名直传（同样必须拿到 file_id） */
export async function uploadAttachment(file: File, directory: string): Promise<UploadedFile> {
  const base = {
    name: file.name,
    type: file.type || 'application/octet-stream',
    size: file.size,
  };
  // 主链路：bidding 统一上传入口（后端保证 fileId 必带，缺失即报错）
  try {
    const r = await uploadBiddingFile(file, directory);
    const fileId = fileIdOf(r?.fileId);
    if (fileId !== undefined && r?.url) {
      return {
        ...base,
        url: r.url,
        fileId,
        sha256: r.sha256 ?? undefined,
        name: r.name || base.name,
        type: r.type || base.type,
        size: typeof r.size === 'number' && r.size > 0 ? r.size : base.size,
      };
    }
  } catch (e) {
    /* 后端已明确报错（含 1_060_101_013/014/015）时直接抛出，不再降级掩盖问题 */
    const msg = (e as Error)?.message || String(e);
    if (/文件注册失败|文件为空|文件地址无效|1_060_101_/.test(msg)) throw new Error(msg);
  }
  // 降级链路：预签名直传 + /infra/file/create 落库（必须拿到 file_id）
  try {
    const pre = await getFilePresignedUrl(safeName(file), directory);
    if (pre?.uploadUrl && pre?.url) {
      await baseRequestClient.put(pre.uploadUrl, file, { headers: { 'Content-Type': file.type } });
      const created = await createFile({
        configId: pre.configId,
        path: pre.path,
        name: file.name,
        url: pre.url,
        type: file.type,
        size: file.size,
      } as never);
      const fileId = fileIdOf(created);
      if (fileId !== undefined) {
        return { ...base, url: pre.url, fileId };
      }
    }
  } catch {
    /* 直传链路不可用：落到统一报错 */
  }
  // 两条链路都拿不到 file_id：明确报错（规范要求，绝不返回"仅 URL"的成功）
  throw new Error(REGISTER_FAILED);
}

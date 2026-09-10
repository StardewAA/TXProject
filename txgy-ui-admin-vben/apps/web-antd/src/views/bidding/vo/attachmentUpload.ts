/* 附件上传（VO 无关的传输层）：返回 { url, fileId?, sha256?, size, name, type }
 * 链路优先级（自上而下，前一条失败自动回落后一条）：
 *  1) POST /bidding/file/upload（2026-09-10 后端新增，bidding 统一上传入口）：响应必带 file_id + url + sha256/size；
 *     这是业务表落库与"上传即可预览"的正路（不再依赖需 infra:file:query 的 /infra/file/get）。
 *  2) 前端直传（预签名）：GET /infra/file/presigned-url → PUT uploadUrl → POST /infra/file/create（回 file_id）。
 *  3) 后端上传兜底：POST /infra/file/upload —— 只回 URL，无 file_id（仅可落 AttachmentSetVO.URL）。
 * 说明：公告发布强制 pdfFileId 非空，故链路 1/2 优先；链路 3 命中时调用方会提示 file_id 缺失。 */
import { uploadBiddingFile } from '#/api/bidding/file';
import { baseRequestClient } from '#/api/request';
import { createFile, getFilePresignedUrl, uploadFile } from '#/api/infra/file';

export interface UploadedFile {
  /** 附件 URL（VO AttachmentSetVO.URL，必填） */
  url: string;
  /** 文件服务 file_id（链路 1/2 可得；链路 3 为 undefined） */
  fileId?: number;
  /** 文件 SHA-256（链路 1 可得；用于招标公告版式文件三件套 pdfFileId/pdfSha256/pdfSize） */
  sha256?: string;
  name: string;
  type: string;
  size: number;
}

/** 生成稳定文件名（与既有上传组件同法：随机 + 原扩展名，避免中文/重名） */
function safeName(file: File): string {
  const dot = file.name.lastIndexOf('.');
  const ext = dot >= 0 ? file.name.slice(dot) : '';
  const rand = Math.random().toString(36).slice(2, 10);
  const ts = Date.now().toString(36);
  return 'att-' + ts + '-' + rand + ext;
}

/** 上传单个文件：链路 1（bidding 统一入口，必带 file_id）→ 链路 2（预签名直传）→ 链路 3（仅 URL） */
export async function uploadAttachment(file: File, directory: string): Promise<UploadedFile> {
  const base: UploadedFile = { url: '', name: file.name, type: file.type || 'application/octet-stream', size: file.size };
  // 链路 1：bidding 统一上传入口（fileId 为权威落库关联键）
  try {
    const r = await uploadBiddingFile(file, directory);
    if (r?.fileId !== undefined && r?.fileId !== null && r.url) {
      return {
        ...base,
        url: r.url,
        fileId: r.fileId,
        sha256: r.sha256 ?? undefined,
        type: r.type || base.type,
        size: typeof r.size === 'number' && r.size > 0 ? r.size : base.size,
        name: r.name || base.name,
      };
    }
  } catch {
    /* 接口未上线/无权限：回落后续链路 */
  }
  // 链路 2：预签名直传 + 落库
  try {
    const pre = await getFilePresignedUrl(safeName(file), directory);
    if (pre?.uploadUrl && pre?.url) {
      await baseRequestClient.put(pre.uploadUrl, file, { headers: { 'Content-Type': file.type } });
      const id = await createFile({
        configId: pre.configId,
        path: pre.path,
        name: file.name,
        url: pre.url,
        type: file.type,
        size: file.size,
      } as never);
      return { ...base, url: pre.url, fileId: typeof id === 'number' ? id : undefined };
    }
  } catch {
    /* 未配置直传/无权限：走链路 3 */
  }
  // 链路 3：后端上传（返回 URL 字符串，无 file_id）
  const res = await uploadFile({ file, directory } as never);
  const url = typeof res === 'string' ? res : (res as { url?: string })?.url;
  if (!url) throw new Error('上传未返回文件 URL');
  return { ...base, url };
}

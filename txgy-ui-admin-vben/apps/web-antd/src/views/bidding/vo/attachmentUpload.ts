/* 附件上传（VO 无关的传输层）：返回 { url, fileId?, size, name, type }
 * 链路依据（前端既有能力）：
 *  1) 前端直传（预签名）：GET /infra/file/presigned-url → PUT uploadUrl → POST /infra/file/create（回 file_id）
 *  2) 后端上传兜底：POST /infra/file/upload —— 后端实现 CommonResult<String>，只回 URL，无 file_id
 * 附件 VO（AttachmentSetVO）以 URL 为必填字段，故两条链路都能落 VO；file_id 仅在链路 1 可得。
 */
import { baseRequestClient } from '#/api/request';
import { createFile, getFilePresignedUrl, uploadFile } from '#/api/infra/file';

export interface UploadedFile {
  /** 附件 URL（VO AttachmentSetVO.URL，必填） */
  url: string;
  /** 文件服务 file_id（链路 1 可得；链路 2 为 undefined） */
  fileId?: number;
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

/** 上传单个文件：优先预签名直传（可得 file_id），失败回落后端上传（只得 URL） */
export async function uploadAttachment(file: File, directory: string): Promise<UploadedFile> {
  const base: UploadedFile = { url: '', name: file.name, type: file.type || 'application/octet-stream', size: file.size };
  // 链路 1：预签名直传 + 落库
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
    /* 未配置直传/无权限：走链路 2 */
  }
  // 链路 2：后端上传（返回 URL 字符串）
  const res = await uploadFile({ file, directory } as never);
  const url = typeof res === 'string' ? res : (res as { url?: string })?.url;
  if (!url) throw new Error('上传未返回文件 URL');
  return { ...base, url };
}

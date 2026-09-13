import { requestClient } from '#/api/request';

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
  size?: number;
  /** 文件 SHA-256（红线 8 三件套之一；按 URL 补登记时为 null） */
  sha256?: string;
}

/** 上传文件（响应体必带 fileId；缺失时后端已返回「文件注册失败，请重试」） */
export function uploadBiddingFile(file: File, directory?: string) {
  const data: { directory?: string; file: File } = { file };
  if (directory) data.directory = directory;
  return requestClient.upload<BiddingFileResp>('/bidding/file/upload', data);
}

/** 按已上传地址补登记 file_id（历史"只有 URL 没有 file_id"数据的修复通道） */
export function registerBiddingFileByUrl(url: string) {
  return requestClient.post<BiddingFileResp>('/bidding/file/register-by-url', null, {
    params: { url },
  });
}

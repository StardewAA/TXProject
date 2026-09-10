import { requestClient } from '#/api/request';

/** 契约来源：txgy-module-bidding BiddingFileController（2026-09-10 后端新增）
 *  POST /bidding/file/upload         → { fileId, url, name, type, size, sha256 }
 *  POST /bidding/file/register-by-url → 按已上传地址补登记 file_id（历史只有 URL 的数据修复通道）
 *  设计口径：fileId 是业务表落库关联键（权威），url 仅用于上传后即时预览；
 *  两者同时返回可免去前端再调 /infra/file/get（该接口需 infra:file:query，业务账号通常没有）。
 *  权限：isAuthenticated()（前台登录即可用）。 */
export namespace BiddingFileApi {
  export interface Uploaded {
    /** 文件编号（infra_file.id，业务表落库关联键） */
    fileId: number;
    /** 文件访问地址（平台文件服务 URL，用于即时预览） */
    url: string;
    name?: string;
    type?: string;
    size?: number;
    /** 文件 SHA-256（红线 8 三件套之一；按 URL 补登记时为 null） */
    sha256?: string;
  }
}

/** 上传文件并返回 file_id（bidding 全部业务表单的统一上传入口） */
export function uploadBiddingFile(file: File, directory?: string) {
  const data: { directory?: string; file: File } = { file };
  if (directory) data.directory = directory;
  return requestClient.upload<BiddingFileApi.Uploaded>('/bidding/file/upload', data);
}

/** 按已上传地址补登记 file_id（历史「只有 URL 没有 file_id」数据的修复通道） */
export function registerBiddingFileByUrl(url: string) {
  return requestClient.post<BiddingFileApi.Uploaded>('/bidding/file/register-by-url', undefined, {
    params: { url },
  });
}

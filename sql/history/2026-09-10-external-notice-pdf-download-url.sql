-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;

-- 外部公告 PDF 归档访问地址（2026-09-10）：平台文件服务上传返回的 URL，前端预览/下载直接可用
-- 背景：爬虫侧 FileApi RPC 上传不返回 Long fileId（fileId 仅前端直传登记模式有），故新增本列承载文件访问；pdf_file_id 保持 NULL
-- 落库时机：爬取下载 PDF 后即上传平台文件服务并回填；存量 PDF（GX 14 条）由回填任务重下载后补传
ALTER TABLE `bidding_notice_external` ADD COLUMN `pdf_download_url` varchar(1024) NULL COMMENT 'PDF 归档访问地址（平台文件服务 URL）' AFTER `pdf_size`;

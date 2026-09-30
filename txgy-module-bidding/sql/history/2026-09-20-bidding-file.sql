-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 文件登记（bidding_file）：把「file_id → 存储坐标」收在招投标侧，支持按 id 取文件
-- 依据：《招投标模块开发规范》§5 三-5（业务表只记 file_id/sha256/size；url 为回显快照、不作一致性依据）
--   + 红线 8（不存裸 url）+ 红线 6（禁止跨库 SQL，故不读 infra_file 表）
--   + 《文件接口收敛方案-后端实施篇-2026-09-20》：上传走 /bidding/file/upload，取文件走 /bidding/file/download
-- 设计说明：
--   ① 本表存的是**存储坐标**（config_id + path），不是可访问 url——坐标单独拿去无法访问，
--      必须经 infra 下载端点解析，故不构成"存裸 url"（红线 8 不破）；
--   ② 坐标由上传/补登记时从 FileApi 返回的 url 解析得出（url 形如 /infra/file/{configId}/get/{path}）；
--   ③ virus_status 按外部公告附件参考实现统一（UNKNOWN/CLEAN/INFECTED），INFECTED 拒绝下载。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `bidding_file` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID',
  `file_id` bigint NOT NULL COMMENT '文件编号（infra_file.id；业务表落库关联键）',
  `config_id` bigint DEFAULT NULL COMMENT '存储配置编号（infra 文件配置；解析自 url）',
  `path` varchar(512) DEFAULT NULL COMMENT '存储路径（对象存储内相对路径；非可访问地址）',
  `name` varchar(256) DEFAULT NULL COMMENT '原始文件名',
  `type` varchar(128) DEFAULT NULL COMMENT 'MIME 类型',
  `size` bigint DEFAULT NULL COMMENT '文件大小（字节）',
  `sha256` char(64) DEFAULT NULL COMMENT '文件 SHA-256（防篡改/去重；按 URL 补登记时无法计算，为 NULL）',
  `virus_status` varchar(16) NOT NULL DEFAULT 'UNKNOWN' COMMENT '病毒扫描状态（UNKNOWN 未知 / CLEAN 安全 / INFECTED 感染；INFECTED 拒绝下载）',
  `source` varchar(16) NOT NULL DEFAULT 'UPLOAD' COMMENT '登记来源（UPLOAD 上传 / BY_URL 按地址补登记 / SERVER 服务端生成）',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bidding_file_file_id` (`file_id`,`deleted`),
  KEY `idx_bidding_file_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件登记表（file_id → 存储坐标；招投标侧文件读出口的事实基础）';

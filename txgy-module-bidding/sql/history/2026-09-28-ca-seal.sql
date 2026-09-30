-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 招投标模块：CA 文件签章台账（测试直接入库，演示/验签闭环的数据基础）
-- 依据：《电子招标投标办法》第20/30条（电子签名与签章）；《电子签名法》第13/14条（可靠电子签名）
-- 登记日期：2026-09-28
-- ============================================================================

-- CA 签章记录表（每次文件签章直接落库一条；承载签章证据链 + 验签状态）
CREATE TABLE IF NOT EXISTS `bidding_ca_seal` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID',
  `document_id` varchar(64) NOT NULL COMMENT '签章会话 documentId（SEAL-DOC-xxxx）',
  `document_name` varchar(255) NOT NULL COMMENT '原文件名',
  `document_type` varchar(16) NOT NULL DEFAULT 'other' COMMENT '原文件类型（pdf / other；pdf 走 PDF 红章，其他走分离式签名信息文件）',
  `file_id` bigint NOT NULL COMMENT '原文件 fileId（FileApi 主键）',
  `file_url` varchar(512) NOT NULL COMMENT '原文件网络路径',
  `file_sha256` varchar(128) DEFAULT NULL COMMENT '原文件 SHA-256',
  `sm3_hash` varchar(64) NOT NULL COMMENT '文件 SM3 指纹（GB/T 32905，签名摘要）',
  `signature` varchar(1024) DEFAULT NULL COMMENT 'SM2 签名值（base64，对文件 SM3 指纹签名，真实可验）',
  `cert_sn` varchar(128) NOT NULL COMMENT 'CA 证书序列号（统一测试账号 SN-LOCAL-CA-TEST-0001）',
  `org_name` varchar(255) NOT NULL COMMENT '签章单位名称',
  `seal_time` varchar(32) NOT NULL COMMENT '签章时间（yyyy-MM-dd HH:mm:ss）',
  `seal_file_id` bigint DEFAULT NULL COMMENT '已签章文件 fileId（PDF=盖章 PDF；其他=签名信息 .sig.txt）',
  `seal_file_url` varchar(512) DEFAULT NULL COMMENT '已签章文件网络路径',
  `seal_file_name` varchar(255) DEFAULT NULL COMMENT '已签章文件名',
  `track_id` bigint DEFAULT NULL COMMENT '关联投标流程跟踪 id（businessCode 可解析时）',
  `verify_status` varchar(16) NOT NULL DEFAULT 'UNVERIFIED' COMMENT '验签状态（UNVERIFIED 未验 / PASS 通过 / FAIL 不通过）',
  `verify_time` datetime DEFAULT NULL COMMENT '验签时间',
  `operator_user_id` bigint DEFAULT NULL COMMENT '操作人用户ID（谁发起的签章）',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_document_id` (`document_id`),
  KEY `idx_document_name` (`document_name`),
  KEY `idx_verify_status` (`verify_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CA 文件签章台账（测试直接入库；SM2 真实签名 + PDF 红章/分离式签名信息文件 + 验签状态）';

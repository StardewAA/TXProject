-- =====================================================================
-- MES 安全环保检测（SET）- 通用业务附件表
-- 表：mes_set_attachment
-- 语义：一条业务单据挂 N 个附件，以 (biz_type,biz_no) 关联业务对象（与 mes_set_sign_record 同口径，
--       共用取值：CHECK(污染判定)/LEDGER(台账)/MANIFEST(危废联单)/FACILITY(治污设施)/EMERGENCY(应急事件)
--       以及危化品等既有业务类型码）。
-- 说明：
--   1. 文件本体在 infra 文件服务（infra_file），本表只存访问地址与展示用文件名——不搬字节。
--   2. 公共 7 列与 SET 其余表一致。
--   3. file_url 512：infra 本地存储回的是完整 http 地址，比签字表 sign_img(255) 长。
-- 执行：mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < attachment-20260910-ddl.sql
-- =====================================================================

CREATE TABLE IF NOT EXISTS `mes_set_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `biz_type` varchar(32) NOT NULL COMMENT '业务关联类型：CHECK/LEDGER/MANIFEST/FACILITY/EMERGENCY 等（与签字表同口径）',
  `biz_no` varchar(64) NOT NULL COMMENT '业务关联单号（判定 recordNo / 台账号 / 联单号 / 设施编号 等）',
  `file_name` varchar(255) NOT NULL COMMENT '原始文件名（展示用）',
  `file_url` varchar(512) NOT NULL COMMENT '文件访问地址（infra 文件服务返回）',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_biz` (`biz_type`, `biz_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MES 安全环保检测-通用业务附件';

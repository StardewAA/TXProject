# ================================================================
# bidding 通用附件表（项目/招标项目/标段/公告 一对 N 附件）
# 规范依据：《招投标模块开发规范》§5 三-5（文件经 FileApi，库内只记 file_id+sha256+size，URL 非一致性依据）
#   + 红线 8（附件三件套）+ api/vo/AttachmentSetVO（附件集：ATTACHMENT_GUID/SET_CODE/NAME/TYPE/FILE_NAME/URL，URL=社交公开）
#   + api/vo/AttachmentItemVO（35 类附件类型字典：TENDER_FILE/招标文件、WIN_BID_NOTICE/中标通知…）
# 用户裁定 2026-09-24：附件往往不止一个 → 针对主键的专门附件表（一对 N）；以 URL 方式承载、对象存储存储。
# 关联方式：biz_type + biz_code（业务编号）一对 N；不侵入四实体表结构。
# 日期：2026-09-27
# ================================================================

CREATE TABLE IF NOT EXISTS `bidding_attachment` (
  `id`                 bigint      NOT NULL COMMENT '主键（雪花）',
  `biz_type`           varchar(32) NOT NULL COMMENT '归属实体：PROJECT/TENDER_PROJECT/BID_SECTION/ANNOUNCEMENT',
  `biz_code`           varchar(64) NOT NULL COMMENT '归属实体业务编号（project_code/tender_project_code/bid_section_code/notice_code）',
  `attachment_type`    varchar(64) DEFAULT NULL COMMENT '附件类型（35 类字典 AttachmentItemVO：TENDER_FILE/WIN_BID_NOTICE…）',
  `attachment_name`    varchar(255) DEFAULT NULL COMMENT '附件集名称（AttachmentSetVO.ATTACHMENT_NAME）',
  `file_id`            bigint      NOT NULL COMMENT 'FileApi 文件主键（一致性依据）',
  `url`                varchar(512) DEFAULT NULL COMMENT '对象存储访问地址（SOCIAL 公开，规范 AttachmentSetVO.URL；预览用）',
  `file_name`          varchar(255) DEFAULT NULL COMMENT '文件名（AttachmentSetVO.ATTACHMENT_FILE_NAME）',
  `sha256`             varchar(64) DEFAULT NULL COMMENT '文件指纹（红线 8 三件套）',
  `size`               bigint      DEFAULT NULL COMMENT '字节数',
  `mime_type`          varchar(64) DEFAULT NULL COMMENT 'MIME 类型',
  `virus_status`       varchar(16) NOT NULL DEFAULT 'UNKNOWN' COMMENT '病毒扫描：UNKNOWN/CLEAN/INFECTED（对齐外部公告）',
  `seq`                int         NOT NULL DEFAULT 0 COMMENT '同实体内附件序号（展示顺序）',
  `remark`             varchar(255) DEFAULT NULL COMMENT '备注',
  `creator`            varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time`        datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`            varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time`        datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            bit(1)      NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`          bigint      NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_biz` (`biz_type`, `biz_code`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='bidding 通用附件（项目/招标项目/标段/公告 一对 N）';

# 注意：执行后请同步登记到 sql/master-bidding-sync.sql 的 tables_ok 清单（由契约代理合并时处理，勿手动双改）。

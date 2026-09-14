-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;

-- =====================================================================
-- 主体信息与授权域（subject）建表脚本 —— txgy-module-bidding 小模块
-- 依据：《招投标模块开发规范》第五章（表结构分轨/审计列/唯一键含 deleted）；
--       法规/数据集依据见 docs/法规依据/00-索引与引用说明.md：
--       招标投标法 第8/25/26条、实施条例 第19条、电子招标投标办法 第9/13/14/24条、
--       招标公告和公示信息发布管理办法 第5/6条；国家+陕西 V2.0 数据规范 表89 法人基本信息。
-- 需求口径：授权 ≠ 完善 —— 管理端授权主体（bidding_subject_auth），被授权企业仍须完善档案
--       （bidding_subject_profile 必填项全齐=complete_flag）后方可投标/发布；关注/订阅/浏览仅需权限。
-- 分轨：bidding_subject_profile = T 轨（TenantBaseDO，租户各自维护本企业档案，租户拦截器自动隔离）；
--       bidding_subject_auth / bidding_subject_user = G 轨（平台级，带 tenant_id 手动过滤）。
-- =====================================================================

-- 1) 主体信息档案（T 轨；一租户一类型一档案：UNIQUE(tenant_id, subject_type, deleted)）
CREATE TABLE IF NOT EXISTS `bidding_subject_profile` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号（企业=租户）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `subject_type` varchar(16) NOT NULL COMMENT '主体类型：BIDDER 投标人 / TENDERER 招标人（SubjectTypeEnum）',
    `subject_name` varchar(200) NOT NULL COMMENT '单位名称（表89 LEGAL_NAME，必填）',
    `uscc` char(18) NOT NULL COMMENT '统一社会信用代码（表89 LEGAL_CODE；GB 32100-2015 含校验码，保存强校验）',
    `legal_person_name` varchar(50) NOT NULL COMMENT '法定代表人姓名（表89 LEGAL_REPRESENT，必填）',
    `legal_person_type_code` varchar(2) NOT NULL COMMENT '法人机构类别代码（表89 LEGAL_TYPE C1，必填）',
    `region_code` char(6) NOT NULL COMMENT '行政区划代码（表89 REGION_CODE C6，GB/T 2260，必填）',
    `industry_code` varchar(3) DEFAULT NULL COMMENT '行业代码（表89 LEGAL_INDUSTRY C3，GB/T 4754-2011，可选）',
    `reg_capital` decimal(20,6) DEFAULT NULL COMMENT '注册资本（表89 REG_CAPITAL，可选）',
    `reg_cap_currency` char(3) DEFAULT '156' COMMENT '注册资本币种数字码（GB/T 12406-2008，默认 156 人民币）',
    `contact_name` varchar(50) NOT NULL COMMENT '联系人（表89 LEGAL_CONTACT，必填）',
    `contact_phone` varchar(20) NOT NULL COMMENT '联系人电话（表89 LEGAL_CONTACT_PHONE，必填）',
    `license_file_id` bigint DEFAULT NULL COMMENT '营业执照影像 file_id（FileApi 对象主键；实名凭据，可选）',
    `license_file_name` varchar(255) DEFAULT NULL COMMENT '营业执照影像附件名称',
    `ext_json` json DEFAULT NULL COMMENT '权威扩展字段 JSON（key=规范英文名称，可选采集）',
    `complete_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否信息完善（必填清单=SubjectCompleteness 实时核算）',
    `complete_time` datetime DEFAULT NULL COMMENT '最近一次达到完善的时间（变不完整则清空）',
    `remark` varchar(255) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_subject_profile_type` (`tenant_id`, `subject_type`, `deleted`),
    KEY `idx_subject_profile_uscc` (`uscc`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '主体信息档案（投标人/招标人，T 轨）';

-- 2) 主体授权（G 轨；平台管理端核发/停用；撤销=DISABLED 留痕；UNIQUE(tenant_id, subject_type, deleted)）
CREATE TABLE IF NOT EXISTS `bidding_subject_auth` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者（平台操作人）',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id` bigint NOT NULL COMMENT '被授权企业租户编号',
    `tenant_name` varchar(128) DEFAULT NULL COMMENT '企业名称快照（授权时点取值，列表免联查）',
    `subject_type` varchar(16) NOT NULL COMMENT '主体类型：BIDDER/TENDERER',
    `auth_status` varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '授权状态：ENABLED/DISABLED（SubjectAuthStatusEnum）',
    `authorize_user_id` bigint DEFAULT NULL COMMENT '授权操作人（AdminUser id）',
    `authorize_time` datetime DEFAULT NULL COMMENT '授权（或最近一次启用）时间',
    `disable_time` datetime DEFAULT NULL COMMENT '停用（撤销授权）时间',
    `remark` varchar(255) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_subject_auth_tenant_type` (`tenant_id`, `subject_type`, `deleted`),
    KEY `idx_subject_auth_status` (`auth_status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '主体授权记录（投标人/招标人，G 轨）';

-- 3) 主体经办用户绑定（G 轨；平台管理端为已授权主体维护其经办账号）
CREATE TABLE IF NOT EXISTS `bidding_subject_user` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id` bigint NOT NULL COMMENT '主体所属企业租户',
    `subject_type` varchar(16) NOT NULL COMMENT '主体类型：BIDDER/TENDERER',
    `user_id` bigint NOT NULL COMMENT '经办用户 id（system AdminUser）',
    `user_nickname` varchar(128) DEFAULT NULL COMMENT '用户昵称快照',
    `user_role` varchar(16) NOT NULL DEFAULT 'OPERATOR' COMMENT '经办角色：OPERATOR（经办/投标操作人，默认）/CONTACT（联系人）',
    `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '启用开关（停用=解绑保留历史）',
    `remark` varchar(255) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_subject_user_bind` (`tenant_id`, `subject_type`, `user_id`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '主体经办用户绑定（G 轨）';

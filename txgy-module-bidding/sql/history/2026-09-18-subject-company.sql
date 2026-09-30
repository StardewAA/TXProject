-- =====================================================================
-- 所属企业（bidding_subject_company）—— 建表 + 字典 + 权限点
-- 依据：《招投标模块开发规范》§3.1/§4.2/§4.4/§4.5/§5 二/§5 五；《后端接口契约-资质证书五页改造》§2（新增实体 A）
-- 业务口径（用户 2026-09-18）：「所属企业」不是租户。租户编号在超管的「全局租户选择」里属平台级；
--   本实体是**租户自己维护的企业名录**（本企业 SELF + 子公司 SUBSIDIARY / 分公司 BRANCH），
--   用于把证照/资质按"登记在哪家公司名下"归集（持证主体选择、看板与筛选维度）。
-- 执行库：bidding 库（与 certificate_info 同库）；菜单/权限点在 system 库（见下方 system_menu 段）。
-- =====================================================================

-- 1. 所属企业表（T 轨）
CREATE TABLE IF NOT EXISTS `bidding_subject_company` (
  `id`                 bigint       NOT NULL COMMENT '主键（雪花 ID）',
  `company_name`       varchar(128) NOT NULL COMMENT '企业名称（对应规范字段「单位名称」；档位 128）',
  `uscc`               varchar(32)  DEFAULT NULL COMMENT '统一社会信用代码（GB32100-2015 18 位；L2）',
  `company_type`       varchar(16)  NOT NULL DEFAULT 'SUBSIDIARY' COMMENT '企业类型 code（字典 subject_company_type：SELF/SUBSIDIARY/BRANCH）',
  `parent_id`          bigint       NOT NULL DEFAULT 0 COMMENT '上级企业 id（顶级为 0；用于"本企业→子公司"从属）',
  `parent_name`        varchar(128) DEFAULT NULL COMMENT '上级企业名称快照（列表展示免联表；源变更不回改）',
  `legal_person_name`  varchar(64)  DEFAULT NULL COMMENT '法定代表人姓名（L3，日志脱敏）',
  `contact_name`       varchar(64)  DEFAULT NULL COMMENT '联系人（L3）',
  `contact_phone`      varchar(32)  DEFAULT NULL COMMENT '联系电话（L3，列表默认掩码）',
  `region_code`        varchar(32)  DEFAULT NULL COMMENT '行政区划代码',
  `industry_code`      varchar(32)  DEFAULT NULL COMMENT '所属行业代码',
  `remark`             varchar(512) DEFAULT NULL COMMENT '备注',
  `enabled`            bit(1)       NOT NULL DEFAULT b'1' COMMENT '是否启用（停用不出现在选择器，不影响历史归属）',
  `version`            int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `tenant_id`          bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
  `creator`            varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`            varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_company_uscc` (`uscc`, `deleted`, `tenant_id`) COMMENT '同租户内 USCC 唯一（USCC 为空时后端按名称判重）',
  KEY `idx_company_name` (`company_name`),
  KEY `idx_company_parent` (`parent_id`, `tenant_id`)
) ENGINE = InnoDB COMMENT = '所属企业（租户自维护的企业名录：本企业 + 子公司/分公司）';

-- 2. 证照归属列扩展（把"登记在哪家公司名下"落到列上；存量 company_id 为空=未归属，前端显示 —）
-- 2026-09-20 修正：原写法用了 MariaDB 专有语法 `ADD COLUMN IF NOT EXISTS` / `ADD KEY IF NOT EXISTS`，
--   本库是 MySQL 8.4，不支持 → 报 1064 语法错。MySQL 侧 ALTER 无 IF NOT EXISTS 写法，
--   与 09-17 保持一致：ALTER 为一次性增量（重复执行会报 Duplicate column），只跑一次即可。
ALTER TABLE `certificate_info`
  ADD COLUMN `company_id`   bigint       DEFAULT NULL COMMENT '所属企业 id（bidding_subject_company.id）',
  ADD COLUMN `company_name` varchar(128) DEFAULT NULL COMMENT '所属企业名称快照（列表展示免联表）',
  ADD KEY `idx_cert_company` (`company_id`);

-- =====================================================================
-- 3. 字典：subject_company_type（与 cert_status/risk_level 同库同批）
-- =====================================================================
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(710000000000002801, 0, 'subject_company_type', 'SELF', '本企业', 'blue', b'0', 1, b'1', '每租户唯一', '1', NOW(), '1', NOW(), b'0'),
(710000000000002802, 0, 'subject_company_type', 'SUBSIDIARY', '子公司', 'default', b'0', 2, b'1', '', '1', NOW(), '1', NOW(), b'0'),
(710000000000002803, 0, 'subject_company_type', 'BRANCH', '分公司', 'default', b'0', 3, b'1', '', '1', NOW(), '1', NOW(), b'0');

-- =====================================================================
-- 4. 权限点（system 库 system_menu，挂到「资质与证书管理」目录 60002000 下）
-- =====================================================================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(60002300, '所属企业管理', 'bidding:subject-company:query', 2, 20, 60002000, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002301, '所属企业新增', 'bidding:subject-company:create', 3, 21, 60002300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002302, '所属企业修改/启停', 'bidding:subject-company:update', 3, 22, 60002300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60002303, '所属企业删除', 'bidding:subject-company:delete', 3, 23, 60002300, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

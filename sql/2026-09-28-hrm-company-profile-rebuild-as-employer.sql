-- HRM：把 `hrm_company_profile` 恢复成它本该是的「用工主体表」
--
-- 背景：这张表在本地库里装错了。它当前是**招聘企业**的结构与数据（25 行演示企业，
--   带 logo / industry / scale / website / established_date），
--   而 DO（HrmCompanyProfileDO）、权威快照（sql/hrm_202609201427.sql）与
--   管理页（views/hrm/company/index.vue「用工主体」）都把 `hrm_company_profile`
--   当作**合同甲方（用工主体）**表，需要
--   company_type / business_scope / insured_count / bank_account / establish_date / default_status 等列。
--
-- 为什么一直没暴露：deploy/mysql/repair/2026-09-21-hrm-schema-sync.sql 用
--   `CREATE TABLE IF NOT EXISTS` —— 表已存在（带着招聘结构），整段被静默跳过，
--   用工主体表从未真正建出来。
--
-- 症状（同一根因）：
--   1) 发送 Offer → createDraftForOffer → getDefaultProfile → 500
--      "Unknown column 'establish_date' in 'field list'"
--   2) 「用工主体」管理页整个不可用：列表按 default_status 排序，同样 500
--      "Unknown column 'default_status' in 'order clause'"
--
-- 重建不丢数据：旧表独有 3 家企业（2013/2014/2016 三个租户）先补进
--   hrm_recruit_company_profile，其余 22 家在新表里已有。
--
-- 执行顺序有讲究：先 trim 掉招聘表里那个带空格的重复名，否则它会被误判成
--   「只在旧表里」而插入第三份。--- 本脚本为一次性迁移，含备份，可重复执行（第二遍会因备份表已存在而报错，属预期）。

SET NAMES utf8mb4;

-- ============ 1. 备份：原表整体改名保留，不删 ============
-- 验证无误后可自行 DROP TABLE `hrm_company_profile_bak_recruit_20260928`;
RENAME TABLE `hrm_company_profile` TO `hrm_company_profile_bak_recruit_20260928`;

-- ============ 2. 修掉招聘表里带空格的重复名 ============
-- 旧表 id=8 是 `陕安防腐保温制品有限公司`（无空格），新表 id=12 是同一个名字但
-- 首尾各带一个空格，于是同一家企业（同 tenant 2008）在库里存了两份。
--
-- 判空白的 WHERE **不能**写成 `name <> TRIM(name)`：本表排序规则是
-- utf8mb4_unicode_ci，属 PAD SPACE —— 比较时忽略尾随空格，`'abc ' <> 'abc'`
-- 求值为 false，于是只抓得到「前导」空格，尾空格的整行被静默漏掉（实测漏掉 3 行）。
-- 改用 CHAR_LENGTH 之差判断，与排序规则无关。short_name 有同样的污染。
UPDATE `hrm_recruit_company_profile`
SET `name` = TRIM(`name`)
WHERE CHAR_LENGTH(`name`) <> CHAR_LENGTH(TRIM(`name`));

UPDATE `hrm_recruit_company_profile`
SET `short_name` = TRIM(`short_name`)
WHERE CHAR_LENGTH(IFNULL(`short_name`, '')) <> CHAR_LENGTH(TRIM(IFNULL(`short_name`, '')));

-- ============ 3. 把旧表独有的 3 家补进招聘表 ============
-- 两个表结构只差 financing_stage / album 两列（新表多），其余同名同义，按列拷贝。
-- id 交给自增，避免与招聘表现有 id 冲突；这两列走默认值。
INSERT INTO `hrm_recruit_company_profile`
    (`name`, `short_name`, `logo`, `industry`, `scale`, `nature`, `summary`, `products`,
     `address`, `area_id`, `longitude`, `latitude`, `website`, `established_date`,
     `registered_capital`, `credit_code`, `legal_person`, `data_source`, `data_source_url`,
     `verified`, `sort`, `status`, `creator`, `create_time`, `updater`, `update_time`,
     `deleted`, `tenant_id`)
SELECT `name`, `short_name`, `logo`, `industry`, `scale`, `nature`, `summary`, `products`,
       `address`, `area_id`, `longitude`, `latitude`, `website`, `established_date`,
       `registered_capital`, `credit_code`, `legal_person`, `data_source`, `data_source_url`,
       `verified`, `sort`, `status`, `creator`, `create_time`, `updater`, `update_time`,
       `deleted`, `tenant_id`
FROM `hrm_company_profile_bak_recruit_20260928` src
WHERE NOT EXISTS (
    SELECT 1 FROM `hrm_recruit_company_profile` dst
    WHERE dst.`name` COLLATE utf8mb4_general_ci = src.`name` COLLATE utf8mb4_general_ci
);

-- ============ 4. 重建为用工主体表 ============
-- DDL 逐字取自 sql/hrm_202609201427.sql，与 HrmCompanyProfileDO 的字段一一对应。
DROP TABLE IF EXISTS `hrm_company_profile`;
CREATE TABLE `hrm_company_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '公司名称（用工主体）',
  `credit_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '统一社会信用代码',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '住所',
  `legal_person` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '法定代表人',
  `establish_date` date DEFAULT NULL COMMENT '成立日期',
  `registered_capital` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '注册资本',
  `company_type` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '企业类型',
  `business_scope` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '经营范围',
  `insured_count` int DEFAULT NULL COMMENT '参保人数',
  `phone` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系电话',
  `bank_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '开户银行',
  `bank_account` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '银行账号',
  `commercial_insurance` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '商业保险说明（雇主责任险/团体意外险）',
  `default_status` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否默认主体',
  `sort` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_hrm_company_profile_tenant` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='HRM 公司信息（用工主体）表';

-- ============ 5. 一条默认用工主体 ============
-- 只填公司名并设为默认：合同草稿的「甲方」至少不为空。
-- 统一社会信用代码 / 法定代表人 / 开户行 / 银行账号等属于真实证照信息，不臆造，
-- 留空由 HR 在「用工主体」管理页补齐。
-- tenant 2020 = HRM 业务所在租户（该 Offer 记录即 tenant_id=2020）。
INSERT INTO `hrm_company_profile`
    (`name`, `default_status`, `sort`, `creator`, `updater`, `tenant_id`)
VALUES
    ('天信管业科技集团有限公司', b'1', 0, 'migration', 'migration', 2020);

-- ============ 核对 ============
-- SELECT id, name FROM hrm_company_profile;                                    -- 应有 1 行
-- SELECT COUNT(*) FROM hrm_recruit_company_profile;                            -- 应为 26
-- 这两条也必须用 CHAR_LENGTH 判，理由同上
-- SELECT id, CONCAT('[', name, ']') FROM hrm_recruit_company_profile
--   WHERE CHAR_LENGTH(name) <> CHAR_LENGTH(TRIM(name));                        -- 应为空
-- SELECT id, CONCAT('[', short_name, ']') FROM hrm_recruit_company_profile
--   WHERE CHAR_LENGTH(IFNULL(short_name,'')) <> CHAR_LENGTH(TRIM(IFNULL(short_name,'')));  -- 应为空

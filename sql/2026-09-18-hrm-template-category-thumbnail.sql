-- ============================================================
-- HRM 模板管理：支持按分类归集 + 缩略图 + 在线预览
--   1) hrm_contract_template 增加：分类、缩略图、预览文件、排序、来源目录
--   2) 存量数据（劳动合同类）默认归入「劳动合同」分类
-- 执行库：ruoyi-vue-pro
-- 说明：ALTER 不支持重复执行（重复执行会报列已存在，可忽略）
-- ============================================================

ALTER TABLE `hrm_contract_template`
  ADD COLUMN `category`      tinyint      NOT NULL DEFAULT 1 COMMENT '模板分类：1 劳动合同，2 个人电子档案，3 培训协议' AFTER `name`,
  ADD COLUMN `thumbnail_url` varchar(512) NULL COMMENT '缩略图地址（PNG，列表卡片展示）' AFTER `file_url`,
  ADD COLUMN `preview_url`   varchar(512) NULL COMMENT '预览文件地址（PDF，浏览器内嵌预览）' AFTER `thumbnail_url`,
  ADD COLUMN `sort`          int          NOT NULL DEFAULT 0 COMMENT '显示顺序（同分类内排序）' AFTER `default_status`,
  ADD COLUMN `source_dir`    varchar(128) NULL COMMENT '来源目录（批量导入时保留，便于追溯）' AFTER `sort`;

-- 档案类 / 培训协议类模板没有「合同类型」概念，放开非空约束
ALTER TABLE `hrm_contract_template`
  MODIFY COLUMN `contract_type` tinyint NULL COMMENT '合同类型（仅劳动合同分类使用）';

-- 存量劳动合同模板归入分类 1，并按合同类型给一个默认排序
UPDATE `hrm_contract_template`
SET `category` = 1,
    `sort` = IFNULL(`contract_type`, 99),
    `updater` = 'admin',
    `update_time` = NOW()
WHERE `deleted` = b'0' AND (`category` IS NULL OR `category` = 1);

-- 核对
SELECT `id`, `name`, `category`, `contract_type`, `employ_type`, `version`,
       `status`, `default_status`, `sort`,
       CASE WHEN `thumbnail_url` IS NULL THEN '无' ELSE '有' END AS thumb,
       CASE WHEN `preview_url` IS NULL THEN '无' ELSE '有' END AS preview
FROM `hrm_contract_template`
WHERE `deleted` = b'0'
ORDER BY `category`, `sort`, `id`;

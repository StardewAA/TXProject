-- =====================================================================
-- 2026-09-16 报表数据集支持「简单模式」（无需写 SQL）
-- 背景：数据集管理原表单要求手写 SQL，非技术岗位无法自助建报表。
-- 新增：report_dataset.column_labels 列标签 JSON（[{field,label}]），
--       由「简单模式」自动生成；报表中心据此输出中文表头，
--       未登记中文表头的数据集不出现在报表中心（避免英文字段直出）。
-- 说明：MySQL 8 会因列已存在而报错，属预期（幂等靠人工判断）。
-- =====================================================================

SET NAMES utf8mb4;

ALTER TABLE `report_dataset`
    ADD COLUMN `column_labels` varchar(2000) NOT NULL DEFAULT '' COMMENT '列标签 JSON 数组：[{field,label}]；简单模式自动生成，报表中心据此输出中文表头' AFTER `param_definitions`;

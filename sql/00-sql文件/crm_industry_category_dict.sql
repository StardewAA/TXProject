-- =============================================================
-- CRM 客户行业 / 客户类别 字典修正（txgycloud / 天信防腐保温平台）
-- 适用库：ruoyi-vue-pro
-- 设计：
--   crm_customer_industry           合并全量（下游+上游），供列表/详情 CellDict 展示
--   crm_customer_industry_downstream 下游客户行业（线索新增 级联下拉）
--   crm_customer_industry_upstream   上游供应商行业（线索新增 级联下拉）
--   crm_customer_category           客户类别（下游客户=1 / 上游供应商=2）
-- 行业取值按类别分段，便于前端“先选类别、再筛行业”级联：下游 101-110，上游 201-206
-- =============================================================

USE `ruoyi-vue-pro`;
SET NAMES utf8mb4;

-- 0) 幂等清理
DELETE FROM `system_dict_type` WHERE `type` IN ('crm_customer_industry_downstream','crm_customer_industry_upstream','crm_customer_category');
DELETE FROM `system_dict_data` WHERE `dict_type` IN ('crm_customer_industry','crm_customer_industry_downstream','crm_customer_industry_upstream','crm_customer_category');

-- 1) 字典类型
INSERT INTO `system_dict_type` (`id`, `name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`) VALUES
  (99001, 'CRM 客户类别', 'crm_customer_category', 0, 'CRM 客户类别（下游客户/上游供应商）', '1', NOW(), '1', NOW(), b'0', NULL),
  (99002, 'CRM 下游客户行业', 'crm_customer_industry_downstream', 0, 'CRM 下游客户行业', '1', NOW(), '1', NOW(), b'0', NULL),
  (99003, 'CRM 上游供应商行业', 'crm_customer_industry_upstream', 0, 'CRM 上游供应商行业', '1', NOW(), '1', NOW(), b'0', NULL);

-- 2) 下游客户行业（crm_customer_industry_downstream）value 101-110
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
  (99101, 1, '石油化工', '101', 'crm_customer_industry_downstream', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'),
  (99102, 2, '电力热力', '102', 'crm_customer_industry_downstream', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'),
  (99103, 3, '冶金钢铁', '103', 'crm_customer_industry_downstream', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'),
  (99104, 4, '新能源(风电/光伏/储能)', '104', 'crm_customer_industry_downstream', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'),
  (99105, 5, '建筑工程总包', '105', 'crm_customer_industry_downstream', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'),
  (99106, 6, '海工船舶', '106', 'crm_customer_industry_downstream', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'),
  (99107, 7, '轨道交通', '107', 'crm_customer_industry_downstream', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'),
  (99108, 8, '医药食品', '108', 'crm_customer_industry_downstream', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'),
  (99109, 9, '渠道经销商', '109', 'crm_customer_industry_downstream', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'),
  (99110, 10, '工程施工单位', '110', 'crm_customer_industry_downstream', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0');

-- 3) 上游供应商行业（crm_customer_industry_upstream）value 201-206
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
  (99201, 1, '化工原料厂', '201', 'crm_customer_industry_upstream', 0, 'default', '', '上游供应商', '1', NOW(), '1', NOW(), b'0'),
  (99202, 2, '保温材料厂', '202', 'crm_customer_industry_upstream', 0, 'default', '', '上游供应商', '1', NOW(), '1', NOW(), b'0'),
  (99203, 3, '金属材料厂商', '203', 'crm_customer_industry_upstream', 0, 'default', '', '上游供应商', '1', NOW(), '1', NOW(), b'0'),
  (99204, 4, '橡塑塑胶厂商', '204', 'crm_customer_industry_upstream', 0, 'default', '', '上游供应商', '1', NOW(), '1', NOW(), b'0'),
  (99205, 5, '填料辅料厂商', '205', 'crm_customer_industry_upstream', 0, 'default', '', '上游供应商', '1', NOW(), '1', NOW(), b'0'),
  (99206, 6, '包装材料供应商', '206', 'crm_customer_industry_upstream', 0, 'default', '', '上游供应商', '1', NOW(), '1', NOW(), b'0');

-- 4) 合并全量（crm_customer_industry）value 101-206，供列表/详情展示
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
  (99301, 1, '石油化工', '101', 'crm_customer_industry', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'),
  (99302, 2, '电力热力', '102', 'crm_customer_industry', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'),
  (99303, 3, '冶金钢铁', '103', 'crm_customer_industry', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'),
  (99304, 4, '新能源(风电/光伏/储能)', '104', 'crm_customer_industry', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'),
  (99305, 5, '建筑工程总包', '105', 'crm_customer_industry', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'),
  (99306, 6, '海工船舶', '106', 'crm_customer_industry', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'),
  (99307, 7, '轨道交通', '107', 'crm_customer_industry', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'),
  (99308, 8, '医药食品', '108', 'crm_customer_industry', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'),
  (99309, 9, '渠道经销商', '109', 'crm_customer_industry', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'),
  (99310, 10, '工程施工单位', '110', 'crm_customer_industry', 0, 'default', '', '下游客户', '1', NOW(), '1', NOW(), b'0'),
  (99311, 11, '化工原料厂', '201', 'crm_customer_industry', 0, 'default', '', '上游供应商', '1', NOW(), '1', NOW(), b'0'),
  (99312, 12, '保温材料厂', '202', 'crm_customer_industry', 0, 'default', '', '上游供应商', '1', NOW(), '1', NOW(), b'0'),
  (99313, 13, '金属材料厂商', '203', 'crm_customer_industry', 0, 'default', '', '上游供应商', '1', NOW(), '1', NOW(), b'0'),
  (99314, 14, '橡塑塑胶厂商', '204', 'crm_customer_industry', 0, 'default', '', '上游供应商', '1', NOW(), '1', NOW(), b'0'),
  (99315, 15, '填料辅料厂商', '205', 'crm_customer_industry', 0, 'default', '', '上游供应商', '1', NOW(), '1', NOW(), b'0'),
  (99316, 16, '包装材料供应商', '206', 'crm_customer_industry', 0, 'default', '', '上游供应商', '1', NOW(), '1', NOW(), b'0');

-- 5) 客户类别（crm_customer_category）value 1/2
INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
  (99401, 1, '下游客户', '1', 'crm_customer_category', 0, 'default', '', '向我方采购', '1', NOW(), '1', NOW(), b'0'),
  (99402, 2, '上游供应商', '2', 'crm_customer_category', 0, 'default', '', '向我方供货', '1', NOW(), '1', NOW(), b'0');

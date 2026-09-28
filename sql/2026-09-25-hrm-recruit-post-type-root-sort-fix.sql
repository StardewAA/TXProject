-- HRM 标准一级职位分类排序修复：统一为 1~10。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
UPDATE hrm_recruit_post_type SET sort = CASE name
  WHEN '施工技能类（一线操作岗）' THEN 1
  WHEN '工程技术类' THEN 2
  WHEN '项目管理类' THEN 3
  WHEN '质量检验类' THEN 4
  WHEN '生产制造类' THEN 5
  WHEN '采购与供应链类' THEN 6
  WHEN '市场营销类' THEN 7
  WHEN '职能管理类' THEN 8
  WHEN '国际业务类' THEN 9
  WHEN '培训生/储备类' THEN 10
END,
updater = 'seed', update_time = NOW()
WHERE parent_id = 0 AND deleted = b'0'
  AND name IN ('施工技能类（一线操作岗）','工程技术类','项目管理类','质量检验类','生产制造类','采购与供应链类','市场营销类','职能管理类','国际业务类','培训生/储备类');

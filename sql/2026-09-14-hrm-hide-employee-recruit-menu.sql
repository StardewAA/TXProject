-- 隐藏 HRM 员工端下的招聘应聘菜单，保留个人端招聘网页及接口。
UPDATE system_menu
SET visible = b'0', updater = 'admin', update_time = NOW()
WHERE id = 1645
  AND parent_id = 1614
  AND path = 'recruit'
  AND deleted = b'0';

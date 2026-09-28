-- 企业入驻属于商城企业管理，与企业店铺、商品中心并列。
-- 保留菜单 ID、组件和按钮权限，已有角色授权继续有效。
UPDATE system_menu entry
JOIN system_menu mall ON mall.id = (
    SELECT id FROM (
        SELECT id FROM system_menu
        WHERE type = 1 AND parent_id = 0 AND deleted = b'0'
          AND (path IN ('mall', '/mall') OR name = '商城系统')
        ORDER BY id LIMIT 1
    ) AS mall_root
)
SET entry.parent_id = mall.id, entry.sort = 2, entry.update_time = NOW()
WHERE entry.component = 'mall/product/enterprise/index' AND entry.type = 2 AND entry.deleted = b'0'
  AND (entry.parent_id <> mall.id OR entry.sort <> 2);

-- 将商城公共租户的脚手架材料调整为启用的二级分类。
-- 保留分类 ID 900014 及商品关联；仅在父分类有效且本分类无子分类时更新。
SET NAMES utf8mb4;
START TRANSACTION;
UPDATE product_category child
JOIN product_category parent ON parent.id=900011 AND parent.tenant_id=1
  AND parent.name='施工辅材' AND parent.parent_id=0 AND parent.status=0 AND parent.deleted=0
LEFT JOIN product_category descendant ON descendant.parent_id=child.id
  AND descendant.tenant_id=child.tenant_id AND descendant.deleted=0
SET child.parent_id=parent.id,child.status=0,child.updater='mall-scaffold-fix'
WHERE child.id=900014 AND child.tenant_id=1 AND child.name='脚手架材料'
  AND child.deleted=0 AND child.parent_id IN (0,900011) AND descendant.id IS NULL;
COMMIT;

-- 将演示询价“123”发布到平台询价专栏。
-- 原记录选择了 TENANT_PUBLIC，且没有结构化交付地区，因此不会进入平台公开目录。
UPDATE product_purchase_inquiry
SET visibility = 'PLATFORM_PUBLIC',
    delivery_area_id = 130108,
    delivery_city_id = 130100,
    delivery_province_id = 130000,
    update_time = NOW()
WHERE id = 300050011
  AND tenant_id = 2009
  AND title = '123'
  AND deleted = b'0'
  AND status = 1
  AND visibility = 'TENANT_PUBLIC';

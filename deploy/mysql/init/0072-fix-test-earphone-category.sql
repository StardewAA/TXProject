-- Keep the bundled demo Sony headset under the matching “耳机耳麦” category.
-- The update is tenant scoped and idempotent so re-initialising the demo database
-- does not leave the product under the unrelated “洗澡用具” category (52).
UPDATE product_spu
SET category_id = 70
WHERE id = 633
  AND tenant_id = 1
  AND category_id <> 70;

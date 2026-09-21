-- 修复 scoped 商城客服菜单名被 latin1 客户端重复编码的问题。
-- 使用 UTF-8 十六进制构造中文，执行结果不依赖 mysql 客户端字符集。
DROP PROCEDURE IF EXISTS fix_mall_kefu_menu_encoding_20260917;
DELIMITER $$
CREATE PROCEDURE fix_mall_kefu_menu_encoding_20260917()
BEGIN
  DECLARE matched_count INT;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  START TRANSACTION;
  SELECT COUNT(*) INTO matched_count FROM system_menu
  WHERE deleted=b'0' AND component IN (
    'mall/promotion/kefu-scoped/agents',
    'mall/promotion/kefu-scoped/workbench',
    'mall/promotion/kefu-scoped/faq');
  IF matched_count <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected exactly three scoped kefu menus';
  END IF;
  UPDATE system_menu SET
    name=CASE component
      WHEN 'mall/promotion/kefu-scoped/agents'
        THEN CONVERT(0xE5AEA2E69C8DE59D90E5B8ADE68E88E69D83 USING utf8mb4)
      WHEN 'mall/promotion/kefu-scoped/workbench'
        THEN CONVERT(0xE5BA97E993BAE5AEA2E69C8DE68EA5E5BE85E58FB0 USING utf8mb4)
      WHEN 'mall/promotion/kefu-scoped/faq'
        THEN CONVERT(0xE5AEA2E69C8DE59BBAE5AE9A464151 USING utf8mb4)
    END,
    updater='fix-kefu-menu-encoding-20260917', update_time=NOW()
  WHERE deleted=b'0' AND component IN (
    'mall/promotion/kefu-scoped/agents',
    'mall/promotion/kefu-scoped/workbench',
    'mall/promotion/kefu-scoped/faq');
  COMMIT;
END$$
DELIMITER ;
CALL fix_mall_kefu_menu_encoding_20260917();
DROP PROCEDURE fix_mall_kefu_menu_encoding_20260917;

SELECT id,name,HEX(name),component FROM system_menu
WHERE deleted=b'0' AND component IN (
  'mall/promotion/kefu-scoped/agents',
  'mall/promotion/kefu-scoped/workbench',
  'mall/promotion/kefu-scoped/faq') ORDER BY sort,id;

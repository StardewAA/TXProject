-- DRAFT / 未执行。先关闭 scoped 客服开关并停用入口；备份菜单及相关授权后再处理。
-- 仅停用本脚本创建的新菜单，保留菜单与角色/租户套餐引用，避免产生悬空授权。
-- 不删除已有会话、消息、FAQ、审计、坐席授权；后端开关必须先关闭。
DROP PROCEDURE IF EXISTS mall_kefu_scoped_menu_rollback_20260917;
DELIMITER $$
CREATE PROCEDURE mall_kefu_scoped_menu_rollback_20260917()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  START TRANSACTION;
  UPDATE system_menu SET status=1,visible=b'0',updater='mall-kefu-rollback',update_time=NOW()
  WHERE creator='mall-kefu-20260917' AND component IN (
    'mall/promotion/kefu-scoped/agents','mall/promotion/kefu-scoped/workbench','mall/promotion/kefu-scoped/faq');
  COMMIT;
END$$
DELIMITER ;
CALL mall_kefu_scoped_menu_rollback_20260917();
DROP PROCEDURE mall_kefu_scoped_menu_rollback_20260917;

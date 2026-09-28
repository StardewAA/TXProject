-- DRAFT / 未执行。仅新增商城客服菜单；不改旧客服、不授予任何角色或真实坐席。
-- 执行前备份 system_menu，并先完成 scoped-chat 后端、范围鉴权与受控账号验收。
-- 新接口开关仍保持 false。菜单权限不能替代 promotion_kefu_agent 的独立范围授权。
-- 不固定环境菜单 ID；以现存旧商城客服组件定位营销父菜单，必须恰好一处且父菜单启用。
-- 失败后 CALL 会留下过程；仅清理本脚本的精确临时过程名以便重跑。
DROP PROCEDURE IF EXISTS mall_kefu_scoped_menu_20260917;
DELIMITER $$
CREATE PROCEDURE mall_kefu_scoped_menu_20260917()
BEGIN
  DECLARE target_parent BIGINT;
  DECLARE old_count INT;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  START TRANSACTION;
  SELECT COUNT(*), MIN(parent_id) INTO old_count, target_parent
  FROM system_menu WHERE component='mall/promotion/kefu/index' AND deleted=b'0';
  IF old_count <> 1 OR target_parent IS NULL OR NOT EXISTS (
    SELECT 1 FROM system_menu WHERE id=target_parent AND deleted=b'0' AND status=0
  ) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Mall kefu parent missing or ambiguous; review menus first'; END IF;

  IF EXISTS (SELECT 1 FROM system_menu WHERE deleted=b'0' AND (
      component IN ('mall/promotion/kefu-scoped/agents','mall/promotion/kefu-scoped/workbench','mall/promotion/kefu-scoped/faq')
      OR (parent_id=target_parent AND path IN ('kefu-scoped-agents','kefu-scoped-workbench','kefu-scoped-faq'))
      OR permission IN ('promotion:kefu-agent:manage','promotion:kefu-agent:receive','promotion:kefu-faq:manage')
    ) AND NOT (
      (COALESCE(component,'')='mall/promotion/kefu-scoped/agents' AND permission='promotion:kefu-agent:manage' AND COALESCE(path,'')='kefu-scoped-agents' AND parent_id=target_parent AND type=2)
      OR (COALESCE(component,'')='mall/promotion/kefu-scoped/workbench' AND permission='promotion:kefu-agent:receive' AND COALESCE(path,'')='kefu-scoped-workbench' AND parent_id=target_parent AND type=2)
      OR (COALESCE(component,'')='mall/promotion/kefu-scoped/faq' AND permission='promotion:kefu-faq:manage' AND COALESCE(path,'')='kefu-scoped-faq' AND parent_id=target_parent AND type=2)
    )) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Scoped kefu menu conflicts; no existing row will be overwritten'; END IF;
  IF EXISTS (SELECT permission FROM system_menu WHERE deleted=b'0' AND
      permission IN ('promotion:kefu-agent:manage','promotion:kefu-agent:receive','promotion:kefu-faq:manage')
      GROUP BY permission HAVING COUNT(*) > 1)
  THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Duplicate scoped kefu permission rows; inspect before importing'; END IF;

  INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
  SELECT CONVERT(0xE5AEA2E69C8DE59D90E5B8ADE68E88E69D83 USING utf8mb4),'promotion:kefu-agent:manage',2,101,target_parent,'kefu-scoped-agents','lucide:user-round-cog','mall/promotion/kefu-scoped/agents','MallScopedKefuAgents',0,b'1',b'0',b'0','mall-kefu-20260917',NOW(),'mall-kefu-20260917',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE component='mall/promotion/kefu-scoped/agents' AND deleted=b'0');
  INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
  SELECT CONVERT(0xE5BA97E993BAE5AEA2E69C8DE68EA5E5BE85E58FB0 USING utf8mb4),'promotion:kefu-agent:receive',2,102,target_parent,'kefu-scoped-workbench','lucide:messages-square','mall/promotion/kefu-scoped/workbench','MallScopedKefuWorkbench',0,b'1',b'0',b'0','mall-kefu-20260917',NOW(),'mall-kefu-20260917',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE component='mall/promotion/kefu-scoped/workbench' AND deleted=b'0');
  INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
  SELECT CONVERT(0xE5AEA2E69C8DE59BBAE5AE9A464151 USING utf8mb4),'promotion:kefu-faq:manage',2,103,target_parent,'kefu-scoped-faq','lucide:book-open-check','mall/promotion/kefu-scoped/faq','MallScopedKefuFaq',0,b'1',b'0',b'0','mall-kefu-20260917',NOW(),'mall-kefu-20260917',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE component='mall/promotion/kefu-scoped/faq' AND deleted=b'0');
  COMMIT;
END$$
DELIMITER ;
CALL mall_kefu_scoped_menu_20260917();
DROP PROCEDURE mall_kefu_scoped_menu_20260917;

-- 只核对新三页；不自动写 system_role_menu 或 system_tenant_package。
SELECT id,name,permission,parent_id,path,component FROM system_menu
WHERE component IN ('mall/promotion/kefu-scoped/agents','mall/promotion/kefu-scoped/workbench','mall/promotion/kefu-scoped/faq') AND deleted=b'0';

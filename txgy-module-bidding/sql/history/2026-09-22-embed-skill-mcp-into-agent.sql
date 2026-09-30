-- 技能管理与 MCP 服务已内置到智能体管理页，隐藏原有独立侧边菜单。
-- 保留 6801/6802 及其权限子菜单，避免破坏 bidding:bid-agent:skill:* 与 bidding:bid-agent:mcp:* 权限码。
UPDATE `system_menu`
SET `visible` = b'0',
    `updater` = '1',
    `update_time` = NOW()
WHERE `id` IN (6801, 6802)
  AND `deleted` = b'0';
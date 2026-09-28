-- 需求文档 §5.6 角色权限表落地：IQC/质检员、车间操作员、仓库管理员 三个角色的环保模块授权
-- 背景：60190/60192/60196 三个角色当前 system_role_menu 为空（对应用户登录后看不到任何菜单）
-- 口径（严格照抄需求文档 §5.6 表）：
--   IQC/质检员(60192 质检审核员)  → 采购入库/中间废弃物/成品的 AI 初筛查看与人工复核
--   车间操作员(60190 工序上报员)  → 报工时标记废弃物、登记中间物去向
--   仓库管理员(60196 物资管理员)  → 领用放行复核、污染品入受控环境登记
-- 菜单：34000 安全环保检测(分组) / 34301 污染判定 (34311 查询 34312 新增 34313 复核) / 34370 污染追溯 (34371 查询)
-- 注意：本模块是裸 SQL 授权，绕过 @CacheEvict → 需清 system-server 的 menu_role_ids:*、permission_menu_ids:*
--
-- !! 祖先目录 5100(MES 系统) / 34010(污染管控) 必须一起绑 !!
--   后端 getPermissionInfo 的 filterDisableMenus 是按父链递归剪枝的：链子断一节，那一节的
--   整棵子树都被判定成"无权" → 这个角色解出来就是 0 个权限码、菜单一个不显示。
--   本文件最初只绑了 34000/34301/...，漏了 5100 和 34010，于是三个角色实际权限为 0。
--   5100/34010 都是 type=1 且 permission 为空的目录：绑它们不发任何权限码，只是把链子接通。

-- IQC / 质检员：看 + 复核 + 批次反查追溯
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
 (60192, 5100, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60192, 34000, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60192, 34010, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60192, 34301, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60192, 34311, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60192, 34313, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60192, 34370, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60192, 34371, 'sql', NOW(), 'sql', NOW(), 0, 2010);

-- 车间操作员：看 + 新建判定（报工时登记中间物去向/标记废弃物），不给复核（终态裁决不归车间）
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
 (60190, 5100, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60190, 34000, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60190, 34010, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60190, 34301, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60190, 34311, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60190, 34312, 'sql', NOW(), 'sql', NOW(), 0, 2010);

-- 仓库管理员：领用放行复核 + 污染品入受控环境登记，台账只读（台账查询走 mes:set-pollution-check:query）
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
 (60196, 5100, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60196, 34000, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60196, 34010, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60196, 34301, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60196, 34311, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60196, 34313, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60196, 34370, 'sql', NOW(), 'sql', NOW(), 0, 2010),
 (60196, 34371, 'sql', NOW(), 'sql', NOW(), 0, 2010);

-- 校验 1：绑了几行
SELECT r.id, r.name, COUNT(*) AS menu_cnt
FROM system_role r JOIN system_role_menu rm ON rm.role_id = r.id
WHERE r.id IN (60190, 60192, 60196) GROUP BY r.id, r.name ORDER BY r.id;

-- 校验 2（关键）：**父链断了几节**。上面那个 COUNT 只能证明"绑了行"，证明不了
-- "运行时能解析出来"——最初就是被它骗过去的（绑了 6 行，实际权限 0）。
-- 期望 0 行；有输出就说明还有祖先没绑，这个角色的菜单会被整棵剪掉。
WITH RECURSIVE chain AS (
  SELECT rm.role_id, rm.menu_id AS missing_id, 0 AS lvl
  FROM system_role_menu rm WHERE rm.deleted = 0 AND rm.role_id IN (60190, 60192, 60196)
  UNION ALL
  SELECT c.role_id, m.parent_id, c.lvl + 1
  FROM chain c JOIN system_menu m ON m.id = c.missing_id
  WHERE m.parent_id <> 0 AND c.lvl < 10
)
SELECT DISTINCT c.role_id, c.missing_id AS 缺的祖先, m.name
FROM chain c JOIN system_menu m ON m.id = c.missing_id
LEFT JOIN system_role_menu g ON g.role_id = c.role_id AND g.menu_id = c.missing_id AND g.deleted = 0
WHERE c.lvl > 0 AND g.id IS NULL AND m.deleted = 0 ORDER BY c.role_id;   -- 必须 0 行

-- 校验 3：实际会拿到的权限码
SELECT rm.role_id, m.permission FROM system_role_menu rm JOIN system_menu m ON m.id = rm.menu_id
WHERE rm.role_id IN (60190, 60192, 60196) AND m.permission IS NOT NULL AND m.permission <> ''
ORDER BY rm.role_id, m.permission;

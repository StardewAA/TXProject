-- ============================================================
-- CMS 模块化调整（需求 1 / 2 / 4）
--   需求1：提交审查 从「审查管理」迁到「发布管理」
--   需求2：「发布已审内容」+「人工发布或驳回」合并为「发布决策」
--   需求4：内容管理独立为一级模块，与智能检索/审查管理/发布管理平级
-- 说明：脚本幂等，可重复执行
-- ============================================================

-- ---------- 需求4：内容管理（一级目录）+ 保留的内容类型 ----------
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater)
SELECT 70040,'内容管理','',1,15,70000,'content','ep:files',NULL,NULL,0,b'1',b'1',b'1','1','1'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM system_menu WHERE id=70040) t);

-- 70041 标准规范：已下线，不再写入菜单（Codex 2026-09-22 调整）

-- 70042 材料参数：已下线，不再写入菜单（Codex 2026-09-22 调整）

-- 70043 工艺案例：已下线，不再写入菜单（Codex 2026-09-22 调整）

-- 70044 定额资料：已下线，不再写入菜单（Codex 2026-09-22 调整）

INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater)
SELECT 70045,'政策法规','cms:content:query',2,50,70040,'policy','ep:notebook','cms/content/policy/index','CmsPolicy',0,b'1',b'1',b'1','1','1'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM system_menu WHERE id=70045) t);

-- ---------- 需求1：提交审查 迁入发布管理 ----------
UPDATE system_menu SET parent_id=70030, sort=20 WHERE id=70008 AND deleted=b'0';

-- ---------- 需求2：合并为「发布决策」 ----------
UPDATE system_menu
SET name='发布决策', path='decision', component='cms/publish/index', component_name='CmsPublishDecision',
    permission='cms:knowledge:publish', sort=10
WHERE id=70031 AND deleted=b'0';

-- 「人工发布或驳回」合并进发布决策后下线
UPDATE system_menu SET deleted=b'1' WHERE id=70035 AND deleted=b'0';

-- 发布管理改为纯目录（统一发布/驳回入口在「发布决策」子菜单）
UPDATE system_menu SET component=NULL, component_name=NULL WHERE id=70030 AND deleted=b'0';
-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 2026-09-16 招投标模块菜单图标统一为「已注册」图标集（ep / lucide）
-- 背景/原因：前端 main.ts → offline-icons.ts 只离线注册了 ep/fa/fa6-solid/foundation/
--   fluent-mdl2/lucide/simple-icons 整集 + mdi/ant-design/material-symbols/ri/tabler 少量按需，
--   并未注册 carbon 集。而本模块根目录 6735 用 carbon:integration、爬虫管理 6737 用
--   carbon:data-vis、主体信息 6759 用 carbon:apps，且 6748/6753/6738/6754/6760/6762
--   图标字段为空串 —— 导致 @iconify/vue 渲染为空/不可见，菜单前无图标。
--   本脚本把 6735 下所有目录/页面菜单图标统一替换为已注册集合（ep/lucide）中
--   已校验存在的图标，保证菜单图标可正常显示。
-- 图标有效性：已用 @iconify/json 本地数据逐一校验（OK），非凭空填写。
-- 仅改 type=1(目录)/type=2(页面) 的可见菜单；type=3(按钮) 不渲染菜单图标，不改。
-- 已为 ep 集合的（6736 ep:document-copy、6771 ep:document）本身有效，保留不动。
--
-- 回滚：把 icon 字段改回原值（carbon:integration / carbon:data-vis / carbon:apps / ''）。
-- ============================================================================

UPDATE system_menu SET icon = 'lucide:gavel',          updater='1', update_time=NOW() WHERE id = 6735 AND deleted = b'0'; -- 招投标
UPDATE system_menu SET icon = 'lucide:bot',            updater='1', update_time=NOW() WHERE id = 6737 AND deleted = b'0'; -- 爬虫管理
UPDATE system_menu SET icon = 'ep:monitor',            updater='1', update_time=NOW() WHERE id = 6738 AND deleted = b'0'; -- 爬取任务工作台
UPDATE system_menu SET icon = 'lucide:list-checks',    updater='1', update_time=NOW() WHERE id = 6748 AND deleted = b'0'; -- 投标流程跟踪
UPDATE system_menu SET icon = 'lucide:megaphone',      updater='1', update_time=NOW() WHERE id = 6753 AND deleted = b'0'; -- 公告/公示管理
UPDATE system_menu SET icon = 'ep:tickets',            updater='1', update_time=NOW() WHERE id = 6754 AND deleted = b'0'; -- 公告列表
UPDATE system_menu SET icon = 'ep:user',               updater='1', update_time=NOW() WHERE id = 6759 AND deleted = b'0'; -- 主体信息与授权管理
UPDATE system_menu SET icon = 'ep:avatar',             updater='1', update_time=NOW() WHERE id = 6760 AND deleted = b'0'; -- 主体档案（企业自维护）
UPDATE system_menu SET icon = 'lucide:shield-check',   updater='1', update_time=NOW() WHERE id = 6762 AND deleted = b'0'; -- 平台授权管理

-- ========== 校验 ==========
-- 期望：6735 下目录/页面菜单 icon 均为 ep:/lucide:，且无 carbon: 前缀、无空串
SELECT id, name, type, icon FROM system_menu
WHERE id = 6735 OR parent_id = 6735
   OR parent_id IN (SELECT id FROM system_menu WHERE parent_id = 6735 AND deleted = b'0')
AND deleted = b'0' AND type IN (1,2)
ORDER BY parent_id, sort, id;

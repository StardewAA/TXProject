-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 2026-09-10 菜单更名：爬虫管理 → 信息聚合任务管理
-- 规范依据：《招投标模块开发规范》4.4（菜单/权限码与 sql/ 脚本同步）；
--   用户口径 2026-09-10：「把数据库的爬虫管理改名成信息聚合任务管理，路由是之前的，前端已经同步改了」。
-- 业务目标：产品口径由"爬虫"调整为"信息聚合"，菜单展示名随之统一。
-- 为什么只改 name：路由（path=crawl）、组件（component_name）、权限码（bidding:crawl-*）、
--   父级（6735 招投标）、排序/图标全部保持不变——前端已按原路由同步，改路由会让页面失联；
--   权限码更不能改（后端 @PreAuthorize 与角色授权行都按 bidding:crawl-* 绑定）。
-- 幂等：目标态 UPDATE（可重复执行，无副作用；不存在该行时为无操作）。
-- ============================================================================

-- 主变更：目录 6737（原「爬虫管理」，path=crawl）
UPDATE system_menu
   SET name = '信息聚合任务管理', updater = '1', update_time = NOW()
 WHERE id = 6737
   AND deleted = b'0';

-- 兼容说明（默认不执行）：2026-09-08 的旧号段脚本曾建过 60002000「外部公告采集管理」目录。
-- 若该环境的 system_menu 里确实存在这一行，且它就是当前在用的采集管理目录，再执行下面这句：
-- UPDATE system_menu SET name = '信息聚合任务管理', updater = '1', update_time = NOW()
--  WHERE id = 60002000 AND deleted = b'0';

-- ========== 校验 ==========
-- 期望：6737 名称为「信息聚合任务管理」，且 path/component_name/permission 与更名前完全一致
SELECT id, name, parent_id, path, icon, component, component_name, permission, type, sort, status
  FROM system_menu
 WHERE id = 6737;

-- 子菜单（保持不变，仅列出以便核对）：6738 爬取任务工作台 / 6739 采集任务 / 6740 采集批次
SELECT id, name, parent_id, path, permission, type
  FROM system_menu
 WHERE parent_id = 6737 OR parent_id IN (6738, 6739, 6740)
 ORDER BY id;

-- 2026-09-09 权限对齐：投标流程跟踪(6748) 菜单 permission 改为 bidding:notice-external:query
-- 原因：页面唯一接口 GET /bidding/notice-follow/item/page 校验 bidding:notice-external:query；
--      原 bidding:process-track:query 在任何已授菜单上均不匹配该接口 → 打开页面必 403。已应用到本地库。

UPDATE system_menu SET permission = 'bidding:notice-external:query' WHERE id = 6748 AND deleted = 0;

-- 说明：6749-6752（bidding:process-track:create/register/review/upload）为将来状态机接口预留，当前无后端校验；
-- 待状态机落地后再按实际端点核对（对照文档：docs/前端规范-全域接口说明/06-bidding前端契约接口完全对照.md §6）。


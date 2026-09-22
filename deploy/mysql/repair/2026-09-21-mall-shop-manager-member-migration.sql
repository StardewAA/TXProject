-- DEPRECATED / DO NOT EXECUTE
-- 该旧脚本会把商品经营迁回 member_user、停用 system_user，并写入共享默认口令，
-- 与《开发方案》第 21 节“采购留在用户端、供应经营迁入后台”相冲突。
-- 请改为执行：2026-09-21-mall-section21-admin-business.sql
SIGNAL SQLSTATE '45000'
  SET MESSAGE_TEXT='脚本已废止：禁止把供应经营迁回 member_user，请执行第21节后台经营迁移';

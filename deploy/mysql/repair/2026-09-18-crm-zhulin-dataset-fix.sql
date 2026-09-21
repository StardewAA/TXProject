-- ============================================================================
-- 「测试数据_朱琳」数据集导入收尾（固邦租户 2012）
-- 日期：2026-09-18
--
-- 数据集：sql/crm_test_data_zhulin/（16 个 CSV，编号区间 1xxx/2xxx/3xxx…与既有数据不冲突）
--   客户 3101~3110、线索 2101~2109、联系人 4101~4110、商机 5101~5107、
--   合同 6101~6104、回款计划 7101~7105、回款 8101~8102、跟进 9101~9109、
--   产品 1101~1114；负责人统一为朱琳(gubang/80446)。
--
-- 导入脚本 sql/crm_test_data/import_to_gubang_2012.py 跑完后有两处需要收尾：
--
--   1) 商机阶段被新建：
--      CSV 里的阶段名沿用旧数据集叫法「报价及方案确认」「需求调研」，
--      与 2026-09-16 归一后的规范三套流程不一致，脚本按名字匹配不上便新建了
--      阶段 2035 / 2036，导致「油气管道防腐销售流程」多出两个游离阶段。
--      处理：按语义归并到规范阶段（与既有 5001/5006 的处理方式保持一致）后删除。
--        报价及方案确认 → 方案评估(1603)
--        需求调研       → 需求确认(1602)
--
--   2) 团队成员被压平成朱琳：
--      脚本第 12 步把 15_团队_团队成员.csv 的所有行都写成 user_id=负责人(80446)，
--      丢掉了 CSV 里真实的成员（王海涛/杨帆/陈晓东/张建军/赵磊/李国庆/马晓梅）。
--      处理：按 CSV 的 (biz_type, biz_id, level) 还原为真实成员。
--      成员用户 id 为导入时新建：CSV 101~111 → 80945~80955
--      （负责人行 level=1 保持 80446 朱琳，与 owner_user_id 一致）。
--
-- 幂等：可重复执行。
-- ============================================================================

USE `ruoyi-vue-pro`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 商机阶段归一（先改引用，再删游离阶段）
-- ---------------------------------------------------------------------------
UPDATE crm_business SET status_id = 1603 WHERE tenant_id = 2012 AND status_id = 2035; -- 报价及方案确认 → 方案评估
UPDATE crm_business SET status_id = 1602 WHERE tenant_id = 2012 AND status_id = 2036; -- 需求调研 → 需求确认
UPDATE crm_business SET status_type_id = 16 WHERE tenant_id = 2012 AND id BETWEEN 5101 AND 5107;

DELETE FROM crm_business_status WHERE tenant_id = 2012 AND id IN (2035, 2036);

-- ---------------------------------------------------------------------------
-- 2. 团队成员还原（客户 biz_type=2 / 商机=4 / 合同=5）
--    level 1=负责人 2=只读 3=读写，脚本已按 CSV 写入 level，这里只纠正 user_id
-- ---------------------------------------------------------------------------
-- 客户
UPDATE crm_permission SET user_id = 80946 WHERE tenant_id = 2012 AND biz_type = 2 AND biz_id = 3101 AND level = 2; -- 王海涛 只读
UPDATE crm_permission SET user_id = 80952 WHERE tenant_id = 2012 AND biz_type = 2 AND biz_id = 3101 AND level = 3; -- 杨帆 读写
UPDATE crm_permission SET user_id = 80949 WHERE tenant_id = 2012 AND biz_type = 2 AND biz_id = 3102 AND level = 3; -- 陈晓东 读写
UPDATE crm_permission SET user_id = 80952 WHERE tenant_id = 2012 AND biz_type = 2 AND biz_id = 3103 AND level = 3; -- 杨帆 读写
UPDATE crm_permission SET user_id = 80946 WHERE tenant_id = 2012 AND biz_type = 2 AND biz_id = 3105 AND level = 2; -- 王海涛 只读
UPDATE crm_permission SET user_id = 80945 WHERE tenant_id = 2012 AND biz_type = 2 AND biz_id = 3106 AND level = 2; -- 张建军 只读
-- 商机
UPDATE crm_permission SET user_id = 80948 WHERE tenant_id = 2012 AND biz_type = 4 AND biz_id = 5101 AND level = 3; -- 赵磊 读写
UPDATE crm_permission SET user_id = 80951 WHERE tenant_id = 2012 AND biz_type = 4 AND biz_id = 5105 AND level = 3; -- 李国庆 读写
-- 合同
UPDATE crm_permission SET user_id = 80954 WHERE tenant_id = 2012 AND biz_type = 5 AND biz_id = 6101 AND level = 2; -- 马晓梅 只读

-- ---------------------------------------------------------------------------
-- 3. 校验：商机阶段引用 & 团队成员
-- ---------------------------------------------------------------------------
SELECT '=== 校验：三套流程的阶段（应各为规范阶段，无 2035/2036） ===' AS section;
SELECT s.type_id, s.name AS 阶段, s.sort
FROM crm_business_status s
WHERE s.tenant_id = 2012 AND s.deleted = b'0'
ORDER BY s.type_id, s.sort;

SELECT '=== 校验：新导入商机的阶段 ===' AS section;
SELECT b.id, b.name, s.name AS 阶段
FROM crm_business b
         LEFT JOIN crm_business_status s ON s.id = b.status_id
WHERE b.tenant_id = 2012 AND b.id BETWEEN 5101 AND 5107
ORDER BY b.id;

SELECT '=== 校验：新导入数据的团队成员（应含朱琳 + 真实成员） ===' AS section;
SELECT p.biz_type, p.biz_id, u.nickname AS 成员, p.level
FROM crm_permission p
         LEFT JOIN system_users u ON u.id = p.user_id
WHERE p.tenant_id = 2012 AND p.biz_id BETWEEN 3101 AND 6104 AND p.deleted = b'0'
ORDER BY p.biz_type, p.biz_id, p.level;

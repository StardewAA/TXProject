-- 需求文档 §11 验收用例 5「不达标成品：整批锁定不出库；有害→受控存储；无害→直接存储并标记」落地
-- 团队终版设计 §四-环节5：成品达标分支 = 达标 / 局部缺陷(返工为主) / 整体报废(钢管回收、剥离层经鉴别才转危废)
--
-- 建模口径（与既有 review_result 正交，不改 R2 二值不变量）：
--   review_result  = 污染态，终态二值 CLEAN/POLLUTED（管放行不放行），所有环节都填
--   finished_result= 达标分支，仅环节 FINISHED_PRODUCT 填：
--       QUALIFIED 达标    → 成品库入库出厂
--       REWORK    局部缺陷 → 返工(补口/重新发泡)，返工回路入追溯链，不锁批
--       SCRAPPED  整体报废 → 整批锁定不出库；有污染走受控存储+台账，无污染直接存储并标记
-- 报废锁定落到 mes_wm_batch.pollution_marked（两个拦截口 productissue/issuesales 都认），
-- 由 refreshBatchStamp 依"该批是否存在 SCRAPPED 判定"重投影，锁定不随其他判定的无污染复核解除。

ALTER TABLE mes_set_pollution_check
    ADD COLUMN finished_result varchar(32) NULL COMMENT '成品达标分支：QUALIFIED达标/REWORK局部缺陷返工/SCRAPPED整体报废(仅成品环节)' AFTER review_result;

-- 演示存量：成品环节的历史判定全部视为达标（不改污染态，只补达标分支）
UPDATE mes_set_pollution_check
SET finished_result = 'QUALIFIED'
WHERE stage = 'FINISHED_PRODUCT' AND finished_result IS NULL AND deleted = 0;

-- 存量校验：成品环节必须都有达标分支
SELECT stage, finished_result, COUNT(*) FROM mes_set_pollution_check WHERE deleted = 0 GROUP BY stage, finished_result ORDER BY stage;

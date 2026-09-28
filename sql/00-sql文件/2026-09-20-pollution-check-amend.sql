-- 签字即冻结 · 变更单：污染判定行加三列
--
-- 需求：「一旦签字之后不允许更改，要更改就新开一份记录来记录更改」。
-- 收口后的判定内容不再允许就地修改，改动一律走「变更单」——新开一行判定，用
-- origin_record_no 指回被替代的原单，原单的 superseded_by 回填新单号。
--
-- 为什么不新开一张 amend 表：变更单与原单**字段完全同构**（同样的环节/批次/物料/复核
-- 结论/去向），前端列表也要在同一个页面里一起看。开新表等于把同一份数据模型复制一遍，
-- 还要在每个查询处做 union。三列就够。
--
-- superseded_by 是**唯一**允许写已收口行的例外，且只此一列、只写一次、只有变更流程能写。

ALTER TABLE `mes_set_pollution_check`
    ADD COLUMN `origin_record_no` VARCHAR(64) NULL COMMENT '被变更的原单号（首版为空；非空即为变更单）' AFTER `record_no`,
    ADD COLUMN `amend_reason` VARCHAR(255) NULL COMMENT '变更事由（变更单必填）' AFTER `origin_record_no`,
    ADD COLUMN `superseded_by` VARCHAR(64) NULL COMMENT '替代本单的变更单号（回填，只写一次）' AFTER `amend_reason`;

-- 列表按「原单 → 变更单」串链、以及「本单是否已被替代」的过滤都要走这两列
CREATE INDEX `idx_origin_record_no` ON `mes_set_pollution_check` (`origin_record_no`);
CREATE INDEX `idx_superseded_by` ON `mes_set_pollution_check` (`superseded_by`);

-- @note 本表仍带 UNIQUE KEY uk_record_no(record_no)（见 set-pollution-check-20260903-p0.sql:42），
--       是全仓唯一一处漏摘的业务编号唯一键。变更单**不派生后缀**（不写 -C1/-R1），
--       单号一律走 generateRecordNo()（PC- + 17 位），所以唯一键在这里不冲突、也不该摘。

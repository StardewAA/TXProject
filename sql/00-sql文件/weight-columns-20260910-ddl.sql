-- 全链重量列（2026-09-10）
--
-- 依据：团队终版设计 §「新增/变更表」——`mes_set_pollution_check`：+ finishedResult/CANCELLED
-- 状态、AI 判据快照、**重量**；以及 §环节4「产废」的**危废重量硬平衡（可逐斤核对）**。
--
-- 为什么判定行要有重量：重量是整条链的**贯通用量纲**——判定(产废)→台账(暂存)→联单(转移)
-- →衡算(投入≈产物+危废+排放+损耗)。缺了它，§「重量三平衡」的等式两边没有共同单位，
-- 「危废重量硬平衡」就无从比对。
--
-- 为什么台账也要有：台账复用判定重量而非再次录入——同一批废物的重量只应有一个来源
-- （复核时确认的那个数）。台账侧冗余存一份是为了衡算时不必回关判定表（判定行可被
-- CANCELLED 逻辑改写，台账是既成事实）。
--
-- 口径：decimal(12,3)，单位 kg，与 `mes_set_weigh_record.net_weight` 同精度同单位，
-- 保证「秤采净重」与「判定重量」可直接相减核对，不引入换算。

ALTER TABLE `mes_set_pollution_check`
    ADD COLUMN `weight` decimal(12,3) NULL COMMENT '重量(kg)' AFTER `item_spec`;

ALTER TABLE `mes_pollution_ledger`
    ADD COLUMN `weight` decimal(12,3) NULL COMMENT '重量(kg)' AFTER `item_spec`;

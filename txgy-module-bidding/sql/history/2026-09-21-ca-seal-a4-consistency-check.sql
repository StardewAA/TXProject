-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- CA 签章台账：历史数据一致性核查与回填（契约 A4）
-- 登记日期：2026-09-21
--
-- [契约原文] A4：bidding_ca_seal 与 bidding_process_track 数据不一致，
--   /ca/seal/check 只查台账 ⇒ 历史签章被判"未认证"，前端提示"未经 CA 认证"并诱导重复签章。
--
-- [实测核实] 逐行比对后，所谓"不一致"实为**两类不同来源的数据**，不可盲目对齐：
--   ① bidding_process_track 带 document_signature 的行共 7 条（tenant_id=1）：
--      - 6 条 document_hash 形如 'sm3-202609181211'（**非合法 SM3**）⇒ 早期测试造数，非真实签章；
--      - 1 条 document_hash=585ba309…（64 位真实 SM3）+ ca_cert_no=SN-LOCAL-CA-TEST-0001 ⇒ 真实签章。
--   ② bidding_ca_seal 台账 2 条，其 sm3_hash 与上述**任何一条都不相同** ⇒ 是另外两笔独立签章
--      （无 track 关联，track_id 均为 NULL，即不经过投标流程的通用签章）。
-- [结论] 台账并未"丢失"流程行的记录——两者本就不是同一批文件。因此**不做跨表强制回填**：
--   把不相关的台账行关联到流程行，等于伪造证据链，比缺失更有害。
--
-- [根治动作（已完成，代码侧）]
--   1. saveSealResult 现在会把签章产物坐标（fileId/文件名/字节数）写回流程行 ⇒ 今后不再缺坐标；
--   2. /ca/seal/check 增加第三优先级"按已签章产物 fileId 匹配" ⇒ 已签章文件再检测不再误判（契约 A5）；
--   3. 签章产物体积 seal_file_size 已加列并落库（契约 A1）。
--
-- [本脚本作用] 仅做**幂等核查**，不修改任何数据；输出应持续为下方期望值。
-- ============================================================================

-- 核查 1：真实签章但缺「原文件坐标」的流程行。
--   注意：document_file_id 表示"投标文件（加密后）fileId"，由**递交**动作写入（/process-track/upload），
--   而该历史行是"仅签章未递交"（document_size/name 亦为 NULL），故此处为 1 属**预期**，
--   不代表数据损坏；递交接口会补齐。新签章链路（saveSealResult 带坐标）已不再产生此形态。
SELECT COUNT(*) AS signed_but_not_submitted
FROM bidding_process_track
WHERE deleted = 0 AND document_signature IS NOT NULL
  AND document_file_id IS NULL AND document_hash REGEXP '^[0-9a-fA-F]{64}$';

-- 核查 2：测试造数行（非合法 SM3）。保持原样，仅统计以便与真实数据区分。
SELECT COUNT(*) AS fake_signed_rows
FROM bidding_process_track
WHERE deleted = 0 AND document_signature IS NOT NULL
  AND document_hash NOT REGEXP '^[0-9a-fA-F]{64}$';

-- 核查 3：台账行应都能被 /ca/seal/check 命中（file_id 或 seal_file_id 非空）。
SELECT COUNT(*) AS ledger_rows_unmatchable
FROM bidding_ca_seal
WHERE deleted = 0 AND file_id IS NULL AND seal_file_id IS NULL;

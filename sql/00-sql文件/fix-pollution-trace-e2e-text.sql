-- 追溯链一期B e2e：修正经 Windows shell(GBK) 提交而损坏的演示文本
-- 影响 PC-20260907101620002（复核→台账 整链，租户 2010）
--
-- ⚠️ 2026-09-10 补漏：初版漏了 mes_pollution_check_log。它是「操作履历」真正读的那张表，
--    结果 check/ledger/sign/trace 四张表都干净了，界面上「去向/库位」「备注」两行仍是乱码——
--    典型的"修了数据源、忘了快照表"。**改这类链式留痕，凡是有 *_log 快照表的都要一并修。**
-- ⚠️ 必须整文件 UTF-8 喂给 mysql（mysql --default-character-set=utf8mb4 < 本文件），
--    不能把中文写在 `mysql -e "...中文..."` 里：Windows shell 按 GBK 交出去，
--    服务端按 utf8mb4 收，于是 危(GBK: CEA3) 被当成 Σ 存下来，整串变成 Σ���ݴ��A-01。
--    本文件可重复执行（全是按 id 定向的 UPDATE）。
UPDATE mes_set_pollution_check SET location='危废暂存间A-01', remark='追溯链一期B e2e 复核留痕' WHERE id=1052;
UPDATE mes_set_sign_record    SET location='危废暂存间A-01' WHERE id=1;
UPDATE mes_set_trace_chain    SET extra='危废暂存间A-01' WHERE id=1;
UPDATE mes_pollution_ledger   SET location='危废暂存间A-01', remark='追溯链一期B e2e 复核留痕' WHERE id=1014;
UPDATE mes_pollution_check_log SET location='危废暂存间A-01', remark='追溯链一期B e2e 复核留痕' WHERE id=40;

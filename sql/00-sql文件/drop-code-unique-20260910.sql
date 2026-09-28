-- 2026-09-10 修复：业务编号唯一键与逻辑删除冲突（#21 生成模块的遗留缺陷）
--
-- 现象：run_e2e_shell.py 首轮 48/48 全绿，次轮 6 个创建全部 500：
--   DuplicateKeyException: Duplicate entry 'REP-2026-09-002-E2E' for key 'mes_set_env_report.uk_report_no'
--
-- 根因：唯一键只建在业务编号列上，而删除是逻辑删除（deleted 0→1）。
--   t1: create REP-001 (deleted=0) → delete (deleted=1)
--   t2: create REP-001 → 服务层查重按 deleted=0 过滤，查不到，放行 → INSERT 撞上那条已删行的唯一键 → 系统异常
--   且 e2e 的 cleanup 按 deleted=0 分页，**根本看不见**这条软删行，无法自愈。
--   这不只是测试问题：用户在界面上"建-删-再建同一个编号"就会稳定复现。
--
-- 为什么不改成 UNIQUE KEY (编号, deleted, tenant_id)（仓库里 mes_wm_misc_issue 等表是这个写法）：
--   deleted 是布尔 0/1，所有已删行都等于 1。同一编号删第二次时，两行 (编号,1,租户) 仍然相撞。
--   即"建-删-建-删"第二轮删除照样报错，只是把故障推迟了一轮，没有根治。
--
-- 采用方案：去掉业务编号上的唯一键，查重交给服务层。
--   6 个模块的 Service 都已内置 <模块>_NO_DUPLICATE 校验（validateBase 里按 deleted=0 查重，
--   命中返回业务错误码而非 500），该路径已被 e2e 断言覆盖。
--   唯一键原本只剩"兜住并发同时建同号"这一点价值，在人工录单场景下不值得用"编号永久不可复用"来换。
--
-- 影响面：以下 6 张表是 #21 落成 CRUD 的；其余 mes_set_* 表（pollution_permit / hazardous_waste 等）
--   存在同类隐患（uk_permit_no 等同样只含业务列，且 pollution_permit 已有软删行），
--   本次不一并改动——它们不是本轮的交付物，逐表确认服务层查重后再处理。
--   ⚠️ 待办清单见 docs/开发说明-2026-09-08.md §13.4。

ALTER TABLE `mes_set_env_report`       DROP INDEX `uk_report_no`;
ALTER TABLE `mes_set_emission_outlet`  DROP INDEX `uk_outlet_code`;
ALTER TABLE `mes_set_exhaust_gas`      DROP INDEX `uk_record_no`;
ALTER TABLE `mes_set_wastewater`       DROP INDEX `uk_record_no`;
ALTER TABLE `mes_set_carbon_emission`  DROP INDEX `uk_calc_no`;
ALTER TABLE `mes_set_chemical_safety`  DROP INDEX `uk_record_no`;

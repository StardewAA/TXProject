-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;

-- =====================================================================
-- bidding_subject_profile 一租户一档（《后端变更请求-主体档案合并一租户一档-2026-09-20》§2.1）
-- 对应代码：SubjectProfileServiceImpl / SubjectEligibilityServiceImpl / SubjectProfileController
--          （2026-09-27 后端落地，提交见本次变更）
-- 背景：同一租户（同一家企业）此前被强制按 BIDDER/TENDERER 各存一份档案（uk_subject_profile_type），
--   用户 2026-09-20 裁定"招投标人用的是同一主体档案，不需要分开"。
-- 本脚本：① 合并存量双行（保留较新/信息更全的一行，其余软删留痕，不物理删除）；② 唯一键改为 (tenant_id, deleted)。
-- 幂等说明：与 2026-09-05-external-notice-review.sql 同口径——ALTER 为一次性结构变更
--   （MySQL 8 无 DROP INDEX IF EXISTS），只对未升级库执行一次；重复执行报"索引不存在"属预期。
-- 执行库：业务库（bidding_subject_profile 所在库）。
-- =====================================================================

-- 1) 合并存量重复档案：同一租户 deleted=0 仅保留一份（优先 complete_flag=1，其次较晚建档/较大 id），
--    其余行置 deleted=1（保留留痕，不物理删除）；单行租户不受影响。
UPDATE `bidding_subject_profile` p
LEFT JOIN (
    SELECT tenant_id,
           SUBSTRING_INDEX(GROUP_CONCAT(id ORDER BY complete_flag DESC, id DESC), ',', 1) AS keep_id
    FROM `bidding_subject_profile`
    WHERE deleted = 0
    GROUP BY tenant_id
) k ON p.id = k.keep_id
SET p.deleted = 1
WHERE p.deleted = 0 AND k.keep_id IS NULL;

-- 2) 唯一键由 (tenant_id, subject_type, deleted) 改为 (tenant_id, deleted)：一租户一档，
--    subject_type 降级为角色标记列（不参与唯一键，由应用层回写）。
ALTER TABLE `bidding_subject_profile`
    DROP INDEX `uk_subject_profile_type`,
    ADD UNIQUE KEY `uk_subject_profile_tenant` (`tenant_id`,`deleted`);

-- =====================================================================
-- 执行时机与顺序（2026-09-28 补充结论）
--   ① 与「一租户一档」代码版本（subject 域，提交 3d4add4）**同一停机窗口**，且 **先执行本脚本、再发布新版本**：
--      - 先改键后发版：新唯一键生效期间旧代码仍按 subject_type 各写一行 → 第二行直接撞唯一键报错（导入/建档失败）；
--      - 先发版后跑脚本：新代码在存量双行上读取，读到哪一行取决于查询口径（可能出现"信息更全的那档看不见"）。
--      两者都不是可接受状态，故必须同窗口紧邻执行。
--   ② 非幂等（MySQL 8 无 DROP INDEX IF EXISTS）：每个库**只执行一次**；重复执行报 "Can't DROP 'uk_subject_profile_type'" 属预期，
--      不是失败。多环境（开发/测试/生产）各自执行一次。
--   ③ 执行前留档（可选回滚依据）：合并只看 deleted=1 的软删留痕，不物理删除；
--      SELECT tenant_id, COUNT(*) AS live_rows FROM bidding_subject_profile WHERE deleted=0 GROUP BY tenant_id HAVING live_rows>1;
--   ④ 执行后自检：
--      SHOW INDEX FROM bidding_subject_profile;   -- 应仅见 PRIMARY / idx_subject_profile_uscc / uk_subject_profile_tenant
--      上面的 HAVING live_rows>1 查询应无结果（同租户仅一档）。
--   ⑤ 本仓库本地开发库（txgy-mysql / 库 ruoyi-vue-pro）2026-09-28 核对：唯一键已是 uk_subject_profile_tenant、无重复档 = 已于本库执行过。
-- =====================================================================

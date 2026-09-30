-- 2026-09-11 追溯页手工签字：签字记录增加「实际操作账号」
-- sign_user_id 记「签的是谁」（下拉选的签字人），本列记「谁点的这一下」。
-- 两者不一致即可判定为代签——追溯页允许代签，但代签必须留痕。
ALTER TABLE `mes_set_sign_record`
    ADD COLUMN `operator_user_id` bigint NULL COMMENT '实际操作账号ID(代签时≠sign_user_id)' AFTER `sign_user_id`;

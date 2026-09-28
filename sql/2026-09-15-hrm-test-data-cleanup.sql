-- =====================================================================
-- 测试数据清理（2026-09-15 自动化测试产生）
-- 说明：全部采用软删除（deleted=b'1'），可通过把 deleted 改回 b'0' 恢复。
-- 覆盖范围：测试注册的个人账号、其简历/投递/沟通数据，以及测试新建的招聘职位。
-- 不涉及业务种子数据与人工数据。
-- =====================================================================

SET NAMES utf8mb4;

-- 1. 测试用户产生的沟通消息
UPDATE hrm_recruit_chat_message
SET deleted = b'1', update_time = NOW(), updater = 'cleanup-20260915'
WHERE deleted = b'0'
  AND conversation_id IN (
      SELECT id FROM (
          SELECT id FROM hrm_recruit_chat_conversation
          WHERE candidate_user_id IN (
              SELECT id FROM system_users
              WHERE username REGEXP '^(hrmtest|hrmflow|hrmqc|uiqa|uinow|vf_|vf2_|vf3_|exp_|exp2_|exp3_|exp4_|exp5_|mk_|dl_)'
          )
      ) t
  );

-- 2. 测试用户的沟通会话
UPDATE hrm_recruit_chat_conversation
SET deleted = b'1', update_time = NOW(), updater = 'cleanup-20260915'
WHERE deleted = b'0'
  AND candidate_user_id IN (
      SELECT id FROM system_users
      WHERE username REGEXP '^(hrmtest|hrmflow|hrmqc|uiqa|uinow|vf_|vf2_|vf3_|exp_|exp2_|exp3_|exp4_|exp5_|mk_|dl_)'
  );

-- 3. 测试投递对应的简历版本
UPDATE hrm_recruit_resume
SET deleted = b'1', update_time = NOW(), updater = 'cleanup-20260915'
WHERE deleted = b'0'
  AND candidate_id IN (
      SELECT candidate_id FROM (
          SELECT a.candidate_id
          FROM hrm_recruit_application a
          JOIN system_users u ON u.id = a.applicant_user_id
          WHERE u.username REGEXP '^(hrmtest|hrmflow|hrmqc|uiqa|uinow|vf_|vf2_|vf3_|exp_|exp2_|exp3_|exp4_|exp5_|mk_|dl_)'
      ) t
  );

-- 4. 测试投递产生的候选人（含同一手机号下的其他候选人记录）
UPDATE hrm_recruit_candidate
SET deleted = b'1', update_time = NOW(), updater = 'cleanup-20260915'
WHERE deleted = b'0'
  AND mobile IN (
      SELECT mobile FROM (
          SELECT DISTINCT c.mobile
          FROM hrm_recruit_candidate c
          JOIN hrm_recruit_application a ON a.candidate_id = c.id
          JOIN system_users u ON u.id = a.applicant_user_id
          WHERE u.username REGEXP '^(hrmtest|hrmflow|hrmqc|uiqa|uinow|vf_|vf2_|vf3_|exp_|exp2_|exp3_|exp4_|exp5_|mk_|dl_)'
            AND c.mobile IS NOT NULL
      ) t
  );

-- 5. 测试投递记录
UPDATE hrm_recruit_application
SET deleted = b'1', update_time = NOW(), updater = 'cleanup-20260915'
WHERE deleted = b'0'
  AND applicant_user_id IN (
      SELECT id FROM system_users
      WHERE username REGEXP '^(hrmtest|hrmflow|hrmqc|uiqa|uinow|vf_|vf2_|vf3_|exp_|exp2_|exp3_|exp4_|exp5_|mk_|dl_)'
  );

-- 6. 测试用户的个人简历
UPDATE hrm_personal_resume
SET deleted = b'1', update_time = NOW(), updater = 'cleanup-20260915'
WHERE deleted = b'0'
  AND user_id IN (
      SELECT id FROM system_users
      WHERE username REGEXP '^(hrmtest|hrmflow|hrmqc|uiqa|uinow|vf_|vf2_|vf3_|exp_|exp2_|exp3_|exp4_|exp5_|mk_|dl_)'
  );

-- 7. 测试用户的后台账号（软删除 + 停用）
UPDATE system_user_role
SET deleted = b'1', update_time = NOW(), updater = 'cleanup-20260915'
WHERE deleted = b'0'
  AND user_id IN (
      SELECT id FROM system_users
      WHERE username REGEXP '^(hrmtest|hrmflow|hrmqc|uiqa|uinow|vf_|vf2_|vf3_|exp_|exp2_|exp3_|exp4_|exp5_|mk_|dl_)'
  );

UPDATE system_users
SET deleted = b'1', status = 1, update_time = NOW(), updater = 'cleanup-20260915'
WHERE deleted = b'0'
  AND username REGEXP '^(hrmtest|hrmflow|hrmqc|uiqa|uinow|vf_|vf2_|vf3_|exp_|exp2_|exp3_|exp4_|exp5_|mk_|dl_)';

-- 8. 自动化测试创建的招聘职位（软删除 + 暂停）
UPDATE hrm_recruit_post
SET deleted = b'1', status = 0, stop_reason = '测试数据清理', update_time = NOW(), updater = 'cleanup-20260915'
WHERE deleted = b'0'
  AND post_name LIKE '自动化测试岗位%';

-- 9. 清理后核对（可人工执行）
-- SELECT COUNT(*) AS test_users_left FROM system_users WHERE deleted = b'0' AND username REGEXP '^(hrmtest|hrmflow|hrmqc|uiqa|uinow|vf_|vf2_|vf3_|exp_|exp2_|exp3_|exp4_|exp5_|mk_|dl_)';
-- SELECT COUNT(*) AS test_posts_left FROM hrm_recruit_post WHERE deleted = b'0' AND post_name LIKE '自动化测试岗位%';

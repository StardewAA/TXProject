-- HRM 应聘记录主流程投影修复。
-- 执行前提：已执行 2026-09-03-hrm-recruit-groups-integration.sql。
-- 只修复候选人状态和面试轮次投影，不回写候选人 post_id。

SET @schema_name = DATABASE();
SET @has_application_status_index = (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'hrm_recruit_application'
    AND index_name = 'idx_application_candidate_status_time'
);
SET @sql = IF(@has_application_status_index = 0,
  'ALTER TABLE `hrm_recruit_application` ADD KEY `idx_application_candidate_status_time` (`candidate_id`, `status_update_time`, `id`)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

DROP TEMPORARY TABLE IF EXISTS `tmp_hrm_current_application`;
CREATE TEMPORARY TABLE `tmp_hrm_current_application` (
  `candidate_id` bigint NOT NULL PRIMARY KEY,
  `application_id` bigint NOT NULL,
  `status` varchar(32) NOT NULL,
  `status_update_time` datetime NOT NULL
) ENGINE=Memory;

INSERT INTO `tmp_hrm_current_application`
(`candidate_id`, `application_id`, `status`, `status_update_time`)
SELECT a.`candidate_id`, a.`id`, a.`status`, a.`status_update_time`
FROM `hrm_recruit_application` a
WHERE a.`deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM `hrm_recruit_application` newer
    WHERE newer.`deleted` = b'0'
      AND newer.`candidate_id` = a.`candidate_id`
      AND (
        (CASE WHEN newer.`status` IN (
          'SUBMITTED', 'SCREENING', 'SCREENING_PASSED', 'PENDING_INTERVIEW',
          'INTERVIEW_SCHEDULED', 'INTERVIEWING', 'INTERVIEW_EVALUATING',
          'NEXT_INTERVIEW', 'INTERVIEW_CANCELED', 'INTERVIEW_ABSENT',
          'FINAL_INTERVIEW_PASSED', 'PASSED', 'PENDING_OFFER',
          'OFFER_APPROVING', 'OFFER_SENT', 'PENDING_ONBOARD'
        ) THEN 1 ELSE 0 END)
        >
        (CASE WHEN a.`status` IN (
          'SUBMITTED', 'SCREENING', 'SCREENING_PASSED', 'PENDING_INTERVIEW',
          'INTERVIEW_SCHEDULED', 'INTERVIEWING', 'INTERVIEW_EVALUATING',
          'NEXT_INTERVIEW', 'INTERVIEW_CANCELED', 'INTERVIEW_ABSENT',
          'FINAL_INTERVIEW_PASSED', 'PASSED', 'PENDING_OFFER',
          'OFFER_APPROVING', 'OFFER_SENT', 'PENDING_ONBOARD'
        ) THEN 1 ELSE 0 END)
        OR (
          (CASE WHEN newer.`status` IN (
            'SUBMITTED', 'SCREENING', 'SCREENING_PASSED', 'PENDING_INTERVIEW',
            'INTERVIEW_SCHEDULED', 'INTERVIEWING', 'INTERVIEW_EVALUATING',
            'NEXT_INTERVIEW', 'INTERVIEW_CANCELED', 'INTERVIEW_ABSENT',
            'FINAL_INTERVIEW_PASSED', 'PASSED', 'PENDING_OFFER',
            'OFFER_APPROVING', 'OFFER_SENT', 'PENDING_ONBOARD'
          ) THEN 1 ELSE 0 END)
          =
          (CASE WHEN a.`status` IN (
            'SUBMITTED', 'SCREENING', 'SCREENING_PASSED', 'PENDING_INTERVIEW',
            'INTERVIEW_SCHEDULED', 'INTERVIEWING', 'INTERVIEW_EVALUATING',
            'NEXT_INTERVIEW', 'INTERVIEW_CANCELED', 'INTERVIEW_ABSENT',
            'FINAL_INTERVIEW_PASSED', 'PASSED', 'PENDING_OFFER',
            'OFFER_APPROVING', 'OFFER_SENT', 'PENDING_ONBOARD'
          ) THEN 1 ELSE 0 END)
          AND (
            newer.`status_update_time` > a.`status_update_time`
            OR (newer.`status_update_time` = a.`status_update_time` AND newer.`id` > a.`id`)
          )
        )
      )
  );

UPDATE `hrm_recruit_candidate` c
JOIN `tmp_hrm_current_application` a
  ON a.`candidate_id` = c.`id`
 AND a.`status` <> 'WITHDRAWN'
SET c.`status` = CASE a.`status`
  WHEN 'SUBMITTED' THEN 1
  WHEN 'SCREENING' THEN 1
  WHEN 'SCREENING_PASSED' THEN 2
  WHEN 'PENDING_INTERVIEW' THEN 3
  WHEN 'INTERVIEW_SCHEDULED' THEN 3
  WHEN 'INTERVIEWING' THEN 3
  WHEN 'INTERVIEW_EVALUATING' THEN 3
  WHEN 'NEXT_INTERVIEW' THEN 3
  WHEN 'INTERVIEW_CANCELED' THEN 3
  WHEN 'INTERVIEW_ABSENT' THEN 3
  WHEN 'FINAL_INTERVIEW_PASSED' THEN 4
  WHEN 'PASSED' THEN 4
  WHEN 'PENDING_OFFER' THEN 4
  WHEN 'OFFER_APPROVING' THEN 4
  WHEN 'OFFER_SENT' THEN 5
  WHEN 'PENDING_ONBOARD' THEN 6
  WHEN 'ONBOARDED' THEN 8
  WHEN 'RESUME_REJECTED' THEN 7
  WHEN 'INTERVIEW_REJECTED' THEN 7
  WHEN 'OFFER_REJECTED' THEN 7
  WHEN 'OFFER_EXPIRED' THEN 7
  WHEN 'ELIMINATED' THEN 7
  ELSE c.`status`
END,
c.`stage_number` = CASE WHEN a.`status` IN (
  'PENDING_INTERVIEW', 'INTERVIEW_SCHEDULED', 'INTERVIEWING',
  'INTERVIEW_EVALUATING', 'NEXT_INTERVIEW', 'INTERVIEW_CANCELED',
  'INTERVIEW_ABSENT'
) THEN COALESCE((
  SELECT i.`stage_number`
  FROM `hrm_recruit_interview` i
  WHERE i.`application_id` = a.`application_id`
    AND i.`deleted` = b'0'
  ORDER BY COALESCE(i.`round_no`, i.`stage_number`) DESC, i.`id` DESC
  LIMIT 1
), 0) ELSE 0 END,
c.`status_update_time` = a.`status_update_time`
WHERE c.`deleted` = b'0';

DROP TEMPORARY TABLE `tmp_hrm_current_application`;

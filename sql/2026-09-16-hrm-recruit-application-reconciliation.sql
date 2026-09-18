SET @schema_name = DATABASE();
SET @has_application_applicant_post_status_index = (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'hrm_recruit_application'
    AND index_name = 'idx_application_applicant_post_status'
);
SET @sql = IF(@has_application_applicant_post_status_index = 0,
  'ALTER TABLE `hrm_recruit_application` ADD KEY `idx_application_applicant_post_status` (`applicant_user_id`, `post_id`, `status`)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

DROP TEMPORARY TABLE IF EXISTS `tmp_hrm_reconciliation_application`;
CREATE TEMPORARY TABLE `tmp_hrm_reconciliation_application` (
  `candidate_id` bigint NOT NULL PRIMARY KEY,
  `application_id` bigint NOT NULL,
  `application_post_id` bigint NOT NULL,
  `status` varchar(32) NOT NULL,
  `status_update_time` datetime NOT NULL
) ENGINE=Memory;

INSERT INTO `tmp_hrm_reconciliation_application`
(`candidate_id`, `application_id`, `application_post_id`, `status`, `status_update_time`)
SELECT a.`candidate_id`, a.`id`, a.`post_id`, a.`status`,
       COALESCE(a.`status_update_time`, '1000-01-01 00:00:00')
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
            COALESCE(newer.`status_update_time`, '1000-01-01 00:00:00')
              > COALESCE(a.`status_update_time`, '1000-01-01 00:00:00')
            OR (
              COALESCE(newer.`status_update_time`, '1000-01-01 00:00:00')
                = COALESCE(a.`status_update_time`, '1000-01-01 00:00:00')
              AND newer.`id` > a.`id`
            )
          )
        )
      )
  );

DROP TEMPORARY TABLE IF EXISTS `tmp_hrm_reconciliation_candidate`;
CREATE TEMPORARY TABLE `tmp_hrm_reconciliation_candidate` (
  `candidate_id` bigint NOT NULL PRIMARY KEY,
  `application_id` bigint NOT NULL,
  `candidate_post_id` bigint NULL,
  `application_post_id` bigint NOT NULL,
  `current_candidate_status` tinyint NULL,
  `expected_candidate_status` tinyint NULL,
  `current_stage_number` int NULL,
  `expected_stage_number` int NULL,
  `application_status_update_time` datetime NOT NULL,
  `issue_type` varchar(64) NOT NULL
) ENGINE=Memory;

INSERT INTO `tmp_hrm_reconciliation_candidate`
(`candidate_id`, `application_id`, `candidate_post_id`, `application_post_id`,
 `current_candidate_status`, `expected_candidate_status`, `current_stage_number`,
 `expected_stage_number`, `application_status_update_time`, `issue_type`)
SELECT c.`id`, a.`application_id`, c.`post_id`, a.`application_post_id`, c.`status`,
       CASE a.`status`
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
         WHEN 'OFFER_WITHDRAWN' THEN 7
         WHEN 'OFFER_EXPIRED' THEN 7
         WHEN 'OFFER_CANDIDATE_REJECTED' THEN 7
         WHEN 'ELIMINATED' THEN 7
         ELSE c.`status`
       END,
       c.`stage_number`,
       CASE WHEN a.`status` IN (
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
       a.`status_update_time`,
       CASE
         WHEN NOT (c.`status` <=> CASE a.`status`
           WHEN 'SUBMITTED' THEN 1 WHEN 'SCREENING' THEN 1
           WHEN 'SCREENING_PASSED' THEN 2 WHEN 'PENDING_INTERVIEW' THEN 3
           WHEN 'INTERVIEW_SCHEDULED' THEN 3 WHEN 'INTERVIEWING' THEN 3
           WHEN 'INTERVIEW_EVALUATING' THEN 3 WHEN 'NEXT_INTERVIEW' THEN 3
           WHEN 'INTERVIEW_CANCELED' THEN 3 WHEN 'INTERVIEW_ABSENT' THEN 3
           WHEN 'FINAL_INTERVIEW_PASSED' THEN 4 WHEN 'PASSED' THEN 4
           WHEN 'PENDING_OFFER' THEN 4 WHEN 'OFFER_APPROVING' THEN 4
           WHEN 'OFFER_SENT' THEN 5 WHEN 'PENDING_ONBOARD' THEN 6
           WHEN 'ONBOARDED' THEN 8 WHEN 'RESUME_REJECTED' THEN 7
           WHEN 'INTERVIEW_REJECTED' THEN 7 WHEN 'OFFER_REJECTED' THEN 7
           WHEN 'OFFER_WITHDRAWN' THEN 7 WHEN 'OFFER_EXPIRED' THEN 7
           WHEN 'OFFER_CANDIDATE_REJECTED' THEN 7 WHEN 'ELIMINATED' THEN 7
           ELSE c.`status` END) THEN 'STATUS_MISMATCH'
         ELSE 'STAGE_MISMATCH'
       END
FROM `hrm_recruit_candidate` c
JOIN `tmp_hrm_reconciliation_application` a
  ON a.`candidate_id` = c.`id`
WHERE c.`deleted` = b'0'
  AND a.`status` <> 'WITHDRAWN'
  AND (
    NOT (c.`status` <=> CASE a.`status`
      WHEN 'SUBMITTED' THEN 1 WHEN 'SCREENING' THEN 1
      WHEN 'SCREENING_PASSED' THEN 2 WHEN 'PENDING_INTERVIEW' THEN 3
      WHEN 'INTERVIEW_SCHEDULED' THEN 3 WHEN 'INTERVIEWING' THEN 3
      WHEN 'INTERVIEW_EVALUATING' THEN 3 WHEN 'NEXT_INTERVIEW' THEN 3
      WHEN 'INTERVIEW_CANCELED' THEN 3 WHEN 'INTERVIEW_ABSENT' THEN 3
      WHEN 'FINAL_INTERVIEW_PASSED' THEN 4 WHEN 'PASSED' THEN 4
      WHEN 'PENDING_OFFER' THEN 4 WHEN 'OFFER_APPROVING' THEN 4
      WHEN 'OFFER_SENT' THEN 5 WHEN 'PENDING_ONBOARD' THEN 6
      WHEN 'ONBOARDED' THEN 8 WHEN 'RESUME_REJECTED' THEN 7
      WHEN 'INTERVIEW_REJECTED' THEN 7 WHEN 'OFFER_REJECTED' THEN 7
      WHEN 'OFFER_WITHDRAWN' THEN 7 WHEN 'OFFER_EXPIRED' THEN 7
      WHEN 'OFFER_CANDIDATE_REJECTED' THEN 7 WHEN 'ELIMINATED' THEN 7
      ELSE c.`status` END)
    OR NOT (COALESCE(c.`stage_number`, 0) <=> CASE WHEN a.`status` IN (
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
    ), 0) ELSE 0 END)
  );

SELECT * FROM `tmp_hrm_reconciliation_candidate` ORDER BY `candidate_id`;

SELECT c.`id` AS `candidate_id`, c.`post_id` AS `candidate_post_id`,
       a.`application_id`, a.`application_post_id`, a.`status`
FROM `hrm_recruit_candidate` c
JOIN `tmp_hrm_reconciliation_application` a ON a.`candidate_id` = c.`id`
WHERE c.`deleted` = b'0'
  AND a.`status` <> 'WITHDRAWN'
  AND NOT (c.`post_id` <=> a.`application_post_id`)
ORDER BY c.`id`;

SELECT o.`id` AS `offer_id`, o.`application_id`, o.`post_id` AS `offer_post_id`,
       a.`post_id` AS `application_post_id`, o.`approval_status`
FROM `hrm_recruit_offer` o
JOIN `hrm_recruit_application` a ON a.`id` = o.`application_id` AND a.`deleted` = b'0'
WHERE o.`deleted` = b'0'
  AND NOT (o.`post_id` <=> a.`post_id`)
ORDER BY o.`id`;

SELECT a.`id` AS `application_id`, a.`candidate_id`, a.`post_id`,
       CASE WHEN c.`id` IS NULL THEN 'CANDIDATE_NOT_FOUND'
            WHEN p.`id` IS NULL THEN 'POST_NOT_FOUND'
            WHEN r.`id` IS NULL THEN 'RESUME_NOT_FOUND'
            ELSE 'OK' END AS `reference_status`
FROM `hrm_recruit_application` a
LEFT JOIN `hrm_recruit_candidate` c ON c.`id` = a.`candidate_id` AND c.`deleted` = b'0'
LEFT JOIN `hrm_recruit_post` p ON p.`id` = a.`post_id` AND p.`deleted` = b'0'
LEFT JOIN `hrm_recruit_resume` r ON r.`id` = a.`resume_id` AND r.`deleted` = b'0'
WHERE a.`deleted` = b'0'
  AND (c.`id` IS NULL OR p.`id` IS NULL OR r.`id` IS NULL)
ORDER BY a.`id`;

SELECT a.`candidate_id`, COUNT(*) AS `application_count`,
       COUNT(DISTINCT a.`post_id`) AS `post_count`
FROM `hrm_recruit_application` a
WHERE a.`deleted` = b'0'
GROUP BY a.`candidate_id`
HAVING COUNT(DISTINCT a.`post_id`) > 1
ORDER BY a.`candidate_id`;

UPDATE `hrm_recruit_candidate` c
JOIN `tmp_hrm_reconciliation_candidate` r ON r.`candidate_id` = c.`id`
SET c.`status` = r.`expected_candidate_status`,
    c.`stage_number` = r.`expected_stage_number`,
    c.`status_update_time` = r.`application_status_update_time`
WHERE c.`deleted` = b'0';

SELECT ROW_COUNT() AS `repaired_candidate_projection_rows`;

DROP TEMPORARY TABLE `tmp_hrm_reconciliation_candidate`;
DROP TEMPORARY TABLE `tmp_hrm_reconciliation_application`;

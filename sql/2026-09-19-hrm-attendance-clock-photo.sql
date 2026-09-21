-- ============================================================
-- HRM 现场打卡留痕：打卡照片（人脸/现场照）
-- 表：hrm_attendance_clock
-- 背景：防腐保温工地为室外作业，实名+人脸+定位是上岗与计薪的凭证，
--       一旦发生劳资纠纷，需要"本人+现场+时间+坐标"四位一体的证据。
-- 幂等：重复执行不会报错（MySQL 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 判断）
-- ============================================================

SET @db = DATABASE();

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema = @db AND table_name = 'hrm_attendance_clock'
                 AND column_name = 'photo_url') = 0,
  'ALTER TABLE hrm_attendance_clock ADD COLUMN photo_url varchar(512) DEFAULT NULL COMMENT ''打卡现场照片（人脸/工地照）地址'' AFTER mac',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

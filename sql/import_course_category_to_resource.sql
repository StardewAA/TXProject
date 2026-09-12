-- =============================================================================
-- 浜戝煿璁闄?- 妯″潡涓€锛氬皢璇剧▼鍒嗙被鏁版嵁瀵煎叆璧勬簮涓績鍒嗙被
-- 璇存槑锛?
--   1. 涓?training_category 澧炲姞 code 瀛楁锛堝灏氭湭娣诲姞锛夛紱
--   2. 灏?training_course_category 涓殑鏈夋晥鏁版嵁鍒嗗埆澶嶅埗鍒?doc / video / question
--      涓夌璧勬簮鍒嗙被涓嬶紙鍚勪竴浠界嫭绔嬫暟鎹級锛屽苟澶嶇敤璇剧▼鍒嗙被鐨?code 浣滀负鍚屾閿紱
--   3. 姣忕绫诲瀷鍐呴儴鐨?parent_id 浼氭牴鎹绋嬪垎绫荤殑鐖跺瓙鍏崇郴閲嶆柊寤虹珛锛?
--   4. 鑻ユ煇绫诲瀷涓嬪凡瀛樺湪鏁版嵁锛屽垯璺宠繃璇ョ被鍨嬶紝閬垮厤閲嶅瀵煎叆銆?
-- 鎵ц锛歞ocker exec -i deploy-mysql-1 mysql -uroot -proot ruoyi-vue-pro < import_course_category_to_resource.sql
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------
-- 1. 琛ュ叏 code 瀛楁涓庣储寮曪紙骞傜瓑锛?
-- ----------------------------------------------------------------------
SET @add_code = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'training_category'
      AND COLUMN_NAME = 'code'
);
SET @alter_sql = IF(@add_code = 0,
    'ALTER TABLE training_category
        ADD COLUMN code VARCHAR(64) DEFAULT NULL COMMENT "鍒嗙被缂栫爜锛岀敤浜?doc/video/question 涓夌被涔嬮棿鍚屾" AFTER name,
        ADD KEY idx_tcat_type_code (type, code)',
    'SELECT "code 瀛楁宸插瓨鍦紝璺宠繃 ALTER" AS message'
);
PREPARE stmt FROM @alter_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------
-- 2. 鍑嗗璇剧▼鍒嗙被鐖跺瓙缂栫爜鏄犲皠
-- ----------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_course_parent_code;
CREATE TEMPORARY TABLE tmp_course_parent_code (
    course_id   BIGINT PRIMARY KEY,
    course_code VARCHAR(64) NOT NULL,
    parent_id   BIGINT NOT NULL DEFAULT 0,
    parent_code VARCHAR(64) DEFAULT NULL,
    name        VARCHAR(100) NOT NULL,
    sort        INT NOT NULL DEFAULT 0
) ENGINE=InnoDB;

INSERT INTO tmp_course_parent_code (course_id, course_code, parent_id, parent_code, name, sort)
SELECT c.id,
       IFNULL(c.code, CONCAT('IMPORT-', c.id)),
       c.parent_id,
       IFNULL(p.code, CONCAT('IMPORT-', p.id)),
       c.name,
       c.sort
FROM training_course_category c
         LEFT JOIN training_course_category p ON p.id = c.parent_id
WHERE c.deleted = b'0';

-- ----------------------------------------------------------------------
-- 3. 鎸夌被鍨嬪鍏ワ紙doc / video / question锛夛紝宸插瓨鍦ㄥ垯璺宠繃
-- ----------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_rc_id;
CREATE TEMPORARY TABLE tmp_rc_id (
    type VARCHAR(20) NOT NULL,
    code VARCHAR(64) NOT NULL,
    id   BIGINT NOT NULL,
    PRIMARY KEY (type, code)
) ENGINE=InnoDB;

-- ---------- doc ----------
INSERT INTO training_category (type, name, code, parent_id, sort, creator)
SELECT 'doc', cp.name, cp.course_code, 0, cp.sort, 'admin'
FROM tmp_course_parent_code cp
WHERE NOT EXISTS (SELECT 1 FROM training_category WHERE type = 'doc' LIMIT 1);

INSERT INTO tmp_rc_id (type, code, id)
SELECT type, code, id FROM training_category WHERE type = 'doc';

UPDATE training_category rc
    JOIN tmp_course_parent_code cp ON rc.code = cp.course_code
    JOIN tmp_rc_id prc ON prc.type = rc.type AND prc.code = cp.parent_code
SET rc.parent_id = prc.id
WHERE rc.type = 'doc'
  AND cp.parent_id != 0;

-- ---------- video ----------
INSERT INTO training_category (type, name, code, parent_id, sort, creator)
SELECT 'video', cp.name, cp.course_code, 0, cp.sort, 'admin'
FROM tmp_course_parent_code cp
WHERE NOT EXISTS (SELECT 1 FROM training_category WHERE type = 'video' LIMIT 1);

INSERT INTO tmp_rc_id (type, code, id)
SELECT type, code, id FROM training_category WHERE type = 'video';

UPDATE training_category rc
    JOIN tmp_course_parent_code cp ON rc.code = cp.course_code
    JOIN tmp_rc_id prc ON prc.type = rc.type AND prc.code = cp.parent_code
SET rc.parent_id = prc.id
WHERE rc.type = 'video'
  AND cp.parent_id != 0;

-- ---------- question ----------
INSERT INTO training_category (type, name, code, parent_id, sort, creator)
SELECT 'question', cp.name, cp.course_code, 0, cp.sort, 'admin'
FROM tmp_course_parent_code cp
WHERE NOT EXISTS (SELECT 1 FROM training_category WHERE type = 'question' LIMIT 1);

INSERT INTO tmp_rc_id (type, code, id)
SELECT type, code, id FROM training_category WHERE type = 'question';

UPDATE training_category rc
    JOIN tmp_course_parent_code cp ON rc.code = cp.course_code
    JOIN tmp_rc_id prc ON prc.type = rc.type AND prc.code = cp.parent_code
SET rc.parent_id = prc.id
WHERE rc.type = 'question'
  AND cp.parent_id != 0;

-- ----------------------------------------------------------------------
-- 4. 娓呯悊涓存椂琛?
-- ----------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_course_parent_code;
DROP TEMPORARY TABLE IF EXISTS tmp_rc_id;

SET FOREIGN_KEY_CHECKS = 1;

-- 绠€鍗曟牎楠?
SELECT type, COUNT(*) AS cnt
FROM training_category
WHERE deleted = b'0'
GROUP BY type
ORDER BY type;

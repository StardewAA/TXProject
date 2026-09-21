-- 用户已授权的单次历史冲正：tenant1 / member247 / order280。
-- 先备份、停止故障重试源或部署防重，再执行。不能当作通用初始化脚本。
-- 保留原165扣3、217首次返3、218~221重复返还的原流水；只追加-12冲正。
-- 余额/原流水/最新流水任一与已对账快照不符即回滚，禁止换ID后盲跑。
-- 全部DML在同一过程、连接、事务内。过程DDL不参与回滚；成功后删除本过程。
-- 如前次失败留下同名过程，先检查定义和失败原因，不要覆盖未知过程。
DELIMITER $$
CREATE PROCEDURE mall_reconcile_points_20260915_order280()
main: BEGIN
    DECLARE v_balance INT DEFAULT NULL;
    DECLARE v_count INT DEFAULT 0;
    DECLARE v_sum INT DEFAULT 0;
    DECLARE v_latest BIGINT DEFAULT NULL;
    DECLARE v_record BIGINT DEFAULT NULL;
    DECLARE v_audit VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
        DEFAULT 'reversal:tenant1:order280:dup218-221:20260915';
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
    SELECT point INTO v_balance FROM member_user
      WHERE id=247 AND tenant_id=1 AND deleted=b'0' FOR UPDATE;
    IF v_balance IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Refusing reversal: scoped member missing';
    END IF;
    -- 先锁会员，随后读业务流水；并发冲正必须等待此锁，第二次不能再次扣分。
    SELECT COUNT(*) INTO v_count FROM member_point_record
      WHERE tenant_id=1 AND user_id=247 AND biz_id=v_audit;
    IF v_count > 0 THEN
        SELECT COUNT(*) INTO v_count FROM member_point_record
          WHERE tenant_id=1 AND user_id=247 AND biz_id=v_audit AND biz_type=2
            AND point=-12 AND total_point=84362 AND deleted=b'0'
            AND creator='approved-point-reversal-20260915';
        IF v_count <> 1 OR (SELECT COUNT(*) FROM member_point_record
                WHERE tenant_id=1 AND user_id=247 AND biz_id=v_audit) <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Refusing reversal: conflicting audit record';
        END IF;
        COMMIT;
        SELECT 'ALREADY_APPLIED' AS result, v_balance AS current_points;
        LEAVE main;
    END IF;

    -- 锁定并核对所有历史证据，不允许删掉/替换原始记录来通过检查。
    SELECT id FROM member_point_record
      WHERE tenant_id=1 AND user_id=247 AND id IN (165,217,218,219,220,221) FOR UPDATE;
    SELECT COUNT(*) INTO v_count FROM member_point_record
      WHERE tenant_id=1 AND user_id=247 AND biz_id='280' AND biz_type=11;
    IF v_count <> 1 OR NOT EXISTS (SELECT 1 FROM member_point_record
      WHERE id=165 AND tenant_id=1 AND user_id=247 AND biz_type=11 AND biz_id='280'
        AND point=-3 AND deleted=b'0') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Refusing reversal: original debit differs';
    END IF;
    SELECT COUNT(*), COALESCE(SUM(point),0) INTO v_count,v_sum FROM member_point_record
      WHERE tenant_id=1 AND user_id=247 AND biz_id='280' AND biz_type=12;
    IF v_count <> 5 OR v_sum <> 15 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Refusing reversal: return history differs';
    END IF;
    SELECT COUNT(*) INTO v_count FROM member_point_record
      WHERE tenant_id=1 AND user_id=247 AND id IN (217,218,219,220,221)
        AND biz_type=12 AND biz_id='280' AND point=3 AND deleted=b'0'
        AND total_point = 84362 + (id - 217) * 3;
    SELECT MAX(id) INTO v_latest FROM member_point_record WHERE tenant_id=1 AND user_id=247;
    IF v_count <> 5 OR v_balance <> 84374 OR v_latest <> 221 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Refusing reversal: balance or latest record changed, reconcile again';
    END IF;

    UPDATE member_user SET point=point-12, updater='approved-point-reversal-20260915'
      WHERE id=247 AND tenant_id=1 AND deleted=b'0' AND point=84374;
    IF ROW_COUNT() <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Refusing reversal: balance update not exactly one row';
    END IF;
    INSERT INTO member_point_record
      (user_id,biz_id,biz_type,title,description,point,total_point,creator,updater,create_time,update_time,deleted,tenant_id)
    VALUES (247,v_audit,2,'积分冲正：重复取消返还',
      '用户2026-09-15授权；订单280原扣分流水165为-3；保留首次返还217；重复返还218、219、220、221共+12，本记录-12冲正。原流水完整保留，不变更活动商品关联或订单状态。',
      -12,84362,'approved-point-reversal-20260915','approved-point-reversal-20260915',UTC_TIMESTAMP(),UTC_TIMESTAMP(),b'0',1);
    IF ROW_COUNT() <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Refusing reversal: journal insert not exactly one row';
    END IF;
    SET v_record=LAST_INSERT_ID();
    COMMIT;
    SELECT 'APPLIED' AS result, v_record AS reversal_record_id, 84374 AS points_before, 84362 AS points_after, -12 AS delta;
END$$
DELIMITER ;
CALL mall_reconcile_points_20260915_order280();
DROP PROCEDURE mall_reconcile_points_20260915_order280;

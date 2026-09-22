-- 第 21 节：经人工复核后启用“单店运营”后台账号。
--
-- 安全边界：
-- 1. 仅处理当前已确认的 74 个历史候选账号，以及 tenant_id=1/member_id=316 的 comency 账号；
-- 2. 一律授予 mall_shop_merchant，并以 scope_type=2 仅授权迁移表中的一个店铺；
-- 3. 不处理 tenant_id=3000，不处理 member_id=315，不授予 mall_enterprise_admin；
-- 4. 不生成、复用或复制任何密码。执行者必须在同一个 MySQL 会话中，提前向临时输入表写入
--    每个账号各不相同的新 BCrypt 哈希，以及同租户有效管理员 verifier；
-- 5. 脚本执行成功或失败都会清空临时输入表。请勿把真实哈希写入仓库、命令历史或部署日志。
-- 注意：带盐 BCrypt 无法由 SQL 反推出两个哈希是否来自同一明文；外部密码重置流程仍必须为
-- 每个账号生成不同的高强度临时口令。本脚本负责校验哈希格式、哈希唯一性及禁止复制既有口令哈希。
--
-- 推荐执行方式（必须使用同一 MySQL 会话）：
--   CREATE TEMPORARY TABLE tmp_mall_shop_merchant_activation_input (...下方同结构...);
--   INSERT INTO tmp_mall_shop_merchant_activation_input
--     (migration_id, login_name, bcrypt_password_hash, verifier_admin_user_id,
--      purchase_login_disposition)
--   VALUES (...由受控密码重置流程生成的数据...);
--   SOURCE deploy/mysql/repair/2026-09-21-mall-shop-merchant-account-activation.sql;
--
-- purchase_login_disposition 只能为 KEEP 或 REVOKE_AFTER_HANDOFF，表示是否保留同一自然人的
-- member 采购登录；无论选择哪一项，都不会把 member 密码复制给后台账号。

SET NAMES utf8mb4;

CREATE TEMPORARY TABLE IF NOT EXISTS tmp_mall_shop_merchant_activation_input (
  migration_id bigint NOT NULL,
  login_name varchar(30) NOT NULL,
  bcrypt_password_hash varchar(100) NOT NULL,
  verifier_admin_user_id bigint NOT NULL,
  purchase_login_disposition varchar(32) NOT NULL,
  PRIMARY KEY (migration_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP PROCEDURE IF EXISTS execute_mall_shop_merchant_activation;
DELIMITER $$
CREATE PROCEDURE execute_mall_shop_merchant_activation()
main: BEGIN
  DECLARE v_count bigint DEFAULT 0;
  DECLARE v_legacy_count bigint DEFAULT 0;
  DECLARE v_comency_count bigint DEFAULT 0;
  DECLARE v_invalid_count bigint DEFAULT 0;

  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    DROP TEMPORARY TABLE IF EXISTS tmp_mall_shop_merchant_resolved;
    DROP TEMPORARY TABLE IF EXISTS tmp_mall_shop_merchant_expected;
    DROP TEMPORARY TABLE IF EXISTS tmp_mall_shop_merchant_activation_input;
    RESIGNAL;
  END;

  -- 基础表必须先由 2026-09-21-mall-section21-admin-business.sql 创建。
  SELECT COUNT(*) INTO v_count
  FROM information_schema.tables
  WHERE table_schema=DATABASE() AND table_name IN (
    'product_business_account_migration','product_enterprise_admin_member',
    'product_shop_admin_authorization','product_business_operation_audit');
  IF v_count <> 4 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='单店账号启用中止：请先执行第21节后台经营基础脚本';
  END IF;

  SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
  START TRANSACTION;

  DROP TEMPORARY TABLE IF EXISTS tmp_mall_shop_merchant_expected;
  CREATE TEMPORARY TABLE tmp_mall_shop_merchant_expected (
    migration_id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    member_id bigint NOT NULL,
    enterprise_id bigint NOT NULL,
    shop_id bigint NOT NULL,
    login_name varchar(30) NOT NULL,
    member_mobile varchar(11) NOT NULL,
    member_nickname varchar(30) NOT NULL,
    PRIMARY KEY (migration_id),
    UNIQUE KEY uk_tenant_login (tenant_id,login_name),
    UNIQUE KEY uk_tenant_member (tenant_id,member_id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

  INSERT INTO tmp_mall_shop_merchant_expected
    (migration_id,tenant_id,member_id,enterprise_id,shop_id,login_name,member_mobile,member_nickname)
  SELECT m.id,m.tenant_id,m.member_id,m.enterprise_id,m.shop_id,
         CASE WHEN m.tenant_id=1 AND m.member_id=316 THEN 'comency'
              ELSE m.original_login_name END,
         COALESCE(NULLIF(mu.contact_mobile,''),NULLIF(mu.mobile,'')),
         COALESCE(NULLIF(mu.nickname,''),CASE WHEN m.member_id=316 THEN 'comency' ELSE m.original_login_name END)
  FROM product_business_account_migration m
  JOIN member_user mu
    ON mu.id=m.member_id AND mu.tenant_id=m.tenant_id
   AND mu.status=0 AND mu.deleted=b'0'
  JOIN product_enterprise_application a
    ON a.member_id=m.member_id AND a.enterprise_id=m.enterprise_id AND a.tenant_id=m.tenant_id
   AND a.identity='supplier' AND a.status=1 AND a.deleted=b'0'
  JOIN product_enterprise e
    ON e.id=m.enterprise_id AND e.tenant_id=m.tenant_id
   AND e.audit_status=1 AND e.deleted=b'0'
  JOIN product_shop s
    ON s.id=m.shop_id AND s.enterprise_id=m.enterprise_id AND s.tenant_id=m.tenant_id
   AND s.status=0 AND s.deleted=b'0'
  WHERE m.deleted=b'0'
    AND COALESCE(NULLIF(mu.contact_mobile,''),NULLIF(mu.mobile,'')) IS NOT NULL
    AND (
      (m.tenant_id BETWEEN 2001 AND 2024
       AND m.original_login_name IS NOT NULL AND m.original_login_name<>'')
      OR (m.tenant_id=1 AND m.member_id=316 AND m.enterprise_id=910123 AND m.shop_id=1)
    )
    AND m.tenant_id<>3000
    AND m.member_id<>315;

  SELECT COUNT(*),
         SUM(tenant_id BETWEEN 2001 AND 2024),
         SUM(tenant_id=1 AND member_id=316 AND enterprise_id=910123 AND shop_id=1)
    INTO v_count,v_legacy_count,v_comency_count
  FROM tmp_mall_shop_merchant_expected;
  IF v_count<>75 OR v_legacy_count<>74 OR v_comency_count<>1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='单店账号启用中止：目标集合不再是74个历史候选加member316，请重新审计';
  END IF;

  SELECT COUNT(*) INTO v_count FROM tmp_mall_shop_merchant_activation_input;
  IF v_count<>75 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='单店账号启用中止：必须为75个目标账号逐一提供外部安全输入';
  END IF;

  SELECT COUNT(*) INTO v_invalid_count
  FROM tmp_mall_shop_merchant_expected e
  LEFT JOIN tmp_mall_shop_merchant_activation_input i ON i.migration_id=e.migration_id
  WHERE i.migration_id IS NULL
     OR BINARY i.login_name<>BINARY e.login_name
     OR i.purchase_login_disposition NOT IN ('KEEP','REVOKE_AFTER_HANDOFF');
  IF v_invalid_count>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='单店账号启用中止：存在缺失输入、登录名不一致或采购登录处置无效';
  END IF;

  SELECT COUNT(*) INTO v_invalid_count
  FROM tmp_mall_shop_merchant_activation_input i
  LEFT JOIN tmp_mall_shop_merchant_expected e ON e.migration_id=i.migration_id
  WHERE e.migration_id IS NULL OR i.login_name='' OR CHAR_LENGTH(i.login_name)>30;
  IF v_invalid_count>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='单店账号启用中止：输入包含范围外账号或非法登录名';
  END IF;

  -- BCrypt 只接受 cost 10-14；哈希必须逐账号唯一。
  SELECT COUNT(*) INTO v_invalid_count
  FROM tmp_mall_shop_merchant_activation_input
  WHERE CHAR_LENGTH(bcrypt_password_hash)<>60
     OR BINARY bcrypt_password_hash NOT REGEXP BINARY
        '^[$]2[aby][$]1[0-4][$][./A-Za-z0-9]{53}$';
  IF v_invalid_count>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='单店账号启用中止：密码输入不是cost 10-14的BCrypt哈希';
  END IF;

  SELECT COUNT(*) INTO v_invalid_count
  FROM (
    SELECT BINARY bcrypt_password_hash password_hash
    FROM tmp_mall_shop_merchant_activation_input
    GROUP BY BINARY bcrypt_password_hash
    HAVING COUNT(*)>1
  ) duplicate_password_hash;
  IF v_invalid_count>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='单店账号启用中止：不同账号不得复用同一BCrypt哈希';
  END IF;

  -- verifier 必须是同租户启用中的 tenant_admin/super_admin，且不能是待启用账号本人。
  SELECT COUNT(*) INTO v_invalid_count
  FROM tmp_mall_shop_merchant_expected e
  JOIN tmp_mall_shop_merchant_activation_input i ON i.migration_id=e.migration_id
  WHERE NOT EXISTS (
    SELECT 1
    FROM system_users verifier
    JOIN system_user_role verifier_ur
      ON verifier_ur.user_id=verifier.id AND verifier_ur.tenant_id=verifier.tenant_id
     AND verifier_ur.deleted=b'0'
    JOIN system_role verifier_role
      ON verifier_role.id=verifier_ur.role_id AND verifier_role.tenant_id=verifier_ur.tenant_id
     AND verifier_role.code IN ('tenant_admin','super_admin')
     AND verifier_role.status=0 AND verifier_role.deleted=b'0'
    WHERE verifier.id=i.verifier_admin_user_id AND verifier.tenant_id=e.tenant_id
      AND verifier.status=0 AND verifier.deleted=b'0'
  );
  IF v_invalid_count>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='单店账号启用中止：verifier不是同租户有效管理员';
  END IF;

  -- 每个目标租户必须且只能有一个有效的单店运营角色模板。
  SELECT COUNT(*) INTO v_invalid_count
  FROM tmp_mall_shop_merchant_expected e
  WHERE (SELECT COUNT(*) FROM system_role r
         WHERE r.tenant_id=e.tenant_id AND r.code='mall_shop_merchant'
           AND r.status=0 AND r.deleted=b'0')<>1;
  IF v_invalid_count>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='单店账号启用中止：目标租户的mall_shop_merchant角色不唯一或不可用';
  END IF;

  -- 74 个旧候选必须仍是同租户唯一账号，且三项身份线索一致。允许脚本成功后的幂等重跑。
  SELECT COUNT(*) INTO v_invalid_count
  FROM tmp_mall_shop_merchant_expected e
  JOIN product_business_account_migration m ON m.id=e.migration_id AND m.tenant_id=e.tenant_id
  WHERE e.member_id<>316
    AND (
      (SELECT COUNT(*) FROM system_users u
       WHERE u.tenant_id=e.tenant_id
         AND u.username COLLATE utf8mb4_unicode_ci=e.login_name COLLATE utf8mb4_unicode_ci)<>1
      OR NOT EXISTS (
        SELECT 1 FROM system_users u
        WHERE u.tenant_id=e.tenant_id
          AND u.username COLLATE utf8mb4_unicode_ci=e.login_name COLLATE utf8mb4_unicode_ci
          AND u.mobile COLLATE utf8mb4_unicode_ci=e.member_mobile COLLATE utf8mb4_unicode_ci
          AND u.nickname COLLATE utf8mb4_unicode_ci=e.member_nickname COLLATE utf8mb4_unicode_ci
          AND (u.creator IN ('mall-business-catalog-20260921','mall-kedun-20260917')
               OR (m.admin_user_id=u.id AND m.target_role_code='mall_shop_merchant'
                   AND m.migration_status='READY' AND m.verified_by IS NOT NULL))
      )
    );
  IF v_invalid_count>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='单店账号启用中止：历史候选账号不再唯一或身份线索已变化';
  END IF;

  -- comency 首次执行时必须不存在；幂等重跑时只接受本脚本已经完成并留有迁移记录的账号。
  SELECT COUNT(*) INTO v_count
  FROM system_users u
  WHERE u.tenant_id=1 AND u.username='comency';
  IF v_count>1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='单店账号启用中止：tenant1存在多个comency后台账号';
  END IF;
  IF v_count=1 THEN
    SELECT COUNT(*) INTO v_invalid_count
    FROM system_users u
    JOIN product_business_account_migration m
      ON m.tenant_id=1 AND m.member_id=316 AND m.admin_user_id=u.id
     AND m.target_role_code='mall_shop_merchant' AND m.migration_status='READY'
     AND m.verified_by IS NOT NULL AND m.deleted=b'0'
    JOIN tmp_mall_shop_merchant_expected e ON e.migration_id=m.id
    WHERE u.tenant_id=1 AND u.username='comency'
      AND u.mobile COLLATE utf8mb4_unicode_ci=e.member_mobile COLLATE utf8mb4_unicode_ci
      AND u.nickname COLLATE utf8mb4_unicode_ci=e.member_nickname COLLATE utf8mb4_unicode_ci
      AND u.creator='mall-section21-shop-activation';
    IF v_invalid_count<>1 THEN
      SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT='单店账号启用中止：现有comency不是本迁移已核验账号';
    END IF;
  ELSE
    SELECT COUNT(*) INTO v_invalid_count
    FROM system_users u
    JOIN tmp_mall_shop_merchant_expected e ON e.tenant_id=1 AND e.member_id=316
    WHERE u.tenant_id=1
      AND u.mobile COLLATE utf8mb4_unicode_ci=e.member_mobile COLLATE utf8mb4_unicode_ci;
    IF v_invalid_count>0 THEN
      SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT='单店账号启用中止：comency手机号已被其他后台账号占用';
    END IF;
  END IF;

  -- 新哈希不能与任何会员密码相同。与后台账号相同仅允许本脚本已完成后的同账号幂等重跑。
  SELECT COUNT(*) INTO v_invalid_count
  FROM tmp_mall_shop_merchant_activation_input i
  WHERE EXISTS (
    SELECT 1 FROM member_user member_account
    WHERE BINARY member_account.password=BINARY i.bcrypt_password_hash
  );
  IF v_invalid_count>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='单店账号启用中止：禁止复制或复用会员密码哈希';
  END IF;

  SELECT COUNT(*) INTO v_invalid_count
  FROM tmp_mall_shop_merchant_activation_input i
  JOIN tmp_mall_shop_merchant_expected e ON e.migration_id=i.migration_id
  JOIN product_business_account_migration m ON m.id=e.migration_id
  WHERE EXISTS (
    SELECT 1 FROM system_users other_user
    WHERE BINARY other_user.password=BINARY i.bcrypt_password_hash
      AND NOT (
        other_user.tenant_id=e.tenant_id
        AND other_user.username COLLATE utf8mb4_unicode_ci=e.login_name COLLATE utf8mb4_unicode_ci
        AND m.admin_user_id=other_user.id
        AND m.target_role_code='mall_shop_merchant'
        AND m.migration_status='READY'
        AND m.verified_by IS NOT NULL
      )
  );
  IF v_invalid_count>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='单店账号启用中止：BCrypt哈希已被其他或尚未核验的后台账号使用';
  END IF;

  -- 锁定迁移记录及现有候选，避免核验和授权之间被并发修改。
  SELECT m.id AS locked_migration_id
  FROM product_business_account_migration m
  JOIN tmp_mall_shop_merchant_expected e ON e.migration_id=m.id
  ORDER BY m.id FOR UPDATE;

  SELECT u.id AS locked_admin_user_id
  FROM system_users u
  JOIN tmp_mall_shop_merchant_expected e
    ON e.tenant_id=u.tenant_id
   AND e.login_name COLLATE utf8mb4_unicode_ci=u.username COLLATE utf8mb4_unicode_ci
  ORDER BY u.id FOR UPDATE;

  INSERT INTO system_users
    (username,password,nickname,remark,mobile,sex,avatar,status,
     creator,create_time,updater,update_time,deleted,tenant_id)
  SELECT e.login_name,i.bcrypt_password_hash,e.member_nickname,
         '第21节商城单店运营独立账号',e.member_mobile,COALESCE(mu.sex,0),COALESCE(mu.avatar,''),0,
         'mall-section21-shop-activation',NOW(),CAST(i.verifier_admin_user_id AS CHAR),NOW(),b'0',e.tenant_id
  FROM tmp_mall_shop_merchant_expected e
  JOIN tmp_mall_shop_merchant_activation_input i ON i.migration_id=e.migration_id
  JOIN member_user mu ON mu.id=e.member_id AND mu.tenant_id=e.tenant_id
  WHERE e.tenant_id=1 AND e.member_id=316
    AND NOT EXISTS (
      SELECT 1 FROM system_users u
      WHERE u.tenant_id=e.tenant_id
        AND u.username COLLATE utf8mb4_unicode_ci=e.login_name COLLATE utf8mb4_unicode_ci
    );

  DROP TEMPORARY TABLE IF EXISTS tmp_mall_shop_merchant_resolved;
  CREATE TEMPORARY TABLE tmp_mall_shop_merchant_resolved (
    migration_id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    member_id bigint NOT NULL,
    enterprise_id bigint NOT NULL,
    shop_id bigint NOT NULL,
    login_name varchar(30) NOT NULL,
    admin_user_id bigint NOT NULL,
    verifier_admin_user_id bigint NOT NULL,
    purchase_login_disposition varchar(32) NOT NULL,
    bcrypt_password_hash varchar(100) NOT NULL,
    PRIMARY KEY (migration_id),
    UNIQUE KEY uk_admin_user (admin_user_id),
    UNIQUE KEY uk_tenant_login (tenant_id,login_name)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

  INSERT INTO tmp_mall_shop_merchant_resolved
    (migration_id,tenant_id,member_id,enterprise_id,shop_id,login_name,admin_user_id,
     verifier_admin_user_id,purchase_login_disposition,bcrypt_password_hash)
  SELECT e.migration_id,e.tenant_id,e.member_id,e.enterprise_id,e.shop_id,e.login_name,u.id,
         i.verifier_admin_user_id,i.purchase_login_disposition,i.bcrypt_password_hash
  FROM tmp_mall_shop_merchant_expected e
  JOIN tmp_mall_shop_merchant_activation_input i ON i.migration_id=e.migration_id
  JOIN system_users u
    ON u.tenant_id=e.tenant_id
   AND u.username COLLATE utf8mb4_unicode_ci=e.login_name COLLATE utf8mb4_unicode_ci;

  SELECT COUNT(*) INTO v_count FROM tmp_mall_shop_merchant_resolved;
  IF v_count<>75 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='单店账号启用中止：后台账号解析结果不是75条';
  END IF;

  SELECT COUNT(*) INTO v_invalid_count
  FROM tmp_mall_shop_merchant_resolved r
  JOIN tmp_mall_shop_merchant_expected e ON e.migration_id=r.migration_id
  JOIN system_users u ON u.id=r.admin_user_id AND u.tenant_id=r.tenant_id
  WHERE r.admin_user_id=r.verifier_admin_user_id
     OR u.mobile COLLATE utf8mb4_unicode_ci<>e.member_mobile COLLATE utf8mb4_unicode_ci
     OR u.nickname COLLATE utf8mb4_unicode_ci<>e.member_nickname COLLATE utf8mb4_unicode_ci
     OR (SELECT COUNT(*) FROM system_users same_login
         WHERE same_login.tenant_id=r.tenant_id
           AND same_login.username COLLATE utf8mb4_unicode_ci=r.login_name COLLATE utf8mb4_unicode_ci)<>1
     OR (SELECT COUNT(*) FROM system_users same_mobile
         WHERE same_mobile.tenant_id=r.tenant_id
           AND same_mobile.mobile COLLATE utf8mb4_unicode_ci=e.member_mobile COLLATE utf8mb4_unicode_ci)<>1;
  IF v_invalid_count>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='单店账号启用中止：账号身份不匹配或核验人与目标账号相同';
  END IF;

  SELECT COUNT(*) INTO v_invalid_count
  FROM tmp_mall_shop_merchant_resolved resolved
  JOIN product_business_account_migration m ON m.id=resolved.migration_id
  WHERE (m.admin_user_id IS NOT NULL AND m.admin_user_id<>resolved.admin_user_id)
     OR (m.verified_by IS NOT NULL AND
         (m.verified_by<>resolved.verifier_admin_user_id
          OR m.target_role_code<>'mall_shop_merchant'
          OR m.purchase_login_disposition<>resolved.purchase_login_disposition
          OR m.migration_status<>'READY'));
  IF v_invalid_count>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='单店账号启用中止：迁移记录已有不一致的账号或人工核验结果';
  END IF;

  -- 已完成记录只允许原样重跑。后续改密、停用账号或撤销授权都是新的业务状态，
  -- 旧启用输入不得把这些状态悄悄恢复。
  SELECT COUNT(*) INTO v_invalid_count
  FROM tmp_mall_shop_merchant_resolved resolved
  JOIN product_business_account_migration migration_row ON migration_row.id=resolved.migration_id
  JOIN system_users u ON u.id=resolved.admin_user_id AND u.tenant_id=resolved.tenant_id
  WHERE migration_row.migration_status='READY' AND migration_row.verified_by IS NOT NULL
    AND (u.status<>0 OR u.deleted<>b'0'
      OR BINARY u.password<>BINARY resolved.bcrypt_password_hash
      OR NOT EXISTS (
        SELECT 1 FROM system_user_role ready_ur
        JOIN system_role ready_role
          ON ready_role.id=ready_ur.role_id AND ready_role.tenant_id=ready_ur.tenant_id
         AND ready_role.code='mall_shop_merchant'
         AND ready_role.status=0 AND ready_role.deleted=b'0'
        WHERE ready_ur.user_id=resolved.admin_user_id
          AND ready_ur.tenant_id=resolved.tenant_id AND ready_ur.deleted=b'0')
      OR NOT EXISTS (
        SELECT 1 FROM product_enterprise_admin_member ready_membership
        WHERE ready_membership.admin_user_id=resolved.admin_user_id
          AND ready_membership.enterprise_id=resolved.enterprise_id
          AND ready_membership.tenant_id=resolved.tenant_id
          AND ready_membership.scope_type=2
          AND ready_membership.status=0 AND ready_membership.deleted=b'0')
      OR NOT EXISTS (
        SELECT 1 FROM product_shop_admin_authorization ready_grant
        WHERE ready_grant.admin_user_id=resolved.admin_user_id
          AND ready_grant.enterprise_id=resolved.enterprise_id
          AND ready_grant.shop_id=resolved.shop_id
          AND ready_grant.tenant_id=resolved.tenant_id
          AND ready_grant.status=0 AND ready_grant.deleted=b'0'));
  IF v_invalid_count>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='单店账号启用中止：已完成账号发生改密、停用或授权变化，禁止旧输入覆盖';
  END IF;

  -- 只允许目标 merchant 角色；任何其他已启用角色都必须先人工撤销并重新审计。
  SELECT COUNT(*) INTO v_invalid_count
  FROM tmp_mall_shop_merchant_resolved resolved
  WHERE EXISTS (
    SELECT 1 FROM system_user_role ur
    JOIN system_role role_row ON role_row.id=ur.role_id AND role_row.tenant_id=ur.tenant_id
    WHERE ur.user_id=resolved.admin_user_id AND ur.tenant_id=resolved.tenant_id
      AND ur.deleted=b'0' AND role_row.deleted=b'0' AND role_row.status=0
      AND role_row.code<>'mall_shop_merchant'
  );
  IF v_invalid_count>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='单店账号启用中止：目标账号存在mall_shop_merchant之外的启用角色';
  END IF;

  SELECT COUNT(*) INTO v_invalid_count
  FROM tmp_mall_shop_merchant_resolved resolved
  WHERE (SELECT COUNT(*) FROM system_user_role ur
         JOIN system_role role_row ON role_row.id=ur.role_id AND role_row.tenant_id=ur.tenant_id
         WHERE ur.user_id=resolved.admin_user_id AND ur.tenant_id=resolved.tenant_id
           AND role_row.code='mall_shop_merchant')>1;
  IF v_invalid_count>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='单店账号启用中止：单店运营角色关系存在重复历史行';
  END IF;

  SELECT COUNT(*) INTO v_invalid_count
  FROM tmp_mall_shop_merchant_resolved resolved
  WHERE EXISTS (
      SELECT 1 FROM product_enterprise_admin_member membership
      WHERE membership.admin_user_id=resolved.admin_user_id
        AND membership.tenant_id=resolved.tenant_id
        AND membership.status=0 AND membership.deleted=b'0'
        AND membership.enterprise_id<>resolved.enterprise_id
    )
    OR EXISTS (
      SELECT 1 FROM product_shop_admin_authorization shop_grant
      WHERE shop_grant.admin_user_id=resolved.admin_user_id
        AND shop_grant.tenant_id=resolved.tenant_id
        AND shop_grant.status=0 AND shop_grant.deleted=b'0'
        AND (shop_grant.enterprise_id<>resolved.enterprise_id OR shop_grant.shop_id<>resolved.shop_id)
    );
  IF v_invalid_count>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='单店账号启用中止：目标账号已有其他企业或店铺的有效授权';
  END IF;

  SELECT COUNT(*) INTO v_invalid_count
  FROM tmp_mall_shop_merchant_resolved resolved
  WHERE (SELECT COUNT(*) FROM product_enterprise_admin_member membership
         WHERE membership.admin_user_id=resolved.admin_user_id
           AND membership.tenant_id=resolved.tenant_id
           AND membership.enterprise_id=resolved.enterprise_id)>1
     OR (SELECT COUNT(*) FROM product_shop_admin_authorization shop_grant
         WHERE shop_grant.admin_user_id=resolved.admin_user_id
           AND shop_grant.tenant_id=resolved.tenant_id
           AND shop_grant.shop_id=resolved.shop_id)>1;
  IF v_invalid_count>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='单店账号启用中止：目标授权存在重复历史行，请先人工合并';
  END IF;

  -- 仅到这里才写持久数据；全部输入已经通过 fail-closed 校验。
  UPDATE system_users u
  JOIN tmp_mall_shop_merchant_resolved resolved ON resolved.admin_user_id=u.id AND resolved.tenant_id=u.tenant_id
  SET u.password=resolved.bcrypt_password_hash,u.status=0,u.deleted=b'0',
      u.updater=CAST(resolved.verifier_admin_user_id AS CHAR),u.update_time=NOW()
  WHERE BINARY u.password<>BINARY resolved.bcrypt_password_hash
     OR u.status<>0 OR u.deleted<>b'0';

  UPDATE system_user_role ur
  JOIN tmp_mall_shop_merchant_resolved resolved
    ON resolved.admin_user_id=ur.user_id AND resolved.tenant_id=ur.tenant_id
  JOIN system_role role_row
    ON role_row.id=ur.role_id AND role_row.tenant_id=ur.tenant_id
   AND role_row.code='mall_shop_merchant' AND role_row.status=0 AND role_row.deleted=b'0'
  SET ur.deleted=b'0',ur.updater=CAST(resolved.verifier_admin_user_id AS CHAR),ur.update_time=NOW()
  WHERE ur.deleted IS NULL OR ur.deleted<>b'0';

  INSERT INTO system_user_role
    (user_id,role_id,creator,create_time,updater,update_time,deleted,tenant_id)
  SELECT resolved.admin_user_id,role_row.id,CAST(resolved.verifier_admin_user_id AS CHAR),NOW(),
         CAST(resolved.verifier_admin_user_id AS CHAR),NOW(),b'0',resolved.tenant_id
  FROM tmp_mall_shop_merchant_resolved resolved
  JOIN system_role role_row
    ON role_row.tenant_id=resolved.tenant_id AND role_row.code='mall_shop_merchant'
   AND role_row.status=0 AND role_row.deleted=b'0'
  WHERE NOT EXISTS (
    SELECT 1 FROM system_user_role existing_relation
    WHERE existing_relation.user_id=resolved.admin_user_id
      AND existing_relation.role_id=role_row.id
      AND existing_relation.tenant_id=resolved.tenant_id
  );

  UPDATE product_enterprise_admin_member membership
  JOIN tmp_mall_shop_merchant_resolved resolved
    ON resolved.admin_user_id=membership.admin_user_id
   AND resolved.enterprise_id=membership.enterprise_id
   AND resolved.tenant_id=membership.tenant_id
  SET membership.scope_type=2,membership.status=0,membership.deleted=b'0',
      membership.updater=CAST(resolved.verifier_admin_user_id AS CHAR),membership.update_time=NOW()
  WHERE membership.scope_type<>2 OR membership.status<>0 OR membership.deleted<>b'0';

  INSERT INTO product_enterprise_admin_member
    (admin_user_id,enterprise_id,scope_type,status,creator,create_time,updater,update_time,deleted,tenant_id)
  SELECT resolved.admin_user_id,resolved.enterprise_id,2,0,
         CAST(resolved.verifier_admin_user_id AS CHAR),NOW(),
         CAST(resolved.verifier_admin_user_id AS CHAR),NOW(),b'0',resolved.tenant_id
  FROM tmp_mall_shop_merchant_resolved resolved
  WHERE NOT EXISTS (
    SELECT 1 FROM product_enterprise_admin_member existing_membership
    WHERE existing_membership.admin_user_id=resolved.admin_user_id
      AND existing_membership.enterprise_id=resolved.enterprise_id
      AND existing_membership.tenant_id=resolved.tenant_id
  );

  UPDATE product_shop_admin_authorization shop_grant
  JOIN tmp_mall_shop_merchant_resolved resolved
    ON resolved.admin_user_id=shop_grant.admin_user_id
   AND resolved.shop_id=shop_grant.shop_id
   AND resolved.tenant_id=shop_grant.tenant_id
  SET shop_grant.enterprise_id=resolved.enterprise_id,shop_grant.status=0,shop_grant.deleted=b'0',
      shop_grant.updater=CAST(resolved.verifier_admin_user_id AS CHAR),shop_grant.update_time=NOW()
  WHERE shop_grant.enterprise_id<>resolved.enterprise_id
     OR shop_grant.status<>0 OR shop_grant.deleted<>b'0';

  INSERT INTO product_shop_admin_authorization
    (admin_user_id,enterprise_id,shop_id,status,creator,create_time,updater,update_time,deleted,tenant_id)
  SELECT resolved.admin_user_id,resolved.enterprise_id,resolved.shop_id,0,
         CAST(resolved.verifier_admin_user_id AS CHAR),NOW(),
         CAST(resolved.verifier_admin_user_id AS CHAR),NOW(),b'0',resolved.tenant_id
  FROM tmp_mall_shop_merchant_resolved resolved
  WHERE NOT EXISTS (
    SELECT 1 FROM product_shop_admin_authorization existing_grant
    WHERE existing_grant.admin_user_id=resolved.admin_user_id
      AND existing_grant.shop_id=resolved.shop_id
      AND existing_grant.tenant_id=resolved.tenant_id
  );

  -- 商户运营账号不是企业管理员，不能占用企业入驻审核记录的 admin_user_id。
  UPDATE product_enterprise_application application_row
  JOIN tmp_mall_shop_merchant_resolved resolved
    ON resolved.member_id=application_row.member_id
   AND resolved.enterprise_id=application_row.enterprise_id
   AND resolved.tenant_id=application_row.tenant_id
  SET application_row.admin_user_id=NULL,
      application_row.updater=CAST(resolved.verifier_admin_user_id AS CHAR),
      application_row.update_time=NOW()
  WHERE application_row.admin_user_id=resolved.admin_user_id;

  UPDATE product_business_account_migration migration_row
  JOIN tmp_mall_shop_merchant_resolved resolved ON resolved.migration_id=migration_row.id
  SET migration_row.admin_user_id=resolved.admin_user_id,
      migration_row.original_login_name=resolved.login_name,
      migration_row.target_role_code='mall_shop_merchant',
      migration_row.keep_purchase_login=NULL,
      migration_row.purchase_login_disposition=resolved.purchase_login_disposition,
      migration_row.account_preparation_status='ELIGIBLE_FOR_VERIFICATION',
      migration_row.migration_status='READY',
      migration_row.verified_by=resolved.verifier_admin_user_id,
      migration_row.verified_at=COALESCE(migration_row.verified_at,NOW()),
      migration_row.updater=CAST(resolved.verifier_admin_user_id AS CHAR),
      migration_row.update_time=NOW(),migration_row.deleted=b'0'
  WHERE NOT (migration_row.admin_user_id<=>resolved.admin_user_id)
     OR BINARY COALESCE(migration_row.original_login_name,'')<>BINARY resolved.login_name
     OR migration_row.target_role_code<>'mall_shop_merchant'
     OR migration_row.keep_purchase_login IS NOT NULL
     OR migration_row.purchase_login_disposition<>resolved.purchase_login_disposition
     OR migration_row.account_preparation_status<>'ELIGIBLE_FOR_VERIFICATION'
     OR migration_row.migration_status<>'READY'
     OR NOT (migration_row.verified_by<=>resolved.verifier_admin_user_id)
     OR migration_row.verified_at IS NULL OR migration_row.deleted<>b'0';

  INSERT INTO product_business_operation_audit
    (enterprise_id,shop_id,actor_type,actor_id,action,resource_type,resource_id,result,detail,
     creator,create_time,updater,update_time,deleted,tenant_id)
  SELECT resolved.enterprise_id,resolved.shop_id,'ADMIN',resolved.verifier_admin_user_id,
         'SHOP_MERCHANT_ACCOUNT_ACTIVATE','BUSINESS_ACCOUNT_MIGRATION',resolved.migration_id,'SUCCESS',
         CONCAT('adminUserId=',resolved.admin_user_id,
                ',targetRole=mall_shop_merchant,scopeType=2,shopId=',resolved.shop_id),
         CAST(resolved.verifier_admin_user_id AS CHAR),NOW(),
         CAST(resolved.verifier_admin_user_id AS CHAR),NOW(),b'0',resolved.tenant_id
  FROM tmp_mall_shop_merchant_resolved resolved
  WHERE NOT EXISTS (
    SELECT 1 FROM product_business_operation_audit existing_audit
    WHERE existing_audit.tenant_id=resolved.tenant_id
      AND existing_audit.action='SHOP_MERCHANT_ACCOUNT_ACTIVATE'
      AND existing_audit.resource_type='BUSINESS_ACCOUNT_MIGRATION'
      AND existing_audit.resource_id=resolved.migration_id
      AND existing_audit.result='SUCCESS' AND existing_audit.deleted=b'0'
  );

  -- 提交前闭环校验：账号、角色、单店范围、迁移审计必须各 75 条，且没有企业级范围。
  SELECT COUNT(*) INTO v_invalid_count
  FROM tmp_mall_shop_merchant_resolved resolved
  JOIN system_users u ON u.id=resolved.admin_user_id AND u.tenant_id=resolved.tenant_id
  JOIN product_business_account_migration migration_row
    ON migration_row.id=resolved.migration_id AND migration_row.tenant_id=resolved.tenant_id
  WHERE u.status<>0 OR u.deleted<>b'0'
     OR BINARY u.password<>BINARY resolved.bcrypt_password_hash
     OR migration_row.admin_user_id<>resolved.admin_user_id
     OR migration_row.target_role_code<>'mall_shop_merchant'
     OR migration_row.purchase_login_disposition<>resolved.purchase_login_disposition
     OR migration_row.account_preparation_status<>'ELIGIBLE_FOR_VERIFICATION'
     OR migration_row.migration_status<>'READY'
     OR migration_row.verified_by<>resolved.verifier_admin_user_id
     OR migration_row.verified_at IS NULL;
  IF v_invalid_count>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='单店账号启用回滚：账号或迁移审计闭环校验失败';
  END IF;

  SELECT COUNT(*) INTO v_invalid_count
  FROM tmp_mall_shop_merchant_resolved resolved
  WHERE (SELECT COUNT(*) FROM system_user_role ur
         JOIN system_role role_row ON role_row.id=ur.role_id AND role_row.tenant_id=ur.tenant_id
         WHERE ur.user_id=resolved.admin_user_id AND ur.tenant_id=resolved.tenant_id
           AND ur.deleted=b'0' AND role_row.code='mall_shop_merchant'
           AND role_row.status=0 AND role_row.deleted=b'0')<>1
     OR EXISTS (
         SELECT 1 FROM system_user_role extra_ur
         JOIN system_role extra_role
           ON extra_role.id=extra_ur.role_id AND extra_role.tenant_id=extra_ur.tenant_id
          AND extra_role.status=0 AND extra_role.deleted=b'0'
         WHERE extra_ur.user_id=resolved.admin_user_id
           AND extra_ur.tenant_id=resolved.tenant_id
           AND extra_ur.deleted=b'0' AND extra_role.code<>'mall_shop_merchant')
     OR (SELECT COUNT(*) FROM product_enterprise_admin_member membership
         WHERE membership.admin_user_id=resolved.admin_user_id
           AND membership.enterprise_id=resolved.enterprise_id
           AND membership.tenant_id=resolved.tenant_id
           AND membership.scope_type=2 AND membership.status=0 AND membership.deleted=b'0')<>1
     OR (SELECT COUNT(*) FROM product_shop_admin_authorization shop_grant
         WHERE shop_grant.admin_user_id=resolved.admin_user_id
           AND shop_grant.enterprise_id=resolved.enterprise_id
           AND shop_grant.shop_id=resolved.shop_id
           AND shop_grant.tenant_id=resolved.tenant_id
           AND shop_grant.status=0 AND shop_grant.deleted=b'0')<>1
     OR EXISTS (
         SELECT 1 FROM product_enterprise_admin_member enterprise_scope
         WHERE enterprise_scope.admin_user_id=resolved.admin_user_id
           AND enterprise_scope.tenant_id=resolved.tenant_id
           AND enterprise_scope.scope_type=1
           AND enterprise_scope.status=0 AND enterprise_scope.deleted=b'0')
     OR EXISTS (
         SELECT 1 FROM product_enterprise_admin_member other_enterprise
         WHERE other_enterprise.admin_user_id=resolved.admin_user_id
           AND other_enterprise.tenant_id=resolved.tenant_id
           AND other_enterprise.status=0 AND other_enterprise.deleted=b'0'
           AND other_enterprise.enterprise_id<>resolved.enterprise_id)
     OR EXISTS (
         SELECT 1 FROM product_shop_admin_authorization other_shop
         WHERE other_shop.admin_user_id=resolved.admin_user_id
           AND other_shop.tenant_id=resolved.tenant_id
           AND other_shop.status=0 AND other_shop.deleted=b'0'
           AND other_shop.shop_id<>resolved.shop_id)
     OR EXISTS (
         SELECT 1 FROM product_enterprise_application application_row
         WHERE application_row.member_id=resolved.member_id
           AND application_row.enterprise_id=resolved.enterprise_id
           AND application_row.tenant_id=resolved.tenant_id
           AND application_row.admin_user_id=resolved.admin_user_id
           AND application_row.deleted=b'0');
  IF v_invalid_count>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='单店账号启用回滚：角色或单店最小权限校验失败';
  END IF;

  SELECT COUNT(*) INTO v_count
  FROM product_business_operation_audit audit_row
  JOIN tmp_mall_shop_merchant_resolved resolved
    ON resolved.tenant_id=audit_row.tenant_id AND resolved.migration_id=audit_row.resource_id
  WHERE audit_row.action='SHOP_MERCHANT_ACCOUNT_ACTIVATE'
    AND audit_row.resource_type='BUSINESS_ACCOUNT_MIGRATION'
    AND audit_row.result='SUCCESS' AND audit_row.deleted=b'0';
  IF v_count<>75 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='单店账号启用回滚：迁移审计记录不完整';
  END IF;

  COMMIT;

  SELECT 'SHOP_MERCHANT_ACTIVATION_OK' result,
         COUNT(*) activated_account_count,
         COUNT(DISTINCT tenant_id) tenant_count,
         SUM(member_id=316 AND tenant_id=1) comency_count
  FROM tmp_mall_shop_merchant_resolved;

  DROP TEMPORARY TABLE IF EXISTS tmp_mall_shop_merchant_resolved;
  DROP TEMPORARY TABLE IF EXISTS tmp_mall_shop_merchant_expected;
  DROP TEMPORARY TABLE IF EXISTS tmp_mall_shop_merchant_activation_input;
END$$
DELIMITER ;

CALL execute_mall_shop_merchant_activation();
DROP PROCEDURE IF EXISTS execute_mall_shop_merchant_activation;

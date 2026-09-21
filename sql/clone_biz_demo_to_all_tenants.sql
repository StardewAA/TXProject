-- 将租户 2010 的 MES 核心业务演示数据克隆到所有其他租户（查询演示用）
-- 克隆表：物料/客户/供应商/车间/工位/设备类型/设备/班组/生产工单/生产任务/报工/IPQC/物料库存
-- 外键保留原值（join 查询不受租户过滤，产品/客户名称可正常关联显示）；幂等 IGNORE
DROP PROCEDURE IF EXISTS clone_biz_to_tenants;
DELIMITER $$
CREATE PROCEDURE clone_biz_to_tenants()
BEGIN
  DECLARE tid BIGINT;
  DECLARE off_ BIGINT;
  DECLARE done INT DEFAULT 0;
  DECLARE cur CURSOR FOR SELECT id FROM system_tenant WHERE deleted = 0 AND id <> 2010;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;
  OPEN cur;
  loop_t: LOOP
    FETCH cur INTO tid;
    IF done THEN LEAVE loop_t; END IF;
    SET off_ = tid * 1000000;

    CREATE TEMPORARY TABLE tmp1 AS SELECT * FROM mes_md_item WHERE tenant_id = 2010 AND deleted = 0;
    UPDATE tmp1 SET id = id + off_, tenant_id = tid;
    INSERT IGNORE INTO mes_md_item SELECT * FROM tmp1;
    DROP TEMPORARY TABLE tmp1;

    CREATE TEMPORARY TABLE tmp2 AS SELECT * FROM mes_md_client WHERE tenant_id = 2010 AND deleted = 0;
    UPDATE tmp2 SET id = id + off_, tenant_id = tid;
    INSERT IGNORE INTO mes_md_client SELECT * FROM tmp2;
    DROP TEMPORARY TABLE tmp2;

    CREATE TEMPORARY TABLE tmp3 AS SELECT * FROM mes_md_vendor WHERE tenant_id = 2010 AND deleted = 0;
    UPDATE tmp3 SET id = id + off_, tenant_id = tid;
    INSERT IGNORE INTO mes_md_vendor SELECT * FROM tmp3;
    DROP TEMPORARY TABLE tmp3;

    CREATE TEMPORARY TABLE tmp4 AS SELECT * FROM mes_md_workshop WHERE tenant_id = 2010 AND deleted = 0;
    UPDATE tmp4 SET id = id + off_, tenant_id = tid;
    INSERT IGNORE INTO mes_md_workshop SELECT * FROM tmp4;
    DROP TEMPORARY TABLE tmp4;

    CREATE TEMPORARY TABLE tmp5 AS SELECT * FROM mes_md_workstation WHERE tenant_id = 2010 AND deleted = 0;
    UPDATE tmp5 SET id = id + off_, tenant_id = tid;
    INSERT IGNORE INTO mes_md_workstation SELECT * FROM tmp5;
    DROP TEMPORARY TABLE tmp5;

    CREATE TEMPORARY TABLE tmp6 AS SELECT * FROM mes_dv_machinery_type WHERE tenant_id = 2010 AND deleted = 0;
    UPDATE tmp6 SET id = id + off_, tenant_id = tid;
    INSERT IGNORE INTO mes_dv_machinery_type SELECT * FROM tmp6;
    DROP TEMPORARY TABLE tmp6;

    CREATE TEMPORARY TABLE tmp7 AS SELECT * FROM mes_dv_machinery WHERE tenant_id = 2010 AND deleted = 0;
    UPDATE tmp7 SET id = id + off_, tenant_id = tid;
    INSERT IGNORE INTO mes_dv_machinery SELECT * FROM tmp7;
    DROP TEMPORARY TABLE tmp7;

    CREATE TEMPORARY TABLE tmp8 AS SELECT * FROM mes_cal_team WHERE tenant_id = 2010 AND deleted = 0;
    UPDATE tmp8 SET id = id + off_, tenant_id = tid;
    INSERT IGNORE INTO mes_cal_team SELECT * FROM tmp8;
    DROP TEMPORARY TABLE tmp8;

    CREATE TEMPORARY TABLE tmp9 AS SELECT * FROM mes_pro_work_order WHERE tenant_id = 2010 AND deleted = 0;
    UPDATE tmp9 SET id = id + off_, tenant_id = tid;
    INSERT IGNORE INTO mes_pro_work_order SELECT * FROM tmp9;
    DROP TEMPORARY TABLE tmp9;

    CREATE TEMPORARY TABLE tmp10 AS SELECT * FROM mes_pro_task WHERE tenant_id = 2010 AND deleted = 0;
    UPDATE tmp10 SET id = id + off_, tenant_id = tid;
    INSERT IGNORE INTO mes_pro_task SELECT * FROM tmp10;
    DROP TEMPORARY TABLE tmp10;

    CREATE TEMPORARY TABLE tmp11 AS SELECT * FROM mes_pro_feedback WHERE tenant_id = 2010 AND deleted = 0;
    UPDATE tmp11 SET id = id + off_, tenant_id = tid;
    INSERT IGNORE INTO mes_pro_feedback SELECT * FROM tmp11;
    DROP TEMPORARY TABLE tmp11;

    CREATE TEMPORARY TABLE tmp12 AS SELECT * FROM mes_qc_ipqc WHERE tenant_id = 2010 AND deleted = 0;
    UPDATE tmp12 SET id = id + off_, tenant_id = tid;
    INSERT IGNORE INTO mes_qc_ipqc SELECT * FROM tmp12;
    DROP TEMPORARY TABLE tmp12;

    CREATE TEMPORARY TABLE tmp13 AS SELECT * FROM mes_wm_material_stock WHERE tenant_id = 2010 AND deleted = 0;
    UPDATE tmp13 SET id = id + off_, tenant_id = tid;
    INSERT IGNORE INTO mes_wm_material_stock SELECT * FROM tmp13;
    DROP TEMPORARY TABLE tmp13;

  END LOOP;
  CLOSE cur;
END$$
DELIMITER ;

CALL clone_biz_to_tenants();
DROP PROCEDURE IF EXISTS clone_biz_to_tenants;

-- 验证：各租户工单数
SELECT tenant_id, COUNT(*) AS work_orders FROM mes_pro_work_order WHERE deleted = 0 GROUP BY tenant_id ORDER BY tenant_id;

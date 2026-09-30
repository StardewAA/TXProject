-- ============================================
-- 客户/供应商统一编码治理脚本
-- 平台：ruoyi-vue-pro（txgy-module-mes / txgy-module-erp）
-- 背景：协同测试发现同一客户/供应商在 ERP、MES、WMS 三处编码不一致
-- 决策：客户/供应商统一编码以 ERP 为唯一权威源，格式 CLI/VEN + yyyyMMdd + 6 位流水
--       例：CLI20260917000001 / VEN20260917000001
-- 内容：
--   Part 1  MES 停用 MD_CLIENT_CODE / MD_VENDOR_CODE 两条编码规则（防止 MES 继续发号造成编号漂移）
--   Part 2  修正 MES MD_VENDOR_CODE 规则的名称错误（原为"车间编码规则"）
--   Part 3  ERP 存量客户/供应商编码迁移（ERP-C-xxx / ERP-V-xxx → 新格式）
-- 说明：脚本幂等，可重复执行
-- 执行环境：dev / prod 均适用，按 tenant_id 分别执行
-- 注意：MES 表结构 rule 主键 id 与 tenant_id 共同决定唯一性，务必带 tenant_id 条件
-- ============================================

-- --------------------------------------------
-- Part 1：MES 停用客户/供应商两条自动编码规则
-- --------------------------------------------
-- status：0=开启 1=停用（沿用 platform 编码规则表约定，MES 侧规则当前全为 0）
-- 停用后 MesMdAutoCodeRecordServiceImpl.generateAutoCode("MD_CLIENT_CODE") 仍可被显式调用，
-- 但业务侧不应再调用；ERP 侧改为自行发号并推送给 MES/WMS。
UPDATE mes_md_auto_code_rule
SET status = 1,
    remark = CONCAT(IFNULL(remark, ''), ' [20260917 停用：客户编码统一由 ERP 生成，格式 CLI+yyyyMMdd+6位流水]'),
    updater = 'system',
    update_time = NOW()
WHERE code = 'MD_CLIENT_CODE'
  AND deleted = b'0';

UPDATE mes_md_auto_code_rule
SET status = 1,
    remark = CONCAT(IFNULL(remark, ''), ' [20260917 停用：供应商编码统一由 ERP 生成，格式 VEN+yyyyMMdd+6位流水]'),
    updater = 'system',
    update_time = NOW()
WHERE code = 'MD_VENDOR_CODE'
  AND deleted = b'0';

-- --------------------------------------------
-- Part 2：修正 MD_VENDOR_CODE 规则名称
-- 现状：名称误写为"车间编码规则"（车间是 MD_WORKSHOP_CODE），易误导使用者
-- --------------------------------------------
UPDATE mes_md_auto_code_rule
SET name = '供应商编码',
    description = '供应商编码自动生成规则（已停用，改由 ERP 统一发号）',
    updater = 'system',
    update_time = NOW()
WHERE code = 'MD_VENDOR_CODE'
  AND name <> '供应商编码'
  AND deleted = b'0';

-- --------------------------------------------
-- Part 3：ERP 存量客户编码迁移
-- --------------------------------------------
-- 迁移规则：partner_code 形如 ERP-C-{nnn} → CLI{yyyyMMdd}{000001 起顺序}
--
-- 重要：partner_code 创建后不可修改（CUSTOMER_PARTNER_CODE_IMMUTABLE），
--       页面无法修改，只能走 SQL；且该编码已同步至 WMS wms_merchant.code，
--       迁移后必须同步更新 WMS，否则会造成新的不一致。
--
-- 执行前请先备份：
--   CREATE TABLE erp_customer_bak_20260917 AS SELECT * FROM erp_customer;
--   CREATE TABLE wms_merchant_bak_20260917 AS SELECT * FROM wms_merchant;

-- 3.1 预览：待迁移的 ERP 存量客户（先执行确认范围）
SELECT id, partner_code, name, sync_version, tenant_id
FROM erp_customer
WHERE partner_code LIKE 'ERP-%'
ORDER BY id;

-- 3.2 预览：对应的 WMS 往来单位（code 即 ERP partner_code）
SELECT id, code, name, type, source_version, tenant_id
FROM wms_merchant
WHERE code LIKE 'ERP-%'
ORDER BY id;

-- 3.3 建立映射临时表：迁移前 code → 迁移后新 code
-- 先固化映射，再分别更新 ERP 与 WMS，保证两侧编码改动一致、可回滚
DROP TEMPORARY TABLE IF EXISTS tmp_partner_code_map;
CREATE TEMPORARY TABLE tmp_partner_code_map (
    customer_id bigint      NOT NULL PRIMARY KEY,
    old_code    varchar(64) NOT NULL,
    new_code    varchar(64) NOT NULL,
    KEY idx_old (old_code)
) ENGINE = InnoDB;

INSERT INTO tmp_partner_code_map (customer_id, old_code, new_code)
SELECT id,
       partner_code,
       CONCAT('CLI',
              DATE_FORMAT(IFNULL(create_time, NOW()), '%Y%m%d'),
              LPAD(ROW_NUMBER() OVER (
                       PARTITION BY DATE(IFNULL(create_time, NOW()))
                       ORDER BY id), 6, '0'))
FROM erp_customer
WHERE partner_code LIKE 'ERP-%'
  AND deleted = b'0';

-- 3.3.1 预览映射结果（执行 3.4/3.5 前确认无误）
SELECT * FROM tmp_partner_code_map ORDER BY customer_id;

-- 3.4 更新 ERP 客户编码
UPDATE erp_customer c
JOIN tmp_partner_code_map m ON m.customer_id = c.id
SET c.partner_code  = m.new_code,
    c.sync_version  = IFNULL(c.sync_version, 1) + 1,
    c.update_time   = NOW();

-- 3.5 同步更新 WMS 往来单位编码（保持与 ERP 一致）
-- 说明：wms_merchant.code 存的就是 ERP 的 partner_code，必须同步改，否则产生新的不一致。
--       若 WMS 存量数据可由 ERP 重新推送覆盖，也可改为迁移后触发一次全量推送（同样可行）。
UPDATE wms_merchant w
JOIN tmp_partner_code_map m ON m.old_code = w.code
SET w.code        = m.new_code,
    w.update_time = NOW();

-- 3.6 若 MES 也存在同源客户数据（mes_md_client.code 存过 ERP 编码的场景），一并同步
-- 说明：MES 的 mes_md_client.code 当前为 MES 自行发号，与 ERP 编码并不一致，
--       因此本段默认不执行；待 ERP→MES 客户主数据推送打通后，再由推送逻辑统一覆盖。
-- UPDATE mes_md_client mc
-- JOIN tmp_partner_code_map m ON m.old_code = mc.code
-- SET mc.code = m.new_code, mc.update_time = NOW();

-- 3.5 迁移后校验：新旧编码不得残留、不得重复
SELECT 'erp_customer 残留旧格式' AS chk, COUNT(*) AS cnt
FROM erp_customer WHERE partner_code LIKE 'ERP-%' AND deleted = b'0'
UNION ALL
SELECT 'erp_customer 编码重复', COUNT(*) FROM (
    SELECT partner_code FROM erp_customer WHERE deleted = b'0'
    GROUP BY partner_code HAVING COUNT(*) > 1
) d
UNION ALL
SELECT 'wms_merchant 残留旧格式', COUNT(*)
FROM wms_merchant WHERE code LIKE 'ERP-%';

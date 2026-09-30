-- 治污设施运行管控（#25）：设施台账 + 换炭→HW49 + 停运申报审批 + 同开同停
-- 设计文档《MES环保模块质优化设计-最终版-2026-09-09》§6.3（治理设施与危废联动）、§3.3（产线→设施）。
--
-- 表名：设计稿总表（§10）把它写作 mes_dg_facility。本仓从一期起实际落的都是 mes_set_* 前缀
--   （mes_set_emission_outlet / mes_set_hazardous_waste / mes_set_chemical_profile …），
--   库里一张 mes_dg_* 都没有。**跟本仓已有的走**，不为了对齐设计稿而开第二种前缀，
--   否则同一个子系统两张命名并存，后来人先得猜哪个是"真的"。
--
-- 业务编号唯一键：按 drop-code-unique-20260910.sql 口径，只给普通索引，不给 UNIQUE。
--   查重放 Service 层，撞号返回业务错误码 1040831001。
--
-- 停运申报为什么放在本表而不是另开一张申报表：
--   「一台设施当前有没有停运」是一个**状态**，不是一堆历史行；读的时候永远只要最新那条。
--   而审批是要留痕的合规动作 —— 留痕复用 mes_set_sign_record（biz_type=FACILITY_SHUTDOWN），
--   白拿一份历史，不必为此再建一张表。

CREATE TABLE IF NOT EXISTS `mes_set_treatment_facility` (
  `id`                     bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `facility_no`            varchar(64)   NOT NULL COMMENT '设施编号',
  `facility_name`          varchar(128)  NOT NULL COMMENT '设施名称',
  `facility_type`          varchar(32)   NOT NULL COMMENT '设施类型：ACTIVATED_CARBON 活性炭吸附 / CATALYTIC_COMBUSTION 催化燃烧 / BAG_FILTER 布袋除尘',
  `outlet_code`            varchar(64)   DEFAULT NULL COMMENT '关联排放口编号（mes_set_emission_outlet.outlet_code）',
  `line_code`              varchar(64)   DEFAULT NULL COMMENT '所属产线/工序（§3.3：喷涂 / 抛丸打磨）',
  `run_status`             varchar(16)   NOT NULL DEFAULT 'RUNNING' COMMENT '运行状态：RUNNING 运行 / STOPPED 停运',
  `design_air_volume`      decimal(18,2) DEFAULT NULL COMMENT '设计风量 m³/h',
  `consumable_name`        varchar(64)   DEFAULT NULL COMMENT '耗材名称（如 活性炭）',
  `replace_cycle_days`     int           DEFAULT NULL COMMENT '更换周期(天)',
  `last_replace_date`      date          DEFAULT NULL COMMENT '上次更换日期',
  `next_replace_date`      date          DEFAULT NULL COMMENT '下次更换日期（=上次+周期，用于到期预警）',
  `shutdown_status`        varchar(16)   NOT NULL DEFAULT 'NONE' COMMENT '停运申报状态：NONE 无 / PENDING 待审批 / APPROVED 已批准 / REJECTED 已驳回',
  `shutdown_reason`        varchar(255)  DEFAULT NULL COMMENT '停运事由',
  `shutdown_plan_start`    datetime      DEFAULT NULL COMMENT '计划停运开始',
  `shutdown_plan_end`      datetime      DEFAULT NULL COMMENT '计划停运结束',
  `shutdown_declared_at`   datetime      DEFAULT NULL COMMENT '申报时间',
  `shutdown_approver`      varchar(64)   DEFAULT NULL COMMENT '审批人',
  `shutdown_approved_at`   datetime      DEFAULT NULL COMMENT '审批时间',
  `status`                 varchar(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '档案状态：ENABLED/DISABLED',
  `remark`                 varchar(500)  DEFAULT NULL COMMENT '备注',
  `creator`                varchar(64)   DEFAULT '' COMMENT '创建者',
  `create_time`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`                varchar(64)   DEFAULT '' COMMENT '更新者',
  `update_time`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`              bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_treatment_facility_no` (`facility_no`),
  KEY `idx_treatment_facility_type` (`facility_type`),
  KEY `idx_treatment_facility_outlet` (`outlet_code`),
  KEY `idx_treatment_facility_next_replace` (`next_replace_date`),
  KEY `idx_treatment_facility_run_status` (`run_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='治污设施台账（换炭/停运申报/同开同停）';

-- 校验
SELECT '设施表' k, COUNT(*) v FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'mes_set_treatment_facility'
UNION ALL SELECT '索引数', COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'mes_set_treatment_facility'
UNION ALL SELECT '含唯一键?', COUNT(DISTINCT CASE WHEN non_unique = 0 THEN index_name END)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'mes_set_treatment_facility';

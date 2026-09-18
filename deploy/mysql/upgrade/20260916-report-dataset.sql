-- =====================================================================
-- 2026-09-16 报表体系 P0-1：受控数据集治理
-- 背景：GoView /get-by-sql 允许前端直接传 SQL 到主库执行（无白名单、无行级权限、
--       无行数上限、无审计），报表模板建得越多风险越大。本迁移建立「数据集」层：
--       报表只能按数据集编码 + 参数取数，租户条件由服务端强制注入。
-- 内容：
--   1. report_dataset           报表数据集（白名单登记）
--   2. report_dataset_execute_log 数据集执行审计日志
--   3. 菜单与权限（数据集管理 6800~6805、自定义 SQL 高危权限 6806）
--   4. 只读数据库账号 report_ro（报表查询专用，仅 SELECT）
--   5. 平台级种子数据集（MES 域：报工明细台账；其余 MES 报表数据集见 20260916b 脚本）
-- 说明：幂等，可重复执行；菜单使用 6800 段（6800~6850 已确认空闲）。
-- =====================================================================

SET NAMES utf8mb4;

START TRANSACTION;

-- ---------------------------------------------------------------------
-- 1. 报表数据集表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `report_dataset`
(
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
    `code`              varchar(64)  NOT NULL COMMENT '数据集编码（报表引用标识，租户内唯一）',
    `name`              varchar(128) NOT NULL COMMENT '数据集名称',
    `category`          varchar(64)  NOT NULL DEFAULT '' COMMENT '分类（域：MES/WMS/ERP/CRM 等）',
    `sql_text`          text         NOT NULL COMMENT 'SQL 语句（必须 SELECT/WITH 开头；参数用 ${name} 占位）',
    `param_definitions` varchar(2000) NOT NULL DEFAULT '' COMMENT '参数定义 JSON 数组：[{name,label,type,required,defaultValue}]',
    `tenant_column`     varchar(64)  NOT NULL DEFAULT '' COMMENT '租户过滤列名；填写后由服务端强制追加等值条件',
    `max_rows`          int          NOT NULL DEFAULT 5000 COMMENT '最大返回行数，超过抛错',
    `timeout_seconds`   int          NOT NULL DEFAULT 30 COMMENT '查询超时（秒）',
    `status`            tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0 开启、1 关闭。枚举 CommonStatusEnum',
    `owner`             varchar(64)  NOT NULL DEFAULT '' COMMENT '数据集责任人',
    `remark`            varchar(255) NOT NULL DEFAULT '' COMMENT '备注',
    `creator`           varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`           varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`         bigint       NOT NULL DEFAULT 0 COMMENT '租户编号；0 表示平台级数据集（所有租户只读可见）',
    PRIMARY KEY (`id`),
    KEY `idx_report_dataset_code` (`code`, `tenant_id`, `deleted`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='报表数据集';

-- ---------------------------------------------------------------------
-- 2. 数据集执行审计日志
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `report_dataset_execute_log`
(
    `id`            bigint        NOT NULL AUTO_INCREMENT COMMENT '编号',
    `dataset_id`    bigint        NOT NULL DEFAULT 0 COMMENT '数据集编号；0 表示未登记的原始 SQL',
    `dataset_code`  varchar(64)   NOT NULL DEFAULT '' COMMENT '数据集编码',
    `source`        varchar(32)   NOT NULL DEFAULT 'dataset' COMMENT '来源：dataset 数据集、raw-sql 大屏原始 SQL',
    `params`        varchar(2000) NOT NULL DEFAULT '' COMMENT '查询参数 JSON',
    `row_count`     int           NOT NULL DEFAULT 0 COMMENT '返回行数',
    `duration_ms`   bigint        NOT NULL DEFAULT 0 COMMENT '耗时（毫秒）',
    `success`       bit(1)        NOT NULL DEFAULT b'1' COMMENT '是否成功',
    `error_message` varchar(500)  NOT NULL DEFAULT '' COMMENT '失败原因',
    `user_id`       bigint                 DEFAULT NULL COMMENT '执行人编号',
    `creator`       varchar(64)            DEFAULT '' COMMENT '创建者',
    `create_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`       varchar(64)            DEFAULT '' COMMENT '更新者',
    `update_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`     bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_report_dataset_log_code` (`dataset_code`, `create_time`),
    KEY `idx_report_dataset_log_user` (`user_id`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='报表数据集执行日志';

-- ---------------------------------------------------------------------
-- 3. 菜单与权限
--    6800 数据集管理（挂在 1281 报表管理下）
--    6801~6805 权限按钮
--    6806 高危权限：大屏使用自定义 SQL（默认不授权给任何角色）
-- ---------------------------------------------------------------------
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (6800, '数据集管理', 'report:dataset:query', 2, 10, 1281, 'dataset', 'lucide:database', 'report/dataset/index', 'ReportDataset', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`),
                        `type` = VALUES(`type`), `parent_id` = VALUES(`parent_id`), `path` = VALUES(`path`),
                        `component` = VALUES(`component`), `component_name` = VALUES(`component_name`),
                        `updater` = 'admin', `update_time` = NOW(), `deleted` = b'0';

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (6801, '查询数据集', 'report:dataset:query', 3, 1, 6800, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
       (6802, '新增数据集', 'report:dataset:create', 3, 2, 6800, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
       (6803, '修改数据集', 'report:dataset:update', 3, 3, 6800, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
       (6804, '删除数据集', 'report:dataset:delete', 3, 4, 6800, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
       (6805, '执行数据集', 'report:dataset:execute', 3, 5, 6800, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
       (6806, '大屏自定义 SQL', 'report:dataset:raw-sql', 3, 9, 2153, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`),
                        `type` = VALUES(`type`), `parent_id` = VALUES(`parent_id`), `sort` = VALUES(`sort`),
                        `updater` = 'admin', `update_time` = NOW(), `deleted` = b'0';

-- 自愈按钮 type，避免历史误建为菜单
UPDATE `system_menu`
SET `type` = 3, `updater` = 'admin', `update_time` = NOW()
WHERE `id` BETWEEN 6801 AND 6806 AND `type` <> 3;

-- ---------------------------------------------------------------------
-- 4. 只读数据库账号（报表查询专用）
--    注意：密码请在部署环境按实际情况替换，不要沿用示例值。
--    该账号仅有 SELECT 权限，报表连接即使被注入也无法写入。
-- ---------------------------------------------------------------------
CREATE USER IF NOT EXISTS 'report_ro'@'%' IDENTIFIED BY 'ChangeMe_ReportRo_2026';
GRANT SELECT ON `ruoyi-vue-pro`.* TO 'report_ro'@'%';
FLUSH PRIVILEGES;

-- ---------------------------------------------------------------------
-- 5. 平台级种子数据集（tenant_id = 0，所有租户只读可见）
-- ---------------------------------------------------------------------
INSERT INTO `report_dataset`
(`id`, `code`, `name`, `category`, `sql_text`, `param_definitions`, `tenant_column`, `max_rows`, `timeout_seconds`,
 `status`, `owner`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES (1, 'mes_feedback_detail', '生产报工明细台账', 'MES',
        'SELECT f.tenant_id AS tenant_id, f.code AS feedback_code, w.code AS work_order_code, p.name AS process_name, ws.name AS workstation_name, i.code AS item_code, i.name AS item_name, f.feedback_quantity, f.qualified_quantity, f.unqualified_quantity, DATE_FORMAT(f.feedback_time, ''%Y-%m-%d %H:%i:%s'') AS feedback_time, f.status FROM mes_pro_feedback f LEFT JOIN mes_pro_work_order w ON w.id = f.work_order_id LEFT JOIN mes_pro_process p ON p.id = f.process_id LEFT JOIN mes_md_workstation ws ON ws.id = f.workstation_id LEFT JOIN mes_md_item i ON i.id = f.item_id WHERE f.deleted = 0 AND f.status IN (2, 3, 4) AND f.feedback_time >= ${startDate} AND f.feedback_time < DATE_ADD(${endDate}, INTERVAL 1 DAY)',
        '[{"name":"startDate","label":"开始日期","type":"date","required":false,"defaultValue":"2000-01-01"},{"name":"endDate","label":"结束日期","type":"date","required":false,"defaultValue":"2099-12-31"}]',
        'tenant_id', 5000, 30, 0, '刘洋', '报工台账报表数据源；tenant_id 必须出现在 SELECT 列表中，供服务端强制租户过滤', 'admin', NOW(), 'admin', NOW(), b'0', 0)
ON DUPLICATE KEY UPDATE `code` = VALUES(`code`), `name` = VALUES(`name`), `category` = VALUES(`category`),
                        `sql_text` = VALUES(`sql_text`),
                        `param_definitions` = VALUES(`param_definitions`), `tenant_column` = VALUES(`tenant_column`),
                        `max_rows` = VALUES(`max_rows`), `timeout_seconds` = VALUES(`timeout_seconds`),
                        `status` = VALUES(`status`), `owner` = VALUES(`owner`), `remark` = VALUES(`remark`),
                        `updater` = 'admin', `update_time` = NOW();

COMMIT;

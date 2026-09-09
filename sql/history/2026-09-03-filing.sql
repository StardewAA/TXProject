-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;

-- =====================================================================
-- 备案域（filing）建表脚本 —— txgy-module-bidding 小模块
-- 依据：《招投标模块开发规范》第五章；接口口径 05 §2.1/§3.1 BID-ENT-REG-01、§3.2 BID-SUP-FILE-01
-- 说明：备案提交幂等键=备案类型(filing_type)+项目编号(project_code)；
--       出参受理号由服务端生成（bidding_filing.receipt_no，唯一）；
--       状态机 PENDING(待监管受理) → ACCEPTED/REJECTED（监管回推）。
-- =====================================================================
CREATE TABLE IF NOT EXISTS `bidding_filing` (
    `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `filing_type` varchar(32) NOT NULL COMMENT '备案类型（幂等键①）',
    `project_code` varchar(64) NOT NULL COMMENT '项目编号（幂等键②）',
    `tender_project_code` varchar(64) DEFAULT NULL COMMENT '招标项目编号',
    `unified_deal_code` varchar(64) DEFAULT NULL COMMENT '统一交易标识码',
    `tender_mode` varchar(32) DEFAULT NULL COMMENT '招标方式（代码）',
    `org_form` varchar(32) DEFAULT NULL COMMENT '组织形式（代码）',
    `supervise_dept_code` varchar(64) DEFAULT NULL COMMENT '监督部门代码',
    `supervise_dept_name` varchar(128) DEFAULT NULL COMMENT '监督部门名称',
    `submit_time` datetime DEFAULT NULL COMMENT '提交时间',
    `data_timestamp` datetime DEFAULT NULL COMMENT 'data_timestamp（05 交换口径，幂等防重）',
    `extra_json` json DEFAULT NULL COMMENT '扩展/附件快照（如附件 file_id 列表）',
    `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/REJECTED（受理状态由监管回推）',
    `receipt_no` varchar(64) DEFAULT NULL COMMENT '备案受理号（服务端生成，唯一）',
    `receipt_time` datetime DEFAULT NULL COMMENT '受理（回推）时间',
    `opinion` varchar(512) DEFAULT NULL COMMENT '监管回推意见',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_filing_power` (`filing_type`, `project_code`, `deleted`, `tenant_id`),
    UNIQUE KEY `uk_filing_receipt` (`receipt_no`),
    KEY `idx_filing_status_time` (`status`, `submit_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '备案受理记录（T 轨）';

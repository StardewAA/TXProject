-- =====================================================================
-- MES 安全环保检测（SET）模块 - 追溯链 / 签字 / 称重 三表 DDL（一期B 底座）
-- 表：mes_set_trace_chain 追溯链节点、mes_set_sign_record 签字记录、mes_set_weigh_record 称重记录
-- 依据：《MES环保模块质优化设计-团队终版-2026-09-09.md》§10.1；方案 keen-launching-donut.md
-- 语义：三表均为系统/流程写入的审计数据（前端只读）；weigh 无电子秤源，本期只落表只读，AUTO 采集留待后续
-- 说明：
--   1. 公共 7 列：creator/create_time/updater/update_time/deleted/tenant_id
--   2. 枚举用 VARCHAR 代码存储（与 SET 其余表一致）
--   3. sign/weigh 与 trace 以 (biz_type,biz_no) 关联；trace 另以 trace_code(现=判定 recordNo，后续=批次/容器码)
-- 执行：mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < pollution-trace-20260909-ddl.sql
-- =====================================================================

-- 1) 追溯链节点（一条业务对象一条链；复核收口、台账每步处置流转各写一节点）
CREATE TABLE IF NOT EXISTS `mes_set_trace_chain` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `trace_code` varchar(64) NOT NULL COMMENT '追溯对象码（现=判定 recordNo(PC-...)；后续=批次码/容器码）',
  `trace_type` varchar(32) NOT NULL COMMENT '对象类型：CHECK(污染判定)/LEDGER(台账处置)；预留 BATCH/CONTAINER',
  `parent_code` varchar(128) DEFAULT NULL COMMENT '上游关联码（来源单号/批次码）',
  `biz_type` varchar(32) DEFAULT NULL COMMENT '业务关联类型（与 sign/weigh 共用：CHECK/LEDGER）',
  `biz_no` varchar(64) DEFAULT NULL COMMENT '业务关联单号（判定 recordNo / 台账来源判定 recordNo）',
  `node_stage` varchar(32) DEFAULT NULL COMMENT '环节（复用 PURCHASE_INBOUND/MATERIAL_ISSUE/WASTE_INTERMEDIATE/FINISHED_PRODUCT；处置流转记 DISPOSAL）',
  `node_action` varchar(128) NOT NULL COMMENT '节点中文动作（时间轴主展示）',
  `batch_status` varchar(32) DEFAULT NULL COMMENT '节点时刻对象状态（判定→复核终值 CLEAN/POLLUTED/UNCERTAIN；处置→台账状态码）',
  `operator_name` varchar(64) DEFAULT NULL COMMENT '操作人（复核人/流转人）',
  `node_time` datetime DEFAULT NULL COMMENT '节点时间',
  `extra` varchar(255) DEFAULT NULL COMMENT 'JSON 备注（去向/处置方式等）',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_trace_code` (`trace_code`),
  KEY `idx_biz` (`biz_type`, `biz_no`),
  KEY `idx_node_time` (`node_time`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='安全环保检测-追溯链节点';

-- 2) 签字记录（只增不改删；复核签字等流程节点留档）
CREATE TABLE IF NOT EXISTS `mes_set_sign_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `biz_type` varchar(32) NOT NULL COMMENT '业务关联类型：CHECK/LEDGER',
  `biz_no` varchar(64) NOT NULL COMMENT '业务关联单号（判定 recordNo 等）',
  `sign_role` varchar(32) NOT NULL COMMENT '签字角色：REVIEWER(复核)/OPERATOR(录入)/APPROVER(审批)；预留',
  `sign_user` varchar(64) DEFAULT NULL COMMENT '签字人',
  `sign_time` datetime DEFAULT NULL COMMENT '签字时间',
  `location` varchar(128) DEFAULT NULL COMMENT '签字地点/去向',
  `opinion` varchar(255) DEFAULT NULL COMMENT '签署意见',
  `sign_img` varchar(255) DEFAULT NULL COMMENT '手写签字图片（未接 infra 文件服务前为空，预留）',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_biz` (`biz_type`, `biz_no`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='安全环保检测-签字记录';

-- 3) 称重记录（本期只读；AUTO 待电子秤直采接入，MANUAL 待写入口）
CREATE TABLE IF NOT EXISTS `mes_set_weigh_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `weigh_type` varchar(32) NOT NULL COMMENT '称重类型：PRODUCE(产废)/FACTORY(出厂)；预留',
  `biz_type` varchar(32) DEFAULT NULL COMMENT '业务关联类型：CHECK/LEDGER',
  `biz_no` varchar(64) DEFAULT NULL COMMENT '业务关联单号',
  `container_code` varchar(64) DEFAULT NULL COMMENT '桶/容器编码',
  `batch_code` varchar(64) DEFAULT NULL COMMENT '批次码',
  `device_code` varchar(64) DEFAULT NULL COMMENT '电子秤设备编码',
  `gross_weight` decimal(12,3) DEFAULT NULL COMMENT '毛重(kg)',
  `tare_weight` decimal(12,3) DEFAULT NULL COMMENT '皮重(kg)',
  `net_weight` decimal(12,3) DEFAULT NULL COMMENT '净重(kg)',
  `data_source` varchar(16) NOT NULL DEFAULT 'MANUAL' COMMENT '数据来源：AUTO(电子秤直采)/MANUAL(人工录入)',
  `plate_no` varchar(32) DEFAULT NULL COMMENT '车牌号',
  `photo` varchar(255) DEFAULT NULL COMMENT '现场照片',
  `reason` varchar(255) DEFAULT NULL COMMENT '称重事由/MANUAL 授权理由',
  `operator_name` varchar(64) DEFAULT NULL COMMENT '操作人',
  `weigh_time` datetime DEFAULT NULL COMMENT '称重时间',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_biz` (`biz_type`, `biz_no`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='安全环保检测-称重记录';

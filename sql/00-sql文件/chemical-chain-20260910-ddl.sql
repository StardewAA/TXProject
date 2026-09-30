-- 危化品链（#26 后半）：危化品档案 + 分级储存/禁配 + 储量上限 + 五双
-- 设计文档《MES环保模块质优化设计-最终版-2026-09-09》§5 主线A。
--
-- 为什么要新建表而不是改造 mes_set_chemical_safety：
--   既有那张表是「危化品安全**检查**记录」（record_no/plan_id/label_ok/msds_ok/storage_ok/
--   separation_ok/result/problem_desc/inspector/inspect_time），是**一次巡检**的快照；
--   设计文档要的是「危化品**档案**」（MSDS/禁配/储量），是**一类物料**的常量属性。
--   一个是流水、一个是主数据，语义不同，硬塞进一张表会让两边都别扭。两者用 chemical_code 关联。
--
-- 业务编号唯一键：按 drop-code-unique-20260910.sql 定下的口径，只给普通索引，不给 UNIQUE。
--   理由同 §13.4：唯一键只含业务列时，「建-删-再建同一编号」会撞逻辑删除行 → 500。
--   查重放在 Service 层，撞号返回业务错误码。

CREATE TABLE IF NOT EXISTS `mes_set_chemical_profile` (
  `id`                    bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `profile_no`            varchar(64)  NOT NULL COMMENT '危化品档案编号',
  `chemical_code`         varchar(64)  DEFAULT NULL COMMENT '危化品代码（与 mes_set_chemical_safety.chemical_code 对齐）',
  `chemical_name`         varchar(200) NOT NULL COMMENT '危化品名称',
  `cas_no`                varchar(64)  DEFAULT NULL COMMENT 'CAS 号',
  `compat_group`          varchar(32)  DEFAULT NULL COMMENT '相容组（禁配判定用）：ISOCYANATE/POLYOL/THINNER/EPOXY/WATER/ALCOHOL/AMINE 等',
  `hazard_class`          varchar(64)  DEFAULT NULL COMMENT '危险类别（如 易燃液体/急性毒性/氧化性物质）',
  `storage_zone`          varchar(32)  DEFAULT 'GENERAL' COMMENT '储存专区：GENERAL 一般区 / EXPLOSION_PROOF 防爆区 / ISOLATION 隔离区 / SPECIAL 专库',
  `storage_location`      varchar(200) DEFAULT NULL COMMENT '具体库位描述',
  `storage_limit`         decimal(18,4) DEFAULT NULL COMMENT '储量上限（同库位该物料总量，NULL=不限量）',
  `storage_unit`          varchar(16)  DEFAULT '吨' COMMENT '储量单位',
  `stock_quantity`        decimal(18,4) NOT NULL DEFAULT 0 COMMENT '当前存量（stock-in 累加，用于超量预警）',
  `incompatible_groups`   varchar(500) DEFAULT NULL COMMENT '额外禁配相容组，逗号分隔（人工补充用，矩阵里已锚定的不必重复填）',
  `explosion_proof`       tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否必须存放于防爆区',
  `msds_url`              varchar(500) DEFAULT NULL COMMENT 'MSDS 文件 URL（前端直传，后端只存 URL）',
  `msds_expire_date`      date         DEFAULT NULL COMMENT 'MSDS 版本有效期（到期预警）',
  `expire_manage`         tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否纳入效期管理',
  `shelf_life_days`       int          DEFAULT NULL COMMENT '保质期天数（配合批次到期日；实际到期冻结在 mes_wm_batch.expireDate 侧）',
  `emergency_measure`     varchar(1000) DEFAULT NULL COMMENT '应急措施（泄漏/接触处置）',
  `status`                varchar(16)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED 启用 / DISABLED 停用',
  `remark`                varchar(500) DEFAULT NULL COMMENT '备注',
  `creator`               varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`               varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`               bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`             bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_chemical_profile_no` (`profile_no`),
  KEY `idx_chemical_profile_code` (`chemical_code`),
  KEY `idx_chemical_profile_location` (`storage_location`(100)),
  KEY `idx_chemical_profile_group` (`compat_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='危化品档案（MSDS/禁配/储量/分级储存）';

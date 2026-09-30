-- =============================================================
-- HRM 计件计量：工程量 × 计件单价 自动算「计件工资」
-- 背景：防腐保温一线以计件为主（按平米/吨/延米计），一个工人一个月常有多道工序、多个单价。
--      原实现里计件工资只能人工填金额（代码注释「预留，由 MES 计量导入」）。
--      现增加「计件计量明细」：HR（或后续 MES 报工）按月按员工录入 工程量 × 单价，
--      核算工资表时自动汇总到「计件工资」薪资项，并参与应发/应税/个税。
-- 幂等：可重复执行
-- =============================================================

-- ---------- 1) 计件计量明细表 ----------
CREATE TABLE IF NOT EXISTS `hrm_salary_piece_record` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细编号',
    `employee_id` bigint NOT NULL COMMENT '员工编号',
    `year` int NOT NULL COMMENT '年',
    `month` int NOT NULL COMMENT '月',
    `item_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工程量/工序名称，如：管道保温-岩棉',
    `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '单位，如：㎡ / 吨 / 延米 / 件',
    `quantity` decimal(14,3) NOT NULL DEFAULT 0.000 COMMENT '完成工程量',
    `unit_price` decimal(14,4) NOT NULL DEFAULT 0.0000 COMMENT '计件单价（元）',
    `amount` decimal(14,2) NOT NULL DEFAULT 0.00 COMMENT '计件金额 = 工程量 × 单价（后端计算）',
    `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
    `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_hrm_salary_piece_emp_month` (`tenant_id`, `year`, `month`, `employee_id`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'HRM 计件计量明细表';

-- ---------- 2) 菜单与权限（挂在「薪资管理」1533 下） ----------
INSERT INTO `ruoyi-vue-pro`.`system_menu`
    (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
     `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`,
     `updater`, `update_time`, `deleted`)
SELECT 6997, '计件计量', 'hrm:salary:piece-record:query', 2, 12, 1533, 'piece-record', 'ep:coin',
       'hrm/salary/piece-record/index', 'HrmSalaryPieceRecord', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `ruoyi-vue-pro`.`system_menu` WHERE `permission` = 'hrm:salary:piece-record:query'
);

INSERT INTO `ruoyi-vue-pro`.`system_menu`
    (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
     `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`,
     `updater`, `update_time`, `deleted`)
SELECT 6998, '计件计量维护', 'hrm:salary:piece-record:update', 3, 1, 6997, '', '#', '',
       '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `ruoyi-vue-pro`.`system_menu` WHERE `permission` = 'hrm:salary:piece-record:update'
);

-- 授予原本拥有「薪资档案维护」权限的角色，保证升级后无人丢权限
INSERT INTO `ruoyi-vue-pro`.`system_role_menu`
    (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT src.role_id, target.id, '1', NOW(), '1', NOW(), b'0', src.tenant_id
FROM (
    SELECT rm.role_id, r.tenant_id
    FROM `ruoyi-vue-pro`.`system_role_menu` rm
    JOIN `ruoyi-vue-pro`.`system_role` r ON r.id = rm.role_id
    JOIN `ruoyi-vue-pro`.`system_menu` m ON m.id = rm.menu_id
    WHERE m.permission = 'hrm:salary:employee-info:query' AND rm.deleted = b'0'
    GROUP BY rm.role_id, r.tenant_id
) src
JOIN `ruoyi-vue-pro`.`system_menu` target
  ON target.permission IN ('hrm:salary:piece-record:query', 'hrm:salary:piece-record:update')
WHERE NOT EXISTS (
    SELECT 1 FROM `ruoyi-vue-pro`.`system_role_menu` exist
    WHERE exist.role_id = src.role_id AND exist.menu_id = target.id
);

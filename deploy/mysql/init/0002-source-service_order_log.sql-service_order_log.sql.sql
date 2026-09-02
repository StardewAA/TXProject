/*
 Navicat Premium Dump SQL

 Source Server         : hyperv_ubuntu
 Source Server Type    : MySQL
 Source Server Version : 80039 (8.0.39-0ubuntu0.22.04.1)
 Source Host           : 192.168.3.27:3306
 Source Schema         : ruoyi-vue-pro

 Target Server Type    : MySQL
 Target Server Version : 80039 (8.0.39-0ubuntu0.22.04.1)
 File Encoding         : 65001

 Date: 04/09/2024 00:09:54
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for service_order_log
-- ----------------------------
DROP TABLE IF EXISTS `service_order_log`;
CREATE TABLE `service_order_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '工单变更log表id',
  `order_id` bigint NOT NULL COMMENT '工单id',
  `status` tinyint NOT NULL COMMENT '工单状态',
  `staff` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '负责人',
  `log` json NULL COMMENT '变更的信息json字符串',
  `creater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人，实际不用',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间，实际不用',
  `tenant_id` bigint NOT NULL COMMENT '租户id',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `order_id`(`order_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工单变更log' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;

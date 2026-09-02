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

 Date: 04/09/2024 00:10:07
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for service_order
-- ----------------------------
DROP TABLE IF EXISTS `service_order`;
CREATE TABLE `service_order`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '工单编号',
  `servicetype` bigint NOT NULL COMMENT '维修、租赁、服务，服务类型',
  `productcategory` bigint NOT NULL COMMENT '商品分类id',
  `product` bigint NULL DEFAULT NULL COMMENT '商品id',
  `faulttype` bigint NULL DEFAULT NULL COMMENT '故障类别',
  `linkman` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人',
  `mobile` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '联系电话',
  `memberid` bigint NULL DEFAULT NULL COMMENT '会员id',
  `onsite` tinyint NULL DEFAULT NULL COMMENT '是否上门，0=false，1=true',
  `orderdate` datetime NULL DEFAULT NULL COMMENT '上门日期',
  `homeaddress` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '上门地址',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '描述(用户或后台录入)',
  `status` tinyint NOT NULL COMMENT '工单状态',
  `memo` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '后台备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL COMMENT '租户id',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `serviceid`(`servicetype` ASC) USING BTREE,
  INDEX `productcate`(`productcategory` ASC) USING BTREE,
  INDEX `product`(`product` ASC) USING BTREE,
  INDEX `member`(`memberid` ASC) USING BTREE,
  INDEX `fault`(`faulttype` ASC) USING BTREE,
  INDEX `id`(`id` ASC) USING BTREE,
  INDEX `mobile`(`mobile` ASC) USING BTREE,
  INDEX `linkman`(`linkman` ASC) USING BTREE,
  CONSTRAINT `member` FOREIGN KEY (`memberid`) REFERENCES `member_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `product` FOREIGN KEY (`product`) REFERENCES `product_spu` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `productcate` FOREIGN KEY (`productcategory`) REFERENCES `product_category` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `serviceid` FOREIGN KEY (`servicetype`) REFERENCES `service_type` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fault` FOREIGN KEY (`faulttype`) REFERENCES `service_fault_type` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '服务工单记录' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;

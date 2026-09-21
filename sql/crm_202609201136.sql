-- =============================================================================
-- CRM 模块数据库备份（结构 + 数据）
-- 生成时间：2026-09-20 11:36
-- 范围：crm_ 前缀的 30 张表（业务表 + 售后模块表）
-- 导入：mysql --default-character-set=utf8mb4 -uroot -p ruoyi-vue-pro < crm_202609201136.sql
-- 说明：不含 CREATE DATABASE，导入前请先建好库并指定库名；
--       菜单与字典数据见 deploy/mysql/repair/ 下的脚本。
-- =============================================================================

-- MySQL dump 10.13  Distrib 8.4.11, for Linux (x86_64)
--
-- Host: localhost    Database: ruoyi-vue-pro
-- ------------------------------------------------------
-- Server version	8.4.11

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `crm_after_sale_business_rel`
--

DROP TABLE IF EXISTS `crm_after_sale_business_rel`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_after_sale_business_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号，主键自增',
  `business_id` bigint NOT NULL COMMENT '新商机编号，关联 crm_business.id',
  `warranty_id` bigint NOT NULL COMMENT '来源质保档案编号',
  `visit_record_id` bigint DEFAULT NULL COMMENT '来源回访记录编号（可空）',
  `customer_id` bigint NOT NULL COMMENT '客户编号',
  `source_type` tinyint NOT NULL COMMENT '商机内容方向：1=复购 2=续签 3=扩项/新工程',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '转化说明',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_business_id` (`business_id`) USING BTREE,
  KEY `idx_warranty_id` (`warranty_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 售后转商机关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_after_sale_business_rel`
--

/*!40000 ALTER TABLE `crm_after_sale_business_rel` DISABLE KEYS */;
/*!40000 ALTER TABLE `crm_after_sale_business_rel` ENABLE KEYS */;

--
-- Table structure for table `crm_after_sale_config`
--

DROP TABLE IF EXISTS `crm_after_sale_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_after_sale_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号，主键自增',
  `warranty_warn_days` int NOT NULL DEFAULT '30' COMMENT '质保到期提前提醒天数，范围 7-180',
  `auto_visit_enabled` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否开启自动生成回访计划',
  `auto_visit_cycle_type` tinyint NOT NULL DEFAULT '1' COMMENT '自动回访周期类型：1=每季度 2=每半年 3=每年 4=质保到期前重点回访',
  `auto_visit_cycle_value` int NOT NULL DEFAULT '0' COMMENT '自动回访周期倍数/到期前天数（配合 auto_visit_cycle_type）',
  `task_remind_days` int NOT NULL DEFAULT '3' COMMENT '回访任务临期提醒提前天数',
  `task_overdue_days` int NOT NULL DEFAULT '3' COMMENT '回访任务逾期判定天数',
  `allow_early_visit` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否允许提前提交回访记录：0=不允许（未到计划回访日期禁止提交）1=允许',
  `ticket_overdue_days` int NOT NULL DEFAULT '7' COMMENT '工单超时未处理预警天数',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 售后设置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_after_sale_config`
--

/*!40000 ALTER TABLE `crm_after_sale_config` DISABLE KEYS */;
INSERT INTO `crm_after_sale_config` VALUES (1,30,_binary '\0',1,0,3,3,_binary '\0',7,'1','2026-09-10 11:13:27','1','2026-09-10 11:13:27',_binary '\0',1);
/*!40000 ALTER TABLE `crm_after_sale_config` ENABLE KEYS */;

--
-- Table structure for table `crm_after_sale_ticket`
--

DROP TABLE IF EXISTS `crm_after_sale_ticket`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_after_sale_ticket` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号，主键自增',
  `no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工单编号，AT+yyyyMMddHHmmss+4位随机',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '问题标题',
  `warranty_id` bigint DEFAULT NULL COMMENT '关联质保档案编号（可空）',
  `customer_id` bigint NOT NULL COMMENT '客户编号',
  `contract_id` bigint DEFAULT NULL COMMENT '合同编号（可空）',
  `visit_record_id` bigint DEFAULT NULL COMMENT '来源回访记录编号（客户报障为空）',
  `source` tinyint NOT NULL DEFAULT '1' COMMENT '工单来源：1=回访发现 2=客户报障',
  `priority` tinyint NOT NULL DEFAULT '2' COMMENT '优先级：1=高 2=中 3=低',
  `problem_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '问题类型（字典）',
  `description` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '问题描述',
  `contact_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '报障联系人姓名（快照）',
  `contact_mobile` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '报障联系人电话（快照）',
  `assignee_user_id` bigint DEFAULT NULL COMMENT '处理人（售后工程师/外协）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '工单状态：1=待处理 2=处理中 3=已完成 4=已关闭',
  `handle_result` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理结果说明',
  `start_time` datetime DEFAULT NULL COMMENT '开始处理时间',
  `finish_time` datetime DEFAULT NULL COMMENT '完成时间',
  `close_time` datetime DEFAULT NULL COMMENT '关闭归档时间',
  `close_user_id` bigint DEFAULT NULL COMMENT '关闭人编号',
  `is_chargeable` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否转有偿服务（超质保范围）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者（报单人）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_status_assignee` (`status`,`assignee_user_id`) USING BTREE,
  KEY `idx_customer_id` (`customer_id`) USING BTREE,
  KEY `idx_warranty_id` (`warranty_id`) USING BTREE,
  KEY `idx_no` (`no`) USING BTREE,
  KEY `idx_create_time` (`create_time`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 售后问题工单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_after_sale_ticket`
--

/*!40000 ALTER TABLE `crm_after_sale_ticket` DISABLE KEYS */;
INSERT INTO `crm_after_sale_ticket` VALUES (1,'AT20260920000001','哈哈哈哈',1,3103,6104,NULL,2,2,'mm',NULL,NULL,NULL,NULL,1,NULL,NULL,NULL,NULL,NULL,_binary '\0',NULL,'80446','2026-09-20 08:54:25','80446','2026-09-20 08:54:49',_binary '',2012);
/*!40000 ALTER TABLE `crm_after_sale_ticket` ENABLE KEYS */;

--
-- Table structure for table `crm_after_sale_ticket_note`
--

DROP TABLE IF EXISTS `crm_after_sale_ticket_note`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_after_sale_ticket_note` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号，主键自增',
  `ticket_id` bigint NOT NULL COMMENT '工单编号',
  `operate_type` tinyint NOT NULL COMMENT '操作类型：1=指派 2=状态变更 3=处理记录 4=转有偿 5=关闭',
  `content` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理内容/变更说明',
  `operator_user_id` bigint NOT NULL COMMENT '操作人',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_ticket_id` (`ticket_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 售后工单流转记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_after_sale_ticket_note`
--

/*!40000 ALTER TABLE `crm_after_sale_ticket_note` DISABLE KEYS */;
/*!40000 ALTER TABLE `crm_after_sale_ticket_note` ENABLE KEYS */;

--
-- Table structure for table `crm_after_sale_visit_plan`
--

DROP TABLE IF EXISTS `crm_after_sale_visit_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_after_sale_visit_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号，主键自增',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '计划名称',
  `warranty_id` bigint NOT NULL COMMENT '关联质保档案编号',
  `plan_type` tinyint NOT NULL DEFAULT '1' COMMENT '计划类型：1=手动 2=自动',
  `cycle_type` tinyint NOT NULL DEFAULT '1' COMMENT '周期类型：1=每季度 2=每半年 3=每年 4=质保到期前重点回访',
  `cycle_value` int NOT NULL DEFAULT '0' COMMENT '周期倍数 或 到期前天数（cycle_type=4 时）',
  `start_date` date DEFAULT NULL COMMENT '计划生效开始日期（默认=质保开始/验收日期）',
  `end_date` date DEFAULT NULL COMMENT '计划生效结束日期（默认=质保到期日）',
  `assignee_type` tinyint NOT NULL DEFAULT '1' COMMENT '任务处理人类型：1=售后人员 2=业务员',
  `assignee_user_id` bigint DEFAULT NULL COMMENT '指定处理人（为空则按类型取售后责任人/业务负责人）',
  `visit_type` tinyint NOT NULL DEFAULT '1' COMMENT '默认回访方式：1=电话 2=上门 3=微信 4=邮件',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '计划状态：1=启用 2=停用 3=已完成',
  `last_task_date` date DEFAULT NULL COMMENT '最近一次生成任务的日期（自动计划断点续排）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_warranty_id` (`warranty_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 售后回访计划表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_after_sale_visit_plan`
--

/*!40000 ALTER TABLE `crm_after_sale_visit_plan` DISABLE KEYS */;
INSERT INTO `crm_after_sale_visit_plan` VALUES (1,'西安热力',1,1,1,1,'2026-10-01','2027-09-18',1,80446,1,1,'2026-10-01',NULL,'80446','2026-09-20 08:52:42','80446','2026-09-20 10:42:40',_binary '\0',2012),(2,'陕西延长石油（集团）有限责任公司',3,1,1,1,'2026-06-01','2027-08-31',1,NULL,1,1,'2026-12-20',NULL,'80446','2026-09-20 10:13:29','80446','2026-09-20 10:48:23',_binary '\0',1);
/*!40000 ALTER TABLE `crm_after_sale_visit_plan` ENABLE KEYS */;

--
-- Table structure for table `crm_after_sale_visit_record`
--

DROP TABLE IF EXISTS `crm_after_sale_visit_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_after_sale_visit_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号，主键自增',
  `task_id` bigint DEFAULT NULL COMMENT '关联回访任务编号，手动登记可为空',
  `status` tinyint NOT NULL DEFAULT '2' COMMENT '记录状态：1=待填写 2=已填写',
  `warranty_id` bigint NOT NULL COMMENT '质保档案编号',
  `customer_id` bigint NOT NULL COMMENT '客户编号（冗余）',
  `contract_id` bigint NOT NULL COMMENT '合同编号（冗余）',
  `contact_id` bigint DEFAULT NULL COMMENT '被访联系人编号，关联 crm_contact.id',
  `visit_type` tinyint NOT NULL DEFAULT '1' COMMENT '回访方式：1=电话 2=上门 3=微信 4=邮件',
  `visit_time` datetime NOT NULL COMMENT '回访时间',
  `content` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '回访内容',
  `satisfaction_level` tinyint DEFAULT NULL COMMENT '满意度：5=非常满意 4=满意 3=一般 2=不满意 1=非常不满意',
  `satisfaction_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '满意度文字备注',
  `next_visit_date` date DEFAULT NULL COMMENT '建议下次回访日期',
  `pic_urls` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '现场照片 URL 数组(JSON)',
  `file_urls` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '附件 URL 数组(JSON)',
  `ticket_id` bigint DEFAULT NULL COMMENT '本次回访转出的问题工单编号（可空）',
  `business_id` bigint DEFAULT NULL COMMENT '本次回访转出的新商机编号（可空）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者（回访人）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_task_id` (`task_id`) USING BTREE,
  KEY `idx_warranty_time` (`warranty_id`,`visit_time`) USING BTREE,
  KEY `idx_customer_id` (`customer_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 售后回访记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_after_sale_visit_record`
--

/*!40000 ALTER TABLE `crm_after_sale_visit_record` DISABLE KEYS */;
INSERT INTO `crm_after_sale_visit_record` VALUES (1,3,2,3,12,2,NULL,1,'2026-09-20 10:48:23','满意',4,NULL,NULL,NULL,NULL,NULL,NULL,'80446','2026-09-20 10:48:23','80446','2026-09-20 10:48:23',_binary '\0',1);
/*!40000 ALTER TABLE `crm_after_sale_visit_record` ENABLE KEYS */;

--
-- Table structure for table `crm_after_sale_visit_task`
--

DROP TABLE IF EXISTS `crm_after_sale_visit_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_after_sale_visit_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号，主键自增',
  `plan_id` bigint NOT NULL DEFAULT '0' COMMENT '来源计划编号，0=手动创建任务',
  `cycle_key` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '周期标识：周期型任务为 C1/C2…，到期型为 EXPIRE，手动任务为空',
  `biz_no` int DEFAULT NULL COMMENT '业务编号：按质保档案独立递增，仅用于展示',
  `warranty_id` bigint NOT NULL COMMENT '质保档案编号',
  `customer_id` bigint NOT NULL COMMENT '客户编号（冗余）',
  `contract_id` bigint NOT NULL COMMENT '合同编号（冗余）',
  `assignee_user_id` bigint NOT NULL COMMENT '处理人（售后人员或业务员）',
  `task_date` date NOT NULL COMMENT '计划执行日期',
  `window_end_date` date DEFAULT NULL COMMENT '窗口期截止日期 = 计划执行日期 + 回访任务逾期判定天数；当前日期超过该日期判定为已逾期',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '任务状态：1=待处理 2=已完成 3=已逾期',
  `finish_time` datetime DEFAULT NULL COMMENT '实际完成时间',
  `overdue_sent_date` date DEFAULT NULL COMMENT '临期提醒发送日期（防重复）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务备注/回访提示',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_assignee_status` (`assignee_user_id`,`status`) USING BTREE,
  KEY `idx_task_date` (`task_date`) USING BTREE,
  KEY `idx_plan_id` (`plan_id`) USING BTREE,
  KEY `idx_warranty_id` (`warranty_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 售后回访任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_after_sale_visit_task`
--

/*!40000 ALTER TABLE `crm_after_sale_visit_task` DISABLE KEYS */;
INSERT INTO `crm_after_sale_visit_task` VALUES (1,2,'C1',1,3,12,2,3002,'2026-09-19','2026-09-22',1,NULL,NULL,'【陕西延长石油（集团）有限责任公司】质保期回访任务','80446','2026-09-20 10:40:21','80446','2026-09-20 10:46:54',_binary '',1),(2,1,'C1',1,1,3103,6104,80446,'2026-10-01','2026-10-04',1,NULL,NULL,'【西安热力】质保期回访任务','80446','2026-09-20 10:42:40','80446','2026-09-20 11:26:50',_binary '\0',2012),(3,2,'C1',1,3,12,2,3002,'2026-06-01','2026-06-04',2,'2026-09-20 10:48:23',NULL,'【陕西延长石油（集团）有限责任公司】质保期回访任务','80446','2026-09-20 10:47:23','80446','2026-09-20 10:48:23',_binary '\0',1),(4,2,'C2',2,3,12,2,3002,'2026-12-20','2026-12-23',1,NULL,NULL,'【陕西延长石油（集团）有限责任公司】质保期回访任务','80446','2026-09-20 10:48:23','80446','2026-09-20 10:48:23',_binary '\0',1),(5,0,NULL,3,3,12,2,3002,'2026-09-20','2026-09-23',1,NULL,NULL,'111','80446','2026-09-20 10:50:40','80446','2026-09-20 10:50:57',_binary '\0',1);
/*!40000 ALTER TABLE `crm_after_sale_visit_task` ENABLE KEYS */;

--
-- Table structure for table `crm_after_sale_warranty`
--

DROP TABLE IF EXISTS `crm_after_sale_warranty`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_after_sale_warranty` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号，主键自增',
  `no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '质保编号，WB+yyyyMMdd+3位流水',
  `contract_id` bigint NOT NULL COMMENT '来源合同编号，关联 crm_contract.id',
  `customer_id` bigint NOT NULL COMMENT '客户编号，冗余，关联 crm_customer.id',
  `contract_product_id` bigint DEFAULT NULL COMMENT '质保标的-合同产品行编号，关联 crm_contract_product.id，可空',
  `project_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工程项目名称',
  `project_accept_date` date NOT NULL COMMENT '项目完工验收日期（质保起算日，可手动修改）',
  `warranty_years` int NOT NULL COMMENT '质保年限，1-5 年',
  `warranty_end_date` date NOT NULL COMMENT '质保到期日期，默认=验收日期+年限-1天，可人工修改',
  `warranty_scope` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '质保范围（防腐部位/介质环境/责任边界等描述）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '质保状态：1=质保中 2=即将到期 3=已到期终止',
  `owner_user_id` bigint DEFAULT NULL COMMENT '业务负责人（原合同业务员）',
  `service_user_id` bigint DEFAULT NULL COMMENT '售后责任人',
  `last_visit_time` datetime DEFAULT NULL COMMENT '最近一次回访时间（冗余）',
  `next_visit_time` datetime DEFAULT NULL COMMENT '计划下次回访时间（冗余）',
  `remind_sent_date` date DEFAULT NULL COMMENT '最近一次到期提醒发送日期（防重复推送）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  `uk_contract_id` bigint GENERATED ALWAYS AS (if((`deleted` = 0x00),`contract_id`,NULL)) VIRTUAL COMMENT '一对一约束生成列：未删除=contract_id，已删除=NULL',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_contract_id` (`uk_contract_id`),
  KEY `idx_contract_id` (`contract_id`) USING BTREE,
  KEY `idx_customer_id` (`customer_id`) USING BTREE,
  KEY `idx_status_end` (`status`,`warranty_end_date`) USING BTREE,
  KEY `idx_owner_user_id` (`owner_user_id`) USING BTREE,
  KEY `idx_no` (`no`) USING BTREE,
  KEY `idx_warranty_end_date` (`warranty_end_date`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 售后质保档案表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_after_sale_warranty`
--

/*!40000 ALTER TABLE `crm_after_sale_warranty` DISABLE KEYS */;
INSERT INTO `crm_after_sale_warranty` (`id`, `no`, `contract_id`, `customer_id`, `contract_product_id`, `project_name`, `project_accept_date`, `warranty_years`, `warranty_end_date`, `warranty_scope`, `status`, `owner_user_id`, `service_user_id`, `last_visit_time`, `next_visit_time`, `remind_sent_date`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`) VALUES (1,'WB20260920000001',6104,3103,NULL,'油管防腐','2026-09-29',1,'2027-09-02',NULL,1,80446,80446,NULL,NULL,NULL,NULL,'80446','2026-09-20 08:51:38','80446','2026-09-20 08:51:38',_binary '\0',2012),(2,'WB20260920000002',2,12,NULL,'油管防腐','2026-09-01',1,'2027-08-31',NULL,1,3007,3001,NULL,NULL,NULL,NULL,'80446','2026-09-20 10:12:31','1','2026-09-20 10:24:38',_binary '',1),(3,'WB20260920000003',2,12,NULL,'油管防腐','2026-06-01',1,'2027-08-31',NULL,1,3001,3002,'2026-09-20 10:48:23',NULL,NULL,NULL,'80446','2026-09-20 10:12:50','80446','2026-09-20 10:48:23',_binary '\0',1),(4,'WB20260920000004',2,12,NULL,'油管防腐','2026-09-01',1,'2027-08-31',NULL,1,100,80446,NULL,NULL,NULL,NULL,'80446','2026-09-20 10:21:07','1','2026-09-20 10:24:38',_binary '',1),(5,'WB20260920000005',6101,3102,NULL,'验证-首次创建','2026-01-01',1,'2026-12-31',NULL,1,80446,80446,NULL,NULL,NULL,NULL,'80446','2026-09-20 10:33:36','80446','2026-09-20 10:36:31',_binary '',2012),(6,'WB20260920000006',6101,3102,NULL,'验证-删除后重建','2026-01-01',2,'2027-12-31',NULL,1,80446,80446,NULL,NULL,NULL,NULL,'80446','2026-09-20 10:36:31','80446','2026-09-20 10:36:31',_binary '',2012);
/*!40000 ALTER TABLE `crm_after_sale_warranty` ENABLE KEYS */;

--
-- Table structure for table `crm_business`
--

DROP TABLE IF EXISTS `crm_business`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_business` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '商机名称',
  `customer_id` bigint NOT NULL COMMENT '客户编号',
  `follow_up_status` bit(1) DEFAULT b'0' COMMENT '跟进状态',
  `follow_up_count` int NOT NULL DEFAULT '0' COMMENT '累计跟进次数',
  `contact_last_time` datetime DEFAULT NULL COMMENT '最后跟进时间',
  `contact_next_time` datetime DEFAULT NULL COMMENT '下次联系时间',
  `owner_user_id` bigint DEFAULT NULL COMMENT '负责人的用户编号',
  `status_type_id` bigint DEFAULT NULL COMMENT '商机状态类型编号',
  `status_id` bigint DEFAULT NULL COMMENT '商机状态编号',
  `stage` tinyint DEFAULT NULL COMMENT '商机阶段：1商机待评估 2初步接洽 3需求确认 4方案评估 5商务谈判',
  `business_state` tinyint DEFAULT NULL COMMENT '商机状态组：1未开始 2进行中 3已赢单 4已丢单',
  `win_rate` tinyint DEFAULT NULL COMMENT '预估赢单率(%)：随阶段自动计算，赢单=100 输单=0',
  `lost_reason` tinyint DEFAULT NULL COMMENT '丢单原因：1客户预算不足/预算取消 2客户选择竞品 3项目无限期搁置 4客户内部决策人变更 5客户需求重大变更 6其他',
  `lost_remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '丢单备注',
  `sign_date` datetime DEFAULT NULL COMMENT '签约日期（赢单时填写）',
  `sign_amount` decimal(24,6) DEFAULT NULL COMMENT '签约金额（赢单时填写）',
  `closed_at` datetime DEFAULT NULL COMMENT '关闭时间（赢单/丢单时间）',
  `closed_by` bigint DEFAULT NULL COMMENT '关闭操作人',
  `end_status` tinyint DEFAULT NULL COMMENT '结束状态：1-赢单 2-输单3-无效',
  `deal_time` datetime DEFAULT NULL COMMENT '预计成交日期',
  `total_product_price` decimal(24,6) DEFAULT NULL COMMENT '产品总金额，单位：元',
  `discount_percent` decimal(24,6) DEFAULT NULL COMMENT '整单折扣，百分比',
  `total_price` decimal(24,6) DEFAULT NULL COMMENT '商机总金额，单位：元',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '商机来源（字典 crm_business_source；after_sale=售后回访）',
  `source_rel_id` bigint NOT NULL DEFAULT '0' COMMENT '来源关联编号（售后转商机时= crm_after_sale_business_rel.id）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '创建人',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `end_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '结束时的备注',
  `deleted` bit(1) DEFAULT b'0' COMMENT '逻辑删除',
  `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=5110 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 商机表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_business`
--

/*!40000 ALTER TABLE `crm_business` DISABLE KEYS */;
INSERT INTO `crm_business` VALUES (4,'一个商机',2,NULL,0,'2024-02-19 21:38:37','2024-02-13 00:00:00',1,3,4,1,2,5,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,10.000000,NULL,NULL,0,'','1','2023-11-30 12:05:16','2024-02-19 21:38:38',NULL,_binary '',1),(5,'haoxx',14,_binary '\0',0,NULL,NULL,1,3,NULL,1,2,5,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2024-02-13 00:00:00',10210.000000,2.000000,10005.800000,'321321',NULL,0,'1','1','2024-02-21 16:35:59','2024-02-21 17:42:54',NULL,_binary '\0',1),(6,'哈罗',14,_binary '',1,'2024-02-21 23:35:46','2024-02-20 00:00:00',1,5,NULL,1,2,5,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,10010.000000,0.000000,10010.000000,NULL,NULL,0,'1','1','2024-02-21 21:30:17','2024-02-21 23:35:46',NULL,_binary '\0',1),(7,'摩西摩西',14,_binary '\0',0,NULL,NULL,1,5,7,1,2,5,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0.000000,0.000000,0.000000,NULL,NULL,0,'1','1','2024-02-21 21:48:20','2024-02-21 21:48:20',NULL,_binary '\0',1),(8,'吃饭落',14,_binary '\0',0,NULL,NULL,103,4,5,1,2,5,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2024-02-14 00:00:00',0.000000,0.000000,0.000000,NULL,NULL,0,'1','1','2024-02-21 21:49:29','2024-02-21 21:49:29',NULL,_binary '\0',1),(9,'测试关联商机',14,_binary '\0',0,NULL,NULL,1,5,7,1,3,100,NULL,NULL,NULL,NULL,'2024-02-22 20:40:18',NULL,1,NULL,0.000000,0.000000,0.000000,NULL,NULL,0,'1','1','2024-02-22 09:32:06','2024-02-22 20:40:18',NULL,_binary '\0',1),(10,'测试某个状态',14,_binary '\0',0,NULL,NULL,1,4,6,1,4,0,6,NULL,NULL,NULL,'2024-02-22 20:36:49',NULL,2,NULL,0.000000,0.000000,0.000000,NULL,NULL,0,'1','1','2024-02-22 19:55:25','2024-02-22 20:36:49',NULL,_binary '\0',1),(11,'阿巴阿巴',14,_binary '\0',0,NULL,NULL,1,4,6,1,3,100,NULL,NULL,NULL,NULL,'2024-02-22 20:41:07',NULL,1,NULL,0.000000,0.000000,0.000000,NULL,NULL,0,'1','1','2024-02-22 20:40:54','2024-02-22 20:41:07',NULL,_binary '\0',1),(12,'演示商机',16,_binary '\0',0,NULL,NULL,1,6,10,1,4,0,6,NULL,NULL,NULL,'2024-02-24 00:16:31',NULL,2,NULL,10110.300000,20.000000,8088.240000,NULL,NULL,0,'1','1','2024-02-24 00:12:04','2024-02-24 00:16:31',NULL,_binary '\0',1),(13,'哈哈哈',17,_binary '\0',0,NULL,'2025-09-11 22:02:16',1,4,5,1,2,5,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2024-02-13 00:00:00',20020.000000,20.000000,16016.000000,NULL,NULL,0,'1','1','2024-02-24 00:23:03','2025-09-30 22:02:21',NULL,_binary '\0',1),(14,'新的线索',16,_binary '\0',0,NULL,NULL,1,4,8,1,2,5,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,100.000000,2.000000,98.000000,NULL,NULL,0,'1','1','2024-02-24 14:50:30','2025-09-30 19:25:09',NULL,_binary '\0',1),(15,'xx',16,_binary '\0',0,NULL,NULL,1,4,5,1,2,5,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0.000000,0.000000,0.000000,'111',NULL,0,'1','1','2025-11-22 11:26:54','2025-11-22 11:26:54',NULL,_binary '\0',1),(16,'xxx',16,_binary '\0',0,NULL,NULL,1,4,5,1,2,5,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0.000000,0.000000,0.000000,NULL,NULL,0,'1','1','2025-11-22 11:28:27','2025-11-22 11:28:27',NULL,_binary '\0',1),(5001,'长庆五厂集输管线3PE防腐供货',3001,_binary '\0',0,NULL,'2026-09-10 00:00:00',80939,16,30,5,2,70,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1096000.000000,5.000000,1041200.000000,'年度框架下首批供货',NULL,0,'1','1','2026-09-08 19:56:10','2026-09-08 20:44:38',NULL,_binary '\0',2012),(5002,'靖边采油厂油管防腐年度采购',3002,_binary '\0',0,NULL,'2026-09-08 00:00:00',80939,16,30,5,3,100,NULL,NULL,NULL,NULL,'2026-09-08 20:44:38',NULL,1,NULL,1429000.000000,3.000000,1386130.000000,'已中标，转合同',NULL,0,'1','1','2026-09-08 19:56:10','2026-09-08 20:44:38',NULL,_binary '\0',2012),(5003,'延安热力供热管网保温改造',3003,_binary '\0',0,NULL,'2026-09-12 00:00:00',80940,17,30,5,2,70,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1248000.000000,8.000000,1148160.000000,'聚氨酯保温管+施工',NULL,0,'1','1','2026-09-08 19:56:10','2026-09-08 20:44:38',NULL,_binary '\0',2012),(5004,'长庆二厂集气站保温保冷',3004,_binary '\0',0,NULL,'2026-09-09 00:00:00',80941,17,36,2,2,10,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,648000.000000,0.000000,648000.000000,'需求确认阶段',NULL,0,'1','1','2026-09-08 19:56:10','2026-09-08 20:44:38',NULL,_binary '\0',2012),(5005,'延安炼油厂管道内检测服务',3005,_binary '',1,NULL,'2026-09-06 00:00:00',80945,18,30,5,3,100,NULL,NULL,NULL,NULL,'2026-09-08 20:44:38',NULL,1,NULL,36000.000000,0.000000,36000.000000,'漏磁内检测，已实施',NULL,0,'1','1','2026-09-08 19:56:10','2026-09-08 20:44:38',NULL,_binary '\0',2012),(5006,'榆林化工工艺管线防腐',3007,_binary '',1,NULL,'2026-09-14 00:00:00',80940,16,35,3,2,40,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,376000.000000,5.000000,357200.000000,'环氧陶瓷内衬方案',NULL,0,'1','1','2026-09-08 19:56:10','2026-09-08 20:44:38',NULL,_binary '\0',2012),(5007,'长庆五厂2026年度集输管线3PE防腐大修项目',3001,_binary '\0',0,NULL,NULL,80939,17,30,5,2,70,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-02 00:00:00',0.000000,0.000000,0.000000,NULL,NULL,0,'80446','80446','2026-09-12 21:13:12','2026-09-14 10:48:44',NULL,_binary '',2012),(5008,'长庆五厂2026年度集输管线3PE防腐大修项目',3001,_binary '\0',0,NULL,NULL,80939,16,30,5,3,100,NULL,NULL,NULL,NULL,'2026-09-14 10:48:41',NULL,1,'2026-10-02 00:00:00',0.000000,0.000000,0.000000,NULL,NULL,0,'80446','80446','2026-09-14 10:10:33','2026-09-14 10:48:41',NULL,_binary '',2012),(5009,'长庆五厂2026年度集输管线3PE防腐大修项目',3001,_binary '\0',0,NULL,NULL,80939,17,30,5,2,70,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-02 00:00:00',0.000000,0.000000,0.000000,NULL,NULL,0,'80446','80446','2026-09-14 14:10:15','2026-09-14 14:10:39',NULL,_binary '',2012),(5010,'长庆五厂2026年度集输管线3PE防腐大修项目',3001,_binary '\0',0,NULL,NULL,80939,17,30,5,2,70,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-02 00:00:00',0.000000,0.000000,0.000000,NULL,NULL,0,'80446','80446','2026-09-15 08:43:30','2026-09-15 09:56:15',NULL,_binary '',2012),(5011,'西安石油（集团）有限责任公司',3006,_binary '\0',0,NULL,NULL,80446,16,30,5,3,100,NULL,NULL,NULL,NULL,'2026-09-15 10:48:57',NULL,1,'2026-09-15 10:48:14',0.000000,0.000000,0.000000,NULL,NULL,0,'80446','80446','2026-09-15 10:48:31','2026-09-15 10:48:57',NULL,_binary '\0',2012),(5012,'长安石油',3007,_binary '\0',0,NULL,NULL,80446,16,30,5,3,100,NULL,NULL,NULL,NULL,'2026-09-15 15:09:09',NULL,1,'2026-09-15 15:08:06',0.000000,5.000000,0.000000,NULL,NULL,0,'80446','80446','2026-09-15 15:08:20','2026-09-15 15:09:09',NULL,_binary '\0',2012),(5013,'靖边采油厂油管防腐年度采购',3002,_binary '\0',0,NULL,NULL,80939,16,30,5,4,0,6,NULL,NULL,NULL,'2026-09-17 15:19:18',NULL,2,'2026-08-08 14:53:23',1177000.000000,0.000000,1177000.000000,'早期报价有效期届满未交，系统自动置为已失效',NULL,0,'80446','80446','2026-09-17 14:56:51','2026-09-17 15:19:18',NULL,_binary '\0',2012),(5014,'延安炼油厂管道内检测服务',3005,_binary '\0',0,NULL,NULL,80945,16,30,5,3,100,NULL,NULL,NULL,NULL,'2026-09-17 15:25:09',NULL,1,'2026-09-22 14:59:31',36000.000000,0.000000,36000.000000,'审核通过，已一键转管道内检测服务合同',NULL,0,'80446','80446','2026-09-17 15:00:30','2026-09-17 15:25:09',NULL,_binary '\0',2012),(5015,'靖边采油厂油管防腐年度采购',3002,_binary '\0',0,NULL,NULL,80939,16,30,5,4,0,6,NULL,NULL,NULL,'2026-09-17 15:24:24',NULL,2,'2026-09-25 15:02:20',1429000.000000,0.000000,1429000.000000,'销售售价偏高客户异议，审批驳回，退回修改后重报',NULL,0,'80446','80446','2026-09-17 15:03:34','2026-09-17 15:24:24',NULL,_binary '\0',2012),(5016,'靖边采油厂油管防腐年度采购（修订版）',3002,_binary '\0',0,NULL,NULL,80939,16,30,5,3,100,NULL,NULL,NULL,NULL,'2026-09-17 15:23:28',NULL,1,'2026-09-04 15:06:34',1429000.000000,0.000000,1429000.000000,'审核通过，已一键转油管防腐采购合同',NULL,0,'80446','80446','2026-09-17 15:07:15','2026-09-17 15:23:28',NULL,_binary '\0',2012),(5017,'延安炼油厂管道内检测服务',3005,_binary '\0',0,NULL,NULL,80945,16,30,5,4,0,6,NULL,NULL,NULL,'2026-09-17 15:22:07',NULL,2,'2026-10-08 15:09:12',24000.000000,0.000000,24000.000000,'增补检测段报价，已提交待审核\n',NULL,0,'80446','80446','2026-09-17 15:09:47','2026-09-17 15:22:07',NULL,_binary '\0',2012),(5018,'靖边采油厂油管防腐年度采购',3002,_binary '\0',0,'2026-09-20 09:06:07','2026-09-21 09:06:07',80939,16,35,3,2,40,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-10-10 15:11:11',622000.000000,0.000000,622000.000000,'第二批追加需求，草稿待提交',NULL,0,'80446','80446','2026-09-17 15:11:50','2026-09-20 09:06:08',NULL,_binary '\0',2012),(5019,'中石油管道局西部管道防腐入围',3004,_binary '\0',0,NULL,NULL,80446,16,30,5,3,100,NULL,NULL,'2026-09-08 09:54:44',0.000000,'2026-09-18 09:54:47',80446,1,'2026-09-18 09:52:26',0.000000,5.000000,0.000000,NULL,NULL,0,'80446','80446','2026-09-18 09:52:48','2026-09-18 09:54:53',NULL,_binary '',2012),(5101,'长庆八厂集输管线3PE防腐供货',3101,_binary '',1,'2026-09-20 09:18:37','2026-09-21 09:18:37',80446,16,37,5,3,100,NULL,NULL,'2026-09-18 16:58:48',858420.000000,'2026-09-18 16:58:48',80446,1,'2026-10-25 00:00:00',903600.000000,5.000000,858420.000000,'年度框架下首批供货',NULL,0,'1','80446','2026-09-18 10:59:01','2026-09-20 09:18:39','端到端演示：合同已审批通过',_binary '\0',2012),(5102,'定边采油厂油管防腐年度采购',3102,_binary '',0,NULL,'2026-09-10 00:00:00',80446,16,30,5,3,100,NULL,NULL,'2026-09-08 00:00:00',1595650.000000,'2026-09-18 10:59:01',80446,1,'2026-09-08 00:00:00',1645000.000000,3.000000,1595650.000000,'已中标，转合同',NULL,0,'1','','2026-09-18 10:59:01','2026-09-18 10:59:01',NULL,_binary '\0',2012),(5103,'西安热力供热管网保温改造',3103,_binary '',0,NULL,'2026-09-14 00:00:00',80446,16,30,5,2,70,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-11-20 00:00:00',1176000.000000,8.000000,1081920.000000,'聚氨酯保温管+施工',NULL,0,'1','','2026-09-18 10:59:01','2026-09-18 10:59:01',NULL,_binary '\0',2012),(5104,'长庆三厂集气站保温保冷',3104,_binary '',0,NULL,'2026-09-11 00:00:00',80446,16,36,2,2,10,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-12-15 00:00:00',464000.000000,0.000000,464000.000000,'需求确认阶段',NULL,0,'1','','2026-09-18 10:59:01','2026-09-18 10:59:01',NULL,_binary '\0',2012),(5105,'陕天然气管道内检测服务',3105,_binary '',1,NULL,'2026-09-08 00:00:00',80446,16,30,5,3,100,NULL,NULL,'2026-08-28 00:00:00',39200.000000,'2026-09-18 10:59:01',80446,1,'2026-08-28 00:00:00',39200.000000,0.000000,39200.000000,'超声内检测，已实施',NULL,0,'1','','2026-09-18 10:59:01','2026-09-18 10:59:01',NULL,_binary '\0',2012),(5106,'中煤榆林工艺管线保温',3107,_binary '',1,NULL,'2026-09-15 00:00:00',80446,16,35,3,2,40,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-11-30 00:00:00',465900.000000,5.000000,442605.000000,'环氧陶瓷内衬+保温方案',NULL,0,'1','','2026-09-18 10:59:01','2026-09-18 10:59:01',NULL,_binary '\0',2012),(5107,'榆林炼油厂储罐管线防腐',3108,_binary '',1,NULL,'2026-09-17 00:00:00',80446,16,35,3,3,100,NULL,NULL,'2026-09-20 08:44:49',452505.000000,'2026-09-20 08:44:53',80446,1,'2026-12-20 00:00:00',466500.000000,3.000000,452505.000000,'结合年度检修推进',NULL,0,'1','80446','2026-09-18 10:59:01','2026-09-20 08:44:53',NULL,_binary '\0',2012),(5108,'中石油管道局西部管道防腐入围',3005,_binary '\0',0,NULL,NULL,80446,NULL,NULL,4,4,0,1,NULL,NULL,NULL,'2026-09-18 11:06:26',80446,2,NULL,0.000000,0.000000,0.000000,NULL,NULL,0,'80446','80446','2026-09-18 11:06:04','2026-09-18 11:06:32',NULL,_binary '',2012),(5109,'潞安煤基厂区工艺管线防腐保温改造',3111,_binary '\0',0,NULL,NULL,80446,NULL,NULL,5,3,100,NULL,NULL,'2026-09-23 17:19:52',100004.000000,'2026-09-18 17:19:55',80446,1,'2026-11-15 00:00:00',0.000000,0.000000,0.000000,'厂区工艺管线，结合年度检修窗口实施',NULL,0,'80446','80446','2026-09-18 17:19:40','2026-09-18 17:19:55',NULL,_binary '\0',2012);
/*!40000 ALTER TABLE `crm_business` ENABLE KEYS */;

--
-- Table structure for table `crm_business_product`
--

DROP TABLE IF EXISTS `crm_business_product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_business_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `business_id` bigint NOT NULL COMMENT '商机编号',
  `product_id` bigint NOT NULL COMMENT '产品编号',
  `product_price` decimal(24,6) NOT NULL COMMENT '产品单价',
  `business_price` decimal(24,6) NOT NULL COMMENT '商机价格',
  `count` decimal(24,6) NOT NULL COMMENT '数量',
  `total_price` decimal(24,6) NOT NULL COMMENT '总计价格',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=107 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 商机产品关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_business_product`
--

/*!40000 ALTER TABLE `crm_business_product` DISABLE KEYS */;
INSERT INTO `crm_business_product` VALUES (29,5,4,10010.000000,10010.000000,1.000000,10010.000000,'1','2024-02-21 16:35:59','1','2024-02-21 09:42:54',_binary '',1),(30,5,4,10010.000000,10010.000000,1.000000,10010.000000,'1','2024-02-21 17:42:54','1','2024-02-21 17:42:54',_binary '\0',1),(31,5,5,100.300000,200.000000,1.000000,200.000000,'1','2024-02-21 17:42:54','1','2024-02-21 17:42:54',_binary '\0',1),(32,6,4,10010.000000,10010.000000,1.000000,10010.000000,'1','2024-02-21 21:30:17','1','2024-02-21 21:30:17',_binary '\0',1),(33,12,4,10010.000000,10010.000000,1.000000,10010.000000,'1','2024-02-24 00:12:04','1','2024-02-24 00:12:04',_binary '\0',1),(34,12,5,100.300000,100.300000,1.000000,100.300000,'1','2024-02-24 00:12:04','1','2024-02-24 00:12:04',_binary '\0',1),(35,13,4,10010.000000,10010.000000,1.000000,10010.000000,'1','2024-02-24 07:50:16','1','2024-02-24 07:50:16',_binary '\0',1),(36,13,4,10010.000000,10010.000000,1.000000,10010.000000,'1','2024-02-24 07:50:16','1','2024-02-24 07:50:16',_binary '\0',1),(37,14,3,100.000000,100.000000,1.000000,100.000000,'1','2025-09-30 19:25:10','1','2025-09-30 19:25:10',_binary '\0',1),(56,5001,1001,860.000000,860.000000,800.000000,688000.000000,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(57,5001,1002,1360.000000,1360.000000,300.000000,408000.000000,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(58,5002,1003,185.000000,185.000000,5000.000000,925000.000000,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(59,5002,1004,168.000000,168.000000,3000.000000,504000.000000,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(60,5003,1008,480.000000,480.000000,2600.000000,1248000.000000,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(61,5004,1009,360.000000,360.000000,1800.000000,648000.000000,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(62,5005,1012,12000.000000,12000.000000,3.000000,36000.000000,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(63,5006,1007,240.000000,240.000000,1200.000000,288000.000000,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(64,5006,1006,220.000000,220.000000,400.000000,88000.000000,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(65,5011,1005,152.000000,152.000000,0.000000,0.000000,'80446','2026-09-15 10:48:31','80446','2026-09-15 10:48:31',_binary '\0',2012),(66,5012,1005,152.000000,152.000000,0.000000,0.000000,'80446','2026-09-15 15:08:20','80446','2026-09-15 15:08:58',_binary '',2012),(67,5012,1005,152.000000,152.000000,0.000000,0.000000,'80446','2026-09-15 15:08:58','80446','2026-09-15 15:08:58',_binary '\0',2012),(68,5013,1003,185.000000,188.000000,4000.000000,752000.000000,'80446','2026-09-17 14:56:51','80446','2026-09-17 15:12:40',_binary '',2012),(69,5013,1004,168.000000,170.000000,2500.000000,425000.000000,'80446','2026-09-17 14:56:51','80446','2026-09-17 15:12:40',_binary '',2012),(70,5014,1012,12000.000000,12000.000000,3.000000,36000.000000,'80446','2026-09-17 15:00:30','80446','2026-09-17 15:20:45',_binary '',2012),(71,5015,1003,185.000000,185.000000,5000.000000,925000.000000,'80446','2026-09-17 15:03:34','80446','2026-09-17 15:24:24',_binary '',2012),(72,5015,1004,168.000000,168.000000,3000.000000,504000.000000,'80446','2026-09-17 15:03:34','80446','2026-09-17 15:24:24',_binary '',2012),(73,5016,1003,185.000000,185.000000,5000.000000,925000.000000,'80446','2026-09-17 15:07:15','80446','2026-09-17 15:23:28',_binary '',2012),(74,5016,1004,168.000000,168.000000,3000.000000,504000.000000,'80446','2026-09-17 15:07:15','80446','2026-09-17 15:23:28',_binary '',2012),(75,5017,1012,12000.000000,12000.000000,2.000000,24000.000000,'80446','2026-09-17 15:09:47','80446','2026-09-17 15:22:07',_binary '',2012),(76,5018,1003,185.000000,185.000000,2000.000000,370000.000000,'80446','2026-09-17 15:11:50','80446','2026-09-17 15:21:35',_binary '',2012),(77,5018,1004,168.000000,168.000000,1500.000000,252000.000000,'80446','2026-09-17 15:11:50','80446','2026-09-17 15:21:35',_binary '',2012),(78,5013,1003,185.000000,188.000000,4000.000000,752000.000000,'80446','2026-09-17 15:12:40','80446','2026-09-17 15:19:18',_binary '',2012),(79,5013,1004,168.000000,170.000000,2500.000000,425000.000000,'80446','2026-09-17 15:12:40','80446','2026-09-17 15:19:18',_binary '',2012),(80,5013,1003,185.000000,188.000000,4000.000000,752000.000000,'80446','2026-09-17 15:19:18','80446','2026-09-17 15:19:18',_binary '\0',2012),(81,5013,1004,168.000000,170.000000,2500.000000,425000.000000,'80446','2026-09-17 15:19:18','80446','2026-09-17 15:19:18',_binary '\0',2012),(82,5014,1012,12000.000000,12000.000000,3.000000,36000.000000,'80446','2026-09-17 15:20:45','80446','2026-09-17 15:25:09',_binary '',2012),(83,5018,1003,185.000000,185.000000,2000.000000,370000.000000,'80446','2026-09-17 15:21:35','80446','2026-09-17 15:21:35',_binary '\0',2012),(84,5018,1004,168.000000,168.000000,1500.000000,252000.000000,'80446','2026-09-17 15:21:35','80446','2026-09-17 15:21:35',_binary '\0',2012),(85,5017,1012,12000.000000,12000.000000,2.000000,24000.000000,'80446','2026-09-17 15:22:07','80446','2026-09-17 15:22:07',_binary '\0',2012),(86,5016,1003,185.000000,185.000000,5000.000000,925000.000000,'80446','2026-09-17 15:23:28','80446','2026-09-17 15:23:28',_binary '\0',2012),(87,5016,1004,168.000000,168.000000,3000.000000,504000.000000,'80446','2026-09-17 15:23:28','80446','2026-09-17 15:23:28',_binary '\0',2012),(88,5015,1003,185.000000,185.000000,5000.000000,925000.000000,'80446','2026-09-17 15:24:24','80446','2026-09-17 15:24:24',_binary '\0',2012),(89,5015,1004,168.000000,168.000000,3000.000000,504000.000000,'80446','2026-09-17 15:24:24','80446','2026-09-17 15:24:24',_binary '\0',2012),(90,5014,1012,12000.000000,12000.000000,3.000000,36000.000000,'80446','2026-09-17 15:25:09','80446','2026-09-17 15:25:09',_binary '\0',2012),(91,5019,1002,1360.000000,1360.000000,0.000000,0.000000,'80446','2026-09-18 09:52:48','80446','2026-09-18 09:54:13',_binary '',2012),(92,5019,1004,168.000000,168.000000,0.000000,0.000000,'80446','2026-09-18 09:52:48','80446','2026-09-18 09:54:13',_binary '',2012),(93,5019,1002,1360.000000,1360.000000,0.000000,0.000000,'80446','2026-09-18 09:54:13','80446','2026-09-18 09:54:13',_binary '\0',2012),(94,5019,1004,168.000000,168.000000,0.000000,0.000000,'80446','2026-09-18 09:54:13','80446','2026-09-18 09:54:13',_binary '\0',2012),(95,5101,1101,620.000000,620.000000,900.000000,558000.000000,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(96,5101,1102,1080.000000,1080.000000,320.000000,345600.000000,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(97,5102,1103,215.000000,215.000000,4200.000000,903000.000000,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(98,5102,1104,176.000000,176.000000,2600.000000,457600.000000,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(99,5102,1105,158.000000,158.000000,1800.000000,284400.000000,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(100,5103,1108,560.000000,560.000000,2100.000000,1176000.000000,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(101,5104,1109,290.000000,290.000000,1600.000000,464000.000000,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(102,5105,1112,9800.000000,9800.000000,4.000000,39200.000000,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(103,5106,1107,268.000000,268.000000,1300.000000,348400.000000,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(104,5106,1106,235.000000,235.000000,500.000000,117500.000000,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(105,5107,1103,215.000000,215.000000,1500.000000,322500.000000,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(106,5107,1110,72.000000,72.000000,2000.000000,144000.000000,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012);
/*!40000 ALTER TABLE `crm_business_product` ENABLE KEYS */;

--
-- Table structure for table `crm_business_status`
--

DROP TABLE IF EXISTS `crm_business_status`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_business_status` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `type_id` bigint NOT NULL COMMENT '状态类型编号',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态类型名',
  `percent` decimal(24,6) NOT NULL COMMENT '赢单率',
  `sort` int NOT NULL DEFAULT '1' COMMENT '排序',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=46 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 商机状态表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_business_status`
--

/*!40000 ALTER TABLE `crm_business_status` DISABLE KEYS */;
INSERT INTO `crm_business_status` VALUES (4,3,'小状态',10.000000,1,'1','2023-11-30 12:18:42','','2024-02-21 12:30:33',1,_binary '\0'),(5,4,'吃饭',20.000000,0,'','2024-02-21 12:54:57','','2024-02-21 13:49:11',1,_binary '\0'),(6,4,'睡觉',30.000000,1,'','2024-02-21 12:54:57','','2024-02-21 13:49:11',1,_binary '\0'),(7,5,'',10.000000,0,'','2024-02-21 12:56:49','','2024-02-21 12:57:36',1,_binary '\0'),(8,4,'阿豆豆',10.000000,2,'','2024-02-21 13:49:11','','2024-02-21 13:49:11',1,_binary '\0'),(9,6,'初步接触',10.000000,0,'','2024-02-23 15:58:25','','2024-02-23 15:58:25',1,_binary '\0'),(10,6,'需求分析',20.000000,1,'','2024-02-23 15:58:25','','2024-02-23 15:58:25',1,_binary '\0'),(11,6,'方案制定',30.000000,2,'','2024-02-23 15:58:25','','2024-02-23 15:58:25',1,_binary '\0'),(30,16,'商务谈判',70.000000,4,'1','2026-09-08 19:56:10','','2026-09-18 11:03:39',2012,_binary '\0'),(31,16,'商机待评估',5.000000,0,'1','2026-09-08 19:56:10','','2026-09-18 11:03:39',2012,_binary '\0'),(32,17,'商务谈判',70.000000,4,'1','2026-09-08 19:56:10','','2026-09-18 11:03:39',2012,_binary '\0'),(33,17,'初步接洽',10.000000,1,'1','2026-09-08 19:56:10','','2026-09-18 11:03:39',2012,_binary '\0'),(34,18,'商机待评估',5.000000,0,'1','2026-09-08 19:56:10','','2026-09-18 11:03:39',2012,_binary '\0'),(35,16,'需求确认',40.000000,2,'1','2026-09-08 19:56:10','','2026-09-18 11:03:39',2012,_binary '\0'),(36,16,'初步接洽',10.000000,1,'1','2026-09-15 11:09:57','','2026-09-18 11:03:39',2012,_binary '\0'),(37,16,'方案评估',60.000000,3,'1','2026-09-15 11:09:57','','2026-09-18 11:03:39',2012,_binary '\0'),(39,17,'需求确认',40.000000,2,'1','2026-09-15 11:09:57','','2026-09-18 11:03:39',2012,_binary '\0'),(40,17,'商机待评估',5.000000,0,'1','2026-09-15 11:09:57','','2026-09-18 11:03:39',2012,_binary '\0'),(41,17,'方案评估',60.000000,3,'1','2026-09-15 11:09:57','','2026-09-18 11:03:39',2012,_binary '\0'),(42,18,'初步接洽',10.000000,1,'1','2026-09-15 11:09:57','','2026-09-18 11:03:39',2012,_binary '\0'),(43,18,'需求确认',40.000000,2,'1','2026-09-15 11:09:57','','2026-09-18 11:03:39',2012,_binary '\0'),(44,18,'方案评估',60.000000,3,'1','2026-09-15 11:09:57','','2026-09-18 11:03:39',2012,_binary '\0'),(45,18,'商务谈判',70.000000,4,'','2026-09-18 11:03:39','','2026-09-18 11:03:39',2012,_binary '\0');
/*!40000 ALTER TABLE `crm_business_status` ENABLE KEYS */;

--
-- Table structure for table `crm_business_status_type`
--

DROP TABLE IF EXISTS `crm_business_status_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_business_status_type` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态组名',
  `dept_ids` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '使用的部门编号',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 商机状态组表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_business_status_type`
--

/*!40000 ALTER TABLE `crm_business_status_type` DISABLE KEYS */;
INSERT INTO `crm_business_status_type` VALUES (3,'大状态','100,101','1','2023-11-30 12:18:22','','2024-02-21 12:34:10',1,_binary '\0'),(4,'吃饭睡觉打豆豆','','1','2024-02-21 20:54:58','1','2024-02-21 21:49:12',1,_binary '\0'),(5,'测试一下','103,105,106,107','1','2024-02-21 20:56:50','1','2024-02-21 20:57:37',1,_binary '\0'),(6,'标准流程','','1','2024-02-23 23:58:26','1','2024-02-23 23:58:26',1,_binary '\0'),(16,'油气管道防腐销售流程','','1','2026-09-08 19:56:10','','2026-09-08 19:56:10',2012,_binary '\0'),(17,'市政保温工程流程','','1','2026-09-08 19:56:10','','2026-09-08 19:56:10',2012,_binary '\0'),(18,'检测技术服务流程','','1','2026-09-08 19:56:10','','2026-09-08 19:56:10',2012,_binary '\0');
/*!40000 ALTER TABLE `crm_business_status_type` ENABLE KEYS */;

--
-- Table structure for table `crm_clue`
--

DROP TABLE IF EXISTS `crm_clue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_clue` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号，主键自增',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '线索名称',
  `customer_type` tinyint NOT NULL DEFAULT '1' COMMENT '客户类别：1=下游客户 2=上游供应商',
  `follow_up_status` bit(1) DEFAULT b'0' COMMENT '跟进状态',
  `follow_up_count` int NOT NULL DEFAULT '0' COMMENT '累计跟进次数',
  `contact_last_time` datetime DEFAULT NULL COMMENT '最后跟进时间',
  `contact_last_content` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最后跟进内容',
  `contact_next_time` datetime DEFAULT NULL COMMENT '下次联系时间',
  `owner_user_id` bigint NOT NULL COMMENT '负责人的用户编号',
  `transform_status` bit(1) DEFAULT b'0' COMMENT '转化状态',
  `customer_id` bigint DEFAULT NULL COMMENT '客户编号',
  `mobile` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `telephone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '电话',
  `qq` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'QQ',
  `wechat` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '微信',
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `area_id` bigint DEFAULT NULL COMMENT '地区编号',
  `detail_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '详细地址',
  `industry_id` int DEFAULT NULL COMMENT '所属行业',
  `level` int DEFAULT NULL COMMENT '客户等级',
  `source` int DEFAULT NULL COMMENT '客户来源',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2111 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 线索表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_clue`
--

/*!40000 ALTER TABLE `crm_clue` DISABLE KEYS */;
INSERT INTO `crm_clue` VALUES (1,'我是线索baba',1,NULL,0,NULL,'测试一下内容','2024-01-22 00:00:00',1,NULL,NULL,'18818260278','021-88288283',NULL,NULL,'76853423@qq.com',NULL,'某某小区',1,1,1,NULL,'1','2024-01-22 09:38:05','1','2026-09-11 16:51:17',_binary '\0',1),(2,'测试线索啊',1,_binary '',1,'2024-02-19 18:56:35',NULL,'2024-02-22 00:00:00',103,_binary '',5,'15601691388','15601691389','8888777','wang-server','87684833@qq.com',130102,'某个测试地址',3,2,1,'写个备注','1','2024-02-19 18:56:35','1','2026-09-18 17:28:50',_binary '\0',1),(3,'测试一下',1,_binary '\0',0,'2024-02-19 21:02:00',NULL,NULL,1,_binary '',6,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,'1','2024-02-19 21:02:00','1','2026-09-11 16:51:17',_binary '',1),(4,'哈哈哈哈的线索',1,_binary '',1,'2024-02-19 21:38:16',NULL,NULL,1,_binary '',7,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'1','2024-02-19 21:38:16','1','2026-09-18 17:28:50',_binary '\0',1),(5,'再来一个线索',1,_binary '\0',0,'2024-02-19 22:16:35',NULL,NULL,1,_binary '',8,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'1','2024-02-19 22:16:35','1','2026-09-11 16:51:17',_binary '\0',1),(6,'线索一个',1,_binary '\0',0,'2024-02-19 22:17:08',NULL,NULL,1,_binary '',9,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'1','2024-02-19 22:17:08','1','2026-09-11 16:51:17',_binary '\0',1),(7,'信线索',1,_binary '\0',0,'2024-02-19 22:24:30',NULL,NULL,1,_binary '',10,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'1','2024-02-19 22:24:30','1','2026-09-11 16:51:17',_binary '\0',1),(8,'演示线索',1,_binary '\0',0,NULL,NULL,NULL,1,_binary '',16,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'1','2024-02-23 21:04:05','1','2026-09-11 16:51:17',_binary '\0',1),(9,'10.2线索',1,_binary '',1,'2025-09-24 09:42:49','2222','2025-09-24 09:42:45',1,_binary '\0',NULL,'18818260285',NULL,NULL,NULL,NULL,110101,NULL,NULL,NULL,2,NULL,'1','2024-10-02 09:20:39','1','2026-09-18 17:28:50',_binary '\0',1),(10,'陕西省西安市莲湖区桃园路街道',1,_binary '\0',0,NULL,NULL,NULL,80446,_binary '\0',NULL,'15934835362',NULL,NULL,NULL,NULL,NULL,'陕西省西安市莲湖区桃园路街道',NULL,NULL,2,NULL,'80446','2026-09-04 14:46:25','80446','2026-09-11 16:51:17',_binary '',2012),(11,'西安热力工程有限公司',1,_binary '\0',0,NULL,NULL,'2026-09-08 19:24:53',80932,_binary '\0',NULL,'13652487912','','2745196328',NULL,'zhangjianshe@heat-power.com',NULL,'陕西省西安市未央区凤城五路热力产业园8号楼',102,2,8,NULL,'80446','2026-09-08 19:25:04','80446','2026-09-11 16:51:17',_binary '',2012),(12,'西安热力工程有限公司',1,_binary '\0',0,NULL,NULL,NULL,80932,_binary '\0',NULL,'13652487912','','2745196328',NULL,'zhangjianshe@heat-power.com',NULL,'陕西省西安市未央区凤城五路热力产业园8号楼',102,2,8,NULL,'80446','2026-09-08 19:26:56','80446','2026-09-11 16:51:17',_binary '',2012),(13,'十五',1,_binary '\0',0,NULL,NULL,NULL,80446,_binary '\0',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,'80446','2026-09-08 19:27:31','80446','2026-09-11 16:51:17',_binary '',2012),(2001,'长庆油田第五采油厂集输管线防腐项目',1,_binary '',1,'2026-08-20 00:00:00','已提交防腐方案，进入商务对接','2026-09-10 00:00:00',80939,_binary '',3001,'13891112201','0917-3820001',NULL,'13891112201','lead2001@txgy-crm.com',NULL,'陕西省庆阳市西峰区 长庆油田第五采油厂',1,2,1,'由官网平台线索转化','1','2026-09-08 19:56:10','1','2026-09-18 17:28:50',_binary '\0',2012),(2002,'延长油田靖边采油厂油管防腐采购',1,_binary '',0,'2026-08-25 00:00:00','现场勘查完成，等待招标公告','2026-09-08 00:00:00',80939,_binary '',3002,'13891112202','0912-4611002',NULL,'13891112202','lead2002@txgy-crm.com',NULL,'陕西省榆林市靖边县 靖边采油厂物资供应站',1,2,3,'老客户转介绍','1','2026-09-08 19:56:10','1','2026-09-11 16:51:17',_binary '\0',2012),(2003,'延安市热力公司供热管网保温改造',1,_binary '',0,'2026-08-28 00:00:00','初步接洽，介绍聚氨酯保温管产品','2026-09-12 00:00:00',80940,_binary '\0',NULL,'13991113303','0911-2338003',NULL,'13991113303','lead2003@txgy-crm.com',NULL,'陕西省延安市宝塔区 延安市热力公司',5,3,5,'市政项目','1','2026-09-08 19:56:10','1','2026-09-11 16:51:17',_binary '\0',2012),(2004,'中石油管道局西部管道防腐入围',1,_binary '\0',0,NULL,'无','2026-09-15 00:00:00',80938,_binary '\0',NULL,'13801114404','010-59986004',NULL,'13801114404','lead2004@txgy-crm.com',NULL,'陕西省西安市未央区 中石油管道局西安分公司',3,1,1,'招投标平台线索，待跟进','1','2026-09-08 19:56:10','1','2026-09-11 16:51:17',_binary '\0',2012),(2005,'长庆油田采气二厂集气站保温保冷',1,_binary '',0,'2026-09-01 00:00:00','电话沟通，索取产品资料','2026-09-09 00:00:00',80941,_binary '\0',NULL,'13891115505','0917-8820005',NULL,'13891115505','lead2005@txgy-crm.com',NULL,'陕西省榆林市横山区 长庆油田采气二厂',2,2,7,'','1','2026-09-08 19:56:10','1','2026-09-11 16:51:17',_binary '\0',2012),(2006,'延长石油延安炼油厂管道检测服务',1,_binary '',0,'2026-08-18 00:00:00','技术交流，确定漏磁内检测需求','2026-09-06 00:00:00',80945,_binary '',3005,'13991116606','0911-3352006',NULL,'13991116606','lead2006@txgy-crm.com',NULL,'陕西省延安市宝塔区 延长石油延安炼油厂',4,2,4,'技术服务线索','1','2026-09-08 19:56:10','1','2026-09-11 16:51:17',_binary '\0',2012),(2007,'延安新区市政供水管网工程',1,_binary '\0',0,NULL,'无','2026-09-18 00:00:00',80942,_binary '\0',NULL,'13609117707','0911-8100007',NULL,'13609117707','lead2007@txgy-crm.com',NULL,'陕西省延安市新区 延安新区管委会建设局',5,4,4,'平台咨询，潜在客户','1','2026-09-08 19:56:10','1','2026-09-11 16:51:17',_binary '\0',2012),(2008,'神华煤化工厂区工艺管线防腐',1,_binary '',0,'2026-09-02 00:00:00','展会认识，寄送样册','2026-09-14 00:00:00',80940,_binary '\0',NULL,'13704118808','0912-3390008',NULL,'13704118808','lead2008@txgy-crm.com',NULL,'陕西省榆林市榆阳区 国能榆林化工有限公司',6,3,2,'行业展会','1','2026-09-08 19:56:10','1','2026-09-11 16:51:17',_binary '\0',2012),(2009,'西安热力工程有限公司',1,_binary '\0',0,NULL,NULL,NULL,80446,_binary '\0',NULL,'13652487912',NULL,'2745196328',NULL,'zhangjianshe@heat-power.com',NULL,'陕西省西安市未央区凤城五路热力产业园8号楼',102,NULL,3,NULL,'80446','2026-09-09 09:44:38','80446','2026-09-11 16:51:17',_binary '',2012),(2010,'西安热力工程有限公司',1,_binary '\0',0,NULL,NULL,NULL,80453,_binary '\0',NULL,'13652487912',NULL,'2745196328',NULL,'zhangjianshe@heat-power.com',NULL,'陕西省西安市未央区凤城五路热力产业园8号楼',102,2,6,NULL,'80446','2026-09-09 09:54:09','80446','2026-09-11 16:51:17',_binary '',2012),(2011,'111',1,_binary '\0',0,NULL,NULL,'2026-09-09 10:11:05',80933,_binary '\0',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,4,NULL,'80446','2026-09-09 10:11:08','80446','2026-09-12 20:41:58',_binary '',2012),(2012,'中石化管道储运公司',1,_binary '\0',0,NULL,NULL,NULL,80446,_binary '\0',NULL,'13800138001','010-66668888','123456','txgy_pipe','zhang@example.com',NULL,'北京市朝阳区建国路88号',3,1,4,'用例01 完整字段-预期新增成功','80446','2026-09-11 10:59:10','80446','2026-09-11 16:51:17',_binary '',2012),(2013,'华南新能源科技有限公司',1,_binary '\0',0,NULL,NULL,NULL,80446,_binary '\0',NULL,'13800138002',NULL,NULL,NULL,NULL,NULL,'广东省深圳市南山区科技园南路1号',104,2,2,'用例02 部分字段-预期新增成功','80446','2026-09-11 10:59:10','80446','2026-09-11 16:51:17',_binary '',2012),(2014,'西北市政供热集团',1,_binary '\0',0,NULL,NULL,NULL,80446,_binary '\0',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'甘肃省兰州市城关区',5,3,8,'用例03 无手机号-预期新增成功','80446','2026-09-11 10:59:10','80446','2026-09-11 16:51:17',_binary '',2012),(2015,'我是线索baba',1,_binary '\0',0,NULL,NULL,NULL,80446,_binary '\0',NULL,'18818260278',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'用例04 与库中已有线索同名且同手机-预期重复提示','80446','2026-09-11 10:59:10','80446','2026-09-11 16:51:17',_binary '',2012),(2016,'批内重复测试有限公司',1,_binary '\0',0,NULL,NULL,NULL,80446,_binary '\0',NULL,'13800138005',NULL,NULL,NULL,NULL,NULL,'河北省廊坊市',202,4,3,'用例05 批内同名第1条-预期新增成功','80446','2026-09-11 10:59:10','80446','2026-09-11 16:51:17',_binary '',2012),(2017,'异常手机号测试公司',1,_binary '\0',0,NULL,NULL,NULL,80446,_binary '\0',NULL,'12345',NULL,NULL,NULL,NULL,NULL,'江苏省南京市',107,4,6,'用例08 手机号非法-观察是否校验','80446','2026-09-11 10:59:10','80446','2026-09-11 16:51:17',_binary '',2012),(2018,'特殊字符「测试」&《公司》',1,_binary '\0',0,NULL,NULL,NULL,80446,_binary '\0',NULL,'13800138009',NULL,NULL,NULL,NULL,NULL,'浙江省杭州市西湖区',106,1,6,'用例09 特殊字符名称-预期新增成功','80446','2026-09-11 10:59:10','80446','2026-09-11 16:51:17',_binary '',2012),(2019,'超长文本测试公司',1,_binary '\0',0,NULL,NULL,NULL,80446,_binary '\0',NULL,'13800138010','010-12345678',NULL,NULL,NULL,NULL,'内蒙古自治区呼和浩特市赛罕区某某路某某号某某大厦某某层某某室仅供参考',105,2,10,'用例10 超长地址-观察是否截断','80446','2026-09-11 10:59:10','80446','2026-09-11 16:51:17',_binary '',2012),(2020,'西安热力工程有限公司',1,_binary '\0',0,NULL,NULL,'2026-09-12 20:41:10',80446,_binary '\0',NULL,'13652487912',NULL,'2745196328',NULL,'zhangjianshe@heat-power.com',NULL,'陕西省西安市未央区凤城五路热力产业园8号楼',105,2,3,NULL,'80446','2026-09-12 20:41:26','80446','2026-09-12 20:45:29',_binary '',2012),(2021,'陕西延长石油（集团）有限责任公司',2,_binary '\0',0,NULL,NULL,'2026-09-12 20:47:09',80933,_binary '\0',NULL,NULL,'029-88899517',NULL,NULL,'ycsyb.bjb@163.com',NULL,'陕西省西安市唐延路61号延长石油科研中心',101,1,6,NULL,'80446','2026-09-12 20:47:19','80446','2026-09-12 21:01:57',_binary '',2012),(2022,'西安热力工程有限公司',2,_binary '\0',0,NULL,NULL,NULL,80446,_binary '\0',NULL,'13652487912',NULL,'2745196328',NULL,'zhangjianshe@heat-power.com',NULL,'陕西省西安市未央区凤城五路热力产业园8号楼',NULL,NULL,1,NULL,'80446','2026-09-12 21:02:33','80446','2026-09-12 21:14:01',_binary '',2012),(2023,'西安热力工程有限公司',2,_binary '\0',0,NULL,NULL,NULL,80446,_binary '\0',NULL,'13652487912',NULL,'2745196328',NULL,'zhangjianshe@heat-power.com',NULL,'陕西省西安市未央区凤城五路热力产业园8号楼',NULL,NULL,8,NULL,'80446','2026-09-14 09:53:06','80446','2026-09-14 10:48:19',_binary '',2012),(2024,'西安热力工程有限公司',2,_binary '\0',0,NULL,NULL,'2026-09-14 14:01:23',80446,_binary '\0',NULL,'13652487912',NULL,'2745196328',NULL,'zhangjianshe@heat-power.com',NULL,'陕西省西安市未央区凤城五路热力产业园8号楼',NULL,NULL,3,NULL,'80446','2026-09-14 14:01:32','80446','2026-09-14 14:08:06',_binary '',2012),(2025,'陕西延长石油（集团）有限责任公司',2,_binary '\0',0,NULL,NULL,'2026-09-14 14:05:56',80933,_binary '\0',NULL,NULL,'029-88899517',NULL,NULL,'ycsyb.bjb@163.com',NULL,'陕西省西安市唐延路61号延长石油科研中心',NULL,NULL,6,NULL,'80446','2026-09-14 14:06:10','80446','2026-09-14 14:08:12',_binary '',2012),(2026,'西安热力工程有限公司',2,_binary '\0',0,NULL,NULL,'2026-09-15 08:27:11',80446,_binary '\0',NULL,'13652487912',NULL,'2745196328',NULL,'zhangjianshe@heat-power.com',NULL,'陕西省西安市未央区凤城五路热力产业园8号楼',NULL,2,3,NULL,'80446','2026-09-15 08:27:15','80446','2026-09-15 09:55:34',_binary '',2012),(2027,'陕西延长石油（集团）有限责任公司',2,_binary '\0',0,NULL,NULL,'2026-09-15 08:29:42',80446,_binary '\0',NULL,NULL,'029-88899517',NULL,NULL,'ycsyb.bjb@163.com',NULL,'陕西省西安市唐延路61号延长石油科研中心',NULL,1,3,NULL,'80446','2026-09-15 08:29:43','80446','2026-09-15 08:29:55',_binary '',2012),(2028,'陕西延长石油（集团）有限责任公司',2,_binary '\0',0,NULL,NULL,'2026-09-15 08:30:18',80933,_binary '\0',NULL,NULL,'029-88899517',NULL,NULL,'ycsyb.bjb@163.com',NULL,'陕西省西安市唐延路61号延长石油科研中心',NULL,NULL,6,NULL,'80446','2026-09-15 08:30:24','80446','2026-09-15 08:30:24',_binary '\0',2012),(2029,'西安热力工程有限公司',1,_binary '\0',0,NULL,NULL,NULL,80446,_binary '',3033,'13652487912',NULL,'2745196328',NULL,'zhangjianshe@heat-power.com',NULL,'陕西省西安市未央区凤城五路热力产业园8号楼',102,NULL,2,NULL,'80446','2026-09-15 10:07:02','80446','2026-09-15 10:07:07',_binary '\0',2012),(2101,'长庆油田第八采油厂集输管线防腐项目',1,_binary '',1,'2026-08-22 00:00:00','已完成技术交流，提交防腐报价','2026-09-12 00:00:00',80446,_binary '',3101,'13891113301','0917-3851001',NULL,'13891113301','lead2101@txgy-crm.com',NULL,'长庆油田第八采油厂',1,2,1,'招投标平台线索转化','1','2026-09-18 10:59:01','80446','2026-09-20 09:18:40',_binary '\0',2012),(2102,'延长油田定边采油厂油管防腐采购',1,_binary '',0,'2026-08-26 00:00:00','现场勘查完成，等待招标公告','2026-09-10 00:00:00',80446,_binary '',3102,'13891113302','0912-4621002',NULL,'13891113302','lead2102@txgy-crm.com',NULL,'定边采油厂物资供应站',1,2,3,'老客户转介绍','1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(2103,'西安市热力集团供热管网保温改造',1,_binary '',0,'2026-08-29 00:00:00','初步接洽，介绍聚氨酯保温管产品','2026-09-14 00:00:00',80446,_binary '\0',NULL,'13991114403','029-88610003',NULL,'13991114403','lead2103@txgy-crm.com',NULL,'西安市热力集团有限责任公司',5,3,5,'市政项目','1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(2104,'中石化华北油气分公司防腐入围',1,_binary '\0',0,NULL,'无','2026-09-16 00:00:00',80446,_binary '\0',NULL,'13801117706','010-59987006',NULL,'13801117706','lead2104@txgy-crm.com',NULL,'中石化华北油气分公司西安基地',3,1,1,'招投标平台线索，待跟进','1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(2105,'长庆油田第三采气厂集气站保冷',1,_binary '',0,'2026-09-02 00:00:00','电话沟通，索取产品资料','2026-09-11 00:00:00',80446,_binary '\0',NULL,'13891115504','0917-8830004',NULL,'13891115504','lead2105@txgy-crm.com',NULL,'长庆油田第三采气厂',2,2,7,NULL,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(2106,'陕西省天然气公司管道内检测服务',1,_binary '',0,'2026-08-19 00:00:00','技术交流，确定超声内检测需求','2026-09-08 00:00:00',80446,_binary '',3105,'13991116605','029-86520005',NULL,'13991116605','lead2106@txgy-crm.com',NULL,'陕西省天然气股份有限公司',3,2,4,'技术服务线索','1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(2107,'铜川市新区供水管道防腐工程',1,_binary '\0',0,NULL,'无','2026-09-20 00:00:00',80446,_binary '\0',NULL,'13609110009','0919-3180009',NULL,'13609110009','lead2107@txgy-crm.com',NULL,'铜川市新区住房和城乡建设局',5,4,4,'平台咨询，潜在客户','1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(2108,'中煤榆林能源化工工艺管线保温',1,_binary '',0,'2026-09-03 00:00:00','展会认识，寄送产品样册','2026-09-15 00:00:00',80446,_binary '\0',NULL,'13704118807','0912-3391007',NULL,'13704118807','lead2108@txgy-crm.com',NULL,'中煤榆林能源化工有限公司',6,3,2,'行业展会','1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(2109,'延长石油榆林炼油厂储罐防腐',1,_binary '',0,'2026-09-04 00:00:00','电话陌拜，了解年度防腐检修计划','2026-09-17 00:00:00',80446,_binary '\0',NULL,'13891119908','0912-3880008',NULL,'13891119908','lead2109@txgy-crm.com',NULL,'延长石油集团榆林炼油厂',4,2,6,'电话陌拜线索','1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(2110,'潞安煤基厂区工艺管线防腐保温改造',1,_binary '\0',0,NULL,NULL,NULL,80446,_binary '',3111,'13835110001','0355-5911001',NULL,NULL,'lahg@sx-luan.com',NULL,'山西省长治市潞城区 潞安煤基清洁能源有限公司 设备管理部',102,1,4,'2026 石油石化装备展获取，关注厂区管线防腐与保温','80446','2026-09-18 17:12:07','80446','2026-09-18 17:12:40',_binary '\0',2012);
/*!40000 ALTER TABLE `crm_clue` ENABLE KEYS */;

--
-- Table structure for table `crm_contact`
--

DROP TABLE IF EXISTS `crm_contact`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_contact` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系人名称',
  `customer_id` bigint DEFAULT NULL COMMENT '客户编号',
  `contact_last_time` datetime DEFAULT NULL COMMENT '最后跟进时间',
  `contact_last_content` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最后跟进内容',
  `contact_next_time` datetime DEFAULT NULL COMMENT '下次联系时间',
  `owner_user_id` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '负责人用户编号',
  `mobile` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `telephone` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '电话',
  `email` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '电子邮箱',
  `qq` int DEFAULT NULL,
  `wechat` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `area_id` bigint DEFAULT NULL COMMENT '地区',
  `detail_address` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '地址',
  `sex` int DEFAULT NULL COMMENT '性别',
  `master` bit(1) DEFAULT NULL COMMENT '是否关键决策人',
  `parent_id` bigint DEFAULT NULL COMMENT '直系上属',
  `post` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '职务',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_owner_create_time` (`owner_user_id`,`create_time`) USING BTREE,
  KEY `idx_customer_id` (`customer_id`) USING BTREE,
  KEY `idx_mobile` (`mobile`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4114 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 联系人';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_contact`
--

/*!40000 ALTER TABLE `crm_contact` DISABLE KEYS */;
INSERT INTO `crm_contact` VALUES (9,'土豆',2,'2024-01-17 00:00:00',NULL,'2024-01-26 23:10:11',NULL,'15601691300','18818260277',NULL,NULL,NULL,120101,'1111',1,_binary '\0',11,'CEO',NULL,'1','2023-11-29 14:02:45','1','2024-01-03 23:17:17',_binary '\0',1),(10,'番茄',2,NULL,NULL,NULL,NULL,'15601691301',NULL,NULL,NULL,NULL,NULL,NULL,NULL,_binary '\0',9,'CTO',NULL,'1','2023-11-29 14:02:45','1','2023-11-29 14:02:45',_binary '\0',1),(11,'深夜牛排',3,'2024-01-22 09:35:23','吃饭、睡觉、打逗逗','2023-12-31 00:00:00',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,_binary '\0',NULL,NULL,NULL,'1','2024-01-03 23:16:54','1','2024-02-03 01:30:51',_binary '\0',1),(12,'测试名字',12,NULL,NULL,'2025-09-02 00:00:00',NULL,'15601691300','15601691300','312321@qq.com',32,'321312',110101,'321',1,_binary '\0',12,'321321','321321','1','2024-02-20 21:10:57','1','2025-09-30 22:07:57',_binary '\0',1),(13,'AIYA',NULL,'2024-02-20 23:20:19','啦啦啦','2024-02-07 00:00:00','112',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,_binary '\0',NULL,NULL,NULL,'1','2024-02-20 23:18:16','1','2024-02-24 11:43:05',_binary '\0',1),(14,'天哪',14,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,_binary '\0',NULL,NULL,NULL,'1','2024-02-20 23:27:33','1','2024-02-20 23:27:33',_binary '\0',1),(15,'测试一个关联客户',14,NULL,NULL,NULL,NULL,'15601691300',NULL,NULL,NULL,NULL,NULL,NULL,NULL,_binary '\0',NULL,NULL,NULL,'1','2024-02-22 09:08:12','1','2024-02-22 09:19:29',_binary '\0',1),(16,'演示联系人',17,NULL,NULL,'2025-09-11 22:02:16',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,_binary '\0',NULL,NULL,NULL,'1','2024-02-23 23:16:43','1','2025-09-30 22:02:21',_binary '\0',1),(17,'biubiu',16,'2025-09-30 22:02:21','3123213','2025-09-11 22:02:16',NULL,'18818260278','',NULL,1,NULL,NULL,NULL,NULL,_binary '\0',NULL,'小菜',NULL,'1','2024-02-24 15:30:39','1','2025-09-30 22:02:21',_binary '\0',1),(18,'123',12,NULL,NULL,NULL,'1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,_binary '\0',NULL,NULL,NULL,'1','2025-08-02 19:40:22','1','2025-08-02 19:41:36',_binary '',1),(4001,'刘建国',3001,NULL,NULL,NULL,'80939','13891112211','0917-3820011','ljg@petrochina.com.cn',NULL,NULL,NULL,NULL,NULL,_binary '',NULL,'集输大队副大队长','项目决策人','1','2026-09-08 19:56:10','1','2026-09-08 20:44:38',_binary '\0',2012),(4002,'王芳',3001,NULL,NULL,NULL,'80939','13891112212','0917-3820012','wf@petrochina.com.cn',NULL,NULL,NULL,NULL,NULL,_binary '\0',NULL,'物资采购主管','对接采购','1','2026-09-08 19:56:10','1','2026-09-08 20:44:38',_binary '\0',2012),(4003,'张伟',3002,NULL,NULL,NULL,'80939','13891112221','0912-4611021','zw@yanchang.com',NULL,NULL,NULL,NULL,NULL,_binary '',NULL,'物资供应站站长','招标负责人','1','2026-09-08 19:56:10','1','2026-09-08 20:44:38',_binary '\0',2012),(4004,'李娜',3002,NULL,NULL,NULL,'80939','13891112222','0912-4611022','ln@yanchang.com',NULL,NULL,NULL,NULL,NULL,_binary '\0',NULL,'技术科科长','技术对接','1','2026-09-08 19:56:10','1','2026-09-08 20:44:38',_binary '\0',2012),(4005,'赵永强',3003,NULL,NULL,NULL,'80940','13991113311','0911-2338011','zyq@ya-heating.com',NULL,NULL,NULL,NULL,NULL,_binary '',NULL,'工程建设部部长','保温改造决策','1','2026-09-08 19:56:10','1','2026-09-08 20:44:38',_binary '\0',2012),(4006,'孙浩',3004,NULL,NULL,NULL,'80941','13891115511','0917-8820011','sh@petrochina.com.cn',NULL,NULL,NULL,NULL,NULL,_binary '',NULL,'工程科科长','保温需求对接','1','2026-09-08 19:56:10','1','2026-09-08 20:44:38',_binary '\0',2012),(4007,'钱明理',3005,NULL,NULL,NULL,'80945','13991116611','0911-3352011','qml@yanchang.com',NULL,NULL,NULL,NULL,NULL,_binary '',NULL,'设备管理处处长','检测服务对接','1','2026-09-08 19:56:10','1','2026-09-08 20:44:38',_binary '\0',2012),(4008,'周长海',3006,NULL,NULL,NULL,'80938','13801114411','010-59986011','zch@cnpc.com.cn',NULL,NULL,NULL,NULL,NULL,_binary '',NULL,'物资装备处副处长','入围资格审查对接','1','2026-09-08 19:56:10','1','2026-09-08 20:44:38',_binary '\0',2012),(4009,'吴刚',3007,NULL,NULL,NULL,'80940','13704118811','0912-3390011','wg@ceic.com',NULL,NULL,NULL,NULL,NULL,_binary '',NULL,'生产技术部经理','防腐技术对接','1','2026-09-08 19:56:10','1','2026-09-08 20:44:38',_binary '\0',2012),(4010,'西安热力工程有限公司',3008,NULL,NULL,NULL,'80446','13652487912',NULL,'zhangjianshe@heat-power.com',NULL,NULL,NULL,NULL,NULL,_binary '\0',NULL,NULL,NULL,'80446','2026-09-09 09:53:14','80446','2026-09-09 09:53:19',_binary '',2012),(4011,'西安热力工程有限公司',3006,NULL,NULL,NULL,'80446','13652487912',NULL,'zhangjianshe@heat-power.com',NULL,NULL,NULL,NULL,NULL,_binary '\0',NULL,NULL,NULL,'80446','2026-09-09 09:57:06','80446','2026-09-09 09:58:47',_binary '',2012),(4012,'西安热力工程有限公司',3004,NULL,NULL,NULL,'80446','13652487912',NULL,'zhangjianshe@heat-power.com',NULL,NULL,NULL,NULL,NULL,_binary '\0',NULL,NULL,NULL,'80446','2026-09-09 10:37:27','80446','2026-09-09 10:38:11',_binary '',2012),(4013,'西安热力工程有限公司',3003,NULL,NULL,NULL,'80446','13652487912','','zhangjianshe@heat-power.com',NULL,NULL,NULL,NULL,NULL,_binary '\0',NULL,NULL,NULL,'80446','2026-09-09 10:38:49','80446','2026-09-09 10:38:55',_binary '',2012),(4014,'杀杀杀',3027,NULL,NULL,NULL,'80446','13652487912',NULL,'zhangjianshe@heat-power.com',NULL,NULL,NULL,NULL,NULL,_binary '\0',NULL,NULL,NULL,'80446','2026-09-12 21:03:49','80446','2026-09-12 21:13:50',_binary '',2012),(4015,'张建设',3031,NULL,NULL,'2026-09-15 08:41:40','80446','13652487912',NULL,'zhangjianshe@heat-power.com',NULL,NULL,NULL,NULL,NULL,_binary '\0',NULL,NULL,NULL,'80446','2026-09-15 08:41:43','80446','2026-09-15 09:55:48',_binary '',2012),(4101,'王建国',3101,NULL,NULL,NULL,'80446','13891113311','0917-3851011','wjg@petrochina.com.cn',NULL,NULL,NULL,NULL,NULL,_binary '',NULL,'集输大队大队长','项目决策人','1','2026-09-18 10:59:01',NULL,'2026-09-18 10:59:01',_binary '\0',2012),(4102,'刘敏',3101,NULL,NULL,NULL,'80446','13891113312','0917-3851012','lm@petrochina.com.cn',NULL,NULL,NULL,NULL,NULL,_binary '\0',NULL,'物资采购主管','对接采购流程','1','2026-09-18 10:59:01',NULL,'2026-09-18 10:59:01',_binary '\0',2012),(4103,'陈永刚',3102,NULL,NULL,NULL,'80446','13891113321','0912-4621021','cyg@yanchang.com',NULL,NULL,NULL,NULL,NULL,_binary '',NULL,'物资供应站站长','招标负责人','1','2026-09-18 10:59:01',NULL,'2026-09-18 10:59:01',_binary '\0',2012),(4104,'郭丽',3102,NULL,NULL,NULL,'80446','13891113322','0912-4621022','gl@yanchang.com',NULL,NULL,NULL,NULL,NULL,_binary '\0',NULL,'技术科科长','技术对接','1','2026-09-18 10:59:01',NULL,'2026-09-18 10:59:01',_binary '\0',2012),(4105,'何志强',3103,NULL,NULL,NULL,'80446','13991114411','029-88610011','hzq@xa-heating.com',NULL,NULL,NULL,NULL,NULL,_binary '',NULL,'工程建设部部长','保温改造决策','1','2026-09-18 10:59:01',NULL,'2026-09-18 10:59:01',_binary '\0',2012),(4106,'崔磊',3104,NULL,NULL,NULL,'80446','13891115511','0917-8830011','cl@petrochina.com.cn',NULL,NULL,NULL,NULL,NULL,_binary '',NULL,'工程科科长','保冷需求对接','1','2026-09-18 10:59:01',NULL,'2026-09-18 10:59:01',_binary '\0',2012),(4107,'白晓东',3105,NULL,NULL,NULL,'80446','13991116611','029-86520011','bxd@sxgas.com',NULL,NULL,NULL,NULL,NULL,_binary '',NULL,'管道管理处处长','检测服务对接','1','2026-09-18 10:59:01',NULL,'2026-09-18 10:59:01',_binary '\0',2012),(4108,'冯海军',3106,NULL,NULL,NULL,'80446','13801117711','010-59987011','fhj@sinopec.com',NULL,NULL,NULL,NULL,NULL,_binary '',NULL,'物资装备中心副主任','入围资格审查对接','1','2026-09-18 10:59:01',NULL,'2026-09-18 10:59:01',_binary '\0',2012),(4109,'田伟',3107,NULL,NULL,NULL,'80446','13704118811','0912-3391011','tw@chinacoal.com',NULL,NULL,NULL,NULL,NULL,_binary '',NULL,'生产技术部经理','保温技术对接','1','2026-09-18 10:59:01',NULL,'2026-09-18 10:59:01',_binary '\0',2012),(4110,'薛明',3108,NULL,NULL,NULL,'80446','13891119911','0912-3880011','xm@yanchang.com',NULL,NULL,NULL,NULL,NULL,_binary '',NULL,'设备管理部主管','储罐防腐对接','1','2026-09-18 10:59:01',NULL,'2026-09-18 10:59:01',_binary '\0',2012),(4111,'王海涛',3111,NULL,NULL,NULL,'80446','13835110011','0355-5911011','wanght@sx-luan.com',NULL,NULL,NULL,NULL,NULL,_binary '',NULL,'设备管理部部长',NULL,'80446','2026-09-18 17:18:46','80446','2026-09-18 17:42:17',_binary '',2012),(4112,'了建国',3101,NULL,NULL,NULL,'80446','13891110311',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'集输大队大队长',NULL,'80446','2026-09-19 14:28:15','80446','2026-09-19 14:28:28',_binary '',2012),(4113,'增加敏',3101,NULL,NULL,NULL,'80446','13891116312',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'物资采购主管',NULL,'80446','2026-09-19 14:28:15','80446','2026-09-19 14:28:25',_binary '',2012);
/*!40000 ALTER TABLE `crm_contact` ENABLE KEYS */;

--
-- Table structure for table `crm_contact_business`
--

DROP TABLE IF EXISTS `crm_contact_business`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_contact_business` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
  `contact_id` int DEFAULT NULL COMMENT '联系人id',
  `business_id` int DEFAULT NULL COMMENT '商机id',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=39 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 联系人商机关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_contact_business`
--

/*!40000 ALTER TABLE `crm_contact_business` DISABLE KEYS */;
INSERT INTO `crm_contact_business` VALUES (32,15,7,'1','2024-02-22 09:08:12','1','2024-02-22 09:08:12',_binary '\0',1),(33,15,9,'1','2024-02-22 09:32:09','1','2024-02-22 09:32:09',_binary '\0',1),(34,16,13,'1','2024-02-24 00:23:03','1','2024-02-24 00:23:03',_binary '\0',1),(35,17,14,'1','2024-02-24 19:28:51','1','2024-02-24 11:31:02',_binary '',1),(36,17,14,'1','2024-02-24 19:31:07','1','2025-09-27 07:42:03',_binary '',1),(37,17,14,'1','2025-09-27 15:42:06','1','2025-09-27 07:42:08',_binary '',1),(38,17,14,'1','2025-09-27 15:43:01','1','2025-09-27 15:43:01',_binary '\0',1);
/*!40000 ALTER TABLE `crm_contact_business` ENABLE KEYS */;

--
-- Table structure for table `crm_contract`
--

DROP TABLE IF EXISTS `crm_contract`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_contract` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号，主键自增',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '合同名称',
  `no` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '合同编号',
  `customer_id` bigint NOT NULL COMMENT '客户编号',
  `business_id` bigint DEFAULT NULL COMMENT '商机编号',
  `contact_last_time` datetime DEFAULT NULL COMMENT '最后跟进时间',
  `owner_user_id` bigint DEFAULT NULL COMMENT '负责人的用户编号',
  `process_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工作流编号',
  `audit_status` tinyint NOT NULL DEFAULT '0' COMMENT '审批状态',
  `order_date` datetime DEFAULT NULL COMMENT '下单日期',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `total_product_price` decimal(24,6) DEFAULT NULL COMMENT '产品总金额',
  `discount_percent` decimal(24,6) DEFAULT NULL COMMENT '整单折扣',
  `total_price` decimal(10,2) DEFAULT NULL COMMENT '合同总金额',
  `sign_contact_id` bigint DEFAULT NULL COMMENT '联系人编号',
  `sign_user_id` bigint DEFAULT NULL COMMENT '公司签约人',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  `template_type` tinyint DEFAULT '1' COMMENT '合同模板类型：1=采购合同，2=购销合同',
  `sign_place` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '签订地点',
  `sign_date` datetime DEFAULT NULL COMMENT '签订日期',
  `project_no` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '项目编号',
  `project_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '采购项目名称/建设项目名称',
  `project_area` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '地名（购销合同）',
  `attachment_copies` int DEFAULT NULL COMMENT '附件份数',
  `attachment_pages` int DEFAULT NULL COMMENT '附件页数',
  `contract_copies` int DEFAULT NULL COMMENT '合同份数',
  `gpc_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '政府采购中心名称',
  `quality_standard` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '国家（行业）标准',
  `sample_days` int DEFAULT NULL COMMENT '送样天数',
  `delivery_days` int DEFAULT NULL COMMENT '交货期限天数',
  `delivery_arrive_days` int DEFAULT NULL COMMENT '交货到指定地点天数',
  `install_days` int DEFAULT NULL COMMENT '安装调试验收天数',
  `delivery_deadline` datetime DEFAULT NULL COMMENT '最迟完成安装调试验收日期',
  `first_accept_days` int DEFAULT NULL COMMENT '初步验收天数',
  `trial_days` int DEFAULT NULL COMMENT '试用期天数',
  `final_accept_days` int DEFAULT NULL COMMENT '最终验收天数',
  `auto_accept_days` int DEFAULT NULL COMMENT '甲方不验收视同合格天数',
  `max_repair_times` int DEFAULT NULL COMMENT '维修次数上限（达到可退货）',
  `prepay_amount` decimal(20,2) DEFAULT NULL COMMENT '首付款金额',
  `prepay_days` int DEFAULT NULL COMMENT '首付支付天数',
  `prepay_ratio` decimal(10,2) DEFAULT NULL COMMENT '首付款比例(%)',
  `accept_pay_days` int DEFAULT NULL COMMENT '验收合格后付款天数/工作日',
  `accept_pay_ratio` decimal(10,2) DEFAULT NULL COMMENT '验收款比例(%)',
  `accept_pay_amount` decimal(20,2) DEFAULT NULL COMMENT '验收款金额',
  `deposit_months` int DEFAULT NULL COMMENT '履约保证金月数',
  `deposit_days` int DEFAULT NULL COMMENT '保证金退还天数',
  `deposit_amount` decimal(20,2) DEFAULT NULL COMMENT '保证金金额',
  `deposit_ratio` decimal(10,2) DEFAULT NULL COMMENT '保证金比例(%)',
  `warranty_years` int DEFAULT NULL COMMENT '质保年数',
  `response_hours` int DEFAULT NULL COMMENT '响应到场小时数',
  `repair_hours` int DEFAULT NULL COMMENT '完成维修小时数',
  `repair_times` int DEFAULT NULL COMMENT '质保期内维修次数上限',
  `report_repair_days` int DEFAULT NULL COMMENT '报修无法解决提供代用品天数',
  `party_a_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '甲方单位名称',
  `party_a_legal_person` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '甲方法定代表人',
  `party_a_agent` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '甲方授权代表/授权委托人',
  `party_a_address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '甲方地址',
  `party_a_bank_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '甲方开户银行',
  `party_a_bank_account` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '甲方银行账号',
  `party_a_tel` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '甲方电话',
  `party_a_fax` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '甲方传真',
  `party_b_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '乙方单位名称',
  `party_b_legal_person` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '乙方法定代表人',
  `party_b_agent` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '乙方授权代表/授权委托人',
  `party_b_address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '乙方地址',
  `party_b_bank_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '乙方开户银行',
  `party_b_bank_account` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '乙方银行账号',
  `party_b_tel` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '乙方电话',
  `party_b_fax` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '乙方传真',
  `penalty_terms` text COLLATE utf8mb4_unicode_ci COMMENT '违约责任条款(JSON)',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_owner_audit_id` (`owner_user_id`,`audit_status`,`id`) USING BTREE,
  KEY `idx_end_time_audit` (`end_time`,`audit_status`) USING BTREE,
  KEY `idx_no` (`no`) USING BTREE,
  KEY `idx_customer_id` (`customer_id`) USING BTREE,
  KEY `idx_business_id` (`business_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=6107 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 合同表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_contract`
--

/*!40000 ALTER TABLE `crm_contract` DISABLE KEYS */;
INSERT INTO `crm_contract` VALUES (2,'我是合同','Q100',12,11,'2024-02-19 20:01:17',100,NULL,20,'2024-02-02 00:00:00','2024-02-23 00:00:00','2024-02-28 00:00:00',2.000000,1.000000,200.00,15,1,'测试','1','2024-02-01 13:24:26','1','2024-02-22 13:33:34',_binary '\0',1,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(4,'测试合同','1708617038936',14,9,'2024-02-23 09:30:41',1,'466480fe-d1ed-11ee-a436-8a9af48de719',20,'2024-02-22 00:00:00','2024-02-06 00:00:00','2024-02-13 00:00:00',10010.000000,0.000000,10010.00,14,1,NULL,'1','2024-02-22 23:50:39','1','2024-02-23 12:27:42',_binary '\0',1,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(5,'阿巴阿巴','1708664618781',14,NULL,NULL,1,'e9e87175-d208-11ee-ba3b-5e0431cb568f',20,'2024-02-19 00:00:00',NULL,NULL,0.000000,0.000000,0.00,NULL,NULL,NULL,'1','2024-02-23 13:03:39','1','2024-02-23 13:04:06',_binary '\0',1,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(6,'阿巴','1708668768747',14,NULL,NULL,1,'92b0f9a5-d212-11ee-ba3b-5e0431cb568f',10,'2024-02-19 00:00:00',NULL,NULL,0.000000,0.000000,0.00,NULL,NULL,NULL,'1','2024-02-23 14:12:49','1','2024-02-23 14:12:51',_binary '\0',1,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(7,'hhhh','HT20240223000001',14,NULL,NULL,103,NULL,0,'2024-02-13 00:00:00',NULL,NULL,100.000000,0.000000,100.00,NULL,NULL,NULL,'1','2024-02-23 16:35:12','1','2025-07-14 13:18:53',_binary '\0',1,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(8,'演示合同','HT20240224000001',16,12,NULL,1,'b65ac482-d2a6-11ee-a703-92ff8e2e5a3e',10,'2024-02-07 00:00:00',NULL,NULL,10110.300000,30.000000,7077.21,NULL,NULL,NULL,'1','2024-02-24 07:51:48','1','2024-02-24 00:03:52',_binary '',1,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(9,'演示合同','HT20240224000002',17,13,NULL,1,'f26ee84a-d2a7-11ee-b20a-8adfac90dc14',10,'2024-02-13 00:00:00','2024-02-13 00:00:00','2024-02-06 00:00:00',20020.000000,30.000000,14014.00,16,104,NULL,'1','2024-02-24 08:02:02','1','2024-02-24 00:03:54',_binary '',1,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(10,'演示合同','HT20240224000003',16,12,NULL,1,'44d40c17-d2a8-11ee-b20a-8adfac90dc14',20,'2024-02-07 00:00:00','2024-02-20 00:00:00','2024-02-06 00:00:00',10110.300000,20.000000,8088.24,NULL,1,NULL,'1','2024-02-24 08:04:22','1','2024-02-24 08:07:37',_binary '\0',1,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(11,'哈哈哈哈','HT20240224000004',17,13,NULL,1,'1145eb9b-6d33-11f0-88e1-ca5778fb00b9',20,'2024-02-06 00:00:00',NULL,NULL,20020.000000,20.000000,16016.00,NULL,NULL,NULL,'1','2024-02-24 16:06:37',NULL,'2025-07-30 18:51:01',_binary '\0',1,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(12,'测试过期合同','HT20240224000005',16,14,NULL,1,'1e0ef39c-d2f9-11ee-9d72-262f9d5f4efd',20,'2024-02-15 00:00:00',NULL,'2024-02-25 00:00:00',0.000000,0.000000,0.00,NULL,NULL,NULL,'1','2024-02-24 17:43:06','1','2024-02-24 17:43:13',_binary '\0',1,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(13,'演示回款的合同','HT20240225000001',17,13,NULL,1,'7981141f-d3f3-11ee-853e-2ea58da017f0',20,'2024-02-29 00:00:00','2024-02-12 00:00:00','2024-02-29 00:00:00',20020.000000,20.000000,16016.00,16,1,NULL,'1','2024-02-25 23:35:14','1','2026-09-15 19:08:48',_binary '\0',1,2,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(14,'11','HT20251118000001',17,13,NULL,1,NULL,0,'2025-11-26 19:24:12','2025-10-28 19:23:58','2025-11-15 19:24:01',200.000000,1.000000,198.00,NULL,1,NULL,'1','2025-11-18 19:24:21','1','2025-11-18 19:24:21',_binary '\0',1,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(6001,'靖边采油厂油管防腐采购合同','TXGY-HT-2026-0012',3002,5002,NULL,80939,'BPM-6001',20,'2026-09-05 00:00:00','2026-09-06 00:00:00','2027-03-31 00:00:00',1429000.000000,3.000000,1386130.00,4003,80939,'油管防腐年度采购，分两批交付','1','2026-09-08 19:56:10','1','2026-09-18 16:43:24',_binary '\0',2012,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(6002,'延安炼油厂管道内检测服务合同','TXGY-HT-2026-0015',3005,5005,NULL,80945,'BPM-6002',20,'2026-08-25 00:00:00','2026-08-26 00:00:00','2026-10-31 00:00:00',36000.000000,0.000000,36000.00,4007,80945,'漏磁内检测服务合同','1','2026-09-08 19:56:10','1','2026-09-18 16:43:24',_binary '\0',2012,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(6003,'长庆五厂集输管线3PE防腐供货合同','TXGY-HT-2026-0018',3001,5001,NULL,80939,'BPM-6003',10,'2026-09-12 00:00:00','2026-09-15 00:00:00','2026-12-31 00:00:00',1096000.000000,5.000000,1041200.00,4001,80939,'框架首批供货，审核中','1','2026-09-08 19:56:10','1','2026-09-18 16:43:24',_binary '\0',2012,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(6004,'防腐材料产品购销合同','HT20260912000001',3001,NULL,NULL,80446,NULL,0,'2026-09-11 00:00:00','2026-09-11 00:00:00','2027-09-10 00:00:00',0.000000,0.000000,0.00,NULL,80446,NULL,'80446','2026-09-12 21:16:44','80446','2026-09-12 21:16:44',_binary '\0',2012,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(6005,'防腐材料产品购销合同','HT20260912000002',3001,NULL,NULL,80446,NULL,0,'2026-09-11 00:00:00','2026-09-11 00:00:00','2027-09-10 00:00:00',0.000000,0.000000,0.00,NULL,80446,NULL,'80446','2026-09-12 21:26:11','80446','2026-09-12 21:26:11',_binary '\0',2012,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(6010,'长安石油','HT20260915000004',3007,5012,NULL,80446,NULL,0,'2026-09-15 15:09:49',NULL,NULL,0.000000,4.000000,0.00,NULL,NULL,NULL,'80446','2026-09-15 17:14:48','80446','2026-09-15 17:14:48',_binary '\0',2012,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(6011,'中石油管道局西部管道防腐入围','HT20260915000005',3006,5011,NULL,80446,NULL,0,'2026-09-15 11:02:16',NULL,NULL,0.000000,5.000000,0.00,NULL,NULL,NULL,'80446','2026-09-15 17:15:17','80446','2026-09-15 17:15:17',_binary '\0',2012,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(6012,'购销合同冒烟测试','HT20260915000006',17,NULL,NULL,1,NULL,0,'1970-01-01 08:00:00',NULL,'1970-01-01 08:00:00',20020.000000,0.000000,20020.00,NULL,NULL,'smoke','1','2026-09-15 19:17:03','1','2026-09-15 19:37:14',_binary '',1,2,'成都市','2025-09-15 10:30:00',NULL,'设备采购',NULL,NULL,NULL,4,NULL,NULL,5,30,30,10,'2025-10-15 00:00:00',7,30,7,15,3,6006.00,15,30.00,15,60.00,12012.00,12,15,2002.00,10.00,1,24,48,3,3,'演示客户',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'四川天信防腐保温工程有限公司','王天信','李防腐','成都市武侯区天府大道100号','中国工商银行成都高新支行','4402 2020 0123 4567 890','028-8888 8888','028-8888 8889','{\"sBuyerLateRatio\":0.5,\"sBuyerBreachRatio\":5,\"sSellerLateRatio\":0.5,\"sSellerBreachRatio\":5}'),(6013,'采购合同冒烟测试','HT20260915000007',17,NULL,NULL,1,NULL,0,'1970-01-01 08:00:00',NULL,'1970-01-01 08:00:00',20020.000000,0.000000,20020.00,NULL,NULL,'smoke','1','2026-09-15 19:23:55','1','2026-09-15 19:37:15',_binary '',1,1,'成都市高新区','2025-09-15 10:30:00','TX-2026-001','防腐设备采购',NULL,NULL,NULL,4,'四川','GB/T 9711',5,30,30,10,'2025-10-15 00:00:00',7,30,7,15,3,6006.00,15,30.00,15,60.00,12012.00,12,15,2002.00,10.00,1,24,48,3,3,'四川天信防腐保温工程有限公司','王天信','李防腐','成都市武侯区天府大道100号','中国工商银行成都高新支行','4402 2020 0123 4567 890','028-8888 8888','028-8888 8889','演示客户',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'{\"pBuyerRejectRatio\":5,\"pBuyerOverdueWan\":5,\"pBuyerOverdueDays\":30,\"pSellerQuality\":5,\"pSellerOverdueWan\":5,\"pSellerOverdueDays\":30,\"pSellerCompensate\":10,\"pSellerReplaceDays\":15,\"pSellerTerminate\":10,\"pSellerDefect\":10}'),(6014,'延安炼油厂管道内检测服务报价','HT20260917000001',3005,5014,NULL,80945,NULL,0,'2026-08-22 15:27:33',NULL,NULL,36000.000000,0.000000,36000.00,NULL,NULL,'审核通过已一键转管道内检测服务合同','80446','2026-09-17 15:33:18','80446','2026-09-17 15:33:18',_binary '\0',2012,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'延长石油集团延安炼油厂',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(6017,'靖边采油厂油管防腐年度采购','HT20260917000004',3002,5016,NULL,80446,NULL,0,'2026-09-04 16:26:51',NULL,NULL,1429000.000000,0.000000,1429000.00,NULL,NULL,'审批通过已一键转油管防腐采购合同','80446','2026-09-17 16:30:02','80446','2026-09-17 16:30:02',_binary '\0',2012,2,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'延长油田股份有限公司靖边采油厂',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(6018,'靖边采油厂油管防腐年度采购','HT20260918000001',3002,5016,NULL,80446,NULL,0,'2026-09-04 08:46:08','2026-09-18 08:52:23','2026-10-23 08:52:27',1429000.000000,0.000000,1429000.00,4004,80446,'审核已通过，已一键转油管防腐采购合同','80446','2026-09-18 08:50:11','80446','2026-09-18 08:52:38',_binary '\0',2012,2,'延安大学','2026-09-18 08:51:40',NULL,'油管防腐','延安',1,5,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'延长油田股份有限公司靖边采油厂',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(6101,'定边采油厂油管防腐采购合同','TXGY-HT-2026-0021',3102,5102,NULL,80446,'BPM-6101',20,'2026-09-06 00:00:00','2026-09-08 00:00:00','2027-04-30 00:00:00',1645000.000000,3.000000,1595650.00,4103,NULL,'油管防腐年度采购，分两批交付','1','2026-09-18 10:59:01','','2026-09-18 16:43:24',_binary '\0',2012,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(6102,'陕天然气管道内检测服务合同','TXGY-HT-2026-0024',3105,5105,NULL,80446,'BPM-6102',20,'2026-08-26 00:00:00','2026-08-28 00:00:00','2026-11-30 00:00:00',39200.000000,0.000000,39200.00,4107,NULL,'超声内检测服务合同','1','2026-09-18 10:59:01','','2026-09-18 16:43:24',_binary '\0',2012,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(6103,'长庆八厂集输管线3PE防腐供货合同','TXGY-HT-2026-0027',3101,5101,NULL,80446,'BPM-6103',10,'2026-09-14 00:00:00','2026-09-18 00:00:00','2026-12-31 00:00:00',903600.000000,5.000000,858420.00,4101,NULL,'框架首批供货，审核中','1','2026-09-18 10:59:01','','2026-09-18 16:43:24',_binary '\0',2012,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(6104,'西安热力供热管网保温改造合同','TXGY-HT-2026-0029',3103,5103,NULL,80446,'d5c674fb-b48c-11f1-aa82-9a37f64484c9',20,'2026-09-16 00:00:00','2026-09-20 00:00:00','2027-01-31 00:00:00',1176000.000000,8.000000,1081920.00,4105,NULL,'保温管供货+施工，待提交审核','1','2026-09-18 10:59:01',NULL,'2026-09-20 08:48:16',_binary '\0',2012,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(6105,'长庆八厂集输管线3PE防腐报价单','HT20260918000002',3101,5101,NULL,80446,'f1ea6319-b33e-11f1-a372-ced4ad4f3b4b',20,'2026-09-18 16:56:58',NULL,NULL,903600.000000,5.000000,858420.00,NULL,NULL,'端到端演示','80446','2026-09-18 16:57:15',NULL,'2026-09-18 16:57:38',_binary '\0',2012,2,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'长庆油田分公司第八采油厂',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(6106,'山西潞安煤基清洁能源有限公司报价单','HT20260918000003',3111,5109,NULL,80446,'841d6c1d-b342-11f1-a372-ced4ad4f3b4b',20,'2026-09-18 17:20:22',NULL,NULL,302672.000000,1.000000,299645.28,NULL,NULL,'联系人：基厂区工；地址：潞安煤基厂区','80446','2026-09-18 17:22:24','80446','2026-09-18 17:42:51',_binary '',2012,2,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'山西潞安煤基清洁能源有限公司',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `crm_contract` ENABLE KEYS */;

--
-- Table structure for table `crm_contract_config`
--

DROP TABLE IF EXISTS `crm_contract_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_contract_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `notify_enabled` tinyint(1) DEFAULT NULL COMMENT '是否开启提前提醒',
  `notify_days` int DEFAULT NULL COMMENT '提前提醒天数',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  `our_company_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '我方公司名称',
  `our_legal_person` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '我方法定代表人',
  `our_agent` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '我方授权代表',
  `our_address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '我方地址',
  `our_bank_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '我方开户银行',
  `our_bank_account` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '我方银行账号',
  `our_tel` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '我方电话',
  `our_fax` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '我方传真',
  `default_quality_standard` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '默认国家（行业）标准',
  `default_sample_days` int DEFAULT NULL COMMENT '默认送样天数',
  `default_delivery_days` int DEFAULT NULL COMMENT '默认交货期限天数',
  `default_delivery_arrive_days` int DEFAULT NULL COMMENT '默认到货天数',
  `default_install_days` int DEFAULT NULL COMMENT '默认安装调试验收天数',
  `default_first_accept_days` int DEFAULT NULL COMMENT '默认初步验收天数',
  `default_trial_days` int DEFAULT NULL COMMENT '默认试用期天数',
  `default_final_accept_days` int DEFAULT NULL COMMENT '默认最终验收天数',
  `default_auto_accept_days` int DEFAULT NULL COMMENT '默认视同验收合格天数',
  `default_max_repair_times` int DEFAULT NULL COMMENT '默认维修次数上限',
  `default_prepay_ratio` decimal(10,2) DEFAULT NULL COMMENT '默认首付款比例(%)',
  `default_prepay_days` int DEFAULT NULL COMMENT '默认首付支付天数',
  `default_accept_pay_ratio` decimal(10,2) DEFAULT NULL COMMENT '默认验收款比例(%)',
  `default_accept_pay_days` int DEFAULT NULL COMMENT '默认验收款支付天数',
  `default_deposit_ratio` decimal(10,2) DEFAULT NULL COMMENT '默认保证金比例(%)',
  `default_deposit_months` int DEFAULT NULL COMMENT '默认保证金月数',
  `default_deposit_days` int DEFAULT NULL COMMENT '默认保证金退还天数',
  `default_warranty_years` int DEFAULT NULL COMMENT '默认质保年数',
  `default_response_hours` int DEFAULT NULL COMMENT '默认响应小时数',
  `default_repair_hours` int DEFAULT NULL COMMENT '默认维修小时数',
  `default_repair_times` int DEFAULT NULL COMMENT '默认维修次数',
  `default_report_repair_days` int DEFAULT NULL COMMENT '默认报修天数',
  `default_contract_copies` int DEFAULT NULL COMMENT '默认合同份数',
  `default_gpc_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '默认政府采购中心名称',
  `p_buyer_reject_ratio` decimal(10,2) DEFAULT NULL COMMENT '甲方拒收货物违约金比例(%)',
  `p_buyer_overdue_wan` decimal(10,2) DEFAULT NULL COMMENT '甲方逾期付款违约金(万分之/天)',
  `p_buyer_overdue_days` int DEFAULT NULL COMMENT '甲方逾期付款可终止合同天数',
  `p_seller_quality` decimal(10,2) DEFAULT NULL COMMENT '乙方质量不合格违约金比例(%)',
  `p_seller_overdue_wan` decimal(10,2) DEFAULT NULL COMMENT '乙方逾期交货违约金(万分之/天)',
  `p_seller_overdue_days` int DEFAULT NULL COMMENT '乙方逾期交货可终止合同天数',
  `p_seller_compensate` decimal(10,2) DEFAULT NULL COMMENT '乙方终止合同赔偿金比例(%)',
  `p_seller_replace_days` int DEFAULT NULL COMMENT '检测不合格无条件更换天数',
  `p_seller_terminate` decimal(10,2) DEFAULT NULL COMMENT '逾期不能更换赔偿金比例(%)',
  `p_seller_defect` decimal(10,2) DEFAULT NULL COMMENT '权利瑕疵违约金比例(%)',
  `s_buyer_late_ratio` decimal(10,2) DEFAULT NULL COMMENT '甲方逾期付款滞纳金比例(%/日)',
  `s_buyer_breach_ratio` decimal(10,2) DEFAULT NULL COMMENT '甲方违约金比例(%)',
  `s_seller_late_ratio` decimal(10,2) DEFAULT NULL COMMENT '乙方逾期供货滞纳金比例(%/日)',
  `s_seller_breach_ratio` decimal(10,2) DEFAULT NULL COMMENT '乙方违约金比例(%)',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 合同配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_contract_config`
--

/*!40000 ALTER TABLE `crm_contract_config` DISABLE KEYS */;
INSERT INTO `crm_contract_config` VALUES (1,1,7,'1','2023-11-18 21:52:52','1','2026-09-15 19:14:34',_binary '\0',1,'四川天信防腐保温工程有限公司','王天信','李防腐','成都市武侯区天府大道100号','中国工商银行成都高新支行','4402 2020 0123 4567 890','028-8888 8888','028-8888 8889',NULL,5,30,30,10,7,30,7,15,3,30.00,15,60.00,15,10.00,12,15,1,24,48,3,3,4,NULL,5.00,5.00,30,5.00,5.00,30,10.00,15,10.00,10.00,0.50,5.00,0.50,5.00);
/*!40000 ALTER TABLE `crm_contract_config` ENABLE KEYS */;

--
-- Table structure for table `crm_contract_product`
--

DROP TABLE IF EXISTS `crm_contract_product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_contract_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `contract_id` bigint NOT NULL COMMENT '合同编号',
  `product_id` bigint NOT NULL COMMENT '产品编号',
  `product_price` decimal(24,6) NOT NULL COMMENT '产品单价',
  `contract_price` decimal(24,6) NOT NULL COMMENT '合同价格',
  `count` decimal(24,6) NOT NULL COMMENT '数量',
  `total_price` decimal(24,6) NOT NULL COMMENT '总计价格',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户编号',
  `spec` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规格型号',
  `unit` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '单位',
  `fund_source` tinyint DEFAULT NULL COMMENT '资金来源：1=预算内，2=预算外，3=其他',
  `accessories` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '随机配件',
  `delivery_period` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '交货期',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_contract_id` (`contract_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=93 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 合同产品关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_contract_product`
--

/*!40000 ALTER TABLE `crm_contract_product` DISABLE KEYS */;
INSERT INTO `crm_contract_product` VALUES (29,5,4,10010.000000,10010.000000,1.000000,10010.000000,'1','2024-02-21 16:35:59','1','2024-02-21 09:42:54',_binary '',1,NULL,NULL,NULL,NULL,NULL),(30,5,4,10010.000000,10010.000000,1.000000,10010.000000,'1','2024-02-21 17:42:54','1','2024-02-21 17:42:54',_binary '\0',1,NULL,NULL,NULL,NULL,NULL),(31,5,5,100.300000,200.000000,1.000000,200.000000,'1','2024-02-21 17:42:54','1','2024-02-21 17:42:54',_binary '\0',1,NULL,NULL,NULL,NULL,NULL),(32,6,4,10010.000000,10010.000000,1.000000,10010.000000,'1','2024-02-21 21:30:17','1','2024-02-21 21:30:17',_binary '\0',1,NULL,NULL,NULL,NULL,NULL),(33,4,5,100.300000,100.300000,1.000000,100.300000,'1','2024-02-23 00:04:37','1','2024-02-22 16:04:43',_binary '',1,NULL,NULL,NULL,NULL,NULL),(34,4,5,100.300000,100.300000,1.000000,100.300000,'1','2024-02-23 00:04:44','1','2024-02-22 16:19:54',_binary '',1,NULL,NULL,NULL,NULL,NULL),(35,4,5,100.300000,100.300000,1.000000,100.300000,'1','2024-02-23 00:04:44','1','2024-02-22 16:19:54',_binary '',1,NULL,NULL,NULL,NULL,NULL),(36,4,4,10010.000000,220.000000,1.000000,220.000000,'1','2024-02-23 00:19:54','1','2024-02-22 16:19:59',_binary '',1,NULL,NULL,NULL,NULL,NULL),(37,4,5,100.300000,200.000000,1.000000,200.000000,'1','2024-02-23 00:19:54','1','2024-02-22 16:19:59',_binary '',1,NULL,NULL,NULL,NULL,NULL),(38,4,4,10010.000000,10010.000000,1.000000,10010.000000,'1','2024-02-23 08:44:12','1','2024-02-23 08:44:12',_binary '\0',1,NULL,NULL,NULL,NULL,NULL),(39,8,4,10010.000000,10010.000000,1.000000,10010.000000,'1','2024-02-24 07:51:48','1','2024-02-24 07:51:48',_binary '\0',1,NULL,NULL,NULL,NULL,NULL),(40,8,5,100.300000,100.300000,1.000000,100.300000,'1','2024-02-24 07:51:48','1','2024-02-24 07:51:48',_binary '\0',1,NULL,NULL,NULL,NULL,NULL),(41,9,4,10010.000000,10010.000000,1.000000,10010.000000,'1','2024-02-24 08:02:02','1','2024-02-24 08:02:02',_binary '\0',1,NULL,NULL,NULL,NULL,NULL),(42,9,4,10010.000000,10010.000000,1.000000,10010.000000,'1','2024-02-24 08:02:02','1','2024-02-24 08:02:02',_binary '\0',1,NULL,NULL,NULL,NULL,NULL),(43,10,4,10010.000000,10010.000000,1.000000,10010.000000,'1','2024-02-24 08:04:22','1','2024-02-24 08:04:22',_binary '\0',1,NULL,NULL,NULL,NULL,NULL),(44,10,5,100.300000,100.300000,1.000000,100.300000,'1','2024-02-24 08:04:22','1','2024-02-24 08:04:22',_binary '\0',1,NULL,NULL,NULL,NULL,NULL),(45,11,4,10010.000000,10010.000000,1.000000,10010.000000,'1','2024-02-24 16:06:37','1','2024-02-24 16:06:37',_binary '\0',1,NULL,NULL,NULL,NULL,NULL),(46,11,4,10010.000000,10010.000000,1.000000,10010.000000,'1','2024-02-24 16:06:37','1','2024-02-24 16:06:37',_binary '\0',1,NULL,NULL,NULL,NULL,NULL),(47,13,4,10010.000000,10010.000000,1.000000,10010.000000,'1','2024-02-25 23:35:14','1','2024-02-25 23:35:14',_binary '\0',1,NULL,NULL,NULL,NULL,NULL),(48,13,4,10010.000000,10010.000000,1.000000,10010.000000,'1','2024-02-25 23:35:14','1','2024-02-25 23:35:14',_binary '\0',1,NULL,NULL,NULL,NULL,NULL),(49,7,3,100.000000,100.000000,1.000000,100.000000,'1','2025-07-14 12:52:51','1','2025-07-14 13:02:54',_binary '',1,NULL,NULL,NULL,NULL,NULL),(50,7,3,100.000000,100.000000,1.000000,100.000000,'1','2025-07-14 13:02:54','1','2025-07-14 13:04:36',_binary '',1,NULL,NULL,NULL,NULL,NULL),(51,7,3,100.000000,100.000000,1.000000,100.000000,'1','2025-07-14 13:04:36','1','2025-07-14 13:05:20',_binary '',1,NULL,NULL,NULL,NULL,NULL),(52,7,3,100.000000,100.000000,1.000000,100.000000,'1','2025-07-14 13:05:20','1','2025-07-14 13:06:35',_binary '',1,NULL,NULL,NULL,NULL,NULL),(53,7,3,100.000000,100.000000,1.000000,100.000000,'1','2025-07-14 13:06:35','1','2025-07-14 13:06:35',_binary '\0',1,NULL,NULL,NULL,NULL,NULL),(54,14,3,100.000000,100.000000,2.000000,200.000000,'1','2025-11-18 19:24:21','1','2025-11-18 19:24:21',_binary '\0',1,NULL,NULL,NULL,NULL,NULL),(65,6001,1003,185.000000,185.000000,5000.000000,925000.000000,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012,NULL,NULL,NULL,NULL,NULL),(66,6001,1004,168.000000,168.000000,3000.000000,504000.000000,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012,NULL,NULL,NULL,NULL,NULL),(67,6002,1012,12000.000000,12000.000000,3.000000,36000.000000,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012,NULL,NULL,NULL,NULL,NULL),(68,6003,1001,860.000000,860.000000,800.000000,688000.000000,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012,NULL,NULL,NULL,NULL,NULL),(69,6003,1002,1360.000000,1360.000000,300.000000,408000.000000,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012,NULL,NULL,NULL,NULL,NULL),(70,6010,1005,152.000000,152.000000,0.000000,0.000000,'80446','2026-09-15 17:14:48','80446','2026-09-15 17:14:48',_binary '\0',2012,NULL,NULL,NULL,NULL,NULL),(71,6011,1005,152.000000,152.000000,0.000000,0.000000,'80446','2026-09-15 17:15:17','80446','2026-09-15 17:15:17',_binary '\0',2012,NULL,NULL,NULL,NULL,NULL),(72,6012,4,10010.000000,10010.000000,2.000000,20020.000000,'1','2026-09-15 19:17:03','1','2026-09-15 19:17:03',_binary '\0',1,'DN200','米',1,'含法兰','30天'),(73,6013,4,10010.000000,10010.000000,2.000000,20020.000000,'1','2026-09-15 19:23:55','1','2026-09-15 19:23:55',_binary '\0',1,'DN200','米',2,'含法兰螺栓','合同签订后30天'),(74,6014,1012,12000.000000,12000.000000,3.000000,36000.000000,'80446','2026-09-17 15:33:18','80446','2026-09-17 15:33:18',_binary '\0',2012,NULL,NULL,NULL,NULL,NULL),(75,6017,1003,185.000000,185.000000,5000.000000,925000.000000,'80446','2026-09-17 16:30:02','80446','2026-09-17 16:30:02',_binary '\0',2012,NULL,NULL,NULL,NULL,NULL),(76,6017,1004,168.000000,168.000000,3000.000000,504000.000000,'80446','2026-09-17 16:30:02','80446','2026-09-17 16:30:02',_binary '\0',2012,NULL,NULL,NULL,NULL,NULL),(77,6018,1003,185.000000,185.000000,5000.000000,925000.000000,'80446','2026-09-18 08:50:11','80446','2026-09-18 08:52:38',_binary '',2012,NULL,NULL,NULL,NULL,NULL),(78,6018,1004,168.000000,168.000000,3000.000000,504000.000000,'80446','2026-09-18 08:50:11','80446','2026-09-18 08:52:38',_binary '',2012,NULL,NULL,NULL,NULL,NULL),(79,6018,1003,185.000000,185.000000,5000.000000,925000.000000,'80446','2026-09-18 08:52:38','80446','2026-09-18 08:52:38',_binary '\0',2012,NULL,NULL,NULL,NULL,NULL),(80,6018,1004,168.000000,168.000000,3000.000000,504000.000000,'80446','2026-09-18 08:52:38','80446','2026-09-18 08:52:38',_binary '\0',2012,NULL,NULL,NULL,NULL,NULL),(81,6101,1103,215.000000,215.000000,4200.000000,903000.000000,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012,NULL,NULL,NULL,NULL,NULL),(82,6101,1104,176.000000,176.000000,2600.000000,457600.000000,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012,NULL,NULL,NULL,NULL,NULL),(83,6101,1105,158.000000,158.000000,1800.000000,284400.000000,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012,NULL,NULL,NULL,NULL,NULL),(84,6102,1112,9800.000000,9800.000000,4.000000,39200.000000,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012,NULL,NULL,NULL,NULL,NULL),(85,6103,1101,620.000000,620.000000,900.000000,558000.000000,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012,NULL,NULL,NULL,NULL,NULL),(86,6103,1102,1080.000000,1080.000000,320.000000,345600.000000,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012,NULL,NULL,NULL,NULL,NULL),(87,6104,1108,560.000000,560.000000,2100.000000,1176000.000000,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012,NULL,NULL,NULL,NULL,NULL),(88,6105,1101,620.000000,620.000000,900.000000,558000.000000,'80446','2026-09-18 16:57:15','80446','2026-09-18 16:57:15',_binary '\0',2012,NULL,NULL,NULL,NULL,NULL),(89,6105,1102,1080.000000,1080.000000,320.000000,345600.000000,'80446','2026-09-18 16:57:15','80446','2026-09-18 16:57:15',_binary '\0',2012,NULL,NULL,NULL,NULL,NULL),(90,6106,1004,168.000000,168.000000,4.000000,672.000000,'80446','2026-09-18 17:22:24','80446','2026-09-18 17:22:24',_binary '\0',2012,NULL,NULL,NULL,NULL,NULL),(91,6106,1001,860.000000,860.000000,300.000000,258000.000000,'80446','2026-09-18 17:22:24','80446','2026-09-18 17:22:24',_binary '\0',2012,NULL,NULL,NULL,NULL,NULL),(92,6106,1006,220.000000,220.000000,200.000000,44000.000000,'80446','2026-09-18 17:22:24','80446','2026-09-18 17:22:24',_binary '\0',2012,NULL,NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `crm_contract_product` ENABLE KEYS */;

--
-- Table structure for table `crm_customer`
--

DROP TABLE IF EXISTS `crm_customer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_customer` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号，主键自增',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户名称',
  `contact_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系人',
  `customer_type` tinyint NOT NULL DEFAULT '1' COMMENT '客户类别：1=下游客户 2=上游供应商',
  `follow_up_status` tinyint(1) NOT NULL DEFAULT '0' COMMENT '跟进状态',
  `follow_up_count` int NOT NULL DEFAULT '0' COMMENT '累计跟进次数',
  `contact_last_time` datetime DEFAULT NULL COMMENT '最后跟进时间',
  `contact_last_content` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最后跟进内容',
  `contact_next_time` datetime DEFAULT NULL COMMENT '下次联系时间',
  `owner_user_id` bigint DEFAULT NULL COMMENT '负责人的用户编号',
  `owner_time` datetime NOT NULL COMMENT '成为负责人的时间',
  `lock_status` bit(1) NOT NULL DEFAULT b'0' COMMENT '锁定状态',
  `deal_status` bit(1) NOT NULL DEFAULT b'0' COMMENT '成交状态',
  `mobile` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机',
  `telephone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '电话',
  `qq` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'QQ',
  `wechat` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '微信',
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `area_id` bigint DEFAULT NULL COMMENT '地区编号',
  `detail_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '详细地址',
  `industry_id` int DEFAULT NULL COMMENT '所属行业',
  `level` int DEFAULT NULL COMMENT '客户等级',
  `source` int DEFAULT NULL COMMENT '客户来源',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `owner_user_id` (`owner_user_id`) USING BTREE,
  KEY `idx_lock_deal_owner_time` (`lock_status`,`deal_status`,`owner_time`) USING BTREE,
  KEY `idx_mobile` (`mobile`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=3112 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 客户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_customer`
--

/*!40000 ALTER TABLE `crm_customer` DISABLE KEYS */;
INSERT INTO `crm_customer` VALUES (12,'测试公海逻辑',NULL,1,1,1,'2025-09-30 22:07:57','321','2025-09-02 00:00:00',NULL,'2024-02-20 20:02:19',_binary '\0',_binary '\0',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'1','2024-02-20 17:41:26','1','2026-09-18 17:28:50',_binary '\0',1),(13,'呵呵哒',NULL,1,0,0,NULL,NULL,NULL,NULL,'2025-09-27 20:58:24',_binary '\0',_binary '\0',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'1','2024-02-20 20:17:42','1','2026-09-16 10:42:58',_binary '\0',1),(14,'啦啦啦',NULL,1,0,0,NULL,NULL,'2024-02-29 00:00:00',NULL,'2026-09-15 10:14:33',_binary '\0',_binary '\0','18818260277','18818260277',NULL,'wang-server',NULL,130304,'阿巴都',2,3,1,NULL,'1','2024-02-20 21:39:40','1','2026-09-15 10:15:19',_binary '\0',1),(15,'小土豆',NULL,1,0,0,NULL,NULL,'2024-03-02 00:00:00',NULL,'2025-09-27 20:58:37',_binary '\0',_binary '\0','18248472642',NULL,NULL,NULL,'768423@qq.com',NULL,NULL,2,2,3,NULL,'1','2024-02-23 00:09:46','1','2026-09-16 10:42:58',_binary '\0',1),(16,'演示线索',NULL,1,1,1,'2025-07-14 12:38:07','21321','2025-07-15 00:00:00',NULL,'2024-02-23 21:16:14',_binary '\0',_binary '\0',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,NULL,'1','2024-02-23 21:16:14','1','2026-09-18 17:28:50',_binary '\0',1),(17,'演示客户',NULL,1,0,0,NULL,NULL,NULL,NULL,'2024-02-20 23:05:17',_binary '\0',_binary '\0','18818260223','18818260223',NULL,NULL,'768885413@qq.com',NULL,NULL,NULL,NULL,1,NULL,'1','2024-02-23 22:04:53','1','2026-09-11 16:51:17',_binary '\0',1),(18,'有个小客户',NULL,1,1,2,'2025-09-30 21:03:22','321','2025-09-02 21:07:17',NULL,'2024-10-02 10:30:27',_binary '\0',_binary '\0',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1,NULL,'117','2024-10-02 10:30:27','1','2026-09-18 17:28:50',_binary '\0',1),(19,'里',NULL,1,0,0,NULL,NULL,NULL,80446,'2026-09-08 14:59:15',_binary '\0',_binary '\0','18222222222',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,3,NULL,'80446','2026-09-08 14:59:15','80446','2026-09-11 16:51:17',_binary '',2012),(20,'与',NULL,1,0,0,NULL,NULL,NULL,80446,'2026-09-08 15:00:09',_binary '\0',_binary '\0','13991109876',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,7,NULL,'80446','2026-09-08 15:00:09','80446','2026-09-11 16:51:17',_binary '',2012),(21,'西安热力工程有限公司',NULL,1,0,0,NULL,NULL,NULL,80446,'2026-09-08 19:23:22',_binary '\0',_binary '\0','13652487912','','2745196328',NULL,'zhangjianshe@heat-power.com',NULL,'陕西省西安市未央区凤城五路热力产业园8号楼',102,2,8,NULL,'80446','2026-09-08 19:23:22','80446','2026-09-11 16:51:17',_binary '',2012),(3001,'长庆油田分公司第五采油厂',NULL,1,1,1,'2026-08-30 00:00:00','签订集输管线防腐框架，分批供货','2026-09-10 00:00:00',80939,'2026-09-08 20:44:38',_binary '\0',_binary '','13891112201','0917-3820001',NULL,NULL,'cq5cy@petrochina.com.cn',NULL,'陕西省庆阳市西峰区长庆桥 第五采油厂集输大队',1,2,3,'中石油长庆油田直属，战略级','1','2026-09-08 19:56:10','1','2026-09-18 17:28:50',_binary '\0',2012),(3002,'延长油田股份有限公司靖边采油厂',NULL,1,1,1,'2026-09-01 00:00:00','油管防腐招标已中标，签合同','2026-09-08 00:00:00',80939,'2026-09-08 20:44:38',_binary '\0',_binary '','13891112202','0912-4611002',NULL,NULL,'jbcy@yanchang.com',NULL,'陕西省榆林市靖边县张家畔 物资供应站',1,2,3,'延长石油系统，重要客户','1','2026-09-08 19:56:10','1','2026-09-18 17:28:50',_binary '\0',2012),(3003,'延安市热力有限责任公司',NULL,1,1,1,'2026-09-02 00:00:00','供热管网保温改造方案已报','2026-09-12 00:00:00',80940,'2026-09-08 20:44:38',_binary '\0',_binary '\0','13991113303','0911-2338003',NULL,NULL,'yarl@ya-heating.com',NULL,'陕西省延安市宝塔区七里铺 工程建设部',5,3,1,'市政供热客户','1','2026-09-08 19:56:10','1','2026-09-18 17:28:50',_binary '\0',2012),(3004,'长庆油田分公司第二采气厂',NULL,1,0,0,'2026-09-01 00:00:00','集气站保温保冷需求对接中','2026-09-09 00:00:00',80941,'2026-09-08 20:44:38',_binary '\0',_binary '\0','13891115505','0917-8820005',NULL,NULL,'cq2cq@petrochina.com.cn',NULL,'陕西省榆林市横山区 采气厂工程科',2,2,3,'长庆气区重要客户','1','2026-09-08 19:56:10','1','2026-09-11 16:51:17',_binary '\0',2012),(3005,'延长石油集团延安炼油厂',NULL,1,0,0,'2026-08-28 00:00:00','管道漏磁内检测服务已实施','2026-09-06 00:00:00',80945,'2026-09-08 20:44:38',_binary '\0',_binary '','13991116606','0911-3352006',NULL,NULL,'yalyc@yanchang.com',NULL,'陕西省延安市宝塔区河庄坪 设备管理处',4,2,3,'炼化管道检测客户','1','2026-09-08 19:56:10','1','2026-09-11 16:51:17',_binary '\0',2012),(3006,'中国石油天然气管道局西安分公司',NULL,1,1,1,'2026-08-15 00:00:00','防腐入围资格审查中','2026-09-15 00:00:00',80938,'2026-09-08 20:44:38',_binary '\0',_binary '\0','13801114404','010-59986004',NULL,NULL,'gdxa@cnpc.com.cn',NULL,'陕西省西安市未央区凤城八路 物资装备处',3,1,1,'中石油系统战略客户，锁定跟进','1','2026-09-08 19:56:10','1','2026-09-18 17:28:50',_binary '\0',2012),(3007,'国能榆林化工有限公司',NULL,1,0,0,'2026-09-02 00:00:00','工艺管线防腐初步接触','2026-09-14 00:00:00',80940,'2026-09-08 20:44:38',_binary '\0',_binary '\0','13704118808','0912-3390008',NULL,NULL,'ylhg@ceic.com',NULL,'陕西省榆林市榆阳区麻黄梁工业园 生产技术部',6,3,2,'煤化工客户','1','2026-09-08 19:56:10','1','2026-09-11 16:51:17',_binary '\0',2012),(3008,'延安新区城市建设投资有限公司',NULL,1,0,0,'2026-07-10 00:00:00','长期未跟进，已入公海',NULL,NULL,'2026-09-08 20:44:38',_binary '\0',_binary '\0','13609117707','0911-8100007',NULL,NULL,'yaxq@ya-cc.com',NULL,'陕西省延安市新区为民服务中心 工程管理部',5,4,4,'潜在市政客户，公海池','1','2026-09-08 19:56:10','1','2026-09-11 16:51:17',_binary '\0',2012),(3009,'陕西延安石油钻采设备有限公司',NULL,1,0,0,'2026-06-20 00:00:00','陌拜后长期无进展，放入公海',NULL,NULL,'2026-09-08 20:44:38',_binary '\0',_binary '\0','13809119909','0911-2670009',NULL,NULL,'yazc@sx-oil.com',NULL,'陕西省延安市安塞区高新区 采购部',1,4,6,'公海客户','1','2026-09-08 19:56:10','1','2026-09-11 16:51:17',_binary '\0',2012),(3010,'西安热力工程有限公司',NULL,1,0,0,NULL,NULL,NULL,80446,'2026-09-09 09:38:06',_binary '\0',_binary '\0','13652487912',NULL,'2745196328',NULL,'zhangjianshe@heat-power.com',NULL,'陕西省西安市未央区凤城五路热力产业园8号楼',102,NULL,NULL,NULL,'80446','2026-09-09 09:38:06','80446','2026-09-11 16:51:17',_binary '',2012),(3011,'西安热力工程有限公司',NULL,1,0,0,NULL,NULL,NULL,80446,'2026-09-09 09:55:07',_binary '\0',_binary '\0','13652487912',NULL,'2745196328',NULL,'zhangjianshe@heat-power.com',NULL,'陕西省西安市未央区凤城五路热力产业园8号楼',102,NULL,NULL,NULL,'80446','2026-09-09 09:55:07','80446','2026-09-11 16:51:17',_binary '',2012),(3012,'西安热力工程有限公司',NULL,1,0,0,NULL,NULL,NULL,80446,'2026-09-09 09:55:56',_binary '\0',_binary '\0','13652487912',NULL,'2745196328',NULL,'zhangjianshe@heat-power.com',NULL,'陕西省西安市未央区凤城五路热力产业园8号楼',102,2,6,NULL,'80446','2026-09-09 09:55:56','80446','2026-09-11 16:51:17',_binary '',2012),(3013,'哦哦哦哦哦',NULL,1,0,0,NULL,NULL,NULL,80933,'2026-09-09 10:08:16',_binary '\0',_binary '\0','13652487912',NULL,'2745196328',NULL,'zhangjianshe@heat-power.com',NULL,'陕西省西安市未央区凤城五路热力产业园8号楼',102,2,6,NULL,'80933','2026-09-09 10:08:16','80446','2026-09-11 16:51:17',_binary '',2012),(3014,'111',NULL,1,0,0,NULL,NULL,'2026-09-09 10:11:33',80446,'2026-09-09 10:11:35',_binary '\0',_binary '\0',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'80446','2026-09-09 10:11:35','80446','2026-09-11 16:51:17',_binary '',2012),(3015,'111',NULL,1,0,0,NULL,NULL,NULL,80446,'2026-09-09 10:12:37',_binary '\0',_binary '\0',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'80446','2026-09-09 10:12:37','80446','2026-09-11 16:51:17',_binary '',2012),(3016,'222',NULL,1,0,0,NULL,NULL,NULL,80933,'2026-09-09 10:20:39',_binary '\0',_binary '\0',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'80446','2026-09-09 10:20:39','80446','2026-09-11 16:51:17',_binary '',2012),(3022,'西安热力工程有限公司',NULL,1,0,0,NULL,NULL,'2026-09-12 20:43:19',80446,'2026-09-12 20:43:22',_binary '\0',_binary '\0','13652487912',NULL,'2745196328',NULL,'zhangjianshe@heat-power.com',NULL,'陕西省西安市未央区凤城五路热力产业园8号楼',102,NULL,NULL,NULL,'80446','2026-09-12 20:43:22','80446','2026-09-12 20:45:34',_binary '',2012),(3023,'陕西延长石油（集团）有限责任公司',NULL,1,0,0,NULL,NULL,'2026-09-12 20:47:44',80446,'2026-09-12 20:47:47',_binary '\0',_binary '\0',NULL,'029-88899517',NULL,NULL,'ycsyb.bjb@163.com',NULL,'陕西省西安市唐延路61号延长石油科研中心',101,NULL,6,NULL,'80446','2026-09-12 20:47:47','80446','2026-09-12 20:48:31',_binary '',2012),(3024,'陕西延长石油（集团）有限责任公司',NULL,1,0,0,NULL,NULL,'2026-09-12 20:48:56',80446,'2026-09-12 20:48:58',_binary '\0',_binary '\0',NULL,'029-88899517',NULL,NULL,'ycsyb.bjb@163.com',NULL,'陕西省西安市唐延路61号延长石油科研中心',101,1,3,NULL,'80446','2026-09-12 20:48:58','80446','2026-09-12 20:49:43',_binary '',2012),(3025,'陕西延长石油（集团）有限责任公司',NULL,1,0,0,NULL,NULL,'2026-09-12 20:50:08',80933,'2026-09-12 20:53:55',_binary '\0',_binary '\0',NULL,'029-88899517',NULL,NULL,'ycsyb.bjb@163.com',NULL,'陕西省西安市唐延路61号延长石油科研中心',101,NULL,6,NULL,'80446','2026-09-12 20:50:10','80446','2026-09-12 21:01:49',_binary '',2012),(3026,'ZZ负责人校验测试',NULL,1,0,0,NULL,NULL,NULL,3001,'2026-09-12 20:59:44',_binary '\0',_binary '\0',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'1','2026-09-12 20:59:44','1','2026-09-12 20:59:45',_binary '',1),(3027,'西安热力工程有限公司',NULL,1,0,0,NULL,NULL,'2026-09-01 21:06:02',80446,'2026-09-12 21:02:49',_binary '\0',_binary '\0','13652487912',NULL,'2745196328',NULL,'zhangjianshe@heat-power.com',NULL,'陕西省西安市未央区凤城五路热力产业园8号楼',102,NULL,NULL,NULL,'80446','2026-09-12 21:02:49','80446','2026-09-12 21:13:55',_binary '',2012),(3028,'西安热力工程有限公司',NULL,1,0,0,NULL,NULL,NULL,80446,'2026-09-14 09:55:10',_binary '\0',_binary '\0','13652487912',NULL,'2745196328',NULL,'zhangjianshe@heat-power.com',NULL,'陕西省西安市未央区凤城五路热力产业园8号楼',102,NULL,NULL,NULL,'80446','2026-09-14 09:55:10','80446','2026-09-14 10:48:23',_binary '',2012),(3029,'西安热力工程有限公司',NULL,1,0,0,NULL,NULL,'2026-09-14 14:02:32',80446,'2026-09-14 14:02:19',_binary '\0',_binary '\0','13652487912',NULL,'2745196328',NULL,'zhangjianshe@heat-power.com',NULL,'陕西省西安市未央区凤城五路热力产业园8号楼',102,NULL,NULL,NULL,'80446','2026-09-14 14:02:19','80446','2026-09-14 14:08:18',_binary '',2012),(3030,'陕西延长石油（集团）有限责任公司',NULL,1,0,0,NULL,NULL,'2026-09-14 14:06:47',80933,'2026-09-14 14:07:01',_binary '\0',_binary '\0',NULL,'029-88899517',NULL,NULL,'ycsyb.bjb@163.com',NULL,'陕西省西安市唐延路61号延长石油科研中心',101,2,6,NULL,'80446','2026-09-14 14:07:01','80446','2026-09-14 14:08:24',_binary '',2012),(3031,'西安热力工程有限公司',NULL,1,0,0,NULL,NULL,'2026-09-15 08:27:51',80446,'2026-09-15 08:27:53',_binary '\0',_binary '\0','13652487912',NULL,'2745196328',NULL,'zhangjianshe@heat-power.com',NULL,'陕西省西安市未央区凤城五路热力产业园8号楼',102,NULL,NULL,NULL,'80446','2026-09-15 08:27:53','80446','2026-09-15 09:55:52',_binary '',2012),(3032,'陕西延长石油（集团）有限责任公司',NULL,1,1,1,'2026-09-15 08:34:28','电话联系成功','2026-09-16 08:33:38',80933,'2026-09-15 08:30:53',_binary '\0',_binary '\0',NULL,'029-88899517',NULL,NULL,'ycsyb.bjb@163.com',NULL,'陕西省西安市唐延路61号延长石油科研中心',101,1,3,NULL,'80446','2026-09-15 08:30:53','80933','2026-09-18 17:28:50',_binary '\0',2012),(3033,'西安热力工程有限公司',NULL,1,0,0,NULL,NULL,NULL,80446,'2026-09-15 10:07:07',_binary '\0',_binary '\0','13652487912',NULL,'2745196328',NULL,'zhangjianshe@heat-power.com',NULL,'陕西省西安市未央区凤城五路热力产业园8号楼',102,NULL,2,NULL,'80446','2026-09-15 10:07:07','80446','2026-09-15 10:07:20',_binary '',2012),(3034,'西安热力工程有限公司',NULL,1,1,1,'2026-09-16 11:02:12','打广告的','2026-09-01 16:50:31',80446,'2026-09-16 11:01:34',_binary '\0',_binary '\0','13652487912',NULL,'2745196328',NULL,'zhangjianshe@heat-power.com',NULL,'陕西省西安市未央区凤城五路热力产业园8号楼',102,NULL,NULL,NULL,'80446','2026-09-16 11:01:34','80446','2026-09-18 17:28:50',_binary '',2012),(3037,'西安热力工程有限公司',NULL,1,1,1,'2026-09-02 16:52:21','iKiki','2026-09-10 16:52:28',80446,'2026-09-16 16:52:12',_binary '\0',_binary '\0','13652487912',NULL,'2745196328',NULL,'zhangjianshe@heat-power.com',NULL,'陕西省西安市未央区凤城五路热力产业园8号楼',102,NULL,NULL,NULL,'80446','2026-09-16 16:52:12','80446','2026-09-18 17:28:50',_binary '',2012),(3040,'江苏鑫盛防腐涂料有限公司',NULL,2,1,1,'2026-09-20 08:40:04','了了了了了了','2026-09-25 08:40:06',80446,'2026-09-17 16:53:16',_binary '\0',_binary '\0','13812567890','0512-88886666',NULL,NULL,'jsxinsheng@163.com',NULL,NULL,201,1,4,NULL,'80446','2026-09-17 16:53:16','80446','2026-09-20 08:41:17',_binary '\0',2012),(3041,'江苏鑫盛防腐涂料有限公司','王经理',2,0,0,NULL,NULL,NULL,80446,'2026-09-17 17:15:44',_binary '\0',_binary '\0',NULL,'0512-88886666',NULL,NULL,NULL,NULL,'江苏省苏州市工业园区xx路1号',NULL,1,NULL,'供应商编号：GYS001；主营产品：环氧富锌底漆、环氧云铁中间漆、聚氨酯面漆；等级：A级；账期：月结30天；交货周期：到货7天；状态：启用','80446','2026-09-17 17:15:44','80446','2026-09-19 14:12:38',_binary '',2012),(3042,'廊坊新元保冷材料有限公司',NULL,2,0,0,NULL,NULL,'2026-09-21 14:11:30',80446,'2026-09-17 17:15:44',_binary '\0',_binary '\0','13785643210','0316-7654321',NULL,NULL,'lfxyuan@163.com',NULL,NULL,202,1,2,'供应商编号：GYS002；主营产品：PIR保冷板、PUF保冷管壳、保冷管托；等级：A级；账期：月结30天；交货周期：到货15天；状态：启用','80446','2026-09-17 17:15:44','80446','2026-09-19 14:11:37',_binary '\0',2012),(3043,'山东金彩板业有限公司',NULL,2,0,0,NULL,NULL,'2026-09-20 14:10:54',80446,'2026-09-17 17:15:45',_binary '\0',_binary '\0','13567890123','0531-87654321',NULL,NULL,'sdjincai@163.com',NULL,NULL,203,2,4,'供应商编号：GYS003；主营产品：镀铝锌彩钢板、铝皮、不锈钢扎带；等级：B级；账期：月结60天；交货周期：到货10天；状态：启用','80446','2026-09-17 17:15:45','80446','2026-09-19 14:10:59',_binary '\0',2012),(3044,'河北中兴绝热材料有限公司',NULL,2,0,0,NULL,NULL,'2026-09-23 14:10:21',80446,'2026-09-17 17:15:45',_binary '\0',_binary '\0','13654321098','0311-6543210',NULL,NULL,'hbzhongxing@163.com',NULL,NULL,202,2,4,'供应商编号：GYS004；主营产品：离心玻璃棉毡、硅酸铝针刺毡；等级：B级；账期：月结60天；交货周期：到货10天；状态：启用','80446','2026-09-17 17:15:45','80446','2026-09-19 14:10:27',_binary '\0',2012),(3045,'四川蜀立消防科技有限公司',NULL,2,0,0,NULL,NULL,NULL,80446,'2026-09-17 17:15:45',_binary '\0',_binary '\0','13987654321','028-87654321',NULL,NULL,'scshuli@163.com',NULL,NULL,206,2,4,'供应商编号：GYS005；主营产品：钢结构防火涂料（膨胀型/非膨胀型）；等级：B级；账期：预付30%；交货周期：到货20天；状态：启用','80446','2026-09-17 17:15:45','80446','2026-09-19 14:09:31',_binary '\0',2012),(3046,'山东齐鲁石化防腐材料有限公司',NULL,2,0,0,NULL,NULL,'2026-09-28 14:08:34',80446,'2026-09-17 17:15:45',_binary '\0',_binary '\0','13701234567','0533-7654321',NULL,NULL,'sdqilufu@163.com',NULL,NULL,205,1,8,'主营产品：FBE环氧粉末、3PE共聚胶粘剂、PE外层；等级：A级；账期：月结30天；交货周期：到货10天；状态：启用','80446','2026-09-17 17:15:45','80446','2026-09-20 08:29:52',_binary '\0',2012),(3101,'长庆油田分公司第八采油厂',NULL,1,1,1,'2026-09-18 17:39:33','验证跟进次数功能','2026-09-18 17:39:33',80446,'2026-09-18 10:59:01',_binary '\0',_binary '','13891113301','0917-3851001',NULL,NULL,'cq8cy@petrochina.com.cn',NULL,'陕西省榆林市定边县 第八采油厂集输大队',1,2,3,'长庆油田重要客户','1','2026-09-18 10:59:01','80446','2026-09-18 17:39:37',_binary '\0',2012),(3102,'延长油田股份有限公司定边采油厂',NULL,1,1,1,'2026-09-02 00:00:00','油管防腐招标已中标，待签合同','2026-09-10 00:00:00',80446,'2026-09-18 10:59:01',_binary '\0',_binary '','13891113302','0912-4621002',NULL,NULL,'dbcy@yanchang.com',NULL,'陕西省榆林市定边县 物资供应站',1,2,3,'延长石油系统，重要客户','1','2026-09-18 10:59:01','','2026-09-18 17:28:50',_binary '\0',2012),(3103,'西安市热力集团有限责任公司',NULL,1,1,1,'2026-09-03 00:00:00','供热管网保温改造方案已报','2026-09-14 00:00:00',80446,'2026-09-18 10:59:01',_binary '\0',_binary '\0','13991114403','029-88610003',NULL,NULL,'xarl@xa-heating.com',NULL,'陕西省西安市雁塔区 工程建设部',5,3,1,'市政供热客户','1','2026-09-18 10:59:01','','2026-09-18 17:28:50',_binary '\0',2012),(3104,'长庆油田分公司第三采气厂',NULL,1,1,0,'2026-09-18 17:41:19','该方法','2026-09-23 17:41:21',80446,'2026-09-18 10:59:01',_binary '\0',_binary '\0','13891115504','0917-8830004',NULL,NULL,'cq3cq@petrochina.com.cn',NULL,'陕西省延安市吴起县 采气厂工程科',2,2,3,'长庆气区重要客户','1','2026-09-18 10:59:01','80446','2026-09-18 17:42:06',_binary '\0',2012),(3105,'陕西省天然气股份有限公司',NULL,1,1,0,'2026-08-29 00:00:00','管道超声内检测服务已实施','2026-09-08 00:00:00',80446,'2026-09-18 10:59:01',_binary '\0',_binary '','13991116605','029-86520005',NULL,NULL,'xasn@sxgas.com',NULL,'陕西省西安市经济技术开发区 管道管理处',3,2,3,'省级管网客户','1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(3106,'中石化华北油气分公司',NULL,1,1,1,'2026-08-18 00:00:00','防腐入围资格审查中','2026-09-16 00:00:00',80446,'2026-09-18 10:59:01',_binary '',_binary '\0','13801117706','010-59987006',NULL,NULL,'hbyq@sinopec.com',NULL,'陕西省西安市未央区 物资装备中心',3,1,1,'中石化系统战略客户，锁定跟进','1','2026-09-18 10:59:01','','2026-09-18 17:28:50',_binary '\0',2012),(3107,'中煤榆林能源化工有限公司',NULL,1,0,0,'2026-09-03 00:00:00','工艺管线保温初步接触','2026-09-15 00:00:00',80446,'2026-09-18 10:59:01',_binary '\0',_binary '\0','13704118807','0912-3391007',NULL,NULL,'ylny@chinacoal.com',NULL,'陕西省榆林市横山区 生产技术部',6,3,2,'煤化工客户','1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(3108,'延长石油集团榆林炼油厂',NULL,1,0,0,'2026-09-04 00:00:00','储罐与管线防腐年度计划对接','2026-09-17 00:00:00',80446,'2026-09-18 10:59:01',_binary '\0',_binary '\0','13891119908','0912-3880008',NULL,NULL,'yllyc@yanchang.com',NULL,'陕西省榆林市靖边县 设备管理部',4,2,3,'炼化管道客户','1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(3109,'铜川市新区城市建设发展有限公司',NULL,1,0,0,'2026-07-12 00:00:00','长期未跟进，已入公海',NULL,NULL,'2026-09-18 10:59:01',_binary '\0',_binary '\0','13609110009','0919-3180009',NULL,NULL,'tcxq@tc-cc.com',NULL,'陕西省铜川市新区 工程管理部',5,4,4,'潜在市政客户，公海池','1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(3110,'陕西延安能源化工装备有限公司',NULL,1,0,0,'2026-06-25 00:00:00','陌拜后长期无进展，放入公海',NULL,NULL,'2026-09-18 10:59:01',_binary '\0',_binary '\0','13809111110','0911-2670110',NULL,NULL,'yanhg@sx-energy.com',NULL,'陕西省延安市安塞区高新区 采购部',7,4,6,'公海客户','1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(3111,'山西潞安煤基清洁能源有限公司',NULL,1,0,0,NULL,NULL,NULL,80446,'2026-09-18 17:12:40',_binary '\0',_binary '\0','13835110001','0355-5911001',NULL,NULL,'lahg@sx-luan.com',NULL,'山西省长治市潞城区 潞安煤基清洁能源有限公司 设备管理部',102,1,4,'煤化工客户，年度防腐检修窗口 10 月—12 月','80446','2026-09-18 17:12:40','80446','2026-09-18 17:13:27',_binary '\0',2012);
/*!40000 ALTER TABLE `crm_customer` ENABLE KEYS */;

--
-- Table structure for table `crm_customer_limit_config`
--

DROP TABLE IF EXISTS `crm_customer_limit_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_customer_limit_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `type` int NOT NULL COMMENT '规则类型 1: 拥有客户数限制，2:锁定客户数限制',
  `user_ids` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '规则适用人群',
  `dept_ids` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '规则适用部门',
  `max_count` int NOT NULL COMMENT '数量上限',
  `deal_count_enabled` tinyint DEFAULT NULL COMMENT '成交客户是否占有拥有客户数(当 type = 1 时)',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 客户限制配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_customer_limit_config`
--

/*!40000 ALTER TABLE `crm_customer_limit_config` DISABLE KEYS */;
INSERT INTO `crm_customer_limit_config` VALUES (1,1,'1,103','100',999,0,'1','2023-11-18 22:04:11','1','2025-09-27 17:16:05',_binary '\0',1),(2,2,'104','100',1000,0,'1','2024-02-23 22:53:24','1','2024-02-23 22:53:29',_binary '\0',1),(3,1,'1','101',1,1,'1','2025-09-27 17:16:13','1','2025-09-27 17:16:23',_binary '',1),(4,1,'','',3,0,'1','2025-09-27 17:17:47','1','2025-09-27 17:18:05',_binary '',1),(5,1,'','',3332,0,'1','2025-09-27 17:18:09','1','2025-09-27 17:19:22',_binary '',1);
/*!40000 ALTER TABLE `crm_customer_limit_config` ENABLE KEYS */;

--
-- Table structure for table `crm_customer_pool_config`
--

DROP TABLE IF EXISTS `crm_customer_pool_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_customer_pool_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `enabled` tinyint(1) NOT NULL COMMENT '是否启用客户公海',
  `contact_expire_days` int DEFAULT NULL COMMENT '未跟进放入公海天数',
  `deal_expire_days` int DEFAULT NULL COMMENT '未成交放入公海天数',
  `notify_enabled` tinyint(1) DEFAULT NULL COMMENT '是否开启提前提醒',
  `notify_days` int DEFAULT NULL COMMENT '提前提醒天数',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 客户公海配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_customer_pool_config`
--

/*!40000 ALTER TABLE `crm_customer_pool_config` DISABLE KEYS */;
INSERT INTO `crm_customer_pool_config` VALUES (1,1,10,15,0,NULL,'1','2023-11-18 21:52:52','1','2025-11-19 09:49:13',_binary '\0',1),(2,1,10,10,1,3,'80446','2026-09-12 21:06:52','80446','2026-09-18 16:51:12',_binary '\0',2012);
/*!40000 ALTER TABLE `crm_customer_pool_config` ENABLE KEYS */;

--
-- Table structure for table `crm_follow_up_record`
--

DROP TABLE IF EXISTS `crm_follow_up_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_follow_up_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `biz_type` int DEFAULT NULL COMMENT '数据类型',
  `biz_id` bigint DEFAULT NULL COMMENT '数据编号',
  `type` int DEFAULT NULL COMMENT '跟进类型',
  `content` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '跟进内容',
  `next_time` datetime DEFAULT NULL COMMENT '下次联系时间',
  `pic_urls` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图片',
  `file_urls` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '附件',
  `business_ids` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '关联的商机编号数组',
  `contact_ids` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '关联的联系人编号数组',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  `follow_up_time` datetime DEFAULT NULL COMMENT '实际跟进时间（本次实际联系客户的时间，公海回收计时起点）',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_biz_type_biz_id` (`biz_type`,`biz_id`) USING BTREE,
  KEY `idx_biz_follow_up_time` (`biz_type`,`biz_id`,`follow_up_time`)
) ENGINE=InnoDB AUTO_INCREMENT=9120 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 跟进记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_follow_up_record`
--

/*!40000 ALTER TABLE `crm_follow_up_record` DISABLE KEYS */;
INSERT INTO `crm_follow_up_record` VALUES (2,2,4,1,'<p>132312</p>','2024-01-24 00:00:00',NULL,NULL,'','','1','2024-01-15 20:49:30','1','2026-09-16 16:25:39',_binary '\0',1,'2024-01-15 20:49:30'),(3,2,2,1,'吃饭、睡觉、打逗逗','2023-12-31 00:00:00','http://test.txgy.xianoupeng.cn/bd16ec4f875e9739733f86bcf98f6a15735c1ffa9a7af907131c9debba043a18.png',NULL,'','11','1','2024-01-22 09:35:23','1','2026-09-16 16:25:39',_binary '\0',1,'2024-01-22 09:35:23'),(4,2,3,1,'11','2024-02-06 00:00:00',NULL,NULL,'','','1','2024-02-03 01:33:42','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-03 01:33:42'),(5,2,3,1,'11','2024-02-06 00:00:00',NULL,NULL,'','','1','2024-02-03 01:33:56','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-03 01:33:56'),(6,1,2,1,'啦啦啦啦','2024-02-06 00:00:00',NULL,NULL,'','','1','2024-02-19 20:01:17','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-19 20:01:17'),(7,2,5,1,'啦啦啦啦','2024-02-06 00:00:00',NULL,NULL,'','','1','2024-02-19 20:18:47','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-19 20:18:47'),(8,1,4,2,'啦啦啦','2024-02-13 00:00:00',NULL,NULL,'','','1','2024-02-19 21:38:37','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-19 21:38:37'),(9,2,7,2,'啦啦啦','2024-02-13 00:00:00',NULL,NULL,'','','1','2024-02-19 22:15:27','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-19 22:15:27'),(10,2,2,3,'小土豆','2024-02-26 00:00:00',NULL,NULL,'','','1','2024-02-20 13:08:26','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-20 13:08:26'),(11,2,2,3,'小土豆','2024-02-26 00:00:00',NULL,NULL,'','','1','2024-02-20 13:08:33','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-20 13:08:33'),(12,2,2,3,'小土豆','2024-02-26 00:00:00',NULL,NULL,'','','1','2024-02-20 13:09:05','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-20 13:09:05'),(13,2,2,3,'小土豆','2024-02-26 00:00:00',NULL,NULL,'','','1','2024-02-20 13:09:07','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-20 13:09:07'),(14,2,2,3,'小土豆','2024-02-26 00:00:00',NULL,NULL,'','','1','2024-02-20 13:09:43','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-20 13:09:43'),(15,2,2,3,'小土豆','2024-02-26 00:00:00',NULL,NULL,'','','1','2024-02-20 13:10:11','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-20 13:10:11'),(16,2,2,3,'小土豆','2024-02-26 00:00:00',NULL,NULL,'','','1','2024-02-20 13:10:14','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-20 13:10:14'),(17,2,2,2,'31231','2024-02-06 00:00:00',NULL,NULL,'','','1','2024-02-20 13:11:23','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-20 13:11:23'),(18,2,2,2,'31231','2024-02-06 00:00:00',NULL,NULL,'','','1','2024-02-20 13:12:12','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-20 13:12:12'),(19,2,2,2,'31231','2024-02-06 00:00:00',NULL,NULL,'','','1','2024-02-20 13:14:55','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-20 13:14:55'),(20,2,2,1,'来一个跟进记录','2024-02-12 00:00:00',NULL,NULL,'','','1','2024-02-20 13:15:47','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-20 13:15:47'),(21,2,11,2,'吧','2024-02-15 00:00:00',NULL,NULL,'','','1','2024-02-20 13:16:40','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-20 13:16:40'),(22,3,12,2,'111','2024-02-21 00:00:00',NULL,NULL,'','','1','2024-02-20 23:15:35','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-20 23:15:35'),(23,3,12,2,'111','2024-02-21 00:00:00',NULL,NULL,'','','1','2024-02-20 23:17:05','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-20 23:17:05'),(24,3,13,2,'啦啦啦','2024-02-25 00:00:00',NULL,NULL,'','','1','2024-02-20 23:18:26','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-20 23:18:26'),(25,3,13,2,'啦啦啦','2024-02-25 00:00:00',NULL,NULL,'','','1','2024-02-20 23:18:56','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-20 23:18:56'),(26,3,13,2,'啦啦啦','2024-02-25 00:00:00',NULL,NULL,'','','1','2024-02-20 23:19:03','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-20 23:19:03'),(27,3,13,2,'啦啦啦','2024-02-25 00:00:00',NULL,NULL,'','','1','2024-02-20 23:19:25','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-20 23:19:25'),(28,3,13,2,'啦啦啦','2024-02-25 00:00:00',NULL,NULL,'','','1','2024-02-20 23:20:19','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-20 23:20:19'),(29,4,6,2,'哈哈哈','2024-02-20 00:00:00',NULL,NULL,'','','1','2024-02-21 23:35:46','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-21 23:35:46'),(30,5,4,1,'阿巴阿巴','2024-02-20 00:00:00',NULL,NULL,'','','1','2024-02-23 09:30:41','1','2026-09-16 16:25:39',_binary '\0',1,'2024-02-23 09:30:41'),(31,2,16,1,'阿巴阿巴','2024-02-13 00:00:00','null','null','13','16','1','2024-02-24 11:16:42','1','2026-09-16 16:25:39',_binary '',1,'2024-02-24 11:16:42'),(32,2,16,1,'哈哈哈哈','2024-02-07 00:00:00',NULL,NULL,'','16,13,13','1','2024-02-24 11:43:05','1','2026-09-16 16:25:39',_binary '',1,'2024-02-24 11:43:05'),(33,2,16,1,'111','2024-02-19 00:00:00',NULL,NULL,'','17','1','2024-02-24 15:42:41','1','2026-09-16 16:25:39',_binary '',1,'2024-02-24 15:42:41'),(34,2,18,2,'32123','2024-10-08 00:00:00',NULL,NULL,'','','1','2024-10-02 11:10:37','1','2026-09-16 16:25:39',_binary '\0',1,'2024-10-02 11:10:37'),(35,2,16,1,'21321','2025-07-15 00:00:00','http://test.txgy.xianoupeng.cn/20250714/微信图片_2025-06-22_204705_590_1752467836277.png','http://test.txgy.xianoupeng.cn/20250714/xxx_1752467883491.txt','','','1','2025-07-14 12:38:07','1','2026-09-16 16:25:39',_binary '\0',1,'2025-07-14 12:38:07'),(36,1,9,1,'2222','2025-09-24 09:42:45',NULL,NULL,'','','1','2025-09-24 09:42:49','1','2026-09-16 16:25:39',_binary '\0',1,'2025-09-24 09:42:49'),(37,2,18,1,'321','2025-09-02 21:07:17',NULL,NULL,'','','1','2025-09-30 21:03:22','1','2026-09-16 16:25:39',_binary '\0',1,'2025-09-30 21:03:22'),(38,3,17,1,'321321321','2025-09-09 22:01:53',NULL,NULL,'13','16','1','2025-09-30 22:01:56','1','2026-09-16 16:25:39',_binary '',1,'2025-09-30 22:01:56'),(39,3,17,1,'3123213','2025-09-11 22:02:16',NULL,NULL,'13','16','1','2025-09-30 22:02:21','1','2026-09-16 16:25:39',_binary '\0',1,'2025-09-30 22:02:21'),(40,2,12,1,'321','2025-09-02 00:00:00',NULL,NULL,'','12','1','2025-09-30 22:07:57','1','2026-09-16 16:25:39',_binary '\0',1,'2025-09-30 22:07:57'),(9001,1,2001,3,'赴现场技术交流，介绍3PE防腐工艺及索克萨姆生产线优势','2026-09-10 00:00:00','','','','','80939','2026-08-20 10:30:00','80939','2026-09-16 16:25:39',_binary '\0',2012,'2026-08-20 10:30:00'),(9002,2,3001,6,'商务谈判，确定框架协议与首批供货量','2026-09-10 00:00:00','','','','','80939','2026-08-30 15:00:00','80939','2026-09-16 16:25:39',_binary '\0',2012,'2026-08-30 15:00:00'),(9003,2,3002,4,'招标答疑并递交投标文件，油管防腐规格确认','2026-09-08 00:00:00','','','','','80939','2026-09-01 09:20:00','80939','2026-09-16 16:25:39',_binary '\0',2012,'2026-09-01 09:20:00'),(9004,2,3003,2,'现场勘查供热管网，测量保温管改造工程量','2026-09-12 00:00:00','','','','','80940','2026-09-02 14:10:00','80940','2026-09-16 16:25:39',_binary '\0',2012,'2026-09-02 14:10:00'),(9005,4,5005,5,'完成漏磁内检测送样与设备进场，出具检测报告','2026-09-06 00:00:00','','','','','80945','2026-08-28 11:00:00','80945','2026-09-16 16:25:39',_binary '\0',2012,'2026-08-28 11:00:00'),(9006,2,3006,4,'提交防腐入围资质材料，等待资格审查','2026-09-15 00:00:00','','','','','80938','2026-08-15 16:40:00','80938','2026-09-16 16:25:39',_binary '\0',2012,'2026-08-15 16:40:00'),(9007,4,5006,3,'技术交流，推荐环氧陶瓷内衬防腐方案','2026-09-14 00:00:00','','','','','80940','2026-09-02 10:00:00','80940','2026-09-16 16:25:39',_binary '\0',2012,'2026-09-02 10:00:00'),(9008,2,3032,1,'电话联系成功','2026-09-16 08:33:38',NULL,NULL,'','','80933','2026-09-15 08:34:28','80933','2026-09-16 16:25:39',_binary '\0',2012,'2026-09-15 08:34:28'),(9009,2,3034,3,'拜访','2026-09-01 11:01:56',NULL,NULL,'','','80446','2026-09-16 11:02:12','80446','2026-09-16 16:25:39',_binary '',2012,'2026-09-16 11:02:12'),(9012,2,3034,2,'打广告的','2026-09-01 16:50:31',NULL,NULL,'','','80446','2026-09-16 16:51:01','80446','2026-09-16 16:51:01',_binary '\0',2012,'2025-08-21 16:50:26'),(9013,2,3037,3,'iKiki','2026-09-10 16:52:28',NULL,NULL,'','','80446','2026-09-16 16:52:43','80446','2026-09-16 16:52:43',_binary '\0',2012,'2026-09-02 16:52:21'),(9101,1,2101,3,'赴现场技术交流，介绍3PE防腐工艺与生产线能力','2026-09-12 00:00:00',NULL,NULL,'','','80446','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012,'2026-08-22 10:00:00'),(9102,2,3101,6,'商务谈判，确定框架协议与首批供货量','2026-09-12 00:00:00',NULL,NULL,'','','80446','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012,'2026-09-01 15:30:00'),(9103,2,3102,4,'招标答疑并递交投标文件，油管防腐规格确认','2026-09-10 00:00:00',NULL,NULL,'','','80446','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012,'2026-09-02 09:40:00'),(9104,2,3103,2,'现场勘查供热管网，测量保温管改造工程量','2026-09-14 00:00:00',NULL,NULL,'','','80446','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012,'2026-09-03 14:20:00'),(9105,4,5105,5,'完成超声内检测设备进场并出具检测报告','2026-09-08 00:00:00',NULL,NULL,'','','80446','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012,'2026-08-29 11:20:00'),(9106,2,3106,4,'提交防腐入围资质材料，等待资格审查','2026-09-16 00:00:00',NULL,NULL,'','','80446','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012,'2026-08-18 16:10:00'),(9107,4,5106,3,'技术交流，推荐环氧陶瓷内衬+保温综合方案','2026-09-15 00:00:00',NULL,NULL,'','','80446','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012,'2026-09-03 10:30:00'),(9108,4,5107,1,'电话沟通，了解年度防腐检修计划与预算','2026-09-17 00:00:00',NULL,NULL,'','','80446','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012,'2026-09-04 09:10:00'),(9109,5,6103,7,'跟进合同审批进度，催促预付款到账','2026-09-22 00:00:00',NULL,NULL,'','','80446','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012,'2026-09-15 11:00:00'),(9110,4,5101,6,'端到端演示：合同已审批、回款已到账，项目闭环','2026-09-18 17:00:52',NULL,NULL,'','','80446','2026-09-18 17:00:52','80446','2026-09-18 17:00:52',_binary '\0',2012,'2026-09-18 17:00:52'),(9111,2,3104,4,'拒绝','2026-09-19 17:26:15',NULL,NULL,'','','80446','2026-09-18 17:26:36','80446','2026-09-18 17:27:02',_binary '',2012,'2026-09-18 17:26:11'),(9112,2,3104,6,'的身上的','2026-09-26 17:26:45',NULL,NULL,'','','80446','2026-09-18 17:26:52','80446','2026-09-18 17:27:00',_binary '',2012,'2026-09-18 17:26:42'),(9113,2,3101,1,'验证跟进次数功能','2026-09-18 17:39:33',NULL,NULL,'','','80446','2026-09-18 17:39:36','80446','2026-09-18 17:39:38',_binary '',2012,'2026-09-18 17:39:33'),(9114,2,3104,3,'刚刚','2026-09-18 17:41:11',NULL,NULL,'','','80446','2026-09-18 17:41:17','80446','2026-09-18 17:42:07',_binary '',2012,'2026-09-18 17:41:06'),(9115,2,3104,4,'该方法','2026-09-23 17:41:21',NULL,NULL,'','','80446','2026-09-18 17:41:30','80446','2026-09-18 17:42:05',_binary '',2012,'2026-09-18 17:41:19'),(9116,2,3040,3,'了了了了了了','2026-09-25 08:40:06',NULL,NULL,'','','80446','2026-09-20 08:41:17','80446','2026-09-20 08:41:17',_binary '\0',2012,'2026-09-20 08:40:04'),(9119,4,5101,1,'【权限修复验证】','2026-09-21 09:18:37',NULL,NULL,'','','80446','2026-09-20 09:18:39','80446','2026-09-20 09:18:40',_binary '',2012,'2026-09-20 09:18:37');
/*!40000 ALTER TABLE `crm_follow_up_record` ENABLE KEYS */;

--
-- Table structure for table `crm_owner_record`
--

DROP TABLE IF EXISTS `crm_owner_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_owner_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `biz_type` tinyint NOT NULL COMMENT 'CRM 业务类型（见 CrmBizTypeEnum）',
  `biz_id` bigint NOT NULL COMMENT 'CRM 业务编号',
  `pre_owner_user_id` bigint DEFAULT NULL COMMENT '变更前负责人',
  `post_owner_user_id` bigint DEFAULT NULL COMMENT '变更后负责人',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_biz` (`biz_type`,`biz_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 负责人变更记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_owner_record`
--

/*!40000 ALTER TABLE `crm_owner_record` DISABLE KEYS */;
INSERT INTO `crm_owner_record` VALUES (1,2,14,NULL,1,'1','2026-09-15 10:14:33','1','2026-09-15 10:14:33',_binary '\0',1),(2,2,14,1,NULL,'1','2026-09-15 10:15:20','1','2026-09-15 10:15:20',_binary '\0',1),(4,2,16,1,NULL,'1','2026-09-16 10:37:10','1','2026-09-16 10:37:10',_binary '\0',1),(8,2,12,103,NULL,'1','2026-09-16 10:42:58','1','2026-09-16 10:42:58',_binary '\0',1),(9,2,18,117,NULL,'1','2026-09-16 10:42:58','1','2026-09-16 10:42:58',_binary '\0',1),(10,2,13,103,NULL,'1','2026-09-16 10:42:58','1','2026-09-16 10:42:58',_binary '\0',1),(11,2,15,104,NULL,'1','2026-09-16 10:42:58','1','2026-09-16 10:42:58',_binary '\0',1);
/*!40000 ALTER TABLE `crm_owner_record` ENABLE KEYS */;

--
-- Table structure for table `crm_permission`
--

DROP TABLE IF EXISTS `crm_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `biz_type` tinyint NOT NULL DEFAULT '100' COMMENT '数据类型',
  `biz_id` bigint NOT NULL DEFAULT '0' COMMENT '数据编号',
  `user_id` bigint NOT NULL DEFAULT '0' COMMENT '用户编号',
  `level` int NOT NULL DEFAULT '0' COMMENT '会员等级',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_biz_bizid_user` (`biz_type`,`biz_id`,`user_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=487 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 数据权限表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_permission`
--

/*!40000 ALTER TABLE `crm_permission` DISABLE KEYS */;
INSERT INTO `crm_permission` VALUES (21,2,2,1,1,'1','2023-11-25 10:38:05','1','2023-12-26 22:20:30',_binary '',1),(22,3,9,1,1,'1','2023-11-29 14:02:45','1','2023-11-29 14:02:45',_binary '\0',1),(25,6,4,1,1,'1','2023-12-06 09:10:06','1','2023-12-06 09:10:06',_binary '\0',1),(26,2,3,1,1,'1','2023-12-30 20:59:05','1','2024-02-02 17:37:29',_binary '',1),(27,2,3,114,3,'1','2024-01-03 14:38:54','1','2024-01-03 14:42:46',_binary '\0',1),(28,2,3,103,2,'1','2024-01-03 14:42:55','1','2024-01-03 14:42:55',_binary '\0',1),(29,3,11,1,1,'1','2024-01-03 23:16:54','1','2024-01-03 23:16:54',_binary '\0',1),(30,6,5,1,1,'1','2024-01-13 10:13:33','1','2024-01-13 10:13:33',_binary '\0',1),(31,2,4,1,1,'1','2024-01-15 13:49:09','1','2023-12-29 19:52:55',_binary '',1),(32,2,2,1,1,'1','2024-01-20 19:16:32','1','2024-01-20 19:16:32',_binary '\0',1),(33,1,1,1,1,'1','2024-01-22 09:38:05','1','2024-01-22 09:38:05',_binary '\0',1),(34,5,2,1,1,'1','2024-02-01 13:24:26','1','2024-02-01 13:24:26',_binary '\0',1),(35,5,3,1,1,'1','2024-02-01 13:26:04','1','2024-02-01 13:26:04',_binary '\0',1),(36,1,2,1,2,'1','2024-02-19 18:56:35','1','2024-02-19 20:37:24',_binary '\0',1),(37,2,5,1,1,'1','2024-02-19 20:18:47','1','2024-02-19 20:18:47',_binary '\0',1),(38,1,2,103,1,'1','2024-02-19 20:37:24','1','2024-02-19 20:37:24',_binary '\0',1),(39,1,3,1,1,'1','2024-02-19 21:02:00','1','2024-02-19 13:03:32',_binary '',1),(40,2,6,1,1,'1','2024-02-19 21:02:42','1','2024-02-19 21:02:42',_binary '\0',1),(41,1,4,1,1,'1','2024-02-19 21:38:16','1','2024-02-19 21:38:16',_binary '\0',1),(42,2,7,1,1,'1','2024-02-19 22:15:27','1','2024-02-19 22:15:27',_binary '\0',1),(43,1,5,1,1,'1','2024-02-19 22:16:35','1','2024-02-19 22:16:35',_binary '\0',1),(44,2,8,1,1,'1','2024-02-19 22:16:38','1','2024-02-19 22:16:38',_binary '\0',1),(45,1,6,1,1,'1','2024-02-19 22:17:08','1','2024-02-19 22:17:08',_binary '\0',1),(46,2,9,1,1,'1','2024-02-19 22:17:12','1','2024-02-19 22:17:12',_binary '\0',1),(47,1,7,1,1,'1','2024-02-19 22:24:30','1','2024-02-19 22:24:30',_binary '\0',1),(48,2,10,1,1,'1','2024-02-19 22:46:36','1','2024-02-19 22:46:36',_binary '\0',1),(49,2,11,1,1,'1','2024-02-20 13:16:30','1','2024-02-20 13:16:30',_binary '\0',1),(50,2,12,1,1,'1','2024-02-20 17:41:26','1','2024-02-20 10:56:43',_binary '',1),(51,2,12,1,1,'1','2024-02-20 19:03:40','1','2024-02-20 11:40:59',_binary '',1),(52,2,12,103,1,'1','2024-02-20 20:02:20','1','2026-09-16 10:42:58',_binary '',1),(53,2,13,1,1,'1','2024-02-20 20:17:42','1','2024-02-20 12:35:56',_binary '',1),(54,3,12,1,1,'1','2024-02-20 21:10:57','1','2024-02-20 21:10:57',_binary '\0',1),(55,2,14,1,1,'1','2024-02-20 21:39:40','1','2024-02-23 14:30:34',_binary '',1),(56,3,13,1,3,'1','2024-02-20 23:18:16','1','2024-02-20 23:21:14',_binary '\0',1),(57,3,13,112,1,'1','2024-02-20 23:21:14','1','2024-02-20 23:21:14',_binary '\0',1),(58,3,14,1,1,'1','2024-02-20 23:27:33','1','2024-02-20 23:27:33',_binary '\0',1),(59,4,4,1,1,'1','2024-02-20 23:27:33','1','2024-02-21 13:52:06',_binary '',1),(60,4,5,1,1,'1','2024-02-21 16:35:59','1','2024-02-21 16:35:59',_binary '\0',1),(61,4,6,1,1,'1','2024-02-21 21:30:17','1','2024-02-21 21:30:17',_binary '\0',1),(62,4,7,1,1,'1','2024-02-21 21:48:20','1','2024-02-21 21:48:20',_binary '\0',1),(63,4,8,1,1,'1','2024-02-21 21:49:29','1','2024-02-21 15:49:26',_binary '',1),(64,4,8,103,1,'1','2024-02-21 23:49:27','1','2024-02-21 23:49:27',_binary '\0',1),(65,4,7,104,3,'1','2024-02-21 23:53:27','1','2024-02-21 23:53:27',_binary '\0',1),(66,3,15,1,1,'1','2024-02-22 09:08:12','1','2024-02-22 09:08:12',_binary '\0',1),(67,4,9,1,1,'1','2024-02-22 09:32:06','1','2024-02-22 09:32:06',_binary '\0',1),(68,4,10,1,1,'1','2024-02-22 19:55:25','1','2024-02-22 19:55:25',_binary '\0',1),(69,4,11,1,1,'1','2024-02-22 20:40:54','1','2024-02-22 20:40:54',_binary '\0',1),(70,5,4,1,1,'1','2024-02-22 23:50:39','1','2024-02-22 23:50:39',_binary '\0',1),(71,2,15,1,1,'1','2024-02-23 00:09:46','1','2024-02-23 14:23:35',_binary '',1),(72,5,5,1,1,'1','2024-02-23 13:03:39','1','2024-02-23 13:03:39',_binary '\0',1),(73,5,6,1,1,'1','2024-02-23 14:12:49','1','2024-02-23 14:12:49',_binary '\0',1),(74,5,7,1,1,'1','2024-02-23 16:35:12','1','2025-07-14 13:18:53',_binary '',1),(75,1,8,1,1,'1','2024-02-23 21:04:05','1','2024-02-23 21:04:05',_binary '\0',1),(76,2,16,1,1,'1','2024-02-23 21:16:14','1','2026-09-16 10:37:10',_binary '',1),(77,2,17,1,1,'1','2024-02-23 22:04:53','1','2024-02-23 14:16:40',_binary '',1),(78,2,17,1,1,'1','2024-02-23 23:05:17','1','2024-02-26 01:36:21',_binary '',1),(79,3,16,1,1,'1','2024-02-23 23:16:43','1','2024-02-23 23:16:43',_binary '\0',1),(80,4,12,1,1,'1','2024-02-24 00:12:04','1','2024-02-24 00:12:04',_binary '\0',1),(81,4,13,1,1,'1','2024-02-24 00:23:03','1','2024-02-24 00:23:03',_binary '\0',1),(82,5,8,1,1,'1','2024-02-24 07:51:48','1','2024-02-24 00:03:52',_binary '',1),(83,5,9,1,1,'1','2024-02-24 08:02:02','1','2024-02-24 00:03:54',_binary '',1),(84,5,10,1,1,'1','2024-02-24 08:04:22','1','2024-02-24 08:04:22',_binary '\0',1),(85,6,6,1,1,'1','2024-02-24 09:46:17','1','2024-02-24 09:46:17',_binary '\0',1),(86,4,14,1,1,'1','2024-02-24 14:50:30','1','2024-02-24 14:50:30',_binary '\0',1),(87,3,17,1,1,'1','2024-02-24 15:30:39','1','2024-02-24 15:30:39',_binary '\0',1),(88,5,11,1,1,'1','2024-02-24 16:06:37','1','2024-02-24 16:06:37',_binary '\0',1),(89,5,12,1,1,'1','2024-02-24 17:43:06','1','2024-02-24 17:43:06',_binary '\0',1),(90,8,2,1,1,'1','2024-02-25 08:24:09','1','2024-02-25 08:24:09',_binary '\0',1),(91,7,11,1,1,'1','2024-02-25 09:17:22','1','2024-02-25 09:17:22',_binary '\0',1),(92,8,3,1,1,'1','2024-02-25 15:59:23','1','2024-02-25 15:59:23',_binary '\0',1),(93,8,4,1,1,'1','2024-02-25 16:08:57','1','2024-02-25 16:08:57',_binary '\0',1),(94,7,12,1,1,'1','2024-02-25 17:19:54','1','2024-02-25 17:19:54',_binary '\0',1),(95,7,13,1,1,'1','2024-02-25 18:13:42','1','2024-02-25 18:13:42',_binary '\0',1),(96,8,5,1,1,'1','2024-02-25 18:24:00','1','2024-02-25 18:24:00',_binary '\0',1),(97,7,14,1,1,'1','2024-02-25 18:24:42','1','2024-02-25 18:24:42',_binary '\0',1),(98,7,15,1,1,'1','2024-02-25 18:27:45','1','2024-02-25 18:27:45',_binary '\0',1),(99,7,16,1,1,'1','2024-02-25 18:28:37','1','2024-02-25 18:28:37',_binary '\0',1),(100,7,17,1,1,'1','2024-02-25 18:29:47','1','2024-02-25 18:29:47',_binary '\0',1),(101,7,18,1,1,'1','2024-02-25 18:30:08','1','2024-02-25 18:30:08',_binary '\0',1),(102,7,19,1,1,'1','2024-02-25 18:31:00','1','2024-02-25 18:31:00',_binary '\0',1),(103,7,20,1,1,'1','2024-02-25 18:32:10','1','2025-07-30 10:28:02',_binary '',1),(104,7,21,1,1,'1','2024-02-25 18:32:27','1','2024-02-25 18:32:27',_binary '\0',1),(105,7,22,1,1,'1','2024-02-25 18:33:35','1','2024-02-25 18:33:35',_binary '\0',1),(106,7,23,1,1,'1','2024-02-25 18:43:59','1','2024-02-25 18:43:59',_binary '\0',1),(107,7,23,114,2,'1','2024-02-25 19:32:14','1','2024-02-25 19:32:14',_binary '\0',1),(108,7,24,1,1,'1','2024-02-25 19:59:06','1','2024-02-25 19:59:06',_binary '\0',1),(109,8,6,1,1,'1','2024-02-25 20:11:49','1','2024-02-25 20:11:49',_binary '\0',1),(110,7,25,1,1,'1','2024-02-25 20:17:52','1','2024-02-25 20:17:52',_binary '\0',1),(111,7,26,1,1,'1','2024-02-25 20:21:12','1','2025-07-30 10:29:14',_binary '',1),(112,7,27,1,1,'1','2024-02-25 20:22:36','1','2025-07-30 10:29:09',_binary '',1),(113,7,28,1,1,'1','2024-02-25 20:49:27','1','2024-02-25 20:49:27',_binary '\0',1),(114,5,13,1,1,'1','2024-02-25 23:35:14','1','2024-02-25 23:35:14',_binary '\0',1),(115,7,29,1,1,'1','2024-02-25 23:40:10','1','2024-02-25 23:40:10',_binary '\0',1),(116,8,7,1,1,'1','2024-02-26 00:08:48','1','2024-02-26 00:08:48',_binary '\0',1),(117,8,8,1,1,'1','2024-02-26 00:11:02','1','2024-02-26 00:11:02',_binary '\0',1),(118,7,30,1,1,'1','2024-02-26 09:37:07','1','2025-07-30 10:27:15',_binary '',1),(119,1,9,1,1,'1','2024-10-02 09:20:39','1','2024-10-02 09:20:39',_binary '\0',1),(120,2,18,117,1,'117','2024-10-02 10:30:27','1','2026-09-16 10:42:58',_binary '',1),(121,2,18,1,3,'117','2024-10-02 11:17:55','117','2024-10-02 11:17:55',_binary '\0',1),(122,5,7,103,1,'1','2025-07-14 13:18:53','1','2025-07-14 13:18:53',_binary '\0',1),(123,3,18,1,1,'1','2025-08-02 19:40:23','1','2025-08-02 11:41:36',_binary '',1),(124,2,13,103,1,'1','2025-09-27 20:58:24','1','2026-09-16 10:42:58',_binary '',1),(125,2,15,104,1,'1','2025-09-27 20:58:37','1','2026-09-16 10:42:58',_binary '',1),(126,2,16,100,2,'1','2025-09-30 22:20:36','1','2025-09-30 22:20:36',_binary '\0',1),(127,3,17,100,2,'1','2025-09-30 22:20:36','1','2025-09-30 22:20:36',_binary '\0',1),(128,5,14,1,1,'1','2025-11-18 19:24:21','1','2025-11-18 19:24:21',_binary '\0',1),(129,4,15,1,1,'1','2025-11-22 11:26:54','1','2025-11-22 11:26:54',_binary '\0',1),(130,4,16,1,1,'1','2025-11-22 11:28:27','1','2025-11-22 11:28:27',_binary '\0',1),(131,1,10,80446,1,'80446','2026-09-04 14:46:25','80446','2026-09-04 14:49:24',_binary '',2012),(132,2,19,80446,1,'80446','2026-09-08 14:59:15','80446','2026-09-08 15:04:58',_binary '',2012),(133,2,20,80446,1,'80446','2026-09-08 15:00:09','80446','2026-09-08 15:05:00',_binary '',2012),(134,2,21,80446,1,'80446','2026-09-08 19:23:22','80446','2026-09-08 19:26:14',_binary '',2012),(135,1,11,80932,1,'80446','2026-09-08 19:25:04','80446','2026-09-09 09:59:03',_binary '',2012),(136,1,12,80932,1,'80446','2026-09-08 19:26:56','80446','2026-09-09 09:59:00',_binary '',2012),(137,1,13,80446,1,'80446','2026-09-08 19:27:31','80446','2026-09-08 19:27:43',_binary '',2012),(155,2,3001,80939,1,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(156,2,3001,80938,2,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(157,2,3001,80944,3,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(158,2,3002,80939,1,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(159,2,3002,80941,3,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(160,2,3003,80940,1,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(161,2,3003,80944,3,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(162,2,3005,80945,1,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(163,2,3005,80938,2,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(164,2,3006,80938,1,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(165,2,3006,80937,2,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(166,4,5001,80939,1,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(167,4,5001,80940,3,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(168,4,5005,80945,1,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(169,4,5005,80943,3,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(170,5,6001,80939,1,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(171,5,6001,80946,2,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(172,1,2001,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(173,1,2002,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(174,1,2003,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(175,1,2004,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(176,1,2005,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(177,1,2006,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(178,1,2007,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(179,1,2008,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(180,2,3001,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(181,2,3002,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(182,2,3003,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(183,2,3004,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(184,2,3005,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(185,2,3006,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(186,2,3007,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(187,2,3008,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(188,2,3009,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(189,3,4001,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(190,3,4002,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(191,3,4003,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(192,3,4004,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(193,3,4005,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(194,3,4006,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(195,3,4007,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(196,3,4008,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(197,3,4009,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(198,4,5001,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(199,4,5002,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(200,4,5003,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(201,4,5004,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(202,4,5005,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(203,4,5006,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(204,5,6001,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(205,5,6002,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(206,5,6003,80446,2,'1','2026-09-08 20:04:01','1','2026-09-08 20:04:01',_binary '\0',2012),(207,2,3010,80446,1,'80446','2026-09-09 09:38:06','80446','2026-09-09 09:38:15',_binary '',2012),(208,1,2009,80446,1,'80446','2026-09-09 09:44:38','80446','2026-09-09 09:44:43',_binary '',2012),(209,3,4010,80446,1,'80446','2026-09-09 09:53:14','80446','2026-09-09 09:53:18',_binary '',2012),(210,1,2010,80453,1,'80446','2026-09-09 09:54:09','80446','2026-09-09 09:54:45',_binary '',2012),(211,2,3011,80446,1,'80446','2026-09-09 09:55:07','80446','2026-09-09 09:55:38',_binary '',2012),(212,2,3012,80446,1,'80446','2026-09-09 09:55:56','80446','2026-09-09 09:58:39',_binary '',2012),(213,3,4011,80446,1,'80446','2026-09-09 09:57:06','80446','2026-09-09 09:58:46',_binary '',2012),(214,2,3013,80933,1,'80933','2026-09-09 10:08:16','80933','2026-09-10 11:10:10',_binary '',2012),(215,1,2011,80933,1,'80446','2026-09-09 10:11:08','80446','2026-09-12 20:41:57',_binary '',2012),(216,2,3014,80446,1,'80446','2026-09-09 10:11:35','80446','2026-09-09 10:12:22',_binary '',2012),(217,2,3015,80446,1,'80446','2026-09-09 10:12:38','80446','2026-09-09 10:20:19',_binary '',2012),(218,2,3016,80933,1,'80446','2026-09-09 10:20:39','80446','2026-09-10 11:10:16',_binary '',2012),(219,2,3016,80446,3,'80446','2026-09-09 10:20:39','80446','2026-09-10 11:10:16',_binary '',2012),(220,3,4012,80446,1,'80446','2026-09-09 10:37:27','80446','2026-09-09 10:38:11',_binary '',2012),(221,3,4013,80446,1,'80446','2026-09-09 10:38:49','80446','2026-09-09 10:38:55',_binary '',2012),(222,6,1015,80939,1,'80446','2026-09-10 09:09:03','80446','2026-09-10 09:09:03',_binary '\0',2012),(223,8,7004,80939,1,'80446','2026-09-10 09:10:04','80446','2026-09-10 09:10:15',_binary '',2012),(224,1,2012,80446,1,'80446','2026-09-11 10:59:10','80446','2026-09-11 10:59:30',_binary '',2012),(225,1,2013,80446,1,'80446','2026-09-11 10:59:10','80446','2026-09-11 10:59:28',_binary '',2012),(226,1,2014,80446,1,'80446','2026-09-11 10:59:10','80446','2026-09-11 10:59:27',_binary '',2012),(227,1,2015,80446,1,'80446','2026-09-11 10:59:10','80446','2026-09-11 10:59:25',_binary '',2012),(228,1,2016,80446,1,'80446','2026-09-11 10:59:10','80446','2026-09-11 10:59:23',_binary '',2012),(229,1,2017,80446,1,'80446','2026-09-11 10:59:10','80446','2026-09-11 10:59:21',_binary '',2012),(230,1,2018,80446,1,'80446','2026-09-11 10:59:10','80446','2026-09-11 10:59:19',_binary '',2012),(231,1,2019,80446,1,'80446','2026-09-11 10:59:10','80446','2026-09-11 10:59:18',_binary '',2012),(232,1,2020,80446,1,'80446','2026-09-12 20:41:26','80446','2026-09-12 20:45:29',_binary '',2012),(233,2,3022,80446,1,'80446','2026-09-12 20:43:22','80446','2026-09-12 20:45:34',_binary '',2012),(234,1,2021,80933,1,'80446','2026-09-12 20:47:19','80446','2026-09-12 21:01:57',_binary '',2012),(235,2,3023,80446,1,'80446','2026-09-12 20:47:47','80446','2026-09-12 20:48:30',_binary '',2012),(236,2,3024,80446,1,'80446','2026-09-12 20:48:58','80446','2026-09-12 20:49:42',_binary '',2012),(237,2,3025,80933,1,'80446','2026-09-12 20:50:10','80446','2026-09-12 21:01:49',_binary '',2012),(238,2,3026,3001,1,'1','2026-09-12 20:59:44','1','2026-09-12 20:59:45',_binary '',1),(239,1,2022,80446,1,'80446','2026-09-12 21:02:33','80446','2026-09-12 21:14:01',_binary '',2012),(240,2,3027,80446,1,'80446','2026-09-12 21:02:49','80446','2026-09-12 21:13:55',_binary '',2012),(241,3,4014,80446,1,'80446','2026-09-12 21:03:49','80446','2026-09-12 21:13:49',_binary '',2012),(242,4,5007,80939,1,'80446','2026-09-12 21:13:12','80446','2026-09-14 10:48:43',_binary '',2012),(243,5,6004,80446,1,'80446','2026-09-12 21:16:44','80446','2026-09-12 21:16:44',_binary '\0',2012),(244,8,7005,80939,1,'80446','2026-09-12 21:22:12','80446','2026-09-12 21:23:30',_binary '',2012),(245,8,7006,80939,1,'80446','2026-09-12 21:23:13','80446','2026-09-12 21:23:33',_binary '',2012),(246,5,6005,80446,1,'80446','2026-09-12 21:26:11','80446','2026-09-12 21:26:11',_binary '\0',2012),(247,9,1,80446,1,'80446','2026-09-14 09:34:52','80446','2026-09-14 10:49:25',_binary '',2012),(248,1,2023,80446,1,'80446','2026-09-14 09:53:06','80446','2026-09-14 10:48:18',_binary '',2012),(249,2,3028,80446,1,'80446','2026-09-14 09:55:10','80446','2026-09-14 10:48:22',_binary '',2012),(250,4,5008,80939,1,'80446','2026-09-14 10:10:33','80446','2026-09-14 10:48:40',_binary '',2012),(251,9,2,80446,1,'80446','2026-09-14 10:16:16','80446','2026-09-14 10:49:23',_binary '',2012),(252,1,2024,80446,1,'80446','2026-09-14 14:01:32','80446','2026-09-14 14:08:05',_binary '',2012),(253,2,3029,80446,1,'80446','2026-09-14 14:02:19','80446','2026-09-14 14:08:18',_binary '',2012),(254,1,2025,80933,1,'80446','2026-09-14 14:06:10','80446','2026-09-14 14:08:11',_binary '',2012),(255,2,3030,80933,1,'80446','2026-09-14 14:07:01','80446','2026-09-14 14:08:24',_binary '',2012),(256,4,5009,80939,1,'80446','2026-09-14 14:10:15','80446','2026-09-14 14:10:39',_binary '',2012),(257,1,2026,80446,1,'80446','2026-09-15 08:27:15','80446','2026-09-15 09:55:34',_binary '',2012),(258,2,3031,80446,1,'80446','2026-09-15 08:27:53','80446','2026-09-15 09:55:52',_binary '',2012),(259,1,2027,80446,1,'80446','2026-09-15 08:29:43','80446','2026-09-15 08:29:55',_binary '',2012),(260,1,2028,80933,1,'80446','2026-09-15 08:30:24','80446','2026-09-15 08:30:24',_binary '\0',2012),(261,2,3032,80933,1,'80446','2026-09-15 08:30:53','80446','2026-09-15 08:30:53',_binary '\0',2012),(262,3,4015,80446,1,'80446','2026-09-15 08:41:43','80446','2026-09-15 09:55:48',_binary '',2012),(263,4,5010,80939,1,'80446','2026-09-15 08:43:30','80446','2026-09-15 09:56:15',_binary '',2012),(264,1,2029,80446,1,'80446','2026-09-15 10:07:02','80446','2026-09-15 10:07:02',_binary '\0',2012),(265,2,3033,80446,1,'80446','2026-09-15 10:07:07','80446','2026-09-15 10:07:19',_binary '',2012),(267,2,14,1,1,'1','2026-09-15 10:14:33','1','2026-09-15 10:15:20',_binary '',1),(268,4,5011,80446,1,'80446','2026-09-15 10:48:31','80446','2026-09-15 10:48:31',_binary '\0',2012),(269,9,3,80446,1,'80446','2026-09-15 11:02:44','80446','2026-09-15 11:02:44',_binary '\0',2012),(270,4,5012,80446,1,'80446','2026-09-15 15:08:20','80446','2026-09-15 15:08:20',_binary '\0',2012),(271,9,4,80446,1,'80446','2026-09-15 15:10:10','80446','2026-09-15 15:10:10',_binary '\0',2012),(272,5,6010,80446,1,'80446','2026-09-15 17:14:48','80446','2026-09-15 17:14:48',_binary '\0',2012),(273,5,6011,80446,1,'80446','2026-09-15 17:15:17','80446','2026-09-15 17:15:17',_binary '\0',2012),(274,5,6012,1,1,'1','2026-09-15 19:17:03','1','2026-09-15 19:37:14',_binary '',1),(275,5,6013,1,1,'1','2026-09-15 19:23:55','1','2026-09-15 19:37:14',_binary '',1),(276,2,3034,80446,1,'80446','2026-09-16 11:01:34','80446','2026-09-16 16:51:42',_binary '',2012),(279,2,3037,80446,1,'80446','2026-09-16 16:52:12','80446','2026-09-16 17:10:16',_binary '',2012),(281,9,5501,80939,1,'80939','2026-07-20 09:00:00','80939','2026-07-20 09:00:00',_binary '\0',2012),(282,9,5501,80446,2,'80939','2026-07-20 09:00:00','80939','2026-07-20 09:00:00',_binary '\0',2012),(283,9,5502,80945,1,'80945','2026-08-22 09:00:00','80945','2026-08-22 09:00:00',_binary '\0',2012),(284,9,5502,80446,2,'80945','2026-08-22 09:00:00','80945','2026-08-22 09:00:00',_binary '\0',2012),(285,9,5503,80939,1,'80939','2026-08-26 09:00:00','80939','2026-08-26 09:00:00',_binary '\0',2012),(286,9,5503,80446,2,'80939','2026-08-26 09:00:00','80939','2026-08-26 09:00:00',_binary '\0',2012),(287,9,5504,80939,1,'80939','2026-09-04 09:00:00','80939','2026-09-04 09:00:00',_binary '\0',2012),(288,9,5504,80446,2,'80939','2026-09-04 09:00:00','80939','2026-09-04 09:00:00',_binary '\0',2012),(289,9,5505,80945,1,'80945','2026-09-08 09:00:00','80945','2026-09-08 09:00:00',_binary '\0',2012),(290,9,5505,80446,2,'80945','2026-09-08 09:00:00','80945','2026-09-08 09:00:00',_binary '\0',2012),(291,9,5506,80939,1,'80939','2026-09-10 09:00:00','80939','2026-09-10 09:00:00',_binary '\0',2012),(292,9,5506,80446,2,'80939','2026-09-10 09:00:00','80939','2026-09-10 09:00:00',_binary '\0',2012),(293,4,5013,80939,1,'80446','2026-09-17 14:56:51','80446','2026-09-17 14:56:51',_binary '\0',2012),(294,4,5014,80945,1,'80446','2026-09-17 15:00:30','80446','2026-09-17 15:00:30',_binary '\0',2012),(295,4,5015,80939,1,'80446','2026-09-17 15:03:34','80446','2026-09-17 15:03:34',_binary '\0',2012),(296,4,5016,80939,1,'80446','2026-09-17 15:07:15','80446','2026-09-17 15:07:15',_binary '\0',2012),(297,4,5017,80945,1,'80446','2026-09-17 15:09:47','80446','2026-09-17 15:09:47',_binary '\0',2012),(298,4,5018,80939,1,'80446','2026-09-17 15:11:51','80446','2026-09-17 15:11:51',_binary '\0',2012),(299,9,5507,80945,1,'80446','2026-09-17 15:32:24','80446','2026-09-17 15:32:24',_binary '\0',2012),(300,5,6014,80945,1,'80446','2026-09-17 15:33:18','80446','2026-09-17 15:33:18',_binary '\0',2012),(306,9,5510,80446,1,'80446','2026-09-17 16:28:53','80446','2026-09-17 16:28:53',_binary '\0',2012),(307,5,6017,80446,1,'80446','2026-09-17 16:30:02','80446','2026-09-17 16:30:02',_binary '\0',2012),(308,2,3040,80446,1,'80446','2026-09-17 16:53:16','80446','2026-09-17 16:53:16',_binary '\0',2012),(309,9,5511,80446,1,'80446','2026-09-17 16:53:16','80446','2026-09-17 16:53:16',_binary '\0',2012),(310,2,3041,80446,1,'80446','2026-09-17 17:15:44','80446','2026-09-19 14:12:38',_binary '',2012),(311,2,3042,80446,1,'80446','2026-09-17 17:15:45','80446','2026-09-17 17:15:45',_binary '\0',2012),(312,2,3043,80446,1,'80446','2026-09-17 17:15:45','80446','2026-09-17 17:15:45',_binary '\0',2012),(313,2,3044,80446,1,'80446','2026-09-17 17:15:45','80446','2026-09-17 17:15:45',_binary '\0',2012),(314,2,3045,80446,1,'80446','2026-09-17 17:15:45','80446','2026-09-17 17:15:45',_binary '\0',2012),(315,2,3046,80446,1,'80446','2026-09-17 17:15:45','80446','2026-09-17 17:15:45',_binary '\0',2012),(317,9,5513,80446,1,'80446','2026-09-18 08:48:05','80446','2026-09-18 08:48:05',_binary '\0',2012),(318,5,6018,80446,1,'80446','2026-09-18 08:50:11','80446','2026-09-18 08:50:11',_binary '\0',2012),(319,4,5019,80446,1,'80446','2026-09-18 09:52:48','80446','2026-09-18 09:54:53',_binary '',2012),(320,2,3101,80446,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(321,2,3101,80938,2,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(322,2,3101,80944,3,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(323,2,3102,80446,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(324,2,3102,80941,3,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(325,2,3103,80446,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(326,2,3103,80944,3,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(327,2,3104,80446,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(328,2,3105,80446,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(329,2,3105,80938,2,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(330,2,3106,80446,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(331,2,3106,80937,2,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(332,2,3107,80446,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(333,2,3108,80446,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(334,4,5101,80446,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(335,4,5101,80940,3,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(336,4,5102,80446,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(337,4,5103,80446,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(338,4,5105,80446,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(339,4,5105,80943,3,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(340,4,5106,80446,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(341,4,5107,80446,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(342,5,6101,80446,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(343,5,6101,80946,2,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(344,5,6102,80446,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(345,5,6103,80446,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(346,5,6104,80446,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(347,4,5108,80446,1,'80446','2026-09-18 11:06:04','80446','2026-09-18 11:06:32',_binary '',2012),(348,9,5514,80446,1,'80446','2026-09-18 16:57:00','80446','2026-09-18 16:57:00',_binary '\0',2012),(349,5,6105,80446,1,'80446','2026-09-18 16:57:15','80446','2026-09-18 16:57:15',_binary '\0',2012),(350,8,7106,80446,1,'80446','2026-09-18 16:57:55','80446','2026-09-18 16:57:55',_binary '\0',2012),(351,7,8103,80446,1,'80446','2026-09-18 16:58:49','80446','2026-09-18 16:58:49',_binary '\0',2012),(352,1,2110,80446,1,'80446','2026-09-18 17:12:07','80446','2026-09-18 17:12:07',_binary '\0',2012),(353,2,3111,80446,1,'80446','2026-09-18 17:12:40','80446','2026-09-18 17:12:40',_binary '\0',2012),(354,3,4111,80446,1,'80446','2026-09-18 17:18:46','80446','2026-09-18 17:42:17',_binary '',2012),(355,4,5109,80446,1,'80446','2026-09-18 17:19:40','80446','2026-09-18 17:19:40',_binary '\0',2012),(356,9,5515,80446,1,'80446','2026-09-18 17:22:02','80446','2026-09-18 17:22:02',_binary '\0',2012),(357,5,6106,80446,1,'80446','2026-09-18 17:22:24','80446','2026-09-18 17:42:51',_binary '',2012),(358,3,4112,80446,1,'80446','2026-09-19 14:28:15','80446','2026-09-19 14:28:28',_binary '',2012),(359,3,4113,80446,1,'80446','2026-09-19 14:28:15','80446','2026-09-19 14:28:25',_binary '',2012),(360,1,2001,80939,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(361,1,2002,80939,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(362,1,2003,80940,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(363,1,2004,80938,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(364,1,2005,80941,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(365,1,2006,80945,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(366,1,2007,80942,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(367,1,2008,80940,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(368,1,2101,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(369,1,2102,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(370,1,2103,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(371,1,2104,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(372,1,2105,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(373,1,2106,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(374,1,2107,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(375,1,2108,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(376,1,2109,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(377,2,3004,80941,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(378,2,3007,80940,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(379,3,4001,80939,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(380,3,4002,80939,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(381,3,4003,80939,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(382,3,4004,80939,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(383,3,4005,80940,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(384,3,4006,80941,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(385,3,4007,80945,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(386,3,4008,80938,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(387,3,4009,80940,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(388,3,4101,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(389,3,4102,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(390,3,4103,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(391,3,4104,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(392,3,4105,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(393,3,4106,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(394,3,4107,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(395,3,4108,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(396,3,4109,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(397,3,4110,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(398,4,5002,80939,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(399,4,5003,80940,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(400,4,5004,80941,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(401,4,5006,80940,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(402,4,5104,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(403,5,2,100,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',1),(404,5,6002,80945,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(405,5,6003,80939,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(406,6,3,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',1),(407,6,1001,80940,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(408,6,1002,80940,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(409,6,1003,80939,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(410,6,1004,80939,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(411,6,1005,80941,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(412,6,1006,80945,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(413,6,1007,80945,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(414,6,1008,80944,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(415,6,1009,80944,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(416,6,1010,80943,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(417,6,1011,80943,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(418,6,1012,80945,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(419,6,1013,80945,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(420,6,1014,80945,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(421,6,1016,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2005),(422,6,1017,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2005),(423,6,1018,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2005),(424,6,1019,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2005),(425,6,1020,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2005),(426,6,1021,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2005),(427,6,1022,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2005),(428,6,1023,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2005),(429,6,1024,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2005),(430,6,1025,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2005),(431,6,1026,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2005),(432,6,1027,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2005),(433,6,1028,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2005),(434,6,1029,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2005),(435,6,1030,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2005),(436,6,1031,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(437,6,1032,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(438,6,1033,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(439,6,1034,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(440,6,1035,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(441,6,1036,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(442,6,1037,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(443,6,1038,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(444,6,1039,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(445,6,1040,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(446,6,1041,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(447,6,1042,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(448,6,1043,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(449,6,1044,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(450,6,1045,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(451,6,1101,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(452,6,1102,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(453,6,1103,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(454,6,1104,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(455,6,1105,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(456,6,1106,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(457,6,1107,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(458,6,1108,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(459,6,1109,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(460,6,1110,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(461,6,1111,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(462,6,1112,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(463,6,1113,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(464,6,1114,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(465,7,10,1,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',1),(466,7,8001,80939,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(467,7,8002,80945,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(468,7,8101,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(469,7,8102,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(470,8,7001,80939,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(471,8,7002,80939,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(472,8,7003,80945,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(473,8,7101,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(474,8,7102,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(475,8,7103,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(476,8,7104,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012),(477,8,7105,80446,1,'1','2026-09-20 09:11:05','1','2026-09-20 09:11:05',_binary '\0',2012);
/*!40000 ALTER TABLE `crm_permission` ENABLE KEYS */;

--
-- Table structure for table `crm_product`
--

DROP TABLE IF EXISTS `crm_product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '产品编号',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产品名称',
  `no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产品编码',
  `unit` tinyint DEFAULT NULL COMMENT '单位',
  `price` decimal(24,6) DEFAULT '0.000000' COMMENT '价格，单位：元',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态',
  `category_id` bigint NOT NULL COMMENT '产品分类编号',
  `description` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '产品描述',
  `owner_user_id` bigint NOT NULL COMMENT '负责人的用户编号',
  `wms_item_id` bigint DEFAULT NULL COMMENT 'WMS 商品编号（外部标识）',
  `wms_sku_id` bigint DEFAULT NULL COMMENT 'WMS 规格编号（同步幂等键）',
  `wms_sku_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'WMS 规格编码',
  `data_source` tinyint NOT NULL DEFAULT '1' COMMENT '数据来源：1 手工创建，2 WMS 同步',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_wms_sku_id` (`tenant_id`,`wms_sku_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1115 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 产品表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_product`
--

/*!40000 ALTER TABLE `crm_product` DISABLE KEYS */;
INSERT INTO `crm_product` VALUES (3,'商品001','X001',1,100.000000,1,1,'我就介绍下',1,NULL,NULL,NULL,1,'1','2023-12-05 14:52:01','','2023-12-05 15:04:59',1,_binary '\0'),(4,'商品002','Q200',1,10010.000000,0,2,NULL,1,NULL,NULL,NULL,1,'1','2023-12-06 09:10:06','1','2026-09-15 19:37:14',1,_binary '\0'),(5,'我是产品','A110',1,100.300000,0,2,'啦啦啦啦',1,NULL,NULL,NULL,1,'1','2024-01-13 10:13:33','1','2024-02-21 11:23:11',1,_binary '\0'),(6,'演示产品','A8523',3,10.000000,0,2,NULL,1,NULL,NULL,NULL,1,'1','2024-02-24 09:46:17','1','2024-02-24 09:46:17',1,_binary '\0'),(1001,'3PE外防腐钢管 DN500','TX-3PE-500',1,860.000000,1,11,'意大利索克萨姆传送线生产，三层聚乙烯外防腐，适用于长输油气管道',80940,NULL,NULL,NULL,1,'1','2026-09-08 19:56:10','1','2026-09-08 20:44:38',2012,_binary '\0'),(1002,'3PE外防腐钢管 DN800','TX-3PE-800',1,1360.000000,1,11,'三层PE防腐，φ800，埋地长输管线',80940,NULL,NULL,NULL,1,'1','2026-09-08 19:56:10','1','2026-09-08 20:44:38',2012,_binary '\0'),(1003,'环氧酚醛内衬防腐油管 2-7/8\"','TX-HXF-278',3,185.000000,1,12,'环氧酚醛内涂层，耐腐蚀耐高温，延长油田在用规格',80939,NULL,NULL,NULL,1,'1','2026-09-08 19:56:10','1','2026-09-08 20:44:38',2012,_binary '\0'),(1004,'聚乙烯高分子内衬油管 2-7/8\"','TX-PE-278',3,168.000000,1,12,'聚乙烯高分子内衬，防腐耐磨，减阻降耗',80939,NULL,NULL,NULL,1,'1','2026-09-08 19:56:10','1','2026-09-08 20:44:38',2012,_binary '\0'),(1005,'内减阻涂层油管 2-3/8\"','TX-NJZ-238',3,152.000000,1,12,'内减阻涂层，降低举升能耗',80941,NULL,NULL,NULL,1,'1','2026-09-08 19:56:10','1','2026-09-08 20:44:38',2012,_binary '\0'),(1006,'钛纳米防腐涂层管','TX-TNM-01',7,220.000000,1,13,'钛纳米防腐，在建生产线产品，重防腐工况',80945,NULL,NULL,NULL,1,'1','2026-09-08 19:56:10','1','2026-09-08 20:44:38',2012,_binary '\0'),(1007,'环氧陶瓷内衬防腐管','TX-HTC-01',7,240.000000,1,13,'环氧陶瓷内衬，抗冲刷耐磨，注水/集输管线',80945,NULL,NULL,NULL,1,'1','2026-09-08 19:56:10','1','2026-09-08 20:44:38',2012,_binary '\0'),(1008,'聚氨酯预制直埋保温管 DN300','TX-BW-300',1,480.000000,1,20,'聚氨酯发泡保温+高密度聚乙烯外护，市政供热/输油伴热',80944,NULL,NULL,NULL,1,'1','2026-09-08 19:56:10','1','2026-09-08 20:44:38',2012,_binary '\0'),(1009,'聚氨酯保温保冷管 DN200','TX-BW-200',1,360.000000,1,20,'保温保冷一体，油气地面工艺管线',80944,NULL,NULL,NULL,1,'1','2026-09-08 19:56:10','1','2026-09-08 20:44:38',2012,_binary '\0'),(1010,'油管修复再制造服务','TX-XF-YG',3,68.000000,1,30,'废旧油管检测、除锈、修复、再制造，六条生产线之一',80943,NULL,NULL,NULL,1,'1','2026-09-08 19:56:10','1','2026-09-08 20:44:38',2012,_binary '\0'),(1011,'抽油杆防腐再制造服务','TX-XF-YG2',3,45.000000,1,30,'抽油杆防腐处理与再制造',80943,NULL,NULL,NULL,1,'1','2026-09-08 19:56:10','1','2026-09-08 20:44:38',2012,_binary '\0'),(1012,'管道漏磁内检测服务','TX-JC-LC',6,12000.000000,1,40,'在役管道漏磁/超声内检测，完整性评价',80945,NULL,NULL,NULL,1,'1','2026-09-08 19:56:10','1','2026-09-08 20:44:38',2012,_binary '\0'),(1013,'防腐层电火花检漏服务','TX-JC-DHH',6,3500.000000,1,40,'防腐层针孔/破损电火花检测',80945,NULL,NULL,NULL,1,'1','2026-09-08 19:56:10','1','2026-09-08 20:44:38',2012,_binary '\0'),(1014,'环氧陶瓷内衬修复涂料','TX-CL-HTC',2,8600.000000,0,13,'配套涂料，暂下架',80945,NULL,NULL,NULL,1,'1','2026-09-08 19:56:10','1','2026-09-08 20:44:38',2012,_binary '\0'),(1015,'3PE 防腐钢管 DN200','PRD-3PE-200',9,6000.000000,1,20,'3PE 加强级防腐，适合油田集输管线',80939,NULL,NULL,NULL,1,'80446','2026-09-10 09:09:03','80446','2026-09-10 09:09:34',2012,_binary ''),(1016,'玄武岩矿料 粒径20–40mm；1吨/袋','S96707754',9,0.000000,1,41,'WMS 同步：物料【I76818584 玄武岩矿料】，规格【S96707754 粒径20–40mm；1吨/袋】',1,10014,20020,'S96707754',2,'80168','2026-09-16 09:50:19','80168','2026-09-16 09:50:19',2005,_binary '\0'),(1017,'酚醛粘结树脂 固含量45%；200kg/桶','S25350153',10,0.000000,1,41,'WMS 同步：物料【I81653348 酚醛粘结树脂】，规格【S25350153 固含量45%；200kg/桶】',1,10015,20021,'S25350153',2,'80168','2026-09-16 09:50:19','80168','2026-09-16 09:50:19',2005,_binary '\0'),(1018,'岩棉防尘油 200kg/桶','JY-RAW-OIL-200',10,0.000000,1,41,'WMS 同步：物料【JY-RAW-OIL 岩棉防尘油】，规格【JY-RAW-OIL-200 200kg/桶】',1,10016,20022,'JY-RAW-OIL-200',2,'80168','2026-09-16 09:50:19','80168','2026-09-16 09:50:19',2005,_binary '\0'),(1019,'岩棉包装膜 宽1200mm；厚0.08mm；500m/卷','JY-PKG-FILM-1200-008',20,0.000000,1,41,'WMS 同步：物料【JY-PKG-FILM 岩棉包装膜】，规格【JY-PKG-FILM-1200-008 宽1200mm；厚0.08mm；500m/卷】',1,10017,20023,'JY-PKG-FILM-1200-008',2,'80168','2026-09-16 09:50:19','80168','2026-09-16 09:50:19',2005,_binary '\0'),(1020,'憎水岩棉板 1200×600×50mm；密度100kg/m³；8片/包','JY-FG-BOARD-120060050-D100',21,0.000000,1,41,'WMS 同步：物料【JY-FG-BOARD 憎水岩棉板】，规格【JY-FG-BOARD-120060050-D100 1200×600×50mm；密度100kg/m³；8片/包】',1,10018,20024,'JY-FG-BOARD-120060050-D100',2,'80168','2026-09-16 09:50:19','80168','2026-09-16 09:50:19',2005,_binary '\0'),(1021,'憎水岩棉板 1200×600×80mm；密度120kg/m³；5片/包','JY-FG-BOARD-120060080-D120',21,0.000000,1,41,'WMS 同步：物料【JY-FG-BOARD 憎水岩棉板】，规格【JY-FG-BOARD-120060080-D120 1200×600×80mm；密度120kg/m³；5片/包】',1,10018,20025,'JY-FG-BOARD-120060080-D120',2,'80168','2026-09-16 09:50:19','80168','2026-09-16 09:50:19',2005,_binary '\0'),(1022,'岩棉管壳 DN100；保温厚50mm；密度120kg/m³；1m/根×8根','JY-FG-PIPE-DN100-T50-D120',12,0.000000,1,41,'WMS 同步：物料【JY-FG-PIPE 岩棉管壳】，规格【JY-FG-PIPE-DN100-T50-D120 DN100；保温厚50mm；密度120kg/m³；1m/根×8根】',1,10019,20026,'JY-FG-PIPE-DN100-T50-D120',2,'80168','2026-09-16 09:50:19','80168','2026-09-16 09:50:19',2005,_binary '\0'),(1023,'岩棉毡 宽1000mm；厚50mm；密度80kg/m³；长5m','JY-FG-BLANKET-1000-T50-D80',20,0.000000,1,41,'WMS 同步：物料【JY-FG-BLANKET 岩棉毡】，规格【JY-FG-BLANKET-1000-T50-D80 宽1000mm；厚50mm；密度80kg/m³；长5m】',1,10020,20027,'JY-FG-BLANKET-1000-T50-D80',2,'80168','2026-09-16 09:50:19','80168','2026-09-16 09:50:19',2005,_binary '\0'),(1024,'WMS联调测试原料0912 联调专用','WMS-TEST-RAW-0912-DEFAULT',1,0.000000,1,41,'WMS 同步：物料【WMS-TEST-RAW-0912 WMS联调测试原料0912】，规格【WMS-TEST-RAW-0912-DEFAULT 联调专用】',1,10021,20028,'WMS-TEST-RAW-0912-DEFAULT',2,'80168','2026-09-16 09:50:19','80168','2026-09-16 09:50:19',2005,_binary '\0'),(1025,'四业务本机验收-RAW-0912 验收专用','WMS4-RAW-0912-SKU',1,0.000000,1,41,'WMS 同步：物料【WMS4-RAW-0912 四业务本机验收-RAW-0912】，规格【WMS4-RAW-0912-SKU 验收专用】',1,10022,20029,'WMS4-RAW-0912-SKU',2,'80168','2026-09-16 09:50:19','80168','2026-09-16 09:50:19',2005,_binary '\0'),(1026,'四业务本机验收-FG-0912 验收专用','WMS4-FG-0912-SKU',1,0.000000,1,41,'WMS 同步：物料【WMS4-FG-0912 四业务本机验收-FG-0912】，规格【WMS4-FG-0912-SKU 验收专用】',1,10023,20030,'WMS4-FG-0912-SKU',2,'80168','2026-09-16 09:50:19','80168','2026-09-16 09:50:19',2005,_binary '\0'),(1027,'电脑主机 i5','S12186964',1,0.000000,1,41,'WMS 同步：物料【I49373767 电脑主机】，规格【S12186964 i5】',1,10024,20031,'S12186964',2,'80168','2026-09-16 09:50:19','80168','2026-09-16 09:50:19',2005,_binary '\0'),(1028,'金刚石 金刚石1','S90553428',1,0.000000,1,41,'WMS 同步：物料【I46515893 金刚石】，规格【S90553428 金刚石1】',1,10025,20032,'S90553428',2,'80168','2026-09-16 09:50:19','80168','2026-09-16 09:50:19',2005,_binary '\0'),(1029,'金刚石2 test','S76780999',1,0.000000,1,41,'WMS 同步：物料【I87673239 金刚石2】，规格【S76780999 test】',1,10026,20033,'S76780999',2,'80168','2026-09-16 09:50:19','80168','2026-09-16 09:50:19',2005,_binary '\0'),(1030,'金刚石3 金刚石3','S04400689',1,0.000000,1,41,'WMS 同步：物料【I50363569 金刚石3】，规格【S04400689 金刚石3】',1,10027,20034,'S04400689',2,'80168','2026-09-16 09:50:20','80168','2026-09-16 09:50:20',2005,_binary '\0'),(1031,'玄武岩矿料 粒径20–40mm；1吨/袋','S96707754',9,0.000000,1,42,'WMS 同步：物料【I76818584 玄武岩矿料】，规格【S96707754 粒径20–40mm；1吨/袋】',1,810014,820020,'S96707754',2,'80446','2026-09-16 10:02:43','80446','2026-09-16 10:02:43',2012,_binary '\0'),(1032,'酚醛粘结树脂 固含量45%；200kg/桶','S25350153',10,0.000000,1,42,'WMS 同步：物料【I81653348 酚醛粘结树脂】，规格【S25350153 固含量45%；200kg/桶】',1,810015,820021,'S25350153',2,'80446','2026-09-16 10:02:43','80446','2026-09-16 10:02:43',2012,_binary '\0'),(1033,'岩棉防尘油 200kg/桶','JY-RAW-OIL-200',10,0.000000,1,42,'WMS 同步：物料【JY-RAW-OIL 岩棉防尘油】，规格【JY-RAW-OIL-200 200kg/桶】',1,810016,820022,'JY-RAW-OIL-200',2,'80446','2026-09-16 10:02:43','80446','2026-09-16 10:02:43',2012,_binary '\0'),(1034,'岩棉包装膜 宽1200mm；厚0.08mm；500m/卷','JY-PKG-FILM-1200-008',20,0.000000,1,42,'WMS 同步：物料【JY-PKG-FILM 岩棉包装膜】，规格【JY-PKG-FILM-1200-008 宽1200mm；厚0.08mm；500m/卷】',1,810017,820023,'JY-PKG-FILM-1200-008',2,'80446','2026-09-16 10:02:43','80446','2026-09-16 10:02:43',2012,_binary '\0'),(1035,'憎水岩棉板 1200×600×50mm；密度100kg/m³；8片/包','JY-FG-BOARD-120060050-D100',21,0.000000,1,42,'WMS 同步：物料【JY-FG-BOARD 憎水岩棉板】，规格【JY-FG-BOARD-120060050-D100 1200×600×50mm；密度100kg/m³；8片/包】',1,810018,820024,'JY-FG-BOARD-120060050-D100',2,'80446','2026-09-16 10:02:43','80446','2026-09-16 10:02:43',2012,_binary '\0'),(1036,'憎水岩棉板 1200×600×80mm；密度120kg/m³；5片/包','JY-FG-BOARD-120060080-D120',21,0.000000,1,42,'WMS 同步：物料【JY-FG-BOARD 憎水岩棉板】，规格【JY-FG-BOARD-120060080-D120 1200×600×80mm；密度120kg/m³；5片/包】',1,810018,820025,'JY-FG-BOARD-120060080-D120',2,'80446','2026-09-16 10:02:43','80446','2026-09-16 10:02:43',2012,_binary '\0'),(1037,'岩棉管壳 DN100；保温厚50mm；密度120kg/m³；1m/根×8根','JY-FG-PIPE-DN100-T50-D120',12,0.000000,1,42,'WMS 同步：物料【JY-FG-PIPE 岩棉管壳】，规格【JY-FG-PIPE-DN100-T50-D120 DN100；保温厚50mm；密度120kg/m³；1m/根×8根】',1,810019,820026,'JY-FG-PIPE-DN100-T50-D120',2,'80446','2026-09-16 10:02:43','80446','2026-09-16 10:02:43',2012,_binary '\0'),(1038,'岩棉毡 宽1000mm；厚50mm；密度80kg/m³；长5m','JY-FG-BLANKET-1000-T50-D80',20,0.000000,1,42,'WMS 同步：物料【JY-FG-BLANKET 岩棉毡】，规格【JY-FG-BLANKET-1000-T50-D80 宽1000mm；厚50mm；密度80kg/m³；长5m】',1,810020,820027,'JY-FG-BLANKET-1000-T50-D80',2,'80446','2026-09-16 10:02:43','80446','2026-09-16 10:02:43',2012,_binary '\0'),(1039,'WMS联调测试原料0912 联调专用','WMS-TEST-RAW-0912-DEFAULT',1,0.000000,1,42,'WMS 同步：物料【WMS-TEST-RAW-0912 WMS联调测试原料0912】，规格【WMS-TEST-RAW-0912-DEFAULT 联调专用】',1,810021,820028,'WMS-TEST-RAW-0912-DEFAULT',2,'80446','2026-09-16 10:02:43','80446','2026-09-16 10:02:43',2012,_binary '\0'),(1040,'四业务本机验收-RAW-0912 验收专用','WMS4-RAW-0912-SKU',1,0.000000,1,42,'WMS 同步：物料【WMS4-RAW-0912 四业务本机验收-RAW-0912】，规格【WMS4-RAW-0912-SKU 验收专用】',1,810022,820029,'WMS4-RAW-0912-SKU',2,'80446','2026-09-16 10:02:43','80446','2026-09-16 10:02:43',2012,_binary '\0'),(1041,'四业务本机验收-FG-0912 验收专用','WMS4-FG-0912-SKU',1,0.000000,1,42,'WMS 同步：物料【WMS4-FG-0912 四业务本机验收-FG-0912】，规格【WMS4-FG-0912-SKU 验收专用】',1,810023,820030,'WMS4-FG-0912-SKU',2,'80446','2026-09-16 10:02:43','80446','2026-09-16 10:02:43',2012,_binary '\0'),(1042,'电脑主机 i5','S12186964',1,0.000000,0,42,'WMS 同步：物料【I49373767 电脑主机】，规格【S12186964 i5】',1,810024,820031,'S12186964',2,'80446','2026-09-16 10:02:43','80446','2026-09-16 10:16:58',2012,_binary '\0'),(1043,'金刚石 金刚石1','S90553428',1,0.000000,1,42,'WMS 同步：物料【I46515893 金刚石】，规格【S90553428 金刚石1】',1,810025,820032,'S90553428',2,'80446','2026-09-16 10:02:43','80446','2026-09-16 10:02:43',2012,_binary '\0'),(1044,'金刚石2 test','S76780999',1,0.000000,0,42,'WMS 同步：物料【I87673239 金刚石2】，规格【S76780999 test】',1,810026,820033,'S76780999',2,'80446','2026-09-16 10:02:43','80446','2026-09-16 10:16:58',2012,_binary '\0'),(1045,'金刚石3 金刚石3','S04400689',1,0.000000,1,42,'WMS 同步：物料【I50363569 金刚石3】，规格【S04400689 金刚石3】',1,810027,820034,'S04400689',2,'80446','2026-09-16 10:02:43','80446','2026-09-16 10:13:20',2012,_binary '\0'),(1101,'3PE外防腐钢管 DN300','TX-3PE-300',1,620.000000,1,11,'三层聚乙烯外防腐，φ300，适用于支线油气管道',80446,NULL,NULL,NULL,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',2012,_binary '\0'),(1102,'3PE外防腐钢管 DN600','TX-3PE-600',1,1080.000000,1,11,'三层PE防腐，φ600，埋地长输支线',80446,NULL,NULL,NULL,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',2012,_binary '\0'),(1103,'环氧酚醛内衬防腐油管 3-1/2\"','TX-HXF-350',3,215.000000,1,12,'环氧酚醛内涂层，耐热耐腐蚀，注水井适用',80446,NULL,NULL,NULL,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',2012,_binary '\0'),(1104,'聚乙烯高分子内衬油管 2-7/8\"','TX-PE-278B',3,176.000000,1,12,'高分子内衬，防腐耐磨，延长修井周期',80446,NULL,NULL,NULL,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',2012,_binary '\0'),(1105,'内减阻涂层油管 2-7/8\"','TX-NJZ-278',3,158.000000,1,12,'内减阻涂层，降低举升能耗',80446,NULL,NULL,NULL,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',2012,_binary '\0'),(1106,'钛纳米防腐涂层管 DN200','TX-TNM-200',7,235.000000,1,13,'钛纳米防腐涂层，强腐蚀工况适用',80446,NULL,NULL,NULL,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',2012,_binary '\0'),(1107,'环氧陶瓷内衬防腐管 DN250','TX-HTC-250',7,268.000000,1,13,'环氧陶瓷内衬，抗冲刷耐磨，注水/集输管线',80446,NULL,NULL,NULL,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',2012,_binary '\0'),(1108,'聚氨酯预制直埋保温管 DN400','TX-BW-400',1,560.000000,1,20,'聚氨酯发泡保温+高密度聚乙烯外护，市政供热长输管网',80446,NULL,NULL,NULL,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',2012,_binary '\0'),(1109,'复合硅酸盐保温管 DN150','TX-BW-150',1,290.000000,1,20,'复合硅酸盐保温，油气地面伴热工艺管线',80446,NULL,NULL,NULL,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',2012,_binary '\0'),(1110,'油管检测修复再制造服务','TX-XF-YG3',3,72.000000,1,30,'废旧油管探伤、除锈、修复、试压再制造',80446,NULL,NULL,NULL,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',2012,_binary '\0'),(1111,'抽油杆防腐再制造服务','TX-XF-YG4',3,48.000000,1,30,'抽油杆检测分选、防腐处理与再制造',80446,NULL,NULL,NULL,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',2012,_binary '\0'),(1112,'管道超声内检测服务','TX-JC-CS',6,9800.000000,1,40,'在役管道超声内检测，壁厚减薄与缺陷定量',80446,NULL,NULL,NULL,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',2012,_binary '\0'),(1113,'防腐层完整性检测服务','TX-JC-WZX',6,4200.000000,1,40,'外防腐层检测与完整性评价',80446,NULL,NULL,NULL,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',2012,_binary '\0'),(1114,'环氧陶瓷内衬修复涂料','TX-CL-HTC2',2,8800.000000,0,13,'配套涂料，暂下架',80446,NULL,NULL,NULL,1,'1','2026-09-18 10:59:01','','2026-09-18 10:59:01',2012,_binary '\0');
/*!40000 ALTER TABLE `crm_product` ENABLE KEYS */;

--
-- Table structure for table `crm_product_category`
--

DROP TABLE IF EXISTS `crm_product_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_product_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类编号',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类名称',
  `parent_id` bigint NOT NULL COMMENT '父级编号',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=43 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 产品分类表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_product_category`
--

/*!40000 ALTER TABLE `crm_product_category` DISABLE KEYS */;
INSERT INTO `crm_product_category` VALUES (1,'普通分类',0,'','2023-12-05 14:54:34','','2023-12-05 14:54:37',_binary '\0',1),(2,'二级分类',1,'','2023-12-06 01:09:27','1','2024-02-24 08:59:02',_binary '\0',1),(6,'另外一个分类',0,'1','2023-12-06 12:55:07','1','2023-12-06 12:55:07',_binary '\0',1),(10,'管道防腐产品',0,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(11,'3PE防腐类',10,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(12,'内衬防腐类',10,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(13,'纳米/陶瓷防腐类',10,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(20,'管道保温保冷产品',0,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(30,'油管油杆再制造',0,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(40,'管道检测与技术服务',0,'1','2026-09-08 19:56:10','','2026-09-08 19:56:10',_binary '\0',2012),(41,'WMS 同步产品',0,'80168','2026-09-16 09:50:18','80168','2026-09-16 09:50:18',_binary '\0',2005),(42,'WMS 同步产品',0,'80446','2026-09-16 10:02:43','80446','2026-09-16 10:02:43',_binary '\0',2012);
/*!40000 ALTER TABLE `crm_product_category` ENABLE KEYS */;

--
-- Table structure for table `crm_quotation`
--

DROP TABLE IF EXISTS `crm_quotation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_quotation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '报价编号，主键自增',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '报价名称',
  `no` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '报价单号',
  `quotation_type` tinyint NOT NULL DEFAULT '1' COMMENT '报价类型：1=购销报价 2=采购报价',
  `customer_id` bigint NOT NULL COMMENT '客户编号',
  `supplier_contact` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商联系人',
  `supplier_phone` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商联系电话',
  `supplier_address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商地址',
  `business_id` bigint DEFAULT NULL COMMENT '商机编号',
  `owner_user_id` bigint DEFAULT NULL COMMENT '负责人的用户编号',
  `order_date` datetime DEFAULT NULL COMMENT '报价日期',
  `valid_until` datetime DEFAULT NULL COMMENT '有效期至',
  `process_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工作流编号',
  `audit_status` tinyint NOT NULL DEFAULT '0' COMMENT '审批状态',
  `contract_id` bigint DEFAULT NULL COMMENT '生成的合同编号',
  `total_product_price` decimal(24,6) DEFAULT NULL COMMENT '产品总金额',
  `discount_percent` decimal(24,6) DEFAULT NULL COMMENT '整单折扣',
  `total_price` decimal(10,2) DEFAULT NULL COMMENT '报价总金额',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_owner_audit_id` (`owner_user_id`,`audit_status`,`id`) USING BTREE,
  KEY `idx_no` (`no`) USING BTREE,
  KEY `idx_customer_id` (`customer_id`) USING BTREE,
  KEY `idx_business_id` (`business_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=5516 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 报价单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_quotation`
--

/*!40000 ALTER TABLE `crm_quotation` DISABLE KEYS */;
INSERT INTO `crm_quotation` VALUES (1,'陕西延长石油（集团）有限责任公司','BJ20260914000001',1,3002,NULL,NULL,NULL,5002,80446,'2026-09-17 09:34:32',NULL,NULL,0,NULL,0.000000,0.000000,0.00,NULL,'80446','2026-09-14 09:34:52','80446','2026-09-14 10:49:26',_binary '',2012),(2,'中石油管道局西部管道防腐入围','BJ20260914000002',1,3002,NULL,NULL,NULL,5002,80446,'2026-09-14 10:16:03',NULL,NULL,0,NULL,0.000000,0.000000,0.00,NULL,'80446','2026-09-14 10:16:16','80446','2026-09-14 10:49:24',_binary '',2012),(3,'中石油管道局西部管道防腐入围','BJ20260915000001',1,3006,NULL,NULL,NULL,5011,80446,'2026-09-15 11:02:16',NULL,'c02b0193-b0e1-11f1-b65a-5ead4bc7e282',20,6011,0.000000,5.000000,0.00,NULL,'80446','2026-09-15 11:02:44','80446','2026-09-15 17:15:17',_binary '\0',2012),(4,'长安石油','BJ20260915000002',1,3007,NULL,NULL,NULL,5012,80446,'2026-09-15 15:09:49',NULL,'7c5fcfc9-b0e1-11f1-b65a-5ead4bc7e282',20,6010,0.000000,4.000000,0.00,NULL,'80446','2026-09-15 15:10:09','80446','2026-09-15 17:14:48',_binary '\0',2012),(5501,'靖边采油厂油管防腐报价(早期版)','TXGY-BJ-2026-0001',1,3002,NULL,NULL,NULL,5002,80939,'2026-07-20 00:00:00','2026-08-20 00:00:00',NULL,40,NULL,1177000.000000,100.000000,1177000.00,'早期报价有效期届满未成交，系统自动置为已失效（本环境无「已失效」状态，按「已取消」入库）','80939','2026-07-20 09:00:00','80939','2026-07-20 09:00:00',_binary '\0',2012),(5502,'延安炼油厂管道内检测服务报价','TXGY-BJ-2026-0002',1,3005,NULL,NULL,NULL,5005,80945,'2026-08-22 00:00:00','2026-09-22 00:00:00',NULL,20,6002,36000.000000,100.000000,36000.00,'审批通过，已一键转管道内检测服务合同','80945','2026-08-22 09:00:00','80945','2026-08-22 09:00:00',_binary '\0',2012),(5503,'靖边采油厂油管防腐报价(初版)','TXGY-BJ-2026-0003',1,3002,NULL,NULL,NULL,5002,80939,'2026-08-26 00:00:00','2026-09-25 00:00:00',NULL,30,NULL,1485000.000000,100.000000,1485000.00,'销售售价偏高客户异议，审批驳回，退回修改后重报','80939','2026-08-26 09:00:00','80939','2026-08-26 09:00:00',_binary '\0',2012),(5504,'靖边采油厂油管防腐报价(修订版)','TXGY-BJ-2026-0004',1,3002,NULL,NULL,NULL,5002,80939,'2026-09-04 00:00:00','2026-10-04 00:00:00',NULL,20,6001,1429000.000000,100.000000,1429000.00,'审批通过，已一键转油管防腐采购合同','80939','2026-09-04 09:00:00','80939','2026-09-04 09:00:00',_binary '\0',2012),(5505,'延安炼油厂管道内检测服务报价(增补检测段)','TXGY-BJ-2026-0005',1,3005,NULL,NULL,NULL,5005,80945,'2026-09-08 00:00:00','2026-10-08 00:00:00',NULL,10,NULL,24000.000000,100.000000,24000.00,'增补检测段报价，已提交待审批','80945','2026-09-08 09:00:00','80945','2026-09-08 09:00:00',_binary '\0',2012),(5506,'靖边采油厂油管防腐报价(第二批追加)','TXGY-BJ-2026-0006',1,3002,NULL,NULL,NULL,5002,80939,'2026-09-10 00:00:00','2026-10-10 00:00:00',NULL,0,NULL,622000.000000,100.000000,622000.00,'第二批追加需求，草稿待提交','80939','2026-09-10 09:00:00','80939','2026-09-10 09:00:00',_binary '\0',2012),(5507,'延安炼油厂管道内检测服务报价','BJ20260917000001',1,3005,NULL,NULL,NULL,5014,80945,'2026-08-22 15:27:33','2026-09-22 15:28:26','029480ac-b26a-11f1-b6b5-06fd40868aaf',20,6014,36000.000000,0.000000,36000.00,'审核通过已一键转管道内检测服务合同','80446','2026-09-17 15:32:24','80446','2026-09-17 15:33:18',_binary '\0',2012),(5510,'靖边采油厂油管防腐年度采购','BJ20260917000004',1,3002,NULL,NULL,NULL,5016,80446,'2026-09-04 16:26:51','2026-10-04 16:26:54','f1313ae6-b271-11f1-b6b5-06fd40868aaf',20,6017,1429000.000000,0.000000,1429000.00,'审批通过已一键转油管防腐采购合同','80446','2026-09-17 16:28:53','80446','2026-09-17 16:30:02',_binary '\0',2012),(5511,'西安建元工程物资有限公司报价单','BJ20260917000005',1,3040,NULL,NULL,NULL,NULL,80446,'2026-09-17 16:53:07','2026-09-24 16:53:07',NULL,0,NULL,0.000000,0.000000,0.00,'联系人：安建元工','80446','2026-09-17 16:53:16','80446','2026-09-17 16:53:16',_binary '\0',2012),(5513,'靖边采油厂油管防腐年度采购','BJ20260918000001',1,3002,NULL,NULL,NULL,5016,80446,'2026-09-04 08:46:08','2026-10-08 08:46:19','dd6d9372-b2fa-11f1-a372-ced4ad4f3b4b',20,6018,1429000.000000,0.000000,1429000.00,'审核已通过，已一键转油管防腐采购合同','80446','2026-09-18 08:48:05','80446','2026-09-18 08:50:11',_binary '\0',2012),(5514,'长庆八厂集输管线3PE防腐报价单','BJ20260918000002',1,3101,NULL,NULL,NULL,5101,80446,'2026-09-18 16:56:58',NULL,'e972b91f-b33e-11f1-a372-ced4ad4f3b4b',20,6105,903600.000000,5.000000,858420.00,'端到端演示','80446','2026-09-18 16:57:00','80446','2026-09-18 16:57:15',_binary '\0',2012),(5515,'山西潞安煤基清洁能源有限公司报价单','BJ20260918000003',1,3111,NULL,NULL,NULL,5109,80446,'2026-09-18 17:20:22','2026-09-25 17:20:22','6d494e43-b342-11f1-a372-ced4ad4f3b4b',20,6106,302672.000000,1.000000,299645.28,'联系人：基厂区工；地址：潞安煤基厂区','80446','2026-09-18 17:22:02','80446','2026-09-18 17:22:24',_binary '\0',2012);
/*!40000 ALTER TABLE `crm_quotation` ENABLE KEYS */;

--
-- Table structure for table `crm_quotation_product`
--

DROP TABLE IF EXISTS `crm_quotation_product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_quotation_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `quotation_id` bigint NOT NULL COMMENT '报价编号',
  `product_id` bigint NOT NULL COMMENT '产品编号',
  `product_price` decimal(24,6) NOT NULL COMMENT '产品单价',
  `quotation_price` decimal(24,6) NOT NULL COMMENT '报价价格',
  `count` decimal(24,6) NOT NULL COMMENT '数量',
  `total_price` decimal(24,6) NOT NULL COMMENT '总计价格',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_quotation_id` (`quotation_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 报价单产品明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_quotation_product`
--

/*!40000 ALTER TABLE `crm_quotation_product` DISABLE KEYS */;
INSERT INTO `crm_quotation_product` VALUES (1,1,1002,1360.000000,1360.000000,0.000000,0.000000,'80446','2026-09-14 09:34:52','80446','2026-09-14 10:49:25',_binary '',2012),(2,2,1002,1360.000000,1360.000000,0.000000,0.000000,'80446','2026-09-14 10:16:16','80446','2026-09-14 10:49:23',_binary '',2012),(3,3,1005,152.000000,152.000000,0.000000,0.000000,'80446','2026-09-15 11:02:44','80446','2026-09-15 11:02:44',_binary '\0',2012),(4,4,1005,152.000000,152.000000,0.000000,0.000000,'80446','2026-09-15 15:10:09','80446','2026-09-15 15:10:09',_binary '\0',2012),(5,5501,1003,185.000000,188.000000,4000.000000,752000.000000,'80939','2026-07-20 09:00:00','80939','2026-07-20 09:00:00',_binary '\0',2012),(6,5501,1004,168.000000,170.000000,2500.000000,425000.000000,'80939','2026-07-20 09:00:00','80939','2026-07-20 09:00:00',_binary '\0',2012),(7,5502,1012,12000.000000,12000.000000,3.000000,36000.000000,'80945','2026-08-22 09:00:00','80945','2026-08-22 09:00:00',_binary '\0',2012),(8,5503,1003,185.000000,192.000000,5000.000000,960000.000000,'80939','2026-08-26 09:00:00','80939','2026-08-26 09:00:00',_binary '\0',2012),(9,5503,1004,168.000000,175.000000,3000.000000,525000.000000,'80939','2026-08-26 09:00:00','80939','2026-08-26 09:00:00',_binary '\0',2012),(10,5504,1003,185.000000,185.000000,5000.000000,925000.000000,'80939','2026-09-04 09:00:00','80939','2026-09-04 09:00:00',_binary '\0',2012),(11,5504,1004,168.000000,168.000000,3000.000000,504000.000000,'80939','2026-09-04 09:00:00','80939','2026-09-04 09:00:00',_binary '\0',2012),(12,5505,1012,12000.000000,12000.000000,2.000000,24000.000000,'80945','2026-09-08 09:00:00','80945','2026-09-08 09:00:00',_binary '\0',2012),(13,5506,1003,185.000000,185.000000,2000.000000,370000.000000,'80939','2026-09-10 09:00:00','80939','2026-09-10 09:00:00',_binary '\0',2012),(14,5506,1004,168.000000,168.000000,1500.000000,252000.000000,'80939','2026-09-10 09:00:00','80939','2026-09-10 09:00:00',_binary '\0',2012),(15,5507,1012,12000.000000,12000.000000,3.000000,36000.000000,'80446','2026-09-17 15:32:24','80446','2026-09-17 15:32:24',_binary '\0',2012),(16,5510,1003,185.000000,185.000000,5000.000000,925000.000000,'80446','2026-09-17 16:28:53','80446','2026-09-17 16:28:53',_binary '\0',2012),(17,5510,1004,168.000000,168.000000,3000.000000,504000.000000,'80446','2026-09-17 16:28:53','80446','2026-09-17 16:28:53',_binary '\0',2012),(18,5513,1003,185.000000,185.000000,5000.000000,925000.000000,'80446','2026-09-18 08:48:05','80446','2026-09-18 08:48:05',_binary '\0',2012),(19,5513,1004,168.000000,168.000000,3000.000000,504000.000000,'80446','2026-09-18 08:48:05','80446','2026-09-18 08:48:05',_binary '\0',2012),(20,5514,1101,620.000000,620.000000,900.000000,558000.000000,'80446','2026-09-18 16:57:00','80446','2026-09-18 16:57:00',_binary '\0',2012),(21,5514,1102,1080.000000,1080.000000,320.000000,345600.000000,'80446','2026-09-18 16:57:00','80446','2026-09-18 16:57:00',_binary '\0',2012),(22,5515,1004,168.000000,168.000000,4.000000,672.000000,'80446','2026-09-18 17:22:02','80446','2026-09-18 17:22:02',_binary '\0',2012),(23,5515,1001,860.000000,860.000000,300.000000,258000.000000,'80446','2026-09-18 17:22:02','80446','2026-09-18 17:22:02',_binary '\0',2012),(24,5515,1006,220.000000,220.000000,200.000000,44000.000000,'80446','2026-09-18 17:22:02','80446','2026-09-18 17:22:02',_binary '\0',2012);
/*!40000 ALTER TABLE `crm_quotation_product` ENABLE KEYS */;

--
-- Table structure for table `crm_receivable`
--

DROP TABLE IF EXISTS `crm_receivable`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_receivable` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `no` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '回款编号',
  `plan_id` bigint DEFAULT NULL COMMENT '回款计划ID',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `contract_id` bigint NOT NULL COMMENT '合同ID',
  `owner_user_id` bigint DEFAULT NULL COMMENT '负责人的用户编号',
  `audit_status` tinyint NOT NULL COMMENT '审批状态',
  `process_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工作流编号',
  `return_time` datetime DEFAULT NULL COMMENT '回款日期',
  `return_type` int DEFAULT NULL COMMENT '回款方式',
  `price` decimal(24,6) NOT NULL COMMENT '回款金额',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_owner_audit_id` (`owner_user_id`,`audit_status`,`id`) USING BTREE,
  KEY `idx_contract_audit` (`contract_id`,`audit_status`) USING BTREE,
  KEY `idx_no` (`no`) USING BTREE,
  KEY `idx_customer_id` (`customer_id`) USING BTREE,
  KEY `idx_plan_id` (`plan_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=8104 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 回款表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_receivable`
--

/*!40000 ALTER TABLE `crm_receivable` DISABLE KEYS */;
INSERT INTO `crm_receivable` VALUES (10,'Q233',1,2,1,1,20,NULL,'2024-02-02 19:02:17',1,88.000000,NULL,'1','2023-12-01 10:44:16','','2024-02-02 17:06:38',_binary '\0',1),(11,'AAA',2,16,12,1,0,NULL,'2024-02-14 00:00:00',2,52.000000,'1000','1','2024-02-25 09:17:22','1','2024-02-25 09:17:22',_binary '\0',1),(13,'HK20240225000001',NULL,16,12,1,10,'9084ad68-6d2f-11f0-8f64-ca5778fb00b9','2024-02-27 00:00:00',3,10.000000,'哈哈哈哈','1','2024-02-25 18:13:42','1','2025-07-30 18:25:51',_binary '\0',1),(14,'HK20240225000002',5,16,12,1,0,NULL,'2024-02-27 00:00:00',4,20.000000,'哈哈哈哈','1','2024-02-25 18:24:42','1','2024-02-25 18:25:10',_binary '\0',1),(15,'HK20240225000003',5,16,12,1,0,NULL,'2024-02-14 00:00:00',4,10.000000,NULL,'1','2024-02-25 18:27:45','1','2024-02-25 18:27:45',_binary '\0',1),(16,'HK20240225000004',5,16,12,1,0,NULL,'2024-02-19 00:00:00',4,10.000000,NULL,'1','2024-02-25 18:28:37','1','2024-02-25 18:28:37',_binary '\0',1),(17,'HK20240225000005',5,16,12,1,0,NULL,'2024-02-27 00:00:00',4,10.000000,NULL,'1','2024-02-25 18:29:47','1','2024-02-25 18:29:47',_binary '\0',1),(18,'HK20240225000006',5,16,12,1,0,NULL,'2024-02-27 00:00:00',4,10.000000,NULL,'1','2024-02-25 18:30:08','1','2024-02-25 18:30:08',_binary '\0',1),(19,'HK20240225000007',5,16,12,1,20,'e50a6752-6d2f-11f0-8f64-ca5778fb00b9','2024-03-04 00:00:00',4,10.000000,'31321','1','2024-02-25 18:31:00',NULL,'2025-07-30 18:28:33',_binary '\0',1),(20,'HK20240225000008',NULL,16,12,1,10,'d9c7dd95-6d2f-11f0-8f64-ca5778fb00b9','2024-02-29 00:00:00',5,0.000000,NULL,'1','2024-02-25 18:32:10','1','2025-07-30 18:28:03',_binary '',1),(21,'HK20240225000009',5,16,12,1,10,'d5ab93e3-6d2e-11f0-8f64-ca5778fb00b9','2024-02-19 00:00:00',4,10.000000,NULL,'1','2024-02-25 18:32:27','1','2025-07-30 18:20:37',_binary '\0',1),(22,'HK20240225000010',5,16,12,1,10,'1d14f04b-6d2e-11f0-bb29-ca5778fb00b9','2024-02-28 00:00:00',4,10.000000,'32321','1','2024-02-25 18:33:35','1','2025-07-30 18:15:27',_binary '\0',1),(23,'HK20240225000011',4,16,12,1,20,'8886586f-d3d3-11ee-aa2f-26aa5e0b65cc','2024-02-29 00:00:00',2,10.000000,'哈哈哈哈111','1','2024-02-25 18:43:59','1','2024-02-25 19:51:00',_binary '\0',1),(24,'HK20240225000012',NULL,16,12,1,20,'11435b77-6d30-11f0-8f64-ca5778fb00b9','2024-02-16 00:00:00',1,0.000000,'3232312','1','2024-02-25 19:59:06',NULL,'2025-07-30 18:30:04',_binary '\0',1),(25,'HK20240225000013',6,16,12,1,10,'9bc4b09e-6d2d-11f0-b888-ca5778fb00b9','2024-02-15 00:00:00',3,10.000000,'132312','1','2024-02-25 20:17:52','1','2025-07-30 18:11:50',_binary '\0',1),(26,'HK20240225000014',NULL,16,12,1,10,'779dedc9-6d2d-11f0-b888-ca5778fb00b9','2024-02-14 00:00:00',4,0.000000,'10000','1','2024-02-25 20:21:12','1','2025-07-30 18:29:14',_binary '',1),(27,'HK20240225000015',NULL,16,12,1,10,'cb02a4dd-6d29-11f0-b888-ca5778fb00b9','2024-02-27 00:00:00',4,0.000000,'1111','1','2024-02-25 20:22:36','1','2025-07-30 18:29:09',_binary '',1),(28,'HK20240225000016',NULL,16,10,1,20,'59503b13-d3dc-11ee-853e-2ea58da017f0','2024-02-16 00:00:00',NULL,888.000000,NULL,'1','2024-02-25 20:49:27','1','2024-02-25 20:49:47',_binary '\0',1),(29,'HK20240225000017',NULL,17,13,1,20,'a213f8bb-d3f5-11ee-853e-2ea58da017f0','2024-02-28 00:00:00',NULL,16016.000000,NULL,'1','2024-02-25 23:40:10','1','2024-02-25 23:52:28',_binary '\0',1),(30,'HK20240226000001',NULL,16,10,1,10,'9f398f3c-d447-11ee-ac04-62f15b703df8','2024-02-27 00:00:00',NULL,222.000000,'hhhh','1','2024-02-26 09:37:07','1','2025-07-30 18:27:16',_binary '',1),(8001,'TXGY-SK-8001',7001,3002,6001,80939,20,'BPM-R-8001','2026-09-18 00:00:00',1,693065.000000,'第一期(预付款50%)回款','1','2026-09-08 19:56:10','1','2026-09-18 16:43:24',_binary '\0',2012),(8002,'TXGY-SK-8002',7003,3005,6002,80945,20,'BPM-R-8002','2026-10-12 00:00:00',1,36000.000000,'全款(检测完成后)回款','1','2026-09-08 19:56:10','1','2026-09-18 16:43:24',_binary '\0',2012),(8101,'TXGY-SK-8101',7101,3102,6101,80446,20,'BPM-R-8101','2026-09-20 00:00:00',1,797825.000000,'第一期(预付款50%)回款','1','2026-09-18 10:59:01','','2026-09-18 16:43:24',_binary '\0',2012),(8102,'TXGY-SK-8102',7103,3105,6102,80446,20,'BPM-R-8102','2026-10-16 00:00:00',1,39200.000000,'全款(检测完成后)回款','1','2026-09-18 10:59:01','','2026-09-18 16:43:24',_binary '\0',2012),(8103,'HK20260918000001',7106,3101,6105,80446,20,'68d44831-b33f-11f1-a372-ced4ad4f3b4b','2026-09-18 16:58:48',1,858420.000000,'第一期(验收款100%)已到账','80446','2026-09-18 16:58:49',NULL,'2026-09-18 17:00:39',_binary '\0',2012);
/*!40000 ALTER TABLE `crm_receivable` ENABLE KEYS */;

--
-- Table structure for table `crm_receivable_plan`
--

DROP TABLE IF EXISTS `crm_receivable_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_receivable_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `period` bigint NOT NULL COMMENT '期数',
  `customer_id` bigint NOT NULL COMMENT '客户编号',
  `contract_id` bigint NOT NULL COMMENT '合同编号',
  `owner_user_id` bigint DEFAULT NULL COMMENT '负责人编号',
  `receivable_id` bigint DEFAULT NULL COMMENT '回款编号',
  `return_time` datetime DEFAULT NULL COMMENT '计划回款日期',
  `return_type` tinyint DEFAULT NULL COMMENT '计划还款方式',
  `price` decimal(24,6) NOT NULL COMMENT '计划回款金额',
  `remind_days` bigint DEFAULT NULL COMMENT '提前几天提醒',
  `remind_time` datetime DEFAULT NULL COMMENT '提醒日期',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_contract_id_period` (`contract_id`,`period`) USING BTREE,
  KEY `idx_customer_contract_period` (`customer_id`,`contract_id`,`period`) USING BTREE,
  KEY `idx_receivable_remind_time` (`receivable_id`,`remind_time`) USING BTREE,
  KEY `idx_receivable_return_time` (`receivable_id`,`return_time`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=7107 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 回款计划表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `crm_receivable_plan`
--

/*!40000 ALTER TABLE `crm_receivable_plan` DISABLE KEYS */;
INSERT INTO `crm_receivable_plan` VALUES (4,1,16,12,1,23,'2024-02-16 00:00:00',2,10.000000,10,'2024-02-06 00:00:00','llll','1','2024-02-25 16:08:57','1','2024-02-25 18:43:59',_binary '\0',1),(5,2,16,12,1,22,'2024-02-27 00:00:00',4,10.000000,NULL,NULL,NULL,'1','2024-02-25 18:24:00','1','2024-02-25 18:33:35',_binary '\0',1),(6,3,16,12,1,25,'2024-02-20 00:00:00',3,10.000000,10,'2024-02-10 00:00:00','哈哈哈哈','1','2024-02-25 20:11:49','1','2024-02-25 20:17:52',_binary '\0',1),(7,4,16,12,1,NULL,'2024-02-20 00:00:00',NULL,1.000000,10,'2024-02-10 00:00:00',NULL,'1','2024-02-26 00:08:48','1','2024-02-26 01:47:41',_binary '\0',1),(8,1,17,13,1,NULL,'2024-02-29 00:00:00',NULL,220.000000,10,'2024-02-10 00:00:00',NULL,'1','2024-02-26 00:11:02','1','2024-02-26 01:47:42',_binary '\0',1),(7001,1,3002,6001,80939,8001,'2026-09-20 00:00:00',1,693065.000000,7,'2026-09-13 00:00:00','第一期(预付款50%)','1','2026-09-08 19:56:10','1','2026-09-08 20:44:38',_binary '\0',2012),(7002,2,3002,6001,80939,NULL,'2026-11-30 00:00:00',2,693065.000000,7,'2026-11-23 00:00:00','第二期(到货款50%)','1','2026-09-08 19:56:10','1','2026-09-08 20:44:38',_binary '\0',2012),(7003,1,3005,6002,80945,8002,'2026-10-15 00:00:00',1,36000.000000,5,'2026-10-10 00:00:00','全款(检测完成后)','1','2026-09-08 19:56:10','1','2026-09-08 20:44:38',_binary '\0',2012),(7004,1,3001,6003,80939,NULL,'2026-10-20 00:00:00',4,1041200.000000,7,'2026-10-13 00:00:00','项目第二期回款','80446','2026-09-10 09:10:04','80446','2026-09-10 09:10:16',_binary '',2012),(7005,3,3002,6001,80939,NULL,'2026-08-05 00:00:00',4,1386130.000000,NULL,NULL,'预付款到账，剩余款项发货后按计划支付','80446','2026-09-12 21:22:12','80446','2026-09-12 21:23:31',_binary '',2012),(7006,4,3002,6001,80939,NULL,'2026-08-05 00:00:00',4,1386130.000000,NULL,NULL,'预付款到账，剩余款项发货后按计划支付','80446','2026-09-12 21:23:13','80446','2026-09-12 21:23:33',_binary '',2012),(7101,1,3102,6101,80446,8101,'2026-09-22 00:00:00',1,797825.000000,7,'2026-09-15 00:00:00','第一期(预付款50%)','1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(7102,2,3102,6101,80446,NULL,'2026-12-10 00:00:00',2,797825.000000,7,'2026-12-03 00:00:00','第二期(到货款50%)','1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(7103,1,3105,6102,80446,8102,'2026-10-20 00:00:00',1,39200.000000,5,'2026-10-15 00:00:00','全款(检测完成后)','1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(7104,1,3101,6103,80446,NULL,'2026-10-10 00:00:00',1,343368.000000,7,'2026-10-03 00:00:00','第一期(预付款40%)','1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(7105,2,3101,6103,80446,NULL,'2026-12-25 00:00:00',1,515052.000000,7,'2026-12-18 00:00:00','第二期(验收款60%)','1','2026-09-18 10:59:01','','2026-09-18 10:59:01',_binary '\0',2012),(7106,1,3101,6105,80446,8103,'2026-09-18 16:57:54',1,858420.000000,7,'2026-09-11 16:57:54','第一期(验收款100%)','80446','2026-09-18 16:57:55','80446','2026-09-18 16:58:49',_binary '\0',2012);
/*!40000 ALTER TABLE `crm_receivable_plan` ENABLE KEYS */;

--
-- Dumping routines for database 'ruoyi-vue-pro'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-20 11:36:06

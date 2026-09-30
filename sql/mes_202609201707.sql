-- MySQL dump 10.13  Distrib 8.0.41, for Win64 (x86_64)
--
-- Host: localhost    Database: ruoyi-vue-pro
-- ------------------------------------------------------
-- Server version	8.0.41

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
-- Table structure for table `mes_cal_holiday`
--

DROP TABLE IF EXISTS `mes_cal_holiday`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_cal_holiday` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `day` datetime NOT NULL COMMENT '日期',
  `type` tinyint NOT NULL COMMENT '日期类型',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 假期设置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_cal_holiday`
--

LOCK TABLES `mes_cal_holiday` WRITE;
/*!40000 ALTER TABLE `mes_cal_holiday` DISABLE KEYS */;
INSERT INTO `mes_cal_holiday` VALUES (1,'2026-02-01 00:00:00',2,NULL,'1','2026-02-16 19:23:55','1','2026-02-16 11:32:14',0x00,1),(2,'1970-01-01 08:00:00',2,NULL,'1','2026-02-16 19:37:14','1','2026-02-16 19:37:24',0x00,1),(3,'2026-01-31 00:00:00',2,NULL,'1','2026-02-16 19:46:27','1','2026-02-16 19:46:27',0x00,1),(4,'2026-01-30 00:00:00',2,NULL,'1','2026-02-16 19:49:12','1','2026-02-16 19:49:12',0x00,1),(5,'2026-04-15 00:00:00',2,'','1','2026-04-16 19:48:13','1','2026-04-16 19:48:15',0x00,1);
/*!40000 ALTER TABLE `mes_cal_holiday` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_cal_plan`
--

DROP TABLE IF EXISTS `mes_cal_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_cal_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '计划编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '计划编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '计划名称',
  `calendar_type` tinyint DEFAULT NULL COMMENT '班组类型',
  `start_date` datetime NOT NULL COMMENT '开始日期',
  `end_date` datetime NOT NULL COMMENT '结束日期',
  `shift_type` tinyint DEFAULT NULL COMMENT '轮班方式',
  `shift_method` tinyint DEFAULT NULL COMMENT '倒班方式',
  `shift_count` int DEFAULT NULL COMMENT '倒班天数',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 排班计划表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_cal_plan`
--

LOCK TABLES `mes_cal_plan` WRITE;
/*!40000 ALTER TABLE `mes_cal_plan` DISABLE KEYS */;
INSERT INTO `mes_cal_plan` VALUES (1,'PLAN001','注塑8月三班倒',1,'2022-08-01 00:00:00','2022-08-31 00:00:00',3,3,1,1,'','1','2026-02-17 06:55:00','1','2026-04-01 16:28:08',0x00,1),(2,'PLAN002','注塑11月两班倒',2,'2022-11-01 00:00:00','2022-11-30 00:00:00',2,3,1,1,'','1','2026-02-17 06:55:00','1','2026-04-01 16:28:09',0x00,1),(3,'PLAN003','仓库单白班',1,'2022-07-01 00:00:00','2022-07-31 00:00:00',1,4,1,1,'','1','2026-02-17 06:55:00','1','2026-04-01 16:28:10',0x00,1),(4,'PLAN004','组装三班倒',2,'2022-09-01 00:00:00','2022-10-31 00:00:00',3,3,1,1,'','1','2026-02-17 06:55:00','1','2026-04-01 16:28:11',0x00,1),(5,'PLAN005','测试计划',1,'2022-08-19 00:00:00','2022-08-26 00:00:00',2,2,1,0,'','1','2026-02-17 06:55:00','1','2026-04-01 16:28:12',0x00,1),(10,'PLAN-2602-ZS','注塑2026年2月三班倒',2,'2026-02-01 00:00:00','2026-02-28 23:59:59',3,3,1,1,'','1','2026-02-19 12:39:57','1','2026-04-01 16:28:13',0x00,1),(11,'PLAN-2602-ZZ','组装2026年2月两班倒',1,'2026-02-01 00:00:00','2026-02-28 23:59:59',2,3,1,1,'','1','2026-02-19 12:39:57','1','2026-04-01 16:28:13',0x00,1),(12,'PLAN-2602-CK','仓库2026年2月单白班',2,'2026-02-01 00:00:00','2026-02-28 23:59:59',1,4,1,1,'','1','2026-02-19 12:39:57','1','2026-04-01 16:28:14',0x00,1);
/*!40000 ALTER TABLE `mes_cal_plan` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_cal_plan_shift`
--

DROP TABLE IF EXISTS `mes_cal_plan_shift`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_cal_plan_shift` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '班次编号',
  `plan_id` bigint NOT NULL COMMENT '排班计划编号',
  `sort` int NOT NULL COMMENT '显示顺序',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '班次名称',
  `start_time` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '开始时间',
  `end_time` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '结束时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=107 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 计划班次表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_cal_plan_shift`
--

LOCK TABLES `mes_cal_plan_shift` WRITE;
/*!40000 ALTER TABLE `mes_cal_plan_shift` DISABLE KEYS */;
INSERT INTO `mes_cal_plan_shift` VALUES (1,1,1,'白班','08:00','16:00','','1','2026-02-17 06:55:00','1','2026-02-17 06:55:47',0x00,1),(2,1,2,'中班','16:00','00:00','','1','2026-02-17 06:55:00','1','2026-02-17 06:55:47',0x00,1),(3,1,3,'夜班','00:00','08:00','','1','2026-02-17 06:55:00','1','2026-02-17 06:55:47',0x00,1),(4,2,1,'白班','08:00','20:00','','1','2026-02-17 06:55:00','1','2026-02-17 06:55:47',0x00,1),(5,2,2,'夜班','20:00','08:00','','1','2026-02-17 06:55:00','1','2026-02-17 06:55:47',0x00,1),(6,3,1,'白班','08:00','18:00','','1','2026-02-17 06:55:00','1','2026-02-17 06:55:47',0x00,1),(7,4,1,'白班','08:00','16:00','','1','2026-02-17 06:55:00','1','2026-02-17 06:55:47',0x00,1),(8,4,2,'中班','16:00','00:00','','1','2026-02-17 06:55:00','1','2026-02-17 06:55:47',0x00,1),(9,4,3,'夜班','00:00','08:00','','1','2026-02-17 06:55:00','1','2026-02-17 06:55:47',0x00,1),(10,5,1,'白班','08:00','20:00','','1','2026-02-17 06:55:00','1','2026-02-17 06:55:47',0x00,1),(11,5,2,'夜班','20:00','08:01','','1','2026-02-17 06:55:00','1','2026-04-02 00:31:34',0x00,1),(101,10,1,'白班','08:00','16:00','','1','2026-02-19 12:39:57','1','2026-02-19 12:39:57',0x00,1),(102,10,2,'中班','16:00','00:00','','1','2026-02-19 12:39:57','1','2026-02-19 12:39:57',0x00,1),(103,10,3,'夜班','00:00','08:00','','1','2026-02-19 12:39:57','1','2026-02-19 12:39:57',0x00,1),(104,11,1,'白班','08:00','20:00','','1','2026-02-19 12:39:57','1','2026-02-19 12:39:57',0x00,1),(105,11,2,'夜班','20:00','08:00','','1','2026-02-19 12:39:57','1','2026-02-19 12:39:57',0x00,1),(106,12,1,'白班','08:00','17:00','','1','2026-02-19 12:39:57','1','2026-02-19 12:39:57',0x00,1);
/*!40000 ALTER TABLE `mes_cal_plan_shift` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_cal_plan_team`
--

DROP TABLE IF EXISTS `mes_cal_plan_team`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_cal_plan_team` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `plan_id` bigint NOT NULL COMMENT '排班计划编号',
  `team_id` bigint NOT NULL COMMENT '班组编号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=107 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 计划班组关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_cal_plan_team`
--

LOCK TABLES `mes_cal_plan_team` WRITE;
/*!40000 ALTER TABLE `mes_cal_plan_team` DISABLE KEYS */;
INSERT INTO `mes_cal_plan_team` VALUES (1,1,201,'','1','2026-02-17 06:55:00','1','2026-02-17 06:55:33',0x00,1),(2,1,202,'','1','2026-02-17 06:55:00','1','2026-02-17 06:55:33',0x00,1),(3,1,203,'','1','2026-02-17 06:55:00','1','2026-02-17 06:55:33',0x00,1),(4,2,201,'','1','2026-02-17 06:55:00','1','2026-02-17 06:55:33',0x00,1),(5,2,202,'','1','2026-02-17 06:55:00','1','2026-02-17 06:55:33',0x00,1),(6,3,211,'','1','2026-02-17 06:55:00','1','2026-02-17 06:55:33',0x00,1),(7,4,208,'','1','2026-02-17 06:55:00','1','2026-02-17 06:55:33',0x00,1),(8,4,209,'','1','2026-02-17 06:55:00','1','2026-02-17 06:55:33',0x00,1),(9,4,210,'','1','2026-02-17 06:55:00','1','2026-02-17 06:55:33',0x00,1),(10,5,201,'','1','2026-02-17 06:55:00','1','2026-02-17 06:55:33',0x00,1),(101,10,201,'','1','2026-02-19 12:39:57','1','2026-02-19 12:39:57',0x00,1),(102,10,202,'','1','2026-02-19 12:39:57','1','2026-02-19 12:39:57',0x00,1),(103,10,203,'','1','2026-02-19 12:39:57','1','2026-02-19 12:39:57',0x00,1),(104,11,208,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(105,11,209,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(106,12,211,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1);
/*!40000 ALTER TABLE `mes_cal_plan_team` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_cal_team`
--

DROP TABLE IF EXISTS `mes_cal_team`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_cal_team` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '班组编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '班组编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '班组名称',
  `calendar_type` tinyint DEFAULT NULL COMMENT '班组类型',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=212 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 班组表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_cal_team`
--

LOCK TABLES `mes_cal_team` WRITE;
/*!40000 ALTER TABLE `mes_cal_team` DISABLE KEYS */;
INSERT INTO `mes_cal_team` VALUES (201,'TEAM-A','注塑A组',1,'','1','2026-02-18 01:16:10','1','2026-04-01 16:06:24',0x00,1),(202,'TEAM-B','注塑B组',1,'','1','2026-02-18 01:16:10','1','2026-04-01 16:06:25',0x00,1),(203,'TEAM-C','注塑C组',2,'','1','2026-02-18 01:16:10','1','2026-04-01 16:06:26',0x00,1),(208,'TEAM-D','组装A组',2,'','1','2026-02-18 01:16:10','1','2026-04-01 16:06:27',0x00,1),(209,'TEAM-E','组装B组',2,'','1','2026-02-18 01:16:10','1','2026-04-01 16:06:28',0x00,1),(210,'TEAM-F','组装C组',2,'','1','2026-02-18 01:16:10','1','2026-04-01 16:06:29',0x00,1),(211,'TEAM-G','仓库组',1,'','1','2026-02-18 01:16:10','1','2026-04-01 16:06:30',0x00,1);
/*!40000 ALTER TABLE `mes_cal_team` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_cal_team_member`
--

DROP TABLE IF EXISTS `mes_cal_team_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_cal_team_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '班组成员编号',
  `team_id` bigint NOT NULL COMMENT '班组编号',
  `user_id` bigint NOT NULL COMMENT '用户编号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 班组成员表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_cal_team_member`
--

LOCK TABLES `mes_cal_team_member` WRITE;
/*!40000 ALTER TABLE `mes_cal_team_member` DISABLE KEYS */;
INSERT INTO `mes_cal_team_member` VALUES (1,201,1,'','1','2026-02-18 01:16:10','1','2026-02-18 01:17:01',0x00,1),(2,201,104,'','1','2026-02-18 01:16:10','1','2026-02-18 01:17:01',0x00,1),(3,202,105,'','1','2026-02-18 01:16:10','1','2026-02-18 01:17:01',0x00,1),(4,211,2,'','1','2026-02-18 11:16:21','1','2026-04-01 19:06:10',0x01,1),(5,211,100,'XXXX','1','2026-04-01 19:06:18','1','2026-04-02 00:26:58',0x01,1),(6,211,115,'','1','2026-04-02 00:12:07','1','2026-04-02 00:12:07',0x00,1);
/*!40000 ALTER TABLE `mes_cal_team_member` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_cal_team_shift`
--

DROP TABLE IF EXISTS `mes_cal_team_shift`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_cal_team_shift` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `plan_id` bigint DEFAULT NULL COMMENT '排班计划编号',
  `team_id` bigint NOT NULL COMMENT '班组编号',
  `shift_id` bigint NOT NULL COMMENT '班次编号',
  `day` datetime NOT NULL COMMENT '日期',
  `sort` int DEFAULT NULL COMMENT '排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=121 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 班组排班表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_cal_team_shift`
--

LOCK TABLES `mes_cal_team_shift` WRITE;
/*!40000 ALTER TABLE `mes_cal_team_shift` DISABLE KEYS */;
INSERT INTO `mes_cal_team_shift` VALUES (1,10,201,101,'2026-02-02 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(2,10,202,102,'2026-02-02 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(3,10,203,103,'2026-02-02 00:00:00',3,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(4,10,201,101,'2026-02-03 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(5,10,202,102,'2026-02-03 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(6,10,203,103,'2026-02-03 00:00:00',3,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(7,10,201,101,'2026-02-04 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(8,10,202,102,'2026-02-04 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(9,10,203,103,'2026-02-04 00:00:00',3,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(10,10,201,101,'2026-02-05 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(11,10,202,102,'2026-02-05 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(12,10,203,103,'2026-02-05 00:00:00',3,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(13,10,201,101,'2026-02-06 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(14,10,202,102,'2026-02-06 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(15,10,203,103,'2026-02-06 00:00:00',3,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(16,10,202,101,'2026-02-09 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(17,10,203,102,'2026-02-09 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(18,10,201,103,'2026-02-09 00:00:00',3,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(19,10,202,101,'2026-02-10 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(20,10,203,102,'2026-02-10 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(21,10,201,103,'2026-02-10 00:00:00',3,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(22,10,202,101,'2026-02-11 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(23,10,203,102,'2026-02-11 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(24,10,201,103,'2026-02-11 00:00:00',3,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(25,10,202,101,'2026-02-12 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(26,10,203,102,'2026-02-12 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(27,10,201,103,'2026-02-12 00:00:00',3,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(28,10,202,101,'2026-02-13 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(29,10,203,102,'2026-02-13 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(30,10,201,103,'2026-02-13 00:00:00',3,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(31,10,203,101,'2026-02-16 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(32,10,201,102,'2026-02-16 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(33,10,202,103,'2026-02-16 00:00:00',3,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(34,10,203,101,'2026-02-17 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(35,10,201,102,'2026-02-17 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(36,10,202,103,'2026-02-17 00:00:00',3,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(37,10,203,101,'2026-02-18 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(38,10,201,102,'2026-02-18 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(39,10,202,103,'2026-02-18 00:00:00',3,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(40,10,203,101,'2026-02-19 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(41,10,201,102,'2026-02-19 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(42,10,202,103,'2026-02-19 00:00:00',3,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(43,10,203,101,'2026-02-20 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(44,10,201,102,'2026-02-20 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(45,10,202,103,'2026-02-20 00:00:00',3,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(46,10,201,101,'2026-02-23 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(47,10,202,102,'2026-02-23 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(48,10,203,103,'2026-02-23 00:00:00',3,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(49,10,201,101,'2026-02-24 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(50,10,202,102,'2026-02-24 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(51,10,203,103,'2026-02-24 00:00:00',3,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(52,10,201,101,'2026-02-25 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(53,10,202,102,'2026-02-25 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(54,10,203,103,'2026-02-25 00:00:00',3,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(55,10,201,101,'2026-02-26 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(56,10,202,102,'2026-02-26 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(57,10,203,103,'2026-02-26 00:00:00',3,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(58,10,201,101,'2026-02-27 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(59,10,202,102,'2026-02-27 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(60,10,203,103,'2026-02-27 00:00:00',3,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(61,11,208,104,'2026-02-02 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(62,11,209,105,'2026-02-02 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(63,11,208,104,'2026-02-03 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(64,11,209,105,'2026-02-03 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(65,11,208,104,'2026-02-04 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(66,11,209,105,'2026-02-04 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(67,11,208,104,'2026-02-05 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(68,11,209,105,'2026-02-05 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(69,11,208,104,'2026-02-06 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(70,11,209,105,'2026-02-06 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(71,11,209,104,'2026-02-09 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(72,11,208,105,'2026-02-09 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(73,11,209,104,'2026-02-10 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(74,11,208,105,'2026-02-10 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(75,11,209,104,'2026-02-11 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(76,11,208,105,'2026-02-11 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(77,11,209,104,'2026-02-12 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(78,11,208,105,'2026-02-12 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(79,11,209,104,'2026-02-13 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(80,11,208,105,'2026-02-13 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(81,11,208,104,'2026-02-16 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(82,11,209,105,'2026-02-16 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(83,11,208,104,'2026-02-17 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(84,11,209,105,'2026-02-17 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(85,11,208,104,'2026-02-18 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(86,11,209,105,'2026-02-18 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(87,11,208,104,'2026-02-19 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(88,11,209,105,'2026-02-19 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(89,11,208,104,'2026-02-20 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(90,11,209,105,'2026-02-20 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(91,11,209,104,'2026-02-23 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(92,11,208,105,'2026-02-23 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(93,11,209,104,'2026-02-24 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(94,11,208,105,'2026-02-24 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(95,11,209,104,'2026-02-25 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(96,11,208,105,'2026-02-25 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(97,11,209,104,'2026-02-26 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(98,11,208,105,'2026-02-26 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(99,11,209,104,'2026-02-27 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(100,11,208,105,'2026-02-27 00:00:00',2,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(101,12,211,106,'2026-02-02 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(102,12,211,106,'2026-02-03 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(103,12,211,106,'2026-02-04 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(104,12,211,106,'2026-02-05 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(105,12,211,106,'2026-02-06 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(106,12,211,106,'2026-02-09 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(107,12,211,106,'2026-02-10 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(108,12,211,106,'2026-02-11 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(109,12,211,106,'2026-02-12 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(110,12,211,106,'2026-02-13 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(111,12,211,106,'2026-02-16 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(112,12,211,106,'2026-02-17 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(113,12,211,106,'2026-02-18 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(114,12,211,106,'2026-02-19 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(115,12,211,106,'2026-02-20 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(116,12,211,106,'2026-02-23 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(117,12,211,106,'2026-02-24 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(118,12,211,106,'2026-02-25 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(119,12,211,106,'2026-02-26 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1),(120,12,211,106,'2026-02-27 00:00:00',1,'','1','2026-02-19 12:39:58','1','2026-02-19 12:39:58',0x00,1);
/*!40000 ALTER TABLE `mes_cal_team_shift` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_dv_check_plan`
--

DROP TABLE IF EXISTS `mes_dv_check_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_dv_check_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '方案编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '方案名称',
  `type` tinyint NOT NULL COMMENT '巡检类型',
  `start_date` datetime DEFAULT NULL COMMENT '开始日期',
  `end_date` datetime DEFAULT NULL COMMENT '结束日期',
  `cycle_type` tinyint NOT NULL COMMENT '循环类型',
  `cycle_count` int NOT NULL DEFAULT '1' COMMENT '周期数量',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 点检保养方案';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_dv_check_plan`
--

LOCK TABLES `mes_dv_check_plan` WRITE;
/*!40000 ALTER TABLE `mes_dv_check_plan` DISABLE KEYS */;
INSERT INTO `mes_dv_check_plan` VALUES (1,'CHP001','注塑机日检方案',1,'2024-01-01 00:00:00','2024-12-31 23:59:59',1,1,1,'每日检查注塑机关键部位','1','2026-02-20 07:11:52','1','2026-02-20 07:13:41',0x00,1),(2,'MTP001','注塑机月度保养方案',2,'2024-01-01 00:00:00','2024-12-31 23:59:59',3,1,0,'每月对注塑机进行全面保养','1','2026-02-20 07:11:52','1','2026-02-20 15:14:50',0x00,1),(3,'CHP002','测试草稿方案',1,'2024-06-01 00:00:00','2024-12-31 23:59:59',2,1,0,'草稿状态的方案','1','2026-02-20 07:11:52','1','2026-02-20 07:13:41',0x00,1);
/*!40000 ALTER TABLE `mes_dv_check_plan` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_dv_check_plan_machinery`
--

DROP TABLE IF EXISTS `mes_dv_check_plan_machinery`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_dv_check_plan_machinery` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `plan_id` bigint NOT NULL COMMENT '方案编号',
  `machinery_id` bigint NOT NULL COMMENT '设备编号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_plan_id` (`plan_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 点检保养方案设备';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_dv_check_plan_machinery`
--

LOCK TABLES `mes_dv_check_plan_machinery` WRITE;
/*!40000 ALTER TABLE `mes_dv_check_plan_machinery` DISABLE KEYS */;
INSERT INTO `mes_dv_check_plan_machinery` VALUES (1,1,1,'','1','2026-02-20 07:11:52','1','2026-02-20 07:13:49',0x00,1),(2,1,2,'','1','2026-02-20 07:11:52','1','2026-02-20 07:13:49',0x00,1),(3,3,10,'EEE','1','2026-04-03 00:48:35','1','2026-04-03 00:48:35',0x00,1);
/*!40000 ALTER TABLE `mes_dv_check_plan_machinery` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_dv_check_plan_subject`
--

DROP TABLE IF EXISTS `mes_dv_check_plan_subject`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_dv_check_plan_subject` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `plan_id` bigint NOT NULL COMMENT '方案编号',
  `subject_id` bigint NOT NULL COMMENT '项目编号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_plan_id` (`plan_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 点检保养方案项目';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_dv_check_plan_subject`
--

LOCK TABLES `mes_dv_check_plan_subject` WRITE;
/*!40000 ALTER TABLE `mes_dv_check_plan_subject` DISABLE KEYS */;
INSERT INTO `mes_dv_check_plan_subject` VALUES (1,1,1,'','1','2026-02-20 07:11:52','1','2026-02-20 07:13:55',0x00,1),(2,1,2,'','1','2026-02-20 07:11:52','1','2026-02-20 07:13:55',0x00,1),(3,1,3,'','1','2026-02-20 07:11:52','1','2026-02-20 07:13:55',0x00,1),(4,3,6,'','1','2026-02-20 15:23:03','1','2026-02-20 15:23:03',0x00,1),(5,3,7,'QQQ','1','2026-04-03 00:48:43','1','2026-04-03 00:48:43',0x00,1),(6,2,7,'','1','2026-04-16 01:36:50','1','2026-04-16 01:36:50',0x00,1);
/*!40000 ALTER TABLE `mes_dv_check_plan_subject` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_dv_check_record`
--

DROP TABLE IF EXISTS `mes_dv_check_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_dv_check_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `plan_id` bigint DEFAULT NULL COMMENT '点检计划 ID',
  `machinery_id` bigint NOT NULL COMMENT '设备 ID',
  `check_time` datetime NOT NULL COMMENT '点检时间',
  `user_id` bigint DEFAULT NULL COMMENT '点检人 ID',
  `status` tinyint NOT NULL DEFAULT '10' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备点检记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_dv_check_record`
--

LOCK TABLES `mes_dv_check_record` WRITE;
/*!40000 ALTER TABLE `mes_dv_check_record` DISABLE KEYS */;
INSERT INTO `mes_dv_check_record` VALUES (1,1,1,'2025-03-10 08:30:00',1,20,'注塑机 M0001 日常点检，全部正常','1','2026-02-20 09:55:49','1','2026-02-20 09:56:16',0x00,1),(2,1,2,'2025-03-11 09:00:00',1,20,'数控机床 M0002 日常点检，电气检查异常','1','2026-02-20 09:55:49','1','2026-02-20 09:56:16',0x00,1),(3,1,1,'2025-03-12 08:00:00',1,20,'待执行的点检任务','1','2026-02-20 09:55:49','1','2026-04-03 18:46:20',0x00,1),(4,NULL,3,'2025-03-13 14:00:00',1,20,'冲压机临时点检','1','2026-02-20 09:55:49','1','2026-04-03 09:58:04',0x00,1),(5,NULL,2,'2025-03-09 16:00:00',1,20,'数控机床临时巡检，润滑系统正常','1','2026-02-20 09:55:49','1','2026-02-20 09:56:16',0x00,1),(6,3,10,'2026-04-16 00:00:00',1,20,'','1','2026-04-03 08:57:19','1','2026-04-03 08:57:42',0x00,1),(7,2,10,'2026-04-24 00:00:00',NULL,10,'','1','2026-04-06 19:44:06','1','2026-04-06 19:44:06',0x00,1),(8,NULL,1,'2026-04-11 18:28:00',NULL,10,'','','2026-04-11 18:28:00','','2026-04-11 18:28:00',0x00,1);
/*!40000 ALTER TABLE `mes_dv_check_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_dv_check_record_line`
--

DROP TABLE IF EXISTS `mes_dv_check_record_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_dv_check_record_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `record_id` bigint NOT NULL COMMENT '点检记录 ID',
  `subject_id` bigint NOT NULL COMMENT '点检项目 ID',
  `check_status` tinyint NOT NULL DEFAULT '1' COMMENT '点检结果',
  `check_result` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '异常描述',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_record_id` (`record_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备点检记录明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_dv_check_record_line`
--

LOCK TABLES `mes_dv_check_record_line` WRITE;
/*!40000 ALTER TABLE `mes_dv_check_record_line` DISABLE KEYS */;
INSERT INTO `mes_dv_check_record_line` VALUES (1,1,1,1,NULL,'','1','2026-02-20 09:55:49','1','2026-02-20 09:56:36',0x00,1),(2,1,2,1,NULL,'','1','2026-02-20 09:55:49','1','2026-02-20 09:56:36',0x00,1),(3,1,3,1,NULL,'','1','2026-02-20 09:55:49','1','2026-02-20 09:56:36',0x00,1),(4,2,1,1,NULL,'','1','2026-02-20 09:55:49','1','2026-02-20 09:56:36',0x00,1),(5,2,2,2,'电气接线端子有松动，需紧固处理','','1','2026-02-20 09:55:49','1','2026-02-20 09:56:36',0x00,1),(6,2,3,1,NULL,'','1','2026-02-20 09:55:49','1','2026-02-20 09:56:36',0x00,1),(7,3,1,1,NULL,'','1','2026-02-20 09:55:49','1','2026-02-20 09:56:36',0x00,1),(8,3,2,1,NULL,'','1','2026-02-20 09:55:49','1','2026-02-20 09:56:36',0x00,1),(9,3,3,1,NULL,'','1','2026-02-20 09:55:49','1','2026-02-20 09:56:36',0x00,1),(10,4,8,1,'','123','1','2026-02-20 17:57:28','1','2026-02-20 17:57:28',0x00,1),(11,6,6,1,NULL,'','1','2026-04-03 08:57:20','1','2026-04-03 08:57:20',0x00,1),(12,6,7,1,NULL,'','1','2026-04-03 08:57:20','1','2026-04-03 08:57:20',0x00,1),(13,7,7,1,'','','1','2026-04-06 19:51:13','1','2026-04-06 19:51:13',0x00,1),(14,7,4,2,'abc','EFC','1','2026-04-16 13:27:13','1','2026-04-16 13:27:13',0x00,1),(15,8,1,1,'EEE','','1','2026-05-24 20:25:07','1','2026-05-24 20:25:07',0x00,1);
/*!40000 ALTER TABLE `mes_dv_check_record_line` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_dv_machinery`
--

DROP TABLE IF EXISTS `mes_dv_machinery`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_dv_machinery` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '设备编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '设备名称',
  `brand` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '品牌',
  `specification` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规格型号',
  `machinery_type_id` bigint NOT NULL COMMENT '设备类型编号',
  `workshop_id` bigint NOT NULL COMMENT '所属车间编号',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '设备状态',
  `last_mainten_time` datetime DEFAULT NULL COMMENT '最近保养时间',
  `last_check_time` datetime DEFAULT NULL COMMENT '最近点检时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 设备台账';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_dv_machinery`
--

LOCK TABLES `mes_dv_machinery` WRITE;
/*!40000 ALTER TABLE `mes_dv_machinery` DISABLE KEYS */;
INSERT INTO `mes_dv_machinery` VALUES (1,'M0001','海天注塑机A','海天','MA1600',2,1,1,'2025-01-15 08:00:00','2025-02-01 09:00:00','','1','2022-08-18 11:01:42','1','2026-02-17 02:51:10',0x00,1),(2,'M0002','海天注塑机B','海天','MA1600',2,1,1,'2025-01-15 08:00:00','2025-02-01 09:00:00','','1','2022-08-18 11:02:00','1','2026-02-17 02:51:10',0x00,1),(3,'M0003','钢筋裁切机','虎王','HW-300',8,3,1,NULL,NULL,'','1','2022-08-21 19:42:53','1','2026-02-17 02:51:10',0x00,1),(4,'M0004','冲压机','扬锻','YD-80T',9,3,3,NULL,NULL,'','1','2022-08-22 09:48:52','1','2026-02-17 02:51:10',0x00,1),(5,'M0005','物料干燥机','信易','SHD-50',3,1,2,NULL,NULL,'','1','2022-08-19 14:36:15','1','2026-02-17 02:51:10',0x00,1),(6,'M0006','自动拧螺丝机A','快克','QK-S200',5,2,1,'2025-01-20 10:00:00','2025-02-05 14:00:00','','1','2022-08-24 10:00:00','1','2026-02-17 02:51:10',0x00,1),(7,'M0007','压装机','先帝达','XD-500',6,2,1,NULL,NULL,'','1','2022-08-24 10:00:00','1','2026-02-17 02:51:10',0x00,1),(8,'M0008','CCD视觉检测台','基恩士','CV-X480F',11,2,1,NULL,'2025-02-05 10:55:58','','1','2025-02-05 10:55:58','1','2026-02-17 11:30:16',0x00,1),(9,'M00001','ABCED',NULL,'EEE',1,1,1,NULL,NULL,'EEE','1','2026-04-02 23:20:15','1','2026-04-02 23:20:15',0x00,1),(10,'M00002','AAA','EEE','123213',2,1,1,NULL,NULL,NULL,'1','2026-04-02 23:37:18','1','2026-04-02 23:37:18',0x00,1);
/*!40000 ALTER TABLE `mes_dv_machinery` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_dv_machinery_type`
--

DROP TABLE IF EXISTS `mes_dv_machinery_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_dv_machinery_type` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型名称',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父类型编号',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `sort` int NOT NULL DEFAULT '0' COMMENT '显示排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 设备类型';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_dv_machinery_type`
--

LOCK TABLES `mes_dv_machinery_type` WRITE;
/*!40000 ALTER TABLE `mes_dv_machinery_type` DISABLE KEYS */;
INSERT INTO `mes_dv_machinery_type` VALUES (1,'M_TYPE_001','注塑设备',0,0,0,'','1','2022-05-08 19:26:57','1','2026-02-17 02:51:24',0x00,1),(2,'M_TYPE_002','注塑机',1,0,0,'','1','2022-05-08 19:50:41','1','2026-02-17 02:51:24',0x00,1),(3,'M_TYPE_003','干燥机',1,0,1,'','1','2022-05-08 19:50:57','1','2026-02-17 02:51:24',0x00,1),(4,'M_TYPE_004','组装设备',0,0,1,'','1','2022-05-08 19:51:10','1','2026-02-17 02:51:24',0x00,1),(5,'M_TYPE_005','自动拧螺丝机',4,0,0,'','1','2022-05-08 19:51:25','1','2026-02-17 02:51:24',0x00,1),(6,'M_TYPE_006','压装机',4,0,1,'','1','2022-05-14 13:40:03','1','2026-02-17 02:51:24',0x00,1),(7,'M_TYPE_007','五金加工设备',0,0,2,'aaa','1','2022-05-14 13:43:59','1','2026-02-17 10:52:04',0x00,1),(8,'M_TYPE_008','裁切机',7,0,0,'','1','2022-05-14 13:44:23','1','2026-02-17 02:51:24',0x00,1),(9,'M_TYPE_009','冲压机',7,0,1,'','1','2022-05-14 13:44:33','1','2026-02-17 02:51:24',0x00,1),(10,'M_TYPE_010','检测设备',0,0,3,'','1','2022-05-14 13:49:13','1','2026-02-17 02:51:24',0x00,1),(11,'M_TYPE_011','CCD检测台',10,0,0,'','1','2022-05-14 13:49:25','1','2026-02-17 02:51:24',0x00,1),(12,'MT008','xxx',1,0,0,'','1','2026-05-24 20:24:11','1','2026-05-24 20:24:11',0x00,1);
/*!40000 ALTER TABLE `mes_dv_machinery_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_dv_mainten_record`
--

DROP TABLE IF EXISTS `mes_dv_mainten_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_dv_mainten_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `plan_id` bigint DEFAULT NULL COMMENT '计划ID',
  `machinery_id` bigint NOT NULL COMMENT '设备ID',
  `mainten_time` datetime NOT NULL COMMENT '保养时间',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备保养记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_dv_mainten_record`
--

LOCK TABLES `mes_dv_mainten_record` WRITE;
/*!40000 ALTER TABLE `mes_dv_mainten_record` DISABLE KEYS */;
INSERT INTO `mes_dv_mainten_record` VALUES (1,1,1,'2026-01-15 09:00:00',1,0,'CNC加工中心A 1月份保养','1','2026-01-15 09:30:00','1','2026-04-16 05:32:37',0x00,1),(2,1,2,'2026-01-15 14:00:00',1,0,'CNC加工中心B 1月份保养','1','2026-01-15 14:30:00','1','2026-04-16 05:32:37',0x00,1),(3,2,4,'2026-01-20 08:30:00',1,0,'注塑机D Q1保养','1','2026-01-20 09:00:00','1','2026-04-16 05:32:37',0x00,1),(4,1,1,'2026-02-15 09:00:00',1,4,'CNC加工中心A 2月份保养（草稿）','1','2026-02-15 09:30:00','1','2026-04-16 05:32:37',0x00,1),(5,1,3,'2026-02-16 10:00:00',1,4,'数控车床C 2月份保养（草稿）','1','2026-02-16 10:30:00','1','2026-04-16 05:32:37',0x00,1),(6,NULL,5,'2026-02-18 13:00:00',1,4,'冲压机E 临时保养（无计划）','1','2026-02-18 13:30:00','1','2026-04-16 05:32:37',0x00,1),(7,3,10,'2026-04-09 00:00:00',1,0,'','1','2026-04-03 21:45:05','1','2026-04-03 22:50:16',0x01,1),(8,3,9,'2026-04-11 00:00:00',1,0,'','1','2026-04-03 22:50:26','1','2026-04-03 22:50:26',0x00,1),(9,NULL,1,'2026-04-11 18:28:00',NULL,0,'','','2026-04-11 18:28:00','','2026-04-16 05:32:37',0x00,1);
/*!40000 ALTER TABLE `mes_dv_mainten_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_dv_mainten_record_line`
--

DROP TABLE IF EXISTS `mes_dv_mainten_record_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_dv_mainten_record_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `record_id` bigint NOT NULL COMMENT '保养记录ID',
  `subject_id` bigint NOT NULL COMMENT '项目ID',
  `status` tinyint NOT NULL COMMENT '保养结果',
  `result` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '异常描述',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备保养记录明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_dv_mainten_record_line`
--

LOCK TABLES `mes_dv_mainten_record_line` WRITE;
/*!40000 ALTER TABLE `mes_dv_mainten_record_line` DISABLE KEYS */;
INSERT INTO `mes_dv_mainten_record_line` VALUES (1,1,1,1,NULL,NULL,'1','2026-01-15 09:10:00','1','2026-02-20 07:45:46',0x00,1),(2,1,2,1,NULL,NULL,'1','2026-01-15 09:15:00','1','2026-02-20 07:45:46',0x00,1),(3,1,3,1,NULL,NULL,'1','2026-01-15 09:20:00','1','2026-02-20 07:45:46',0x00,1),(4,1,4,2,'主轴跳动0.008mm，超标','需安排维修','1','2026-01-15 09:25:00','1','2026-02-20 07:45:46',0x00,1),(5,2,1,1,NULL,NULL,'1','2026-01-15 14:10:00','1','2026-02-20 07:45:46',0x00,1),(6,2,2,1,NULL,NULL,'1','2026-01-15 14:15:00','1','2026-02-20 07:45:46',0x00,1),(7,2,3,1,NULL,NULL,'1','2026-01-15 14:20:00','1','2026-02-20 07:45:46',0x00,1),(8,2,4,1,NULL,NULL,'1','2026-01-15 14:25:00','1','2026-02-20 07:45:46',0x00,1),(9,3,5,1,NULL,NULL,'1','2026-01-20 08:40:00','1','2026-02-20 07:45:46',0x00,1),(10,3,6,2,'液压管路轻微渗油','已临时处理，下次更换密封圈','1','2026-01-20 08:50:00','1','2026-02-20 07:45:46',0x00,1),(11,4,1,1,NULL,NULL,'1','2026-02-15 09:10:00','1','2026-02-20 07:45:46',0x00,1),(12,4,2,1,NULL,NULL,'1','2026-02-15 09:15:00','1','2026-02-20 07:45:46',0x00,1),(13,7,8,1,'EEEE','abc','1','2026-04-03 21:47:46','1','2026-04-03 14:50:16',0x01,1),(14,9,7,1,'','','1','2026-04-16 17:14:38','1','2026-04-16 17:14:38',0x00,1),(15,9,4,0,'abc','efs','1','2026-04-16 17:14:47','1','2026-04-16 17:14:47',0x00,1);
/*!40000 ALTER TABLE `mes_dv_mainten_record_line` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_dv_repair`
--

DROP TABLE IF EXISTS `mes_dv_repair`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_dv_repair` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '维修工单编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '维修工单名称',
  `machinery_id` bigint NOT NULL COMMENT '设备编号（关联 mes_dv_machinery.id）',
  `require_date` datetime DEFAULT NULL COMMENT '报修日期',
  `finish_date` datetime DEFAULT NULL COMMENT '维修完成日期',
  `confirm_date` datetime DEFAULT NULL COMMENT '验收日期',
  `result` tinyint DEFAULT NULL COMMENT '维修结果',
  `accepted_user_id` bigint DEFAULT NULL COMMENT '维修人用户编号（关联 system_users.id）',
  `confirm_user_id` bigint DEFAULT NULL COMMENT '验收人用户编号（关联 system_users.id）',
  `source_doc_type` tinyint DEFAULT NULL COMMENT '来源单据类型',
  `source_doc_id` bigint DEFAULT NULL COMMENT '来源单据编号',
  `source_doc_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源单据编码',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_machinery_id` (`machinery_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 维修工单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_dv_repair`
--

LOCK TABLES `mes_dv_repair` WRITE;
/*!40000 ALTER TABLE `mes_dv_repair` DISABLE KEYS */;
INSERT INTO `mes_dv_repair` VALUES (1,'REP2024001','注塑机液压系统漏油维修',1,'2024-03-10 18:00:00',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,'液压系统发现漏油，需紧急处理','1','2026-02-20 10:56:40','1','2026-04-16 05:00:14',0x00,1),(2,'REP2024002','数控机床主轴异常噪音排查',2,'2024-03-12 08:00:00','2024-03-14 16:30:00',NULL,NULL,1,NULL,NULL,NULL,NULL,2,'主轴轴承磨损，已更换轴承','1','2026-02-20 10:56:40','1','2026-04-16 05:00:14',0x00,1),(3,'REP2024003','注塑机控制面板故障维修',1,'2024-02-20 08:00:00','2024-02-22 14:00:00','2024-02-23 10:00:00',1,1,1,NULL,NULL,NULL,4,'控制面板主板已更换，验收通过','1','2026-02-20 10:56:40','1','2026-04-16 05:00:14',0x00,1),(4,'BX20260404000002','AAB',10,'2026-04-03 00:00:00','2026-04-04 00:45:04','2026-04-04 00:45:09',1,1,1,NULL,NULL,NULL,4,'','1','2026-04-04 00:43:47','1','2026-04-04 00:45:09',0x00,1),(5,'BX20260404000003','EEAA',7,'2026-04-29 00:00:00','2026-04-06 00:00:00',NULL,NULL,1,NULL,NULL,NULL,NULL,2,'','1','2026-04-04 00:51:04','1','2026-04-04 01:06:37',0x00,1),(6,'BX20260404000004','abc',10,'2026-04-22 00:00:00','2026-04-07 00:00:00','2026-04-04 01:06:31',1,1,1,NULL,NULL,NULL,4,'','1','2026-04-04 00:58:17','1','2026-04-04 01:06:31',0x00,1),(7,'BX20260404000005','AAAA',10,'2026-04-08 00:00:00',NULL,NULL,NULL,1,NULL,NULL,NULL,NULL,1,'','1','2026-04-04 01:09:14','1','2026-04-04 01:09:18',0x00,1),(8,'BX20260416000004','EEE',5,'2026-04-15 00:00:00',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,'','1','2026-04-16 09:27:12','1','2026-04-16 09:27:12',0x00,1);
/*!40000 ALTER TABLE `mes_dv_repair` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_dv_repair_line`
--

DROP TABLE IF EXISTS `mes_dv_repair_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_dv_repair_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `repair_id` bigint NOT NULL COMMENT '维修工单编号（关联 mes_dv_repair.id）',
  `subject_id` bigint DEFAULT NULL COMMENT '点检保养项目编号（关联 mes_dv_subject.id）',
  `malfunction` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '故障描述',
  `malfunction_url` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '故障图片 URL',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '维修描述',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_repair_id` (`repair_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 维修工单行';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_dv_repair_line`
--

LOCK TABLES `mes_dv_repair_line` WRITE;
/*!40000 ALTER TABLE `mes_dv_repair_line` DISABLE KEYS */;
INSERT INTO `mes_dv_repair_line` VALUES (1,1,1,'液压缸密封圈老化，出现渗漏油现象',NULL,NULL,'','1','2026-02-20 10:56:40','1','2026-02-20 11:17:46',0x00,1),(2,2,NULL,'主轴运行时有明显异响，振动值超标',NULL,'更换 6208 型号轴承一套，润滑脂重新填充','','1','2026-02-20 10:56:40','1','2026-02-20 11:17:46',0x00,1),(3,2,NULL,'主轴端面跳动量超出公差范围',NULL,'重新校准主轴，调整安装精度至合格','','1','2026-02-20 10:56:40','1','2026-02-20 11:17:46',0x00,1),(4,3,2,'操作面板触摸屏无响应，控制板指示灯异常',NULL,'更换主控板 PCB，重新烧录固件并测试通过','','1','2026-02-20 10:56:40','1','2026-02-20 11:17:46',0x00,1),(5,8,7,'EEE','','','','1','2026-04-16 17:18:38','1','2026-04-16 17:18:38',0x00,1);
/*!40000 ALTER TABLE `mes_dv_repair_line` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_dv_subject`
--

DROP TABLE IF EXISTS `mes_dv_subject`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_dv_subject` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '项目名称',
  `type` tinyint NOT NULL COMMENT '项目类型',
  `content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目内容',
  `standard` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标准',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 点检保养项目';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_dv_subject`
--

LOCK TABLES `mes_dv_subject` WRITE;
/*!40000 ALTER TABLE `mes_dv_subject` DISABLE KEYS */;
INSERT INTO `mes_dv_subject` VALUES (1,'CHK001','注塑机外观检查',1,'检查注塑机外壳是否有裂纹、变形、漏油等异常','外观完好，无明显损伤',0,'','1','2022-06-16 20:30:00','1','2026-02-20 01:49:32',0x00,1),(2,'CHK002','电气线路点检',1,'检查设备电气线路连接是否牢固，有无破损老化','线路连接正常，无裸露铜线',0,'','1','2022-06-16 20:31:00','1','2026-02-20 01:49:32',0x00,1),(3,'CHK003','安全防护装置检查',1,'检查设备安全门、急停按钮、防护罩等安全装置是否正常','安全装置功能正常，无失效',0,'','1','2022-06-16 20:32:00','1','2026-02-20 01:49:32',0x00,1),(4,'CHK004','润滑系统点检',1,'检查润滑油位、油质、油路是否通畅','油位在标准范围内，油质清澈',0,'','1','2022-06-16 20:33:00','1','2026-02-20 01:49:32',0x00,1),(5,'MNT001','注塑机润滑保养',2,'对注塑机各润滑点进行加油保养，更换润滑油','按照设备手册规定的润滑周期和油品进行保养',0,'','1','2022-06-16 20:34:00','1','2026-02-20 01:49:32',0x00,1),(6,'MNT002','液压系统保养',2,'清洗液压过滤器，检查并补充液压油，检测系统压力','液压油清洁度达标，系统压力在正常范围',0,'','1','2022-06-16 20:35:00','1','2026-02-20 01:49:32',0x00,1),(7,'MNT003','电气系统保养',2,'清洁电控柜，紧固接线端子，检查散热风扇','电控柜内无灰尘积累，接线端子紧固',0,'','1','2022-06-16 20:36:00','1','2026-02-20 01:49:32',0x00,1),(8,'CHK005','温度检测点检',1,'检测设备关键部位温度是否在正常范围','温度不超过设备规定的最高值',1,'已停用','1','2022-06-16 20:37:00','1','2026-02-20 01:49:32',0x00,1);
/*!40000 ALTER TABLE `mes_dv_subject` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_md_auto_code_part`
--

DROP TABLE IF EXISTS `mes_md_auto_code_part`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_md_auto_code_part` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分段 ID',
  `rule_id` bigint NOT NULL COMMENT '规则 ID',
  `sort` int NOT NULL COMMENT '分段序号',
  `type` tinyint NOT NULL COMMENT '分段类型',
  `length` int NOT NULL COMMENT '分段长度',
  `date_format` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '日期格式（当 type=2 时使用）',
  `fix_character` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '固定字符（当 type=3 时使用）',
  `serial_start_no` int DEFAULT NULL COMMENT '流水号起始值（当 type=4 时使用）',
  `serial_step` int DEFAULT NULL COMMENT '流水号步长（当 type=4 时使用）',
  `cycle_flag` bit(1) DEFAULT b'0' COMMENT '流水号是否循环（当 type=4 时使用）',
  `cycle_method` tinyint DEFAULT NULL COMMENT '循环方式',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_rule_id` (`rule_id`) USING BTREE COMMENT '规则 ID 索引'
) ENGINE=InnoDB AUTO_INCREMENT=155 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 编码规则组成表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_md_auto_code_part`
--

LOCK TABLES `mes_md_auto_code_part` WRITE;
/*!40000 ALTER TABLE `mes_md_auto_code_part` DISABLE KEYS */;
INSERT INTO `mes_md_auto_code_part` VALUES (1,2,2,1,1,NULL,NULL,1,1,0x00,NULL,NULL,'1','2026-03-05 01:06:00','1','2026-03-05 01:06:00',0x00,1),(2,2,4,2,2,'yyyy',NULL,1,1,0x00,NULL,'231','1','2026-03-05 01:06:10','1','2026-03-05 01:06:10',0x00,1),(3,2,5,3,50,NULL,'1232',1,1,0x00,NULL,NULL,'1','2026-03-05 01:06:21','1','2026-03-05 01:06:21',0x00,1),(4,2,5,4,2,NULL,NULL,3,4,0x01,1,'1000','1','2026-03-05 01:06:34','1','2026-03-05 01:06:34',0x00,1),(5,2,10,3,3,NULL,'22',1,1,0x00,NULL,'3','1','2026-03-05 01:15:16','1','2026-03-05 01:15:16',0x00,1),(6,2,1,3,10,'yyyy','32',NULL,NULL,0x00,NULL,'3222','1','2026-03-05 01:17:23','1','2026-03-05 01:23:09',0x01,1),(7,3,1,3,2,NULL,'SN',NULL,NULL,0x00,NULL,NULL,'1','2026-03-05 01:29:24','1','2026-03-05 01:29:24',0x00,1),(8,3,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-05 01:29:24','1','2026-03-05 01:29:24',0x00,1),(9,3,3,4,6,NULL,NULL,1,1,0x01,3,NULL,'1','2026-03-05 01:29:24','1','2026-03-05 01:29:24',0x00,1),(10,4,1,3,3,NULL,'PKG',NULL,NULL,0x00,NULL,NULL,'1','2026-03-08 04:44:00','1','2026-03-08 04:44:00',0x00,1),(11,4,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-08 04:44:00','1','2026-03-08 04:44:00',0x00,1),(12,4,3,4,4,NULL,NULL,1,1,0x01,3,NULL,'1','2026-03-08 04:44:00','1','2026-03-08 04:44:00',0x00,1),(15,12,1,3,2,NULL,'PC',NULL,NULL,0x00,NULL,'固定字符PC','1','2026-03-13 15:36:41','1','2026-03-13 15:36:41',0x00,1),(16,12,2,1,6,NULL,NULL,1,1,0x01,NULL,'6位流水号','1','2026-03-13 15:36:41','1','2026-03-13 23:54:38',0x01,1),(17,12,2,2,5,'yyyyMMddHHmm',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-13 23:54:46','1','2026-03-13 23:54:46',0x00,1),(18,13,1,3,4,NULL,'TASK',NULL,NULL,0x00,NULL,NULL,'1','2026-03-15 14:42:59','1','2026-03-15 14:42:59',0x00,1),(19,13,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-15 14:42:59','1','2026-03-15 14:42:59',0x00,1),(20,13,3,4,6,NULL,NULL,1,1,0x01,3,NULL,'1','2026-03-15 14:42:59','1','2026-03-15 14:42:59',0x00,1),(21,12,3,4,4,NULL,NULL,1,1,0x01,1,NULL,'1','2026-03-21 07:07:29','1','2026-03-21 07:07:29',0x00,1),(22,14,1,3,3,NULL,'IQC',NULL,NULL,0x00,NULL,NULL,'','2026-03-23 14:52:48','','2026-03-23 14:52:48',0x00,1),(23,14,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'','2026-03-23 14:52:48','','2026-03-23 14:52:48',0x00,1),(24,14,3,4,3,NULL,NULL,1,1,0x01,3,NULL,'','2026-03-23 14:52:48','','2026-03-23 15:03:11',0x00,1),(25,15,1,3,4,NULL,'IPQC',NULL,NULL,0x00,NULL,NULL,'1','2026-03-24 13:30:06','1','2026-03-24 13:30:06',0x00,1),(26,15,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-24 13:30:06','1','2026-03-24 13:30:06',0x00,1),(27,15,3,4,6,NULL,NULL,1,1,0x01,3,NULL,'1','2026-03-24 13:30:06','1','2026-03-24 13:30:06',0x00,1),(28,16,1,3,3,NULL,'RQC',NULL,NULL,0x00,NULL,NULL,'1','2026-03-26 05:09:58','1','2026-03-26 05:09:58',0x00,1),(29,16,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-26 05:09:58','1','2026-03-26 05:09:58',0x00,1),(30,16,3,4,6,NULL,NULL,1,1,0x01,3,NULL,'1','2026-03-26 05:09:58','1','2026-03-26 05:09:58',0x00,1),(31,17,1,3,3,NULL,'OQC',NULL,NULL,0x00,NULL,NULL,'1','2026-03-27 09:00:03','1','2026-03-27 09:00:03',0x00,1),(32,17,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-27 09:00:03','1','2026-03-27 09:00:03',0x00,1),(33,17,3,4,3,NULL,NULL,1,1,0x01,3,NULL,'1','2026-03-27 09:00:03','1','2026-03-27 09:00:03',0x00,1),(34,18,1,3,4,NULL,'ITEM',NULL,NULL,0x00,NULL,NULL,'1','2026-03-28 00:56:57','1','2026-03-28 00:56:57',0x00,1),(35,18,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-28 00:56:57','1','2026-03-28 00:56:57',0x00,1),(36,18,3,4,6,NULL,NULL,1,1,0x01,3,NULL,'1','2026-03-28 00:56:57','1','2026-03-28 00:56:57',0x00,1),(37,19,1,3,1,NULL,'C',NULL,NULL,0x00,NULL,NULL,'admin','2026-03-28 03:41:30','admin','2026-03-28 03:41:30',0x00,1),(38,19,2,4,5,NULL,NULL,1,1,0x00,NULL,NULL,'admin','2026-03-28 03:41:30','admin','2026-03-28 03:41:30',0x00,1),(39,20,1,3,2,NULL,'WS',NULL,NULL,0x00,NULL,NULL,'1','2026-03-28 09:41:24','1','2026-03-28 09:41:24',0x00,1),(40,20,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-28 09:41:24','1','2026-03-28 09:41:24',0x00,1),(41,20,3,4,4,NULL,NULL,1,1,0x01,3,NULL,'1','2026-03-28 09:41:24','1','2026-03-28 09:41:24',0x00,1),(42,21,1,3,2,NULL,'WH',NULL,NULL,0x00,NULL,NULL,'1','2026-03-28 12:48:18','1','2026-03-28 12:48:18',0x00,1),(43,21,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-28 12:48:18','1','2026-03-28 12:48:18',0x00,1),(44,21,3,4,4,NULL,NULL,1,1,0x01,3,NULL,'1','2026-03-28 12:48:18','1','2026-03-28 12:48:18',0x00,1),(45,22,1,3,2,NULL,'LC',NULL,NULL,0x00,NULL,NULL,'1','2026-03-28 12:48:18','1','2026-03-28 12:48:18',0x00,1),(46,22,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-28 12:48:18','1','2026-03-28 12:48:18',0x00,1),(47,22,3,4,4,NULL,NULL,1,1,0x01,3,NULL,'1','2026-03-28 12:48:18','1','2026-03-28 12:48:18',0x00,1),(48,23,1,3,2,NULL,'AR',NULL,NULL,0x00,NULL,NULL,'1','2026-03-28 12:48:18','1','2026-03-28 12:48:18',0x00,1),(49,23,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-28 12:48:18','1','2026-03-28 12:48:18',0x00,1),(50,23,3,4,4,NULL,NULL,1,1,0x01,3,NULL,'1','2026-03-28 12:48:18','1','2026-03-28 12:48:18',0x00,1),(51,24,1,3,2,NULL,'TT',NULL,NULL,0x00,NULL,NULL,'admin','2026-03-29 01:27:14','admin','2026-03-29 01:27:14',0x00,1),(52,24,2,4,3,NULL,NULL,1,1,0x00,NULL,NULL,'admin','2026-03-29 01:27:14','admin','2026-03-29 01:27:14',0x00,1),(53,25,1,3,2,NULL,'TL',NULL,NULL,0x00,NULL,NULL,'1','2026-03-29 01:56:00','1','2026-03-29 01:56:00',0x00,1),(54,25,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-29 01:56:00','1','2026-03-29 01:56:00',0x00,1),(55,25,3,4,4,NULL,NULL,1,1,0x01,3,NULL,'1','2026-03-29 01:56:00','1','2026-03-29 01:56:00',0x00,1),(56,26,1,3,2,NULL,'IR',NULL,NULL,0x00,NULL,NULL,'1','2026-03-29 02:48:40','1','2026-03-29 02:48:40',0x00,1),(57,26,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-29 02:48:40','1','2026-03-29 02:48:40',0x00,1),(58,26,3,4,6,NULL,NULL,1,1,0x01,3,NULL,'1','2026-03-29 02:48:40','1','2026-03-29 02:48:40',0x00,1),(59,27,1,3,2,NULL,'AN',NULL,NULL,0x00,NULL,NULL,'1','2026-03-29 11:10:49','1','2026-03-29 11:10:49',0x00,1),(60,27,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-29 11:10:49','1','2026-03-29 11:10:49',0x00,1),(61,27,3,4,6,NULL,NULL,1,1,0x01,3,NULL,'1','2026-03-29 11:10:49','1','2026-03-29 11:10:49',0x00,1),(62,28,1,3,2,NULL,'RV',NULL,NULL,0x00,NULL,NULL,'1','2026-03-29 13:01:34','1','2026-03-29 13:01:34',0x00,1),(63,28,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-29 13:01:34','1','2026-03-29 13:01:34',0x00,1),(64,28,3,4,6,NULL,NULL,1,1,0x01,3,NULL,'1','2026-03-29 13:01:34','1','2026-03-29 13:01:34',0x00,1),(65,29,1,3,2,NULL,'PI',NULL,NULL,0x00,NULL,NULL,'1','2026-03-30 01:44:21','1','2026-03-30 01:44:21',0x00,1),(66,29,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-30 01:44:21','1','2026-03-30 01:44:21',0x00,1),(67,29,3,4,6,NULL,NULL,1,1,0x01,3,NULL,'1','2026-03-30 01:44:21','1','2026-03-30 01:44:21',0x00,1),(68,30,1,3,2,NULL,'RI',NULL,NULL,0x00,NULL,NULL,'1','2026-03-30 03:49:52','1','2026-03-30 03:49:52',0x00,1),(69,30,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-30 03:49:52','1','2026-03-30 03:49:52',0x00,1),(70,30,3,4,6,NULL,NULL,1,1,0x01,3,NULL,'1','2026-03-30 03:49:52','1','2026-03-30 03:49:52',0x00,1),(71,31,1,3,2,NULL,'PR',NULL,NULL,0x00,NULL,NULL,'1','2026-03-30 04:35:05','1','2026-03-30 04:35:05',0x00,1),(72,31,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-30 04:35:05','1','2026-03-30 04:35:05',0x00,1),(73,31,3,4,6,NULL,NULL,1,1,0x01,3,NULL,'1','2026-03-30 04:35:05','1','2026-03-30 04:35:05',0x00,1),(74,32,1,3,2,NULL,'SN',NULL,NULL,0x00,NULL,NULL,'1','2026-03-30 05:05:32','1','2026-03-30 05:05:32',0x00,1),(75,32,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-30 05:05:32','1','2026-03-30 05:05:32',0x00,1),(76,32,3,4,6,NULL,NULL,1,1,0x01,3,NULL,'1','2026-03-30 05:05:32','1','2026-03-30 05:05:32',0x00,1),(77,34,1,3,2,NULL,'RS',NULL,NULL,0x00,NULL,NULL,'1','2026-03-30 11:11:15','1','2026-03-30 11:11:15',0x00,1),(78,34,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-30 11:11:15','1','2026-03-30 11:11:15',0x00,1),(79,34,3,4,3,NULL,NULL,1,1,0x01,3,NULL,'1','2026-03-30 11:11:15','1','2026-03-30 11:11:15',0x00,1),(80,35,1,3,2,NULL,'PS',NULL,NULL,0x00,NULL,NULL,'1','2026-03-30 12:04:29','1','2026-03-30 12:04:29',0x00,1),(81,35,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-30 12:04:29','1','2026-03-30 12:04:29',0x00,1),(82,35,3,4,6,NULL,NULL,1,1,0x01,3,NULL,'1','2026-03-30 12:04:29','1','2026-03-30 12:04:29',0x00,1),(83,36,1,3,5,NULL,'MISCI',NULL,NULL,0x00,NULL,NULL,'1','2026-03-30 15:19:37','1','2026-03-30 15:19:37',0x00,1),(84,36,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-30 15:19:37','1','2026-03-30 15:19:37',0x00,1),(85,36,3,4,3,NULL,NULL,1,1,0x01,3,NULL,'1','2026-03-30 15:19:37','1','2026-03-30 15:19:37',0x00,1),(86,37,1,3,5,NULL,'MISCR',NULL,NULL,0x00,NULL,NULL,'1','2026-03-31 01:49:05','1','2026-03-31 01:49:05',0x00,1),(87,37,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-31 01:49:13','1','2026-03-31 01:49:13',0x00,1),(88,37,3,4,3,NULL,NULL,1,1,0x01,3,NULL,'1','2026-03-31 01:49:21','1','2026-03-31 01:49:21',0x00,1),(89,38,1,3,2,NULL,'TR',NULL,NULL,0x00,NULL,NULL,'','2026-03-31 07:57:32','','2026-03-31 07:57:32',0x00,1),(90,38,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'','2026-03-31 07:57:32','','2026-03-31 07:57:32',0x00,1),(91,38,3,4,3,NULL,NULL,1,1,0x01,3,NULL,'','2026-03-31 07:57:32','','2026-03-31 07:57:32',0x00,1),(92,43,1,3,3,NULL,'PDP',NULL,NULL,0x00,NULL,NULL,'1','2026-03-31 10:17:02','1','2026-03-31 10:17:02',0x00,1),(93,43,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-31 10:17:02','1','2026-03-31 10:17:02',0x00,1),(94,43,3,4,4,NULL,NULL,1,1,0x01,3,NULL,'1','2026-03-31 10:17:02','1','2026-03-31 10:17:02',0x00,1),(95,44,1,3,3,NULL,'PDT',NULL,NULL,0x00,NULL,NULL,'1','2026-03-31 10:17:02','1','2026-03-31 10:17:02',0x00,1),(96,44,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-31 10:17:02','1','2026-03-31 10:17:02',0x00,1),(97,44,3,4,4,NULL,NULL,1,1,0x01,3,NULL,'1','2026-03-31 10:17:02','1','2026-03-31 10:17:02',0x00,1),(98,45,1,3,3,NULL,'OSI',NULL,NULL,0x00,NULL,NULL,'1','2026-03-31 14:39:35','1','2026-03-31 14:39:35',0x00,1),(99,45,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-03-31 14:39:35','1','2026-03-31 14:39:35',0x00,1),(100,45,3,4,4,NULL,NULL,1,1,0x01,3,NULL,'1','2026-03-31 14:39:35','1','2026-03-31 14:39:35',0x00,1),(101,46,1,3,2,NULL,'PP',NULL,NULL,0x00,NULL,NULL,'1','2026-04-01 17:25:54','1','2026-04-01 17:25:54',0x00,1),(102,46,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-04-01 17:25:54','1','2026-04-01 17:25:54',0x00,1),(103,46,3,4,6,NULL,NULL,1,1,0x01,3,NULL,'1','2026-04-01 17:25:54','1','2026-04-01 17:25:54',0x00,1),(106,48,1,3,1,NULL,'M',NULL,NULL,0x00,NULL,NULL,'1','2026-04-02 14:31:05','1','2026-04-02 14:31:05',0x00,1),(107,48,2,4,5,NULL,NULL,1,1,0x00,NULL,NULL,'1','2026-04-02 14:31:05','1','2026-04-02 14:31:05',0x00,1),(108,49,1,3,3,NULL,'CHP',NULL,NULL,0x00,NULL,NULL,'1','2026-04-02 16:54:57','1','2026-04-02 16:54:57',0x00,1),(109,49,2,4,5,NULL,NULL,1,1,0x00,NULL,NULL,'1','2026-04-02 16:54:57','1','2026-04-02 16:54:57',0x00,1),(110,50,1,3,2,NULL,'BX',NULL,NULL,0x00,NULL,NULL,'1','2026-04-03 16:42:51','1','2026-04-03 16:42:51',0x00,1),(111,50,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-04-03 16:42:51','1','2026-04-03 16:42:51',0x00,1),(112,50,3,4,6,NULL,NULL,1,1,0x01,3,NULL,'1','2026-04-03 16:42:51','1','2026-04-03 16:42:51',0x00,1),(113,54,1,3,2,NULL,'WO',NULL,NULL,0x00,NULL,NULL,'1','2026-04-04 02:34:56','','2026-04-04 02:34:56',0x00,1),(114,54,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-04-04 02:34:56','','2026-04-04 02:34:56',0x00,1),(115,54,3,4,4,NULL,NULL,1,1,0x01,3,NULL,'1','2026-04-04 02:34:56','','2026-04-04 02:34:56',0x00,1),(116,55,1,3,4,NULL,'PROC',NULL,NULL,0x00,NULL,NULL,'1','2026-04-04 03:32:16','1','2026-04-04 03:32:16',0x00,1),(117,55,2,4,6,NULL,NULL,1,1,0x00,NULL,NULL,'1','2026-04-04 03:32:16','1','2026-04-04 03:32:16',0x00,1),(118,56,1,3,2,NULL,'PR',NULL,NULL,0x00,NULL,NULL,'1','2026-04-04 08:37:12','1','2026-04-04 08:37:12',0x00,1),(119,56,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-04-04 08:37:12','1','2026-04-04 08:37:12',0x00,1),(120,56,3,4,4,NULL,NULL,1,1,0x01,3,NULL,'1','2026-04-04 08:37:12','1','2026-04-04 08:37:12',0x00,1),(121,57,1,3,2,NULL,'FB',NULL,NULL,0x00,NULL,'固定前缀FB','1','2026-04-04 09:47:29','1','2026-04-04 09:47:29',0x00,1),(122,57,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,'日期部分','1','2026-04-04 09:47:29','1','2026-04-04 09:47:29',0x00,1),(123,57,3,4,6,NULL,NULL,1,1,0x01,3,'流水号，按天循环','1','2026-04-04 09:47:29','1','2026-04-04 09:47:29',0x00,1),(124,58,1,3,4,NULL,'CARD',NULL,NULL,0x00,NULL,NULL,'1','2026-04-04 12:18:15','1','2026-04-04 12:18:15',0x00,1),(125,58,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-04-04 12:18:15','1','2026-04-04 12:18:15',0x00,1),(126,58,3,4,6,NULL,NULL,1,1,0x01,3,NULL,'1','2026-04-04 12:18:15','1','2026-04-04 12:18:15',0x00,1),(127,59,1,3,6,NULL,'DEFECT',NULL,NULL,0x00,NULL,NULL,'1','2026-04-04 12:47:23','1','2026-04-04 12:47:23',0x00,1),(128,59,2,4,8,NULL,NULL,1,1,0x00,NULL,NULL,'1','2026-04-04 12:47:23','1','2026-04-04 12:47:23',0x00,1),(129,60,1,3,3,NULL,'QCT',NULL,NULL,0x00,NULL,NULL,'1','2026-04-04 14:36:43','1','2026-04-04 14:36:43',0x00,1),(130,60,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,NULL,'1','2026-04-04 14:36:43','1','2026-04-04 14:36:43',0x00,1),(131,60,3,4,6,NULL,NULL,1,1,0x01,3,NULL,'1','2026-04-04 14:36:43','1','2026-04-04 14:36:43',0x00,1),(132,61,1,3,2,NULL,'QR',NULL,NULL,0x00,NULL,'','1','2026-04-04 16:31:07','1','2026-04-04 16:34:26',0x00,1),(133,61,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,'','1','2026-04-04 16:31:07','1','2026-04-04 16:34:26',0x00,1),(134,61,3,4,5,NULL,NULL,1,1,0x01,3,'','1','2026-04-04 16:31:07','1','2026-04-04 16:34:26',0x00,1),(135,62,1,3,2,NULL,'OR',NULL,NULL,0x00,NULL,'','1','2026-04-04 16:31:07','1','2026-04-04 16:34:26',0x00,1),(136,62,2,2,8,'yyyyMMdd',NULL,NULL,NULL,0x00,NULL,'','1','2026-04-04 16:31:07','1','2026-04-04 16:34:26',0x00,1),(137,62,3,4,5,NULL,NULL,1,1,0x01,3,'','1','2026-04-04 16:31:07','1','2026-04-04 16:34:26',0x00,1),(138,63,1,3,4,NULL,'ITYP',0,0,0x00,0,NULL,'','2026-04-09 06:38:49','','2026-04-09 06:38:49',0x00,1),(139,63,2,2,8,'yyyyMMdd',NULL,0,0,0x00,0,NULL,'','2026-04-09 06:38:49','','2026-04-09 06:38:49',0x00,1),(140,63,3,4,4,NULL,NULL,1,1,0x01,3,NULL,'','2026-04-09 06:38:49','','2026-04-09 06:38:49',0x00,1),(141,47,1,3,2,NULL,'MT',NULL,NULL,0x00,NULL,NULL,'1','2026-04-09 07:29:15','1','2026-04-09 07:29:15',0x00,1),(142,47,2,4,3,NULL,NULL,1,1,0x00,NULL,NULL,'1','2026-04-09 07:29:15','1','2026-04-09 07:29:15',0x00,1),(143,64,1,3,1,NULL,'V',NULL,NULL,0x00,NULL,NULL,'','2026-04-09 11:36:40','','2026-04-09 11:36:40',0x00,1),(144,64,2,4,5,NULL,NULL,1,1,0x00,NULL,NULL,'','2026-04-09 11:36:40','','2026-04-09 11:36:40',0x00,1),(145,65,1,3,1,NULL,'W',NULL,NULL,0x00,NULL,NULL,'','2026-04-10 01:27:53','','2026-04-13 09:25:58',0x01,1),(146,65,2,4,4,NULL,NULL,1,1,0x00,NULL,NULL,'','2026-04-10 01:27:53','','2026-04-13 09:25:58',0x01,1),(147,66,1,3,5,NULL,'INDIC',NULL,NULL,0x00,NULL,'EEE','1','2026-05-01 16:47:19','1','2026-05-21 14:46:32',0x00,1),(148,66,2,4,8,NULL,NULL,1,1,0x00,NULL,NULL,'1','2026-05-01 16:47:19','1','2026-05-01 16:47:19',0x00,1),(149,67,1,3,4,NULL,'TEAM',NULL,NULL,0x00,NULL,'补充 MES 初始化编码规则','1','2026-06-12 14:06:49','1','2026-06-12 14:06:49',0x00,1),(150,67,2,4,4,NULL,NULL,1,1,0x00,NULL,'补充 MES 初始化编码规则','1','2026-06-12 14:06:49','1','2026-06-12 14:06:49',0x00,1),(151,68,1,3,3,NULL,'CHK',NULL,NULL,0x00,NULL,'补充 MES 初始化编码规则','1','2026-06-12 14:06:49','1','2026-06-12 14:06:49',0x00,1),(152,68,2,4,3,NULL,NULL,1,1,0x00,NULL,'补充 MES 初始化编码规则','1','2026-06-12 14:06:49','1','2026-06-12 14:06:49',0x00,1),(153,69,1,3,2,NULL,'WS',NULL,NULL,0x00,NULL,'补充 MES 初始化编码规则','1','2026-06-12 14:06:49','1','2026-06-12 14:06:49',0x00,1),(154,69,2,4,3,NULL,NULL,1,1,0x00,NULL,'补充 MES 初始化编码规则','1','2026-06-12 14:06:49','1','2026-06-12 14:06:49',0x00,1);
/*!40000 ALTER TABLE `mes_md_auto_code_part` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_md_auto_code_record`
--

DROP TABLE IF EXISTS `mes_md_auto_code_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_md_auto_code_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录 ID',
  `rule_id` bigint NOT NULL COMMENT '规则 ID',
  `result` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '生成的编码',
  `serial_no` bigint DEFAULT NULL COMMENT '生成的流水号（当规则组成中包含流水号分段时记录）',
  `input_char` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '传入的参数',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_rule_id` (`rule_id`) USING BTREE COMMENT '规则 ID 索引'
) ENGINE=InnoDB AUTO_INCREMENT=718 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 编码生成记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_md_auto_code_record`
--

LOCK TABLES `mes_md_auto_code_record` WRITE;
/*!40000 ALTER TABLE `mes_md_auto_code_record` DISABLE KEYS */;
INSERT INTO `mes_md_auto_code_record` VALUES (1,3,'SN20260305000001',1,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(2,3,'SN20260305000002',2,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(3,3,'SN20260305000003',3,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(4,3,'SN20260305000004',4,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(5,3,'SN20260305000005',5,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(6,3,'SN20260305000006',6,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(7,3,'SN20260305000007',7,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(8,3,'SN20260305000008',8,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(9,3,'SN20260305000009',9,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(10,3,'SN20260305000010',10,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(11,3,'SN20260305000011',11,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(12,3,'SN20260305000012',12,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(13,3,'SN20260305000013',13,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(14,3,'SN20260305000014',14,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(15,3,'SN20260305000015',15,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(16,3,'SN20260305000016',16,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(17,3,'SN20260305000017',17,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(18,3,'SN20260305000018',18,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(19,3,'SN20260305000019',19,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(20,3,'SN20260305000020',20,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(21,3,'SN20260305000021',21,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(22,3,'SN20260305000022',22,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(23,3,'SN20260305000023',23,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(24,3,'SN20260305000024',24,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(25,3,'SN20260305000025',25,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(26,3,'SN20260305000026',26,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(27,3,'SN20260305000027',27,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(28,3,'SN20260305000028',28,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(29,3,'SN20260305000029',29,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(30,3,'SN20260305000030',30,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(31,3,'SN20260305000031',31,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(32,3,'SN20260305000032',32,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(33,3,'SN20260305000033',33,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(34,3,'SN20260305000034',34,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(35,3,'SN20260305000035',35,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(36,3,'SN20260305000036',36,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(37,3,'SN20260305000037',37,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(38,3,'SN20260305000038',38,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(39,3,'SN20260305000039',39,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(40,3,'SN20260305000040',40,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(41,3,'SN20260305000041',41,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(42,3,'SN20260305000042',42,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(43,3,'SN20260305000043',43,NULL,'1','2026-03-05 09:51:00','1','2026-03-05 09:51:00',0x00,1),(44,3,'SN20260305000044',44,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(45,3,'SN20260305000045',45,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(46,3,'SN20260305000046',46,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(47,3,'SN20260305000047',47,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(48,3,'SN20260305000048',48,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(49,3,'SN20260305000049',49,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(50,3,'SN20260305000050',50,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(51,3,'SN20260305000051',51,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(52,3,'SN20260305000052',52,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(53,3,'SN20260305000053',53,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(54,3,'SN20260305000054',54,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(55,3,'SN20260305000055',55,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(56,3,'SN20260305000056',56,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(57,3,'SN20260305000057',57,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(58,3,'SN20260305000058',58,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(59,3,'SN20260305000059',59,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(60,3,'SN20260305000060',60,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(61,3,'SN20260305000061',61,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(62,3,'SN20260305000062',62,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(63,3,'SN20260305000063',63,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(64,3,'SN20260305000064',64,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(65,3,'SN20260305000065',65,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(66,3,'SN20260305000066',66,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(67,3,'SN20260305000067',67,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(68,3,'SN20260305000068',68,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(69,3,'SN20260305000069',69,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(70,3,'SN20260305000070',70,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(71,3,'SN20260305000071',71,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(72,3,'SN20260305000072',72,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(73,3,'SN20260305000073',73,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(74,3,'SN20260305000074',74,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(75,3,'SN20260305000075',75,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(76,3,'SN20260305000076',76,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(77,3,'SN20260305000077',77,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(78,3,'SN20260305000078',78,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(79,3,'SN20260305000079',79,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(80,3,'SN20260305000080',80,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(81,3,'SN20260305000081',81,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(82,3,'SN20260305000082',82,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(83,3,'SN20260305000083',83,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(84,3,'SN20260305000084',84,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(85,3,'SN20260305000085',85,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(86,3,'SN20260305000086',86,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(87,3,'SN20260305000087',87,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(88,3,'SN20260305000088',88,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(89,3,'SN20260305000089',89,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(90,3,'SN20260305000090',90,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(91,3,'SN20260305000091',91,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(92,3,'SN20260305000092',92,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(93,3,'SN20260305000093',93,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(94,3,'SN20260305000094',94,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(95,3,'SN20260305000095',95,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(96,3,'SN20260305000096',96,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(97,3,'SN20260305000097',97,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(98,3,'SN20260305000098',98,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(99,3,'SN20260305000099',99,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(100,3,'SN20260305000100',100,NULL,'1','2026-03-05 09:51:01','1','2026-03-05 09:51:01',0x00,1),(101,3,'SN20260305000101',101,NULL,'1','2026-03-05 13:24:55','1','2026-03-05 13:24:55',0x00,1),(102,3,'SN20260305000102',102,NULL,'1','2026-03-05 13:24:55','1','2026-03-05 13:24:55',0x00,1),(103,3,'SN20260305000103',103,NULL,'1','2026-03-05 13:24:55','1','2026-03-05 13:24:55',0x00,1),(104,3,'SN20260305000104',104,NULL,'1','2026-03-05 13:24:55','1','2026-03-05 13:24:55',0x00,1),(105,3,'SN20260305000105',105,NULL,'1','2026-03-05 13:24:55','1','2026-03-05 13:24:55',0x00,1),(106,3,'SN20260305000106',106,NULL,'1','2026-03-05 13:24:55','1','2026-03-05 13:24:55',0x00,1),(107,3,'SN20260305000107',107,NULL,'1','2026-03-05 13:24:55','1','2026-03-05 13:24:55',0x00,1),(108,3,'SN20260305000108',108,NULL,'1','2026-03-05 13:24:55','1','2026-03-05 13:24:55',0x00,1),(109,3,'SN20260305000109',109,NULL,'1','2026-03-05 13:24:55','1','2026-03-05 13:24:55',0x00,1),(110,3,'SN20260305000110',110,NULL,'1','2026-03-05 13:24:55','1','2026-03-05 13:24:55',0x00,1),(111,3,'SN20260305000111',111,NULL,'1','2026-03-05 13:24:55','1','2026-03-05 13:24:55',0x00,1),(112,3,'SN20260305000112',112,NULL,'1','2026-03-05 13:24:55','1','2026-03-05 13:24:55',0x00,1),(113,3,'SN20260305000113',113,NULL,'1','2026-03-05 13:24:55','1','2026-03-05 13:24:55',0x00,1),(114,3,'SN20260305000114',114,NULL,'1','2026-03-05 13:24:55','1','2026-03-05 13:24:55',0x00,1),(115,3,'SN20260305000115',115,NULL,'1','2026-03-05 13:24:55','1','2026-03-05 13:24:55',0x00,1),(116,3,'SN20260305000116',116,NULL,'1','2026-03-05 13:24:55','1','2026-03-05 13:24:55',0x00,1),(117,3,'SN20260305000117',117,NULL,'1','2026-03-05 13:24:55','1','2026-03-05 13:24:55',0x00,1),(118,3,'SN20260305000118',118,NULL,'1','2026-03-05 13:24:55','1','2026-03-05 13:24:55',0x00,1),(119,3,'SN20260305000119',119,NULL,'1','2026-03-05 13:24:55','1','2026-03-05 13:24:55',0x00,1),(120,3,'SN20260305000120',120,NULL,'1','2026-03-05 13:24:55','1','2026-03-05 13:24:55',0x00,1),(121,3,'SN20260305000121',121,NULL,'1','2026-03-05 13:24:55','1','2026-03-05 13:24:55',0x00,1),(122,3,'SN20260305000122',122,NULL,'1','2026-03-05 13:24:55','1','2026-03-05 13:24:55',0x00,1),(123,3,'SN20260305000123',123,NULL,'1','2026-03-05 13:24:55','1','2026-03-05 13:24:55',0x00,1),(124,3,'SN20260305000124',124,NULL,'1','2026-03-05 13:24:55','1','2026-03-05 13:24:55',0x00,1),(125,3,'SN20260305000125',125,NULL,'1','2026-03-05 13:24:56','1','2026-03-05 13:24:56',0x00,1),(126,3,'SN20260305000126',126,NULL,'1','2026-03-05 13:24:56','1','2026-03-05 13:24:56',0x00,1),(127,3,'SN20260305000127',127,NULL,'1','2026-03-05 13:24:56','1','2026-03-05 13:24:56',0x00,1),(128,3,'SN20260305000128',128,NULL,'1','2026-03-05 13:24:56','1','2026-03-05 13:24:56',0x00,1),(129,3,'SN20260305000129',129,NULL,'1','2026-03-05 13:24:56','1','2026-03-05 13:24:56',0x00,1),(130,3,'SN20260305000130',130,NULL,'1','2026-03-05 13:24:56','1','2026-03-05 13:24:56',0x00,1),(131,3,'SN20260305000131',131,NULL,'1','2026-03-05 13:24:56','1','2026-03-05 13:24:56',0x00,1),(132,3,'SN20260305000132',132,NULL,'1','2026-03-05 13:24:56','1','2026-03-05 13:24:56',0x00,1),(133,3,'SN20260305000133',133,NULL,'1','2026-03-05 13:24:56','1','2026-03-05 13:24:56',0x00,1),(134,3,'SN20260305000134',134,NULL,'1','2026-03-05 13:24:56','1','2026-03-05 13:24:56',0x00,1),(135,3,'SN20260305000135',135,NULL,'1','2026-03-05 13:24:56','1','2026-03-05 13:24:56',0x00,1),(136,3,'SN20260305000136',136,NULL,'1','2026-03-05 13:24:56','1','2026-03-05 13:24:56',0x00,1),(137,3,'SN20260305000137',137,NULL,'1','2026-03-05 13:24:56','1','2026-03-05 13:24:56',0x00,1),(138,3,'SN20260305000138',138,NULL,'1','2026-03-05 13:24:56','1','2026-03-05 13:24:56',0x00,1),(139,3,'SN20260305000139',139,NULL,'1','2026-03-05 13:24:56','1','2026-03-05 13:24:56',0x00,1),(140,3,'SN20260305000140',140,NULL,'1','2026-03-05 13:24:56','1','2026-03-05 13:24:56',0x00,1),(141,3,'SN20260305000141',141,NULL,'1','2026-03-05 13:24:56','1','2026-03-05 13:24:56',0x00,1),(142,3,'SN20260305000142',142,NULL,'1','2026-03-05 13:24:56','1','2026-03-05 13:24:56',0x00,1),(143,3,'SN20260305000143',143,NULL,'1','2026-03-05 13:24:56','1','2026-03-05 13:24:56',0x00,1),(144,3,'SN20260305000144',144,NULL,'1','2026-03-05 13:24:56','1','2026-03-05 13:24:56',0x00,1),(145,3,'SN20260305000145',145,NULL,'1','2026-03-05 13:24:56','1','2026-03-05 13:24:56',0x00,1),(146,3,'SN20260305000146',146,NULL,'1','2026-03-05 13:24:56','1','2026-03-05 13:24:56',0x00,1),(147,3,'SN20260305000147',147,NULL,'1','2026-03-05 13:24:56','1','2026-03-05 13:24:56',0x00,1),(148,3,'SN20260305000148',148,NULL,'1','2026-03-05 13:24:56','1','2026-03-05 13:24:56',0x00,1),(149,3,'SN20260305000149',149,NULL,'1','2026-03-05 13:24:56','1','2026-03-05 13:24:56',0x00,1),(150,3,'SN20260305000150',150,NULL,'1','2026-03-05 13:24:56','1','2026-03-05 13:24:56',0x00,1),(151,3,'SN20260305000151',151,NULL,'1','2026-03-05 13:29:19','1','2026-03-05 13:29:19',0x00,1),(152,3,'SN20260305000152',152,NULL,'1','2026-03-05 13:29:19','1','2026-03-05 13:29:19',0x00,1),(153,3,'SN20260305000153',153,NULL,'1','2026-03-05 13:29:19','1','2026-03-05 13:29:19',0x00,1),(154,3,'SN20260305000154',154,NULL,'1','2026-03-05 13:29:19','1','2026-03-05 13:29:19',0x00,1),(155,3,'SN20260305000155',155,NULL,'1','2026-03-05 13:29:19','1','2026-03-05 13:29:19',0x00,1),(156,3,'SN20260305000156',156,NULL,'1','2026-03-05 13:29:19','1','2026-03-05 13:29:19',0x00,1),(157,3,'SN20260305000157',157,NULL,'1','2026-03-05 13:29:19','1','2026-03-05 13:29:19',0x00,1),(158,3,'SN20260305000158',158,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(159,3,'SN20260305000159',159,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(160,3,'SN20260305000160',160,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(161,3,'SN20260305000161',161,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(162,3,'SN20260305000162',162,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(163,3,'SN20260305000163',163,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(164,3,'SN20260305000164',164,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(165,3,'SN20260305000165',165,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(166,3,'SN20260305000166',166,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(167,3,'SN20260305000167',167,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(168,3,'SN20260305000168',168,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(169,3,'SN20260305000169',169,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(170,3,'SN20260305000170',170,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(171,3,'SN20260305000171',171,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(172,3,'SN20260305000172',172,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(173,3,'SN20260305000173',173,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(174,3,'SN20260305000174',174,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(175,3,'SN20260305000175',175,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(176,3,'SN20260305000176',176,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(177,3,'SN20260305000177',177,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(178,3,'SN20260305000178',178,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(179,3,'SN20260305000179',179,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(180,3,'SN20260305000180',180,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(181,3,'SN20260305000181',181,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(182,3,'SN20260305000182',182,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(183,3,'SN20260305000183',183,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(184,3,'SN20260305000184',184,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(185,3,'SN20260305000185',185,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(186,3,'SN20260305000186',186,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(187,3,'SN20260305000187',187,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(188,3,'SN20260305000188',188,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(189,3,'SN20260305000189',189,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(190,3,'SN20260305000190',190,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(191,3,'SN20260305000191',191,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(192,3,'SN20260305000192',192,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(193,3,'SN20260305000193',193,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(194,3,'SN20260305000194',194,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(195,3,'SN20260305000195',195,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(196,3,'SN20260305000196',196,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(197,3,'SN20260305000197',197,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(198,3,'SN20260305000198',198,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(199,3,'SN20260305000199',199,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(200,3,'SN20260305000200',200,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(201,3,'SN20260305000201',201,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(202,3,'SN20260305000202',202,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(203,3,'SN20260305000203',203,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(204,3,'SN20260305000204',204,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(205,3,'SN20260305000205',205,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(206,3,'SN20260305000206',206,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(207,3,'SN20260305000207',207,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(208,3,'SN20260305000208',208,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(209,3,'SN20260305000209',209,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(210,3,'SN20260305000210',210,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(211,3,'SN20260305000211',211,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(212,3,'SN20260305000212',212,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(213,3,'SN20260305000213',213,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(214,3,'SN20260305000214',214,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(215,3,'SN20260305000215',215,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(216,3,'SN20260305000216',216,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(217,3,'SN20260305000217',217,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(218,3,'SN20260305000218',218,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(219,3,'SN20260305000219',219,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(220,3,'SN20260305000220',220,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(221,3,'SN20260305000221',221,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(222,3,'SN20260305000222',222,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(223,3,'SN20260305000223',223,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(224,3,'SN20260305000224',224,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(225,3,'SN20260305000225',225,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(226,3,'SN20260305000226',226,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(227,3,'SN20260305000227',227,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(228,3,'SN20260305000228',228,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(229,3,'SN20260305000229',229,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(230,3,'SN20260305000230',230,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(231,3,'SN20260305000231',231,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(232,3,'SN20260305000232',232,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(233,3,'SN20260305000233',233,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(234,3,'SN20260305000234',234,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(235,3,'SN20260305000235',235,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(236,3,'SN20260305000236',236,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(237,3,'SN20260305000237',237,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(238,3,'SN20260305000238',238,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(239,3,'SN20260305000239',239,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(240,3,'SN20260305000240',240,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(241,3,'SN20260305000241',241,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(242,3,'SN20260305000242',242,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(243,3,'SN20260305000243',243,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(244,3,'SN20260305000244',244,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(245,3,'SN20260305000245',245,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(246,3,'SN20260305000246',246,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(247,3,'SN20260305000247',247,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(248,3,'SN20260305000248',248,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(249,3,'SN20260305000249',249,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(250,3,'SN20260305000250',250,NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(251,4,'PKG202603080001',1,NULL,'1','2026-03-08 13:00:21','1','2026-03-08 13:00:21',0x00,1),(252,4,'PKG202603080002',2,NULL,'1','2026-03-08 13:04:24','1','2026-03-08 13:04:24',0x00,1),(253,4,'PKG202603080003',3,NULL,'1','2026-03-08 13:04:25','1','2026-03-08 13:04:25',0x00,1),(254,4,'PKG202603080004',4,NULL,'1','2026-03-08 13:04:29','1','2026-03-08 13:04:29',0x00,1),(255,4,'PKG202603080005',5,NULL,'1','2026-03-08 13:04:30','1','2026-03-08 13:04:30',0x00,1),(256,4,'PKG202603080006',6,NULL,'1','2026-03-08 13:04:30','1','2026-03-08 13:04:30',0x00,1),(257,4,'PKG202603080007',7,NULL,'1','2026-03-08 13:04:30','1','2026-03-08 13:04:30',0x00,1),(258,12,'PC',NULL,NULL,'1','2026-03-13 23:49:42','1','2026-03-13 23:49:42',0x00,1),(259,12,'PC20260',NULL,NULL,'1','2026-03-13 23:54:53','1','2026-03-13 23:54:53',0x00,1),(260,13,'TASK20260315000001',1,NULL,'1','2026-03-15 22:56:12','1','2026-03-15 22:56:12',0x00,1),(261,12,'PC202600001',1,NULL,'1','2026-03-21 15:07:46','1','2026-03-21 15:07:46',0x00,1),(262,14,'00000IQC202603230001',1,NULL,'1','2026-03-23 23:00:59','1','2026-03-23 23:00:59',0x00,1),(263,14,'00000IQC202603230002',2,NULL,'1','2026-03-23 23:00:59','1','2026-03-23 23:00:59',0x00,1),(264,14,'00000IQC202603230003',3,NULL,'1','2026-03-23 23:01:00','1','2026-03-23 23:01:00',0x00,1),(265,14,'00000IQC202603230004',4,NULL,'1','2026-03-23 23:01:00','1','2026-03-23 23:01:00',0x00,1),(266,14,'00000IQC202603230005',5,NULL,'1','2026-03-23 23:01:00','1','2026-03-23 23:01:00',0x00,1),(267,14,'00000IQC202603230006',6,NULL,'1','2026-03-23 23:01:03','1','2026-03-23 23:01:03',0x00,1),(268,14,'00000IQC202603230007',7,NULL,'1','2026-03-23 23:01:04','1','2026-03-23 23:01:04',0x00,1),(269,14,'00000IQC202603230008',8,NULL,'1','2026-03-23 23:01:04','1','2026-03-23 23:01:04',0x00,1),(270,14,'00000IQC202603230009',9,NULL,'1','2026-03-23 23:01:15','1','2026-03-23 23:01:15',0x00,1),(271,14,'IQC20260323010',10,NULL,'1','2026-03-23 23:03:34','1','2026-03-23 23:03:34',0x00,1),(272,14,'IQC20260323011',11,NULL,'1','2026-03-23 23:03:35','1','2026-03-23 23:03:35',0x00,1),(273,14,'IQC20260323012',12,NULL,'1','2026-03-23 23:03:35','1','2026-03-23 23:03:35',0x00,1),(274,14,'IQC20260323013',13,NULL,'1','2026-03-23 23:03:35','1','2026-03-23 23:03:35',0x00,1),(275,12,'PC202600002',2,NULL,'1','2026-03-24 23:17:24','1','2026-03-24 23:17:24',0x00,1),(276,15,'IPQC20260325000001',1,NULL,'1','2026-03-25 17:16:41','1','2026-03-25 17:16:41',0x00,1),(277,15,'IPQC20260325000002',2,NULL,'1','2026-03-25 17:28:19','1','2026-03-25 17:28:19',0x00,1),(278,14,'IQC20260325001',1,NULL,'1','2026-03-25 17:36:11','1','2026-03-25 17:36:11',0x00,1),(279,16,'RQC20260326000001',1,NULL,'1','2026-03-26 13:10:39','1','2026-03-26 13:10:39',0x00,1),(280,16,'RQC20260326000002',2,NULL,'1','2026-03-26 13:20:46','1','2026-03-26 13:20:46',0x00,1),(281,16,'RQC20260326000003',3,NULL,'1','2026-03-26 21:09:41','1','2026-03-26 21:09:41',0x00,1),(282,16,'RQC20260326000004',4,NULL,'1','2026-03-26 22:37:56','1','2026-03-26 22:37:56',0x00,1),(283,17,'OQC20260327001',1,NULL,'1','2026-03-27 17:01:56','1','2026-03-27 17:01:56',0x00,1),(284,18,'ITEM20260328000001',1,NULL,'1','2026-03-28 08:57:14','1','2026-03-28 08:57:14',0x00,1),(285,18,'ITEM20260328000002',2,NULL,'1','2026-03-28 08:57:15','1','2026-03-28 08:57:15',0x00,1),(286,18,'ITEM20260328000003',3,NULL,'1','2026-03-28 08:57:15','1','2026-03-28 08:57:15',0x00,1),(287,18,'ITEM20260328000004',4,NULL,'1','2026-03-28 08:57:18','1','2026-03-28 08:57:18',0x00,1),(288,18,'ITEM20260328000005',5,NULL,'1','2026-03-28 08:57:18','1','2026-03-28 08:57:18',0x00,1),(289,18,'ITEM20260328000006',6,NULL,'1','2026-03-28 08:57:18','1','2026-03-28 08:57:18',0x00,1),(290,18,'ITEM20260328000007',7,NULL,'1','2026-03-28 09:52:50','1','2026-03-28 09:52:50',0x00,1),(291,18,'ITEM20260328000008',8,NULL,'1','2026-03-28 10:34:27','1','2026-03-28 10:34:27',0x00,1),(292,18,'ITEM20260328000009',9,NULL,'1','2026-03-28 10:37:29','1','2026-03-28 10:37:29',0x00,1),(293,19,'C00001',1,NULL,'1','2026-03-28 17:09:31','1','2026-03-28 17:09:31',0x00,1),(294,19,'C00002',2,NULL,'1','2026-03-28 17:09:31','1','2026-03-28 17:09:31',0x00,1),(295,19,'C00003',3,NULL,'1','2026-03-28 17:09:32','1','2026-03-28 17:09:32',0x00,1),(296,19,'C00004',4,NULL,'1','2026-03-28 17:09:32','1','2026-03-28 17:09:32',0x00,1),(297,19,'C00005',5,NULL,'1','2026-03-28 17:09:32','1','2026-03-28 17:09:32',0x00,1),(298,19,'C00006',6,NULL,'1','2026-03-28 17:15:02','1','2026-03-28 17:15:02',0x00,1),(299,19,'C00007',7,NULL,'1','2026-03-28 17:15:03','1','2026-03-28 17:15:03',0x00,1),(300,19,'C00008',8,NULL,'1','2026-03-28 17:15:03','1','2026-03-28 17:15:03',0x00,1),(301,19,'C00009',9,NULL,'1','2026-03-28 17:15:03','1','2026-03-28 17:15:03',0x00,1),(302,19,'C00010',10,NULL,'1','2026-03-28 17:15:03','1','2026-03-28 17:15:03',0x00,1),(303,21,'WH202603280001',1,NULL,'1','2026-03-28 20:49:03','1','2026-03-28 20:49:03',0x00,1),(304,21,'WH202603280002',2,NULL,'1','2026-03-28 20:49:04','1','2026-03-28 20:49:04',0x00,1),(305,21,'WH202603280003',3,NULL,'1','2026-03-28 20:49:04','1','2026-03-28 20:49:04',0x00,1),(306,21,'WH202603280004',4,NULL,'1','2026-03-28 20:49:04','1','2026-03-28 20:49:04',0x00,1),(307,21,'WH202603280005',5,NULL,'1','2026-03-28 20:49:05','1','2026-03-28 20:49:05',0x00,1),(308,21,'WH202603280006',6,NULL,'1','2026-03-28 20:49:06','1','2026-03-28 20:49:06',0x00,1),(309,23,'AR202603280001',1,NULL,'1','2026-03-28 23:30:15','1','2026-03-28 23:30:15',0x00,1),(310,23,'AR202603280002',2,NULL,'1','2026-03-28 23:30:15','1','2026-03-28 23:30:15',0x00,1),(311,23,'AR202603280003',3,NULL,'1','2026-03-28 23:30:17','1','2026-03-28 23:30:17',0x00,1),(312,23,'AR202603280004',4,NULL,'1','2026-03-28 23:30:17','1','2026-03-28 23:30:17',0x00,1),(313,23,'AR202603280005',5,NULL,'1','2026-03-28 23:30:17','1','2026-03-28 23:30:17',0x00,1),(314,23,'AR202603280006',6,NULL,'1','2026-03-28 23:30:20','1','2026-03-28 23:30:20',0x00,1),(315,24,'TT001',1,NULL,'1','2026-03-29 09:30:11','1','2026-03-29 09:30:11',0x00,1),(316,24,'TT002',2,NULL,'1','2026-03-29 09:30:11','1','2026-03-29 09:30:11',0x00,1),(317,24,'TT003',3,NULL,'1','2026-03-29 09:30:12','1','2026-03-29 09:30:12',0x00,1),(318,24,'TT004',4,NULL,'1','2026-03-29 09:30:12','1','2026-03-29 09:30:12',0x00,1),(319,24,'TT005',5,NULL,'1','2026-03-29 09:30:13','1','2026-03-29 09:30:13',0x00,1),(320,24,'TT006',6,NULL,'1','2026-03-29 09:43:34','1','2026-03-29 09:43:34',0x00,1),(321,25,'TL202603290001',1,NULL,'1','2026-03-29 09:56:46','1','2026-03-29 09:56:46',0x00,1),(322,25,'TL202603290002',2,NULL,'1','2026-03-29 09:56:46','1','2026-03-29 09:56:46',0x00,1),(323,26,'IR20260329000001',1,NULL,'1','2026-03-29 16:18:47','1','2026-03-29 16:18:47',0x00,1),(324,27,'AN20260329000001',1,NULL,'1','2026-03-29 19:35:11','1','2026-03-29 19:35:11',0x00,1),(325,28,'RV20260329000001',1,NULL,'1','2026-03-29 21:29:30','1','2026-03-29 21:29:30',0x00,1),(326,28,'RV20260329000002',2,NULL,'1','2026-03-29 21:37:10','1','2026-03-29 21:37:10',0x00,1),(327,28,'RV20260329000003',3,NULL,'1','2026-03-29 22:09:03','1','2026-03-29 22:09:03',0x00,1),(328,28,'RV20260329000004',4,NULL,'1','2026-03-29 22:09:04','1','2026-03-29 22:09:04',0x00,1),(329,28,'RV20260329000005',5,NULL,'1','2026-03-29 22:09:04','1','2026-03-29 22:09:04',0x00,1),(330,28,'RV20260329000006',6,NULL,'1','2026-03-29 22:09:19','1','2026-03-29 22:09:19',0x00,1),(331,28,'RV20260329000007',7,NULL,'1','2026-03-29 22:09:19','1','2026-03-29 22:09:19',0x00,1),(332,28,'RV20260329000008',8,NULL,'1','2026-03-29 22:09:20','1','2026-03-29 22:09:20',0x00,1),(333,28,'RV20260329000009',9,NULL,'1','2026-03-29 22:09:22','1','2026-03-29 22:09:22',0x00,1),(334,28,'RV20260329000010',10,NULL,'1','2026-03-29 22:37:13','1','2026-03-29 22:37:13',0x00,1),(335,28,'RV20260329000011',11,NULL,'1','2026-03-29 22:44:53','1','2026-03-29 22:44:53',0x00,1),(336,29,'PI20260330000001',1,NULL,'1','2026-03-30 11:02:01','1','2026-03-30 11:02:01',0x00,1),(337,29,'PI20260330000002',2,NULL,'1','2026-03-30 11:02:08','1','2026-03-30 11:02:08',0x00,1),(338,30,'RI20260330000001',1,NULL,'1','2026-03-30 11:52:10','1','2026-03-30 11:52:10',0x00,1),(339,30,'RI20260330000002',2,NULL,'1','2026-03-30 12:07:33','1','2026-03-30 12:07:33',0x00,1),(340,31,'PR20260330000001',1,NULL,'1','2026-03-30 15:17:16','1','2026-03-30 15:17:16',0x00,1),(341,32,'SN20260330000001',1,NULL,'1','2026-03-30 16:58:23','1','2026-03-30 16:58:23',0x00,1),(342,32,'SN20260330000002',2,NULL,'1','2026-03-30 17:01:47','1','2026-03-30 17:01:47',0x00,1),(343,32,'SN20260330000003',3,NULL,'1','2026-03-30 17:01:47','1','2026-03-30 17:01:47',0x00,1),(344,32,'SN20260330000004',4,NULL,'1','2026-03-30 18:02:56','1','2026-03-30 18:02:56',0x00,1),(345,34,'RS20260330001',1,NULL,'1','2026-03-30 19:13:01','1','2026-03-30 19:13:01',0x00,1),(346,35,'PS20260330000001',1,NULL,'1','2026-03-30 20:31:53','1','2026-03-30 20:31:53',0x00,1),(347,35,'PS20260330000002',2,NULL,'1','2026-03-30 20:32:39','1','2026-03-30 20:32:39',0x00,1),(348,35,'PS20260330000003',3,NULL,'1','2026-03-30 20:58:23','1','2026-03-30 20:58:23',0x00,1),(349,35,'PS20260330000004',4,NULL,'1','2026-03-30 21:05:23','1','2026-03-30 21:05:23',0x00,1),(350,35,'PS20260330000005',5,NULL,'1','2026-03-30 21:08:56','1','2026-03-30 21:08:56',0x00,1),(351,36,'MISCI20260330001',1,NULL,'1','2026-03-30 23:40:16','1','2026-03-30 23:40:16',0x00,1),(352,36,'MISCI20260330002',2,NULL,'1','2026-03-30 23:40:17','1','2026-03-30 23:40:17',0x00,1),(353,36,'MISCI20260330003',3,NULL,'1','2026-03-30 23:40:17','1','2026-03-30 23:40:17',0x00,1),(354,36,'MISCI20260330004',4,NULL,'1','2026-03-30 23:40:17','1','2026-03-30 23:40:17',0x00,1),(355,36,'MISCI20260330005',5,NULL,'1','2026-03-30 23:40:18','1','2026-03-30 23:40:18',0x00,1),(356,37,'MISCR20260331001',1,NULL,'1','2026-03-31 09:55:00','1','2026-03-31 09:55:00',0x00,1),(357,37,'MISCR20260331002',2,NULL,'1','2026-03-31 10:03:12','1','2026-03-31 10:03:12',0x00,1),(358,37,'MISCR20260331003',3,NULL,'1','2026-03-31 10:03:13','1','2026-03-31 10:03:13',0x00,1),(359,37,'MISCR20260331004',4,NULL,'1','2026-03-31 10:03:13','1','2026-03-31 10:03:13',0x00,1),(360,37,'MISCR20260331005',5,NULL,'1','2026-03-31 10:03:18','1','2026-03-31 10:03:18',0x00,1),(361,37,'MISCR20260331006',6,NULL,'1','2026-03-31 10:03:18','1','2026-03-31 10:03:18',0x00,1),(362,37,'MISCR20260331007',7,NULL,'1','2026-03-31 10:03:18','1','2026-03-31 10:03:18',0x00,1),(363,37,'MISCR20260331008',8,NULL,'1','2026-03-31 10:03:18','1','2026-03-31 10:03:18',0x00,1),(364,37,'MISCR20260331009',9,NULL,'1','2026-03-31 10:03:19','1','2026-03-31 10:03:19',0x00,1),(365,37,'MISCR20260331010',10,NULL,'1','2026-03-31 10:03:20','1','2026-03-31 10:03:20',0x00,1),(366,37,'MISCR20260331011',11,NULL,'1','2026-03-31 10:03:20','1','2026-03-31 10:03:20',0x00,1),(367,37,'MISCR20260331012',12,NULL,'1','2026-03-31 10:04:29','1','2026-03-31 10:04:29',0x00,1),(368,37,'MISCR20260331013',13,NULL,'1','2026-03-31 10:06:46','1','2026-03-31 10:06:46',0x00,1),(369,37,'MISCR20260331014',14,NULL,'1','2026-03-31 10:06:46','1','2026-03-31 10:06:46',0x00,1),(370,37,'MISCR20260331015',15,NULL,'1','2026-03-31 10:06:46','1','2026-03-31 10:06:46',0x00,1),(371,37,'MISCR20260331016',16,NULL,'1','2026-03-31 10:06:46','1','2026-03-31 10:06:46',0x00,1),(372,37,'MISCR20260331017',17,NULL,'1','2026-03-31 10:06:46','1','2026-03-31 10:06:46',0x00,1),(373,37,'MISCR20260331018',18,NULL,'1','2026-03-31 10:06:48','1','2026-03-31 10:06:48',0x00,1),(374,37,'MISCR20260331019',19,NULL,'1','2026-03-31 10:06:49','1','2026-03-31 10:06:49',0x00,1),(375,37,'MISCR20260331020',20,NULL,'1','2026-03-31 10:06:51','1','2026-03-31 10:06:51',0x00,1),(376,38,'TR20260331001',1,NULL,'1','2026-03-31 16:02:49','1','2026-03-31 16:02:49',0x00,1),(377,38,'TR20260331002',2,NULL,'1','2026-03-31 16:02:50','1','2026-03-31 16:02:50',0x00,1),(378,43,'PDP202603310001',1,NULL,'1','2026-03-31 18:22:57','1','2026-03-31 18:22:57',0x00,1),(379,4,'PKG202603310001',1,NULL,'1','2026-03-31 20:06:21','1','2026-03-31 20:06:21',0x00,1),(380,45,'OSI202603310001',1,NULL,'1','2026-03-31 22:42:06','1','2026-03-31 22:42:06',0x00,1),(381,45,'OSI202603310002',2,NULL,'1','2026-03-31 22:42:28','1','2026-03-31 22:42:28',0x00,1),(382,45,'OSI202603310003',3,NULL,'1','2026-03-31 23:16:01','1','2026-03-31 23:16:01',0x00,1),(383,45,'OSI202603310004',4,NULL,'1','2026-03-31 23:17:18','1','2026-03-31 23:17:18',0x00,1),(384,46,'PP20260402000001',1,NULL,'1','2026-04-02 01:27:42','1','2026-04-02 01:27:42',0x00,1),(385,46,'PP20260402000002',2,NULL,'1','2026-04-02 01:27:43','1','2026-04-02 01:27:43',0x00,1),(386,46,'PP20260402000003',3,NULL,'1','2026-04-02 01:28:03','1','2026-04-02 01:28:03',0x00,1),(387,47,'MT001',1,NULL,'1','2026-04-02 21:54:33','1','2026-04-02 21:54:33',0x00,1),(388,47,'MT002',2,NULL,'1','2026-04-02 21:54:33','1','2026-04-02 21:54:33',0x00,1),(389,47,'MT003',3,NULL,'1','2026-04-02 21:54:34','1','2026-04-02 21:54:34',0x00,1),(390,47,'MT004',4,NULL,'1','2026-04-02 21:54:34','1','2026-04-02 21:54:34',0x00,1),(391,48,'M00001',1,NULL,'1','2026-04-02 23:19:23','1','2026-04-02 23:19:23',0x00,1),(392,48,'M00002',2,NULL,'1','2026-04-02 23:37:03','1','2026-04-02 23:37:03',0x00,1),(393,50,'BX20260404000001',1,NULL,'1','2026-04-04 00:43:29','1','2026-04-04 00:43:29',0x00,1),(394,50,'BX20260404000002',2,NULL,'1','2026-04-04 00:43:30','1','2026-04-04 00:43:30',0x00,1),(395,50,'BX20260404000003',3,NULL,'1','2026-04-04 00:50:44','1','2026-04-04 00:50:44',0x00,1),(396,50,'BX20260404000004',4,NULL,'1','2026-04-04 00:58:09','1','2026-04-04 00:58:09',0x00,1),(397,50,'BX20260404000005',5,NULL,'1','2026-04-04 01:09:09','1','2026-04-04 01:09:09',0x00,1),(398,54,'WO202604040001',1,NULL,'1','2026-04-04 10:37:55','1','2026-04-04 10:37:55',0x00,1),(399,54,'WO202604040002',2,NULL,'1','2026-04-04 10:37:56','1','2026-04-04 10:37:56',0x00,1),(400,54,'WO202604040003',3,NULL,'1','2026-04-04 10:37:56','1','2026-04-04 10:37:56',0x00,1),(401,54,'WO202604040004',4,NULL,'1','2026-04-04 10:37:56','1','2026-04-04 10:37:56',0x00,1),(402,54,'WO202604040005',5,NULL,'1','2026-04-04 10:37:57','1','2026-04-04 10:37:57',0x00,1),(403,54,'WO202604040006',6,NULL,'1','2026-04-04 10:37:57','1','2026-04-04 10:37:57',0x00,1),(404,55,'PROC000001',1,NULL,'1','2026-04-04 11:32:18','1','2026-04-04 11:32:18',0x00,1),(405,55,'PROC000002',2,NULL,'1','2026-04-04 11:32:18','1','2026-04-04 11:32:18',0x00,1),(406,55,'PROC000003',3,NULL,'1','2026-04-04 11:32:19','1','2026-04-04 11:32:19',0x00,1),(407,55,'PROC000004',4,NULL,'1','2026-04-04 11:32:19','1','2026-04-04 11:32:19',0x00,1),(408,56,'PR202604040001',1,NULL,'1','2026-04-04 16:37:52','1','2026-04-04 16:37:52',0x00,1),(409,56,'PR202604040002',2,NULL,'1','2026-04-04 16:37:52','1','2026-04-04 16:37:52',0x00,1),(410,56,'PR202604040003',3,NULL,'1','2026-04-04 16:37:53','1','2026-04-04 16:37:53',0x00,1),(411,56,'PR202604040004',4,NULL,'1','2026-04-04 16:37:56','1','2026-04-04 16:37:56',0x00,1),(412,57,'FB20260404000001',1,NULL,'1','2026-04-04 19:44:34','1','2026-04-04 19:44:34',0x00,1),(413,57,'FB20260404000002',2,NULL,'1','2026-04-04 19:44:37','1','2026-04-04 19:44:37',0x00,1),(414,57,'FB20260404000003',3,NULL,'1','2026-04-04 19:46:59','1','2026-04-04 19:46:59',0x00,1),(415,57,'FB20260404000004',4,NULL,'1','2026-04-04 19:49:03','1','2026-04-04 19:49:03',0x00,1),(416,58,'CARD20260404000001',1,NULL,'1','2026-04-04 20:30:27','1','2026-04-04 20:30:27',0x00,1),(417,59,'DEFECT00000001',1,NULL,'1','2026-04-04 20:48:09','1','2026-04-04 20:48:09',0x00,1),(418,59,'DEFECT00000002',2,NULL,'1','2026-04-04 20:48:10','1','2026-04-04 20:48:10',0x00,1),(419,59,'DEFECT00000003',3,NULL,'1','2026-04-04 20:48:10','1','2026-04-04 20:48:10',0x00,1),(420,59,'DEFECT00000004',4,NULL,'1','2026-04-04 20:48:10','1','2026-04-04 20:48:10',0x00,1),(421,60,'QCT20260404000002',2,NULL,'1','2026-04-04 22:36:59','1','2026-04-04 22:36:59',0x00,1),(422,60,'QCT20260404000001',1,NULL,'1','2026-04-04 22:36:59','1','2026-04-04 22:36:59',0x00,1),(423,60,'QCT20260404000003',3,NULL,'1','2026-04-04 22:37:00','1','2026-04-04 22:37:00',0x00,1),(424,60,'QCT20260404000004',4,NULL,'1','2026-04-04 22:37:34','1','2026-04-04 22:37:34',0x00,1),(425,61,'QR2026040500001',1,NULL,'1','2026-04-05 00:48:00','1','2026-04-05 00:48:00',0x00,1),(426,61,'QR2026040500002',2,NULL,'1','2026-04-05 00:48:00','1','2026-04-05 00:48:00',0x00,1),(427,61,'QR2026040500003',3,NULL,'1','2026-04-05 00:48:00','1','2026-04-05 00:48:00',0x00,1),(428,61,'QR2026040500004',4,NULL,'1','2026-04-05 00:48:01','1','2026-04-05 00:48:01',0x00,1),(429,4,'PKG202604060001',1,NULL,'1','2026-04-06 00:52:28','1','2026-04-06 00:52:28',0x00,1),(430,4,'PKG202604060002',2,NULL,'1','2026-04-06 01:07:49','1','2026-04-06 01:07:49',0x00,1),(431,62,'OR2026040600001',1,NULL,'1','2026-04-06 09:50:19','1','2026-04-06 09:50:19',0x00,1),(432,28,'RV20260406000001',1,NULL,'1','2026-04-06 10:40:54','1','2026-04-06 10:40:54',0x00,1),(433,44,'PDT202604060001',1,NULL,'1','2026-04-06 12:05:00','1','2026-04-06 12:05:00',0x00,1),(434,28,'RV20260406000002',2,NULL,'1','2026-04-06 16:58:23','1','2026-04-06 16:58:23',0x00,1),(435,57,'FB20260406000001',1,NULL,'1','2026-04-06 23:41:30','1','2026-04-06 23:41:30',0x00,1),(436,57,'FB20260406000002',2,NULL,'1','2026-04-06 23:45:19','1','2026-04-06 23:45:19',0x00,1),(437,57,'FB20260407000001',1,NULL,'1','2026-04-07 23:34:29','1','2026-04-07 23:34:29',0x00,1),(438,57,'FB20260407000002',2,NULL,'1','2026-04-07 23:34:36','1','2026-04-07 23:34:36',0x00,1),(439,50,'BX20260407000001',1,NULL,'1','2026-04-07 23:42:45','1','2026-04-07 23:42:45',0x00,1),(440,63,'ITYP202604090001',1,NULL,'1','2026-04-09 14:39:24','1','2026-04-09 14:39:24',0x00,1),(441,63,'ITYP202604090002',2,NULL,'1','2026-04-09 14:39:24','1','2026-04-09 14:39:24',0x00,1),(442,63,'ITYP202604090003',3,NULL,'1','2026-04-09 14:39:25','1','2026-04-09 14:39:25',0x00,1),(443,63,'ITYP202604090004',4,NULL,'1','2026-04-09 14:39:25','1','2026-04-09 14:39:25',0x00,1),(444,63,'ITYP202604090005',5,NULL,'1','2026-04-09 14:47:34','1','2026-04-09 14:47:34',0x00,1),(445,64,'V00001',1,NULL,'1','2026-04-09 19:38:12','1','2026-04-09 19:38:12',0x00,1),(446,64,'V00002',2,NULL,'1','2026-04-09 19:38:13','1','2026-04-09 19:38:13',0x00,1),(447,57,'FB20260411000001',1,NULL,'1','2026-04-11 20:36:42','1','2026-04-11 20:36:42',0x00,1),(448,57,'FB20260413000001',1,NULL,'1','2026-04-13 20:19:49','1','2026-04-13 20:19:49',0x00,1),(449,57,'FB20260413000002',2,NULL,'1','2026-04-13 20:23:44','1','2026-04-13 20:23:44',0x00,1),(450,57,'FB20260414000001',1,NULL,'1','2026-04-14 10:13:50','1','2026-04-14 10:13:50',0x00,1),(451,57,'FB20260414000002',2,NULL,'1','2026-04-14 13:54:58','1','2026-04-14 13:54:58',0x00,1),(452,57,'FB20260414000003',3,NULL,'1','2026-04-14 14:03:09','1','2026-04-14 14:03:09',0x00,1),(453,57,'FB20260414000004',4,NULL,'1','2026-04-14 14:57:52','1','2026-04-14 14:57:52',0x00,1),(454,57,'FB20260415000001',1,NULL,'1','2026-04-15 16:46:52','1','2026-04-15 16:46:52',0x00,1),(455,57,'FB20260415000002',2,NULL,'1','2026-04-15 16:48:13','1','2026-04-15 16:48:13',0x00,1),(456,3,'SN20260415000001',1,NULL,'1','2026-04-15 19:31:39','1','2026-04-15 19:31:39',0x00,1),(457,3,'SN20260415000002',2,NULL,'1','2026-04-15 19:31:39','1','2026-04-15 19:31:39',0x00,1),(458,3,'SN20260415000003',3,NULL,'1','2026-04-15 19:31:39','1','2026-04-15 19:31:39',0x00,1),(459,3,'SN20260415000004',4,NULL,'1','2026-04-15 19:31:39','1','2026-04-15 19:31:39',0x00,1),(460,3,'SN20260415000005',5,NULL,'1','2026-04-15 19:31:39','1','2026-04-15 19:31:39',0x00,1),(461,3,'SN20260415000006',6,NULL,'1','2026-04-15 19:31:39','1','2026-04-15 19:31:39',0x00,1),(462,3,'SN20260415000007',7,NULL,'1','2026-04-15 19:31:39','1','2026-04-15 19:31:39',0x00,1),(463,3,'SN20260415000008',8,NULL,'1','2026-04-15 19:31:39','1','2026-04-15 19:31:39',0x00,1),(464,3,'SN20260415000009',9,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(465,3,'SN20260415000010',10,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(466,3,'SN20260415000011',11,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(467,3,'SN20260415000012',12,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(468,3,'SN20260415000013',13,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(469,3,'SN20260415000014',14,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(470,3,'SN20260415000015',15,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(471,3,'SN20260415000016',16,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(472,3,'SN20260415000017',17,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(473,3,'SN20260415000018',18,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(474,3,'SN20260415000019',19,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(475,3,'SN20260415000020',20,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(476,3,'SN20260415000021',21,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(477,3,'SN20260415000022',22,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(478,3,'SN20260415000023',23,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(479,3,'SN20260415000024',24,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(480,3,'SN20260415000025',25,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(481,3,'SN20260415000026',26,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(482,3,'SN20260415000027',27,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(483,3,'SN20260415000028',28,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(484,3,'SN20260415000029',29,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(485,3,'SN20260415000030',30,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(486,3,'SN20260415000031',31,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(487,3,'SN20260415000032',32,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(488,3,'SN20260415000033',33,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(489,3,'SN20260415000034',34,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(490,3,'SN20260415000035',35,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(491,3,'SN20260415000036',36,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(492,3,'SN20260415000037',37,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(493,3,'SN20260415000038',38,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(494,3,'SN20260415000039',39,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(495,3,'SN20260415000040',40,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(496,3,'SN20260415000041',41,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(497,3,'SN20260415000042',42,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(498,3,'SN20260415000043',43,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(499,3,'SN20260415000044',44,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(500,3,'SN20260415000045',45,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(501,3,'SN20260415000046',46,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(502,3,'SN20260415000047',47,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(503,3,'SN20260415000048',48,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(504,3,'SN20260415000049',49,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(505,3,'SN20260415000050',50,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(506,3,'SN20260415000051',51,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(507,3,'SN20260415000052',52,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(508,3,'SN20260415000053',53,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(509,3,'SN20260415000054',54,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(510,3,'SN20260415000055',55,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(511,3,'SN20260415000056',56,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(512,3,'SN20260415000057',57,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(513,3,'SN20260415000058',58,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(514,3,'SN20260415000059',59,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(515,3,'SN20260415000060',60,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(516,3,'SN20260415000061',61,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(517,3,'SN20260415000062',62,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(518,3,'SN20260415000063',63,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(519,3,'SN20260415000064',64,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(520,3,'SN20260415000065',65,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(521,3,'SN20260415000066',66,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(522,3,'SN20260415000067',67,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(523,3,'SN20260415000068',68,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(524,3,'SN20260415000069',69,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(525,3,'SN20260415000070',70,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(526,3,'SN20260415000071',71,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(527,3,'SN20260415000072',72,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(528,3,'SN20260415000073',73,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(529,3,'SN20260415000074',74,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(530,3,'SN20260415000075',75,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(531,3,'SN20260415000076',76,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(532,3,'SN20260415000077',77,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(533,3,'SN20260415000078',78,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(534,3,'SN20260415000079',79,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(535,3,'SN20260415000080',80,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(536,3,'SN20260415000081',81,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(537,3,'SN20260415000082',82,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(538,3,'SN20260415000083',83,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(539,3,'SN20260415000084',84,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(540,3,'SN20260415000085',85,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(541,3,'SN20260415000086',86,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(542,3,'SN20260415000087',87,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(543,3,'SN20260415000088',88,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(544,3,'SN20260415000089',89,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(545,3,'SN20260415000090',90,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(546,3,'SN20260415000091',91,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(547,3,'SN20260415000092',92,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(548,3,'SN20260415000093',93,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(549,3,'SN20260415000094',94,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(550,3,'SN20260415000095',95,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(551,3,'SN20260415000096',96,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(552,3,'SN20260415000097',97,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(553,3,'SN20260415000098',98,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(554,3,'SN20260415000099',99,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(555,3,'SN20260415000100',100,NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(556,60,'QCT20260415000001',1,NULL,'1','2026-04-15 20:36:17','1','2026-04-15 20:36:17',0x00,1),(557,29,'PI20260415000001',1,NULL,'1','2026-04-15 23:51:38','1','2026-04-15 23:51:38',0x00,1),(558,29,'PI20260415000002',2,NULL,'1','2026-04-15 23:51:38','1','2026-04-15 23:51:38',0x00,1),(559,29,'PI20260415000003',3,NULL,'1','2026-04-15 23:51:38','1','2026-04-15 23:51:38',0x00,1),(560,61,'QR2026041500001',1,NULL,'1','2026-04-15 23:54:49','1','2026-04-15 23:54:49',0x00,1),(561,29,'PI20260415000004',4,NULL,'1','2026-04-15 23:55:25','1','2026-04-15 23:55:25',0x00,1),(562,61,'QR2026041500002',2,NULL,'1','2026-04-15 23:56:36','1','2026-04-15 23:56:36',0x00,1),(563,29,'PI20260416000001',1,NULL,'1','2026-04-16 01:48:48','1','2026-04-16 01:48:48',0x00,1),(564,29,'PI20260416000002',2,NULL,'1','2026-04-16 01:48:48','1','2026-04-16 01:48:48',0x00,1),(565,50,'BX20260416000001',1,NULL,'1','2026-04-16 08:20:55','1','2026-04-16 08:20:55',0x00,1),(566,50,'BX20260416000002',2,NULL,'1','2026-04-16 08:23:35','1','2026-04-16 08:23:35',0x00,1),(567,50,'BX20260416000003',3,NULL,'1','2026-04-16 08:25:47','1','2026-04-16 08:25:47',0x00,1),(568,50,'BX20260416000004',4,NULL,'1','2026-04-16 09:15:45','1','2026-04-16 09:15:45',0x00,1),(569,61,'QR2026041700001',1,NULL,'1','2026-04-17 07:33:07','1','2026-04-17 07:33:07',0x00,1),(570,36,'MISCI20260417001',1,NULL,'1','2026-04-17 08:41:35','1','2026-04-17 08:41:35',0x00,1),(571,37,'MISCR20260417001',1,NULL,'1','2026-04-17 08:43:31','1','2026-04-17 08:43:31',0x00,1),(572,45,'OSI202604170001',1,NULL,'1','2026-04-17 09:35:10','1','2026-04-17 09:35:10',0x00,1),(573,45,'OSI202604170002',2,NULL,'1','2026-04-17 09:35:15','1','2026-04-17 09:35:15',0x00,1),(574,12,'PC202600003',3,NULL,'1','2026-04-17 09:38:09','1','2026-04-17 09:38:09',0x00,1),(575,32,'SN20260417000001',1,NULL,'1','2026-04-17 09:44:54','1','2026-04-17 09:44:54',0x00,1),(576,29,'PI20260417000001',1,NULL,'1','2026-04-17 15:27:31','1','2026-04-17 15:27:31',0x00,1),(577,66,'INDIC00000001',1,NULL,'1','2026-05-02 00:53:54','1','2026-05-02 00:53:54',0x00,1),(578,66,'INDIC00000002',2,NULL,'1','2026-05-02 00:53:56','1','2026-05-02 00:53:56',0x00,1),(579,63,'ITYP202605190001',1,NULL,'1','2026-05-19 18:06:21','1','2026-05-19 18:06:21',0x00,1),(580,63,'ITYP202605190002',2,NULL,'1','2026-05-19 18:06:21','1','2026-05-19 18:06:21',0x00,1),(581,63,'ITYP202605190003',3,NULL,'1','2026-05-19 18:06:22','1','2026-05-19 18:06:22',0x00,1),(582,63,'ITYP202605190004',4,NULL,'1','2026-05-19 18:06:23','1','2026-05-19 18:06:23',0x00,1),(583,63,'ITYP202605190005',5,NULL,'1','2026-05-19 22:13:13','1','2026-05-19 22:13:13',0x00,1),(584,63,'ITYP202605190006',6,NULL,'1','2026-05-19 22:13:13','1','2026-05-19 22:13:13',0x00,1),(585,63,'ITYP202605190007',7,NULL,'1','2026-05-19 22:13:37','1','2026-05-19 22:13:37',0x00,1),(586,63,'ITYP202605190008',8,NULL,'1','2026-05-19 22:13:37','1','2026-05-19 22:13:37',0x00,1),(587,18,'ITEM20260521000001',1,NULL,'1','2026-05-21 18:47:57','1','2026-05-21 18:47:57',0x00,1),(588,18,'ITEM20260521000002',2,NULL,'1','2026-05-21 18:47:58','1','2026-05-21 18:47:58',0x00,1),(589,18,'ITEM20260521000003',3,NULL,'1','2026-05-21 18:47:58','1','2026-05-21 18:47:58',0x00,1),(590,18,'ITEM20260521000004',4,NULL,'1','2026-05-21 18:47:58','1','2026-05-21 18:47:58',0x00,1),(591,24,'TT007',7,NULL,'1','2026-05-22 08:46:48','1','2026-05-22 08:46:48',0x00,1),(592,24,'TT008',8,NULL,'1','2026-05-22 08:46:48','1','2026-05-22 08:46:48',0x00,1),(593,24,'TT009',9,NULL,'1','2026-05-22 08:46:48','1','2026-05-22 08:46:48',0x00,1),(594,24,'TT010',10,NULL,'1','2026-05-22 08:46:48','1','2026-05-22 08:46:48',0x00,1),(595,24,'TT011',11,NULL,'1','2026-05-22 08:46:48','1','2026-05-22 08:46:48',0x00,1),(596,24,'TT012',12,NULL,'1','2026-05-22 08:46:48','1','2026-05-22 08:46:48',0x00,1),(597,24,'TT013',13,NULL,'1','2026-05-22 08:46:49','1','2026-05-22 08:46:49',0x00,1),(598,24,'TT014',14,NULL,'1','2026-05-22 08:46:49','1','2026-05-22 08:46:49',0x00,1),(599,24,'TT015',15,NULL,'1','2026-05-22 08:46:49','1','2026-05-22 08:46:49',0x00,1),(600,24,'TT016',16,NULL,'1','2026-05-22 08:46:49','1','2026-05-22 08:46:49',0x00,1),(601,24,'TT017',17,NULL,'1','2026-05-22 08:46:49','1','2026-05-22 08:46:49',0x00,1),(602,24,'TT018',18,NULL,'1','2026-05-22 08:46:49','1','2026-05-22 08:46:49',0x00,1),(603,24,'TT019',19,NULL,'1','2026-05-22 08:46:50','1','2026-05-22 08:46:50',0x00,1),(604,24,'TT020',20,NULL,'1','2026-05-22 08:46:50','1','2026-05-22 08:46:50',0x00,1),(605,24,'TT021',21,NULL,'1','2026-05-22 08:46:52','1','2026-05-22 08:46:52',0x00,1),(606,24,'TT022',22,NULL,'1','2026-05-22 08:46:52','1','2026-05-22 08:46:52',0x00,1),(607,24,'TT023',23,NULL,'1','2026-05-22 08:46:52','1','2026-05-22 08:46:52',0x00,1),(608,24,'TT024',24,NULL,'1','2026-05-22 08:46:52','1','2026-05-22 08:46:52',0x00,1),(609,24,'TT025',25,NULL,'1','2026-05-22 08:46:52','1','2026-05-22 08:46:52',0x00,1),(610,24,'TT026',26,NULL,'1','2026-05-22 08:46:52','1','2026-05-22 08:46:52',0x00,1),(611,24,'TT027',27,NULL,'1','2026-05-22 08:46:53','1','2026-05-22 08:46:53',0x00,1),(612,24,'TT028',28,NULL,'1','2026-05-22 08:46:53','1','2026-05-22 08:46:53',0x00,1),(613,24,'TT029',29,NULL,'1','2026-05-22 08:46:53','1','2026-05-22 08:46:53',0x00,1),(614,24,'TT030',30,NULL,'1','2026-05-22 08:46:53','1','2026-05-22 08:46:53',0x00,1),(615,24,'TT031',31,NULL,'1','2026-05-22 08:46:53','1','2026-05-22 08:46:53',0x00,1),(616,24,'TT032',32,NULL,'1','2026-05-22 08:46:53','1','2026-05-22 08:46:53',0x00,1),(617,24,'TT033',33,NULL,'1','2026-05-22 08:46:53','1','2026-05-22 08:46:53',0x00,1),(618,24,'TT034',34,NULL,'1','2026-05-22 08:46:54','1','2026-05-22 08:46:54',0x00,1),(619,24,'TT035',35,NULL,'1','2026-05-22 08:46:54','1','2026-05-22 08:46:54',0x00,1),(620,24,'TT036',36,NULL,'1','2026-05-22 08:46:54','1','2026-05-22 08:46:54',0x00,1),(621,24,'TT037',37,NULL,'1','2026-05-22 08:46:54','1','2026-05-22 08:46:54',0x00,1),(622,24,'TT038',38,NULL,'1','2026-05-22 08:46:54','1','2026-05-22 08:46:54',0x00,1),(623,24,'TT039',39,NULL,'1','2026-05-22 08:46:54','1','2026-05-22 08:46:54',0x00,1),(624,24,'TT040',40,NULL,'1','2026-05-22 08:46:54','1','2026-05-22 08:46:54',0x00,1),(625,24,'TT041',41,NULL,'1','2026-05-22 08:46:55','1','2026-05-22 08:46:55',0x00,1),(626,24,'TT042',42,NULL,'1','2026-05-22 08:46:55','1','2026-05-22 08:46:55',0x00,1),(627,24,'TT043',43,NULL,'1','2026-05-22 08:46:55','1','2026-05-22 08:46:55',0x00,1),(628,24,'TT044',44,NULL,'1','2026-05-22 08:46:55','1','2026-05-22 08:46:55',0x00,1),(629,24,'TT045',45,NULL,'1','2026-05-22 08:46:55','1','2026-05-22 08:46:55',0x00,1),(630,24,'TT046',46,NULL,'1','2026-05-22 08:46:55','1','2026-05-22 08:46:55',0x00,1),(631,24,'TT047',47,NULL,'1','2026-05-22 08:46:55','1','2026-05-22 08:46:55',0x00,1),(632,24,'TT048',48,NULL,'1','2026-05-22 08:46:56','1','2026-05-22 08:46:56',0x00,1),(633,24,'TT049',49,NULL,'1','2026-05-22 08:46:56','1','2026-05-22 08:46:56',0x00,1),(634,24,'TT050',50,NULL,'1','2026-05-22 08:46:56','1','2026-05-22 08:46:56',0x00,1),(635,24,'TT051',51,NULL,'1','2026-05-22 08:46:56','1','2026-05-22 08:46:56',0x00,1),(636,24,'TT052',52,NULL,'1','2026-05-22 08:46:56','1','2026-05-22 08:46:56',0x00,1),(637,24,'TT053',53,NULL,'1','2026-05-22 08:46:56','1','2026-05-22 08:46:56',0x00,1),(638,24,'TT054',54,NULL,'1','2026-05-22 08:46:56','1','2026-05-22 08:46:56',0x00,1),(639,24,'TT055',55,NULL,'1','2026-05-22 08:46:57','1','2026-05-22 08:46:57',0x00,1),(640,25,'TL202605220001',1,NULL,'1','2026-05-22 08:47:09','1','2026-05-22 08:47:09',0x00,1),(641,25,'TL202605220002',2,NULL,'1','2026-05-22 08:47:12','1','2026-05-22 08:47:12',0x00,1),(642,25,'TL202605220003',3,NULL,'1','2026-05-22 08:47:12','1','2026-05-22 08:47:12',0x00,1),(643,25,'TL202605220004',4,NULL,'1','2026-05-22 08:47:13','1','2026-05-22 08:47:13',0x00,1),(644,25,'TL202605220005',5,NULL,'1','2026-05-22 08:47:13','1','2026-05-22 08:47:13',0x00,1),(645,25,'TL202605220006',6,NULL,'1','2026-05-22 08:47:13','1','2026-05-22 08:47:13',0x00,1),(646,25,'TL202605220007',7,NULL,'1','2026-05-22 08:47:13','1','2026-05-22 08:47:13',0x00,1),(647,25,'TL202605220008',8,NULL,'1','2026-05-22 08:47:14','1','2026-05-22 08:47:14',0x00,1),(648,25,'TL202605220009',9,NULL,'1','2026-05-22 08:47:21','1','2026-05-22 08:47:21',0x00,1),(649,25,'TL202605220010',10,NULL,'1','2026-05-22 08:47:21','1','2026-05-22 08:47:21',0x00,1),(650,25,'TL202605220011',11,NULL,'1','2026-05-22 08:47:21','1','2026-05-22 08:47:21',0x00,1),(651,25,'TL202605220012',12,NULL,'1','2026-05-22 08:47:22','1','2026-05-22 08:47:22',0x00,1),(652,25,'TL202605220013',13,NULL,'1','2026-05-22 08:47:22','1','2026-05-22 08:47:22',0x00,1),(653,25,'TL202605220014',14,NULL,'1','2026-05-22 08:47:22','1','2026-05-22 08:47:22',0x00,1),(654,18,'ITEM20260522000001',1,NULL,'1','2026-05-22 08:47:38','1','2026-05-22 08:47:38',0x00,1),(655,18,'ITEM20260522000002',2,NULL,'1','2026-05-22 08:47:40','1','2026-05-22 08:47:40',0x00,1),(656,18,'ITEM20260522000003',3,NULL,'1','2026-05-22 08:47:40','1','2026-05-22 08:47:40',0x00,1),(657,18,'ITEM20260522000004',4,NULL,'1','2026-05-22 08:47:41','1','2026-05-22 08:47:41',0x00,1),(658,18,'ITEM20260522000005',5,NULL,'1','2026-05-22 08:47:41','1','2026-05-22 08:47:41',0x00,1),(659,18,'ITEM20260522000006',6,NULL,'1','2026-05-22 08:47:41','1','2026-05-22 08:47:41',0x00,1),(660,18,'ITEM20260522000007',7,NULL,'1','2026-05-22 08:47:41','1','2026-05-22 08:47:41',0x00,1),(661,18,'ITEM20260522000008',8,NULL,'1','2026-05-22 08:47:41','1','2026-05-22 08:47:41',0x00,1),(662,18,'ITEM20260522000009',9,NULL,'1','2026-05-22 08:47:42','1','2026-05-22 08:47:42',0x00,1),(663,18,'ITEM20260522000010',10,NULL,'1','2026-05-22 08:47:45','1','2026-05-22 08:47:45',0x00,1),(664,18,'ITEM20260522000011',11,NULL,'1','2026-05-22 08:47:45','1','2026-05-22 08:47:45',0x00,1),(665,18,'ITEM20260522000012',12,NULL,'1','2026-05-22 08:47:46','1','2026-05-22 08:47:46',0x00,1),(666,18,'ITEM20260522000013',13,NULL,'1','2026-05-22 08:47:48','1','2026-05-22 08:47:48',0x00,1),(667,26,'IR20260522000001',1,NULL,'1','2026-05-22 08:47:58','1','2026-05-22 08:47:58',0x00,1),(668,26,'IR20260522000002',2,NULL,'1','2026-05-22 08:47:59','1','2026-05-22 08:47:59',0x00,1),(669,24,'TT056',56,NULL,'1','2026-05-22 08:50:15','1','2026-05-22 08:50:15',0x00,1),(670,24,'TT057',57,NULL,'1','2026-05-22 08:50:16','1','2026-05-22 08:50:16',0x00,1),(671,24,'TT058',58,NULL,'1','2026-05-22 08:50:16','1','2026-05-22 08:50:16',0x00,1),(672,24,'TT059',59,NULL,'1','2026-05-22 08:50:16','1','2026-05-22 08:50:16',0x00,1),(673,24,'TT060',60,NULL,'1','2026-05-22 08:50:16','1','2026-05-22 08:50:16',0x00,1),(674,24,'TT061',61,NULL,'1','2026-05-24 18:33:01','1','2026-05-24 18:33:01',0x00,1),(675,24,'TT062',62,NULL,'1','2026-05-24 18:33:01','1','2026-05-24 18:33:01',0x00,1),(676,47,'MT005',5,NULL,'1','2026-05-24 20:24:07','1','2026-05-24 20:24:07',0x00,1),(677,47,'MT006',6,NULL,'1','2026-05-24 20:24:08','1','2026-05-24 20:24:08',0x00,1),(678,47,'MT007',7,NULL,'1','2026-05-24 20:24:08','1','2026-05-24 20:24:08',0x00,1),(679,47,'MT008',8,NULL,'1','2026-05-24 20:24:08','1','2026-05-24 20:24:08',0x00,1),(680,49,'CHP00001',1,NULL,'1','2026-05-24 20:24:53','1','2026-05-24 20:24:53',0x00,1),(681,49,'CHP00002',2,NULL,'1','2026-05-24 20:24:53','1','2026-05-24 20:24:53',0x00,1),(682,49,'CHP00003',3,NULL,'1','2026-05-24 20:24:54','1','2026-05-24 20:24:54',0x00,1),(683,49,'CHP00004',4,NULL,'1','2026-05-24 20:24:54','1','2026-05-24 20:24:54',0x00,1),(684,49,'CHP00005',5,NULL,'1','2026-05-24 20:24:54','1','2026-05-24 20:24:54',0x00,1),(685,49,'CHP00006',6,NULL,'1','2026-05-24 20:24:54','1','2026-05-24 20:24:54',0x00,1),(686,49,'CHP00007',7,NULL,'1','2026-05-24 20:24:54','1','2026-05-24 20:24:54',0x00,1),(687,49,'CHP00008',8,NULL,'1','2026-05-24 20:24:55','1','2026-05-24 20:24:55',0x00,1),(688,49,'CHP00009',9,NULL,'1','2026-05-24 20:24:55','1','2026-05-24 20:24:55',0x00,1),(689,49,'CHP00010',10,NULL,'1','2026-05-24 20:24:55','1','2026-05-24 20:24:55',0x00,1),(690,55,'PROC000005',5,NULL,'1','2026-05-25 09:02:51','1','2026-05-25 09:02:51',0x00,1),(691,55,'PROC000006',6,NULL,'1','2026-05-25 09:02:52','1','2026-05-25 09:02:52',0x00,1),(692,55,'PROC000007',7,NULL,'1','2026-05-25 09:02:52','1','2026-05-25 09:02:52',0x00,1),(693,55,'PROC000008',8,NULL,'1','2026-05-25 09:02:52','1','2026-05-25 09:02:52',0x00,1),(694,55,'PROC000009',9,NULL,'1','2026-05-25 09:02:52','1','2026-05-25 09:02:52',0x00,1),(695,55,'PROC000010',10,NULL,'1','2026-05-25 09:02:53','1','2026-05-25 09:02:53',0x00,1),(696,57,'FB20260526000001',1,NULL,'1','2026-05-26 00:37:42','1','2026-05-26 00:37:42',0x00,1),(697,57,'FB20260526000002',2,NULL,'1','2026-05-26 08:35:32','1','2026-05-26 08:35:32',0x00,1),(698,57,'FB20260526000003',3,NULL,'1','2026-05-26 21:32:08','1','2026-05-26 21:32:08',0x00,1),(699,57,'FB20260526000004',4,NULL,'1','2026-05-26 21:32:09','1','2026-05-26 21:32:09',0x00,1),(700,59,'DEFECT00000005',5,NULL,'1','2026-05-27 06:38:25','1','2026-05-27 06:38:25',0x00,1),(701,59,'DEFECT00000006',6,NULL,'1','2026-05-27 06:38:25','1','2026-05-27 06:38:25',0x00,1),(702,59,'DEFECT00000007',7,NULL,'1','2026-05-27 06:38:25','1','2026-05-27 06:38:25',0x00,1),(703,59,'DEFECT00000008',8,NULL,'1','2026-05-27 06:38:25','1','2026-05-27 06:38:25',0x00,1),(704,59,'DEFECT00000009',9,NULL,'1','2026-05-27 06:38:25','1','2026-05-27 06:38:25',0x00,1),(705,59,'DEFECT00000010',10,NULL,'1','2026-05-27 06:38:26','1','2026-05-27 06:38:26',0x00,1),(706,59,'DEFECT00000011',11,NULL,'1','2026-05-27 06:38:26','1','2026-05-27 06:38:26',0x00,1),(707,59,'DEFECT00000012',12,NULL,'1','2026-05-27 06:38:26','1','2026-05-27 06:38:26',0x00,1),(708,59,'DEFECT00000013',13,NULL,'1','2026-05-27 06:38:26','1','2026-05-27 06:38:26',0x00,1),(709,21,'WH202605270001',1,NULL,'1','2026-05-27 06:41:53','1','2026-05-27 06:41:53',0x00,1),(710,21,'WH202605270002',2,NULL,'1','2026-05-27 06:41:53','1','2026-05-27 06:41:53',0x00,1),(711,21,'WH202605270003',3,NULL,'1','2026-05-27 06:42:10','1','2026-05-27 06:42:10',0x00,1),(712,60,'QCT20260528000001',1,NULL,'1','2026-05-28 23:56:39','1','2026-05-28 23:56:39',0x00,1),(713,14,'IQC20260529001',1,NULL,'1','2026-05-29 09:56:16','1','2026-05-29 09:56:16',0x00,1),(714,4,'PKG202605290001',1,NULL,'1','2026-05-29 23:55:50','1','2026-05-29 23:55:50',0x00,1),(715,4,'PKG202605290002',2,NULL,'1','2026-05-29 23:55:51','1','2026-05-29 23:55:51',0x00,1),(716,57,'FB20260821000001',1,NULL,'1','2026-08-21 10:43:23','1','2026-08-21 10:43:23',0x00,1),(717,57,'FB20260821000002',2,NULL,'1','2026-08-21 13:13:54','1','2026-08-21 13:13:54',0x00,1);
/*!40000 ALTER TABLE `mes_md_auto_code_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_md_auto_code_rule`
--

DROP TABLE IF EXISTS `mes_md_auto_code_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_md_auto_code_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '规则 ID',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '描述',
  `max_length` int DEFAULT NULL COMMENT '最大长度',
  `padded` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否补齐',
  `padded_char` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '补齐字符',
  `padded_method` tinyint DEFAULT '1' COMMENT '补齐方式',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_code` (`code`,`deleted`,`tenant_id`) USING BTREE COMMENT '规则编码唯一索引'
) ENGINE=InnoDB AUTO_INCREMENT=70 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 编码规则表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_md_auto_code_rule`
--

LOCK TABLES `mes_md_auto_code_rule` WRITE;
/*!40000 ALTER TABLE `mes_md_auto_code_rule` DISABLE KEYS */;
INSERT INTO `mes_md_auto_code_rule` VALUES (1,'132','323','QQQ',20,0x00,'0',1,0,'XXX','1','2026-03-04 23:47:19','1','2026-03-05 00:52:50',0x00,1),(2,'312','333','444',5,0x00,NULL,1,0,NULL,'1','2026-03-05 01:05:45','1','2026-03-05 01:05:45',0x00,1),(3,'WM_SN_CODE','SN 码','SN 码自动生成规则',NULL,0x00,NULL,1,0,NULL,'1','2026-03-05 01:29:24','1','2026-03-23 14:58:01',0x00,1),(4,'WM_PACKAGE_CODE','装箱单编码',NULL,NULL,0x00,NULL,1,0,NULL,'1','2026-03-08 04:44:00','1','2026-03-23 14:58:01',0x00,1),(12,'WM_BATCH_CODE','批次编码','批次编码自动生成规则',11,0x00,NULL,1,0,NULL,'1','2026-03-13 15:36:41','1','2026-03-23 14:58:01',0x00,1),(13,'PRO_TASK_CODE','生产任务编码','生产任务编码自动生成规则',NULL,0x00,NULL,NULL,0,NULL,'1','2026-03-15 14:42:59','1','2026-03-23 14:58:01',0x00,1),(14,'QC_IQC_CODE','来料检验单编码','来料检验单（IQC）自动编码规则',14,0x00,'0',1,0,NULL,'','2026-03-23 14:52:16','','2026-03-23 15:03:11',0x00,1),(15,'QC_IPQC_CODE','过程检验单编码','过程检验单编码自动生成规则',NULL,0x00,NULL,NULL,0,NULL,'1','2026-03-24 13:30:06','1','2026-03-24 13:30:06',0x00,1),(16,'QC_RQC_CODE','退货检验单编码','退货检验单编码自动生成规则',NULL,0x00,NULL,NULL,0,NULL,'1','2026-03-26 05:09:58','1','2026-03-26 05:09:58',0x00,1),(17,'QC_OQC_CODE','出货检验单编码','OQC + 日期 + 流水号',14,0x00,'0',1,0,NULL,'1','2026-03-27 09:00:03','1','2026-03-27 09:00:03',0x00,1),(18,'MD_ITEM_CODE','物料编码','物料编码自动生成规则',NULL,0x00,NULL,NULL,0,NULL,'1','2026-03-28 00:56:57','1','2026-03-28 00:56:57',0x00,1),(19,'MD_CLIENT_CODE','客户编码','客户编码规则：前缀C + 5位流水号',6,0x00,NULL,NULL,0,NULL,'admin','2026-03-28 03:41:15','admin','2026-03-28 09:12:15',0x00,1),(20,'MD_WORKSTATION_CODE','工作站编码','工作站编码自动生成规则',NULL,0x00,NULL,NULL,0,NULL,'1','2026-03-28 09:41:24','1','2026-03-28 09:41:24',0x00,1),(21,'WM_WAREHOUSE_CODE','仓库编码','仓库编码自动生成规则',NULL,0x00,NULL,NULL,0,NULL,'1','2026-03-28 12:48:18','1','2026-03-28 12:48:18',0x00,1),(22,'WM_LOCATION_CODE','库区编码','库区编码自动生成规则',NULL,0x00,NULL,NULL,0,NULL,'1','2026-03-28 12:48:18','1','2026-03-28 12:48:18',0x00,1),(23,'WM_AREA_CODE','库位编码','库位编码自动生成规则',NULL,0x00,NULL,NULL,0,NULL,'1','2026-03-28 12:48:18','1','2026-03-28 12:48:18',0x00,1),(24,'TM_TOOL_TYPE_CODE','工具类型编码','工具类型自动编码规则',5,0x00,NULL,NULL,0,NULL,'admin','2026-03-29 01:27:07','admin','2026-03-29 01:27:07',0x00,1),(25,'TM_TOOL_CODE','工具编码','工具编码自动生成规则',NULL,0x00,NULL,NULL,0,NULL,'1','2026-03-29 01:56:00','1','2026-03-29 01:56:00',0x00,1),(26,'WM_ITEM_RECEIPT_CODE','采购入库单编码','采购入库单编码自动生成规则',NULL,0x00,NULL,NULL,0,NULL,'1','2026-03-29 02:48:40','1','2026-03-29 02:48:40',0x00,1),(27,'WM_ARRIVAL_NOTICE_CODE','到货通知单编码','到货通知单自动编码规则',NULL,0x00,NULL,NULL,0,NULL,'1','2026-03-29 11:10:40','1','2026-03-29 11:10:40',0x00,1),(28,'WM_RETURN_VENDOR_CODE','采购退货单编码','采购退货单编码自动生成规则',NULL,0x00,NULL,NULL,0,NULL,'1','2026-03-29 13:01:34','1','2026-03-29 13:01:34',0x00,1),(29,'WM_PRODUCT_ISSUE_CODE','生产领料出库单编码','生产领料出库单编码自动生成规则',NULL,0x00,NULL,NULL,0,NULL,'1','2026-03-30 01:44:21','1','2026-03-30 01:44:21',0x00,1),(30,'WM_RETURN_ISSUE_CODE','生产退料单编码','生产退料单编码自动生成规则',NULL,0x00,NULL,NULL,0,NULL,'1','2026-03-30 03:49:52','1','2026-03-30 03:49:52',0x00,1),(31,'PRODUCTRECPT_CODE','产品入库单编码','产品入库单编码自动生成规则',NULL,0x00,NULL,NULL,0,NULL,'1','2026-03-30 04:35:05','1','2026-03-30 04:35:05',0x00,1),(32,'WM_SALES_NOTICE_CODE','发货通知单编码','发货通知单编码自动生成规则',NULL,0x00,NULL,NULL,0,NULL,'1','2026-03-30 05:05:32','1','2026-03-30 05:05:32',0x00,1),(34,'WM_RETURN_SALES_CODE','销售退货单编码规则','格式: RS + yyyyMMdd + 3位流水号',13,0x00,NULL,1,0,NULL,'1','2026-03-30 11:11:15','1','2026-03-30 11:11:15',0x00,1),(35,'WM_PRODUCT_SALES_CODE','销售出库单编码','销售出库单编码自动生成规则',NULL,0x00,NULL,NULL,0,NULL,'1','2026-03-30 12:04:29','1','2026-03-30 12:04:29',0x00,1),(36,'WM_MISC_ISSUE_CODE','杂项出库单编码',NULL,16,0x00,NULL,1,0,NULL,'1','2026-03-30 15:19:37','1','2026-03-30 15:19:37',0x00,1),(37,'WM_MISC_RECEIPT_CODE','杂项入库单编码',NULL,16,0x00,NULL,1,0,NULL,'1','2026-03-31 01:48:51','1','2026-03-31 01:48:51',0x00,1),(38,'TRANSFER_CODE','转移单编码规则',NULL,13,0x00,NULL,1,0,NULL,'','2026-03-31 07:57:32','','2026-03-31 08:01:53',0x00,1),(43,'WM_STOCK_TAKING_PLAN_CODE','盘点方案编码','盘点方案编码规则',20,0x00,NULL,1,0,NULL,'1','2026-03-31 10:17:02','1','2026-03-31 10:17:02',0x00,1),(44,'WM_STOCK_TAKING_CODE','盘点任务编码','盘点任务编码规则',20,0x00,NULL,1,0,NULL,'1','2026-03-31 10:17:02','1','2026-03-31 10:17:02',0x00,1),(45,'WM_OUTSOURCE_ISSUE_CODE','外协发料单编码','外协发料单编码规则',20,0x00,NULL,1,0,NULL,'1','2026-03-31 14:39:35','1','2026-03-31 14:39:35',0x00,1),(46,'CAL_PLAN_CODE','排班计划编码','排班计划编码自动生成规则',NULL,0x00,NULL,NULL,0,NULL,'1','2026-04-01 17:25:54','1','2026-04-01 17:25:54',0x00,1),(47,'DV_MACHINERY_TYPE_CODE','设备类型编码','设备类型编码自动生成规则',NULL,0x00,NULL,NULL,0,NULL,'1','2026-04-02 07:02:16','1','2026-04-09 07:29:15',0x00,1),(48,'DV_MACHINERY_CODE','设备编码','设备编码自动生成规则',NULL,0x00,NULL,NULL,0,NULL,'1','2026-04-02 14:31:05','1','2026-04-02 14:31:05',0x00,1),(49,'DV_CHECK_PLAN_CODE','点检保养方案编码','点检保养方案编码自动生成规则',NULL,0x00,NULL,1,0,NULL,'1','2026-04-02 16:54:57','1','2026-04-02 16:54:57',0x00,1),(50,'DV_REPAIR_CODE','维修单编码','维修单编码自动生成规则',NULL,0x00,NULL,NULL,0,NULL,'1','2026-04-03 16:42:51','1','2026-04-03 16:42:51',0x00,1),(54,'PRO_WORK_ORDER_CODE','生产工单编码',NULL,20,0x00,NULL,1,0,NULL,'1','2026-04-04 02:34:56','','2026-04-04 02:34:56',0x00,1),(55,'PRO_PROCESS_CODE','工序编码','工序编码自动生成规则',NULL,0x00,NULL,NULL,0,NULL,'1','2026-04-04 03:32:16','1','2026-04-04 03:32:16',0x00,1),(56,'PRO_ROUTE_CODE','工艺路线编码','工艺路线单的自动编码规则',50,0x00,'0',1,0,NULL,'1','2026-04-04 08:37:12','1','2026-04-04 08:37:12',0x00,1),(57,'PRO_FEEDBACK_CODE','生产报工单编码','生产报工单自动编码规则',NULL,0x00,NULL,1,0,NULL,'1','2026-04-04 09:47:29','1','2026-04-04 09:47:29',0x00,1),(58,'PRO_CARD_CODE','流转卡编码','生产流转卡自动编码规则',NULL,0x00,NULL,NULL,0,NULL,'1','2026-04-04 12:18:15','1','2026-04-04 12:18:15',0x00,1),(59,'QC_DEFECT_CODE','缺陷类型编码','缺陷类型编码自动生成规则',NULL,0x00,NULL,NULL,0,NULL,'1','2026-04-04 12:47:23','1','2026-04-04 12:47:23',0x00,1),(60,'QC_TEMPLATE_CODE','质检方案编码','质检方案自动编码规则',NULL,0x00,NULL,NULL,0,NULL,'1','2026-04-04 14:36:43','1','2026-04-04 14:36:43',0x00,1),(61,'QC_INDICATOR_RESULT_CODE','样品检验结果编码','自动生成',20,0x00,NULL,1,0,'','1','2026-04-04 16:31:07','1','2026-04-04 16:34:26',0x00,1),(62,'WM_OUTSOURCE_RECEIPT_CODE','外协入库单编码','自动生成',20,0x00,NULL,1,0,'','1','2026-04-04 16:31:07','1','2026-04-04 16:34:26',0x00,1),(63,'MD_ITEM_TYPE_CODE','物料分类编码','物料/产品分类编码',0,0x00,NULL,1,0,NULL,'','2026-04-09 06:38:49','','2026-04-09 06:38:49',0x00,1),(64,'MD_VENDOR_CODE','车间编码规则','车间编码自动生成规则',6,0x00,NULL,NULL,0,NULL,'','2026-04-09 11:36:40','1','2026-04-13 17:27:26',0x00,1),(65,'MD_WORKSHOP_CODE','è½¦é—´ç¼–ç ','è½¦é—´æµæ°´å·: W + 4ä½åºåˆ—å·',5,0x00,NULL,1,0,NULL,'','2026-04-10 01:27:53','1','2026-04-13 17:25:58',0x01,1),(66,'QC_INDICATOR_CODE','检测项编码',NULL,NULL,0x00,NULL,1,0,NULL,'1','2026-05-01 16:47:19','1','2026-05-01 16:47:19',0x00,1),(67,'CAL_TEAM_CODE','班组编码','班组设置编码规则',8,0x00,NULL,NULL,0,'补充 MES 初始化编码规则','1','2026-06-12 14:06:49','1','2026-06-12 14:06:49',0x00,1),(68,'DV_SUBJECT_CODE','点检保养项目编码','点检保养项目编码规则',6,0x00,NULL,NULL,0,'补充 MES 初始化编码规则','1','2026-06-12 14:06:49','1','2026-06-12 14:06:49',0x00,1),(69,'MD_WORKSHOP_CODE','车间编码','车间设置编码规则',5,0x00,NULL,NULL,0,'补充 MES 初始化编码规则','1','2026-06-12 14:06:49','1','2026-06-12 14:06:49',0x00,1);
/*!40000 ALTER TABLE `mes_md_auto_code_rule` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_md_client`
--

DROP TABLE IF EXISTS `mes_md_client`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_md_client` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '客户编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户名称',
  `nickname` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户简称',
  `english_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户英文名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户简介',
  `logo` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户LOGO地址',
  `type` tinyint NOT NULL COMMENT '客户类型',
  `address` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户地址',
  `website` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户官网地址',
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户邮箱地址',
  `telephone` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户电话',
  `contact1_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系人1',
  `contact1_telephone` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系人1-电话',
  `contact1_email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系人1-邮箱',
  `contact2_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系人2',
  `contact2_telephone` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系人2-电话',
  `contact2_email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系人2-邮箱',
  `credit_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '统一社会信用代码',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=209 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 客户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_md_client`
--

LOCK TABLES `mes_md_client` WRITE;
/*!40000 ALTER TABLE `mes_md_client` DISABLE KEYS */;
INSERT INTO `mes_md_client` VALUES (200,'C00184','比亚迪股份有限公司','比亚迪','BYD','比亚迪品牌诞生于深圳','',1,'深圳南山区无名路12号','https://www.bydglobal.com','salse@bydglobal.com','123432222','张三','122212312','s1@bydglobal.com','李四','1132323232','s2@bydglobal.com','11212121',0,'','1','2026-02-15 15:06:50','1','2026-02-15 15:06:50',0x00,1),(207,'C00197','博世','博世','BOSCH',NULL,NULL,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,NULL,'1','2026-02-15 15:06:50','1','2026-02-15 15:06:50',0x00,1),(208,'C00198','德力西电气','德力西','DELIXI',NULL,NULL,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,NULL,'1','2026-02-15 15:06:50','1','2026-02-15 15:06:50',0x00,1);
/*!40000 ALTER TABLE `mes_md_client` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_md_item`
--

DROP TABLE IF EXISTS `mes_md_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_md_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物料编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料名称',
  `specification` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规格型号',
  `unit_measure_id` bigint NOT NULL DEFAULT '0' COMMENT '计量单位编号',
  `item_type_id` bigint NOT NULL DEFAULT '0' COMMENT '物料分类编号',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `safe_stock_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否启用安全库存',
  `min_stock` decimal(12,4) NOT NULL DEFAULT '0.0000' COMMENT '最低库存量',
  `max_stock` decimal(12,4) NOT NULL DEFAULT '0.0000' COMMENT '最高库存量',
  `high_value` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否高值物料',
  `batch_flag` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否启用批次管理',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1240 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 物料产品表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_md_item`
--

LOCK TABLES `mes_md_item` WRITE;
/*!40000 ALTER TABLE `mes_md_item` DISABLE KEYS */;
INSERT INTO `mes_md_item` VALUES (69,'IF2022082437','色粉【黑色】','黑色2',201,275,0,0x00,0.0000,0.0000,0x00,0x01,'','admin','2022-08-24 21:44:21','admin','2026-02-15 14:07:59',0x00,1),(70,'IF2022082432','PVC颗粒','透明',200,275,0,0x00,0.0000,0.0000,0x00,0x01,'','admin','2022-08-24 21:44:59','admin','2026-02-15 14:07:59',0x00,1),(71,'IF2022082403','色粉【蓝色】','蓝色',201,275,1,0x00,0.0000,0.0000,0x00,0x01,'','admin','2022-08-24 21:45:24','admin','2026-02-15 14:07:59',0x00,1),(72,'IF2022082404','钢筋','100mm X  5mm',204,274,1,0x00,0.0000,0.0000,0x00,0x01,'','admin','2022-08-24 21:46:06','testuser','2026-02-15 14:07:59',0x00,1),(73,'IF2022082428','螺丝刀刀柄【蓝色】','10CM',202,276,1,0x00,0.0000,0.0000,0x00,0x01,'','admin','2022-08-24 21:52:09','admin','2026-02-15 14:07:59',0x00,1),(74,'IF2022082416','螺丝刀刀头','15CM',202,276,1,0x00,0.0000,0.0000,0x00,0x01,'','admin','2022-08-24 21:52:35','admin','2026-02-15 14:07:59',0x00,1),(75,'IF2022082439','螺丝刀【蓝色，一字型】','蓝色，一字型，3.2x75mm',202,277,1,0x00,0.0000,0.0000,0x00,0x01,'','admin','2022-08-24 21:52:46','admin','2026-02-15 14:07:59',0x00,1),(94,'IF20250312003','小包装盒',NULL,202,282,0,0x01,1000.0000,50000.0000,0x00,0x00,'','admin','2025-03-12 10:36:41','admin','2026-02-15 14:07:59',0x00,1),(95,'IF20250312004','大包装箱',NULL,202,282,0,0x01,500.0000,1000.0000,0x00,0x00,'','admin','2025-03-12 10:37:08','','2026-02-15 14:07:59',0x00,1),(96,'IF20250312005','螺丝刀刀柄【黑色】',NULL,202,276,1,0x00,0.0000,0.0000,0x00,0x01,'','admin','2025-03-12 10:39:32','1','2026-02-16 17:39:22',0x00,1),(100,'SF-BLACK-001','ABC','EFG',200,272,0,0x00,0.0000,0.0000,0x00,0x01,'','1','2026-03-10 11:14:37','1','2026-03-15 00:36:38',0x00,1),(101,'ABC-A','ABC-A',NULL,200,275,0,0x00,0.0000,0.0000,0x00,0x01,'','1','2026-03-15 00:35:28','1','2026-03-15 00:35:28',0x00,1),(102,'ABC-B','ABC-B',NULL,200,274,0,0x00,0.0000,0.0000,0x00,0x01,'','1','2026-03-15 00:35:46','1','2026-03-15 00:36:25',0x00,1),(103,'ABC-CCC','ABC-CCC',NULL,200,273,0,0x00,0.0000,0.0000,0x00,0x01,'','1','2026-03-15 10:12:30','1','2026-03-15 10:15:59',0x00,1),(104,'ITEM20260328000007','AAA',NULL,200,200,1,0x00,0.0000,0.0000,0x00,0x01,'','1','2026-03-28 09:52:57','1','2026-03-28 10:29:14',0x00,1),(105,'ITEM20260328000008','奥特曼',NULL,200,274,1,0x00,0.0000,0.0000,0x00,0x01,'','1','2026-03-28 10:34:37','1','2026-03-28 10:34:37',0x00,1),(106,'ITEM20260328000009','剑来','呃呃呃',200,274,1,0x00,0.0000,0.0000,0x00,0x01,'','1','2026-03-28 10:37:51','1','2026-05-28 09:35:45',0x00,1),(1072,'IF2022082404','钢筋','100mm X  5mm',204,274,1,0x00,0.0000,0.0000,0x00,0x01,'','admin','2022-08-24 21:46:06','testuser','2026-02-15 14:07:59',0x00,2010),(1075,'IF2022082439','螺丝刀【蓝色，一字型】','蓝色，一字型，3.2x75mm',202,277,1,0x00,0.0000,0.0000,0x00,0x01,'','admin','2022-08-24 21:52:46','admin','2026-02-15 14:07:59',0x00,2010),(1094,'IF20250312003','小包装盒',NULL,202,282,0,0x01,1000.0000,50000.0000,0x00,0x00,'','admin','2025-03-12 10:36:41','admin','2026-02-15 14:07:59',0x00,2010),(1100,'SF-BLACK-001','ABC','EFG',200,272,0,0x00,0.0000,0.0000,0x00,0x01,'','1','2026-03-10 11:14:37','1','2026-03-15 00:36:38',0x00,2010),(1230,'HZ-AC-001','废活性炭','HW49 900-039-49',200,282,0,0x00,0.0000,0.0000,0x00,0x00,'污染判定主链种子数据','seed','2026-09-20 17:05:55','seed','2026-09-20 17:05:55',0x00,2010),(1231,'HZ-OIL-001','废矿物油','HW08 900-249-08',200,282,0,0x00,0.0000,0.0000,0x00,0x00,'污染判定主链种子数据','seed','2026-09-20 17:05:55','seed','2026-09-20 17:05:55',0x00,2010),(1232,'HZ-BEN-001','含苯清洗废液','HW06 900-402-06',200,282,0,0x00,0.0000,0.0000,0x00,0x00,'污染判定主链种子数据','seed','2026-09-20 17:05:56','seed','2026-09-20 17:05:56',0x00,2010),(1233,'HZ-EMU-001','废乳化液','HW09 900-006-09',200,282,0,0x00,0.0000,0.0000,0x00,0x00,'污染判定主链种子数据','seed','2026-09-20 17:05:56','seed','2026-09-20 17:05:56',0x00,2010),(1234,'HZ-RAG-001','沾染油污的废抹布','HW49 900-041-49',200,282,0,0x00,0.0000,0.0000,0x00,0x00,'污染判定主链种子数据','seed','2026-09-20 17:05:56','seed','2026-09-20 17:05:56',0x00,2010),(1235,'HZ-DRM-001','废包装桶（含残液）','HW49 900-041-49',200,282,0,0x00,0.0000,0.0000,0x00,0x00,'污染判定主链种子数据','seed','2026-09-20 17:05:56','seed','2026-09-20 17:05:56',0x00,2010),(1236,'HZ-SLD-001','污水站沉淀污泥','HW17 336-064-17',200,282,0,0x00,0.0000,0.0000,0x00,0x00,'污染判定主链种子数据','seed','2026-09-20 17:05:56','seed','2026-09-20 17:05:56',0x00,2010),(1237,'HZ-COT-001','岩棉边角料','SW01 一般工业固废',200,282,0,0x00,0.0000,0.0000,0x00,0x00,'污染判定主链种子数据','seed','2026-09-20 17:05:56','seed','2026-09-20 17:05:56',0x00,2010),(1238,'HZ-PU-001','聚氨酯保温管边角料','SW01 一般工业固废',200,282,0,0x00,0.0000,0.0000,0x00,0x00,'污染判定主链种子数据','seed','2026-09-20 17:05:56','seed','2026-09-20 17:05:56',0x00,2010),(1239,'HZ-GLW-001','废玻璃棉','SW01 一般工业固废',200,282,0,0x00,0.0000,0.0000,0x00,0x00,'污染判定主链种子数据','seed','2026-09-20 17:05:57','seed','2026-09-20 17:05:57',0x00,2010);
/*!40000 ALTER TABLE `mes_md_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_md_item_batch_config`
--

DROP TABLE IF EXISTS `mes_md_item_batch_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_md_item_batch_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `item_id` bigint NOT NULL COMMENT '物料编号',
  `produce_date_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '批次属性-生产日期',
  `expire_date_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '批次属性-有效期',
  `receipt_date_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '批次属性-入库日期',
  `vendor_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '批次属性-供应商',
  `client_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '批次属性-客户',
  `sales_order_code_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '批次属性-销售订单编号',
  `purchase_order_code_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '批次属性-采购订单编号',
  `work_order_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '批次属性-生产工单',
  `task_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '批次属性-生产任务',
  `workstation_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '批次属性-工作站',
  `tool_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '批次属性-工具',
  `mold_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '批次属性-模具',
  `lot_number_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '批次属性-生产批号',
  `quality_status_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '批次属性-质量状态',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_item_id` (`item_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 物料批次属性配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_md_item_batch_config`
--

LOCK TABLES `mes_md_item_batch_config` WRITE;
/*!40000 ALTER TABLE `mes_md_item_batch_config` DISABLE KEYS */;
INSERT INTO `mes_md_item_batch_config` VALUES (1,96,0x01,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x01,0x01,0x01,0x00,0x01,'1','2026-02-16 00:47:44','1','2026-02-16 01:21:35',0x00,1),(2,95,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x01,'1','2026-02-16 00:47:59','1','2026-02-16 00:47:59',0x00,1),(3,100,0x00,0x01,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,'1','2026-03-13 23:48:32','1','2026-03-13 23:48:47',0x00,1),(4,102,0x00,0x00,0x01,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,'1','2026-03-15 00:36:24','1','2026-03-15 00:36:24',0x00,1),(5,103,0x00,0x00,0x00,0x00,0x01,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,'1','2026-03-15 10:15:58','1','2026-03-15 10:15:58',0x00,1),(6,75,0x01,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,'1','2026-03-21 06:49:58','1','2026-03-21 06:49:58',0x00,1),(7,104,0x01,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x01,'1','2026-03-28 10:02:57','1','2026-03-28 10:02:57',0x00,1),(8,106,0x01,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,'1','2026-05-21 00:42:36','1','2026-05-21 00:42:36',0x00,1);
/*!40000 ALTER TABLE `mes_md_item_batch_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_md_item_type`
--

DROP TABLE IF EXISTS `mes_md_item_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_md_item_type` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类名称',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父分类编号',
  `item_or_product` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料/产品标识',
  `sort` int NOT NULL DEFAULT '0' COMMENT '显示排序',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=284 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 物料产品分类表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_md_item_type`
--

LOCK TABLES `mes_md_item_type` WRITE;
/*!40000 ALTER TABLE `mes_md_item_type` DISABLE KEYS */;
INSERT INTO `mes_md_item_type` VALUES (200,'ITEM_TYPE_0000','物料产品分类',0,'PRODUCT',1,0,'','admin','2022-04-27 16:32:09','admin','2026-02-15 00:48:58',0x00,1),(272,'ITYP202604090005','原材料',200,'ITEM',1,0,'','admin','2022-08-24 21:33:18','1','2026-04-09 14:47:36',0x00,1),(273,'ITEM_TYPE_0088','产品',200,'PRODUCT',2,0,'','admin','2022-08-24 21:33:36','','2026-02-15 00:48:58',0x00,1),(274,'ITEM_TYPE_0089','五金类',272,'ITEM',1,0,'','admin','2022-08-24 21:42:41','','2026-02-15 00:48:58',0x00,1),(275,'ITEM_TYPE_0090','注塑类',272,'ITEM',2,0,'','admin','2022-08-24 21:42:52','','2026-02-15 00:48:58',0x00,1),(276,'ITEM_TYPE_0091','半成品',273,'PRODUCT',1,0,'','admin','2022-08-24 21:43:06','','2026-02-15 00:48:58',0x00,1),(277,'ITEM_TYPE_0092','产成品',273,'PRODUCT',2,0,'','admin','2022-08-24 21:43:16','','2026-02-15 00:48:58',0x00,1),(278,'ITEM_TYPE_0093','包装类',272,'ITEM',3,0,'','admin','2022-09-27 10:01:36','','2026-02-15 00:48:58',0x00,1),(282,'ITEM_TYPE_0097','辅材',272,'ITEM',3,0,'QQQ','admin','2025-03-12 10:34:43','1','2026-02-15 14:02:55',0x00,1),(283,'xx','yy',0,'ITEM',0,0,'','1','2026-02-15 14:02:45','1','2026-02-15 14:02:48',0x01,1);
/*!40000 ALTER TABLE `mes_md_item_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_md_product_bom`
--

DROP TABLE IF EXISTS `mes_md_product_bom`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_md_product_bom` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'BOM 编号',
  `item_id` bigint NOT NULL COMMENT '物料产品 ID（父产品）',
  `bom_item_id` bigint NOT NULL COMMENT 'BOM 物料 ID（子物料）',
  `quantity` decimal(12,4) NOT NULL DEFAULT '0.0000' COMMENT '物料使用比例',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 产品BOM表（物料清单）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_md_product_bom`
--

LOCK TABLES `mes_md_product_bom` WRITE;
/*!40000 ALTER TABLE `mes_md_product_bom` DISABLE KEYS */;
INSERT INTO `mes_md_product_bom` VALUES (1,96,95,1.0000,0,NULL,'1','2026-02-16 17:28:31','1','2026-02-16 17:28:31',0x00,1),(2,96,95,1.0000,0,NULL,'1','2026-02-16 17:31:07','1','2026-02-16 17:31:07',0x00,1),(3,96,94,1.0000,0,NULL,'1','2026-02-16 17:31:07','1','2026-02-16 17:31:07',0x00,1),(4,96,75,1.0000,0,NULL,'1','2026-02-16 17:39:20','1','2026-02-16 17:39:20',0x00,1),(5,96,95,1.0000,0,NULL,'1','2026-02-18 09:14:19','1','2026-02-18 09:14:19',0x00,1),(6,100,101,1.0000,0,NULL,'1','2026-03-15 00:35:33','1','2026-03-15 00:35:33',0x00,1),(7,100,102,1.0000,0,NULL,'1','2026-03-15 00:36:36','1','2026-03-15 00:36:36',0x00,1),(8,100,103,1.0000,0,NULL,'1','2026-03-15 10:12:36','1','2026-03-15 10:12:36',0x00,1),(9,103,73,1.0000,0,NULL,'1','2026-03-28 08:41:35','1','2026-03-28 08:41:35',0x00,1),(10,104,102,1.0000,0,'AAA','1','2026-03-28 10:24:21','1','2026-03-28 10:32:18',0x00,1),(11,104,101,1.0000,0,NULL,'1','2026-03-28 10:26:56','1','2026-03-28 10:26:56',0x00,1),(12,104,100,1.0000,0,NULL,'1','2026-03-28 10:26:56','1','2026-03-28 10:26:56',0x00,1),(13,104,95,1.0000,0,NULL,'1','2026-03-28 10:28:11','1','2026-03-28 10:28:11',0x00,1),(14,106,103,1.0000,0,NULL,'1','2026-03-28 10:38:01','1','2026-03-28 10:38:01',0x00,1),(15,106,103,1.0000,0,NULL,'1','2026-04-13 10:22:26','1','2026-04-13 10:22:26',0x00,1),(16,106,95,1.0000,0,NULL,'1','2026-05-21 00:56:51','1','2026-05-21 00:56:51',0x00,1);
/*!40000 ALTER TABLE `mes_md_product_bom` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_md_product_sip`
--

DROP TABLE IF EXISTS `mes_md_product_sip`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_md_product_sip` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'SIP 编号',
  `item_id` bigint NOT NULL COMMENT '物料产品 ID',
  `sort` int DEFAULT NULL COMMENT '排列顺序',
  `process_id` bigint DEFAULT NULL COMMENT '工序 ID',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标题',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '详细描述',
  `url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图片地址',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 产品标准检验程序表（SIP）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_md_product_sip`
--

LOCK TABLES `mes_md_product_sip` WRITE;
/*!40000 ALTER TABLE `mes_md_product_sip` DISABLE KEYS */;
INSERT INTO `mes_md_product_sip` VALUES (1,96,2,NULL,'11','333',NULL,NULL,'1','2026-02-16 12:30:26','1','2026-02-16 12:30:26',0x00,1),(2,106,0,1,'ABC',NULL,'http://test.txgy.xianoupeng.cn/20260328/108260013-1770021196430-gettyimages-2258948071-TFSPI_31012026-4160_1774665919537.jpeg',NULL,'1','2026-03-28 10:42:23','1','2026-08-21 12:27:30',0x01,1),(3,106,0,NULL,'SIP-Standard-01',NULL,'http://test.txgy.xianoupeng.cn/20260521/iShot_2026-05-11_19.33.09.png',NULL,'1','2026-04-13 10:14:34','1','2026-08-21 12:27:30',0x00,1),(4,103,0,NULL,'SIP-Standard-01',NULL,NULL,NULL,'1','2026-04-13 10:43:20','1','2026-04-13 10:43:20',0x00,1);
/*!40000 ALTER TABLE `mes_md_product_sip` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_md_product_sop`
--

DROP TABLE IF EXISTS `mes_md_product_sop`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_md_product_sop` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'SOP 编号',
  `item_id` bigint NOT NULL COMMENT '物料产品 ID',
  `sort` int DEFAULT NULL COMMENT '排列顺序',
  `process_id` bigint DEFAULT NULL COMMENT '工序 ID',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标题',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '详细描述',
  `url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图片地址',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 产品标准作业程序表（SOP）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_md_product_sop`
--

LOCK TABLES `mes_md_product_sop` WRITE;
/*!40000 ALTER TABLE `mes_md_product_sop` DISABLE KEYS */;
INSERT INTO `mes_md_product_sop` VALUES (1,96,2,NULL,'11','33',NULL,'222','1','2026-02-16 12:30:06','1','2026-02-16 12:30:06',0x00,1),(2,106,0,NULL,'SOP-Work-01',NULL,'http://test.txgy.xianoupeng.cn/20260521/iShot_2026-05-10_14.00.36.png',NULL,'1','2026-04-13 10:16:57','1','2026-08-21 12:27:30',0x00,1),(3,103,0,NULL,'SOP-Work-01',NULL,NULL,NULL,'1','2026-04-13 10:48:56','1','2026-04-13 10:48:56',0x00,1);
/*!40000 ALTER TABLE `mes_md_product_sop` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_md_unit_measure`
--

DROP TABLE IF EXISTS `mes_md_unit_measure`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_md_unit_measure` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '单位编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '单位编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '单位名称',
  `primary_flag` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否主单位',
  `primary_id` bigint DEFAULT NULL COMMENT '主单位编号',
  `change_rate` decimal(12,4) DEFAULT NULL COMMENT '与主单位换算比例',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=233 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 计量单位表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_md_unit_measure`
--

LOCK TABLES `mes_md_unit_measure` WRITE;
/*!40000 ALTER TABLE `mes_md_unit_measure` DISABLE KEYS */;
INSERT INTO `mes_md_unit_measure` VALUES (200,'KG','公斤',0x01,NULL,NULL,0,'','admin','2022-04-27 21:52:19','admin','2026-02-15 14:08:56',0x00,1),(201,'g','克',0x00,200,0.1000,0,'','admin','2022-04-27 21:53:29','admin','2026-02-15 14:08:56',0x00,1),(202,'PCS','个',0x01,NULL,NULL,0,'','admin','2022-04-27 21:54:13','','2026-02-15 14:08:56',0x00,1),(203,'CASE','箱',0x01,NULL,NULL,0,'','admin','2022-04-27 21:55:14','','2026-02-15 14:08:56',0x00,1),(204,'m','米',0x01,NULL,NULL,0,'','admin','2022-05-18 15:03:21','','2026-02-15 14:08:56',0x00,1),(205,'cm','厘米',0x00,204,100.0000,0,'','admin','2022-05-18 15:07:23','','2026-02-15 14:08:56',0x00,1),(206,'mm','毫米',0x00,204,1000.0000,0,'','admin','2022-05-18 15:07:42','','2026-02-15 14:08:56',0x00,1),(214,'T','吨',0x01,NULL,NULL,0,'','admin','2022-08-17 11:16:18','','2026-02-15 14:08:56',0x00,1),(216,'p','瓶',0x00,203,10.0000,0,'','admin','2022-08-18 14:11:57','admin','2026-02-15 14:08:56',0x00,1),(218,'pm','测试人员',0x01,NULL,NULL,0,'','admin','2022-08-19 14:24:41','','2026-02-15 14:08:56',0x00,1),(219,'Nm','公支',0x01,NULL,NULL,0,'','admin','2022-08-21 18:49:28','','2026-02-15 14:08:56',0x00,1),(220,'Ne','英支',0x01,NULL,NULL,0,'','admin','2022-08-21 18:49:55','','2026-02-15 14:08:56',0x00,1),(221,'匹','匹',0x01,NULL,NULL,0,'','admin','2022-08-21 18:59:57','','2026-02-15 14:08:56',0x00,1),(222,'捆','捆',0x01,NULL,NULL,0,'','admin','2022-08-21 19:05:50','','2026-02-15 14:08:56',0x00,1),(223,'mg','毫克',0x00,200,0.0010,0,'','admin','2022-09-27 10:17:16','','2026-02-15 14:08:56',0x00,1);
/*!40000 ALTER TABLE `mes_md_unit_measure` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_md_vendor`
--

DROP TABLE IF EXISTS `mes_md_vendor`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_md_vendor` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '供应商编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '供应商编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '供应商名称',
  `nickname` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商简称',
  `english_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商英文名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商简介',
  `logo` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商LOGO地址',
  `level` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商等级',
  `score` int DEFAULT NULL COMMENT '供应商评分',
  `address` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商地址',
  `website` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商官网地址',
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商邮箱地址',
  `telephone` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商电话',
  `contact1_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系人1',
  `contact1_telephone` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系人1-电话',
  `contact1_email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系人1-邮箱',
  `contact2_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系人2',
  `contact2_telephone` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系人2-电话',
  `contact2_email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系人2-邮箱',
  `credit_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '统一社会信用代码',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9200001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 供应商表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_md_vendor`
--

LOCK TABLES `mes_md_vendor` WRITE;
/*!40000 ALTER TABLE `mes_md_vendor` DISABLE KEYS */;
INSERT INTO `mes_md_vendor` VALUES (200,'V00101','深圳市海力德电子有限公司','海力德','HLD Electronics','专业从事电子元器件生产与销售','','A',95,'深圳市宝安区福永街道白石厦工业区','https://www.hld-elec.com','info@hld-elec.com','0755-12345678','王经理','13800138001','wang@hld-elec.com','赵助理','13800138002','zhao@hld-elec.com','91440300MA5EXAMPLE',0,'','1','2026-02-15 16:00:07','1','2026-02-15 16:00:07',0x00,1),(201,'V00102','东莞市精密五金制品有限公司','精密五金','JM Hardware',NULL,NULL,'B',80,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,NULL,'1','2026-02-15 16:00:07','1','2026-02-15 16:00:07',0x00,1),(202,'V00103','苏州工业材料科技有限公司','苏州工材','SZ Material Tech',NULL,NULL,'A',92,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,NULL,'1','2026-02-15 16:00:07','1','2026-02-15 16:00:07',0x00,1),(9200000,'V-HZ-001','北京华翰金属材料有限公司',NULL,NULL,NULL,NULL,'A',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,'仓库测试数据','80370','2026-09-10 09:00:00','80370','2026-09-10 09:00:00',0x00,2010);
/*!40000 ALTER TABLE `mes_md_vendor` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_md_workshop`
--

DROP TABLE IF EXISTS `mes_md_workshop`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_md_workshop` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '车间编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '车间编码',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '车间名称',
  `area` decimal(12,2) DEFAULT NULL COMMENT '面积',
  `charge_user_id` bigint DEFAULT NULL COMMENT '负责人用户 ID',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态（0开启 1关闭）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_code` (`code`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 车间表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_md_workshop`
--

LOCK TABLES `mes_md_workshop` WRITE;
/*!40000 ALTER TABLE `mes_md_workshop` DISABLE KEYS */;
INSERT INTO `mes_md_workshop` VALUES (1,'WS001','注塑车间',1200.00,1,0,'负责PVC注塑成型，生产螺丝刀刀柄','admin','2022-08-24 10:00:00','admin','2026-02-15 23:51:11',0x00,1),(2,'WS002','组装车间',800.00,1,0,'负责螺丝刀半成品组装和成品包装','admin','2022-08-24 10:00:00','admin','2026-02-15 23:51:11',0x00,1),(3,'WS003','五金车间',600.00,1,0,'负责钢筋裁切和刀头加工','admin','2022-08-24 10:00:00','admin','2026-02-15 23:51:11',0x00,1);
/*!40000 ALTER TABLE `mes_md_workshop` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_md_workstation`
--

DROP TABLE IF EXISTS `mes_md_workstation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_md_workstation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '工作站编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作站编码',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作站名称',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工作站地点',
  `workshop_id` bigint NOT NULL COMMENT '所在车间 ID',
  `process_id` bigint DEFAULT NULL COMMENT '工序 ID',
  `warehouse_id` bigint DEFAULT NULL COMMENT '线边库 ID',
  `location_id` bigint DEFAULT NULL COMMENT '库区 ID',
  `area_id` bigint DEFAULT NULL COMMENT '库位 ID',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态（0开启 1关闭）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_code` (`code`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 工作站表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_md_workstation`
--

LOCK TABLES `mes_md_workstation` WRITE;
/*!40000 ALTER TABLE `mes_md_workstation` DISABLE KEYS */;
INSERT INTO `mes_md_workstation` VALUES (1,'ST001','注塑工位A','注塑车间A区',1,NULL,NULL,NULL,NULL,0,'蓝色刀柄注塑','admin','2022-08-24 10:00:00','admin','2026-02-15 23:51:42',0x00,1),(2,'ST002','注塑工位B','注塑车间B区',1,NULL,NULL,NULL,NULL,0,'黑色刀柄注塑','admin','2022-08-24 10:00:00','admin','2026-02-15 23:51:42',0x00,1),(3,'ST003','组装工位A','组装车间A区',2,NULL,NULL,NULL,NULL,0,'刀头+刀柄组装','admin','2022-08-24 10:00:00','admin','2026-02-15 23:51:42',0x00,1),(4,'ST004','包装工位','组装车间B区',2,NULL,NULL,NULL,NULL,0,'成品包装','admin','2022-08-24 10:00:00','admin','2026-02-15 23:51:42',0x00,1),(5,'ST005','裁切工位','五金车间A区',3,NULL,NULL,NULL,NULL,0,'钢筋裁切','admin','2022-08-24 10:00:00','admin','2026-02-15 23:51:42',0x00,1),(6,'ST006','刀头加工工位','五金车间B区',3,1,703,714,725,0,'刀头成型加工','admin','2022-08-24 10:00:00','1','2026-03-28 17:36:44',0x00,1),(7,'ST_ACCEPT_FB_P1','验收报工-下料工位','验收造数',3,1,NULL,NULL,NULL,0,'验收造数：生产报工非质检工序','1','2026-05-26 00:33:47','1','2026-05-26 00:33:47',0x00,1),(8,'ST_ACCEPT_FB_P4','验收报工-质检工位','验收造数',2,4,NULL,NULL,NULL,0,'验收造数：生产报工质检工序','1','2026-05-26 00:33:47','1','2026-05-26 00:33:47',0x00,1);
/*!40000 ALTER TABLE `mes_md_workstation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_md_workstation_machine`
--

DROP TABLE IF EXISTS `mes_md_workstation_machine`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_md_workstation_machine` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `workstation_id` bigint NOT NULL COMMENT '工作站 ID',
  `machinery_id` bigint NOT NULL COMMENT '设备 ID',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '数量',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 工作站-设备资源表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_md_workstation_machine`
--

LOCK TABLES `mes_md_workstation_machine` WRITE;
/*!40000 ALTER TABLE `mes_md_workstation_machine` DISABLE KEYS */;
INSERT INTO `mes_md_workstation_machine` VALUES (1,1,1,2,'注塑机2台','admin','2022-08-24 10:00:00','admin','2026-02-15 23:51:19',0x00,1),(2,2,2,1,'注塑机1台','admin','2022-08-24 10:00:00','admin','2026-02-15 23:51:19',0x00,1),(3,5,3,1,'裁切机1台','admin','2022-08-24 10:00:00','admin','2026-02-15 23:51:19',0x00,1),(4,6,4,1,'冲压机1台','admin','2022-08-24 10:00:00','admin','2026-02-15 23:51:19',0x00,1),(5,6,8,1,NULL,'1','2026-03-28 18:06:28','1','2026-03-28 18:06:30',0x01,1);
/*!40000 ALTER TABLE `mes_md_workstation_machine` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_md_workstation_tool`
--

DROP TABLE IF EXISTS `mes_md_workstation_tool`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_md_workstation_tool` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `workstation_id` bigint NOT NULL COMMENT '工作站 ID',
  `tool_type_id` bigint NOT NULL COMMENT '工具类型 ID',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '数量',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 工作站-工装夹具资源表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_md_workstation_tool`
--

LOCK TABLES `mes_md_workstation_tool` WRITE;
/*!40000 ALTER TABLE `mes_md_workstation_tool` DISABLE KEYS */;
INSERT INTO `mes_md_workstation_tool` VALUES (1,3,1,5,'螺丝刀专用组装夹具5套','admin','2022-08-24 10:00:00','admin','2026-02-15 23:51:24',0x00,1),(2,3,2,3,'电动扭力工具3把','admin','2022-08-24 10:00:00','admin','2026-02-15 23:51:24',0x00,1),(3,5,3,10,'裁切刀片10片','admin','2022-08-24 10:00:00','admin','2026-02-15 23:51:24',0x00,1),(4,6,1,2,'3','1','2026-02-16 15:21:21','1','2026-02-16 15:22:09',0x00,1),(5,6,205,1,NULL,'1','2026-03-28 18:06:35','1','2026-03-28 18:06:35',0x00,1);
/*!40000 ALTER TABLE `mes_md_workstation_tool` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_md_workstation_worker`
--

DROP TABLE IF EXISTS `mes_md_workstation_worker`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_md_workstation_worker` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `workstation_id` bigint NOT NULL COMMENT '工作站 ID',
  `post_id` bigint NOT NULL COMMENT '岗位 ID',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '数量',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 工作站-人力资源表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_md_workstation_worker`
--

LOCK TABLES `mes_md_workstation_worker` WRITE;
/*!40000 ALTER TABLE `mes_md_workstation_worker` DISABLE KEYS */;
INSERT INTO `mes_md_workstation_worker` VALUES (1,1,1,2,'注塑操作员2人','admin','2022-08-24 10:00:00','admin','2026-02-15 23:51:30',0x00,1),(2,3,1,3,'组装操作员3人','admin','2022-08-24 10:00:00','admin','2026-02-15 23:51:30',0x00,1),(3,3,2,1,'质检员1人','admin','2022-08-24 10:00:00','admin','2026-02-15 23:51:30',0x00,1),(4,4,1,2,'包装操作员2人','admin','2022-08-24 10:00:00','admin','2026-02-15 23:51:30',0x00,1),(5,5,1,1,'裁切操作员1人','admin','2022-08-24 10:00:00','admin','2026-02-15 23:51:30',0x00,1),(6,6,2,1,'3','1','2026-02-16 15:28:01','1','2026-02-16 15:28:01',0x00,1);
/*!40000 ALTER TABLE `mes_md_workstation_worker` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_pollution_check_log`
--

DROP TABLE IF EXISTS `mes_pollution_check_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_pollution_check_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `check_id` bigint NOT NULL COMMENT '判定记录ID(mes_set_pollution_check.id)',
  `record_no` varchar(64) DEFAULT NULL COMMENT '判定记录编号(PC-...)',
  `op_type` varchar(32) NOT NULL COMMENT '操作类型：CREATE(创建+AI初筛)/UPDATE(改单+AI重筛)/REVIEW(人工复核终态)',
  `item_name` varchar(200) DEFAULT NULL COMMENT '当时的物料/产品名称',
  `field_signs` varchar(500) DEFAULT NULL COMMENT '当时现场观察到的污染特征快照',
  `ai_result` varchar(32) DEFAULT NULL COMMENT '当时 AI 初筛结果：CLEAN/POLLUTED/UNCERTAIN',
  `ai_confidence` int DEFAULT NULL COMMENT '当时 AI 置信度(%)',
  `ai_reason` varchar(500) DEFAULT NULL COMMENT '当时 AI 判定依据',
  `ai_basis` varchar(500) DEFAULT NULL COMMENT '当时 AI 判定的法规依据',
  `suggested_storage` varchar(500) DEFAULT NULL COMMENT '当时 AI 推荐存储方法',
  `review_result` varchar(32) DEFAULT NULL COMMENT '人工复核结果(仅REVIEW行)：CLEAN/POLLUTED',
  `review_basis` varchar(1000) DEFAULT NULL COMMENT '人工复核的判定依据(仅 REVIEW 行)',
  `storage_method` varchar(200) DEFAULT NULL COMMENT '最终存储方法',
  `disposition` varchar(32) DEFAULT NULL COMMENT '处置方式',
  `location` varchar(200) DEFAULT NULL COMMENT '去向/库位',
  `marked` bit(1) DEFAULT NULL COMMENT '是否标记',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注/复核备注',
  `op_by` varchar(64) DEFAULT NULL COMMENT '操作人',
  `op_time` datetime DEFAULT NULL COMMENT '操作时间',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_check_id` (`check_id`),
  KEY `idx_record_no` (`record_no`)
) ENGINE=InnoDB AUTO_INCREMENT=1189 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全环保检测-污染判定履历(操作+AI/人工快照)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_pollution_check_log`
--

LOCK TABLES `mes_pollution_check_log` WRITE;
/*!40000 ALTER TABLE `mes_pollution_check_log` DISABLE KEYS */;
INSERT INTO `mes_pollution_check_log` VALUES (1148,1681,'PC-20260920170557281','CREATE','废活性炭',NULL,'UNCERTAIN',69,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'刘洋','2026-09-20 17:05:58','80370','2026-09-20 17:05:58','80370','2026-09-20 17:05:58',0x00,2010),(1149,1681,'PC-20260920170557281','REVIEW','废活性炭',NULL,'UNCERTAIN',69,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','POLLUTED','《国家危险废物名录（2025年版）》：判断是否属危险废物及其废物类别（HW 类别）。','隔离待检库位暂存，待人工复核/送检后确定存储','ISOLATE_STORAGE','危废暂存间A-01',0x01,'更换活性炭吸附箱产生，吸附有机物后属危险废物，已委外处置','刘洋','2026-09-20 17:05:58','80370','2026-09-20 17:05:58','80370','2026-09-20 17:05:58',0x00,2010),(1150,1682,'PC-20260920170558098','CREATE','废矿物油',NULL,'UNCERTAIN',60,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'刘洋','2026-09-20 17:05:58','80370','2026-09-20 17:05:58','80370','2026-09-20 17:05:58',0x00,2010),(1151,1682,'PC-20260920170558098','REVIEW','废矿物油',NULL,'UNCERTAIN',60,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','POLLUTED','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十九条：产生危险废物的单位应当按照国家有关规定和环境保护标准要求贮存、利用、处置危险废物，不得擅自倾倒、堆放。','隔离待检库位暂存，待人工复核/送检后确定存储','ISOLATE_STORAGE','危废暂存间A-02',0x01,'设备换油产生，含重金属杂质，转有资质单位再生利用中','刘洋','2026-09-20 17:05:58','80370','2026-09-20 17:05:58','80370','2026-09-20 17:05:58',0x00,2010),(1152,1683,'PC-20260920170558334','CREATE','含苯清洗废液',NULL,'POLLUTED',95,'命中污染特征词「苯」，需按污染受控处置','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十九条（按环境保护标准要求贮存、处置，不得擅自倾倒堆放）、第八十一条（按危险废物特性分类贮存，禁止与性质不相容废物混放）；疑似属《国家危险废物名录（2025年版）》管控，具体废物类别需按名录比对核实','污染/危废受控存储：密封防渗容器、专用污染管控库位、贴污染标识并登记暂存台账，避免混放',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'刘洋','2026-09-20 17:05:58','80370','2026-09-20 17:05:58','80370','2026-09-20 17:05:58',0x00,2010),(1153,1683,'PC-20260920170558334','REVIEW','含苯清洗废液',NULL,'POLLUTED',95,'命中污染特征词「苯」，需按污染受控处置','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十九条（按环境保护标准要求贮存、处置，不得擅自倾倒堆放）、第八十一条（按危险废物特性分类贮存，禁止与性质不相容废物混放）；疑似属《国家危险废物名录（2025年版）》管控，具体废物类别需按名录比对核实','污染/危废受控存储：密封防渗容器、专用污染管控库位、贴污染标识并登记暂存台账，避免混放','POLLUTED','《国家危险废物名录（2025年版）》：判断是否属危险废物及其废物类别（HW 类别）。','污染/危废受控存储：密封防渗容器、专用污染管控库位、贴污染标识并登记暂存台账，避免混放','ISOLATE_STORAGE','危废暂存间C-03',0x01,'喷涂线清洗产生，含苯系物，危废暂存间C-03 隔离暂存','刘洋','2026-09-20 17:05:58','80370','2026-09-20 17:05:58','80370','2026-09-20 17:05:58',0x00,2010),(1154,1684,'PC-20260920170558076','CREATE','废乳化液',NULL,'UNCERTAIN',68,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'刘洋','2026-09-20 17:05:59','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:59',0x00,2010),(1155,1684,'PC-20260920170558076','REVIEW','废乳化液',NULL,'UNCERTAIN',68,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','POLLUTED','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十九条：产生危险废物的单位应当按照国家有关规定和环境保护标准要求贮存、利用、处置危险废物，不得擅自倾倒、堆放。','隔离待检库位暂存，待人工复核/送检后确定存储','ISOLATE_STORAGE','危废暂存间A-01',0x01,'机加工冷却液更换产生，破乳后回收基础油','刘洋','2026-09-20 17:05:59','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:59',0x00,2010),(1156,1685,'PC-20260920170558310','CREATE','沾染油污的废抹布',NULL,'UNCERTAIN',61,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'刘洋','2026-09-20 17:05:59','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:59',0x00,2010),(1157,1685,'PC-20260920170558310','REVIEW','沾染油污的废抹布',NULL,'UNCERTAIN',61,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','POLLUTED','《国家危险废物名录（2025年版）》：判断是否属危险废物及其废物类别（HW 类别）。','隔离待检库位暂存，待人工复核/送检后确定存储','ISOLATE_STORAGE','危废暂存间A-02',0x01,'设备检修擦拭产生，沾染矿物油，已按危废焚烧处置','刘洋','2026-09-20 17:05:59','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:59',0x00,2010),(1158,1686,'PC-20260920170559576','CREATE','废包装桶（含残液）',NULL,'UNCERTAIN',57,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'刘洋','2026-09-20 17:05:59','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:59',0x00,2010),(1159,1686,'PC-20260920170559576','REVIEW','废包装桶（含残液）',NULL,'UNCERTAIN',57,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','POLLUTED','《国家危险废物名录（2025年版）》：判断是否属危险废物及其废物类别（HW 类别）。','隔离待检库位暂存，待人工复核/送检后确定存储','ISOLATE_STORAGE','危废暂存间C-03',0x01,'原料桶倒空后残留，属危废，暂存待委外','刘洋','2026-09-20 17:05:59','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:59',0x00,2010),(1160,1687,'PC-20260920170559613','CREATE','污水站沉淀污泥',NULL,'UNCERTAIN',58,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'刘洋','2026-09-20 17:05:59','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:59',0x00,2010),(1161,1687,'PC-20260920170559613','REVIEW','污水站沉淀污泥',NULL,'UNCERTAIN',58,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','POLLUTED','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十九条：产生危险废物的单位应当按照国家有关规定和环境保护标准要求贮存、利用、处置危险废物，不得擅自倾倒、堆放。','隔离待检库位暂存，待人工复核/送检后确定存储','ISOLATE_STORAGE','危废暂存间A-01',0x01,'污水站物化沉淀产生，含重金属，脱水后委外处置','刘洋','2026-09-20 17:05:59','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(1162,1688,'PC-20260920170559501','CREATE','岩棉边角料',NULL,'UNCERTAIN',75,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'刘洋','2026-09-20 17:06:00','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(1163,1688,'PC-20260920170559501','REVIEW','岩棉边角料',NULL,'UNCERTAIN',75,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','CLEAN',NULL,'普通仓储','REUSE','原料A-01',0x00,'岩棉切割边角料，无污染，回收作保温填充料','刘洋','2026-09-20 17:06:00','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(1164,1689,'PC-20260920170559935','CREATE','聚氨酯保温管边角料',NULL,'UNCERTAIN',72,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'刘洋','2026-09-20 17:06:00','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(1165,1689,'PC-20260920170559935','REVIEW','聚氨酯保温管边角料',NULL,'UNCERTAIN',72,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','CLEAN',NULL,'普通仓储','REUSE','原料A-01',0x00,'聚氨酯发泡边角料，无污染，破碎回用','刘洋','2026-09-20 17:06:00','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(1166,1690,'PC-20260920170600727','CREATE','废玻璃棉',NULL,'CLEAN',85,'命中安全特征词「玻璃」，未检出污染特征','《中华人民共和国固体废物污染环境防治法》（2020年修订）第二十条（采取防扬散、防流失、防渗漏措施）、第三十六条（建立工业固体废物全过程责任制度与管理台账）','普通仓储（常温、通风、防潮常规存放）',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'刘洋','2026-09-20 17:06:00','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(1167,1691,'PC-20260920170600858','CREATE','小包装盒',NULL,'UNCERTAIN',75,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'刘洋','2026-09-20 17:06:00','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(1168,1691,'PC-20260920170600858','REVIEW','小包装盒',NULL,'UNCERTAIN',75,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','POLLUTED',NULL,'隔离待检库位暂存，待人工复核/送检后确定存储','REJECT_ISSUE','危废暂存间A-02',0x01,'领用抽检检出含铅油墨残留，拒绝领用转受控存储','刘洋','2026-09-20 17:06:00','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(1169,1692,'PC-20260920170600823','CREATE','钢筋',NULL,'UNCERTAIN',57,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'刘洋','2026-09-20 17:06:01','80370','2026-09-20 17:06:01','80370','2026-09-20 17:06:01',0x00,2010),(1170,1692,'PC-20260920170600823','REVIEW','钢筋',NULL,'UNCERTAIN',57,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','POLLUTED',NULL,'隔离待检库位暂存，待人工复核/送检后确定存储','REJECT_ISSUE','危废暂存间A-01',0x01,'表面锈蚀超限，按受控流程隔离，待除锈复检','刘洋','2026-09-20 17:06:01','80370','2026-09-20 17:06:01','80370','2026-09-20 17:06:01',0x00,2010),(1171,1693,'PC-20260920170600387','CREATE','钢筋',NULL,'UNCERTAIN',57,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'刘洋','2026-09-20 17:06:01','80370','2026-09-20 17:06:01','80370','2026-09-20 17:06:01',0x00,2010),(1172,1693,'PC-20260920170600387','REVIEW','钢筋',NULL,'UNCERTAIN',57,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','CLEAN',NULL,'普通仓储','NORMAL_INBOUND','原料A-01',0x00,'除锈后复检合格，判无污染，解除同批隔离并放行','刘洋','2026-09-20 17:06:01','80370','2026-09-20 17:06:01','80370','2026-09-20 17:06:01',0x00,2010),(1173,1694,'PC-20260920170601619','CREATE','螺丝刀【蓝色，一字型】',NULL,'UNCERTAIN',65,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'刘洋','2026-09-20 17:06:01','80370','2026-09-20 17:06:01','80370','2026-09-20 17:06:01',0x00,2010),(1174,1695,'PC-20260920170601418','CREATE','螺丝刀【蓝色，一字型】',NULL,'UNCERTAIN',58,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'刘洋','2026-09-20 17:06:01','80370','2026-09-20 17:06:01','80370','2026-09-20 17:06:01',0x00,2010),(1175,1694,'PC-20260920170601619','REVIEW','螺丝刀【蓝色，一字型】',NULL,'UNCERTAIN',65,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','CLEAN',NULL,'普通仓储','NORMAL_INBOUND','原料A-01',0x00,'出厂检测无污染，达标入库','刘洋','2026-09-20 17:06:02','80370','2026-09-20 17:06:02','80370','2026-09-20 17:06:02',0x00,2010),(1176,1695,'PC-20260920170601418','REVIEW','螺丝刀【蓝色，一字型】',NULL,'UNCERTAIN',58,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','CLEAN',NULL,'普通仓储','NORMAL_INBOUND','原料A-01',0x00,'在库抽检无污染；该批已无待检行，解冻放行','刘洋','2026-09-20 17:06:02','80370','2026-09-20 17:06:02','80370','2026-09-20 17:06:02',0x00,2010),(1177,1696,'PC-20260920170601449','CREATE','ABC',NULL,'UNCERTAIN',61,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'刘洋','2026-09-20 17:06:02','80370','2026-09-20 17:06:02','80370','2026-09-20 17:06:02',0x00,2010),(1178,1696,'PC-20260920170601449','REVIEW','ABC',NULL,'UNCERTAIN',61,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','POLLUTED',NULL,'隔离待检库位暂存，待人工复核/送检后确定存储','CONTROLLED_STORAGE','危废暂存间C-03',0x01,'整批性能不达标，整体报废并受控存储，批次锁定不出库','刘洋','2026-09-20 17:06:02','80370','2026-09-20 17:06:02','80370','2026-09-20 17:06:02',0x00,2010),(1179,1697,'PC-20260920170602587','CREATE','小包装盒',NULL,'UNCERTAIN',56,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'刘洋','2026-09-20 17:06:02','80370','2026-09-20 17:06:02','80370','2026-09-20 17:06:02',0x00,2010),(1180,1697,'PC-20260920170602587','REVIEW','小包装盒',NULL,'UNCERTAIN',56,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','CLEAN',NULL,'普通仓储','NORMAL_INBOUND','原料A-01',0x00,'到货抽检无污染，正常入库','刘洋','2026-09-20 17:06:02','80370','2026-09-20 17:06:02','80370','2026-09-20 17:06:02',0x00,2010),(1181,1698,'PC-20260920170602957','CREATE','小包装盒',NULL,'UNCERTAIN',72,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'刘洋','2026-09-20 17:06:03','80370','2026-09-20 17:06:03','80370','2026-09-20 17:06:03',0x00,2010),(1182,1698,'PC-20260920170602957','REVIEW','小包装盒',NULL,'UNCERTAIN',72,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','POLLUTED',NULL,'隔离待检库位暂存，待人工复核/送检后确定存储','CONTROLLED_STORAGE','危废暂存间A-02',0x01,'包装破损渗漏，按受控入库隔离暂存','刘洋','2026-09-20 17:06:03','80370','2026-09-20 17:06:03','80370','2026-09-20 17:06:03',0x00,2010),(1183,1699,'PC-20260920170602477','CREATE','小包装盒',NULL,'UNCERTAIN',64,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'刘洋','2026-09-20 17:06:03','80370','2026-09-20 17:06:03','80370','2026-09-20 17:06:03',0x00,2010),(1184,1699,'PC-20260920170602477','REVIEW','小包装盒',NULL,'UNCERTAIN',64,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','CLEAN',NULL,'普通仓储','NORMAL_INBOUND','原料A-01',0x00,'在库复检无异常','刘洋','2026-09-20 17:06:03','80370','2026-09-20 17:06:03','80370','2026-09-20 17:06:03',0x00,2010),(1185,1700,'PC-20260920170603894','CREATE','钢筋',NULL,'UNCERTAIN',66,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'刘洋','2026-09-20 17:06:03','80370','2026-09-20 17:06:03','80370','2026-09-20 17:06:03',0x00,2010),(1186,1701,'PC-20260920170605415','AMEND','岩棉切割边角料',NULL,'UNCERTAIN',60,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'刘洋','2026-09-20 17:06:05','80370','2026-09-20 17:06:05','80370','2026-09-20 17:06:05',0x00,2010),(1187,1688,'PC-20260920170559501','SUPERSEDE','岩棉边角料',NULL,'UNCERTAIN',75,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','CLEAN',NULL,'普通仓储','REUSE','原料A-01',0x00,'岩棉切割边角料，无污染，回收作保温填充料','刘洋','2026-09-20 17:06:05','80370','2026-09-20 17:06:05','80370','2026-09-20 17:06:05',0x00,2010),(1188,1701,'PC-20260920170605415','REVIEW','岩棉切割边角料',NULL,'UNCERTAIN',60,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','POLLUTED','《国家危险废物名录（2025年版）》：判断是否属危险废物及其废物类别（HW 类别）。','隔离待检库位暂存，待人工复核/送检后确定存储','ISOLATE_STORAGE','危废暂存间C-03',0x01,'按石棉指标改判有污染，转危废暂存间C-03 隔离暂存','刘洋','2026-09-20 17:06:06','80370','2026-09-20 17:06:06','80370','2026-09-20 17:06:06',0x00,2010);
/*!40000 ALTER TABLE `mes_pollution_check_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_pollution_ledger`
--

DROP TABLE IF EXISTS `mes_pollution_ledger`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_pollution_ledger` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `source_check_id` bigint DEFAULT NULL COMMENT '来源判定记录ID(mes_set_pollution_check.id)；产废登记无判定记录，留空',
  `source_record_no` varchar(64) NOT NULL COMMENT '来源判定记录编号(PC-...)',
  `source_type` varchar(32) DEFAULT NULL COMMENT '来源类型：POLLUTION_CHECK(判定复核登记)/WASTE_REGISTER(产废登记)',
  `stage` varchar(32) DEFAULT NULL COMMENT '环节',
  `biz_no` varchar(64) DEFAULT NULL COMMENT '关联单号',
  `batch_no` varchar(64) DEFAULT NULL COMMENT '批次号',
  `item_code` varchar(64) DEFAULT NULL COMMENT '物料/产品编码',
  `item_name` varchar(200) DEFAULT NULL COMMENT '物料/产品名称',
  `item_spec` varchar(200) DEFAULT NULL COMMENT '规格',
  `weight` decimal(12,3) DEFAULT NULL COMMENT '重量(kg)',
  `unit_name` varchar(32) DEFAULT NULL COMMENT '重量单位快照(由源判定行带入)',
  `disposition` varchar(32) DEFAULT NULL COMMENT '处置方式',
  `storage_method` varchar(200) DEFAULT NULL COMMENT '最终存储方法',
  `location` varchar(200) DEFAULT NULL COMMENT '去向/库位',
  `location_id` bigint DEFAULT NULL COMMENT '受控库位编号（mes_wm_warehouse_area.id；location 为其名称快照）',
  `marked` bit(1) DEFAULT NULL COMMENT '是否标记',
  `status` varchar(32) NOT NULL DEFAULT 'STORED' COMMENT '台账状态：STORED(暂存)/PROCESSING(处置中)/REUSED(已回用)/DISCHARGED(已排放)/DISPOSED(已处置)/CLEARED(已解除)',
  `status_by` varchar(64) DEFAULT NULL COMMENT '最近流转人',
  `status_time` datetime DEFAULT NULL COMMENT '最近流转时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ledger_source_check` (`source_check_id`),
  KEY `idx_source_check_id` (`source_check_id`),
  KEY `idx_batch_no` (`batch_no`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=1298 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全环保检测-污染/危废暂存台账';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_pollution_ledger`
--

LOCK TABLES `mes_pollution_ledger` WRITE;
/*!40000 ALTER TABLE `mes_pollution_ledger` DISABLE KEYS */;
INSERT INTO `mes_pollution_ledger` VALUES (1283,1681,'PC-20260920170557281','POLLUTION_CHECK','WASTE_INTERMEDIATE',NULL,NULL,'HZ-AC-001','废活性炭','HW49 900-039-49',12.500,'KG','ISOLATE_STORAGE','隔离待检库位暂存，待人工复核/送检后确定存储','危废暂存间A-01',728,0x01,'DISPOSED','刘洋','2026-09-20 17:06:04','委托有资质单位焚烧处置，危废转移联单已归档','80370','2026-09-20 17:05:58','80370','2026-09-20 17:05:58',0x00,2010),(1284,1682,'PC-20260920170558098','POLLUTION_CHECK','WASTE_INTERMEDIATE',NULL,NULL,'HZ-OIL-001','废矿物油','HW08 900-249-08',85.000,'KG','ISOLATE_STORAGE','隔离待检库位暂存，待人工复核/送检后确定存储','危废暂存间A-02',729,0x01,'PROCESSING','刘洋','2026-09-20 17:06:04','送再生装置蒸馏提纯中','80370','2026-09-20 17:05:58','80370','2026-09-20 17:05:58',0x00,2010),(1285,1683,'PC-20260920170558334','POLLUTION_CHECK','WASTE_INTERMEDIATE',NULL,NULL,'HZ-BEN-001','含苯清洗废液','HW06 900-402-06',40.000,'KG','ISOLATE_STORAGE','污染/危废受控存储：密封防渗容器、专用污染管控库位、贴污染标识并登记暂存台账，避免混放','危废暂存间C-03',730,0x01,'STORED','刘洋','2026-09-20 17:05:58','喷涂线清洗产生，含苯系物，危废暂存间C-03 隔离暂存','80370','2026-09-20 17:05:58','80370','2026-09-20 17:05:58',0x00,2010),(1286,1684,'PC-20260920170558076','POLLUTION_CHECK','WASTE_INTERMEDIATE',NULL,NULL,'HZ-EMU-001','废乳化液','HW09 900-006-09',60.000,'KG','ISOLATE_STORAGE','隔离待检库位暂存，待人工复核/送检后确定存储','危废暂存间A-01',728,0x01,'REUSED','刘洋','2026-09-20 17:06:04','破乳回收基础油，回用于设备润滑','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:59',0x00,2010),(1287,1685,'PC-20260920170558310','POLLUTION_CHECK','WASTE_INTERMEDIATE',NULL,NULL,'HZ-RAG-001','沾染油污的废抹布','HW49 900-041-49',8.000,'KG','ISOLATE_STORAGE','隔离待检库位暂存，待人工复核/送检后确定存储','危废暂存间A-02',729,0x01,'DISPOSED','刘洋','2026-09-20 17:06:05','随批次一并焚烧处置','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:59',0x00,2010),(1288,1686,'PC-20260920170559576','POLLUTION_CHECK','WASTE_INTERMEDIATE',NULL,NULL,'HZ-DRM-001','废包装桶（含残液）','HW49 900-041-49',15.000,'KG','ISOLATE_STORAGE','隔离待检库位暂存，待人工复核/送检后确定存储','危废暂存间C-03',730,0x01,'STORED','刘洋','2026-09-20 17:05:59','原料桶倒空后残留，属危废，暂存待委外','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:59',0x00,2010),(1289,1687,'PC-20260920170559613','POLLUTION_CHECK','WASTE_INTERMEDIATE',NULL,NULL,'HZ-SLD-001','污水站沉淀污泥','HW17 336-064-17',120.000,'KG','ISOLATE_STORAGE','隔离待检库位暂存，待人工复核/送检后确定存储','危废暂存间A-01',728,0x01,'PROCESSING','刘洋','2026-09-20 17:06:05','脱水减量后委外处置中','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(1290,1691,'PC-20260920170600858','POLLUTION_CHECK','MATERIAL_ISSUE',NULL,'BATCH_ITEM_94','IF20250312003','小包装盒',NULL,300.000,'个','REJECT_ISSUE','隔离待检库位暂存，待人工复核/送检后确定存储','危废暂存间A-02',729,0x01,'STORED','刘洋','2026-09-20 17:06:00','领用抽检检出含铅油墨残留，拒绝领用转受控存储','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(1291,1692,'PC-20260920170600823','POLLUTION_CHECK','MATERIAL_ISSUE',NULL,'BATCH_ITEM_72','IF2022082404','钢筋','100mm X  5mm',500.000,'米','REJECT_ISSUE','隔离待检库位暂存，待人工复核/送检后确定存储','危废暂存间A-01',728,0x01,'CLEARED','刘洋','2026-09-20 17:06:01','表面锈蚀超限，按受控流程隔离，待除锈复检；批次复核无污染，自动解除(源判定: PC-20260920170600387)','80370','2026-09-20 17:06:01','80370','2026-09-20 17:06:01',0x00,2010),(1292,1696,'PC-20260920170601449','POLLUTION_CHECK','FINISHED_PRODUCT',NULL,'PC202600003','SF-BLACK-001','ABC','EFG',120.000,'KG','CONTROLLED_STORAGE','隔离待检库位暂存，待人工复核/送检后确定存储','危废暂存间C-03',730,0x01,'STORED','刘洋','2026-09-20 17:06:02','整批性能不达标，整体报废并受控存储，批次锁定不出库','80370','2026-09-20 17:06:02','80370','2026-09-20 17:06:02',0x00,2010),(1293,1698,'PC-20260920170602957','POLLUTION_CHECK','PURCHASE_INBOUND','IR-HZ-20260520001','BATCH_ITEM_94','IF20250312003','小包装盒',NULL,500.000,'个','CONTROLLED_STORAGE','隔离待检库位暂存，待人工复核/送检后确定存储','危废暂存间A-02',729,0x01,'STORED','刘洋','2026-09-20 17:06:03','包装破损渗漏，按受控入库隔离暂存','80370','2026-09-20 17:06:03','80370','2026-09-20 17:06:03',0x00,2010),(1294,NULL,'WO-CARBON-202609-001','WASTE_REGISTER','WASTE_INTERMEDIATE',NULL,NULL,'HZ-AC-001','废活性炭','HW49 900-039-49',25.000,'KG','MARKED_STORAGE',NULL,'危废暂存间A-01',NULL,0x01,'STORED','刘洋','2026-09-20 17:06:03','2# 吸附箱换炭作业产废','80370','2026-09-20 17:06:03','80370','2026-09-20 17:06:03',0x00,2010),(1295,NULL,'WO-OIL-202609-002','WASTE_REGISTER','WASTE_INTERMEDIATE',NULL,NULL,'HZ-OIL-001','废矿物油','HW08 900-249-08',60.000,'KG','MARKED_STORAGE',NULL,'危废暂存间A-02',NULL,0x01,'STORED','刘洋','2026-09-20 17:06:03','空压机换油作业产废','80370','2026-09-20 17:06:03','80370','2026-09-20 17:06:03',0x00,2010),(1296,NULL,'WO-CLEAN-202609-003','WASTE_REGISTER','WASTE_INTERMEDIATE',NULL,NULL,'HZ-DRM-001','废包装桶（含残液）','HW49 900-041-49',12.000,'KG','MARKED_STORAGE',NULL,'危废暂存间A-03',NULL,0x01,'STORED','刘洋','2026-09-20 17:06:03','车间清洁作业产生空桶','80370','2026-09-20 17:06:03','80370','2026-09-20 17:06:03',0x00,2010),(1297,1701,'PC-20260920170605415','POLLUTION_CHECK','WASTE_INTERMEDIATE',NULL,NULL,'HZ-COT-001','岩棉切割边角料','SW01 一般工业固废',46.000,'KG','ISOLATE_STORAGE','隔离待检库位暂存，待人工复核/送检后确定存储','危废暂存间C-03',730,0x01,'STORED','刘洋','2026-09-20 17:06:06','按石棉指标改判有污染，转危废暂存间C-03 隔离暂存','80370','2026-09-20 17:06:06','80370','2026-09-20 17:06:06',0x00,2010);
/*!40000 ALTER TABLE `mes_pollution_ledger` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_pollution_ledger_log`
--

DROP TABLE IF EXISTS `mes_pollution_ledger_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_pollution_ledger_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `ledger_id` bigint NOT NULL COMMENT '台账ID(mes_pollution_ledger.id)',
  `source_record_no` varchar(64) DEFAULT NULL COMMENT '来源判定记录编号(PC-...)',
  `from_status` varchar(32) DEFAULT NULL COMMENT '原状态(空=初始登记)',
  `to_status` varchar(32) NOT NULL COMMENT '新状态：STORED/PROCESSING/REUSED/DISCHARGED/DISPOSED/CLEARED',
  `operator` varchar(64) DEFAULT NULL COMMENT '操作人(自动流转填复核人)',
  `op_time` datetime DEFAULT NULL COMMENT '流转时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '流转说明/备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_ledger_id` (`ledger_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1359 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全环保检测-污染/危废暂存台账-流转历史';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_pollution_ledger_log`
--

LOCK TABLES `mes_pollution_ledger_log` WRITE;
/*!40000 ALTER TABLE `mes_pollution_ledger_log` DISABLE KEYS */;
INSERT INTO `mes_pollution_ledger_log` VALUES (1338,1283,'PC-20260920170557281',NULL,'STORED','刘洋','2026-09-20 17:05:58','复核判为有污染，自动登记暂存台账','80370','2026-09-20 17:05:58','80370','2026-09-20 17:05:58',0x00,2010),(1339,1284,'PC-20260920170558098',NULL,'STORED','刘洋','2026-09-20 17:05:58','复核判为有污染，自动登记暂存台账','80370','2026-09-20 17:05:58','80370','2026-09-20 17:05:58',0x00,2010),(1340,1285,'PC-20260920170558334',NULL,'STORED','刘洋','2026-09-20 17:05:58','复核判为有污染，自动登记暂存台账','80370','2026-09-20 17:05:58','80370','2026-09-20 17:05:58',0x00,2010),(1341,1286,'PC-20260920170558076',NULL,'STORED','刘洋','2026-09-20 17:05:59','复核判为有污染，自动登记暂存台账','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:59',0x00,2010),(1342,1287,'PC-20260920170558310',NULL,'STORED','刘洋','2026-09-20 17:05:59','复核判为有污染，自动登记暂存台账','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:59',0x00,2010),(1343,1288,'PC-20260920170559576',NULL,'STORED','刘洋','2026-09-20 17:05:59','复核判为有污染，自动登记暂存台账','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:59',0x00,2010),(1344,1289,'PC-20260920170559613',NULL,'STORED','刘洋','2026-09-20 17:05:59','复核判为有污染，自动登记暂存台账','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(1345,1290,'PC-20260920170600858',NULL,'STORED','刘洋','2026-09-20 17:06:00','复核判为有污染，自动登记暂存台账','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(1346,1291,'PC-20260920170600823',NULL,'STORED','刘洋','2026-09-20 17:06:01','复核判为有污染，自动登记暂存台账','80370','2026-09-20 17:06:01','80370','2026-09-20 17:06:01',0x00,2010),(1347,1291,'PC-20260920170600823','STORED','CLEARED','刘洋','2026-09-20 17:06:01','批次复核无污染，自动解除(源判定: PC-20260920170600387)','80370','2026-09-20 17:06:01','80370','2026-09-20 17:06:01',0x00,2010),(1348,1292,'PC-20260920170601449',NULL,'STORED','刘洋','2026-09-20 17:06:02','复核判为有污染，自动登记暂存台账','80370','2026-09-20 17:06:02','80370','2026-09-20 17:06:02',0x00,2010),(1349,1293,'PC-20260920170602957',NULL,'STORED','刘洋','2026-09-20 17:06:03','复核判为有污染，自动登记暂存台账','80370','2026-09-20 17:06:03','80370','2026-09-20 17:06:03',0x00,2010),(1350,1294,'WO-CARBON-202609-001',NULL,'STORED','刘洋','2026-09-20 17:06:03','产废称重登记入暂存台账','80370','2026-09-20 17:06:03','80370','2026-09-20 17:06:03',0x00,2010),(1351,1295,'WO-OIL-202609-002',NULL,'STORED','刘洋','2026-09-20 17:06:03','产废称重登记入暂存台账','80370','2026-09-20 17:06:03','80370','2026-09-20 17:06:03',0x00,2010),(1352,1296,'WO-CLEAN-202609-003',NULL,'STORED','刘洋','2026-09-20 17:06:03','产废称重登记入暂存台账','80370','2026-09-20 17:06:03','80370','2026-09-20 17:06:03',0x00,2010),(1353,1283,'PC-20260920170557281','STORED','DISPOSED','刘洋','2026-09-20 17:06:04','委托有资质单位焚烧处置，危废转移联单已归档','80370','2026-09-20 17:06:04','80370','2026-09-20 17:06:04',0x00,2010),(1354,1284,'PC-20260920170558098','STORED','PROCESSING','刘洋','2026-09-20 17:06:04','送再生装置蒸馏提纯中','80370','2026-09-20 17:06:04','80370','2026-09-20 17:06:04',0x00,2010),(1355,1286,'PC-20260920170558076','STORED','REUSED','刘洋','2026-09-20 17:06:04','破乳回收基础油，回用于设备润滑','80370','2026-09-20 17:06:04','80370','2026-09-20 17:06:04',0x00,2010),(1356,1287,'PC-20260920170558310','STORED','DISPOSED','刘洋','2026-09-20 17:06:05','随批次一并焚烧处置','80370','2026-09-20 17:06:05','80370','2026-09-20 17:06:05',0x00,2010),(1357,1289,'PC-20260920170559613','STORED','PROCESSING','刘洋','2026-09-20 17:06:05','脱水减量后委外处置中','80370','2026-09-20 17:06:05','80370','2026-09-20 17:06:05',0x00,2010),(1358,1297,'PC-20260920170605415',NULL,'STORED','刘洋','2026-09-20 17:06:06','复核判为有污染，自动登记暂存台账','80370','2026-09-20 17:06:06','80370','2026-09-20 17:06:06',0x00,2010);
/*!40000 ALTER TABLE `mes_pollution_ledger_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_pro_andon_config`
--

DROP TABLE IF EXISTS `mes_pro_andon_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_pro_andon_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '呼叫原因',
  `level` tinyint NOT NULL DEFAULT '3' COMMENT '级别',
  `handler_role_id` bigint DEFAULT NULL COMMENT '处置人角色编号',
  `handler_user_id` bigint DEFAULT NULL COMMENT '处置人编号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 安灯呼叫配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_pro_andon_config`
--

LOCK TABLES `mes_pro_andon_config` WRITE;
/*!40000 ALTER TABLE `mes_pro_andon_config` DISABLE KEYS */;
INSERT INTO `mes_pro_andon_config` VALUES (1,'设备故障，需要维修',1,3,103,'','1','2026-02-21 00:08:46','1','2026-05-25 22:58:46',0x00,1),(2,'物料不足，需要补料',2,NULL,1,'','1','2026-02-21 00:08:46','1','2026-05-25 23:18:47',0x00,1),(3,'质量异常，需要确认',2,2,100,'','1','2026-02-21 00:08:46','1','2026-05-25 23:18:51',0x00,1),(4,'人员不足，需要支援',3,NULL,107,'','1','2026-02-21 00:08:46','1','2026-02-21 00:08:46',0x00,1),(5,'工艺问题，需要技术支持',1,2,NULL,'','1','2026-02-21 00:08:46','1','2026-02-21 00:08:46',0x00,1);
/*!40000 ALTER TABLE `mes_pro_andon_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_pro_andon_record`
--

DROP TABLE IF EXISTS `mes_pro_andon_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_pro_andon_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `config_id` bigint NOT NULL COMMENT '安灯配置编号',
  `workstation_id` bigint NOT NULL COMMENT '工作站编号',
  `user_id` bigint NOT NULL COMMENT '发起用户编号',
  `work_order_id` bigint DEFAULT NULL COMMENT '生产工单编号',
  `process_id` bigint DEFAULT NULL COMMENT '工序编号',
  `reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '呼叫原因',
  `level` tinyint NOT NULL DEFAULT '3' COMMENT '级别',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '处置状态',
  `handle_time` datetime DEFAULT NULL COMMENT '处置时间',
  `handler_user_id` bigint DEFAULT NULL COMMENT '处置人编号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 安灯呼叫记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_pro_andon_record`
--

LOCK TABLES `mes_pro_andon_record` WRITE;
/*!40000 ALTER TABLE `mes_pro_andon_record` DISABLE KEYS */;
INSERT INTO `mes_pro_andon_record` VALUES (1,0,1,100,1,1,'设备故障，需要维修',1,1,'2025-03-15 10:30:00',107,'已更换电机','1','2026-02-21 00:08:46','1','2026-02-21 00:08:46',0x00,1),(2,0,2,101,1,2,'物料不足，需要补料',2,1,'1970-01-01 08:00:00',1,'','1','2026-02-21 00:08:46','1','2026-03-17 20:12:53',0x00,1),(3,0,1,100,2,1,'质量异常，需要确认',2,1,'2025-03-16 14:00:00',107,'已调整工艺参数','1','2026-02-21 00:08:46','1','2026-02-21 00:08:46',0x00,1),(4,4,3,1,NULL,NULL,'人员不足，需要支援',3,1,'1970-01-01 08:00:00',1,'333','1','2026-02-21 09:23:41','1','2026-02-21 09:25:55',0x00,1),(5,3,1,1,NULL,NULL,'质量异常，需要确认',2,1,'1970-01-01 08:00:00',115,'','1','2026-03-17 21:03:05','1','2026-03-17 21:03:27',0x00,1),(6,2,1,1,2,2,'物料不足，需要补料',2,1,'1970-01-01 08:00:00',1,'','1','2026-03-17 21:03:39','1','2026-04-04 10:29:58',0x00,1),(7,2,1,1,16,2,'物料不足，需要补料',2,0,'2026-05-25 22:40:45',1,'','1','2026-04-04 10:30:17','1','2026-05-25 22:41:03',0x00,1);
/*!40000 ALTER TABLE `mes_pro_andon_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_pro_card`
--

DROP TABLE IF EXISTS `mes_pro_card`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_pro_card` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流转卡编码',
  `work_order_id` bigint NOT NULL COMMENT '生产工单编号',
  `item_id` bigint NOT NULL COMMENT '产品物料编号',
  `batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `transfered_quantity` decimal(14,2) NOT NULL DEFAULT '0.00' COMMENT '流转数量',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 生产流转卡';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_pro_card`
--

LOCK TABLES `mes_pro_card` WRITE;
/*!40000 ALTER TABLE `mes_pro_card` DISABLE KEYS */;
INSERT INTO `mes_pro_card` VALUES (1,'CARD202503150001',1,75,'B20250315001',5000.00,1,'','1','2026-02-21 03:16:43','1','2026-02-21 03:16:43',0x00,1),(2,'CARD202503160001',1,75,'B20250316001',3000.00,0,'第二批次流转','1','2026-02-21 03:16:43','1','2026-02-21 03:16:43',0x00,1),(3,'CARD202503200001',4,75,NULL,10000.00,1,'','1','2026-02-21 03:16:43','1','2026-02-21 03:16:43',0x00,1),(4,'CARD20260404000001',16,69,'AABB',1.00,2,'','1','2026-04-04 20:30:36','1','2026-04-04 20:35:29',0x00,1);
/*!40000 ALTER TABLE `mes_pro_card` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_pro_card_process`
--

DROP TABLE IF EXISTS `mes_pro_card_process`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_pro_card_process` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `card_id` bigint NOT NULL COMMENT '流转卡编号',
  `sort` int DEFAULT NULL COMMENT '序号',
  `process_id` bigint DEFAULT NULL COMMENT '工序编号',
  `input_time` datetime DEFAULT NULL COMMENT '进入工序时间',
  `output_time` datetime DEFAULT NULL COMMENT '出工序时间',
  `input_quantity` decimal(14,2) DEFAULT NULL COMMENT '投入数量',
  `output_quantity` decimal(14,2) DEFAULT NULL COMMENT '产出数量',
  `unqualified_quantity` decimal(14,2) DEFAULT NULL COMMENT '不合格品数量',
  `workstation_id` bigint DEFAULT NULL COMMENT '工位编号',
  `user_id` bigint DEFAULT NULL COMMENT '操作人编号',
  `ipqc_id` bigint DEFAULT NULL COMMENT '过程检验单编号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_card_id` (`card_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 流转卡工序记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_pro_card_process`
--

LOCK TABLES `mes_pro_card_process` WRITE;
/*!40000 ALTER TABLE `mes_pro_card_process` DISABLE KEYS */;
INSERT INTO `mes_pro_card_process` VALUES (1,1,1,1,'2025-03-15 08:00:00','2025-03-15 16:00:00',5000.00,4980.00,20.00,1,1,NULL,'','1','2026-02-21 03:16:43','1','2026-02-21 03:16:43',0x00,1),(2,1,2,2,'2025-03-16 08:00:00','2025-03-16 16:00:00',4980.00,4960.00,20.00,2,1,NULL,'','1','2026-02-21 03:16:43','1','2026-02-21 03:16:43',0x00,1),(3,1,3,3,'2025-03-17 08:00:00',NULL,4960.00,NULL,NULL,3,1,NULL,'组装进行中','1','2026-02-21 03:16:43','1','2026-02-21 03:16:43',0x00,1),(4,3,1,1,'2025-03-20 08:00:00','2025-03-20 18:00:00',10000.00,9950.00,50.00,1,1,NULL,'','1','2026-02-21 03:16:43','1','2026-02-21 03:16:43',0x00,1),(5,3,2,2,NULL,NULL,NULL,NULL,NULL,2,NULL,NULL,'','1','2026-02-21 03:16:43','1','2026-02-21 03:16:43',0x00,1),(6,4,1,1,'2026-04-06 00:00:00','2026-04-15 00:00:00',2.00,1.00,1.00,1,100,NULL,'aaab','1','2026-04-04 20:30:59','1','2026-04-04 20:30:59',0x00,1);
/*!40000 ALTER TABLE `mes_pro_card_process` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_pro_feedback`
--

DROP TABLE IF EXISTS `mes_pro_feedback`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_pro_feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '报工单编号',
  `type` tinyint NOT NULL COMMENT '报工类型',
  `channel` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '报工途径',
  `feedback_time` datetime DEFAULT NULL COMMENT '报工时间',
  `workstation_id` bigint NOT NULL COMMENT '工作站编号',
  `route_id` bigint NOT NULL COMMENT '工艺路线编号',
  `process_id` bigint NOT NULL COMMENT '工序编号',
  `work_order_id` bigint NOT NULL COMMENT '生产工单编号',
  `task_id` bigint NOT NULL COMMENT '生产任务编号',
  `item_id` bigint NOT NULL COMMENT '产品物料编号',
  `expire_date` datetime DEFAULT NULL COMMENT '过期日期',
  `lot_number` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '生产批号',
  `scheduled_quantity` decimal(14,2) NOT NULL DEFAULT '0.00' COMMENT '排产数量',
  `feedback_quantity` decimal(14,2) NOT NULL DEFAULT '0.00' COMMENT '本次报工数量',
  `qualified_quantity` decimal(14,2) NOT NULL DEFAULT '0.00' COMMENT '合格品数量',
  `unqualified_quantity` decimal(14,2) NOT NULL DEFAULT '0.00' COMMENT '不良品数量',
  `uncheck_quantity` decimal(14,2) NOT NULL DEFAULT '0.00' COMMENT '待检测数量',
  `labor_scrap_quantity` decimal(14,2) NOT NULL DEFAULT '0.00' COMMENT '工废数量',
  `material_scrap_quantity` decimal(14,2) NOT NULL DEFAULT '0.00' COMMENT '料废数量',
  `other_scrap_quantity` decimal(14,2) NOT NULL DEFAULT '0.00' COMMENT '其他废品数量',
  `feedback_user_id` bigint DEFAULT NULL COMMENT '报工用户编号',
  `approve_user_id` bigint DEFAULT NULL COMMENT '审核用户编号',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 生产报工';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_pro_feedback`
--

LOCK TABLES `mes_pro_feedback` WRITE;
/*!40000 ALTER TABLE `mes_pro_feedback` DISABLE KEYS */;
INSERT INTO `mes_pro_feedback` VALUES (1,'FB202503160001',1,'PC','2025-03-16 10:30:00',1,1,1,1,1,75,NULL,NULL,5000.00,510.00,500.00,10.00,0.00,6.00,4.00,0.00,1,1,4,'','1','2026-02-21 00:50:39','1','2026-03-19 00:48:09',0x00,1),(2,'FB202503170001',1,'PC','2026-02-21 12:29:27',1,1,1,1,1,75,NULL,NULL,5000.00,820.00,800.00,20.00,0.00,12.00,5.00,3.00,1,1,4,'第二批次报工','1','2026-02-21 00:50:39','1','2026-03-19 00:48:09',0x00,1),(3,'FB202503180001',2,'APP','2025-03-18 09:00:00',1,1,1,1,1,75,NULL,NULL,5000.00,200.00,0.00,0.00,200.00,0.00,0.00,0.00,1,1,3,'统一报工，待质检','1','2026-02-21 00:50:39','1','2026-03-19 00:48:09',0x00,1),(4,'FB20260318233037414',1,NULL,'2026-03-19 08:35:25',3,1,3,1,4,75,NULL,NULL,0.00,1.00,1.00,0.00,0.00,0.00,0.00,0.00,1,NULL,2,'','1','2026-03-18 23:30:59','1','2026-03-19 00:48:09',0x00,1),(5,'FB20260319083538497',1,NULL,'2026-03-19 08:39:03',3,1,3,1,4,75,NULL,NULL,0.00,1.00,1.00,0.00,0.00,0.00,0.00,0.00,1,1,4,'','1','2026-03-19 08:36:03','1','2026-03-21 15:07:46',0x00,1),(6,'FB20260319083926305',1,NULL,'2026-03-19 13:01:06',3,1,3,1,4,75,NULL,NULL,0.00,1.00,0.00,1.00,0.00,0.00,0.00,0.00,1,1,4,'','1','2026-03-19 08:39:40','1','2026-03-21 15:09:48',0x00,1),(7,'FB20260319085214000',1,NULL,'2026-03-19 08:52:59',3,1,3,1,4,75,NULL,NULL,0.00,1.00,0.00,1.00,0.00,0.00,0.00,0.00,1,1,4,'','1','2026-03-19 08:52:28','1','2026-03-19 23:02:57',0x00,1),(8,'FB20260324231635515',2,NULL,'2026-03-24 23:17:21',3,1,3,1,4,75,NULL,NULL,0.00,2.00,1.00,1.00,0.00,1.00,0.00,0.00,1,1,4,'','1','2026-03-24 23:17:15','1','2026-03-24 23:17:24',0x00,1),(9,'FB20260324231843582',2,NULL,'2026-03-24 23:19:14',1,1,1,1,1,75,NULL,NULL,0.00,3.00,2.00,1.00,0.00,0.00,0.00,0.00,1,1,2,'321','1','2026-03-24 23:19:09','1','2026-04-15 09:03:44',0x00,1),(10,'FB_ACCEPT_PREPARE',1,'PC','2026-05-26 00:33:47',7,1,1,17,7,75,'2026-11-22 00:00:00','LOT-FB-PREPARE',100.00,10.00,8.00,2.00,0.00,1.00,1.00,0.00,100,1,0,'验收造数：草稿，可测试编辑/提交','1','2026-05-26 00:33:47','1','2026-05-26 00:33:47',0x00,1),(11,'FB_ACCEPT_APPROVING',1,'PC','2026-05-25 23:33:47',7,1,1,17,7,75,'2026-11-22 00:00:00','LOT-FB-APPROVING',100.00,12.00,11.00,1.00,0.00,1.00,0.00,0.00,100,1,2,'验收造数：审批中，可测试审批/驳回','1','2026-05-26 00:33:47','1','2026-05-26 00:33:47',0x00,1),(12,'FB_ACCEPT_UNCHECK',2,'PC','2026-05-25 22:33:47',8,1,4,17,8,75,'2026-11-22 00:00:00','LOT-FB-UNCHECK',100.00,15.00,0.00,0.00,15.00,0.00,0.00,0.00,100,1,3,'验收造数：待检验，可测试只读和明细 Tab','1','2026-05-26 00:33:47','1','2026-05-26 00:33:47',0x00,1),(13,'FB_ACCEPT_FINISHED',2,'PC','2026-05-25 21:33:47',7,1,1,17,7,75,'2026-11-22 00:00:00','LOT-FB-FINISHED',100.00,20.00,18.00,2.00,0.00,1.00,1.00,0.00,100,1,4,'验收造数：已完成，可测试消耗/产出明细','1','2026-05-26 00:33:47','1','2026-05-26 00:33:47',0x00,1);
/*!40000 ALTER TABLE `mes_pro_feedback` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_pro_process`
--

DROP TABLE IF EXISTS `mes_pro_process`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_pro_process` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工序编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工序名称',
  `attention` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工艺要求',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 生产工序';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_pro_process`
--

LOCK TABLES `mes_pro_process` WRITE;
/*!40000 ALTER TABLE `mes_pro_process` DISABLE KEYS */;
INSERT INTO `mes_pro_process` VALUES (1,'PROCESS001','下料工序','按照图纸尺寸进行切割，注意材料方向',0,'金属板材下料','1','2026-02-17 11:39:58','1','2026-02-17 11:39:58',0x00,1),(2,'PROCESS002','折弯工序','折弯角度需精确到 ±1°，避免回弹',0,'钣金折弯成型','1','2026-02-17 11:39:58','1','2026-02-17 11:39:58',0x00,1),(3,'PROCESS003','焊接工序','焊接电流 180A，氩气流量 12L/min',0,'氩弧焊接','1','2026-02-17 11:39:58','1','2026-02-17 11:39:58',0x00,1),(4,'PROCESS004','打磨工序','打磨至 Ra3.2，无毛刺',0,'表面处理','1','2026-02-17 11:39:58','1','2026-02-17 11:39:58',0x00,1),(5,'PROCESS005','喷涂工序','底漆厚度 30μm，面漆厚度 50μm',0,'表面喷涂','1','2026-02-17 11:39:58','1','2026-02-18 15:54:14',0x00,1),(6,'PROC000010','测试 123','测试 123',0,'','1','2026-03-15 19:44:01','1','2026-05-25 09:02:54',0x00,1);
/*!40000 ALTER TABLE `mes_pro_process` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_pro_process_content`
--

DROP TABLE IF EXISTS `mes_pro_process_content`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_pro_process_content` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `process_id` bigint NOT NULL COMMENT '工序编号',
  `sort` int NOT NULL DEFAULT '0' COMMENT '顺序编号',
  `content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '步骤说明',
  `device` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '辅助设备',
  `material` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '辅助材料',
  `doc_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '材料文档 URL',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_process_id` (`process_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 生产工序内容';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_pro_process_content`
--

LOCK TABLES `mes_pro_process_content` WRITE;
/*!40000 ALTER TABLE `mes_pro_process_content` DISABLE KEYS */;
INSERT INTO `mes_pro_process_content` VALUES (1,3,1,'检查焊接设备是否正常','氩弧焊机','无',NULL,'','1','2026-02-17 11:39:58','1','2026-02-17 11:39:58',0x00,1),(2,3,2,'清洁焊接部位，去除油污','角磨机','丙酮',NULL,'','1','2026-02-17 11:39:58','1','2026-02-17 11:39:58',0x00,1),(3,3,3,'点焊定位','氩弧焊机','焊丝 ER308',NULL,'','1','2026-02-17 11:39:58','1','2026-02-17 11:39:58',0x00,1),(4,3,4,'正式焊接，控制焊接速度','氩弧焊机','焊丝 ER308',NULL,'','1','2026-02-17 11:39:58','1','2026-02-17 11:39:58',0x00,1),(5,3,5,'焊后检查，清除焊渣','锤子','钢丝刷',NULL,'','1','2026-02-17 11:39:58','1','2026-02-17 11:39:58',0x00,1),(6,5,1,'你好','1','2','3','4','1','2026-02-18 15:54:08','1','2026-02-18 15:54:08',0x00,1),(7,6,1,'组装','','','','','1','2026-03-15 19:44:14','1','2026-03-15 19:44:14',0x00,1),(8,6,2,'质检','','','','','1','2026-03-15 19:44:23','1','2026-03-15 19:44:23',0x00,1),(9,6,3,'包装','','','','','1','2026-03-15 19:44:28','1','2026-03-15 19:44:28',0x00,1),(10,6,4,'怎么说？？？',NULL,NULL,NULL,'呃呃呃','1','2026-05-25 09:02:37','1','2026-05-25 09:02:45',0x00,1),(11,6,5,'呃呃呃',NULL,NULL,NULL,'','1','2026-05-25 15:43:46','1','2026-05-25 15:43:46',0x00,1);
/*!40000 ALTER TABLE `mes_pro_process_content` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_pro_route`
--

DROP TABLE IF EXISTS `mes_pro_route`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_pro_route` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工艺路线编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工艺路线名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工艺路线说明',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 工艺路线表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_pro_route`
--

LOCK TABLES `mes_pro_route` WRITE;
/*!40000 ALTER TABLE `mes_pro_route` DISABLE KEYS */;
INSERT INTO `mes_pro_route` VALUES (1,'ROUTE20250301001','螺丝刀生产工艺','一字型蓝色螺丝刀的完整生产工艺路线，包含下料、折弯、焊接、打磨、喷涂五道工序',1,'','1','2026-02-19 09:23:15','1','2026-02-19 18:27:09',0x00,1),(2,'ROUTE20250301002','刀柄注塑工艺','PVC注塑成型刀柄的生产工艺路线',0,'','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(3,'ROUTE20250301003','刀头加工工艺','螺丝刀刀头的钢筋加工工艺路线',0,'','1','2026-02-19 09:23:15','1','2026-02-19 18:26:04',0x00,1),(4,'ROUTE20250301004','新产品试制工艺','新产品试制阶段的临时工艺路线，待完善后启用',1,'待补充工序和产品','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(5,'ROUTE20250301005','成品包装工艺','螺丝刀成品包装工艺路线，单道工序完成质检和包装',0,'','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(6,'ROUTEyyXnJu4W','测试流程','',0,'','1','2026-03-15 19:44:39','1','2026-03-15 20:14:41',0x00,1);
/*!40000 ALTER TABLE `mes_pro_route` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_pro_route_process`
--

DROP TABLE IF EXISTS `mes_pro_route_process`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_pro_route_process` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `route_id` bigint NOT NULL COMMENT '工艺路线编号',
  `process_id` bigint NOT NULL COMMENT '工序编号',
  `sort` int NOT NULL DEFAULT '1' COMMENT '序号',
  `next_process_id` bigint DEFAULT NULL COMMENT '下一道工序编号',
  `link_type` tinyint NOT NULL DEFAULT '0' COMMENT '与下一道工序关系',
  `prepare_time` int DEFAULT '0' COMMENT '准备时间（分钟）',
  `wait_time` int DEFAULT '0' COMMENT '等待时间（分钟）',
  `color_code` char(7) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '#00AEF3' COMMENT '甘特图显示颜色',
  `key_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否关键工序',
  `check_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否质检工序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 工艺路线工序表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_pro_route_process`
--

LOCK TABLES `mes_pro_route_process` WRITE;
/*!40000 ALTER TABLE `mes_pro_route_process` DISABLE KEYS */;
INSERT INTO `mes_pro_route_process` VALUES (1,1,1,1,2,3,10,5,'#409EFF',0x00,0x00,'按图纸切割钢筋','1','2026-02-19 09:23:15','1','2026-02-19 18:27:55',0x00,1),(2,1,2,2,3,3,5,5,'#67C23A',0x00,0x00,'折弯成刀头形状','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(3,1,3,3,4,3,15,30,'#E6A23C',0x01,0x00,'焊接刀头与刀柄','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(4,1,4,4,5,3,5,10,'#F56C6C',0x00,0x01,'打磨焊接处和毛刺','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(5,1,5,5,NULL,3,10,60,'#909399',0x00,0x00,'表面喷涂保护漆','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(6,2,1,1,3,3,5,5,'#409EFF',0x00,0x00,'称量 PVC 颗粒和色粉','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(7,2,3,2,5,3,20,15,'#E6A23C',0x01,0x00,'注塑加热成型','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(8,2,5,3,NULL,3,5,30,'#909399',0x00,0x01,'表面光泽检查及喷涂','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(9,3,1,1,2,3,10,5,'#409EFF',0x00,0x00,'按尺寸切割钢筋','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(10,3,2,2,4,3,5,5,'#67C23A',0x01,0x00,'折弯成一字型刀头','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(11,3,4,3,NULL,3,5,10,'#F56C6C',0x00,0x01,'去毛刺精整','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(12,5,4,1,NULL,3,5,5,'#F56C6C',0x01,0x01,'成品外观检查及包装','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(13,6,6,1,5,0,0,0,'#00AEF3',0x01,0x00,'','1','2026-03-15 19:45:21','1','2026-03-15 19:45:45',0x00,1),(14,6,2,2,NULL,2,0,0,'#00AEF3',0x00,0x00,'','1','2026-03-15 19:45:30','1','2026-03-15 19:48:06',0x01,1),(15,6,5,2,3,3,0,0,'#00AEF3',0x00,0x00,'','1','2026-03-15 20:14:07','1','2026-03-15 20:14:07',0x00,1),(16,6,3,3,NULL,3,0,0,'#00AEF3',0x00,0x00,'','1','2026-03-15 20:14:37','1','2026-03-15 20:14:37',0x00,1);
/*!40000 ALTER TABLE `mes_pro_route_process` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_pro_route_product`
--

DROP TABLE IF EXISTS `mes_pro_route_product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_pro_route_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `route_id` bigint NOT NULL COMMENT '工艺路线编号',
  `item_id` bigint NOT NULL COMMENT '产品物料编号',
  `quantity` int DEFAULT '1' COMMENT '生产数量',
  `production_time` decimal(12,2) DEFAULT '1.00' COMMENT '生产用时',
  `time_unit_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'MINUTE' COMMENT '时间单位',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 工艺路线产品表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_pro_route_product`
--

LOCK TABLES `mes_pro_route_product` WRITE;
/*!40000 ALTER TABLE `mes_pro_route_product` DISABLE KEYS */;
INSERT INTO `mes_pro_route_product` VALUES (1,1,75,100,480.00,'MINUTE','每批 100 个螺丝刀','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(2,2,73,500,2.00,'HOUR','每批 500 个蓝色刀柄','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(3,2,96,500,2.00,'HOUR','每批 500 个黑色刀柄','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(4,3,74,1000,3.00,'HOUR','每批 1000 个刀头','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(5,6,100,1,1.00,'MINUTE','','1','2026-03-15 19:45:08','1','2026-03-15 19:55:00',0x00,1),(6,6,101,1,1.00,'MINUTE','','1','2026-03-15 19:46:19','1','2026-03-15 19:54:33',0x01,1),(7,6,102,1,1.00,'MINUTE','','1','2026-03-15 19:46:25','1','2026-03-15 19:54:35',0x01,1),(8,6,103,1,1.00,'MINUTE','','1','2026-03-15 19:46:29','1','2026-03-15 19:54:37',0x01,1);
/*!40000 ALTER TABLE `mes_pro_route_product` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_pro_route_product_bom`
--

DROP TABLE IF EXISTS `mes_pro_route_product_bom`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_pro_route_product_bom` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `route_id` bigint NOT NULL COMMENT '工艺路线编号',
  `process_id` bigint NOT NULL COMMENT '工序编号',
  `product_id` bigint NOT NULL COMMENT '产品物料编号',
  `item_id` bigint NOT NULL COMMENT 'BOM 物料编号',
  `quantity` decimal(12,2) DEFAULT '1.00' COMMENT '用料比例',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 工艺路线产品 BOM 表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_pro_route_product_bom`
--

LOCK TABLES `mes_pro_route_product_bom` WRITE;
/*!40000 ALTER TABLE `mes_pro_route_product_bom` DISABLE KEYS */;
INSERT INTO `mes_pro_route_product_bom` VALUES (1,1,1,75,72,15.00,'每个螺丝刀需钢筋 15cm','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(2,1,3,75,72,0.50,'焊接补料','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(3,1,5,75,71,5.00,'蓝色面漆色粉 5g/个','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(4,1,5,75,70,2.00,'PVC底漆 2g/个','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(5,2,1,73,70,20.00,'PVC颗粒 20g/个','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(6,2,1,73,71,2.00,'蓝色色粉 2g/个','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(7,2,5,73,71,1.00,'表面喷涂蓝色色粉 1g/个','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(8,2,1,96,70,20.00,'PVC颗粒 20g/个','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(9,2,1,96,69,2.00,'黑色色粉 2g/个','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(10,2,5,96,69,1.00,'表面喷涂黑色色粉 1g/个','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(11,3,1,74,72,8.00,'钢筋 8cm/个','1','2026-02-19 09:23:15','1','2026-02-19 09:23:15',0x00,1),(12,6,6,100,101,1.00,'','1','2026-03-15 19:54:43','1','2026-03-15 19:54:43',0x00,1),(13,6,6,100,102,1.00,'','1','2026-03-15 19:54:49','1','2026-03-15 19:54:49',0x00,1),(14,6,6,100,103,2.00,'','1','2026-03-15 19:54:59','1','2026-03-15 19:54:59',0x00,1);
/*!40000 ALTER TABLE `mes_pro_route_product_bom` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_pro_task`
--

DROP TABLE IF EXISTS `mes_pro_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_pro_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务名称',
  `work_order_id` bigint NOT NULL COMMENT '生产工单编号',
  `workstation_id` bigint NOT NULL COMMENT '工作站编号',
  `route_id` bigint NOT NULL COMMENT '工艺路线编号',
  `process_id` bigint NOT NULL COMMENT '工序编号',
  `item_id` bigint NOT NULL COMMENT '产品物料编号',
  `quantity` decimal(14,2) NOT NULL DEFAULT '0.00' COMMENT '排产数量',
  `produced_quantity` decimal(14,2) NOT NULL DEFAULT '0.00' COMMENT '已生产数量',
  `qualify_quantity` decimal(14,2) NOT NULL DEFAULT '0.00' COMMENT '合格品数量',
  `unqualify_quantity` decimal(14,2) NOT NULL DEFAULT '0.00' COMMENT '不良品数量',
  `changed_quantity` decimal(14,2) NOT NULL DEFAULT '0.00' COMMENT '调整数量',
  `client_id` bigint DEFAULT NULL COMMENT '客户编号',
  `start_time` datetime DEFAULT NULL COMMENT '开始生产时间',
  `duration` int NOT NULL DEFAULT '1' COMMENT '生产时长（工作日，1=8小时）',
  `end_time` datetime DEFAULT NULL COMMENT '结束生产时间',
  `color_code` char(7) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '#00AEF3' COMMENT '甘特图显示颜色',
  `finish_date` datetime DEFAULT NULL COMMENT '完成日期',
  `cancel_date` datetime DEFAULT NULL COMMENT '取消日期',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '任务状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 生产任务';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_pro_task`
--

LOCK TABLES `mes_pro_task` WRITE;
/*!40000 ALTER TABLE `mes_pro_task` DISABLE KEYS */;
INSERT INTO `mes_pro_task` VALUES (1,'PT202503150001','博世螺丝刀【5000】PCS 注塑',1,1,1,1,75,5000.00,1500.00,1480.00,20.00,0.00,207,'2025-03-15 08:00:00',3,'2025-03-18 08:00:00','#00AEF3',NULL,NULL,0,'','1','2026-02-19 15:25:39','1','2026-04-16 09:47:00',0x00,1),(2,'PT202503150002','博世螺丝刀【5000】PCS 注塑',1,1,1,1,75,5000.00,0.00,0.00,0.00,0.00,207,'2025-03-18 08:00:00',3,'2025-03-21 08:00:00','#FF6B6B',NULL,NULL,0,'','1','2026-02-19 15:25:39','1','2026-02-19 15:25:39',0x00,1),(3,'PT202503150003','博世螺丝刀【10000】PCS 冲压',1,2,1,2,75,10000.00,0.00,0.00,0.00,0.00,207,'2025-03-20 08:00:00',5,'2025-03-27 08:00:00','#4ECDC4',NULL,NULL,0,'','1','2026-02-19 15:25:39','1','2026-02-19 15:25:39',0x00,1),(4,'PT202503150004','博世螺丝刀【10000】PCS 组装',1,3,1,3,75,10000.00,4.00,2.00,2.00,0.00,207,'2025-03-27 08:00:00',4,'2025-04-02 08:00:00','#45B7D1',NULL,NULL,0,'','1','2026-02-19 15:25:39','1','2026-03-24 15:17:23',0x00,1),(5,'PT202503200001','德力西螺丝刀【10000】PCS 注塑',4,1,1,1,75,10000.00,0.00,0.00,0.00,0.00,208,'2025-03-25 08:00:00',5,'2025-04-01 08:00:00','#96CEB4',NULL,NULL,0,'','1','2026-02-19 15:25:39','1','2026-02-19 15:25:39',0x00,1),(6,'TASK20260315000001','ABC【null】',15,1,6,6,100,1.00,0.00,0.00,0.00,0.00,NULL,'1970-01-01 08:00:00',5,'1970-01-01 08:00:00','#00AEF3','2026-04-04 10:41:24',NULL,5,'','1','2026-03-15 22:56:12','1','2026-04-16 09:47:00',0x00,1),(7,'TASK_ACCEPT_FB_NORMAL','验收报工任务-非质检',17,7,1,1,75,100.00,20.00,18.00,2.00,0.00,207,'2026-05-26 00:33:47',1,'2026-05-27 00:33:47','#00AEF3',NULL,NULL,0,'验收造数：非质检任务','1','2026-05-26 00:33:47','1','2026-05-26 00:33:47',0x00,1),(8,'TASK_ACCEPT_FB_CHECK','验收报工任务-待检验',17,8,1,4,75,100.00,20.00,18.00,2.00,0.00,207,'2026-05-26 00:33:47',1,'2026-05-27 00:33:47','#F59E0B',NULL,NULL,0,'验收造数：质检任务','1','2026-05-26 00:33:47','1','2026-05-26 00:33:47',0x00,1);
/*!40000 ALTER TABLE `mes_pro_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_pro_task_issue`
--

DROP TABLE IF EXISTS `mes_pro_task_issue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_pro_task_issue` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `task_id` bigint NOT NULL COMMENT '生产任务编号',
  `work_order_id` bigint DEFAULT NULL COMMENT '生产工单编号',
  `workstation_id` bigint DEFAULT NULL COMMENT '工作站编号',
  `source_doc_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源单据类型',
  `source_doc_id` bigint NOT NULL COMMENT '来源单据编号',
  `source_line_id` bigint DEFAULT NULL COMMENT '来源单据行编号',
  `source_doc_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源单据编码',
  `batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '投料批次',
  `item_id` bigint DEFAULT NULL COMMENT '产品物料编号',
  `unit_measure_id` bigint DEFAULT NULL COMMENT '单位编号',
  `issued_quantity` decimal(12,2) DEFAULT '0.00' COMMENT '总投料数量',
  `available_quantity` decimal(12,2) DEFAULT '0.00' COMMENT '当前可用数量',
  `used_quantity` decimal(12,2) DEFAULT '0.00' COMMENT '当前使用数量',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 生产任务投料';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_pro_task_issue`
--

LOCK TABLES `mes_pro_task_issue` WRITE;
/*!40000 ALTER TABLE `mes_pro_task_issue` DISABLE KEYS */;
INSERT INTO `mes_pro_task_issue` VALUES (1,1,1,1,'ISSUE',1001,1,'ISS202503150001','B20250315001',70,200,100.00,50.00,50.00,'','1','2026-02-19 15:25:39','1','2026-02-19 15:25:39',0x00,1),(2,1,1,1,'ISSUE',1001,2,'ISS202503150001','B20250315001',71,201,25000.00,10000.00,15000.00,'','1','2026-02-19 15:25:39','1','2026-02-19 15:25:39',0x00,1);
/*!40000 ALTER TABLE `mes_pro_task_issue` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_pro_work_order`
--

DROP TABLE IF EXISTS `mes_pro_work_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_pro_work_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工单编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工单名称',
  `type` tinyint NOT NULL DEFAULT '1' COMMENT '工单类型',
  `order_source_type` tinyint NOT NULL COMMENT '来源类型',
  `order_source_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源单据编号',
  `product_id` bigint NOT NULL COMMENT '产品编号',
  `quantity` decimal(14,2) NOT NULL DEFAULT '0.00' COMMENT '生产数量',
  `quantity_produced` decimal(14,2) NOT NULL DEFAULT '0.00' COMMENT '已生产数量',
  `quantity_changed` decimal(14,2) NOT NULL DEFAULT '0.00' COMMENT '调整数量',
  `quantity_scheduled` decimal(14,2) NOT NULL DEFAULT '0.00' COMMENT '已排产数量',
  `client_id` bigint DEFAULT NULL COMMENT '客户编号',
  `vendor_id` bigint DEFAULT NULL COMMENT '供应商编号',
  `batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `request_date` datetime NOT NULL COMMENT '需求日期',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父工单编号',
  `finish_date` datetime DEFAULT NULL COMMENT '完成时间',
  `cancel_date` datetime DEFAULT NULL COMMENT '取消时间',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '工单状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 生产工单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_pro_work_order`
--

LOCK TABLES `mes_pro_work_order` WRITE;
/*!40000 ALTER TABLE `mes_pro_work_order` DISABLE KEYS */;
INSERT INTO `mes_pro_work_order` VALUES (1,'MO202503120008','博世螺丝刀【一字型，蓝色刀柄】',1,1,'PO202503011001',75,10000.00,19.00,0.00,0.00,207,NULL,NULL,'2025-03-31 00:00:00',0,NULL,NULL,1,'','1','2026-02-17 12:05:23','1','2026-03-24 15:17:23',0x00,1),(2,'MO202503120009','螺丝刀刀柄【蓝色】【10000】PCS',1,1,'PO202503011001',73,10000.00,20.00,0.00,0.00,207,NULL,NULL,'2025-03-31 00:00:00',1,'2025-05-07 15:48:12','2025-05-07 16:07:45',3,'','1','2026-02-17 12:05:23','1','2026-02-17 12:05:23',0x00,1),(3,'MO202503120010','螺丝刀刀头【10000】PCS',1,1,'PO202503011001',74,10000.00,100.00,0.00,0.00,207,NULL,NULL,'2025-03-31 00:00:00',1,'2025-05-07 16:07:31',NULL,2,'','1','2026-02-17 12:05:23','1','2026-02-17 12:05:23',0x00,1),(4,'MO202503180007','德力西螺丝刀【蓝色、一字型】',1,1,'202503181001',75,10000.00,0.00,0.00,0.00,208,NULL,NULL,'2025-03-31 00:00:00',0,NULL,NULL,1,'','1','2026-02-17 12:05:23','1','2026-02-17 12:05:23',0x00,1),(5,'MO202503180013','螺丝刀刀柄【蓝色】【10000】PCS',1,1,'202503181001',73,10000.00,0.00,0.00,0.00,208,NULL,NULL,'2025-03-31 00:00:00',4,NULL,'2025-05-07 16:07:24',3,'','1','2026-02-17 12:05:23','1','2026-02-17 12:05:23',0x00,1),(6,'MO202503180014','螺丝刀刀头【10000】PCS',1,1,'202503181001',74,10000.00,0.00,0.00,0.00,208,NULL,NULL,'2025-03-31 00:00:00',4,NULL,NULL,1,'','1','2026-02-17 12:05:23','1','2026-02-17 12:05:23',0x00,1),(7,'MO202504120001','外协工单-螺丝刀刀柄【蓝色】【1000】个',2,1,'PO202503011001',73,1000.00,112.00,0.00,0.00,207,201,NULL,'2025-03-31 00:00:00',1,NULL,'2025-05-07 16:07:28',3,'','1','2026-02-17 12:05:23','1','2026-02-17 12:05:23',0x00,1),(8,'MOPFAsX7VPpn','222',1,1,'1',69,3.00,0.00,0.00,0.00,200,NULL,'5','2026-02-05 00:00:00',0,NULL,NULL,1,'2312312','1','2026-02-19 19:22:42','1','2026-02-19 23:56:48',0x00,1),(9,'MO2FkNe4IlHv','EEE',1,1,NULL,69,10.00,0.00,0.00,0.00,NULL,NULL,NULL,'2026-03-27 00:00:00',0,'2026-04-04 10:41:42',NULL,2,'','1','2026-03-15 00:14:30','1','2026-04-04 10:41:42',0x00,1),(10,'MOp6T6IA7yJS','123',1,1,NULL,104,10.00,0.00,0.00,0.00,NULL,NULL,NULL,'2026-03-27 00:00:00',0,NULL,NULL,0,'','1','2026-03-15 00:32:18','1','2026-04-05 16:19:56',0x00,1),(11,'MOfIPF0aMKBM','EEE',1,1,NULL,70,10.00,0.00,0.00,0.00,NULL,NULL,NULL,'2026-03-19 00:00:00',0,NULL,NULL,0,'','1','2026-03-15 00:33:54','1','2026-03-15 00:33:54',0x00,1),(12,'MOnzySr0PP6s','123',1,1,NULL,100,1.00,0.00,0.00,0.00,NULL,NULL,NULL,'2026-04-02 00:00:00',0,'2026-04-04 17:00:09',NULL,2,'','1','2026-03-15 00:36:59','1','2026-04-04 17:00:09',0x00,1),(13,'MOFkVXhpDqwa','eee',1,1,NULL,69,1.00,0.00,0.00,0.00,207,NULL,NULL,'2026-03-14 00:00:00',0,NULL,'2026-03-15 09:24:32',3,'','1','2026-03-15 09:24:20','1','2026-03-15 09:24:32',0x00,1),(14,'MOjs2jM1ETv6','11',1,1,NULL,100,10.00,0.00,0.00,0.00,207,NULL,NULL,'2026-03-12 00:00:00',0,NULL,NULL,0,'1321','1','2026-03-15 09:28:06','1','2026-03-15 09:28:23',0x00,1),(15,'MOgf77qA1G0y','EEE',1,2,NULL,100,10.00,0.00,0.00,0.00,200,NULL,NULL,'2026-03-25 00:00:00',0,'2026-04-04 10:41:24',NULL,2,'','1','2026-03-15 10:13:10','1','2026-04-04 10:41:24',0x00,1),(16,'MO1InqI3Jbfa','ABC-CCC【10】公斤',1,2,NULL,102,10.00,0.00,0.00,0.00,200,NULL,NULL,'2026-03-25 00:00:00',15,NULL,NULL,0,'','1','2026-03-15 10:38:53','1','2026-04-05 16:19:41',0x00,1),(17,'MO_ACCEPT_FB_20260526','验收生产报工工单-螺丝刀',1,1,'ACCEPT-FEEDBACK',75,200.00,40.00,0.00,0.00,207,NULL,'BATCH-ACCEPT-FB','2026-06-02 00:00:00',0,NULL,NULL,1,'验收造数：生产报工依赖工单','1','2026-05-26 00:33:47','1','2026-05-26 00:33:47',0x00,1);
/*!40000 ALTER TABLE `mes_pro_work_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_pro_work_order_bom`
--

DROP TABLE IF EXISTS `mes_pro_work_order_bom`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_pro_work_order_bom` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `work_order_id` bigint NOT NULL COMMENT '生产工单编号',
  `item_id` bigint NOT NULL COMMENT 'BOM 物料编号',
  `quantity` decimal(14,2) NOT NULL DEFAULT '0.00' COMMENT '预计使用量',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 生产工单 BOM';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_pro_work_order_bom`
--

LOCK TABLES `mes_pro_work_order_bom` WRITE;
/*!40000 ALTER TABLE `mes_pro_work_order_bom` DISABLE KEYS */;
INSERT INTO `mes_pro_work_order_bom` VALUES (1,1,73,10000.00,'','1','2026-02-17 12:05:23','1','2026-02-17 12:05:23',0x00,1),(2,1,74,10000.00,'','1','2026-02-17 12:05:23','1','2026-02-17 12:05:23',0x00,1),(3,1,94,1000.00,'','1','2026-02-17 12:05:23','1','2026-02-17 12:05:23',0x00,1),(4,1,95,100.00,'','1','2026-02-17 12:05:23','1','2026-02-17 12:05:23',0x00,1),(5,2,71,50000.00,'','1','2026-02-17 12:05:23','1','2026-02-17 12:05:23',0x00,1),(6,2,70,200.00,'','1','2026-02-17 12:05:23','1','2026-02-17 12:05:23',0x00,1),(7,3,72,2000.00,'','1','2026-02-17 12:05:23','1','2026-02-17 12:05:23',0x00,1),(8,4,73,10000.00,'','1','2026-02-17 12:05:23','1','2026-02-17 12:05:23',0x00,1),(9,4,74,10000.00,'','1','2026-02-17 12:05:23','1','2026-02-17 12:05:23',0x00,1),(10,4,94,1000.00,'','1','2026-02-17 12:05:23','1','2026-02-17 12:05:23',0x00,1),(11,4,95,100.00,'','1','2026-02-17 12:05:23','1','2026-02-17 12:05:23',0x00,1),(12,5,71,50000.00,'','1','2026-02-17 12:05:23','1','2026-02-17 12:05:23',0x00,1),(13,5,70,200.00,'','1','2026-02-17 12:05:23','1','2026-02-17 12:05:23',0x00,1),(14,6,72,2001.00,'','1','2026-02-17 12:05:23','1','2026-02-17 12:05:23',0x00,1),(15,7,71,5000.00,'','1','2026-02-17 12:05:23','1','2026-02-17 12:05:23',0x00,1),(16,7,70,20.00,'','1','2026-02-17 12:05:23','1','2026-02-17 12:05:23',0x00,1),(17,12,101,1.00,'','1','2026-03-15 00:37:12','1','2026-03-15 00:37:12',0x00,1),(18,12,102,1.00,'','1','2026-03-15 00:37:12','1','2026-03-15 00:37:12',0x00,1),(19,14,101,10.00,'','1','2026-03-15 09:28:06','1','2026-03-15 09:28:06',0x00,1),(20,14,102,200.00,'','1','2026-03-15 09:28:06','1','2026-03-15 09:28:20',0x00,1),(21,15,101,10.00,'','1','2026-03-15 10:13:10','1','2026-03-15 10:13:10',0x00,1),(22,15,102,10.00,'','1','2026-03-15 10:13:10','1','2026-03-15 10:13:10',0x00,1),(23,15,103,10.00,'','1','2026-03-15 10:13:10','1','2026-03-15 10:13:10',0x00,1),(24,10,102,10.00,'','1','2026-04-05 16:19:56','1','2026-04-05 16:19:56',0x00,1),(25,10,101,10.00,'','1','2026-04-05 16:19:56','1','2026-04-05 16:19:56',0x00,1),(26,10,100,10.00,'','1','2026-04-05 16:19:56','1','2026-04-05 16:19:56',0x00,1),(27,10,95,10.00,'','1','2026-04-05 16:19:56','1','2026-04-05 16:19:56',0x00,1);
/*!40000 ALTER TABLE `mes_pro_work_order_bom` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_pro_work_record`
--

DROP TABLE IF EXISTS `mes_pro_work_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_pro_work_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id` bigint NOT NULL COMMENT '用户编号',
  `workstation_id` bigint NOT NULL COMMENT '工作站编号',
  `type` tinyint NOT NULL COMMENT '当前状态',
  `clock_in_time` datetime DEFAULT NULL COMMENT '上工时间',
  `clock_out_time` datetime DEFAULT NULL COMMENT '下工时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 当前绑定状态快照';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_pro_work_record`
--

LOCK TABLES `mes_pro_work_record` WRITE;
/*!40000 ALTER TABLE `mes_pro_work_record` DISABLE KEYS */;
INSERT INTO `mes_pro_work_record` VALUES (1,1,7,1,'2026-05-28 00:01:50','2026-05-28 00:01:42','','1','2026-04-05 09:00:00','1','2026-05-28 00:01:50',0x00,1);
/*!40000 ALTER TABLE `mes_pro_work_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_pro_work_record_log`
--

DROP TABLE IF EXISTS `mes_pro_work_record_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_pro_work_record_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id` bigint NOT NULL COMMENT '用户编号',
  `workstation_id` bigint NOT NULL COMMENT '工作站编号',
  `type` tinyint NOT NULL COMMENT '操作类型',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 上下工记录流水';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_pro_work_record_log`
--

LOCK TABLES `mes_pro_work_record_log` WRITE;
/*!40000 ALTER TABLE `mes_pro_work_record_log` DISABLE KEYS */;
INSERT INTO `mes_pro_work_record_log` VALUES (1,1,1,1,'','1','2026-04-05 09:00:00','1','2026-04-05 09:00:00',0x00,1),(2,1,1,2,'','1','2026-04-05 12:00:00','1','2026-04-05 12:00:00',0x00,1),(3,1,3,1,'','1','2026-04-05 13:30:00','1','2026-04-05 13:30:00',0x00,1),(4,1,3,2,'','1','2026-04-05 17:30:00','1','2026-04-05 17:30:00',0x00,1),(5,1,1,1,'','1','2026-04-06 09:00:00','1','2026-04-06 09:00:00',0x00,1),(6,1,1,2,'','1','2026-05-27 06:55:24','1','2026-05-27 06:55:24',0x00,1),(7,1,4,1,'','1','2026-05-27 06:55:40','1','2026-05-27 06:55:40',0x00,1),(8,1,4,2,'','1','2026-05-27 06:55:45','1','2026-05-27 06:55:45',0x00,1),(9,1,6,1,'','1','2026-05-27 06:56:04','1','2026-05-27 06:56:04',0x00,1),(10,1,6,2,'','1','2026-05-27 06:56:07','1','2026-05-27 06:56:07',0x00,1),(11,1,6,1,'','1','2026-05-27 23:55:22','1','2026-05-27 23:55:22',0x00,1),(12,1,6,2,'','1','2026-05-27 23:55:26','1','2026-05-27 23:55:26',0x00,1),(13,1,5,1,'','1','2026-05-27 23:55:43','1','2026-05-27 23:55:43',0x00,1),(14,1,5,2,'','1','2026-05-28 00:01:34','1','2026-05-28 00:01:34',0x00,1),(15,1,6,1,'','1','2026-05-28 00:01:40','1','2026-05-28 00:01:40',0x00,1),(16,1,6,2,'','1','2026-05-28 00:01:42','1','2026-05-28 00:01:42',0x00,1),(17,1,7,1,'','1','2026-05-28 00:01:50','1','2026-05-28 00:01:50',0x00,1);
/*!40000 ALTER TABLE `mes_pro_work_record_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_qc_defect`
--

DROP TABLE IF EXISTS `mes_qc_defect`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_qc_defect` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '缺陷编码',
  `name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '缺陷描述',
  `type` tinyint NOT NULL COMMENT '检测项类型（枚举：MesQcDefectTypeEnum）',
  `level` int NOT NULL COMMENT '缺陷等级',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=208 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 缺陷类型表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_qc_defect`
--

LOCK TABLES `mes_qc_defect` WRITE;
/*!40000 ALTER TABLE `mes_qc_defect` DISABLE KEYS */;
INSERT INTO `mes_qc_defect` VALUES (200,'D0001','表面划痕',2,3,'轻微划痕，不影响功能','1','2026-02-21 13:35:58','1','2026-04-09 15:03:20',0x00,1),(201,'D0002','颜色偏差',2,3,'色差在可接受范围边缘','1','2026-02-21 13:35:58','1','2026-04-09 15:03:20',0x00,1),(202,'D0003','尺寸超差',1,2,'尺寸超出公差范围','1','2026-02-21 13:35:58','1','2026-04-09 15:03:20',0x00,1),(203,'D0004','表面凹陷',2,2,'表面有明显凹陷','1','2026-02-21 13:35:58','1','2026-04-09 15:03:20',0x00,1),(204,'D0005','材料裂纹',2,1,'材料出现裂纹，存在安全隐患','1','2026-02-21 13:35:58','1','2026-04-09 15:03:20',0x00,1),(205,'D0006','毛刺未清理',2,3,'边缘存在毛刺','1','2026-02-21 13:35:58','1','2026-04-09 15:03:20',0x00,1),(206,'D0007','焊接不良',2,2,'焊点不饱满或虚焊','1','2026-02-21 13:35:58','1','2026-04-09 15:03:20',0x00,1),(207,'D0008','功能失效',2,1,'功能测试不通过','1','2026-02-21 13:35:58','1','2026-04-09 15:03:20',0x00,1);
/*!40000 ALTER TABLE `mes_qc_defect` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_qc_defect_record`
--

DROP TABLE IF EXISTS `mes_qc_defect_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_qc_defect_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `qc_type` int NOT NULL DEFAULT '1' COMMENT '检验类型',
  `qc_id` bigint NOT NULL COMMENT '检验单ID',
  `line_id` bigint NOT NULL COMMENT '检验行ID',
  `name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '缺陷描述',
  `level` int NOT NULL COMMENT '缺陷等级',
  `quantity` int DEFAULT '1' COMMENT '缺陷数量',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_qc_defect_record_type_qc` (`qc_type`,`qc_id`) USING BTREE,
  KEY `idx_qc_defect_record_type_qc_line` (`qc_type`,`qc_id`,`line_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=227 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 质检缺陷记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_qc_defect_record`
--

LOCK TABLES `mes_qc_defect_record` WRITE;
/*!40000 ALTER TABLE `mes_qc_defect_record` DISABLE KEYS */;
INSERT INTO `mes_qc_defect_record` VALUES (200,1,201,203,'长度超出上限，实测80.35mm（上限80.20mm）',2,1,'3号样品','1','2026-02-03 12:00:00','1','2026-02-03 16:00:00',0x00,1),(201,1,201,204,'颜色偏差，与标准色板不一致',3,1,'2号样品','1','2026-02-03 12:30:00','1','2026-02-03 16:00:00',0x00,1),(202,1,201,204,'表面有轻微污渍',3,1,'7号样品','1','2026-02-03 13:00:00','1','2026-02-03 16:00:00',0x00,1),(203,1,202,205,'表面含轻微杂质颗粒',3,1,'1号样品，不影响使用','1','2026-02-10 13:00:00','104','2026-02-10 15:00:00',0x00,1),(204,1,203,207,'xxx',2,1,'','1','2026-02-21 22:06:23','1','2026-02-21 22:06:23',0x00,1),(210,2,301,303,'长度超出上限，实测150.65mm（上限150.50mm）',2,1,'5号样品','1','2026-02-21 12:00:00','1','2026-02-21 16:00:00',0x00,1),(211,2,301,303,'表面有轻微划痕',3,1,'8号样品','1','2026-02-21 12:30:00','1','2026-02-21 16:00:00',0x00,1),(212,2,301,303,'注塑毛刺未清理',3,1,'12号样品','1','2026-02-21 13:00:00','1','2026-02-21 16:00:00',0x00,1),(213,3,201,202,'包装盒长度超出上限，实测120.65mm（上限120.50mm）',2,1,'2号样品','1','2026-02-19 13:00:00','1','2026-02-19 15:00:00',0x00,1),(214,3,201,203,'包装盒正面印刷文字模糊',3,1,'3号样品','1','2026-02-19 13:30:00','1','2026-02-19 15:00:00',0x00,1),(215,3,202,206,'合格证字迹模糊，需重新打印',3,1,'1号箱','1','2026-02-20 13:00:00','104','2026-02-20 14:00:00',0x00,1),(220,4,301,303,'退回刀头长度超出上限，实测80.72mm（上限80.50mm）',2,1,'5号样品','1','2026-02-21 13:00:00','1','2026-02-21 15:00:00',0x00,1),(221,4,301,304,'退回数量与退货单不一致，差1件',3,1,'清点差异','1','2026-02-21 13:30:00','1','2026-02-21 15:00:00',0x00,1),(222,4,301,305,'退回产品合格证缺失',2,1,'3号样品','1','2026-02-21 14:00:00','1','2026-02-21 15:00:00',0x00,1),(223,4,302,307,'退料包装袋破损，颗粒轻微溢出',3,1,'1号包装袋','1','2026-02-22 12:00:00','104','2026-02-22 14:00:00',0x00,1),(224,4,304,310,'1',1,1,'333','1','2026-02-22 14:49:31','1','2026-02-22 14:49:31',0x00,1),(225,1,208,219,'ABC',1,1,'','1','2026-03-23 23:09:35','1','2026-03-23 23:09:35',0x00,1),(226,1,204,210,'AA',1,1,'','1','2026-04-17 07:35:41','1','2026-04-17 07:35:41',0x00,1);
/*!40000 ALTER TABLE `mes_qc_defect_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_qc_indicator`
--

DROP TABLE IF EXISTS `mes_qc_indicator`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_qc_indicator` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检测项编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检测项名称',
  `type` tinyint NOT NULL,
  `tool` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '检测工具',
  `result_type` tinyint NOT NULL COMMENT '结果值类型',
  `result_specification` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '结果值属性',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=207 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 质检指标';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_qc_indicator`
--

LOCK TABLES `mes_qc_indicator` WRITE;
/*!40000 ALTER TABLE `mes_qc_indicator` DISABLE KEYS */;
INSERT INTO `mes_qc_indicator` VALUES (200,'QI0035','长度',1,'卡尺',1,NULL,'','1','2026-02-21 13:35:58','1','2026-04-09 14:38:53',0x00,1),(201,'QI0036','宽度',1,'卡尺',1,NULL,'','1','2026-02-21 13:35:58','1','2026-04-09 14:38:53',0x00,1),(202,'QI0038','颜色是否纯正',2,NULL,4,'sys_yes_no','','1','2026-02-21 13:35:58','1','2026-04-09 14:38:53',0x00,1),(203,'QI0040','外观照片',2,NULL,5,'IMG','','1','2026-02-21 13:35:58','1','2026-04-09 14:38:53',0x00,1),(204,'QI0041','检测过程视频',2,NULL,5,'FILE','','1','2026-02-21 13:35:58','1','2026-04-09 14:38:53',0x00,1),(205,'QI0042','个数清点',2,NULL,2,NULL,'','1','2026-02-21 13:35:58','1','2026-04-09 14:38:53',0x00,1),(206,'QI0043','合格证编号',2,NULL,3,NULL,'','1','2026-02-21 13:35:58','1','2026-04-09 14:38:53',0x00,1);
/*!40000 ALTER TABLE `mes_qc_indicator` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_qc_indicator_result`
--

DROP TABLE IF EXISTS `mes_qc_indicator_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_qc_indicator_result` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '样品编号',
  `qc_id` bigint NOT NULL COMMENT '关联质检单ID（IQC/IPQC/OQC/RQC 的 id）',
  `qc_type` tinyint NOT NULL COMMENT '质检类型',
  `item_id` bigint DEFAULT NULL COMMENT '产品物料ID（关联 mes_md_item）',
  `sn` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '物资SN',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_qc_id` (`qc_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 检验结果记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_qc_indicator_result`
--

LOCK TABLES `mes_qc_indicator_result` WRITE;
/*!40000 ALTER TABLE `mes_qc_indicator_result` DISABLE KEYS */;
INSERT INTO `mes_qc_indicator_result` VALUES (1,'xx',203,1,72,'12','333','1','2026-02-21 23:27:17','1','2026-02-22 07:55:38',0x01,1),(2,'33',203,1,72,'12','4444','1','2026-02-21 23:27:41','1','2026-02-22 07:55:40',0x01,1),(3,'1',203,1,72,'2','','1','2026-02-22 07:55:51','1','2026-02-22 07:57:58',0x00,1),(4,'2',203,1,72,'3','','1','2026-02-22 08:01:04','1','2026-02-22 08:01:23',0x00,1),(5,'QR2026041500001',204,1,70,NULL,'','1','2026-04-15 23:54:53','1','2026-04-15 23:54:53',0x00,1),(6,'QR2026041500002',204,1,70,NULL,'','1','2026-04-15 23:56:43','1','2026-04-15 23:56:43',0x00,1),(7,'QR2026041700001',209,1,69,NULL,'','1','2026-04-17 07:33:12','1','2026-04-17 07:33:12',0x00,1);
/*!40000 ALTER TABLE `mes_qc_indicator_result` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_qc_indicator_result_detail`
--

DROP TABLE IF EXISTS `mes_qc_indicator_result_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_qc_indicator_result_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `result_id` bigint NOT NULL COMMENT '关联检验结果ID（关联 mes_qc_indicator_result）',
  `indicator_id` bigint DEFAULT NULL COMMENT '检测指标ID（关联 mes_qc_indicator）',
  `value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '检测值（统一存为字符串）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_result_id` (`result_id`) USING BTREE,
  KEY `idx_indicator_id` (`indicator_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 检验结果明细记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_qc_indicator_result_detail`
--

LOCK TABLES `mes_qc_indicator_result_detail` WRITE;
/*!40000 ALTER TABLE `mes_qc_indicator_result_detail` DISABLE KEYS */;
INSERT INTO `mes_qc_indicator_result_detail` VALUES (1,1,200,'3','','1','2026-02-21 23:27:17','1','2026-02-21 23:55:38',0x01,1),(2,1,201,NULL,'','1','2026-02-21 23:27:17','1','2026-02-21 23:55:38',0x01,1),(3,1,206,'6','','1','2026-02-21 23:27:17','1','2026-02-21 23:55:38',0x01,1),(4,2,200,'3','','1','2026-02-21 23:27:41','1','2026-02-21 23:55:40',0x01,1),(5,2,201,'4','','1','2026-02-21 23:27:41','1','2026-02-21 23:55:40',0x01,1),(6,2,206,'6','','1','2026-02-21 23:27:41','1','2026-02-21 23:55:40',0x01,1),(7,3,200,'5','','1','2026-02-22 07:55:51','1','2026-02-22 07:57:58',0x00,1),(8,3,201,'8','','1','2026-02-22 07:55:51','1','2026-02-22 07:57:58',0x00,1),(9,3,206,'10','','1','2026-02-22 07:55:51','1','2026-02-22 07:57:58',0x00,1),(10,4,200,NULL,'','1','2026-02-22 08:01:04','1','2026-02-22 08:01:23',0x00,1),(11,4,201,NULL,'','1','2026-02-22 08:01:04','1','2026-02-22 08:01:23',0x00,1),(12,4,206,'6','','1','2026-02-22 08:01:04','1','2026-02-22 08:01:23',0x00,1),(13,5,200,'200','','1','2026-04-15 23:54:53','1','2026-04-15 23:54:53',0x00,1),(14,5,205,NULL,'','1','2026-04-15 23:54:53','1','2026-04-15 23:54:53',0x00,1),(15,5,206,NULL,'','1','2026-04-15 23:54:53','1','2026-04-15 23:54:53',0x00,1),(16,6,200,'10.5','','1','2026-04-15 23:56:43','1','2026-04-15 23:56:43',0x00,1),(17,6,205,'100','','1','2026-04-15 23:56:43','1','2026-04-15 23:56:43',0x00,1),(18,6,206,'CERT-OK-20260415','','1','2026-04-15 23:56:43','1','2026-04-15 23:56:43',0x00,1),(19,7,200,'1','','1','2026-04-17 07:33:12','1','2026-04-17 07:33:12',0x00,1),(20,7,205,'2','','1','2026-04-17 07:33:12','1','2026-04-17 07:33:12',0x00,1),(21,7,206,'3','','1','2026-04-17 07:33:12','1','2026-04-17 07:33:12',0x00,1);
/*!40000 ALTER TABLE `mes_qc_indicator_result_detail` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_qc_ipqc`
--

DROP TABLE IF EXISTS `mes_qc_ipqc`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_qc_ipqc` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检验单编号',
  `name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检验单名称',
  `type` tinyint NOT NULL COMMENT 'IPQC 检验类型',
  `template_id` bigint NOT NULL COMMENT '检验模板ID',
  `source_doc_type` int DEFAULT NULL COMMENT '来源单据类型',
  `source_doc_id` bigint DEFAULT NULL COMMENT '来源单据ID',
  `source_line_id` bigint DEFAULT NULL COMMENT '来源单据行ID',
  `source_doc_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源单据编号（冗余）',
  `work_order_id` bigint NOT NULL COMMENT '生产工单ID',
  `task_id` bigint DEFAULT NULL COMMENT '生产任务ID',
  `workstation_id` bigint NOT NULL COMMENT '工位ID',
  `process_id` bigint DEFAULT NULL COMMENT '工序ID',
  `item_id` bigint NOT NULL COMMENT '产品物料ID',
  `check_quantity` decimal(14,2) DEFAULT NULL COMMENT '检测数量',
  `qualified_quantity` decimal(14,2) DEFAULT '0.00' COMMENT '合格品数量',
  `unqualified_quantity` decimal(14,2) DEFAULT '0.00' COMMENT '不合格品数量',
  `labor_scrap_quantity` decimal(14,2) DEFAULT '0.00' COMMENT '工废数量',
  `material_scrap_quantity` decimal(14,2) DEFAULT '0.00' COMMENT '料废数量',
  `other_scrap_quantity` decimal(14,2) DEFAULT '0.00' COMMENT '其他废品数量',
  `critical_rate` decimal(14,2) DEFAULT '0.00' COMMENT '致命缺陷率（%）',
  `major_rate` decimal(14,2) DEFAULT '0.00' COMMENT '严重缺陷率（%）',
  `minor_rate` decimal(14,2) DEFAULT '0.00' COMMENT '轻微缺陷率（%）',
  `critical_quantity` int DEFAULT '0' COMMENT '致命缺陷数量',
  `major_quantity` int DEFAULT '0' COMMENT '严重缺陷数量',
  `minor_quantity` int DEFAULT '0' COMMENT '轻微缺陷数量',
  `check_result` tinyint DEFAULT NULL COMMENT '检测结果',
  `inspect_date` datetime DEFAULT NULL COMMENT '检测日期',
  `inspector_user_id` bigint DEFAULT NULL COMMENT '检测人员用户 ID',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_code` (`code`) USING BTREE,
  KEY `idx_work_order_id` (`work_order_id`) USING BTREE,
  KEY `idx_workstation_id` (`workstation_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=304 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 过程检验单（IPQC）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_qc_ipqc`
--

LOCK TABLES `mes_qc_ipqc` WRITE;
/*!40000 ALTER TABLE `mes_qc_ipqc` DISABLE KEYS */;
INSERT INTO `mes_qc_ipqc` VALUES (300,'IPQC20260220001','螺丝刀组装过程巡检',1,102,NULL,NULL,NULL,NULL,1,NULL,3,NULL,75,50.00,50.00,0.00,0.00,0.00,0.00,0.00,0.00,0.00,0,0,0,1,'2026-02-20 14:30:00',1,4,'全部合格，工艺稳定','1','2026-02-20 09:00:00','1','2026-02-23 21:16:03',0x00,1),(301,'IPQC20260221002','刀柄注塑首检',2,102,NULL,NULL,NULL,NULL,2,NULL,1,NULL,73,20.00,17.00,3.00,1.00,1.00,1.00,0.00,5.00,10.00,0,1,2,2,'2026-02-21 16:00:00',1,4,'不合格，需调整注塑参数','1','2026-02-21 08:30:00','1','2026-02-23 21:16:03',0x00,1),(302,'IPQC20260222003','包装工序自检',4,102,NULL,NULL,NULL,NULL,1,NULL,4,NULL,75,NULL,0.00,0.00,0.00,0.00,0.00,0.00,0.00,0.00,0,0,0,NULL,NULL,NULL,0,'待检验','1','2026-02-22 09:00:00','1','2026-02-22 09:00:00',0x00,1),(303,'IPQC20260325000002','FB202503180001 过程检验单',1,105,304,3,NULL,NULL,1,1,1,NULL,75,200.00,200.00,0.00,0.00,0.00,0.00,0.00,0.00,0.00,0,0,0,1,'2025-03-18 09:00:00',1,0,'','1','2026-03-25 17:35:19','1','2026-03-25 17:35:19',0x00,1);
/*!40000 ALTER TABLE `mes_qc_ipqc` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_qc_ipqc_line`
--

DROP TABLE IF EXISTS `mes_qc_ipqc_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_qc_ipqc_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `ipqc_id` bigint NOT NULL COMMENT '过程检验单ID',
  `indicator_id` bigint NOT NULL COMMENT '检测指标ID',
  `tool` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '检测工具',
  `check_method` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '检测方法',
  `standard_value` decimal(14,4) DEFAULT NULL COMMENT '标准值',
  `unit_measure_id` bigint DEFAULT NULL COMMENT '计量单位ID',
  `max_threshold` decimal(14,4) DEFAULT NULL COMMENT '误差上限',
  `min_threshold` decimal(14,4) DEFAULT NULL COMMENT '误差下限',
  `critical_quantity` int DEFAULT '0' COMMENT '致命缺陷数量',
  `major_quantity` int DEFAULT '0' COMMENT '严重缺陷数量',
  `minor_quantity` int DEFAULT '0' COMMENT '轻微缺陷数量',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_ipqc_id` (`ipqc_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=310 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 过程检验单行';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_qc_ipqc_line`
--

LOCK TABLES `mes_qc_ipqc_line` WRITE;
/*!40000 ALTER TABLE `mes_qc_ipqc_line` DISABLE KEYS */;
INSERT INTO `mes_qc_ipqc_line` VALUES (300,300,200,NULL,'使用千分尺测量成品长度',200.0000,206,1.0000,-1.0000,0,0,0,'实测值均在公差范围内','1','2026-02-20 10:00:00','1','2026-02-20 14:30:00',0x00,1),(301,300,205,NULL,'人工清点成品数量',NULL,NULL,NULL,NULL,0,0,0,'数量与报工单一致','1','2026-02-20 11:00:00','1','2026-02-20 14:30:00',0x00,1),(302,300,206,NULL,'核对合格证编号与批次一致性',NULL,NULL,NULL,NULL,0,0,0,'编号一致','1','2026-02-20 11:30:00','1','2026-02-20 14:30:00',0x00,1),(303,301,200,NULL,'使用千分尺测量刀柄长度',150.0000,206,0.5000,-0.5000,0,1,2,'5号样品长度超上限','1','2026-02-21 09:00:00','1','2026-02-21 16:00:00',0x00,1),(304,301,205,NULL,'人工清点数量',NULL,NULL,NULL,NULL,0,0,0,'数量一致','1','2026-02-21 10:00:00','1','2026-02-21 16:00:00',0x00,1),(305,301,206,NULL,'核对合格证编号',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-02-21 10:30:00','1','2026-02-21 16:00:00',0x00,1),(306,302,200,NULL,'使用千分尺测量成品长度',200.0000,206,1.0000,-1.0000,0,0,0,'','1','2026-02-22 09:00:00','1','2026-02-22 09:00:00',0x00,1),(307,302,205,NULL,'人工清点成品数量',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-02-22 09:00:00','1','2026-02-22 09:00:00',0x00,1),(308,302,206,NULL,'核对合格证编号与批次一致性',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-02-22 09:00:00','1','2026-02-22 09:00:00',0x00,1),(309,303,200,'卡尺','游标卡尺测量',15.0000,NULL,0.5000,0.5000,0,0,0,'','1','2026-03-25 17:35:19','1','2026-03-25 17:35:19',0x00,1);
/*!40000 ALTER TABLE `mes_qc_ipqc_line` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_qc_iqc`
--

DROP TABLE IF EXISTS `mes_qc_iqc`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_qc_iqc` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检验单编号',
  `name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检验单名称',
  `template_id` bigint NOT NULL COMMENT '检验模板ID',
  `source_doc_type` tinyint DEFAULT NULL COMMENT '来源单据类型',
  `source_doc_id` bigint DEFAULT NULL COMMENT '来源单据ID',
  `source_line_id` bigint DEFAULT NULL COMMENT '来源单据行ID',
  `source_doc_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源单据编号（冗余）',
  `vendor_id` bigint NOT NULL COMMENT '供应商ID',
  `vendor_batch` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商批次号',
  `item_id` bigint NOT NULL COMMENT '产品物料ID',
  `received_quantity` decimal(14,2) NOT NULL COMMENT '本次接收数量',
  `check_quantity` decimal(14,2) DEFAULT NULL COMMENT '本次检测数量',
  `qualified_quantity` decimal(14,2) DEFAULT '0.00' COMMENT '合格品数量',
  `unqualified_quantity` decimal(14,2) DEFAULT '0.00' COMMENT '不合格品数量',
  `critical_rate` decimal(14,2) DEFAULT '0.00' COMMENT '致命缺陷率（%）',
  `major_rate` decimal(14,2) DEFAULT '0.00' COMMENT '严重缺陷率（%）',
  `minor_rate` decimal(14,2) DEFAULT '0.00' COMMENT '轻微缺陷率（%）',
  `critical_quantity` int DEFAULT '0' COMMENT '致命缺陷数量',
  `major_quantity` int DEFAULT '0' COMMENT '严重缺陷数量',
  `minor_quantity` int DEFAULT '0' COMMENT '轻微缺陷数量',
  `check_result` tinyint DEFAULT NULL COMMENT '检测结果',
  `receive_date` datetime DEFAULT NULL COMMENT '来料日期',
  `inspect_date` datetime DEFAULT NULL COMMENT '检测日期',
  `inspector_user_id` bigint DEFAULT NULL COMMENT '检测人员用户 ID',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_code` (`code`) USING BTREE,
  KEY `idx_vendor_id` (`vendor_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=211 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 来料检验单（IQC）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_qc_iqc`
--

LOCK TABLES `mes_qc_iqc` WRITE;
/*!40000 ALTER TABLE `mes_qc_iqc` DISABLE KEYS */;
INSERT INTO `mes_qc_iqc` VALUES (200,'IQC20260201001','螺丝刀刀柄【蓝色】来料检验',100,NULL,NULL,NULL,NULL,200,'VB20260201-A',73,500.00,5.00,5.00,0.00,0.00,0.00,0.00,0,0,0,1,'2026-02-01 09:00:00','2026-02-01 14:30:00',1,4,'全部合格，质量稳定','1','2026-02-01 09:00:00','1','2026-02-23 21:16:03',0x00,1),(201,'IQC20260203002','螺丝刀刀头来料检验',100,NULL,NULL,NULL,NULL,201,'VB20260203-B',74,1000.00,10.00,7.00,3.00,0.00,10.00,20.00,0,1,2,2,'2026-02-03 08:30:00','2026-02-03 16:00:00',1,4,'不合格，已通知供应商退货','1','2026-02-03 08:30:00','1','2026-02-23 21:16:03',0x00,1),(202,'IQC20260210003','PVC颗粒来料检验',102,NULL,NULL,NULL,NULL,202,'VB20260210-C',70,200.00,3.00,2.00,1.00,0.00,0.00,33.33,0,0,1,3,'2026-02-10 10:00:00','2026-02-10 15:00:00',104,4,'轻微外观问题，让步接收','1','2026-02-10 10:00:00','104','2026-02-23 21:16:03',0x00,1),(203,'IQC20260215004','钢筋来料检验',102,100,NULL,NULL,NULL,200,'VB20260215-D',72,300.00,0.00,0.00,0.00,0.00,0.00,0.00,0,1,0,NULL,'2026-02-15 09:00:00','1970-01-01 08:00:00',1,0,'待检验','1','2026-02-15 09:00:00','1','2026-02-24 04:35:52',0x00,1),(204,'IQCFsDNvt7ZHX','呃呃呃',102,NULL,NULL,NULL,NULL,202,NULL,70,3.00,2.00,2.00,0.00,50.00,0.00,0.00,1,0,0,NULL,'1970-01-01 08:00:00','1970-01-01 08:00:00',1,0,'','1','2026-02-24 04:37:19','1','2026-04-17 07:35:41',0x00,1),(206,'IQC8vMXxJFbUe','aaa',102,NULL,NULL,NULL,NULL,200,'eee',70,3.00,3.00,2.00,1.00,0.00,0.00,0.00,0,0,0,1,'1970-01-01 08:00:00','1970-01-01 08:00:00',1,4,'','1','2026-02-24 04:40:18','1','2026-03-27 22:32:56',0x00,1),(207,'IQCRFgr3hdsbJ','ANbhv1kzQHtc 来料检验单',102,100,106,110,NULL,200,NULL,69,1.00,1.00,1.00,0.00,0.00,0.00,0.00,0,0,0,1,'2026-03-04 00:00:00','1970-01-01 08:00:00',1,4,'12321','1','2026-03-23 19:45:55','1','2026-03-23 19:51:39',0x00,1),(208,'IQCDPBIp5v1S8','ABC',102,NULL,NULL,NULL,NULL,201,NULL,69,1.00,1.00,0.00,1.00,100.00,0.00,0.00,1,0,0,1,'2026-03-03 00:00:00','2026-03-12 00:00:00',100,4,'','1','2026-03-23 21:52:59','1','2026-03-27 22:30:11',0x00,1),(209,'IQC20260325001','ANpcHrG0Q7S2 来料检验单',102,100,105,109,NULL,200,NULL,69,10.00,10.00,10.00,0.00,0.00,0.00,0.00,0,0,0,1,'2026-03-23 00:00:00','2026-03-10 00:00:00',1,4,'','1','2026-03-25 17:36:12','1','2026-03-27 22:32:18',0x00,1),(210,'IQC20260529001','测试一下',102,NULL,NULL,NULL,NULL,202,NULL,69,3.00,3.00,1.00,2.00,0.00,0.00,0.00,0,0,0,NULL,'2026-05-13 09:56:23','2026-05-14 09:56:25',100,0,'','1','2026-05-29 09:56:51','1','2026-05-29 09:56:51',0x00,1);
/*!40000 ALTER TABLE `mes_qc_iqc` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_qc_iqc_line`
--

DROP TABLE IF EXISTS `mes_qc_iqc_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_qc_iqc_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `iqc_id` bigint NOT NULL COMMENT '来料检验单ID',
  `indicator_id` bigint NOT NULL COMMENT '检测指标ID',
  `tool` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '检测工具',
  `check_method` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '检测方法',
  `standard_value` decimal(14,4) DEFAULT NULL COMMENT '标准值',
  `unit_measure_id` bigint DEFAULT NULL COMMENT '计量单位ID',
  `max_threshold` decimal(14,4) DEFAULT NULL COMMENT '误差上限',
  `min_threshold` decimal(14,4) DEFAULT NULL COMMENT '误差下限',
  `critical_quantity` int DEFAULT '0' COMMENT '致命缺陷数量',
  `major_quantity` int DEFAULT '0' COMMENT '严重缺陷数量',
  `minor_quantity` int DEFAULT '0' COMMENT '轻微缺陷数量',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_iqc_id` (`iqc_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=228 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 来料检验单行';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_qc_iqc_line`
--

LOCK TABLES `mes_qc_iqc_line` WRITE;
/*!40000 ALTER TABLE `mes_qc_iqc_line` DISABLE KEYS */;
INSERT INTO `mes_qc_iqc_line` VALUES (200,200,200,NULL,'使用千分尺测量刀柄长度',150.0000,206,150.5000,149.5000,0,0,0,'实测值 150.02mm，合格','1','2026-02-01 10:00:00','1','2026-02-01 14:30:00',0x00,1),(201,200,201,NULL,'使用千分尺测量刀柄宽度',25.0000,206,25.2000,24.8000,0,0,0,'实测值 24.98mm，合格','1','2026-02-01 10:30:00','1','2026-02-01 14:30:00',0x00,1),(202,200,202,NULL,'目视对比标准色板',NULL,NULL,NULL,NULL,0,0,0,'颜色纯正，合格','1','2026-02-01 11:00:00','1','2026-02-01 14:30:00',0x00,1),(203,201,200,NULL,'使用千分尺测量刀头长度',80.0000,206,80.2000,79.8000,0,1,0,'3号样品实测 80.35mm 超上限','1','2026-02-03 09:00:00','1','2026-02-03 16:00:00',0x00,1),(204,201,202,NULL,'目视对比标准色板',NULL,NULL,NULL,NULL,0,0,2,'2号和7号样品颜色偏差','1','2026-02-03 10:00:00','1','2026-02-03 16:00:00',0x00,1),(205,202,203,NULL,'拍摄外观照片存档',NULL,NULL,NULL,NULL,0,0,1,'1号样品表面有轻微杂质','1','2026-02-10 11:00:00','104','2026-02-10 15:00:00',0x00,1),(206,202,205,NULL,'人工清点入库数量',NULL,NULL,NULL,NULL,0,0,0,'数量与送货单一致','1','2026-02-10 11:30:00','104','2026-02-10 15:00:00',0x00,1),(207,203,200,NULL,'使用千分尺测量钢筋长度',6000.0000,206,6010.0000,5990.0000,0,1,0,'','1','2026-02-15 09:00:00','1','2026-02-21 22:06:23',0x00,1),(208,203,201,NULL,'使用千分尺测量钢筋直径',12.0000,206,12.1000,11.9000,0,0,0,'','1','2026-02-15 09:00:00','1','2026-02-21 22:06:23',0x00,1),(209,203,206,NULL,'核对合格证编号',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-02-15 09:00:00','1','2026-02-21 22:06:23',0x00,1),(210,204,200,NULL,'使用千分尺测量长度',200.0000,206,1.0000,-1.0000,1,0,0,'','1','2026-02-24 04:37:19','1','2026-04-17 07:35:41',0x00,1),(211,204,205,NULL,'人工清点数量',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-02-24 04:37:19','1','2026-04-17 07:35:41',0x00,1),(212,204,206,NULL,'核对合格证编号与送检批次一致性',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-02-24 04:37:19','1','2026-04-17 07:35:41',0x00,1),(213,206,200,'卡尺','使用千分尺测量长度',200.0000,206,1.0000,-1.0000,0,0,0,'','1','2026-02-24 04:40:18','1','2026-02-24 04:40:18',0x00,1),(214,206,205,NULL,'人工清点数量',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-02-24 04:40:18','1','2026-02-24 04:40:18',0x00,1),(215,206,206,NULL,'核对合格证编号与送检批次一致性',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-02-24 04:40:18','1','2026-02-24 04:40:18',0x00,1),(216,207,200,'卡尺','使用千分尺测量长度',200.0000,206,1.0000,-1.0000,0,0,0,'','1','2026-03-23 19:45:55','1','2026-03-23 19:45:55',0x00,1),(217,207,205,NULL,'人工清点数量',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-03-23 19:45:55','1','2026-03-23 19:45:55',0x00,1),(218,207,206,NULL,'核对合格证编号与送检批次一致性',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-03-23 19:45:55','1','2026-03-23 19:45:55',0x00,1),(219,208,200,'卡尺','使用千分尺测量长度',200.0000,206,1.0000,-1.0000,1,0,0,'','1','2026-03-23 21:52:59','1','2026-03-23 23:09:35',0x00,1),(220,208,205,NULL,'人工清点数量',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-03-23 21:52:59','1','2026-03-23 23:09:35',0x00,1),(221,208,206,NULL,'核对合格证编号与送检批次一致性',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-03-23 21:52:59','1','2026-03-23 23:09:35',0x00,1),(222,209,200,'卡尺','使用千分尺测量长度',200.0000,206,1.0000,-1.0000,0,0,0,'','1','2026-03-25 17:36:12','1','2026-03-25 17:36:12',0x00,1),(223,209,205,NULL,'人工清点数量',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-03-25 17:36:12','1','2026-03-25 17:36:12',0x00,1),(224,209,206,NULL,'核对合格证编号与送检批次一致性',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-03-25 17:36:12','1','2026-03-25 17:36:12',0x00,1),(225,210,200,'卡尺','使用千分尺测量长度',200.0000,206,1.0000,-1.0000,0,0,0,'','1','2026-05-29 09:56:51','1','2026-05-29 09:56:51',0x00,1),(226,210,205,NULL,'人工清点数量',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-05-29 09:56:51','1','2026-05-29 09:56:51',0x00,1),(227,210,206,NULL,'核对合格证编号与送检批次一致性',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-05-29 09:56:51','1','2026-05-29 09:56:51',0x00,1);
/*!40000 ALTER TABLE `mes_qc_iqc_line` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_qc_oqc`
--

DROP TABLE IF EXISTS `mes_qc_oqc`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_qc_oqc` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检验单编号',
  `name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检验单名称',
  `template_id` bigint NOT NULL COMMENT '检验模板ID（关联 mes_qc_template）',
  `source_doc_type` tinyint DEFAULT NULL COMMENT '来源单据类型',
  `source_doc_id` bigint DEFAULT NULL COMMENT '来源单据ID',
  `source_line_id` bigint DEFAULT NULL COMMENT '来源单据行ID',
  `source_doc_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源单据编号',
  `client_id` bigint NOT NULL COMMENT '客户ID（关联 mes_md_client）',
  `batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `item_id` bigint NOT NULL COMMENT '产品物料ID（关联 mes_md_item）',
  `min_check_quantity` int DEFAULT '1' COMMENT '最低检测数',
  `max_unqualified_quantity` int DEFAULT '0' COMMENT '最大不合格数',
  `out_quantity` decimal(14,2) NOT NULL COMMENT '本次出货数量',
  `check_quantity` decimal(14,2) DEFAULT NULL COMMENT '本次检测数量',
  `qualified_quantity` decimal(14,2) DEFAULT '0.00' COMMENT '合格品数量',
  `unqualified_quantity` decimal(14,2) DEFAULT '0.00' COMMENT '不合格品数量',
  `critical_rate` decimal(14,2) DEFAULT '0.00' COMMENT '致命缺陷率（%）',
  `major_rate` decimal(14,2) DEFAULT '0.00' COMMENT '严重缺陷率（%）',
  `minor_rate` decimal(14,2) DEFAULT '0.00' COMMENT '轻微缺陷率（%）',
  `critical_quantity` int DEFAULT '0' COMMENT '致命缺陷数量',
  `major_quantity` int DEFAULT '0' COMMENT '严重缺陷数量',
  `minor_quantity` int DEFAULT '0' COMMENT '轻微缺陷数量',
  `check_result` tinyint DEFAULT NULL COMMENT '检测结果（枚举 MesQcCheckResultEnum）',
  `out_date` datetime DEFAULT NULL COMMENT '出货日期',
  `inspect_date` datetime DEFAULT NULL COMMENT '检测日期',
  `inspector_user_id` bigint DEFAULT NULL COMMENT '检测人员用户 ID',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_code` (`code`) USING BTREE,
  KEY `idx_client_id` (`client_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=205 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 出货检验单（OQC）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_qc_oqc`
--

LOCK TABLES `mes_qc_oqc` WRITE;
/*!40000 ALTER TABLE `mes_qc_oqc` DISABLE KEYS */;
INSERT INTO `mes_qc_oqc` VALUES (200,'OQC20260218001','螺丝刀成品【蓝色一字型】出货检验',101,NULL,NULL,NULL,NULL,200,'OB20260218-A',75,5,1,2000.00,5.00,5.00,0.00,0.00,0.00,0.00,0,0,0,1,'2026-02-18 08:00:00','2026-02-18 11:30:00',1,1,'全部合格，准予出货','1','2026-02-18 08:00:00','1','2026-02-18 11:30:00',0x00,1),(201,'OQC20260219002','小包装盒出货检验',101,NULL,NULL,NULL,NULL,207,'OB20260219-B',94,3,0,500.00,3.00,1.00,2.00,0.00,33.33,33.33,0,1,1,2,'2026-02-19 09:00:00','2026-02-19 15:00:00',1,1,'不合格，已拦截出货','1','2026-02-19 09:00:00','1','2026-02-19 15:00:00',0x00,1),(202,'OQC20260220003','大包装箱出货检验',102,NULL,NULL,NULL,NULL,208,'OB20260220-C',95,3,0,300.00,3.00,2.00,1.00,0.00,0.00,33.33,0,0,1,3,'2026-02-20 10:00:00','2026-02-20 14:00:00',104,1,'轻微外观问题，客户接受','1','2026-02-20 10:00:00','104','2026-02-20 14:00:00',0x00,1),(203,'OQC20260222004','PVC颗粒出货检验',102,NULL,NULL,NULL,NULL,200,'OB20260222-D',70,3,0,1000.00,NULL,0.00,0.00,0.00,0.00,0.00,0,0,0,NULL,'2026-02-22 09:00:00',NULL,NULL,0,'待检验','1','2026-02-22 09:00:00','1','2026-02-22 09:00:00',0x00,1),(204,'OQC20260327001','AAA',102,NULL,NULL,NULL,NULL,200,NULL,69,1,0,2.00,2.00,1.00,1.00,0.00,0.00,0.00,0,0,0,NULL,'1970-01-01 08:00:00','1970-01-01 08:00:00',1,0,'','1','2026-03-27 17:07:22','1','2026-03-27 17:07:22',0x00,1);
/*!40000 ALTER TABLE `mes_qc_oqc` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_qc_oqc_line`
--

DROP TABLE IF EXISTS `mes_qc_oqc_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_qc_oqc_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `oqc_id` bigint NOT NULL COMMENT '出货检验单ID（关联 mes_qc_oqc）',
  `indicator_id` bigint NOT NULL COMMENT '检测指标ID（关联 mes_qc_indicator）',
  `tool` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '检测工具（冗余自 mes_qc_indicator）',
  `check_method` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '检测方法',
  `standard_value` decimal(14,4) DEFAULT NULL COMMENT '标准值',
  `unit_measure_id` bigint DEFAULT NULL COMMENT '计量单位ID（关联 mes_md_unit_measure）',
  `max_threshold` decimal(14,4) DEFAULT NULL COMMENT '误差上限',
  `min_threshold` decimal(14,4) DEFAULT NULL COMMENT '误差下限',
  `critical_quantity` int DEFAULT '0' COMMENT '致命缺陷数量',
  `major_quantity` int DEFAULT '0' COMMENT '严重缺陷数量',
  `minor_quantity` int DEFAULT '0' COMMENT '轻微缺陷数量',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_oqc_id` (`oqc_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=213 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 出货检验单行';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_qc_oqc_line`
--

LOCK TABLES `mes_qc_oqc_line` WRITE;
/*!40000 ALTER TABLE `mes_qc_oqc_line` DISABLE KEYS */;
INSERT INTO `mes_qc_oqc_line` VALUES (200,200,200,NULL,'使用卡尺测量成品总长度',150.0000,206,150.3000,149.7000,0,0,0,'实测值 150.05mm，合格','1','2026-02-18 09:00:00','1','2026-02-18 11:30:00',0x00,1),(201,200,203,NULL,'拍摄外观照片存档',NULL,NULL,NULL,NULL,0,0,0,'外观完好','1','2026-02-18 09:30:00','1','2026-02-18 11:30:00',0x00,1),(202,201,200,NULL,'使用卡尺测量包装盒长度',120.0000,206,120.5000,119.5000,0,1,0,'2号样品实测 120.65mm 超上限','1','2026-02-19 10:00:00','1','2026-02-19 15:00:00',0x00,1),(203,201,203,NULL,'拍摄外观照片',NULL,NULL,NULL,NULL,0,0,1,'3号样品印刷模糊','1','2026-02-19 10:30:00','1','2026-02-19 15:00:00',0x00,1),(204,202,200,NULL,'使用卷尺测量纸箱长度',600.0000,206,605.0000,595.0000,0,0,0,'实测值 601mm，合格','1','2026-02-20 11:00:00','104','2026-02-20 14:00:00',0x00,1),(205,202,205,NULL,'人工清点纸箱数量',NULL,NULL,NULL,NULL,0,0,0,'数量与发货单一致','1','2026-02-20 11:30:00','104','2026-02-20 14:00:00',0x00,1),(206,202,206,NULL,'核对合格证编号',NULL,NULL,NULL,NULL,0,0,1,'1号箱合格证字迹模糊','1','2026-02-20 12:00:00','104','2026-02-20 14:00:00',0x00,1),(207,203,200,NULL,'使用千分尺测量颗粒粒径',3.0000,206,3.5000,2.5000,0,0,0,'','1','2026-02-22 09:00:00','1','2026-02-22 09:00:00',0x00,1),(208,203,205,NULL,'人工清点包装数量',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-02-22 09:00:00','1','2026-02-22 09:00:00',0x00,1),(209,203,206,NULL,'核对合格证编号',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-02-22 09:00:00','1','2026-02-22 09:00:00',0x00,1),(210,204,200,'卡尺','使用千分尺测量长度',200.0000,206,1.0000,-1.0000,0,0,0,'','1','2026-03-27 17:07:22','1','2026-03-27 17:07:22',0x00,1),(211,204,205,NULL,'人工清点数量',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-03-27 17:07:22','1','2026-03-27 17:07:22',0x00,1),(212,204,206,NULL,'核对合格证编号与送检批次一致性',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-03-27 17:07:22','1','2026-03-27 17:07:22',0x00,1);
/*!40000 ALTER TABLE `mes_qc_oqc_line` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_qc_rqc`
--

DROP TABLE IF EXISTS `mes_qc_rqc`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_qc_rqc` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检验单编号',
  `name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检验单名称',
  `template_id` bigint NOT NULL COMMENT '检验模板ID（关联 mes_qc_template）',
  `source_doc_type` tinyint DEFAULT NULL COMMENT '来源单据类型',
  `source_doc_id` bigint DEFAULT NULL COMMENT '来源单据ID',
  `source_line_id` bigint DEFAULT NULL COMMENT '来源单据行ID',
  `source_doc_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源单据编码（冗余）',
  `type` int DEFAULT NULL COMMENT '检验类型',
  `item_id` bigint NOT NULL COMMENT '产品物料ID（关联 mes_md_item）',
  `batch_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `check_quantity` decimal(14,2) DEFAULT NULL COMMENT '检测数量',
  `qualified_quantity` decimal(14,2) DEFAULT '0.00' COMMENT '合格品数量',
  `unqualified_quantity` decimal(14,2) DEFAULT '0.00' COMMENT '不合格数量',
  `critical_rate` decimal(5,2) DEFAULT '0.00' COMMENT '致命缺陷率（%）',
  `major_rate` decimal(5,2) DEFAULT '0.00' COMMENT '严重缺陷率（%）',
  `minor_rate` decimal(5,2) DEFAULT '0.00' COMMENT '轻微缺陷率（%）',
  `critical_quantity` int DEFAULT '0' COMMENT '致命缺陷数量',
  `major_quantity` int DEFAULT '0' COMMENT '严重缺陷数量',
  `minor_quantity` int DEFAULT '0' COMMENT '轻微缺陷数量',
  `check_result` tinyint DEFAULT NULL COMMENT '检测结果（枚举 MesQcCheckResultEnum）',
  `inspect_date` datetime DEFAULT NULL COMMENT '检测日期',
  `inspector_user_id` bigint DEFAULT NULL COMMENT '检测人员用户 ID',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_code` (`code`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=308 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 退货检验单（RQC）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_qc_rqc`
--

LOCK TABLES `mes_qc_rqc` WRITE;
/*!40000 ALTER TABLE `mes_qc_rqc` DISABLE KEYS */;
INSERT INTO `mes_qc_rqc` VALUES (300,'RQC20260220001','螺丝刀刀柄【蓝色】生产退料检验',102,NULL,NULL,NULL,NULL,1,73,'RB20260220-A',50.00,48.00,2.00,0.00,0.00,0.00,0,0,0,1,'2026-02-20 10:00:00',1,4,'退料合格，可重新入库使用','1','2026-02-20 08:00:00','1','2026-02-23 21:16:03',0x00,1),(301,'RQC20260221002','螺丝刀刀头销售退货检验',102,NULL,NULL,NULL,NULL,2,74,'RB20260221-B',30.00,22.00,8.00,0.00,0.00,0.00,0,0,0,2,'2026-02-21 15:00:00',1,4,'不合格，部分产品存在严重缺陷需报废','1','2026-02-21 09:00:00','1','2026-02-23 21:16:03',0x00,1),(302,'RQC20260222003','PVC颗粒生产退料检验',102,NULL,NULL,NULL,NULL,1,70,'RB20260222-C',20.00,18.00,2.00,0.00,0.00,0.00,0,0,0,3,'2026-02-22 14:00:00',104,4,'轻微外观问题，让步接收重新使用','1','2026-02-22 10:00:00','104','2026-02-23 21:16:03',0x00,1),(303,'RQC20260222004','螺丝刀成品【蓝色一字型】销售退货检验',102,NULL,NULL,NULL,NULL,2,75,'RB20260222-D',5.00,2.00,3.00,0.00,0.00,0.00,0,0,0,NULL,NULL,NULL,4,'待检验','1','2026-02-22 09:00:00','1','2026-02-23 21:16:03',0x00,1),(304,'RQCV5CsmJAiJg','3',102,NULL,NULL,NULL,NULL,1,94,'555',1.00,2.00,3.00,0.00,0.00,0.00,0,0,0,2,'1970-01-01 08:00:00',103,0,'','1','2026-02-22 14:49:23','1','2026-02-22 14:49:33',0x00,1),(305,'RQC20260326000002','RITmmKTJb4p0 退货检验单',102,116,7,9,NULL,1,69,NULL,10.00,9.00,1.00,0.00,0.00,0.00,0,0,0,1,'1970-01-01 08:00:00',1,0,'','1','2026-03-26 13:21:00','1','2026-03-26 13:21:00',0x00,1),(306,'RQC20260326000003','RIpV4sw33lVy 退货检验单',102,116,4,6,'RIpV4sw33lVy',2,70,NULL,5.00,1.00,4.00,0.00,0.00,0.00,0,0,0,1,'1970-01-01 08:00:00',1,0,'','1','2026-03-26 21:10:07','1','2026-03-26 21:10:07',0x00,1),(307,'RQC20260326000004','RIpV4sw33lVy 退货检验单',102,116,4,6,'RIpV4sw33lVy',1,70,NULL,5.00,1.00,4.00,0.00,0.00,0.00,0,0,0,1,'1970-01-01 08:00:00',100,0,'','1','2026-03-26 22:38:03','1','2026-03-26 22:38:03',0x00,1);
/*!40000 ALTER TABLE `mes_qc_rqc` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_qc_rqc_line`
--

DROP TABLE IF EXISTS `mes_qc_rqc_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_qc_rqc_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `rqc_id` bigint NOT NULL COMMENT '退货检验单ID（关联 mes_qc_rqc）',
  `indicator_id` bigint NOT NULL COMMENT '检测指标ID（关联 mes_qc_indicator）',
  `tool` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '检测工具',
  `check_method` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '检测方法',
  `standard_value` decimal(14,4) DEFAULT NULL COMMENT '标准值',
  `unit_measure_id` bigint DEFAULT NULL COMMENT '计量单位ID（关联 mes_md_unit_measure）',
  `max_threshold` decimal(14,4) DEFAULT NULL COMMENT '误差上限',
  `min_threshold` decimal(14,4) DEFAULT NULL COMMENT '误差下限',
  `critical_quantity` int DEFAULT '0' COMMENT '致命缺陷数量',
  `major_quantity` int DEFAULT '0' COMMENT '严重缺陷数量',
  `minor_quantity` int DEFAULT '0' COMMENT '轻微缺陷数量',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_rqc_id` (`rqc_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=322 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 退货检验行';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_qc_rqc_line`
--

LOCK TABLES `mes_qc_rqc_line` WRITE;
/*!40000 ALTER TABLE `mes_qc_rqc_line` DISABLE KEYS */;
INSERT INTO `mes_qc_rqc_line` VALUES (300,300,200,NULL,'使用千分尺测量退回刀柄长度',200.0000,206,201.0000,199.0000,0,0,0,'实测值 200.05mm，合格','1','2026-02-20 09:00:00','1','2026-02-20 10:00:00',0x00,1),(301,300,205,NULL,'人工清点退料数量',NULL,NULL,NULL,NULL,0,0,0,'数量与退料单一致','1','2026-02-20 09:30:00','1','2026-02-20 10:00:00',0x00,1),(302,300,206,NULL,'核对退料批次合格证',NULL,NULL,NULL,NULL,0,0,0,'合格证信息完整','1','2026-02-20 09:45:00','1','2026-02-20 10:00:00',0x00,1),(303,301,200,NULL,'使用千分尺测量退回刀头长度',80.0000,206,80.5000,79.5000,0,1,0,'5号样品实测 80.72mm 超上限','1','2026-02-21 10:00:00','1','2026-02-21 15:00:00',0x00,1),(304,301,205,NULL,'人工清点退货数量',NULL,NULL,NULL,NULL,0,0,1,'实际退回数量与退货单差1件','1','2026-02-21 10:30:00','1','2026-02-21 15:00:00',0x00,1),(305,301,206,NULL,'核对退货批次合格证',NULL,NULL,NULL,NULL,0,1,0,'3号样品合格证缺失','1','2026-02-21 11:00:00','1','2026-02-21 15:00:00',0x00,1),(306,302,200,NULL,'使用千分尺测量PVC颗粒粒径',3.0000,206,3.5000,2.5000,0,0,0,'实测值 3.1mm，合格','1','2026-02-22 11:00:00','104','2026-02-22 14:00:00',0x00,1),(307,302,205,NULL,'人工清点退料包装数量',NULL,NULL,NULL,NULL,0,0,1,'1袋包装破损，颗粒轻微溢出','1','2026-02-22 11:30:00','104','2026-02-22 14:00:00',0x00,1),(308,303,200,NULL,'使用千分尺测量退回成品总长度',150.0000,206,151.0000,149.0000,0,0,0,'','1','2026-02-22 09:00:00','1','2026-02-22 09:00:00',0x00,1),(309,303,205,NULL,'人工清点退货数量',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-02-22 09:00:00','1','2026-02-22 09:00:00',0x00,1),(310,304,200,NULL,'使用千分尺测量长度',200.0000,206,1.0000,-1.0000,1,0,0,'','1','2026-02-22 14:49:23','1','2026-02-22 14:49:31',0x00,1),(311,304,205,NULL,'人工清点数量',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-02-22 14:49:23','1','2026-02-22 14:49:31',0x00,1),(312,304,206,NULL,'核对合格证编号与送检批次一致性',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-02-22 14:49:23','1','2026-02-22 14:49:31',0x00,1),(313,305,200,'卡尺','使用千分尺测量长度',200.0000,206,1.0000,-1.0000,0,0,0,'','1','2026-03-26 13:21:00','1','2026-03-26 13:21:00',0x00,1),(314,305,205,NULL,'人工清点数量',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-03-26 13:21:00','1','2026-03-26 13:21:00',0x00,1),(315,305,206,NULL,'核对合格证编号与送检批次一致性',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-03-26 13:21:00','1','2026-03-26 13:21:00',0x00,1),(316,306,200,'卡尺','使用千分尺测量长度',200.0000,206,1.0000,-1.0000,0,0,0,'','1','2026-03-26 21:10:07','1','2026-03-26 21:10:07',0x00,1),(317,306,205,NULL,'人工清点数量',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-03-26 21:10:07','1','2026-03-26 21:10:07',0x00,1),(318,306,206,NULL,'核对合格证编号与送检批次一致性',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-03-26 21:10:07','1','2026-03-26 21:10:07',0x00,1),(319,307,200,'卡尺','使用千分尺测量长度',200.0000,206,1.0000,-1.0000,0,0,0,'','1','2026-03-26 22:38:03','1','2026-03-26 22:38:03',0x00,1),(320,307,205,NULL,'人工清点数量',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-03-26 22:38:03','1','2026-03-26 22:38:03',0x00,1),(321,307,206,NULL,'核对合格证编号与送检批次一致性',NULL,NULL,NULL,NULL,0,0,0,'','1','2026-03-26 22:38:03','1','2026-03-26 22:38:03',0x00,1);
/*!40000 ALTER TABLE `mes_qc_rqc_line` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_qc_template`
--

DROP TABLE IF EXISTS `mes_qc_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_qc_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '方案编号',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '方案名称',
  `types` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检测种类（逗号分隔：IQC,IPQC,OQC,RQC）',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=107 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='质检方案';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_qc_template`
--

LOCK TABLES `mes_qc_template` WRITE;
/*!40000 ALTER TABLE `mes_qc_template` DISABLE KEYS */;
INSERT INTO `mes_qc_template` VALUES (100,'QCT0000000001','IQC 通用来料检验方案','1',0,'适用于常规来料检验','1','2026-02-21 13:35:58','1','2026-02-21 13:35:58',0x00,1),(101,'QCT0000000002','IQC+OQC 综合检验方案','1,3',0,'同时适用于来料检验和出货检验','1','2026-02-21 13:35:58','1','2026-02-21 13:35:58',0x00,1),(102,'QCT0000000003','全流程质检方案','1,2,3,4',0,'覆盖 IQC/IPQC/OQC/RQC 全流程','1','2026-02-21 13:35:58','1','2026-03-23 19:45:52',0x00,1),(105,'QT-IPQC-001','螺丝刀过程检验方案','2',0,'自动新增的过程检验方案','1','2026-03-25 09:34:27','1','2026-03-25 09:34:27',0x00,1),(106,'QCT9VAvjfa2pc','EEE','1,2',0,NULL,'1','2026-04-04 22:20:23','1','2026-04-04 22:20:23',0x00,1);
/*!40000 ALTER TABLE `mes_qc_template` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_qc_template_indicator`
--

DROP TABLE IF EXISTS `mes_qc_template_indicator`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_qc_template_indicator` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `template_id` bigint NOT NULL COMMENT '质检方案ID',
  `indicator_id` bigint NOT NULL COMMENT '质检指标ID（关联 mes_qc_indicator，通过 JOIN 查询指标信息）',
  `check_method` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '检测方法/检测要求',
  `standard_value` decimal(12,4) DEFAULT NULL COMMENT '标准值',
  `unit_measure_id` bigint DEFAULT NULL COMMENT '计量单位ID（关联 mes_md_unit_measure）',
  `threshold_max` decimal(12,4) DEFAULT NULL COMMENT '误差上限',
  `threshold_min` decimal(12,4) DEFAULT NULL COMMENT '误差下限',
  `doc_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明图URL',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=210 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='质检方案-检测指标项';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_qc_template_indicator`
--

LOCK TABLES `mes_qc_template_indicator` WRITE;
/*!40000 ALTER TABLE `mes_qc_template_indicator` DISABLE KEYS */;
INSERT INTO `mes_qc_template_indicator` VALUES (200,100,200,'使用卡尺测量长度，记录实测值',100.0000,206,0.5000,-0.5000,NULL,'','1','2026-02-21 13:35:58','1','2026-02-21 13:35:58',0x00,1),(201,100,201,'使用卡尺测量宽度，记录实测值',50.0000,206,0.3000,-0.3000,NULL,'','1','2026-02-21 13:35:58','1','2026-02-21 13:35:58',0x00,1),(202,100,202,'目视检查颜色是否符合标准色板',NULL,NULL,NULL,NULL,NULL,'','1','2026-02-21 13:35:58','1','2026-02-21 13:35:58',0x00,1),(203,101,200,'使用卡尺测量长度',150.0000,206,0.3000,-0.3000,NULL,'','1','2026-02-21 13:35:58','1','2026-02-21 13:35:58',0x00,1),(204,101,203,'拍摄外观照片存档',NULL,NULL,NULL,NULL,NULL,'','1','2026-02-21 13:35:58','1','2026-02-21 13:35:58',0x00,1),(205,102,200,'使用千分尺测量长度',200.0000,206,1.0000,-1.0000,NULL,'','1','2026-02-21 13:35:58','1','2026-02-21 13:35:58',0x00,1),(206,102,205,'人工清点数量',NULL,NULL,NULL,NULL,NULL,'','1','2026-02-21 13:35:58','1','2026-02-21 13:35:58',0x00,1),(207,102,206,'核对合格证编号与送检批次一致性',NULL,NULL,NULL,NULL,NULL,'','1','2026-02-21 13:35:58','1','2026-02-21 13:35:58',0x00,1),(208,105,200,'游标卡尺测量',15.0000,NULL,0.5000,0.5000,NULL,NULL,'1','2026-03-25 09:34:27','1','2026-03-25 09:34:27',0x00,1),(209,106,205,'4',1.0000,200,2.0000,3.0000,NULL,NULL,'1','2026-05-28 23:56:55','1','2026-05-28 23:57:01',0x01,1);
/*!40000 ALTER TABLE `mes_qc_template_indicator` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_qc_template_item`
--

DROP TABLE IF EXISTS `mes_qc_template_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_qc_template_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `template_id` bigint NOT NULL COMMENT '质检方案ID',
  `item_id` bigint NOT NULL COMMENT '产品物料ID（关联 mes_md_item，通过 ID 查询物料信息）',
  `quantity_check` int NOT NULL DEFAULT '1' COMMENT '最低检测数',
  `quantity_unqualified` int NOT NULL DEFAULT '0' COMMENT '最大不合格数',
  `critical_rate` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '最大致命缺陷率（%）',
  `major_rate` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '最大严重缺陷率（%）',
  `minor_rate` decimal(12,2) NOT NULL DEFAULT '100.00' COMMENT '最大轻微缺陷率（%）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=310 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='质检方案-产品关联';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_qc_template_item`
--

LOCK TABLES `mes_qc_template_item` WRITE;
/*!40000 ALTER TABLE `mes_qc_template_item` DISABLE KEYS */;
INSERT INTO `mes_qc_template_item` VALUES (300,100,73,5,1,0.00,5.00,20.00,'螺丝刀刀柄蓝色','1','2026-02-21 13:35:58','1','2026-02-21 13:35:58',0x00,1),(301,100,74,10,2,0.00,5.00,15.00,'螺丝刀刀头','1','2026-02-21 13:35:58','1','2026-02-21 13:35:58',0x00,1),(302,101,75,5,1,0.00,3.00,10.00,'螺丝刀成品蓝色一字型','1','2026-02-21 13:35:58','1','2026-02-21 13:35:58',0x00,1),(303,101,94,3,0,0.00,0.00,10.00,'小包装盒','1','2026-02-21 13:35:58','1','2026-02-21 13:35:58',0x00,1),(304,102,70,3,0,0.00,0.00,100.00,'PVC颗粒','1','2026-02-21 13:35:58','1','2026-02-21 13:35:58',0x00,1),(305,102,72,5,1,0.00,5.00,20.00,'钢筋','1','2026-02-21 13:35:58','1','2026-02-21 13:35:58',0x00,1),(306,102,95,3,0,0.00,0.00,100.00,'大包装箱','1','2026-02-21 13:35:58','1','2026-02-21 13:35:58',0x00,1),(307,102,69,1,0,0.00,0.00,100.00,NULL,'1','2026-03-23 19:45:51','1','2026-03-23 19:45:51',0x00,1),(308,105,75,5,1,0.00,3.00,10.00,'自动绑定螺丝刀','1','2026-03-25 09:34:27','1','2026-03-25 09:34:27',0x00,1),(309,106,95,1,0,0.00,0.00,100.00,'12332131','1','2026-05-28 23:57:13','1','2026-05-28 23:57:13',0x00,1);
/*!40000 ALTER TABLE `mes_qc_template_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_attachment`
--

DROP TABLE IF EXISTS `mes_set_attachment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `biz_type` varchar(32) NOT NULL COMMENT '业务关联类型：CHECK/LEDGER/MANIFEST/FACILITY/EMERGENCY 等（与签字表同口径）',
  `biz_no` varchar(64) NOT NULL COMMENT '业务关联单号（判定 recordNo / 台账号 / 联单号 / 设施编号 等）',
  `file_name` varchar(255) NOT NULL COMMENT '原始文件名（展示用）',
  `file_url` varchar(512) NOT NULL COMMENT '文件访问地址（infra 文件服务返回）',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_biz` (`biz_type`,`biz_no`)
) ENGINE=InnoDB AUTO_INCREMENT=34 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MES 安全环保检测-通用业务附件';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_attachment`
--

LOCK TABLES `mes_set_attachment` WRITE;
/*!40000 ALTER TABLE `mes_set_attachment` DISABLE KEYS */;
INSERT INTO `mes_set_attachment` VALUES (15,'HAZWASTE_MANIFEST','LD-2026-0910-001','危废转移联单扫描件.pdf','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','随单归档','80370','2026-09-10 20:42:22','80370','2026-09-10 20:42:22',0x00,2010),(16,'HAZWASTE_MANIFEST','LD-2026-0910-002','运输车辆资质.pdf','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','随单归档','80370','2026-09-10 20:42:22','80370','2026-09-10 20:42:22',0x00,2010),(17,'FACILITY_SHUTDOWN','TF-2026-0910-006','换炭作业记录.jpg','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','随单归档','80370','2026-09-10 20:42:22','80370','2026-09-10 20:42:22',0x00,2010),(18,'EMERGENCY','EV-2026-0910-001','现场处置照片.jpg','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','随单归档','80370','2026-09-10 20:42:22','80370','2026-09-10 20:42:22',0x00,2010);
/*!40000 ALTER TABLE `mes_set_attachment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_carbon_emission`
--

DROP TABLE IF EXISTS `mes_set_carbon_emission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_carbon_emission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `calc_no` varchar(64) NOT NULL COMMENT '核算批次号 CARBON-YYYYMM',
  `period_type` varchar(10) NOT NULL COMMENT '核算周期：DAILY/MONTHLY/YEARLY',
  `period_start` date NOT NULL COMMENT '周期开始日期',
  `period_end` date NOT NULL COMMENT '周期结束日期',
  `wo_id` bigint DEFAULT NULL COMMENT '关联工单编号(工单级碳足迹可空)',
  `source_record_id` bigint DEFAULT NULL COMMENT '关联能耗记录编号(能源台账)',
  `energy_type` varchar(20) NOT NULL COMMENT '能源类型：ELECTRICITY/NATURAL_GAS/DIESEL/STEAM',
  `consumption` decimal(15,3) DEFAULT NULL COMMENT '能源消耗量',
  `emission_factor` decimal(10,4) DEFAULT NULL COMMENT '排放因子',
  `carbon_emission` decimal(15,3) DEFAULT NULL COMMENT '碳排放量=consumption*factor',
  `unit` varchar(10) NOT NULL DEFAULT 'tCO2' COMMENT '单位 tCO2',
  `process_emission` decimal(15,3) DEFAULT NULL COMMENT '工艺排放',
  `total_emission` decimal(15,3) DEFAULT NULL COMMENT '合计',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=43 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MES SET-碳排放核算记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_carbon_emission`
--

LOCK TABLES `mes_set_carbon_emission` WRITE;
/*!40000 ALTER TABLE `mes_set_carbon_emission` DISABLE KEYS */;
INSERT INTO `mes_set_carbon_emission` VALUES (1,'CARBON-SMOKE-1','MONTHLY','2026-09-01','2026-09-30',NULL,NULL,'ELECTRICITY',1000.000,0.5810,581.000,'tCO2',NULL,581.000,'1','2026-09-02 15:33:10','1','2026-09-02 15:33:28',0x01,1),(2,'CE-2026-09-001','MONTHLY','2026-09-01','2026-09-30',NULL,NULL,'ELECTRICITY',12500.000,0.5703,7128.750,'tCO2',0.000,7128.750,'80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(3,'CE-2026-09-002','MONTHLY','2026-09-01','2026-09-30',NULL,NULL,'COAL',8.200,1.9003,15.580,'tCO2',0.500,16.080,'80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(38,'CE-2026-0910-001','MONTH','2026-06-01','2026-06-28',NULL,NULL,'ELECTRICITY',128000.000,0.5703,72.998,'tCO2e',0.320,73.318,'80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(39,'CE-2026-0910-002','MONTH','2026-07-01','2026-07-28',NULL,NULL,'ELECTRICITY',119500.000,0.5703,68.151,'tCO2e',0.300,68.451,'80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(40,'CE-2026-0910-003','MONTH','2026-08-01','2026-08-28',NULL,NULL,'ELECTRICITY',134200.000,0.5703,76.534,'tCO2e',0.350,76.884,'80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(41,'CE-2026-0910-004','MONTH','2026-09-01','2026-09-28',NULL,NULL,'ELECTRICITY',96000.000,0.5703,54.749,'tCO2e',0.280,55.029,'80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010);
/*!40000 ALTER TABLE `mes_set_carbon_emission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_chemical_profile`
--

DROP TABLE IF EXISTS `mes_set_chemical_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_chemical_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `profile_no` varchar(64) NOT NULL COMMENT '危化品档案编号',
  `chemical_code` varchar(64) DEFAULT NULL COMMENT '危化品代码（与 mes_set_chemical_safety.chemical_code 对齐）',
  `chemical_name` varchar(200) NOT NULL COMMENT '危化品名称',
  `cas_no` varchar(64) DEFAULT NULL COMMENT 'CAS 号',
  `compat_group` varchar(32) DEFAULT NULL COMMENT '相容组（禁配判定用）：ISOCYANATE/POLYOL/THINNER/EPOXY/WATER/ALCOHOL/AMINE 等',
  `hazard_class` varchar(64) DEFAULT NULL COMMENT '危险类别（如 易燃液体/急性毒性/氧化性物质）',
  `storage_zone` varchar(32) DEFAULT 'GENERAL' COMMENT '储存专区：GENERAL 一般区 / EXPLOSION_PROOF 防爆区 / ISOLATION 隔离区 / SPECIAL 专库',
  `storage_location` varchar(200) DEFAULT NULL COMMENT '具体库位描述',
  `storage_limit` decimal(18,4) DEFAULT NULL COMMENT '储量上限（同库位该物料总量，NULL=不限量）',
  `storage_unit` varchar(16) DEFAULT '吨' COMMENT '储量单位',
  `stock_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '当前存量（stock-in 累加，用于超量预警）',
  `incompatible_groups` varchar(500) DEFAULT NULL COMMENT '额外禁配相容组，逗号分隔（人工补充用，矩阵里已锚定的不必重复填）',
  `explosion_proof` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否必须存放于防爆区',
  `msds_url` varchar(500) DEFAULT NULL COMMENT 'MSDS 文件 URL（前端直传，后端只存 URL）',
  `msds_expire_date` date DEFAULT NULL COMMENT 'MSDS 版本有效期（到期预警）',
  `expire_manage` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否纳入效期管理',
  `shelf_life_days` int DEFAULT NULL COMMENT '保质期天数（配合批次到期日；实际到期冻结在 mes_wm_batch.expireDate 侧）',
  `emergency_measure` varchar(1000) DEFAULT NULL COMMENT '应急措施（泄漏/接触处置）',
  `status` varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED 启用 / DISABLED 停用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  `item_id` bigint DEFAULT NULL COMMENT '关联物料 id（mes_md_item.id）。为空=未绑定：在库侧不判禁配/专区',
  PRIMARY KEY (`id`),
  KEY `idx_chemical_profile_no` (`profile_no`),
  KEY `idx_chemical_profile_code` (`chemical_code`),
  KEY `idx_chemical_profile_location` (`storage_location`(100)),
  KEY `idx_chemical_profile_group` (`compat_group`),
  KEY `idx_item_id` (`item_id`)
) ENGINE=InnoDB AUTO_INCREMENT=147 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='危化品档案（MSDS/禁配/储量/分级储存）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_chemical_profile`
--

LOCK TABLES `mes_set_chemical_profile` WRITE;
/*!40000 ALTER TABLE `mes_set_chemical_profile` DISABLE KEYS */;
INSERT INTO `mes_set_chemical_profile` VALUES (128,'CHM-2026-0910-001','CAS-26471-62-5','甲苯二异氰酸酯','CAS-26471-62-5','ISOCYANATE','易燃液体/急性毒性','EXPLOSION_PROOF','防爆库A区-01',5000.0000,'kg',120.0000,NULL,1,'http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','2027-12-31',1,730,'皮肤接触：脱去污染衣物，大量流动清水冲洗 15min；吸入：迅速移至新鲜空气处，保持呼吸道通畅','ENABLED','库位标签、MSDS、防泄漏托盘三项已核验','80370','2026-09-10 20:42:17','80370','2026-09-20 17:00:46',0x00,2010,NULL),(129,'CHM-2026-0910-002','CAS-101-68-8','二苯基甲烷二异氰酸酯','CAS-101-68-8','ISOCYANATE','急性毒性/刺激','EXPLOSION_PROOF','防爆库A区-02',8000.0000,'kg',120.0000,NULL,1,'http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','2027-12-31',1,730,'泄漏：佩戴防毒面具与橡胶手套，用干沙围堵吸附收集，禁用水冲','ENABLED','库位标签、MSDS、防泄漏托盘三项已核验','80370','2026-09-10 20:42:17','80370','2026-09-10 20:42:17',0x00,2010,NULL),(130,'CHM-2026-0910-003','CAS-25322-69-4','组合聚醚多元醇','CAS-25322-69-4','POLYOL','刺激性','GENERAL','一般库C区-05',10000.0000,'kg',300.0000,NULL,0,'http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','2027-12-31',0,NULL,'皮肤接触：肥皂水冲洗；泄漏：砂土吸收后置于专用容器','ENABLED','库位标签、MSDS、防泄漏托盘三项已核验','80370','2026-09-10 20:42:17','80370','2026-09-10 20:42:17',0x00,2010,NULL),(131,'CHM-2026-0910-004','CAS-75-09-2','二氯甲烷','CAS-75-09-2','THINNER','易燃液体/致癌嫌疑','EXPLOSION_PROOF','防爆库B区-01',3000.0000,'kg',120.0000,NULL,1,'http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','2027-12-31',1,730,'吸入：立即移至新鲜空气处；泄漏：通风并吸附收集，禁止排入下水道','ENABLED','库位标签、MSDS、防泄漏托盘三项已核验','80370','2026-09-10 20:42:17','80370','2026-09-10 20:42:17',0x00,2010,NULL),(132,'CHM-2026-0910-005','CAS-141-78-6','乙酸乙酯','CAS-141-78-6','THINNER','易燃液体/刺激','EXPLOSION_PROOF','防爆库B区-02',4000.0000,'kg',120.0000,NULL,1,'http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','2027-12-31',1,730,'灭火：抗溶性泡沫或干粉；泄漏：干沙吸附','ENABLED','库位标签、MSDS、防泄漏托盘三项已核验','80370','2026-09-10 20:42:17','80370','2026-09-20 17:00:47',0x00,2010,NULL),(133,'CHM-2026-0910-006','CAS-25068-38-6','环氧树脂 E-51','CAS-25068-38-6','EPOXY','刺激性/致敏','GENERAL','一般库C区-08',6000.0000,'kg',300.0000,'AMINE,WATER',0,'http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','2027-12-31',0,NULL,'皮肤接触：丙酮擦拭后清水冲洗；避免与胺类混放','ENABLED','库位标签、MSDS、防泄漏托盘三项已核验','80370','2026-09-10 20:42:17','80370','2026-09-10 20:42:17',0x00,2010,NULL),(134,'CHM-2026-0910-007','CAS-107-15-3','乙二胺','CAS-107-15-3','AMINE','易燃液体/腐蚀','ISOLATION','隔离库D区-01',1000.0000,'kg',0.0000,NULL,1,'http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','2027-12-31',1,730,'强腐蚀强刺激：立即脱去污染衣物，大量清水冲洗并就医；严禁与环氧树脂同库','ENABLED','库位标签、MSDS、防泄漏托盘三项已核验','80370','2026-09-10 20:42:17','80370','2026-09-10 20:42:17',0x00,2010,NULL),(135,'CHM-2026-0910-008','CAS-64-17-5','乙醇','CAS-64-17-5','ALCOHOL','易燃液体','EXPLOSION_PROOF','防爆库B区-03',5000.0000,'kg',0.0000,NULL,1,'http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','2027-12-31',1,730,'灭火：抗溶性泡沫；泄漏：通风稀释，严禁明火','ENABLED','库位标签、MSDS、防泄漏托盘三项已核验','80370','2026-09-10 20:42:17','80370','2026-09-10 20:42:17',0x00,2010,NULL),(136,'CHM-2026-0910-009','CAS-7664-93-9','浓硫酸','CAS-7664-93-9','WATER','腐蚀性/氧化性','ISOLATION','隔离库D区-03',2000.0000,'kg',0.0000,NULL,1,'http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','2027-12-31',0,NULL,'皮肤接触：立即脱去衣物，大量流动清水冲洗 20min 后就医；泄漏：勿直接用水，先用干沙围堵','ENABLED','库位标签、MSDS、防泄漏托盘三项已核验','80370','2026-09-10 20:42:17','80370','2026-09-10 20:42:17',0x00,2010,NULL),(137,'CHM-2026-0910-010','CAS-1310-73-2','氢氧化钠','CAS-1310-73-2','WATER','腐蚀性','ISOLATION','隔离库D区-04',2000.0000,'kg',0.0000,NULL,0,'http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','2027-12-31',0,NULL,'接触：大量清水冲洗；泄漏：干沙收集，勿与酸类同库','ENABLED','库位标签、MSDS、防泄漏托盘三项已核验','80370','2026-09-10 20:42:17','80370','2026-09-20 17:00:45',0x00,2010,NULL);
/*!40000 ALTER TABLE `mes_set_chemical_profile` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_chemical_safety`
--

DROP TABLE IF EXISTS `mes_set_chemical_safety`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_chemical_safety` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `record_no` varchar(64) NOT NULL COMMENT '记录编号',
  `plan_id` bigint DEFAULT NULL COMMENT '关联检测计划编号',
  `chemical_code` varchar(64) DEFAULT NULL COMMENT '危化品编码',
  `chemical_name` varchar(200) NOT NULL COMMENT '危化品名称',
  `storage_location` varchar(200) DEFAULT NULL COMMENT '存储地点',
  `label_ok` tinyint(1) NOT NULL DEFAULT '1' COMMENT '标识完整性：1是/0否',
  `msds_ok` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'MSDS有效性：1是/0否',
  `storage_ok` tinyint(1) NOT NULL DEFAULT '1' COMMENT '储存条件(温湿度/通风)合格：1是/0否',
  `separation_ok` tinyint(1) NOT NULL DEFAULT '1' COMMENT '禁忌物分离合格：1是/0否',
  `result` varchar(8) DEFAULT NULL COMMENT '结果：PASS/FAIL',
  `problem_desc` varchar(500) DEFAULT NULL COMMENT '异常/不合格描述',
  `inspector` varchar(64) DEFAULT NULL COMMENT '检测人',
  `inspect_time` datetime NOT NULL COMMENT '检测时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=51 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全环保检测-危化品安全检测记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_chemical_safety`
--

LOCK TABLES `mes_set_chemical_safety` WRITE;
/*!40000 ALTER TABLE `mes_set_chemical_safety` DISABLE KEYS */;
INSERT INTO `mes_set_chemical_safety` VALUES (1,'CS-0001',NULL,'CAS-64-17-5','乙醇','危化库A',1,1,1,1,'PASS',NULL,'张三','2026-09-02 10:00:00','P0冒烟','1','2026-09-02 14:36:43','1','2026-09-10 16:11:35',0x00,1),(2,'CHM-2026-0901-001',NULL,'CAS-75-07-0','乙醛','危化品库A区-01',1,1,1,1,'PASS',NULL,'刘洋','2026-09-01 10:20:00','巡检正常','80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(3,'CHM-2026-0901-002',NULL,'CAS-7664-93-9','浓硫酸','危化品库B区-03',0,1,1,1,'FAIL','试剂瓶标签破损','刘洋','2026-09-01 11:05:00',NULL,'80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(44,'CHS-2026-0910-001',NULL,'CAS-26471-62-5','甲苯二异氰酸酯','危化品库A区-01',1,1,1,1,'PASS',NULL,'刘洋','2026-09-02 10:00:00','已限期 3 日整改并复验','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(45,'CHS-2026-0910-002',NULL,'CAS-101-68-8','二苯基甲烷二异氰酸酯','危化品库A区-02',1,1,1,1,'PASS',NULL,'刘洋','2026-09-04 11:00:00','已限期 3 日整改并复验','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(46,'CHS-2026-0910-003',NULL,'CAS-25068-38-6','环氧树脂 E-51','一般库C区-08',1,1,1,0,'FAIL','环氧树脂与胺类未有效隔离','刘洋','2026-09-06 12:00:00','已限期 3 日整改并复验','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(47,'CHS-2026-0910-004',NULL,'CAS-107-15-3','乙二胺','隔离库D区-01',1,1,1,1,'PASS',NULL,'刘洋','2026-09-08 13:00:00','已限期 3 日整改并复验','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(48,'CHS-2026-0910-005',NULL,'CAS-64-17-5','乙醇','防爆库B区-03',1,1,1,1,'PASS',NULL,'刘洋','2026-09-09 10:00:00','已限期 3 日整改并复验','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(49,'CHS-2026-0910-006',NULL,'CAS-7664-93-9','浓硫酸','隔离库D区-03',1,0,1,1,'FAIL','MSDS 版本过期未更新','刘洋','2026-09-10 11:00:00','已限期 3 日整改并复验','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010);
/*!40000 ALTER TABLE `mes_set_chemical_safety` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_dust_record`
--

DROP TABLE IF EXISTS `mes_set_dust_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_dust_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `record_no` varchar(64) NOT NULL COMMENT '记录编号 DUST-YYYYMMDD-NNN',
  `plan_id` bigint DEFAULT NULL COMMENT '关联检测计划编号',
  `wo_id` bigint DEFAULT NULL COMMENT '关联工单编号',
  `operation_id` bigint DEFAULT NULL COMMENT '关联工序编号',
  `device_id` bigint DEFAULT NULL COMMENT '关联设备编号(除尘/产尘设备)',
  `location` varchar(200) DEFAULT '' COMMENT '检测位置/作业区域',
  `dust_type` varchar(16) NOT NULL COMMENT '检测参数：TOTAL_DUST/RESPIRABLE_DUST/SIO2',
  `concentration` decimal(10,3) DEFAULT NULL COMMENT '检测浓度/含量',
  `sio2_content` decimal(5,2) DEFAULT NULL COMMENT '游离SiO2含量%(总尘且需矽尘分级时)',
  `unit` varchar(16) DEFAULT '' COMMENT '单位 mg/m3/%',
  `limit_value` decimal(10,3) DEFAULT NULL COMMENT '限值',
  `ref_standard` varchar(100) DEFAULT '' COMMENT '引用国标',
  `result` varchar(8) DEFAULT NULL COMMENT '结果：PASS/FAIL',
  `collection_mode` varchar(16) DEFAULT 'MANUAL' COMMENT '采集方式：IOT_AUTO/MANUAL',
  `instrument_no` varchar(64) DEFAULT '' COMMENT '检测仪器编号',
  `inspector` varchar(64) DEFAULT '' COMMENT '检测人',
  `inspect_time` datetime NOT NULL COMMENT '检测时间',
  `photo_urls` varchar(2000) DEFAULT NULL COMMENT '检测照片URL(逗号分隔)',
  `remark` varchar(500) DEFAULT '' COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_record_no` (`record_no`)
) ENGINE=InnoDB AUTO_INCREMENT=54 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MES SET-粉尘浓度检测记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_dust_record`
--

LOCK TABLES `mes_set_dust_record` WRITE;
/*!40000 ALTER TABLE `mes_set_dust_record` DISABLE KEYS */;
INSERT INTO `mes_set_dust_record` VALUES (1,'DUST-SMOKE-1',NULL,NULL,NULL,NULL,'抛丸A','TOTAL_DUST',1.200,NULL,'mg/m3',NULL,'','PASS','MANUAL','','','2026-09-02 11:00:00',NULL,'','1','2026-09-02 15:33:08','1','2026-09-10 16:11:35',0x01,1),(2,'DUST-2026-0901-001',NULL,NULL,NULL,1,'一号车间打磨区','粉尘',3.200,12.00,'mg/m3',4.000,'GBZ 2.1-2019','PASS','MANUAL','DM-001','刘洋','2026-09-01 09:40:00',NULL,'','80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(3,'DUST-2026-0901-002',NULL,NULL,NULL,2,'木工车间锯切工位','木粉尘',1.800,0.00,'mg/m3',3.000,'GBZ 2.1-2019','PASS','MANUAL','DM-002','刘洋','2026-09-01 14:15:00',NULL,'','80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(47,'DUST-2026-0910-001',NULL,NULL,NULL,NULL,'喷涂线-1 调漆间','TOTAL_DUST',1.800,12.50,'mg/m³',120.000,'GBZ 2.1-2019','PASS','布袋除尘+局部排风','DUST-A01','陈静','2026-09-02 09:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','','80370','2026-09-10 20:42:19','80370','2026-09-10 20:42:19',0x00,2010),(48,'DUST-2026-0910-002',NULL,NULL,NULL,NULL,'抛丸打磨工位','TOTAL_DUST',4.200,12.50,'mg/m³',120.000,'GBZ 2.1-2019','PASS','布袋除尘+局部排风','DUST-A02','陈静','2026-09-04 10:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','','80370','2026-09-10 20:42:19','80370','2026-09-10 20:42:19',0x00,2010),(49,'DUST-2026-0910-003',NULL,NULL,NULL,NULL,'木工开料区','木粉尘',145.000,12.50,'mg/m³',120.000,'GBZ 2.1-2019','FAIL','布袋除尘+局部排风','DUST-A03','陈静','2026-09-06 11:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','超标点位已下达整改，加装移动式除尘','80370','2026-09-10 20:42:19','80370','2026-09-10 20:42:19',0x00,2010),(50,'DUST-2026-0910-004',NULL,NULL,NULL,NULL,'搅拌配料区','TOTAL_DUST',2.600,12.50,'mg/m³',120.000,'GBZ 2.1-2019','PASS','布袋除尘+局部排风','DUST-A04','陈静','2026-09-08 12:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','','80370','2026-09-10 20:42:19','80370','2026-09-10 20:42:19',0x00,2010),(51,'DUST-2026-0910-005',NULL,NULL,NULL,NULL,'喷涂线-2 调漆间','TOTAL_DUST',1.500,12.50,'mg/m³',120.000,'GBZ 2.1-2019','PASS','布袋除尘+局部排风','DUST-A05','陈静','2026-09-09 09:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','','80370','2026-09-10 20:42:19','80370','2026-09-10 20:42:19',0x00,2010),(52,'DUST-2026-0910-006',NULL,NULL,NULL,NULL,'包装码垛区','TOTAL_DUST',0.900,12.50,'mg/m³',120.000,'GBZ 2.1-2019','PASS','布袋除尘+局部排风','DUST-A06','陈静','2026-09-10 10:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','','80370','2026-09-10 20:42:19','80370','2026-09-10 20:42:19',0x00,2010);
/*!40000 ALTER TABLE `mes_set_dust_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_electrical_record`
--

DROP TABLE IF EXISTS `mes_set_electrical_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_electrical_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `record_no` varchar(64) NOT NULL COMMENT '记录编号 ELEC-YYYYMMDD-NNN',
  `plan_id` bigint DEFAULT NULL COMMENT '关联检测计划编号',
  `device_id` bigint NOT NULL COMMENT '关联设备/配电设施编号',
  `location` varchar(200) DEFAULT '' COMMENT '检测位置(配电柜/线路区域)',
  `check_item` varchar(32) NOT NULL COMMENT '检测项目：INSULATION_RESISTANCE/GROUND_RESISTANCE/LEAKAGE_ACTION_CURRENT/LEAKAGE_ACTION_TIME/WITHSTAND_VOLTAGE',
  `measured_value` decimal(12,3) DEFAULT NULL COMMENT '实测值',
  `unit` varchar(16) DEFAULT '' COMMENT '单位 MΩ/Ω/mA/ms/V',
  `limit_value` decimal(12,3) DEFAULT NULL COMMENT '标准限值',
  `result` varchar(8) DEFAULT NULL COMMENT '结果：PASS/FAIL',
  `instrument_no` varchar(64) DEFAULT '' COMMENT '检测仪器编号',
  `instrument_calib_ok` tinyint(1) DEFAULT NULL COMMENT '仪器校准状态快照：1已校准/0未校准',
  `inspector` varchar(64) DEFAULT '' COMMENT '检测人',
  `inspect_time` datetime NOT NULL COMMENT '检测时间',
  `photo_urls` varchar(2000) DEFAULT NULL COMMENT '检测照片URL(逗号分隔)',
  `remark` varchar(500) DEFAULT '' COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_record_no` (`record_no`)
) ENGINE=InnoDB AUTO_INCREMENT=48 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MES SET-电气安全检测记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_electrical_record`
--

LOCK TABLES `mes_set_electrical_record` WRITE;
/*!40000 ALTER TABLE `mes_set_electrical_record` DISABLE KEYS */;
INSERT INTO `mes_set_electrical_record` VALUES (1,'ELEC-SMOKE-1',NULL,1,'配电房','INSULATION_RESISTANCE',500.000,'',NULL,'PASS','',NULL,'','2026-09-02 11:00:00',NULL,'','1','2026-09-02 15:33:08','1','2026-09-10 16:11:35',0x01,1),(2,'ELE-2026-0901-001',NULL,1,'一号车间配电柜P1','接地电阻',0.850,'Ω',4.000,'PASS','EM-001',1,'刘洋','2026-09-01 09:00:00',NULL,'','80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(3,'ELE-2026-0901-002',NULL,2,'空压机房配电箱P2','绝缘电阻',500.000,'MΩ',1000.000,'PASS','EM-002',1,'刘洋','2026-09-01 15:30:00',NULL,'','80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(41,'ELE-2026-0910-001',NULL,1,'车间 1 号工位','接地电阻',3.200,'Ω',4.000,'PASS','ELEC-3001',1,'黄涛','2026-09-02 09:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','','80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(42,'ELE-2026-0910-002',NULL,2,'车间 2 号工位','绝缘电阻',0.600,'MΩ',0.500,'FAIL','ELEC-3002',1,'黄涛','2026-09-04 10:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','已更换绝缘老化电缆并复测合格','80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(43,'ELE-2026-0910-003',NULL,3,'车间 3 号工位','漏电保护动作电流',26.000,'mA',30.000,'PASS','ELEC-3003',1,'黄涛','2026-09-06 11:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','','80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(44,'ELE-2026-0910-004',NULL,4,'车间 4 号工位','漏电保护动作时间',0.080,'s',0.100,'PASS','ELEC-3004',1,'黄涛','2026-09-08 12:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','','80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(45,'ELE-2026-0910-005',NULL,5,'车间 5 号工位','接地电阻',2.800,'Ω',4.000,'PASS','ELEC-3005',1,'黄涛','2026-09-09 09:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','','80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(46,'ELE-2026-0910-006',NULL,6,'车间 6 号工位','绝缘电阻',1.200,'MΩ',0.500,'PASS','ELEC-3006',1,'黄涛','2026-09-10 10:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','','80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010);
/*!40000 ALTER TABLE `mes_set_electrical_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_emergency_drill`
--

DROP TABLE IF EXISTS `mes_set_emergency_drill`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_emergency_drill` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `drill_no` varchar(64) NOT NULL COMMENT '演练编号',
  `drill_name` varchar(128) NOT NULL COMMENT '演练名称',
  `plan_id` bigint DEFAULT NULL COMMENT '关联预案编号（须为已发布/已备案的预案）',
  `plan_version` varchar(32) DEFAULT NULL COMMENT '演练时的预案版本快照',
  `drill_type` varchar(32) NOT NULL COMMENT '演练类型：COMPREHENSIVE(综合)/SPECIAL(专项)/ONSITE(现场处置)',
  `drill_date` date NOT NULL COMMENT '演练日期',
  `participant_count` int DEFAULT NULL COMMENT '参加人数',
  `participants` varchar(500) DEFAULT NULL COMMENT '参加人员',
  `sign_sheet_url` varchar(255) DEFAULT NULL COMMENT '签到表附件',
  `photo_url` varchar(255) DEFAULT NULL COMMENT '演练照片',
  `video_url` varchar(255) DEFAULT NULL COMMENT '演练视频',
  `evaluation` varchar(500) DEFAULT NULL COMMENT '演练评估',
  `rectify_requirement` varchar(500) DEFAULT NULL COMMENT '整改要求（填写后整改状态转 PENDING）',
  `rectify_status` varchar(32) DEFAULT NULL COMMENT '整改状态：NONE(无需整改)/PENDING(待整改)/DONE(已整改)',
  `rectify_done_date` date DEFAULT NULL COMMENT '整改完成日期',
  `closed_date` date DEFAULT NULL COMMENT '闭环日期',
  `status` varchar(32) NOT NULL DEFAULT 'PLANNED' COMMENT '状态：PLANNED(已计划)/DONE(已演练)/CLOSED(已闭环)',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_drill_no` (`drill_no`),
  KEY `idx_drill_date` (`drill_date`),
  KEY `idx_plan_id` (`plan_id`)
) ENGINE=InnoDB AUTO_INCREMENT=36 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全环保检测-应急演练';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_emergency_drill`
--

LOCK TABLES `mes_set_emergency_drill` WRITE;
/*!40000 ALTER TABLE `mes_set_emergency_drill` DISABLE KEYS */;
INSERT INTO `mes_set_emergency_drill` VALUES (31,'YL-2026-0910-001','异氰酸酯泄漏综合应急演练',56,'A/2','COMPREHENSIVE','2026-06-18',42,'环保专员、车间班组长、应急处置组、门卫','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png',NULL,'响应及时，围堵吸附到位；改进：应急物资库与泄漏点距离偏远',NULL,'NONE',NULL,'2026-09-10','CLOSED','年度演练计划内','80370','2026-09-10 20:42:18','80370','2026-09-10 20:42:18',0x00,2010),(32,'YL-2026-0910-002','危险化学品泄漏专项演练',57,'B/1','SPECIAL','2026-07-22',28,'环保专员、车间班组长、应急处置组、门卫','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png',NULL,'防化服穿戴用时 4min，达标；改进：洗眼器水压偏低需检修','2026-08-10 前完成洗眼器检修并复测水压','PENDING',NULL,NULL,'DONE','年度演练计划内','80370','2026-09-10 20:42:18','80370','2026-09-10 20:42:18',0x00,2010),(33,'YL-2026-0910-003','火灾疏散现场处置演练',59,'B/2','ONSITE','2026-08-15',96,'环保专员、车间班组长、应急处置组、门卫','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png',NULL,'3min 内全员疏散至集合点，达标',NULL,'NONE',NULL,'2026-09-10','CLOSED','年度演练计划内','80370','2026-09-10 20:42:18','80370','2026-09-10 20:42:18',0x00,2010),(34,'YL-2026-0910-004','突发环境事件应急演练',60,'B/1','SPECIAL','2026-09-05',35,'环保专员、车间班组长、应急处置组、门卫','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png',NULL,'废水异常排放拦截有效；改进：应急池液位计需校验',NULL,'NONE',NULL,'2026-09-10','CLOSED','年度演练计划内','80370','2026-09-10 20:42:18','80370','2026-09-10 20:42:18',0x00,2010);
/*!40000 ALTER TABLE `mes_set_emergency_drill` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_emergency_event`
--

DROP TABLE IF EXISTS `mes_set_emergency_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_emergency_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `event_no` varchar(64) NOT NULL COMMENT '事件编号',
  `event_type` varchar(32) NOT NULL COMMENT '事件类型：LEAK(泄漏)/EXCEED(超标)/FACILITY_FAULT(设施故障)/OTHER(其他)',
  `scenario` varchar(32) DEFAULT NULL COMMENT '泄漏场景（对应处置卡）：MDI_LEAK/THINNER_LEAK/WASTE_OIL_LEAK',
  `occur_time` datetime DEFAULT NULL COMMENT '发生时间',
  `location` varchar(128) DEFAULT NULL COMMENT '发生地点',
  `chemical_code` varchar(64) DEFAULT NULL COMMENT '涉事化学品编码（关联危化品档案取 MSDS/禁配）',
  `leak_quantity` decimal(12,3) DEFAULT NULL COMMENT '泄漏量',
  `impact_scope` varchar(255) DEFAULT NULL COMMENT '影响范围',
  `report_user` varchar(64) DEFAULT NULL COMMENT '上报人',
  `report_time` datetime DEFAULT NULL COMMENT '上报时间（PDA 一键上报）',
  `handler` varchar(64) DEFAULT NULL COMMENT '处置人（派发时指定）',
  `dispatch_time` datetime DEFAULT NULL COMMENT '派发时间',
  `dispose_note` varchar(500) DEFAULT NULL COMMENT '处置说明',
  `dispose_photo_url` varchar(255) DEFAULT NULL COMMENT '处置照片',
  `waste_count` int DEFAULT NULL COMMENT '应急废物桶数（处置时按明细回填，非人工填写）',
  `waste_quantity` decimal(12,3) DEFAULT NULL COMMENT '应急废物合计净重 kg（处置时按明细回填，非人工填写）',
  `report_content` varchar(1000) DEFAULT NULL COMMENT '事件报告（原因/数量/处置/整改）',
  `approver` varchar(64) DEFAULT NULL COMMENT '负责人',
  `approve_time` datetime DEFAULT NULL COMMENT '签字时间',
  `closed_time` datetime DEFAULT NULL COMMENT '闭环时间',
  `status` varchar(32) NOT NULL DEFAULT 'REPORTED' COMMENT '状态：REPORTED(已上报)/DISPOSING(处置中)/PENDING_REPORT(待报告)/CLOSED(已闭环)',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_event_no` (`event_no`),
  KEY `idx_status` (`status`),
  KEY `idx_occur_time` (`occur_time`)
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全环保检测-应急事件';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_emergency_event`
--

LOCK TABLES `mes_set_emergency_event` WRITE;
/*!40000 ALTER TABLE `mes_set_emergency_event` DISABLE KEYS */;
INSERT INTO `mes_set_emergency_event` VALUES (22,'EV-2026-0910-001','LEAK','THINNER_LEAK','2026-09-10 14:00:00','喷涂线-1 调漆间','CAS-75-09-2',3.200,'车间局部，未出厂界','刘洋','2026-09-10 20:42:22','吴斌','2026-09-10 20:42:22','干沙围堵后收集，未用水冲；现场已通风并检测合格','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png',1,4.200,'起因：调漆时稀释剂桶倾倒约 3.2kg；处置：沙土围堵吸附，1 桶过秤 4.2kg 入危废台账；无人员伤害；整改：桶架加装防倒挡块','刘洋','2026-09-10 20:42:22','2026-09-10 20:42:22','CLOSED','PDA 一键上报','80370','2026-09-10 20:42:22','80370','2026-09-10 20:42:22',0x00,2010),(23,'EV-2026-0910-002','LEAK','WASTE_OIL_LEAK','2026-09-10 15:00:00','设备维修区',NULL,1.600,'车间局部，未出厂界','刘洋','2026-09-10 20:42:22','吴斌','2026-09-10 20:42:22','干沙围堵后收集，未用水冲；现场已通风并检测合格','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png',1,5.200,NULL,NULL,NULL,NULL,'PENDING_REPORT','PDA 一键上报','80370','2026-09-10 20:42:22','80370','2026-09-10 20:42:22',0x00,2010);
/*!40000 ALTER TABLE `mes_set_emergency_event` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_emergency_material`
--

DROP TABLE IF EXISTS `mes_set_emergency_material`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_emergency_material` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `material_no` varchar(64) NOT NULL COMMENT '物资编号',
  `material_name` varchar(128) NOT NULL COMMENT '物资名称',
  `material_type` varchar(32) NOT NULL COMMENT '物资类型：DRY_SAND(干沙)/OIL_ABSORBENT(吸油毡)/CHEM_SUIT(防化服)/GAS_MASK(防毒面具)/EXPLOSION_TOOL(防爆工具)/SANDBAG(围堰沙袋)/EYE_WASH(洗眼器)/DRY_POWDER(干粉灭火器)',
  `spec` varchar(64) DEFAULT NULL COMMENT '规格型号',
  `unit` varchar(16) DEFAULT NULL COMMENT '计量单位',
  `quantity` decimal(12,3) DEFAULT NULL COMMENT '在库数量',
  `storage_location` varchar(128) DEFAULT NULL COMMENT '定点存放位置',
  `produce_date` date DEFAULT NULL COMMENT '生产日期',
  `expire_date` date DEFAULT NULL COMMENT '有效期至（临期预警依据）',
  `last_check_date` date DEFAULT NULL COMMENT '最近检查日期',
  `status` varchar(32) DEFAULT NULL COMMENT '状态：NORMAL(正常)/EXPIRING(临期)/EXPIRED(过期)/OUT(缺货)，派生值不进表单',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_material_no` (`material_no`),
  KEY `idx_material_type` (`material_type`),
  KEY `idx_expire_date` (`expire_date`)
) ENGINE=InnoDB AUTO_INCREMENT=115 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全环保检测-应急物资';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_emergency_material`
--

LOCK TABLES `mes_set_emergency_material` WRITE;
/*!40000 ALTER TABLE `mes_set_emergency_material` DISABLE KEYS */;
INSERT INTO `mes_set_emergency_material` VALUES (97,'WZ-2026-0910-001','干沙','DRY_SAND','50kg/袋','袋',40.000,'应急物资库-01','2026-01-10','2028-01-09','2026-09-01','NORMAL','定点存放，标识清晰','80370','2026-09-10 20:42:18','80370','2026-09-10 20:42:18',0x00,2010),(98,'WZ-2026-0910-002','吸油毡','OIL_ABSORBENT','400×500mm','包',60.000,'应急物资库-02','2026-02-01','2028-01-31','2026-09-01','NORMAL','定点存放，标识清晰','80370','2026-09-10 20:42:18','80370','2026-09-10 20:42:18',0x00,2010),(99,'WZ-2026-0910-003','防化服','CHEM_SUIT','L 码 全封闭','套',12.000,'应急物资柜-A','2025-06-01','2028-05-31','2026-09-01','NORMAL','定点存放，标识清晰','80370','2026-09-10 20:42:18','80370','2026-09-10 20:42:18',0x00,2010),(100,'WZ-2026-0910-004','防毒面具','GAS_MASK','半面罩 双滤盒','个',30.000,'应急物资柜-A','2025-09-01','2026-09-30','2026-09-01','EXPIRING','定点存放，标识清晰','80370','2026-09-10 20:42:18','80370','2026-09-10 20:42:18',0x00,2010),(101,'WZ-2026-0910-005','防爆工具组','EXPLOSION_TOOL','铜合金 12 件','套',4.000,'应急物资柜-B','2024-05-01','2029-04-30','2026-09-01','NORMAL','定点存放，标识清晰','80370','2026-09-10 20:42:18','80370','2026-09-10 20:42:18',0x00,2010),(102,'WZ-2026-0910-006','围堰沙袋','SANDBAG','40×60cm','条',200.000,'应急物资库-03','2026-03-01','2029-02-28','2026-09-01','NORMAL','定点存放，标识清晰','80370','2026-09-10 20:42:18','80370','2026-09-10 20:42:18',0x00,2010),(103,'WZ-2026-0910-007','洗眼器','EYE_WASH','紧急冲淋复合式','台',6.000,'各车间出入口','2025-01-01','2030-01-01','2026-09-01','NORMAL','定点存放，标识清晰','80370','2026-09-10 20:42:18','80370','2026-09-10 20:42:18',0x00,2010),(104,'WZ-2026-0910-008','干粉灭火器','DRY_POWDER','MFZ/ABC4 4kg','具',80.000,'各消防点位','2025-04-01','2027-03-31','2026-09-01','NORMAL','定点存放，标识清晰','80370','2026-09-10 20:42:18','80370','2026-09-10 20:42:18',0x00,2010),(105,'WZ-2026-0910-009','吸油拖栏','OIL_ABSORBENT','Φ200×3000mm','条',20.000,'应急物资库-02','2026-02-01','2028-01-31','2026-09-01','NORMAL','定点存放，标识清晰','80370','2026-09-10 20:42:18','80370','2026-09-10 20:42:18',0x00,2010),(106,'WZ-2026-0910-010','正压式空气呼吸器','GAS_MASK','6.8L 碳纤维瓶','套',8.000,'应急物资柜-B','2024-08-01','2029-07-31','2026-09-01','NORMAL','定点存放，标识清晰','80370','2026-09-10 20:42:18','80370','2026-09-10 20:42:18',0x00,2010),(107,'WZ-2026-0910-011','推车式干粉灭火器','DRY_POWDER','MFT/ABC35 35kg','台',6.000,'各消防点位','2025-11-01','2028-10-31','2026-09-01','NORMAL','定点存放，标识清晰','80370','2026-09-10 20:42:18','80370','2026-09-10 20:42:18',0x00,2010),(108,'WZ-2026-0910-012','中和吸附剂','OIL_ABSORBENT','酸碱两用 25kg','袋',25.000,'应急物资库-03','2024-05-01','2026-08-20','2026-09-01','EXPIRED','定点存放，标识清晰','80370','2026-09-10 20:42:18','80370','2026-09-10 20:42:18',0x00,2010);
/*!40000 ALTER TABLE `mes_set_emergency_material` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_emergency_plan`
--

DROP TABLE IF EXISTS `mes_set_emergency_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_emergency_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `plan_no` varchar(64) NOT NULL COMMENT '预案编号',
  `plan_name` varchar(128) NOT NULL COMMENT '预案名称',
  `plan_type` varchar(32) NOT NULL COMMENT '预案类型：COMPREHENSIVE(综合)/SPECIAL(专项)/ONSITE(现场处置)',
  `version` varchar(32) DEFAULT NULL COMMENT '版本号',
  `publish_date` date DEFAULT NULL COMMENT '发布日期（备案时限与评估周期的起算点）',
  `filing_deadline` date DEFAULT NULL COMMENT '备案截止日（发布 + 20 个工作日，派生值不进表单）',
  `filing_no` varchar(64) DEFAULT NULL COMMENT '备案号（报生态环境部门后登记）',
  `filing_date` date DEFAULT NULL COMMENT '备案日期',
  `attach_url` varchar(255) DEFAULT NULL COMMENT '预案附件地址',
  `last_review_date` date DEFAULT NULL COMMENT '上次评估修订日期',
  `next_review_date` date DEFAULT NULL COMMENT '下次评估修订日期（发布或上次修订 + 3 年，派生值不进表单）',
  `review_reason` varchar(255) DEFAULT NULL COMMENT '修订原因（工艺/物料/法规变化）',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT(草稿)/PUBLISHED(已发布)/FILED(已备案)',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_plan_no` (`plan_no`),
  KEY `idx_status` (`status`),
  KEY `idx_filing_deadline` (`filing_deadline`),
  KEY `idx_next_review` (`next_review_date`)
) ENGINE=InnoDB AUTO_INCREMENT=64 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全环保检测-应急预案';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_emergency_plan`
--

LOCK TABLES `mes_set_emergency_plan` WRITE;
/*!40000 ALTER TABLE `mes_set_emergency_plan` DISABLE KEYS */;
INSERT INTO `mes_set_emergency_plan` VALUES (56,'YA-2026-0910-001','生产安全事故综合应急预案','COMPREHENSIVE','A/2','2026-03-10','2026-04-07','110108-YA-2026-0101','2026-04-20','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png',NULL,'2029-03-10',NULL,'FILED','依据 GB/T 29639-2020 编制','80370','2026-09-10 20:42:18','80370','2026-09-10 20:42:18',0x00,2010),(57,'YA-2026-0910-002','危险化学品泄漏专项应急预案','SPECIAL','B/1','2026-04-11','2026-05-08','110108-YA-2026-0102','2026-05-21','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png',NULL,'2029-04-11',NULL,'FILED','依据 GB/T 29639-2020 编制','80370','2026-09-10 20:42:18','80370','2026-09-10 20:42:18',0x00,2010),(58,'YA-2026-0910-003','异氰酸酯泄漏现场处置方案','ONSITE','C/1','2026-05-12','2026-06-09','110108-YA-2026-0103','2026-06-22','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png',NULL,'2029-05-12',NULL,'FILED','依据 GB/T 29639-2020 编制','80370','2026-09-10 20:42:18','80370','2026-09-10 20:42:18',0x00,2010),(59,'YA-2026-0910-004','火灾事故专项应急预案','SPECIAL','B/2','2026-03-13','2026-04-10','110108-YA-2026-0104','2026-04-23','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png',NULL,'2029-03-13',NULL,'FILED','依据 GB/T 29639-2020 编制','80370','2026-09-10 20:42:18','80370','2026-09-10 20:42:18',0x00,2010),(60,'YA-2026-0910-005','突发环境事件应急预案','SPECIAL','B/1','2026-04-14','2026-05-12','110108-YA-2026-0105','2026-05-24','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','2026-09-11','2029-09-11',NULL,'PUBLISHED','依据 GB/T 29639-2020 编制','80370','2026-09-10 20:42:18','80370','2026-09-11 18:22:18',0x00,2010),(61,'YA-2026-0910-006','有限空间作业现场处置方案','ONSITE','C/1','2026-09-11','2026-10-09',NULL,NULL,'http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png',NULL,'2029-09-11',NULL,'PUBLISHED','依据 GB/T 29639-2020 编制','80370','2026-09-10 20:42:18','80370','2026-09-11 18:22:24',0x00,2010);
/*!40000 ALTER TABLE `mes_set_emergency_plan` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_emergency_waste`
--

DROP TABLE IF EXISTS `mes_set_emergency_waste`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_emergency_waste` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `event_id` bigint NOT NULL COMMENT '应急事件编号',
  `event_no` varchar(64) NOT NULL COMMENT '应急事件编号（冗余，便于按单号直查）',
  `container_code` varchar(64) NOT NULL COMMENT '危废桶码（贴 HJ1276 标签的那只桶）',
  `waste_code` varchar(32) NOT NULL COMMENT '危废类别代码（泄漏吸附物按场景落 HW49/HW06/HW08）',
  `waste_name` varchar(128) DEFAULT NULL COMMENT '危废名称',
  `net_weight` decimal(12,3) NOT NULL COMMENT '净重 kg（过秤所得，不是手填）',
  `storage_location` varchar(128) DEFAULT NULL COMMENT '入库暂存库位',
  `weigh_record_id` bigint DEFAULT NULL COMMENT '称重记录编号（mes_set_weigh_record，过秤证据）',
  `hazwaste_id` bigint DEFAULT NULL COMMENT '危废台账行编号（mes_set_hazardous_waste）',
  `manifest_no` varchar(64) DEFAULT NULL COMMENT '危废台账业务号（冗余，便于反查）',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_event_id` (`event_id`),
  KEY `idx_event_no` (`event_no`),
  KEY `idx_container` (`container_code`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全环保检测-应急废物明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_emergency_waste`
--

LOCK TABLES `mes_set_emergency_waste` WRITE;
/*!40000 ALTER TABLE `mes_set_emergency_waste` DISABLE KEYS */;
INSERT INTO `mes_set_emergency_waste` VALUES (21,22,'EV-2026-0910-001','EM-EV-2026-0910-001-1','HW06','废稀释剂及吸附物',4.200,'危废暂存间-B',34,128,'EM-EV-2026-0910-001-1','80370','2026-09-10 20:42:22','80370','2026-09-10 20:42:22',0x00,2010),(22,23,'EV-2026-0910-002','EM-EV-2026-0910-002-1','HW08','废矿物油及吸附物',5.200,'危废暂存间-B',35,129,'EM-EV-2026-0910-002-1','80370','2026-09-10 20:42:22','80370','2026-09-10 20:42:22',0x00,2010);
/*!40000 ALTER TABLE `mes_set_emergency_waste` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_emission_outlet`
--

DROP TABLE IF EXISTS `mes_set_emission_outlet`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_emission_outlet` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `outlet_code` varchar(64) NOT NULL COMMENT '排放口编号(如 DA001/DW001)',
  `outlet_name` varchar(200) NOT NULL COMMENT '排放口名称',
  `outlet_type` varchar(16) NOT NULL COMMENT '排放类型：GAS/WASTEWATER/NOISE',
  `pollutant_codes` varchar(1000) DEFAULT NULL COMMENT '主要污染物列表(JSON/CSV文本)',
  `location` varchar(200) DEFAULT '' COMMENT '位置描述',
  `longitude` decimal(10,6) DEFAULT NULL COMMENT '经度',
  `latitude` decimal(10,6) DEFAULT NULL COMMENT '纬度',
  `stack_height` decimal(8,2) DEFAULT NULL COMMENT '排气筒高度 m',
  `monitor_method` varchar(16) DEFAULT NULL COMMENT '在线监测方式：CEMS/MANUAL/NONE',
  `permit_no` varchar(64) DEFAULT '' COMMENT '关联排污许可证号',
  `permit_limits` varchar(4000) DEFAULT NULL COMMENT '许可排放限值JSON文本',
  `is_key_outlet` tinyint(1) DEFAULT NULL COMMENT '是否重点/国控排放口：1是/0否',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
  `remark` varchar(500) DEFAULT '' COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=129 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MES SET-排放口';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_emission_outlet`
--

LOCK TABLES `mes_set_emission_outlet` WRITE;
/*!40000 ALTER TABLE `mes_set_emission_outlet` DISABLE KEYS */;
INSERT INTO `mes_set_emission_outlet` VALUES (1,'DA-SMOKE-1','一号废气排放口','GAS',NULL,'',NULL,NULL,NULL,NULL,'',NULL,1,'ACTIVE','','1','2026-09-02 15:33:09','1','2026-09-10 16:11:35',0x01,1),(2,'DA-001','1#废气排放口','EXHAUST_GAS','颗粒物,SO2,NOx','一号车间楼顶',NULL,NULL,15.00,'CEMS','P-110108-2026-0001','[{\"pollutantCode\":\"颗粒物\",\"pollutantName\":\"颗粒物\",\"limitValue\":30,\"unit\":\"mg/m3\"},{\"pollutantCode\":\"SO2\",\"pollutantName\":\"二氧化硫\",\"limitValue\":50,\"unit\":\"mg/m3\"},{\"pollutantCode\":\"VOCs\",\"pollutantName\":\"非甲烷总烃\",\"limitValue\":60,\"unit\":\"mg/m3\"}]',1,'ACTIVE','锅炉烟气排放口','80370','2026-09-02 10:00:00','80370','2026-09-10 10:19:18',0x00,2010),(3,'DA-002','2#废水排放口','WASTE_WATER','COD,氨氮,SS','厂区东侧污水处理站',NULL,NULL,NULL,'MANUAL','P-110108-2026-0002','[{\"pollutantCode\":\"COD\",\"pollutantName\":\"化学需氧量\",\"limitValue\":100,\"unit\":\"mg/L\"},{\"pollutantCode\":\"氨氮\",\"pollutantName\":\"氨氮\",\"limitValue\":8,\"unit\":\"mg/L\"}]',0,'ACTIVE','综合废水总排口','80370','2026-09-02 10:00:00','80370','2026-09-10 10:19:18',0x00,2010),(120,'DA-003','3#喷涂废气排放口','EXHAUST_GAS','颗粒物,SO2,VOCs','厂区东侧',116.300000,40.000000,15.00,'CEMS','P-110108-2026-0001','[{\"pollutantCode\": \"颗粒物\", \"pollutantName\": \"颗粒物\", \"limitValue\": 30}, {\"pollutantCode\": \"SO2\", \"pollutantName\": \"二氧化硫\", \"limitValue\": 50}, {\"pollutantCode\": \"VOCs\", \"pollutantName\": \"非甲烷总烃\", \"limitValue\": 60}]',1,'ACTIVE','排污许可证载明排放口','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(121,'DA-004','4#打磨废气排放口','EXHAUST_GAS','颗粒物','厂区东侧',116.301000,40.001000,12.00,'MANUAL','P-110108-2026-0001','[{\"pollutantCode\": \"颗粒物\", \"pollutantName\": \"颗粒物\", \"limitValue\": 30}]',0,'ACTIVE','排污许可证载明排放口','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(122,'DA-005','3#生活污水排放口','WASTE_WATER','COD,氨氮','厂区南侧',116.302000,40.002000,NULL,'MANUAL','P-110108-2026-0002','[{\"pollutantCode\": \"COD\", \"pollutantName\": \"化学需氧量\", \"limitValue\": 100}, {\"pollutantCode\": \"氨氮\", \"pollutantName\": \"氨氮\", \"limitValue\": 8}]',0,'ACTIVE','排污许可证载明排放口','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010);
/*!40000 ALTER TABLE `mes_set_emission_outlet` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_env_report`
--

DROP TABLE IF EXISTS `mes_set_env_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_env_report` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `report_no` varchar(64) NOT NULL COMMENT '报告编号 EP-YYYYMMDD-NNN',
  `report_name` varchar(200) NOT NULL COMMENT '报告名称',
  `report_type` varchar(16) NOT NULL COMMENT '报告来源：THIRD_PARTY/INTERNAL',
  `report_category` varchar(32) DEFAULT NULL COMMENT '类别：EXHAUST_GAS/WASTEWATER/NOISE/SOLID_WASTE/AMBIENT/COMPREHENSIVE等',
  `template_id` bigint DEFAULT NULL COMMENT '复用质检报告模板编号',
  `period_start` date DEFAULT NULL COMMENT '报告统计期起',
  `period_end` date DEFAULT NULL COMMENT '报告统计期止',
  `report_date` date DEFAULT NULL COMMENT '报告日期',
  `data_summary` varchar(4000) DEFAULT NULL COMMENT '检测结果摘要JSON文本',
  `file_url` varchar(500) DEFAULT '' COMMENT '报告文件URL(第三方导入PDF)',
  `sign_url` varchar(500) DEFAULT '' COMMENT '电子签名文件URL',
  `form_id` bigint DEFAULT NULL COMMENT '自定义表单配置id',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/APPROVED/REJECTED/ARCHIVED',
  `audit_by` varchar(64) DEFAULT '' COMMENT '审核人(终审)',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `remark` varchar(500) DEFAULT '' COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=82 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MES SET-环保检测报告';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_env_report`
--

LOCK TABLES `mes_set_env_report` WRITE;
/*!40000 ALTER TABLE `mes_set_env_report` DISABLE KEYS */;
INSERT INTO `mes_set_env_report` VALUES (1,'EP-SMOKE-1','2026年度废气检测报告（第三方）','THIRD_PARTY','EXHAUST_GAS',NULL,NULL,NULL,'2026-09-30',NULL,'','',NULL,'DRAFT','',NULL,'','1','2026-09-02 15:33:10','1','2026-09-11 15:35:52',0x01,1),(2,'REP-2026-09-001','2026年8月环保月度报告','MONTHLY',NULL,NULL,'2026-08-01','2026-08-31','2026-09-05','各排放口监测数据均达标','','',NULL,'APPROVED','',NULL,'已归档','80370','2026-09-02 10:00:00','80370','2026-09-03 08:29:22',0x01,2010),(3,'REP-2026-09-002','2026年第三季度排污许可执行报告','QUARTERLY',NULL,NULL,'2026-07-01','2026-09-30',NULL,NULL,'','',NULL,'DRAFT','',NULL,'编制中','80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(74,'REP-2026-0910-001','2026 年2026-06环境监测报告','MONTHLY','ENVIRONMENT',NULL,'2026-06-01','2026-06-30','2026-06-30','废气、废水、噪声、粉尘监测数据汇总，超标项均已闭环整改','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E7%AD%BE%E5%90%8D%E5%BA%95%E5%9B%BE.png',NULL,'DRAFT','',NULL,'','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(75,'REP-2026-0910-002','2026 年2026-07环境监测报告','MONTHLY','ENVIRONMENT',NULL,'2026-07-01','2026-07-31','2026-07-31','废气、废水、噪声、粉尘监测数据汇总，超标项均已闭环整改','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E7%AD%BE%E5%90%8D%E5%BA%95%E5%9B%BE.png',NULL,'DRAFT','',NULL,'','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(76,'REP-2026-0910-003','2026 年2026-04环境监测报告','QUARTERLY','ENVIRONMENT',NULL,'2026-04-01','2026-06-30','2026-06-30','废气、废水、噪声、粉尘监测数据汇总，超标项均已闭环整改','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E7%AD%BE%E5%90%8D%E5%BA%95%E5%9B%BE.png',NULL,'DRAFT','',NULL,'','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(77,'REP-2026-0910-004','2026 年2026-08环境监测报告','MONTHLY','SAFETY',NULL,'2026-08-01','2026-08-31','2026-08-31','废气、废水、噪声、粉尘监测数据汇总，超标项均已闭环整改','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E7%AD%BE%E5%90%8D%E5%BA%95%E5%9B%BE.png',NULL,'DRAFT','',NULL,'','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010);
/*!40000 ALTER TABLE `mes_set_env_report` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_exhaust_gas`
--

DROP TABLE IF EXISTS `mes_set_exhaust_gas`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_exhaust_gas` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `record_no` varchar(64) NOT NULL COMMENT '记录编号 EXGAS-YYYYMMDD-NNN',
  `outlet_id` bigint NOT NULL COMMENT '关联排放口编号',
  `wo_id` bigint DEFAULT NULL COMMENT '关联工单编号',
  `pollutant_code` varchar(32) NOT NULL COMMENT '污染物：SO2/NOX/PM/VOCs/HCL/HF等',
  `concentration` decimal(12,3) DEFAULT NULL COMMENT '排放浓度 mg/m3',
  `unit` varchar(16) DEFAULT '' COMMENT '单位 mg/m3等',
  `flow_rate` decimal(12,3) DEFAULT NULL COMMENT '标态干烟气流量 m3/h',
  `emission_amount` decimal(12,3) DEFAULT NULL COMMENT '折算排放速率 kg/h',
  `limit_value` decimal(12,3) DEFAULT NULL COMMENT '限值',
  `result` varchar(8) DEFAULT NULL COMMENT '结果：PASS/FAIL',
  `collection_mode` varchar(16) DEFAULT 'MANUAL' COMMENT '采集方式：CEMS_AUTO/MANUAL',
  `monitor_time` datetime NOT NULL COMMENT '监测时间',
  `instrument_no` varchar(64) DEFAULT '' COMMENT 'CEMS设备编号/采样仪器号',
  `inspector` varchar(64) DEFAULT '' COMMENT '监测人(手工时)',
  `remark` varchar(500) DEFAULT '' COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=314 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MES SET-废气排放检测记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_exhaust_gas`
--

LOCK TABLES `mes_set_exhaust_gas` WRITE;
/*!40000 ALTER TABLE `mes_set_exhaust_gas` DISABLE KEYS */;
INSERT INTO `mes_set_exhaust_gas` VALUES (1,'EXGAS-SMOKE-1',1,NULL,'SO2',35.000,'mg/m3',NULL,NULL,NULL,'PASS','MANUAL','2026-09-02 11:00:00','','','','1','2026-09-02 15:33:09','1','2026-09-02 15:33:27',0x01,1),(2,'EXH-2026-0901-001',2,NULL,'SO2',35.000,'mg/m3',1200.000,0.850,50.000,'PASS','MANUAL','2026-09-01 09:00:00','CEMS-01','刘洋','','80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(3,'EXH-2026-0901-002',2,NULL,'颗粒物',18.000,'mg/m3',1200.000,0.450,30.000,'PASS','CEMS_AUTO','2026-09-01 10:00:00','CEMS-01','刘洋','','80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(292,'EXH-2026-0910-001',2,NULL,'颗粒物',12.000,'mg/m³',11000.000,0.132,30.000,'PASS','CEMS_AUTO','2026-09-02 10:00:00','EXH-4001','陈静','','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(293,'EXH-2026-0910-002',2,NULL,'VOCs',118.000,'mg/m³',11000.000,1.298,60.000,'FAIL','CEMS_AUTO','2026-09-04 10:00:00','EXH-4002','陈静','当日换炭后复测合格','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(294,'EXH-2026-0910-003',2,NULL,'SO2',18.000,'mg/m³',11000.000,0.198,50.000,'PASS','CEMS_AUTO','2026-09-06 10:00:00','EXH-4003','陈静','','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(295,'EXH-2026-0910-004',2,NULL,'颗粒物',9.500,'mg/m³',10500.000,0.100,30.000,'PASS','CEMS_AUTO','2026-09-08 10:00:00','EXH-4004','陈静','','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(296,'EXH-2026-0910-005',2,NULL,'VOCs',22.000,'mg/m³',9800.000,0.216,60.000,'PASS','CEMS_AUTO','2026-09-09 10:00:00','EXH-4005','陈静','','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(297,'EXH-2026-0910-006',2,NULL,'SO2',15.000,'mg/m³',10200.000,0.153,50.000,'PASS','CEMS_AUTO','2026-09-10 10:00:00','EXH-4006','陈静','','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010);
/*!40000 ALTER TABLE `mes_set_exhaust_gas` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_fire_check`
--

DROP TABLE IF EXISTS `mes_set_fire_check`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_fire_check` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `record_no` varchar(64) NOT NULL COMMENT '记录编号',
  `plan_id` bigint DEFAULT NULL COMMENT '关联检测计划编号',
  `location` varchar(200) DEFAULT NULL COMMENT '区域/位置',
  `facility_name` varchar(200) NOT NULL COMMENT '设施名称(灭火器/消火栓/烟感/温感/应急照明/疏散指示等)',
  `facility_code` varchar(64) DEFAULT NULL COMMENT '设施编号(资产编号)',
  `check_time` datetime NOT NULL COMMENT '检测时间',
  `result` varchar(8) DEFAULT NULL COMMENT '结果：PASS/FAIL',
  `problem_desc` varchar(500) DEFAULT NULL COMMENT '异常/不合格描述',
  `inspector` varchar(64) DEFAULT NULL COMMENT '检测人',
  `photo_urls` varchar(2000) DEFAULT NULL COMMENT '检测照片URL(逗号分隔)',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_record_no` (`record_no`)
) ENGINE=InnoDB AUTO_INCREMENT=48 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全环保检测-消防设施检测记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_fire_check`
--

LOCK TABLES `mes_set_fire_check` WRITE;
/*!40000 ALTER TABLE `mes_set_fire_check` DISABLE KEYS */;
INSERT INTO `mes_set_fire_check` VALUES (1,'FC-0001',NULL,'1号车间','干粉灭火器','MFZ-001','2026-09-02 10:00:00','PASS',NULL,'张三',NULL,'P0冒烟','1','2026-09-02 14:36:43','1','2026-09-10 16:11:35',0x00,1),(2,'FIRE-2026-0901-001',NULL,'一号车间东侧通道','手提式干粉灭火器','XF-001','2026-09-01 10:30:00','PASS',NULL,'刘洋',NULL,NULL,'80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(3,'FIRE-2026-0901-002',NULL,'二号车间楼梯间','室内消火栓','XF-002','2026-09-01 11:00:00','FAIL','水带老化渗水，需更换','刘洋',NULL,NULL,'80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(41,'FIRE-2026-0910-001',NULL,'消防泵房','消防水泵','FIRE-PUMP-01','2026-09-02 09:00:00','PASS',NULL,'刘洋','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png',NULL,'80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(42,'FIRE-2026-0910-002',NULL,'1#车间北侧','手提式干粉灭火器','MFZ-001','2026-09-04 10:00:00','PASS',NULL,'刘洋','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png',NULL,'80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(43,'FIRE-2026-0910-003',NULL,'1#车间南侧','手提式干粉灭火器','MFZ-014','2026-09-06 11:00:00','FAIL','压力表指针落在红区，已送检充装','刘洋','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png',NULL,'80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(44,'FIRE-2026-0910-004',NULL,'危废暂存间','推车式干粉灭火器','MFT-003','2026-09-08 12:00:00','PASS',NULL,'刘洋','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png',NULL,'80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(45,'FIRE-2026-0910-005',NULL,'成品仓库','室内消火栓','FH-007','2026-09-09 13:00:00','PASS',NULL,'刘洋','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png',NULL,'80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(46,'FIRE-2026-0910-006',NULL,'配电室','二氧化碳灭火器','MT-002','2026-09-10 09:00:00','PASS',NULL,'刘洋','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png',NULL,'80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010);
/*!40000 ALTER TABLE `mes_set_fire_check` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_gas_record`
--

DROP TABLE IF EXISTS `mes_set_gas_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_gas_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `record_no` varchar(64) NOT NULL COMMENT '记录编号',
  `plan_id` bigint DEFAULT NULL COMMENT '关联检测计划编号',
  `wo_id` bigint DEFAULT NULL COMMENT '关联工单编号（事件触发型）',
  `operation_id` bigint DEFAULT NULL COMMENT '关联工序编号',
  `permit_id` bigint DEFAULT NULL COMMENT '关联作业许可编号',
  `location` varchar(200) DEFAULT NULL COMMENT '检测位置',
  `gas_type` varchar(16) NOT NULL COMMENT '气体类型：CO/H2S/O2/LEL/VOC/NH3/CL2',
  `concentration` decimal(10,3) DEFAULT NULL COMMENT '检测浓度值',
  `unit` varchar(16) DEFAULT NULL COMMENT '单位：mg/m3 / % / %LEL',
  `limit_value` decimal(10,3) DEFAULT NULL COMMENT '限值',
  `result` varchar(8) DEFAULT NULL COMMENT '结果：PASS/FAIL',
  `collection_mode` varchar(16) NOT NULL DEFAULT 'MANUAL' COMMENT '采集方式：IOT_AUTO/MANUAL',
  `instrument_no` varchar(64) DEFAULT NULL COMMENT '检测仪器编号',
  `inspector` varchar(64) DEFAULT NULL COMMENT '检测人',
  `inspect_time` datetime NOT NULL COMMENT '检测时间',
  `photo_urls` varchar(2000) DEFAULT NULL COMMENT '检测照片URL(逗号分隔)',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_record_no` (`record_no`)
) ENGINE=InnoDB AUTO_INCREMENT=48 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全环保检测-作业环境气体检测记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_gas_record`
--

LOCK TABLES `mes_set_gas_record` WRITE;
/*!40000 ALTER TABLE `mes_set_gas_record` DISABLE KEYS */;
INSERT INTO `mes_set_gas_record` VALUES (1,'GR-0001',NULL,NULL,NULL,NULL,'1号车间焊接区','CO',12.500,'mg/m3',30.000,'PASS','MANUAL',NULL,'张三','2026-09-02 10:00:00',NULL,'P0冒烟','1','2026-09-02 14:36:43','1','2026-09-10 16:11:35',0x00,1),(2,'GAS-2026-0901-001',NULL,NULL,NULL,NULL,'焊接工位A-03','CO',5.200,'ppm',30.000,'PASS','MANUAL','GA-001','刘洋','2026-09-01 09:50:00',NULL,NULL,'80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(3,'GAS-2026-0901-002',NULL,NULL,NULL,NULL,'喷漆房','NO2',0.080,'mg/m3',0.200,'PASS','MANUAL','GA-002','刘洋','2026-09-01 13:20:00',NULL,NULL,'80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(41,'GAS-2026-0910-001',NULL,NULL,NULL,NULL,'锅炉房','CO',12.000,'mg/m³',20.000,'PASS','固定式报警器+便携式复测','GAS-1001','杨磊','2026-09-02 10:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png',NULL,'80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(42,'GAS-2026-0910-002',NULL,NULL,NULL,NULL,'污水站集水井（有限空间）','CO',24.000,'mg/m³',20.000,'FAIL','固定式报警器+便携式复测','GAS-1002','杨磊','2026-09-04 11:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','已启动局部强制通风并复测','80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(43,'GAS-2026-0910-003',NULL,NULL,NULL,NULL,'焊接工位','NO2',2.400,'mg/m³',5.000,'PASS','固定式报警器+便携式复测','GAS-1003','杨磊','2026-09-06 12:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png',NULL,'80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(44,'GAS-2026-0910-004',NULL,NULL,NULL,NULL,'热处理炉旁','NO2',6.800,'mg/m³',5.000,'FAIL','固定式报警器+便携式复测','GAS-1004','杨磊','2026-09-08 13:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','已启动局部强制通风并复测','80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(45,'GAS-2026-0910-005',NULL,NULL,NULL,NULL,'喷涂线-1 烘干房','CO',8.500,'mg/m³',20.000,'PASS','固定式报警器+便携式复测','GAS-1005','杨磊','2026-09-09 14:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png',NULL,'80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(46,'GAS-2026-0910-006',NULL,NULL,NULL,NULL,'废料暂存区','NO2',1.600,'mg/m³',5.000,'PASS','固定式报警器+便携式复测','GAS-1006','杨磊','2026-09-10 10:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png',NULL,'80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010);
/*!40000 ALTER TABLE `mes_set_gas_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_hazardous_waste`
--

DROP TABLE IF EXISTS `mes_set_hazardous_waste`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_hazardous_waste` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `manifest_no` varchar(64) NOT NULL COMMENT '危废联单号',
  `waste_code` varchar(64) DEFAULT NULL COMMENT '危废代码(如 HW08)',
  `waste_name` varchar(200) NOT NULL COMMENT '危废名称',
  `quantity` decimal(12,3) DEFAULT NULL COMMENT '数量',
  `quantity_unit` varchar(16) DEFAULT NULL COMMENT '数量单位（吨等）',
  `stage` varchar(16) NOT NULL DEFAULT 'GENERATED' COMMENT '台账环节：GENERATED(产生)/STORED(贮存)/TRANSFERRED(转移)/DISPOSED(处置)',
  `storage_location` varchar(200) DEFAULT NULL COMMENT '贮存地点',
  `container_code` varchar(64) DEFAULT NULL COMMENT '容器码(一桶一码，危废标签二维码内容)',
  `label_url` varchar(500) DEFAULT NULL COMMENT 'HJ1276 标签归档文件 URL',
  `counterparty` varchar(200) DEFAULT NULL COMMENT '交接方/接收单位',
  `wo_id` bigint DEFAULT NULL COMMENT '来源工单编号',
  `handle_time` datetime DEFAULT NULL COMMENT '交接/处理时间',
  `handler` varchar(64) DEFAULT NULL COMMENT '经办人',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态：DRAFT/APPROVED/REJECTED',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_hazwaste_container_code` (`container_code`),
  KEY `idx_hazwaste_manifest_no` (`manifest_no`)
) ENGINE=InnoDB AUTO_INCREMENT=148 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全环保检测-危废台账';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_hazardous_waste`
--

LOCK TABLES `mes_set_hazardous_waste` WRITE;
/*!40000 ALTER TABLE `mes_set_hazardous_waste` DISABLE KEYS */;
INSERT INTO `mes_set_hazardous_waste` VALUES (1,'HW-0001','HW08','废矿物油',120.500,'kg','GENERATED','危废暂存间',NULL,NULL,'xx环保科技有限公司',NULL,NULL,NULL,'DRAFT','P0冒烟','1','2026-09-02 14:36:43','1','2026-09-10 16:11:35',0x00,1),(2,'HW-2026-0901-001','HW-09','废矿物油',1.200,'t','STORED','危废间A区',NULL,NULL,'北京绿源环保科技有限公司',NULL,NULL,NULL,'APPROVED',NULL,'80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(3,'HW-2026-0901-002','HW-49','废弃油漆桶',0.300,'t','TRANSFERRED','危废间B区',NULL,NULL,'北京绿源环保科技有限公司',NULL,'2026-09-01 16:00:00','刘洋','APPROVED',NULL,'80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(121,'HW49-TF-2026-0910-006-20260910204217','HW49','废活性炭',260.000,'kg','GENERATED','危废暂存间-A','BUCKET-AC-0910-01',NULL,'备用活性炭吸附装置',NULL,'2026-09-10 00:00:00','刘洋','DRAFT','治污设施 备用活性炭吸附装置(TF-2026-0910-006) 换炭自动登记：耗材→危废转化（设计文档 §6.3）','80370','2026-09-10 20:42:17','80370','2026-09-10 20:42:17',0x00,2010),(122,'LD-2026-0910-001','HW49','废活性炭',260.000,'kg','TRANSFERRED','危废暂存间-A','BUCKET-AC-0910-02',NULL,'北京金隅红树林环保科技',NULL,'2026-09-10 20:42:19','刘洋','APPROVED','过秤贴签后入库','80370','2026-09-10 20:42:19','80370','2026-09-10 20:42:19',0x00,2010),(123,'LD-2026-0910-002','HW08','废矿物油',180.500,'kg','STORED','危废暂存间-A','BUCKET-OIL-0910-01',NULL,'北京金隅红树林环保科技',NULL,'2026-09-10 20:42:19','刘洋','DRAFT','过秤贴签后入库','80370','2026-09-10 20:42:19','80370','2026-09-10 20:42:19',0x00,2010),(124,'HW-2026-0910-003','HW12','废油漆渣',95.000,'kg','GENERATED','危废暂存间-B','BUCKET-PAINT-0910-01',NULL,'北京金隅红树林环保科技',NULL,'2026-09-10 10:00:00','刘洋','DRAFT','过秤贴签后入库','80370','2026-09-10 20:42:19','80370','2026-09-10 20:42:19',0x00,2010),(125,'HW-2026-0910-004','HW06','废稀释剂',140.000,'kg','GENERATED','危废暂存间-B','BUCKET-THIN-0910-01',NULL,'北京金隅红树林环保科技',NULL,'2026-09-10 10:00:00','刘洋','DRAFT','过秤贴签后入库','80370','2026-09-10 20:42:19','80370','2026-09-10 20:42:19',0x00,2010),(126,'HW-2026-0910-005','HW09','废切削液',210.000,'kg','GENERATED','危废暂存间-C','BUCKET-CUT-0910-01',NULL,'北京金隅红树林环保科技',NULL,'2026-09-10 10:00:00','刘洋','DRAFT','过秤贴签后入库','80370','2026-09-10 20:42:19','80370','2026-09-10 20:42:19',0x00,2010),(127,'HW-2026-0910-006','HW49','废包装桶',65.000,'kg','DISPOSED','危废暂存间-C','BUCKET-PKG-0910-01',NULL,'北京金隅红树林环保科技',NULL,'2026-09-11 17:59:20','刘洋','APPROVED','过秤贴签后入库','80370','2026-09-10 20:42:19','80370','2026-09-11 17:59:20',0x00,2010),(128,'EM-EV-2026-0910-001-1','HW06','废稀释剂及吸附物',4.200,'kg','GENERATED','危废暂存间-B','EM-EV-2026-0910-001-1',NULL,'应急事件 EV-2026-0910-001',NULL,'2026-09-10 20:42:22','刘洋','DRAFT','应急处置自动登记：喷涂线-1 调漆间（设计文档 §八.4 应急废物一律过秤贴签入台账）','80370','2026-09-10 20:42:22','80370','2026-09-10 20:42:22',0x00,2010),(129,'EM-EV-2026-0910-002-1','HW08','废矿物油及吸附物',5.200,'kg','GENERATED','危废暂存间-B','EM-EV-2026-0910-002-1',NULL,'应急事件 EV-2026-0910-002',NULL,'2026-09-10 20:42:22','刘洋','DRAFT','应急处置自动登记：设备维修区（设计文档 §八.4 应急废物一律过秤贴签入台账）','80370','2026-09-10 20:42:22','80370','2026-09-10 20:42:22',0x00,2010);
/*!40000 ALTER TABLE `mes_set_hazardous_waste` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_hazwaste_manifest`
--

DROP TABLE IF EXISTS `mes_set_hazwaste_manifest`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_hazwaste_manifest` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `manifest_no` varchar(64) NOT NULL COMMENT '国家固废系统电子转移联单号',
  `waste_code` varchar(64) DEFAULT NULL COMMENT '危险废物类别代码(HW08/HW49等)',
  `waste_name` varchar(200) DEFAULT NULL COMMENT '危险废物名称',
  `quantity` decimal(12,3) DEFAULT NULL COMMENT '申报转移量',
  `quantity_unit` varchar(16) DEFAULT '吨' COMMENT '数量单位',
  `generate_unit` varchar(200) DEFAULT NULL COMMENT '产生单位(五方之一)',
  `carrier_unit` varchar(200) DEFAULT NULL COMMENT '运输单位',
  `receive_unit` varchar(200) DEFAULT NULL COMMENT '接收单位',
  `storage_unit` varchar(200) DEFAULT NULL COMMENT '贮存单位',
  `dispose_unit` varchar(200) DEFAULT NULL COMMENT '处置单位',
  `carrier_confirm_time` datetime DEFAULT NULL COMMENT '运输方确认时间',
  `receive_confirm_time` datetime DEFAULT NULL COMMENT '接收方确认时间',
  `storage_confirm_time` datetime DEFAULT NULL COMMENT '贮存方确认时间',
  `dispose_confirm_time` datetime DEFAULT NULL COMMENT '处置方确认时间',
  `declared_time` datetime DEFAULT NULL COMMENT '产生方申报时间',
  `declare_deadline` datetime DEFAULT NULL COMMENT '申报/确认时限(国家平台倒排提醒基准)',
  `vehicle_no` varchar(32) DEFAULT NULL COMMENT '运输车牌号(门卫扫牌核对)',
  `net_weight` decimal(12,3) DEFAULT NULL COMMENT '地磅净重(吨)',
  `transfer_time` datetime DEFAULT NULL COMMENT '启运出厂时间',
  `gate_release_time` datetime DEFAULT NULL COMMENT '门卫放行时间',
  `gate_guard` varchar(64) DEFAULT NULL COMMENT '放行门卫',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT草稿/DECLARED已申报/EFFECTIVE已生效/TRANSFERRED已出厂/CLOSED已回执归档',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_hazwaste_manifest_no` (`manifest_no`),
  KEY `idx_hazwaste_manifest_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=86 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全环保检测-危废转移联单(国家固废五方)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_hazwaste_manifest`
--

LOCK TABLES `mes_set_hazwaste_manifest` WRITE;
/*!40000 ALTER TABLE `mes_set_hazwaste_manifest` DISABLE KEYS */;
INSERT INTO `mes_set_hazwaste_manifest` VALUES (70,'LD-2026-0910-001','HW49','废活性炭',260.000,'kg','华翰智能制造（北京）有限公司','北京危废运输有限公司','北京金隅红树林环保科技有限公司','华翰智能制造（北京）有限公司','北京金隅红树林环保科技有限公司','2026-09-10 20:42:19','2026-09-10 20:42:19',NULL,NULL,'2026-09-10 20:42:19','2026-09-20 18:00:00','京A·X7392',258.600,'2026-09-10 20:42:19','2026-09-10 20:42:19','刘洋','CLOSED','换炭产生，桶装密闭','80370','2026-09-10 20:42:19','80370','2026-09-10 20:42:19',0x00,2010),(71,'LD-2026-0910-002','HW08','废矿物油',180.500,'kg','华翰智能制造（北京）有限公司','北京危废运输有限公司','北京金隅红树林环保科技有限公司',NULL,NULL,'2026-09-10 20:42:19','2026-09-10 20:42:19',NULL,NULL,'2026-09-10 20:42:19','2026-09-25 18:00:00','京B·M2048',179.200,NULL,NULL,NULL,'EFFECTIVE','设备换油产生','80370','2026-09-10 20:42:19','80370','2026-09-10 20:42:19',0x00,2010);
/*!40000 ALTER TABLE `mes_set_hazwaste_manifest` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_noise_record`
--

DROP TABLE IF EXISTS `mes_set_noise_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_noise_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `record_no` varchar(64) NOT NULL COMMENT '记录编号 NOISE-YYYYMMDD-NNN',
  `plan_id` bigint DEFAULT NULL COMMENT '关联检测计划编号',
  `wo_id` bigint DEFAULT NULL COMMENT '关联工单编号',
  `operation_id` bigint DEFAULT NULL COMMENT '关联工序编号',
  `device_id` bigint DEFAULT NULL COMMENT '关联设备编号',
  `emp_id` bigint DEFAULT NULL COMMENT '关联人员编号(个体剂量计佩戴人)',
  `source_type` varchar(16) NOT NULL COMMENT '监测类型：STATIONARY(固定式声级计)/PERSONAL(个体剂量计)',
  `location` varchar(200) DEFAULT '' COMMENT '检测位置/区域',
  `collection_mode` varchar(16) DEFAULT 'MANUAL' COMMENT '采集方式：IOT_AUTO/MANUAL',
  `lex8h` decimal(10,3) DEFAULT NULL COMMENT '8小时等效声级 dB(A)',
  `lpeak` decimal(10,3) DEFAULT NULL COMMENT '峰值声级 dB(C)',
  `limit_lex8h` decimal(10,3) DEFAULT NULL COMMENT '限值 dB(A)≤85',
  `limit_lpeak` decimal(10,3) DEFAULT NULL COMMENT '限值 dB(C)≤140',
  `spectrum` varchar(1000) DEFAULT NULL COMMENT '频谱分析(倍频程，JSON文本)',
  `standard_id` bigint DEFAULT NULL COMMENT '关联检测标准编号',
  `result` varchar(8) DEFAULT NULL COMMENT '结果：PASS/FAIL',
  `instrument_no` varchar(64) DEFAULT '' COMMENT '检测仪器编号',
  `inspector` varchar(64) DEFAULT '' COMMENT '检测人',
  `inspect_time` datetime NOT NULL COMMENT '检测时间',
  `photo_urls` varchar(2000) DEFAULT NULL COMMENT '检测照片URL(逗号分隔)',
  `remark` varchar(500) DEFAULT '' COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_record_no` (`record_no`)
) ENGINE=InnoDB AUTO_INCREMENT=48 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MES SET-噪声检测记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_noise_record`
--

LOCK TABLES `mes_set_noise_record` WRITE;
/*!40000 ALTER TABLE `mes_set_noise_record` DISABLE KEYS */;
INSERT INTO `mes_set_noise_record` VALUES (1,'NOISE-SMOKE-1',NULL,NULL,NULL,NULL,NULL,'STATIONARY','噪声A','MANUAL',78.500,NULL,NULL,NULL,NULL,NULL,'PASS','','','2026-09-02 11:00:00',NULL,'','1','2026-09-02 15:33:08','1','2026-09-10 16:11:35',0x01,1),(2,'NOISE-2026-0901-001',NULL,NULL,NULL,1,80370,'冲床','冲压车间','AUTO',82.500,105.000,85.000,120.000,NULL,NULL,'PASS','NM-001','刘洋','2026-09-01 10:10:00',NULL,'','80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(3,'NOISE-2026-0901-002',NULL,NULL,NULL,2,80371,'空压机','空压机房','AUTO',87.000,112.000,85.000,120.000,NULL,NULL,'FAIL','NM-002','刘洋','2026-09-01 15:00:00',NULL,'','80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(41,'NOISE-2026-0910-001',NULL,NULL,NULL,NULL,NULL,'设备噪声','喷涂线-1','MANUAL',82.500,96.000,85.000,115.000,'A',NULL,'PASS','NOISE-2001','赵敏','2026-09-02 09:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','','80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(42,'NOISE-2026-0910-002',NULL,NULL,NULL,NULL,NULL,'设备噪声','抛丸机旁','MANUAL',94.200,108.000,85.000,115.000,'A',NULL,'FAIL','NOISE-2002','赵敏','2026-09-04 10:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','已配发耳塞并纳入听力保护计划','80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(43,'NOISE-2026-0910-003',NULL,NULL,NULL,NULL,NULL,'设备噪声','空压站','MANUAL',88.000,102.000,85.000,115.000,'A',NULL,'PASS','NOISE-2003','赵敏','2026-09-06 11:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','','80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(44,'NOISE-2026-0910-004',NULL,NULL,NULL,NULL,NULL,'设备噪声','冲压车间','MANUAL',84.100,99.000,85.000,115.000,'A',NULL,'PASS','NOISE-2004','赵敏','2026-09-08 12:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','','80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(45,'NOISE-2026-0910-005',NULL,NULL,NULL,NULL,NULL,'设备噪声','废水站风机房','MANUAL',86.700,101.000,85.000,115.000,'A',NULL,'FAIL','NOISE-2005','赵敏','2026-09-09 13:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','已配发耳塞并纳入听力保护计划','80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(46,'NOISE-2026-0910-006',NULL,NULL,NULL,NULL,NULL,'环境噪声','办公楼走廊','MANUAL',55.000,68.000,60.000,70.000,'A',NULL,'PASS','NOISE-2006','赵敏','2026-09-10 14:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','','80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010);
/*!40000 ALTER TABLE `mes_set_noise_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_occupational_hazard`
--

DROP TABLE IF EXISTS `mes_set_occupational_hazard`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_occupational_hazard` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `record_no` varchar(64) NOT NULL COMMENT '记录编号 OH-YYYYMMDD-NNN',
  `plan_id` bigint DEFAULT NULL COMMENT '关联检测计划编号',
  `emp_id` bigint DEFAULT NULL COMMENT '关联人员编号(个体暴露监测时；岗位检测可空)',
  `factor_category` varchar(16) NOT NULL COMMENT '因素类别：CHEMICAL/PHYSICAL/BIOLOGICAL',
  `factor_code` varchar(32) NOT NULL COMMENT '具体因素：TOXIC/DUST/NOISE/RADIATION/HEAT/VIBRATION/BIOAGENT',
  `workplace` varchar(200) NOT NULL COMMENT '检测岗位/工作场所',
  `source_ref_type` varchar(16) DEFAULT NULL COMMENT '数据来源：REUSE/ORIGINAL',
  `source_record_id` bigint DEFAULT NULL COMMENT '来源安全检测记录id(复用气体/噪声/粉尘)',
  `measured_value` decimal(12,3) DEFAULT NULL COMMENT '实测浓度/强度',
  `unit` varchar(16) DEFAULT '' COMMENT '单位 mg/m3/dB(A)/mSv/C/m-s2等',
  `limit_type` varchar(16) DEFAULT NULL COMMENT '接触限值类型：MAC/PC-TWA/PC-STEL',
  `oel_value` decimal(12,3) DEFAULT NULL COMMENT '职业接触限值',
  `ref_standard` varchar(100) DEFAULT '' COMMENT '引用国标',
  `result` varchar(8) DEFAULT NULL COMMENT '结果：PASS/FAIL',
  `inspector` varchar(64) DEFAULT '' COMMENT '检测人',
  `inspect_time` datetime NOT NULL COMMENT '检测时间',
  `remark` varchar(500) DEFAULT '' COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_record_no` (`record_no`)
) ENGINE=InnoDB AUTO_INCREMENT=47 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MES SET-职业病危害因素检测记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_occupational_hazard`
--

LOCK TABLES `mes_set_occupational_hazard` WRITE;
/*!40000 ALTER TABLE `mes_set_occupational_hazard` DISABLE KEYS */;
INSERT INTO `mes_set_occupational_hazard` VALUES (1,'OH-SMOKE-1',NULL,NULL,'CHEMICAL','TOXIC','涂装车间',NULL,NULL,8.500,'mg/m3','PC-TWA',10.000,'','PASS','','2026-09-02 11:00:00','','1','2026-09-02 15:33:11','1','2026-09-10 16:11:35',0x01,1),(2,'OCH-2026-0901-001',NULL,80370,'CHEMICAL','苯','喷漆房','POINT',NULL,2.000,'mg/m3','PC-TWA',6.000,'GBZ 2.1-2019','PASS','刘洋','2026-09-01 11:30:00','','80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(3,'OCH-2026-0901-002',NULL,80371,'PHYSICAL','噪声','冲压车间','POINT',NULL,87.000,'dB(A)','NOISE',85.000,'GBZ 2.2-2007','FAIL','刘洋','2026-09-01 14:40:00','','80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(40,'OCH-2026-0910-001',NULL,80371,'CHEMICAL','甲苯二异氰酸酯','喷涂线-1 调漆间','WORKSHOP',NULL,0.005,'mg/m³','PC-TWA',0.100,'GBZ 2.1-2019','PASS','周倩','2026-09-02 09:00:00','','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(41,'OCH-2026-0910-002',NULL,80373,'CHEMICAL','二甲苯','调漆间','WORKSHOP',NULL,18.000,'mg/m³','PC-TWA',50.000,'GBZ 2.1-2019','PASS','周倩','2026-09-04 10:00:00','','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(42,'OCH-2026-0910-003',NULL,80375,'CHEMICAL','苯','喷涂线-2','WORKSHOP',NULL,0.300,'mg/m³','PC-TWA',1.000,'GBZ 2.1-2019','PASS','周倩','2026-09-06 11:00:00','','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(43,'OCH-2026-0910-004',NULL,80377,'PHYSICAL','噪声','抛丸工位','WORKSHOP',NULL,94.200,'dB(A)','LEX_8H',85.000,'GBZ 2.2-2007','FAIL','周倩','2026-09-08 12:00:00','已纳入职业健康监护重点岗位','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(44,'OCH-2026-0910-005',NULL,80379,'PHYSICAL','高温','烘干房','WORKSHOP',NULL,28.500,'℃','WBGT',33.000,'GBZ 2.2-2007','PASS','周倩','2026-09-09 13:00:00','','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(45,'OCH-2026-0910-006',NULL,80381,'PHYSICAL','手传振动','打磨工位','WORKSHOP',NULL,3.200,'m/s²','4h等能量',5.000,'GBZ 2.2-2007','PASS','周倩','2026-09-10 09:00:00','','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010);
/*!40000 ALTER TABLE `mes_set_occupational_hazard` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_plan`
--

DROP TABLE IF EXISTS `mes_set_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `plan_no` varchar(64) NOT NULL COMMENT '计划编号',
  `plan_name` varchar(200) NOT NULL COMMENT '计划名称',
  `plan_type` varchar(16) NOT NULL DEFAULT 'PERIODIC' COMMENT '触发类型：PERIODIC(周期)/EVENT(事件)',
  `period_type` varchar(16) DEFAULT NULL COMMENT '周期类型(周期型)：DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY',
  `start_date` date DEFAULT NULL COMMENT '生效开始日期',
  `end_date` date DEFAULT NULL COMMENT '生效结束日期',
  `machinery_id` bigint DEFAULT NULL COMMENT '关联设备编号(事件/设备型)',
  `operation_id` bigint DEFAULT NULL COMMENT '关联工序编号(事件/工单型)',
  `standard_id` bigint DEFAULT NULL COMMENT '关联检测标准编号',
  `assignee_id` bigint DEFAULT NULL COMMENT '责任人/执行人编号',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/ACTIVE/STOPPED',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_plan_no` (`plan_no`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全环保检测-检测计划';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_plan`
--

LOCK TABLES `mes_set_plan` WRITE;
/*!40000 ALTER TABLE `mes_set_plan` DISABLE KEYS */;
INSERT INTO `mes_set_plan` VALUES (1,'PLAN-0001','2026年度气体检测计划','YEAR','YEAR','2026-01-01','2026-12-31',NULL,NULL,1,NULL,'ACTIVE','P0冒烟','1','2026-09-02 14:36:42','1','2026-09-11 15:35:52',0x00,1),(2,'PLAN-2026-SET-001','2026年9月职业健康与安全检测计划','PERIODIC','MONTHLY','2026-09-01','2026-09-30',NULL,NULL,NULL,80370,'ACTIVE','含噪声、粉尘、职业危害因素','80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(3,'PLAN-2026-SET-002','国庆节前消防专项检查计划','EVENT',NULL,'2026-09-25','2026-09-30',NULL,NULL,NULL,80371,'DRAFT','全厂消防设施','80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(19,'PLAN-2026-0910-001','2026 年第四季度环境监测计划','PERIODIC','MONTHLY','2026-10-01','2026-12-31',NULL,NULL,NULL,NULL,'DRAFT','按年度监测方案分解','80370','2026-09-10 20:42:22','80370','2026-09-10 20:42:22',0x00,2010),(20,'PLAN-2026-0910-002','2026 年 10 月职业危害检测计划','PERIODIC','MONTHLY','2026-10-01','2026-10-31',NULL,NULL,NULL,NULL,'DRAFT','按年度监测方案分解','80370','2026-09-10 20:42:22','80370','2026-09-10 20:42:22',0x00,2010),(21,'PLAN-2026-0910-003','2026 年下半年特种设备检查计划','YEAR','YEAR','2026-07-01','2026-12-31',NULL,NULL,NULL,NULL,'DRAFT','按年度监测方案分解','80370','2026-09-10 20:42:22','80370','2026-09-10 20:42:22',0x00,2010);
/*!40000 ALTER TABLE `mes_set_plan` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_pollution_check`
--

DROP TABLE IF EXISTS `mes_set_pollution_check`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_pollution_check` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `record_no` varchar(64) NOT NULL COMMENT '记录编号(PC-yyyyMMddHHmmss+随机)',
  `origin_record_no` varchar(64) DEFAULT NULL COMMENT '被变更的原单号（首版为空；非空即为变更单）',
  `amend_reason` varchar(255) DEFAULT NULL COMMENT '变更事由（变更单必填）',
  `superseded_by` varchar(64) DEFAULT NULL COMMENT '替代本单的变更单号（回填，只写一次）',
  `stage` varchar(32) NOT NULL COMMENT '环节：PURCHASE_INBOUND(采购入库)/MATERIAL_ISSUE(生产领用)/WASTE_INTERMEDIATE(中间废弃物)/FINISHED_PRODUCT(成品)',
  `biz_no` varchar(64) DEFAULT NULL COMMENT '关联单号(入库单/领料单/报工单)',
  `batch_id` bigint DEFAULT NULL COMMENT '批次编号（mes_wm_batch.id）；空=历史手工录入未关联，非"无批次"',
  `batch_no` varchar(64) DEFAULT NULL COMMENT '批次号（显示快照，权威值见 batch_id；空=历史未关联记录）',
  `item_code` varchar(64) DEFAULT NULL COMMENT '物料/产品编码',
  `item_name` varchar(200) NOT NULL COMMENT '物料/产品名称(AI 据此初筛)',
  `item_spec` varchar(200) DEFAULT NULL COMMENT '规格',
  `field_signs` varchar(500) DEFAULT NULL COMMENT '现场观察到的污染特征(多选,存特征code,换行分隔)',
  `weight` decimal(12,3) DEFAULT NULL COMMENT '重量(kg)',
  `unit_name` varchar(32) DEFAULT NULL COMMENT '重量单位快照(取自物料主单位;已换算为KG的落KG)',
  `ai_result` varchar(16) DEFAULT NULL COMMENT 'AI 初筛结果：CLEAN(无污染)/POLLUTED(有污染)/UNCERTAIN(不确定)',
  `ai_confidence` int DEFAULT NULL COMMENT 'AI 置信度(0-100)',
  `ai_reason` varchar(1000) DEFAULT NULL COMMENT 'AI 判定依据',
  `ai_basis` varchar(500) DEFAULT NULL COMMENT 'AI 判定的法规依据(法规名+条款号/名录编号+要点)',
  `suggested_storage` varchar(200) DEFAULT NULL COMMENT 'AI 推荐存储方法',
  `review_result` varchar(16) DEFAULT NULL COMMENT '人工复核结果：CLEAN/POLLUTED；空=待复核',
  `finished_result` varchar(32) DEFAULT NULL COMMENT '成品达标分支：QUALIFIED达标/REWORK局部缺陷返工/SCRAPPED整体报废(仅成品环节)',
  `review_basis` varchar(1000) DEFAULT NULL COMMENT '人工复核的判定依据(复选法规条款,换行分隔)',
  `review_by` varchar(64) DEFAULT NULL COMMENT '复核人',
  `review_time` datetime DEFAULT NULL COMMENT '复核时间',
  `disposition` varchar(32) DEFAULT NULL COMMENT '处置方式：NORMAL_INBOUND/CONTROLLED_STORAGE/ISSUE_ALLOWED/REJECT_ISSUE/REUSE/DISCHARGE/ISOLATE_STORAGE/MARKED_STORAGE',
  `storage_method` varchar(200) DEFAULT NULL COMMENT '最终存储方法',
  `location` varchar(200) DEFAULT NULL COMMENT '去向/库位',
  `location_id` bigint DEFAULT NULL COMMENT '受控库位编号（mes_wm_warehouse_area.id；location 为其名称快照）',
  `marked` bit(1) DEFAULT NULL COMMENT '是否标记(有污染默认标记)',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record_no` (`record_no`),
  KEY `idx_batch_id` (`batch_id`),
  KEY `idx_origin_record_no` (`origin_record_no`),
  KEY `idx_superseded_by` (`superseded_by`)
) ENGINE=InnoDB AUTO_INCREMENT=1702 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全环保检测-环保污染判定记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_pollution_check`
--

LOCK TABLES `mes_set_pollution_check` WRITE;
/*!40000 ALTER TABLE `mes_set_pollution_check` DISABLE KEYS */;
INSERT INTO `mes_set_pollution_check` VALUES (1681,'PC-20260920170557281',NULL,NULL,NULL,'WASTE_INTERMEDIATE',NULL,NULL,NULL,'HZ-AC-001','废活性炭','HW49 900-039-49',NULL,12.500,'KG','UNCERTAIN',69,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','POLLUTED',NULL,'《国家危险废物名录（2025年版）》：判断是否属危险废物及其废物类别（HW 类别）。','刘洋','2026-09-20 17:05:58','ISOLATE_STORAGE','隔离待检库位暂存，待人工复核/送检后确定存储','危废暂存间A-01',728,0x01,'更换活性炭吸附箱产生，吸附有机物后属危险废物，已委外处置','80370','2026-09-20 17:05:58','80370','2026-09-20 17:05:57',0x00,2010),(1682,'PC-20260920170558098',NULL,NULL,NULL,'WASTE_INTERMEDIATE',NULL,NULL,NULL,'HZ-OIL-001','废矿物油','HW08 900-249-08',NULL,85.000,'KG','UNCERTAIN',60,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','POLLUTED',NULL,'《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十九条：产生危险废物的单位应当按照国家有关规定和环境保护标准要求贮存、利用、处置危险废物，不得擅自倾倒、堆放。','刘洋','2026-09-20 17:05:58','ISOLATE_STORAGE','隔离待检库位暂存，待人工复核/送检后确定存储','危废暂存间A-02',729,0x01,'设备换油产生，含重金属杂质，转有资质单位再生利用中','80370','2026-09-20 17:05:58','80370','2026-09-20 17:05:58',0x00,2010),(1683,'PC-20260920170558334',NULL,NULL,NULL,'WASTE_INTERMEDIATE',NULL,NULL,NULL,'HZ-BEN-001','含苯清洗废液','HW06 900-402-06',NULL,40.000,'KG','POLLUTED',95,'命中污染特征词「苯」，需按污染受控处置','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十九条（按环境保护标准要求贮存、处置，不得擅自倾倒堆放）、第八十一条（按危险废物特性分类贮存，禁止与性质不相容废物混放）；疑似属《国家危险废物名录（2025年版）》管控，具体废物类别需按名录比对核实','污染/危废受控存储：密封防渗容器、专用污染管控库位、贴污染标识并登记暂存台账，避免混放','POLLUTED',NULL,'《国家危险废物名录（2025年版）》：判断是否属危险废物及其废物类别（HW 类别）。','刘洋','2026-09-20 17:05:58','ISOLATE_STORAGE','污染/危废受控存储：密封防渗容器、专用污染管控库位、贴污染标识并登记暂存台账，避免混放','危废暂存间C-03',730,0x01,'喷涂线清洗产生，含苯系物，危废暂存间C-03 隔离暂存','80370','2026-09-20 17:05:58','80370','2026-09-20 17:05:58',0x00,2010),(1684,'PC-20260920170558076',NULL,NULL,NULL,'WASTE_INTERMEDIATE',NULL,NULL,NULL,'HZ-EMU-001','废乳化液','HW09 900-006-09',NULL,60.000,'KG','UNCERTAIN',68,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','POLLUTED',NULL,'《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十九条：产生危险废物的单位应当按照国家有关规定和环境保护标准要求贮存、利用、处置危险废物，不得擅自倾倒、堆放。','刘洋','2026-09-20 17:05:59','ISOLATE_STORAGE','隔离待检库位暂存，待人工复核/送检后确定存储','危废暂存间A-01',728,0x01,'机加工冷却液更换产生，破乳后回收基础油','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:58',0x00,2010),(1685,'PC-20260920170558310',NULL,NULL,NULL,'WASTE_INTERMEDIATE',NULL,NULL,NULL,'HZ-RAG-001','沾染油污的废抹布','HW49 900-041-49',NULL,8.000,'KG','UNCERTAIN',61,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','POLLUTED',NULL,'《国家危险废物名录（2025年版）》：判断是否属危险废物及其废物类别（HW 类别）。','刘洋','2026-09-20 17:05:59','ISOLATE_STORAGE','隔离待检库位暂存，待人工复核/送检后确定存储','危废暂存间A-02',729,0x01,'设备检修擦拭产生，沾染矿物油，已按危废焚烧处置','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:58',0x00,2010),(1686,'PC-20260920170559576',NULL,NULL,NULL,'WASTE_INTERMEDIATE',NULL,NULL,NULL,'HZ-DRM-001','废包装桶（含残液）','HW49 900-041-49',NULL,15.000,'KG','UNCERTAIN',57,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','POLLUTED',NULL,'《国家危险废物名录（2025年版）》：判断是否属危险废物及其废物类别（HW 类别）。','刘洋','2026-09-20 17:05:59','ISOLATE_STORAGE','隔离待检库位暂存，待人工复核/送检后确定存储','危废暂存间C-03',730,0x01,'原料桶倒空后残留，属危废，暂存待委外','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:59',0x00,2010),(1687,'PC-20260920170559613',NULL,NULL,NULL,'WASTE_INTERMEDIATE',NULL,NULL,NULL,'HZ-SLD-001','污水站沉淀污泥','HW17 336-064-17',NULL,120.000,'KG','UNCERTAIN',58,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','POLLUTED',NULL,'《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十九条：产生危险废物的单位应当按照国家有关规定和环境保护标准要求贮存、利用、处置危险废物，不得擅自倾倒、堆放。','刘洋','2026-09-20 17:05:59','ISOLATE_STORAGE','隔离待检库位暂存，待人工复核/送检后确定存储','危废暂存间A-01',728,0x01,'污水站物化沉淀产生，含重金属，脱水后委外处置','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:59',0x00,2010),(1688,'PC-20260920170559501',NULL,NULL,'PC-20260920170605415','WASTE_INTERMEDIATE',NULL,NULL,NULL,'HZ-COT-001','岩棉边角料','SW01 一般工业固废',NULL,46.000,'KG','UNCERTAIN',75,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','CLEAN',NULL,NULL,'刘洋','2026-09-20 17:06:00','REUSE','普通仓储','原料A-01',727,0x00,'岩棉切割边角料，无污染，回收作保温填充料','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:05',0x00,2010),(1689,'PC-20260920170559935',NULL,NULL,NULL,'WASTE_INTERMEDIATE',NULL,NULL,NULL,'HZ-PU-001','聚氨酯保温管边角料','SW01 一般工业固废',NULL,33.000,'KG','UNCERTAIN',72,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','CLEAN',NULL,NULL,'刘洋','2026-09-20 17:06:00','REUSE','普通仓储','原料A-01',727,0x00,'聚氨酯发泡边角料，无污染，破碎回用','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(1690,'PC-20260920170600727',NULL,NULL,NULL,'WASTE_INTERMEDIATE',NULL,NULL,NULL,'HZ-GLW-001','废玻璃棉','SW01 一般工业固废',NULL,21.000,'KG','CLEAN',85,'命中安全特征词「玻璃」，未检出污染特征','《中华人民共和国固体废物污染环境防治法》（2020年修订）第二十条（采取防扬散、防流失、防渗漏措施）、第三十六条（建立工业固体废物全过程责任制度与管理台账）','普通仓储（常温、通风、防潮常规存放）',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(1691,'PC-20260920170600858',NULL,NULL,NULL,'MATERIAL_ISSUE',NULL,1007,'BATCH_ITEM_94','IF20250312003','小包装盒',NULL,NULL,300.000,'个','UNCERTAIN',75,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','POLLUTED',NULL,NULL,'刘洋','2026-09-20 17:06:00','REJECT_ISSUE','隔离待检库位暂存，待人工复核/送检后确定存储','危废暂存间A-02',729,0x01,'领用抽检检出含铅油墨残留，拒绝领用转受控存储','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(1692,'PC-20260920170600823',NULL,NULL,NULL,'MATERIAL_ISSUE',NULL,1008,'BATCH_ITEM_72','IF2022082404','钢筋','100mm X  5mm',NULL,500.000,'米','UNCERTAIN',57,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','POLLUTED',NULL,NULL,'刘洋','2026-09-20 17:06:01','REJECT_ISSUE','隔离待检库位暂存，待人工复核/送检后确定存储','危废暂存间A-01',728,0x01,'表面锈蚀超限，按受控流程隔离，待除锈复检','80370','2026-09-20 17:06:01','80370','2026-09-20 17:06:00',0x00,2010),(1693,'PC-20260920170600387',NULL,NULL,NULL,'FINISHED_PRODUCT',NULL,1008,'BATCH_ITEM_72','IF2022082404','钢筋','100mm X  5mm',NULL,500.000,'米','UNCERTAIN',57,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','CLEAN','QUALIFIED',NULL,'刘洋','2026-09-20 17:06:01','NORMAL_INBOUND','普通仓储','原料A-01',727,0x00,'除锈后复检合格，判无污染，解除同批隔离并放行','80370','2026-09-20 17:06:01','80370','2026-09-20 17:06:01',0x00,2010),(1694,'PC-20260920170601619',NULL,NULL,NULL,'FINISHED_PRODUCT',NULL,1003,'PC202600001','IF2022082439','螺丝刀【蓝色，一字型】','蓝色，一字型，3.2x75mm',NULL,200.000,'个','UNCERTAIN',65,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','CLEAN','QUALIFIED',NULL,'刘洋','2026-09-20 17:06:02','NORMAL_INBOUND','普通仓储','原料A-01',727,0x00,'出厂检测无污染，达标入库','80370','2026-09-20 17:06:01','80370','2026-09-20 17:06:01',0x00,2010),(1695,'PC-20260920170601418',NULL,NULL,NULL,'IN_STOCK',NULL,1003,'PC202600001','IF2022082439','螺丝刀【蓝色，一字型】','蓝色，一字型，3.2x75mm',NULL,60.000,'个','UNCERTAIN',58,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','CLEAN',NULL,NULL,'刘洋','2026-09-20 17:06:02','NORMAL_INBOUND','普通仓储','原料A-01',727,0x00,'在库抽检无污染；该批已无待检行，解冻放行','80370','2026-09-20 17:06:01','80370','2026-09-20 17:06:01',0x00,2010),(1696,'PC-20260920170601449',NULL,NULL,NULL,'FINISHED_PRODUCT',NULL,1010,'PC202600003','SF-BLACK-001','ABC','EFG',NULL,120.000,'KG','UNCERTAIN',61,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','POLLUTED','SCRAPPED',NULL,'刘洋','2026-09-20 17:06:02','CONTROLLED_STORAGE','隔离待检库位暂存，待人工复核/送检后确定存储','危废暂存间C-03',730,0x01,'整批性能不达标，整体报废并受控存储，批次锁定不出库','80370','2026-09-20 17:06:02','80370','2026-09-20 17:06:01',0x00,2010),(1697,'PC-20260920170602587',NULL,NULL,NULL,'PURCHASE_INBOUND','IR-HZ-20260603001',1009,'RAW-BATCH-001','IF20250312003','小包装盒',NULL,NULL,1000.000,'个','UNCERTAIN',56,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','CLEAN',NULL,NULL,'刘洋','2026-09-20 17:06:02','NORMAL_INBOUND','普通仓储','原料A-01',727,0x00,'到货抽检无污染，正常入库','80370','2026-09-20 17:06:02','80370','2026-09-20 17:06:02',0x00,2010),(1698,'PC-20260920170602957',NULL,NULL,NULL,'PURCHASE_INBOUND','IR-HZ-20260520001',1007,'BATCH_ITEM_94','IF20250312003','小包装盒',NULL,NULL,500.000,'个','UNCERTAIN',72,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','POLLUTED',NULL,NULL,'刘洋','2026-09-20 17:06:03','CONTROLLED_STORAGE','隔离待检库位暂存，待人工复核/送检后确定存储','危废暂存间A-02',729,0x01,'包装破损渗漏，按受控入库隔离暂存','80370','2026-09-20 17:06:03','80370','2026-09-20 17:06:02',0x00,2010),(1699,'PC-20260920170602477',NULL,NULL,NULL,'IN_STOCK',NULL,1006,'BATCH_ITEM_2','IF20250312003','小包装盒',NULL,NULL,150.000,'个','UNCERTAIN',64,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','CLEAN',NULL,NULL,'刘洋','2026-09-20 17:06:03','NORMAL_INBOUND','普通仓储','原料A-01',727,0x00,'在库复检无异常','80370','2026-09-20 17:06:03','80370','2026-09-20 17:06:02',0x00,2010),(1700,'PC-20260920170603894',NULL,NULL,NULL,'IN_STOCK',NULL,1005,'BATCH_ITEM_1','IF2022082404','钢筋',NULL,NULL,400.000,'米','UNCERTAIN',66,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'80370','2026-09-20 17:06:03','80370','2026-09-20 17:06:03',0x00,2010),(1701,'PC-20260920170605415','PC-20260920170559501','第三方复检报告 HS-2026-0917 检出石棉（属危险废物），原判「无污染可回收」有误',NULL,'WASTE_INTERMEDIATE',NULL,NULL,NULL,'HZ-COT-001','岩棉切割边角料','SW01 一般工业固废',NULL,46.000,'KG','UNCERTAIN',60,'未命中污染词典与安全白名单，请人工复核/必要时送检','《中华人民共和国固体废物污染环境防治法》（2020年修订）第七十五条（危险废物鉴别标准与鉴别方法由国务院生态环境主管部门规定）；现有信息不足以定性，建议人工复核或送检鉴别后确定','隔离待检库位暂存，待人工复核/送检后确定存储','POLLUTED',NULL,'《国家危险废物名录（2025年版）》：判断是否属危险废物及其废物类别（HW 类别）。','刘洋','2026-09-20 17:06:06','ISOLATE_STORAGE','隔离待检库位暂存，待人工复核/送检后确定存储','危废暂存间C-03',730,0x01,'按石棉指标改判有污染，转危废暂存间C-03 隔离暂存','80370','2026-09-20 17:06:05','80370','2026-09-20 17:06:05',0x00,2010);
/*!40000 ALTER TABLE `mes_set_pollution_check` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_pollution_discharge`
--

DROP TABLE IF EXISTS `mes_set_pollution_discharge`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_pollution_discharge` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `ledger_id` bigint DEFAULT NULL COMMENT '来源台账编号',
  `source_record_no` varchar(64) DEFAULT NULL COMMENT '来源判定记录号(PC-...；与追溯链同键)',
  `stage` varchar(32) DEFAULT NULL COMMENT '环节（中间废弃物等）',
  `batch_no` varchar(64) DEFAULT NULL COMMENT '批次号（可为空，中间废弃物无批次）',
  `item_code` varchar(64) DEFAULT NULL COMMENT '物料/产品编码',
  `item_name` varchar(128) DEFAULT NULL COMMENT '物料/产品名称',
  `item_spec` varchar(128) DEFAULT NULL COMMENT '规格',
  `destination` varchar(200) DEFAULT NULL COMMENT '排放去向（管线/处理站/回用渠等）',
  `standard` varchar(200) DEFAULT NULL COMMENT '执行排放标准/达标口径',
  `discharge_time` datetime DEFAULT NULL COMMENT '排放(登记)时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '登记人(创建者)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_source_record_no` (`source_record_no`),
  KEY `idx_ledger_id` (`ledger_id`),
  KEY `idx_discharge_time` (`discharge_time`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全环保检测-排放合规流水';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_pollution_discharge`
--

LOCK TABLES `mes_set_pollution_discharge` WRITE;
/*!40000 ALTER TABLE `mes_set_pollution_discharge` DISABLE KEYS */;
INSERT INTO `mes_set_pollution_discharge` VALUES (2,1015,'PC-20260903163248987-H','MATERIAL_ISSUE','RAW-BATCH-001','IF20250312003','含铅焊锡丝','0.8mm','危废处置单位·东方环保','GB18597-2023','2026-09-10 08:37:49','含铅焊锡丝委外处置，登记人昵称代码路径复验','刘洋','2026-09-10 08:37:49','80370','2026-09-10 08:37:49',0x00,2010);
/*!40000 ALTER TABLE `mes_set_pollution_discharge` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_pollution_permit`
--

DROP TABLE IF EXISTS `mes_set_pollution_permit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_pollution_permit` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `permit_no` varchar(64) NOT NULL COMMENT '排污许可证号',
  `enterprise_name` varchar(200) NOT NULL COMMENT '排污单位名称(本企业)',
  `issuing_authority` varchar(200) DEFAULT '' COMMENT '发证机关',
  `issue_date` date DEFAULT NULL COMMENT '发证日期',
  `start_date` date DEFAULT NULL COMMENT '许可有效期起',
  `end_date` date DEFAULT NULL COMMENT '许可有效期止',
  `outlet_codes` varchar(1000) DEFAULT NULL COMMENT '绑定的排放口编号列表',
  `annual_limits` varchar(4000) DEFAULT NULL COMMENT '许可排放量JSON文本(驱动80%预警/100%报警)',
  `annual_reports` varchar(4000) DEFAULT NULL COMMENT '年度执行报告JSON文本',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/EXPIRED/REVOKED',
  `remark` varchar(500) DEFAULT '' COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permit_no` (`permit_no`)
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MES SET-排污许可证';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_pollution_permit`
--

LOCK TABLES `mes_set_pollution_permit` WRITE;
/*!40000 ALTER TABLE `mes_set_pollution_permit` DISABLE KEYS */;
INSERT INTO `mes_set_pollution_permit` VALUES (1,'PERMIT-SMOKE-1','冒烟测试企业','北京市生态环境局','2026-01-01','2026-01-01','2029-01-01',NULL,NULL,NULL,'ACTIVE','','1','2026-09-02 15:33:10','1','2026-09-09 18:59:52',0x01,1),(2,'P-110108-2026-0001','华翰智能制造(北京)有限公司','北京市海淀区生态环境局','2026-01-15','2026-01-15','2029-01-14','DA-001,DA-002','[{\"pollutantCode\":\"颗粒物\",\"pollutantName\":\"颗粒物\",\"annualLimitT\":1.2,\"annualUsedT\":0},{\"pollutantCode\":\"SO2\",\"pollutantName\":\"二氧化硫\",\"annualLimitT\":3,\"annualUsedT\":0},{\"pollutantCode\":\"VOCs\",\"pollutantName\":\"非甲烷总烃\",\"annualLimitT\":12,\"annualUsedT\":0}]',NULL,'ACTIVE',NULL,'80370','2026-09-02 10:00:00','80370','2026-09-10 10:19:18',0x00,2010),(3,'P-110108-2026-0002','华翰智能制造(北京)有限公司','北京市海淀区生态环境局','2023-11-01','2023-11-01','2026-10-31','DA-002','[{\"pollutantCode\":\"COD\",\"pollutantName\":\"化学需氧量\",\"annualLimitT\":0.5,\"annualUsedT\":0},{\"pollutantCode\":\"氨氮\",\"pollutantName\":\"氨氮\",\"annualLimitT\":0.05,\"annualUsedT\":0}]',NULL,'ACTIVE','证载有效期止2026-10-31，临近换证，请提前办理换证申请','80370','2026-09-02 10:00:00','80370','2026-09-10 10:19:18',0x00,2010),(4,'P-110108-2026-0003','华翰智能制造(北京)有限公司','北京市海淀区生态环境局','2026-09-09','2026-09-09','2029-09-08','DA001,DA003','[{\"pollutantCode\":\"VOCs\",\"pollutantName\":\"挥发性有机物\",\"annualLimitT\":12,\"annualUsedT\":0}]',NULL,'REVOKED','排污许可模块落成接口验证创建','80370','2026-09-09 19:18:47','80370','2026-09-10 16:11:35',0x01,2010);
/*!40000 ALTER TABLE `mes_set_pollution_permit` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_ppe_check`
--

DROP TABLE IF EXISTS `mes_set_ppe_check`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_ppe_check` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `record_no` varchar(64) NOT NULL COMMENT '记录编号 PPE-YYYYMMDD-NNN',
  `emp_id` bigint NOT NULL COMMENT '关联人员编号(佩戴校验对象)',
  `wo_id` bigint DEFAULT NULL COMMENT '关联工单编号',
  `operation_id` bigint DEFAULT NULL COMMENT '关联工序编号',
  `ppe_type` varchar(32) NOT NULL COMMENT 'PPE类别：HELMET/GOGGLES/RESPIRATOR/ANTISTATIC_CLOTHING/EARPLUGS/GLOVES/SAFETY_SHOES等',
  `check_mode` varchar(16) DEFAULT 'MANUAL' COMMENT '检查方式：AI_VISION/MANUAL',
  `wearing_ok` tinyint(1) DEFAULT NULL COMMENT '佩戴完整性：1是/0否',
  `grade_match_ok` tinyint(1) DEFAULT NULL COMMENT '防护等级匹配性：1是/0否',
  `valid_ok` tinyint(1) DEFAULT NULL COMMENT '有效期/损坏检查：1是/0否',
  `expiry_date` date DEFAULT NULL COMMENT 'PPE到期日期',
  `result` varchar(8) DEFAULT NULL COMMENT '结果：PASS/FAIL',
  `block_flag` tinyint(1) DEFAULT NULL COMMENT '是否阻断开工(FAIL时=1)',
  `device_no` varchar(64) DEFAULT '' COMMENT 'AI识别设备编号',
  `checker_name` varchar(64) DEFAULT '' COMMENT '检查人(人工检查时)',
  `check_time` datetime NOT NULL COMMENT '检查时间',
  `photo_urls` varchar(2000) DEFAULT NULL COMMENT '检查照片/AI抓拍URL(逗号分隔)',
  `remark` varchar(500) DEFAULT '' COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_record_no` (`record_no`)
) ENGINE=InnoDB AUTO_INCREMENT=47 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MES SET-PPE防护检查记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_ppe_check`
--

LOCK TABLES `mes_set_ppe_check` WRITE;
/*!40000 ALTER TABLE `mes_set_ppe_check` DISABLE KEYS */;
INSERT INTO `mes_set_ppe_check` VALUES (1,'PPE-SMOKE-1',1,NULL,NULL,'HELMET','MANUAL',1,NULL,NULL,NULL,'PASS',NULL,'','','2026-09-02 11:00:00',NULL,'','1','2026-09-02 15:33:09','1','2026-09-02 15:33:27',0x01,1),(2,'PPE-2026-0901-001',80370,NULL,NULL,'安全帽','MANUAL',1,1,1,'2027-03-01','PASS',0,'PPE-H-001','刘洋','2026-09-01 08:30:00',NULL,NULL,'80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(3,'PPE-2026-0901-002',80371,NULL,NULL,'防尘口罩','AI_VISION',0,1,1,'2026-12-01','FAIL',1,'PPE-M-002','刘洋','2026-09-01 08:45:00',NULL,'未规范佩戴，已拦截','80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(40,'PPE-2026-0910-001',80371,NULL,NULL,'GAS_MASK','MANUAL',1,1,1,'2027-08-31','PASS',0,'PPE-GATE-01','周倩','2026-09-02 07:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','','80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(41,'PPE-2026-0910-002',80373,NULL,NULL,'CHEM_SUIT','MANUAL',1,1,1,'2028-05-31','PASS',0,'PPE-GATE-02','周倩','2026-09-04 08:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','','80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(42,'PPE-2026-0910-003',80375,NULL,NULL,'GOGGLES','AI_VISION',0,1,1,'2027-12-31','FAIL',0,'PPE-GATE-03','周倩','2026-09-06 09:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','未通过者已拦下并补发合格劳保','80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(43,'PPE-2026-0910-004',80377,NULL,NULL,'EARPLUG','MANUAL',1,1,1,'2027-06-30','PASS',0,'PPE-GATE-04','周倩','2026-09-08 07:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','','80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(44,'PPE-2026-0910-005',80379,NULL,NULL,'DUST_MASK','MANUAL',1,1,0,'2026-10-15','FAIL',1,'PPE-GATE-05','周倩','2026-09-09 08:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','未通过者已拦下并补发合格劳保','80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(45,'PPE-2026-0910-006',80381,NULL,NULL,'GLOVES','AI_VISION',1,1,1,'2027-03-31','PASS',0,'PPE-GATE-06','周倩','2026-09-10 09:00:00','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','','80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010);
/*!40000 ALTER TABLE `mes_set_ppe_check` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_pressure_vessel`
--

DROP TABLE IF EXISTS `mes_set_pressure_vessel`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_pressure_vessel` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `record_no` varchar(64) NOT NULL COMMENT '检验记录/报告编号 PV-YYYY-NNN',
  `plan_id` bigint DEFAULT NULL COMMENT '关联检测计划编号',
  `device_id` bigint NOT NULL COMMENT '关联设备编号(特种设备台账)',
  `vessel_reg_no` varchar(64) DEFAULT '' COMMENT '压力容器使用登记证号(特种设备注册代码)',
  `wall_thickness` decimal(8,3) DEFAULT NULL COMMENT '壁厚测定最小壁厚 mm',
  `ndt_methods` varchar(64) DEFAULT '' COMMENT '无损检测方法：UT/RT/MT/PT(多选逗号分隔)',
  `ndt_results` varchar(1000) DEFAULT NULL COMMENT '无损检测结果JSON文本如{UT:PASS,MT:PASS}',
  `safety_valve_ok` tinyint(1) DEFAULT NULL COMMENT '安全阀校验合格：1是/0否',
  `pressure_test_value` decimal(10,3) DEFAULT NULL COMMENT '耐压试验压力 MPa',
  `pressure_test_result` varchar(8) DEFAULT NULL COMMENT '耐压试验结果 PASS/FAIL',
  `process_pressure` decimal(10,3) DEFAULT NULL COMMENT '工艺允许压力 MPa',
  `result` varchar(8) DEFAULT NULL COMMENT '综合结论 PASS/FAIL',
  `inspect_org` varchar(200) NOT NULL COMMENT '检验机构(需资质)',
  `next_inspect_date` date NOT NULL COMMENT '下次检验日期',
  `report_file_url` varchar(500) DEFAULT '' COMMENT '检验报告文件URL',
  `inspector` varchar(64) DEFAULT '' COMMENT '检验人',
  `inspect_time` datetime NOT NULL COMMENT '检验时间',
  `remark` varchar(500) DEFAULT '' COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_record_no` (`record_no`)
) ENGINE=InnoDB AUTO_INCREMENT=48 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MES SET-压力容器检测记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_pressure_vessel`
--

LOCK TABLES `mes_set_pressure_vessel` WRITE;
/*!40000 ALTER TABLE `mes_set_pressure_vessel` DISABLE KEYS */;
INSERT INTO `mes_set_pressure_vessel` VALUES (1,'PV-SMOKE-1',NULL,1,'',NULL,'',NULL,NULL,NULL,NULL,NULL,'PASS','市特检院','2027-09-02','','','2026-09-02 11:00:00','','1','2026-09-02 15:33:08','1','2026-09-10 16:11:35',0x01,1),(2,'PV-2026-0901-001',NULL,1,'容1LS京A0001',6.500,'超声检测','合格',1,1.600,'合格',1.000,'PASS','北京市特种设备检验研究院','2027-09-01','','刘洋','2026-09-01 09:30:00','','80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(3,'PV-2026-0901-002',NULL,2,'容1LS京A0002',8.000,'磁粉检测','合格',1,2.500,'合格',2.000,'PASS','北京市特种设备检验研究院','2027-09-01','','刘洋','2026-09-01 10:00:00','','80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(41,'PV-2026-0910-001',NULL,1,'BJ-2021-0087',12.400,'超声检测+磁粉检测','未发现超标缺陷',1,1.600,'合格',0.800,'PASS','北京市特种设备检验检测研究院','2029-05-20','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','吴斌','2026-09-02 09:00:00','','80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(42,'PV-2026-0910-002',NULL,2,'BJ-2021-0088',11.900,'射线检测','焊缝合格',1,1.600,'合格',0.750,'PASS','北京市特种设备检验检测研究院','2029-06-15','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','吴斌','2026-09-04 10:00:00','','80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(43,'PV-2026-0910-003',NULL,3,'BJ-2022-0154',10.200,'超声检测','筒体发现 2mm 腐蚀坑，打磨后复测合格',1,1.600,'合格',0.900,'PASS','中国特种设备检测研究院','2029-03-10','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','吴斌','2026-09-06 11:00:00','','80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(44,'PV-2026-0910-004',NULL,4,'BJ-2022-0155',13.500,'磁粉检测+渗透检测','未发现表面裂纹',1,1.600,'合格',0.850,'PASS','中国特种设备检测研究院','2029-04-22','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','吴斌','2026-09-08 12:00:00','','80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(45,'PV-2026-0910-005',NULL,5,'BJ-2023-0210',9.800,'超声检测','壁厚低于设计值，需降压使用',0,1.400,'不合格',0.700,'FAIL','北京市特种设备检验检测研究院','2026-12-01','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','吴斌','2026-09-09 09:00:00','建议降额定压力运行并加密巡检','80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010),(46,'PV-2026-0910-006',NULL,6,'BJ-2023-0211',12.100,'射线检测','焊缝合格',1,1.600,'合格',0.800,'PASS','中国特种设备检测研究院','2029-07-30','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','吴斌','2026-09-10 10:00:00','','80370','2026-09-10 20:42:20','80370','2026-09-10 20:42:20',0x00,2010);
/*!40000 ALTER TABLE `mes_set_pressure_vessel` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_sign_record`
--

DROP TABLE IF EXISTS `mes_set_sign_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_sign_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `biz_type` varchar(32) NOT NULL COMMENT '业务关联类型：CHECK/LEDGER',
  `biz_no` varchar(64) NOT NULL COMMENT '业务关联单号（判定 recordNo 等）',
  `sign_role` varchar(32) NOT NULL COMMENT '签字角色：REVIEWER(复核)/OPERATOR(录入)/APPROVER(审批)；预留',
  `sign_user` varchar(64) DEFAULT NULL COMMENT '签字人',
  `sign_user_id` bigint DEFAULT NULL COMMENT '签字账号ID（账号即签名；五双双人制按此判重，历史行可为空）',
  `operator_user_id` bigint DEFAULT NULL COMMENT '实际操作账号ID(代签时≠sign_user_id)',
  `sign_time` datetime DEFAULT NULL COMMENT '签字时间',
  `location` varchar(128) DEFAULT NULL COMMENT '签字地点/去向',
  `opinion` varchar(255) DEFAULT NULL COMMENT '签署意见',
  `sign_img` varchar(255) DEFAULT NULL COMMENT '手写签字图片（未接 infra 文件服务前为空，预留）',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_biz` (`biz_type`,`biz_no`),
  KEY `idx_sign_record_user_id` (`sign_user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1184 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全环保检测-签字记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_sign_record`
--

LOCK TABLES `mes_set_sign_record` WRITE;
/*!40000 ALTER TABLE `mes_set_sign_record` DISABLE KEYS */;
INSERT INTO `mes_set_sign_record` VALUES (307,'CHEMICAL_DOUBLE','CHM-2026-0910-001','RECEIVE','刘洋',80370,NULL,'2026-09-10 20:42:17',NULL,'TDI 入库 120kg，双人核验','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E7%AD%BE%E5%90%8D%E5%BA%95%E5%9B%BE.png','80370','2026-09-10 20:42:17','80370','2026-09-10 20:42:17',0x00,2010),(308,'CHEMICAL_DOUBLE','CHM-2026-0910-001','RECEIVE','陈静',80371,NULL,'2026-09-10 20:42:17',NULL,'TDI 入库 120kg，双人核验','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E7%AD%BE%E5%90%8D%E5%BA%95%E5%9B%BE.png','80371','2026-09-10 20:42:17','80371','2026-09-10 20:42:17',0x00,2010),(309,'CHEMICAL_DOUBLE','CHM-2026-0910-007','ISSUE','刘洋',80370,NULL,'2026-09-10 20:42:17',NULL,'乙二胺 20kg 领用，待第二人复核','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E7%AD%BE%E5%90%8D%E5%BA%95%E5%9B%BE.png','80370','2026-09-10 20:42:17','80370','2026-09-10 20:42:17',0x00,2010),(310,'HAZWASTE_MANIFEST','LD-2026-0910-001','HANDOVER','刘洋',80370,NULL,'2026-09-10 20:42:19',NULL,'移交环保员核验桶码与标签','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E7%AD%BE%E5%90%8D%E5%BA%95%E5%9B%BE.png','80370','2026-09-10 20:42:19','80370','2026-09-10 20:42:19',0x00,2010),(311,'HAZWASTE_MANIFEST','LD-2026-0910-001','DRIVER','刘洋',80370,NULL,'2026-09-10 20:42:19',NULL,'押运司机确认封条完好','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E7%AD%BE%E5%90%8D%E5%BA%95%E5%9B%BE.png','80370','2026-09-10 20:42:19','80370','2026-09-10 20:42:19',0x00,2010),(312,'HAZWASTE_MANIFEST','LD-2026-0910-001','RECEIVER','刘洋',80370,NULL,'2026-09-10 20:42:19',NULL,'接收方确认品类与净重','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E7%AD%BE%E5%90%8D%E5%BA%95%E5%9B%BE.png','80370','2026-09-10 20:42:19','80370','2026-09-10 20:42:19',0x00,2010),(313,'HAZWASTE_MANIFEST','LD-2026-0910-001','GUARD','刘洋',80370,NULL,'2026-09-10 20:42:19',NULL,'门卫核验放行','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E7%AD%BE%E5%90%8D%E5%BA%95%E5%9B%BE.png','80370','2026-09-10 20:42:19','80370','2026-09-10 20:42:19',0x00,2010),(314,'HAZWASTE_MANIFEST','LD-2026-0910-002','HANDOVER','刘洋',80370,NULL,'2026-09-10 20:42:19',NULL,'仅移交方签字','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E7%AD%BE%E5%90%8D%E5%BA%95%E5%9B%BE.png','80370','2026-09-10 20:42:19','80370','2026-09-10 20:42:19',0x00,2010),(315,'EMERGENCY','EV-2026-0910-001','APPROVER','刘洋',80370,NULL,'2026-09-10 20:42:22',NULL,'应急事件报告签发闭环','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E7%AD%BE%E5%90%8D%E5%BA%95%E5%9B%BE.png','80370','2026-09-10 20:42:22','80370','2026-09-10 20:42:22',0x00,2010),(1139,'CHECK','PC-20260920170557281','OPERATOR','刘洋',80370,80370,'2026-09-20 17:05:58',NULL,'发起检测：废活性炭 现场取样送检','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:05:58','80370','2026-09-20 17:05:58',0x00,2010),(1140,'CHECK','PC-20260920170557281','REVIEWER','刘洋',NULL,NULL,'2026-09-20 17:05:58','危废暂存间A-01','复核判定：有污染，须受控处置','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:05:58','80370','2026-09-20 17:05:58',0x00,2010),(1141,'CHECK','PC-20260920170558098','OPERATOR','刘洋',80370,80370,'2026-09-20 17:05:58',NULL,'发起检测：废矿物油 现场取样送检','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:05:58','80370','2026-09-20 17:05:58',0x00,2010),(1142,'CHECK','PC-20260920170558098','REVIEWER','刘洋',NULL,NULL,'2026-09-20 17:05:58','危废暂存间A-02','复核判定：有污染，须受控处置','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:05:58','80370','2026-09-20 17:05:58',0x00,2010),(1143,'CHECK','PC-20260920170558334','OPERATOR','刘洋',80370,80370,'2026-09-20 17:05:58',NULL,'发起检测：含苯清洗废液 现场取样送检','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:05:58','80370','2026-09-20 17:05:58',0x00,2010),(1144,'CHECK','PC-20260920170558334','REVIEWER','刘洋',NULL,NULL,'2026-09-20 17:05:58','危废暂存间C-03','复核判定：有污染，须受控处置','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:05:58','80370','2026-09-20 17:05:58',0x00,2010),(1145,'CHECK','PC-20260920170558076','OPERATOR','刘洋',80370,80370,'2026-09-20 17:05:59',NULL,'发起检测：废乳化液 现场取样送检','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:59',0x00,2010),(1146,'CHECK','PC-20260920170558076','REVIEWER','刘洋',NULL,NULL,'2026-09-20 17:05:59','危废暂存间A-01','复核判定：有污染，须受控处置','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:59',0x00,2010),(1147,'CHECK','PC-20260920170558310','OPERATOR','刘洋',80370,80370,'2026-09-20 17:05:59',NULL,'发起检测：沾染油污的废抹布 现场取样送检','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:59',0x00,2010),(1148,'CHECK','PC-20260920170558310','REVIEWER','刘洋',NULL,NULL,'2026-09-20 17:05:59','危废暂存间A-02','复核判定：有污染，须受控处置','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:59',0x00,2010),(1149,'CHECK','PC-20260920170559576','OPERATOR','刘洋',80370,80370,'2026-09-20 17:05:59',NULL,'发起检测：废包装桶（含残液） 现场取样送检','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:59',0x00,2010),(1150,'CHECK','PC-20260920170559576','REVIEWER','刘洋',NULL,NULL,'2026-09-20 17:05:59','危废暂存间C-03','复核判定：有污染，须受控处置','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:59',0x00,2010),(1151,'CHECK','PC-20260920170559613','OPERATOR','刘洋',80370,80370,'2026-09-20 17:05:59',NULL,'发起检测：污水站沉淀污泥 现场取样送检','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:59',0x00,2010),(1152,'CHECK','PC-20260920170559613','REVIEWER','刘洋',NULL,NULL,'2026-09-20 17:05:59','危废暂存间A-01','复核判定：有污染，须受控处置','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(1153,'CHECK','PC-20260920170559501','OPERATOR','刘洋',80370,80370,'2026-09-20 17:06:00',NULL,'发起检测：岩棉边角料 现场取样送检','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(1154,'CHECK','PC-20260920170559501','REVIEWER','刘洋',NULL,NULL,'2026-09-20 17:06:00','原料A-01','复核通过：无污染','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(1155,'CHECK','PC-20260920170559935','OPERATOR','刘洋',80370,80370,'2026-09-20 17:06:00',NULL,'发起检测：聚氨酯保温管边角料 现场取样送检','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(1156,'CHECK','PC-20260920170559935','REVIEWER','刘洋',NULL,NULL,'2026-09-20 17:06:00','原料A-01','复核通过：无污染','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(1157,'CHECK','PC-20260920170600727','OPERATOR','刘洋',80370,80370,'2026-09-20 17:06:00',NULL,'发起检测：废玻璃棉 现场取样送检','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(1158,'CHECK','PC-20260920170600858','OPERATOR','刘洋',80370,80370,'2026-09-20 17:06:00',NULL,'发起检测：小包装盒 现场取样送检','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(1159,'CHECK','PC-20260920170600858','REVIEWER','刘洋',NULL,NULL,'2026-09-20 17:06:00','危废暂存间A-02','复核判定：有污染，须受控处置','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(1160,'CHECK','PC-20260920170600823','OPERATOR','刘洋',80370,80370,'2026-09-20 17:06:01',NULL,'发起检测：钢筋 现场取样送检','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:01','80370','2026-09-20 17:06:01',0x00,2010),(1161,'CHECK','PC-20260920170600823','REVIEWER','刘洋',NULL,NULL,'2026-09-20 17:06:01','危废暂存间A-01','复核判定：有污染，须受控处置','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:01','80370','2026-09-20 17:06:01',0x00,2010),(1162,'CHECK','PC-20260920170600387','OPERATOR','刘洋',80370,80370,'2026-09-20 17:06:01',NULL,'发起检测：钢筋 现场取样送检','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:01','80370','2026-09-20 17:06:01',0x00,2010),(1163,'CHECK','PC-20260920170600387','REVIEWER','刘洋',NULL,NULL,'2026-09-20 17:06:01','原料A-01','复核通过：无污染','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:01','80370','2026-09-20 17:06:01',0x00,2010),(1164,'CHECK','PC-20260920170601619','OPERATOR','刘洋',80370,80370,'2026-09-20 17:06:01',NULL,'发起检测：螺丝刀【蓝色，一字型】 现场取样送检','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:01','80370','2026-09-20 17:06:01',0x00,2010),(1165,'CHECK','PC-20260920170601418','OPERATOR','刘洋',80370,80370,'2026-09-20 17:06:01',NULL,'发起检测：螺丝刀【蓝色，一字型】 现场取样送检','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:01','80370','2026-09-20 17:06:01',0x00,2010),(1166,'CHECK','PC-20260920170601619','REVIEWER','刘洋',NULL,NULL,'2026-09-20 17:06:02','原料A-01','复核通过：无污染','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:02','80370','2026-09-20 17:06:02',0x00,2010),(1167,'CHECK','PC-20260920170601418','REVIEWER','刘洋',NULL,NULL,'2026-09-20 17:06:02','原料A-01','复核通过：无污染','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:02','80370','2026-09-20 17:06:02',0x00,2010),(1168,'CHECK','PC-20260920170601449','OPERATOR','刘洋',80370,80370,'2026-09-20 17:06:02',NULL,'发起检测：ABC 现场取样送检','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:02','80370','2026-09-20 17:06:02',0x00,2010),(1169,'CHECK','PC-20260920170601449','REVIEWER','刘洋',NULL,NULL,'2026-09-20 17:06:02','危废暂存间C-03','复核判定：有污染，须受控处置','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:02','80370','2026-09-20 17:06:02',0x00,2010),(1170,'CHECK','PC-20260920170602587','OPERATOR','刘洋',80370,80370,'2026-09-20 17:06:02',NULL,'发起检测：小包装盒 现场取样送检','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:02','80370','2026-09-20 17:06:02',0x00,2010),(1171,'CHECK','PC-20260920170602587','REVIEWER','刘洋',NULL,NULL,'2026-09-20 17:06:02','原料A-01','复核通过：无污染','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:02','80370','2026-09-20 17:06:02',0x00,2010),(1172,'CHECK','PC-20260920170602957','OPERATOR','刘洋',80370,80370,'2026-09-20 17:06:03',NULL,'发起检测：小包装盒 现场取样送检','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:03','80370','2026-09-20 17:06:03',0x00,2010),(1173,'CHECK','PC-20260920170602957','REVIEWER','刘洋',NULL,NULL,'2026-09-20 17:06:03','危废暂存间A-02','复核判定：有污染，须受控处置','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:03','80370','2026-09-20 17:06:03',0x00,2010),(1174,'CHECK','PC-20260920170602477','OPERATOR','刘洋',80370,80370,'2026-09-20 17:06:03',NULL,'发起检测：小包装盒 现场取样送检','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:03','80370','2026-09-20 17:06:03',0x00,2010),(1175,'CHECK','PC-20260920170602477','REVIEWER','刘洋',NULL,NULL,'2026-09-20 17:06:03','原料A-01','复核通过：无污染','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:03','80370','2026-09-20 17:06:03',0x00,2010),(1176,'CHECK','PC-20260920170603894','OPERATOR','刘洋',80370,80370,'2026-09-20 17:06:03',NULL,'发起检测：钢筋 现场取样送检','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:03','80370','2026-09-20 17:06:03',0x00,2010),(1177,'LEDGER','PC-20260920170557281','APPROVER','刘洋',80370,80370,'2026-09-20 17:06:04',NULL,'危废处置流转确认：委托有资质单位焚烧处置，危废转移联单已归档','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:04','80370','2026-09-20 17:06:04',0x00,2010),(1178,'LEDGER','PC-20260920170558098','APPROVER','刘洋',80370,80370,'2026-09-20 17:06:04',NULL,'危废处置流转确认：送再生装置蒸馏提纯中','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:04','80370','2026-09-20 17:06:04',0x00,2010),(1179,'LEDGER','PC-20260920170558076','APPROVER','刘洋',80370,80370,'2026-09-20 17:06:04',NULL,'危废处置流转确认：破乳回收基础油，回用于设备润滑','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:04','80370','2026-09-20 17:06:04',0x00,2010),(1180,'LEDGER','PC-20260920170558310','APPROVER','刘洋',80370,80370,'2026-09-20 17:06:05',NULL,'危废处置流转确认：随批次一并焚烧处置','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:05','80370','2026-09-20 17:06:05',0x00,2010),(1181,'LEDGER','PC-20260920170559613','APPROVER','刘洋',80370,80370,'2026-09-20 17:06:05',NULL,'危废处置流转确认：脱水减量后委外处置中','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:05','80370','2026-09-20 17:06:05',0x00,2010),(1182,'CHECK','PC-20260920170605415','OPERATOR','刘洋',80370,80370,'2026-09-20 17:06:05',NULL,'变更发起：石棉超标需改判危废','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:05','80370','2026-09-20 17:06:05',0x00,2010),(1183,'CHECK','PC-20260920170605415','REVIEWER','刘洋',NULL,NULL,'2026-09-20 17:06:06','危废暂存间C-03','复核判定：有污染，须受控处置','http://127.0.0.1:48080/admin-api/infra/file/4/get/20260920/seed-sign.png','80370','2026-09-20 17:06:06','80370','2026-09-20 17:06:06',0x00,2010);
/*!40000 ALTER TABLE `mes_set_sign_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_standard`
--

DROP TABLE IF EXISTS `mes_set_standard`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_standard` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `standard_no` varchar(64) NOT NULL COMMENT '标准编号',
  `standard_name` varchar(200) NOT NULL COMMENT '标准名称',
  `domain` varchar(16) DEFAULT NULL COMMENT '检测域：SAFETY/ENV/HEALTH',
  `test_type` varchar(32) DEFAULT NULL COMMENT '检测类型：GAS/NOISE/DUST/RADIATION/ELECTRICAL/FIRE/CHEMICAL/PPE/PRESSURE等',
  `ref_standard` varchar(100) DEFAULT NULL COMMENT '引用国标编号（GBZ/GB/T）',
  `limits_config` varchar(4000) DEFAULT NULL COMMENT '限值配置(JSON文本，如 {CO:{mac,pcTWA,unit}})',
  `method` varchar(500) DEFAULT NULL COMMENT '检测方法描述',
  `period_type` varchar(16) DEFAULT NULL COMMENT '周期类型：DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY/EVENT',
  `trigger_config` varchar(1000) DEFAULT NULL COMMENT '事件触发配置(JSON文本)',
  `applicable_area` varchar(1000) DEFAULT NULL COMMENT '适用区域/工序(JSON文本)',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/ACTIVE/OBSOLETE',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_standard_no` (`standard_no`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全环保检测-检测标准';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_standard`
--

LOCK TABLES `mes_set_standard` WRITE;
/*!40000 ALTER TABLE `mes_set_standard` DISABLE KEYS */;
INSERT INTO `mes_set_standard` VALUES (1,'STD-0001','气体检测标准','GAS','现场直播','GBZ/T 189.4-2007','{\"CO\":30,\"O2\":19.5}','现场采样-实验室分析','YEAR',NULL,NULL,'ACTIVE','P0冒烟','1','2026-09-02 14:36:42','1','2026-09-11 15:35:52',0x00,1),(2,'STD-2010-0001','废气采样检测标准','EXHAUST','现场采样',NULL,NULL,NULL,NULL,NULL,NULL,'ACTIVE','租户2010冒烟','80370','2026-09-02 15:10:04','80370','2026-09-11 15:35:52',0x00,2010),(3,'GBZ 2.1-2019','工作场所有害因素职业接触限值 第1部分：化学有害因素','HEALTH','OCCUPATIONAL','GBZ 2.1-2019',NULL,NULL,'YEARLY',NULL,'职业卫生','ACTIVE','强制性国标','80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(4,'GBZ 2.2-2007','工作场所有害因素职业接触限值 第2部分：物理因素','HEALTH','OCCUPATIONAL','GBZ 2.2-2007',NULL,NULL,'YEARLY',NULL,'职业卫生','ACTIVE','噪声/高温/振动限值','80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010);
/*!40000 ALTER TABLE `mes_set_standard` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_trace_chain`
--

DROP TABLE IF EXISTS `mes_set_trace_chain`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_trace_chain` (
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
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_trace_code` (`trace_code`),
  KEY `idx_biz` (`biz_type`,`biz_no`),
  KEY `idx_node_time` (`node_time`)
) ENGINE=InnoDB AUTO_INCREMENT=640 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全环保检测-追溯链节点';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_trace_chain`
--

LOCK TABLES `mes_set_trace_chain` WRITE;
/*!40000 ALTER TABLE `mes_set_trace_chain` DISABLE KEYS */;
INSERT INTO `mes_set_trace_chain` VALUES (178,'EV-2026-0910-001','EMERGENCY','CAS-75-09-2','EMERGENCY','EV-2026-0910-001','EMERGENCY','应急处置完成','DISPOSING','刘洋','2026-09-10 20:42:22','应急废物 1 桶、合计 4.2 kg 已过秤贴签入危废台账','80370','2026-09-10 20:42:22','80370','2026-09-10 20:42:22',0x00,2010),(179,'EV-2026-0910-001','EMERGENCY','CAS-75-09-2','EMERGENCY','EV-2026-0910-001','EMERGENCY','应急事件闭环','PENDING_REPORT','刘洋','2026-09-10 20:42:22','负责人 刘洋 签发事件报告：起因：调漆时稀释剂桶倾倒约 3.2kg；处置：沙土围堵吸附，1 桶过秤 4.2kg 入危废台账；无人员伤害；整改：桶架加装防倒挡块','80370','2026-09-10 20:42:22','80370','2026-09-10 20:42:22',0x00,2010),(180,'EV-2026-0910-002','EMERGENCY',NULL,'EMERGENCY','EV-2026-0910-002','EMERGENCY','应急处置完成','DISPOSING','刘洋','2026-09-10 20:42:22','应急废物 1 桶、合计 5.2 kg 已过秤贴签入危废台账','80370','2026-09-10 20:42:22','80370','2026-09-10 20:42:22',0x00,2010),(614,'PC-20260920170557281','CHECK',NULL,'CHECK','PC-20260920170557281','WASTE_INTERMEDIATE','复核收口：有污染受控，登记台账处置','POLLUTED','刘洋','2026-09-20 17:05:58','危废暂存间A-01','80370','2026-09-20 17:05:58','80370','2026-09-20 17:05:58',0x00,2010),(615,'PC-20260920170558098','CHECK',NULL,'CHECK','PC-20260920170558098','WASTE_INTERMEDIATE','复核收口：有污染受控，登记台账处置','POLLUTED','刘洋','2026-09-20 17:05:58','危废暂存间A-02','80370','2026-09-20 17:05:58','80370','2026-09-20 17:05:58',0x00,2010),(616,'PC-20260920170558334','CHECK',NULL,'CHECK','PC-20260920170558334','WASTE_INTERMEDIATE','复核收口：有污染受控，登记台账处置','POLLUTED','刘洋','2026-09-20 17:05:58','危废暂存间C-03','80370','2026-09-20 17:05:58','80370','2026-09-20 17:05:58',0x00,2010),(617,'PC-20260920170558076','CHECK',NULL,'CHECK','PC-20260920170558076','WASTE_INTERMEDIATE','复核收口：有污染受控，登记台账处置','POLLUTED','刘洋','2026-09-20 17:05:59','危废暂存间A-01','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:59',0x00,2010),(618,'PC-20260920170558310','CHECK',NULL,'CHECK','PC-20260920170558310','WASTE_INTERMEDIATE','复核收口：有污染受控，登记台账处置','POLLUTED','刘洋','2026-09-20 17:05:59','危废暂存间A-02','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:59',0x00,2010),(619,'PC-20260920170559576','CHECK',NULL,'CHECK','PC-20260920170559576','WASTE_INTERMEDIATE','复核收口：有污染受控，登记台账处置','POLLUTED','刘洋','2026-09-20 17:05:59','危废暂存间C-03','80370','2026-09-20 17:05:59','80370','2026-09-20 17:05:59',0x00,2010),(620,'PC-20260920170559613','CHECK',NULL,'CHECK','PC-20260920170559613','WASTE_INTERMEDIATE','复核收口：有污染受控，登记台账处置','POLLUTED','刘洋','2026-09-20 17:05:59','危废暂存间A-01','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(621,'PC-20260920170559501','CHECK',NULL,'CHECK','PC-20260920170559501','WASTE_INTERMEDIATE','复核收口：无污染合格，正常流转','CLEAN','刘洋','2026-09-20 17:06:00','原料A-01','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(622,'PC-20260920170559935','CHECK',NULL,'CHECK','PC-20260920170559935','WASTE_INTERMEDIATE','复核收口：无污染合格，正常流转','CLEAN','刘洋','2026-09-20 17:06:00','原料A-01','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(623,'PC-20260920170600858','CHECK',NULL,'CHECK','PC-20260920170600858','MATERIAL_ISSUE','复核收口：有污染受控，登记台账处置','POLLUTED','刘洋','2026-09-20 17:06:00','危废暂存间A-02','80370','2026-09-20 17:06:00','80370','2026-09-20 17:06:00',0x00,2010),(624,'PC-20260920170600823','CHECK',NULL,'CHECK','PC-20260920170600823','MATERIAL_ISSUE','复核收口：有污染受控，登记台账处置','POLLUTED','刘洋','2026-09-20 17:06:01','危废暂存间A-01','80370','2026-09-20 17:06:01','80370','2026-09-20 17:06:01',0x00,2010),(625,'PC-20260920170600387','CHECK',NULL,'CHECK','PC-20260920170600387','FINISHED_PRODUCT','复核收口：无污染合格，正常流转','CLEAN','刘洋','2026-09-20 17:06:01','原料A-01','80370','2026-09-20 17:06:01','80370','2026-09-20 17:06:01',0x00,2010),(626,'PC-20260920170601619','CHECK',NULL,'CHECK','PC-20260920170601619','FINISHED_PRODUCT','复核收口：无污染合格，正常流转','CLEAN','刘洋','2026-09-20 17:06:02','原料A-01','80370','2026-09-20 17:06:02','80370','2026-09-20 17:06:02',0x00,2010),(627,'PC-20260920170601418','CHECK',NULL,'CHECK','PC-20260920170601418','IN_STOCK','复核收口：无污染合格，正常流转','CLEAN','刘洋','2026-09-20 17:06:02','原料A-01','80370','2026-09-20 17:06:02','80370','2026-09-20 17:06:02',0x00,2010),(628,'PC-20260920170601449','CHECK',NULL,'CHECK','PC-20260920170601449','FINISHED_PRODUCT','复核收口：有污染受控，登记台账处置','POLLUTED','刘洋','2026-09-20 17:06:02','危废暂存间C-03','80370','2026-09-20 17:06:02','80370','2026-09-20 17:06:02',0x00,2010),(629,'PC-20260920170602587','CHECK',NULL,'CHECK','PC-20260920170602587','PURCHASE_INBOUND','复核收口：无污染合格，正常流转','CLEAN','刘洋','2026-09-20 17:06:02','原料A-01','80370','2026-09-20 17:06:02','80370','2026-09-20 17:06:02',0x00,2010),(630,'PC-20260920170602957','CHECK',NULL,'CHECK','PC-20260920170602957','PURCHASE_INBOUND','复核收口：有污染受控，登记台账处置','POLLUTED','刘洋','2026-09-20 17:06:03','危废暂存间A-02','80370','2026-09-20 17:06:03','80370','2026-09-20 17:06:03',0x00,2010),(631,'PC-20260920170602477','CHECK',NULL,'CHECK','PC-20260920170602477','IN_STOCK','复核收口：无污染合格，正常流转','CLEAN','刘洋','2026-09-20 17:06:03','原料A-01','80370','2026-09-20 17:06:03','80370','2026-09-20 17:06:03',0x00,2010),(632,'PC-20260920170557281','LEDGER',NULL,'LEDGER','PC-20260920170557281','DISPOSAL','台账状态：暂存 → 已处置','DISPOSED','刘洋','2026-09-20 17:06:04','委托有资质单位焚烧处置，危废转移联单已归档','80370','2026-09-20 17:06:04','80370','2026-09-20 17:06:04',0x00,2010),(633,'PC-20260920170558098','LEDGER',NULL,'LEDGER','PC-20260920170558098','DISPOSAL','台账状态：暂存 → 处置中','PROCESSING','刘洋','2026-09-20 17:06:04','送再生装置蒸馏提纯中','80370','2026-09-20 17:06:04','80370','2026-09-20 17:06:04',0x00,2010),(634,'PC-20260920170558076','LEDGER',NULL,'LEDGER','PC-20260920170558076','DISPOSAL','台账状态：暂存 → 已回用','REUSED','刘洋','2026-09-20 17:06:04','破乳回收基础油，回用于设备润滑','80370','2026-09-20 17:06:04','80370','2026-09-20 17:06:04',0x00,2010),(635,'PC-20260920170558310','LEDGER',NULL,'LEDGER','PC-20260920170558310','DISPOSAL','台账状态：暂存 → 已处置','DISPOSED','刘洋','2026-09-20 17:06:05','随批次一并焚烧处置','80370','2026-09-20 17:06:05','80370','2026-09-20 17:06:05',0x00,2010),(636,'PC-20260920170559613','LEDGER',NULL,'LEDGER','PC-20260920170559613','DISPOSAL','台账状态：暂存 → 处置中','PROCESSING','刘洋','2026-09-20 17:06:05','脱水减量后委外处置中','80370','2026-09-20 17:06:05','80370','2026-09-20 17:06:05',0x00,2010),(637,'PC-20260920170559501','CHECK',NULL,'CHECK','PC-20260920170559501','WASTE_INTERMEDIATE','已被变更单 PC-20260920170605415 替代：第三方复检报告 HS-2026-0917 检出石棉（属危险废物），原判「无污染可回收」有误',NULL,'刘洋','2026-09-20 17:06:05',NULL,'80370','2026-09-20 17:06:05','80370','2026-09-20 17:06:05',0x00,2010),(638,'PC-20260920170605415','CHECK',NULL,'CHECK','PC-20260920170605415','WASTE_INTERMEDIATE','替代原单 PC-20260920170559501：第三方复检报告 HS-2026-0917 检出石棉（属危险废物），原判「无污染可回收」有误',NULL,'刘洋','2026-09-20 17:06:05',NULL,'80370','2026-09-20 17:06:05','80370','2026-09-20 17:06:05',0x00,2010),(639,'PC-20260920170605415','CHECK',NULL,'CHECK','PC-20260920170605415','WASTE_INTERMEDIATE','复核收口：有污染受控，登记台账处置','POLLUTED','刘洋','2026-09-20 17:06:06','危废暂存间C-03','80370','2026-09-20 17:06:06','80370','2026-09-20 17:06:06',0x00,2010);
/*!40000 ALTER TABLE `mes_set_trace_chain` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_treatment_facility`
--

DROP TABLE IF EXISTS `mes_set_treatment_facility`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_treatment_facility` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `facility_no` varchar(64) NOT NULL COMMENT '设施编号',
  `facility_name` varchar(128) NOT NULL COMMENT '设施名称',
  `facility_type` varchar(32) NOT NULL COMMENT '设施类型：ACTIVATED_CARBON 活性炭吸附 / CATALYTIC_COMBUSTION 催化燃烧 / BAG_FILTER 布袋除尘',
  `outlet_code` varchar(64) DEFAULT NULL COMMENT '关联排放口编号（mes_set_emission_outlet.outlet_code）',
  `line_code` varchar(64) DEFAULT NULL COMMENT '所属产线/工序（§3.3：喷涂 / 抛丸打磨）',
  `run_status` varchar(16) NOT NULL DEFAULT 'RUNNING' COMMENT '运行状态：RUNNING 运行 / STOPPED 停运',
  `design_air_volume` decimal(18,2) DEFAULT NULL COMMENT '设计风量 m³/h',
  `consumable_name` varchar(64) DEFAULT NULL COMMENT '耗材名称（如 活性炭）',
  `replace_cycle_days` int DEFAULT NULL COMMENT '更换周期(天)',
  `last_replace_date` date DEFAULT NULL COMMENT '上次更换日期',
  `next_replace_date` date DEFAULT NULL COMMENT '下次更换日期（=上次+周期，用于到期预警）',
  `shutdown_status` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT '停运申报状态：NONE 无 / PENDING 待审批 / APPROVED 已批准 / REJECTED 已驳回',
  `shutdown_reason` varchar(255) DEFAULT NULL COMMENT '停运事由',
  `shutdown_plan_start` datetime DEFAULT NULL COMMENT '计划停运开始',
  `shutdown_plan_end` datetime DEFAULT NULL COMMENT '计划停运结束',
  `shutdown_declared_at` datetime DEFAULT NULL COMMENT '申报时间',
  `shutdown_approver` varchar(64) DEFAULT NULL COMMENT '审批人',
  `shutdown_approved_at` datetime DEFAULT NULL COMMENT '审批时间',
  `status` varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '档案状态：ENABLED/DISABLED',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_treatment_facility_no` (`facility_no`),
  KEY `idx_treatment_facility_type` (`facility_type`),
  KEY `idx_treatment_facility_outlet` (`outlet_code`),
  KEY `idx_treatment_facility_next_replace` (`next_replace_date`),
  KEY `idx_treatment_facility_run_status` (`run_status`)
) ENGINE=InnoDB AUTO_INCREMENT=153 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='治污设施台账（换炭/停运申报/同开同停）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_treatment_facility`
--

LOCK TABLES `mes_set_treatment_facility` WRITE;
/*!40000 ALTER TABLE `mes_set_treatment_facility` DISABLE KEYS */;
INSERT INTO `mes_set_treatment_facility` VALUES (124,'TF-2026-0910-001','1#喷涂线活性炭吸附装置','ACTIVATED_CARBON','DA-001','喷涂线-1','RUNNING',12000.00,'蜂窝活性炭',90,'2026-06-15','2026-09-13','NONE',NULL,NULL,NULL,NULL,NULL,NULL,'ENABLED','按环保设施运行台账要求建卡','80370','2026-09-10 20:42:17','80370','2026-09-10 20:42:17',0x00,2010),(125,'TF-2026-0910-002','2#喷涂线活性炭吸附装置','ACTIVATED_CARBON','DA-001','喷涂线-2','RUNNING',12000.00,'蜂窝活性炭',90,'2026-06-20','2026-09-18','NONE',NULL,NULL,NULL,NULL,NULL,NULL,'ENABLED','按环保设施运行台账要求建卡','80370','2026-09-10 20:42:17','80370','2026-09-10 20:42:17',0x00,2010),(126,'TF-2026-0910-003','抛丸打磨布袋除尘器','BAG_FILTER','DA-001','抛丸线','RUNNING',20000.00,'覆膜滤袋',180,'2026-05-20','2026-11-16','NONE',NULL,NULL,NULL,NULL,NULL,NULL,'ENABLED','按环保设施运行台账要求建卡','80370','2026-09-10 20:42:17','80370','2026-09-10 20:42:17',0x00,2010),(127,'TF-2026-0910-004','烘干房催化燃烧装置','CATALYTIC_COMBUSTION','DA-001','烘干线','RUNNING',15000.00,'蜂窝陶瓷催化剂',730,'2025-11-10','2027-11-10','NONE',NULL,NULL,NULL,NULL,NULL,NULL,'ENABLED','按环保设施运行台账要求建卡','80370','2026-09-10 20:42:17','80370','2026-09-10 20:42:17',0x00,2010),(128,'TF-2026-0910-005','废水站活性炭吸附罐','ACTIVATED_CARBON','DA-002','废水站','RUNNING',8000.00,'柱状活性炭',120,'2026-08-01','2026-11-29','NONE',NULL,NULL,NULL,NULL,NULL,NULL,'ENABLED','按环保设施运行台账要求建卡','80370','2026-09-10 20:42:17','80370','2026-09-10 20:42:17',0x00,2010),(129,'TF-2026-0910-006','备用活性炭吸附装置','ACTIVATED_CARBON','DA-001','喷涂线-1','RUNNING',12000.00,'蜂窝活性炭',90,'2026-09-10','2026-12-09','PENDING','活性炭更换期间停机检修，预计 4 小时','2026-09-11 08:00:00','2026-09-11 12:00:00','2026-09-10 20:42:18','',NULL,'ENABLED','按环保设施运行台账要求建卡','80370','2026-09-10 20:42:17','80370','2026-09-10 20:42:18',0x00,2010);
/*!40000 ALTER TABLE `mes_set_treatment_facility` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_wastewater`
--

DROP TABLE IF EXISTS `mes_set_wastewater`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_wastewater` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `record_no` varchar(64) NOT NULL COMMENT '记录编号 WW-YYYYMMDD-NNN',
  `outlet_id` bigint NOT NULL COMMENT '关联排放口编号',
  `wo_id` bigint DEFAULT NULL COMMENT '关联工单编号',
  `sample_no` varchar(64) DEFAULT '' COMMENT '实验室样品编号(手工检测时)',
  `pollutant_code` varchar(32) NOT NULL COMMENT '污染物：PH/COD/BOD5/NH3_N/TP/PETROLEUM/SS/HEAVY_METAL等',
  `concentration` decimal(12,3) DEFAULT NULL COMMENT '检测浓度 mg/L(pH无量纲)',
  `unit` varchar(16) DEFAULT '' COMMENT '单位 mg/L(pH留空)',
  `flow_rate` decimal(12,3) DEFAULT NULL COMMENT '排放流量 m3/h',
  `emission_amount` decimal(12,3) DEFAULT NULL COMMENT '排放量 kg/h',
  `limit_value` decimal(12,3) DEFAULT NULL COMMENT '限值',
  `result` varchar(8) DEFAULT NULL COMMENT '结果：PASS/FAIL',
  `collection_mode` varchar(16) DEFAULT 'MANUAL' COMMENT '采集方式：ONLINE_AUTO/LAB_MANUAL',
  `monitor_time` datetime NOT NULL COMMENT '监测时间',
  `instrument_no` varchar(64) DEFAULT '' COMMENT '在线监测仪/化验设备编号',
  `inspector` varchar(64) DEFAULT '' COMMENT '化验员(手工时)',
  `remark` varchar(500) DEFAULT '' COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=68 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MES SET-废水排放检测记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_wastewater`
--

LOCK TABLES `mes_set_wastewater` WRITE;
/*!40000 ALTER TABLE `mes_set_wastewater` DISABLE KEYS */;
INSERT INTO `mes_set_wastewater` VALUES (1,'WW-SMOKE-1',1,NULL,'','COD',60.000,'mg/L',NULL,NULL,NULL,'PASS','LAB_MANUAL','2026-09-02 11:00:00','','','','1','2026-09-02 15:33:09','1','2026-09-02 15:33:28',0x01,1),(2,'WW-2026-0901-001',3,NULL,'SW-2026-0901-001','COD',35.000,'mg/L',30.000,0.020,100.000,'PASS','MANUAL','2026-09-01 10:30:00','LAB-01','刘洋','','80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(3,'WW-2026-0901-002',3,NULL,'SW-2026-0901-002','氨氮',2.100,'mg/L',30.000,0.001,8.000,'PASS','MANUAL','2026-09-01 11:00:00','LAB-01','刘洋','','80370','2026-09-02 10:00:00','80370','2026-09-02 10:00:00',0x00,2010),(60,'WW-2026-0910-001',3,NULL,'WW-S5001','COD',86.000,'mg/L',42.000,3.612,100.000,'PASS','LAB_MANUAL','2026-09-02 11:00:00','WW-5001','杨磊','','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(61,'WW-2026-0910-002',3,NULL,'WW-S5002','氨氮',5.200,'mg/L',42.000,0.218,8.000,'PASS','LAB_MANUAL','2026-09-04 11:00:00','WW-5002','杨磊','','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(62,'WW-2026-0910-003',3,NULL,'WW-S5003','COD',92.000,'mg/L',42.000,3.864,100.000,'PASS','LAB_MANUAL','2026-09-06 11:00:00','WW-5003','杨磊','','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(63,'WW-2026-0910-004',3,NULL,'WW-S5004','COD',118.000,'mg/L',42.000,4.956,100.000,'FAIL','LAB_MANUAL','2026-09-08 11:00:00','WW-5004','杨磊','已启动应急池暂存并加药处理','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(64,'WW-2026-0910-005',3,NULL,'WW-S5005','氨氮',6.800,'mg/L',42.000,0.286,8.000,'PASS','LAB_MANUAL','2026-09-09 11:00:00','WW-5005','杨磊','','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010),(65,'WW-2026-0910-006',3,NULL,'WW-S5006','COD',74.000,'mg/L',42.000,3.108,100.000,'PASS','LAB_MANUAL','2026-09-10 11:00:00','WW-5006','杨磊','','80370','2026-09-10 20:42:21','80370','2026-09-10 20:42:21',0x00,2010);
/*!40000 ALTER TABLE `mes_set_wastewater` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_set_weigh_record`
--

DROP TABLE IF EXISTS `mes_set_weigh_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_set_weigh_record` (
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
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_biz` (`biz_type`,`biz_no`)
) ENGINE=InnoDB AUTO_INCREMENT=46 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全环保检测-称重记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_set_weigh_record`
--

LOCK TABLES `mes_set_weigh_record` WRITE;
/*!40000 ALTER TABLE `mes_set_weigh_record` DISABLE KEYS */;
INSERT INTO `mes_set_weigh_record` VALUES (16,'PRODUCE','EMERGENCY','EV-2026-0910-001','EM-EV-2026-0910-001-1',NULL,NULL,4.200,0.000,4.200,'MANUAL',NULL,'http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','应急事件 EV-2026-0910-001 应急处置产废过秤','刘洋','2026-09-10 20:24:41','80370','2026-09-10 20:24:41','80370','2026-09-10 20:24:41',0x00,2010),(17,'PRODUCE','EMERGENCY','EV-2026-0910-002','EM-EV-2026-0910-002-1',NULL,NULL,5.200,0.000,5.200,'MANUAL',NULL,'http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','应急事件 EV-2026-0910-002 应急处置产废过秤','刘洋','2026-09-10 20:24:41','80370','2026-09-10 20:24:41','80370','2026-09-10 20:24:41',0x00,2010),(22,'PRODUCE','EMERGENCY','EV-2026-0910-001','EM-EV-2026-0910-001-1',NULL,NULL,4.200,0.000,4.200,'MANUAL',NULL,'http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','应急事件 EV-2026-0910-001 应急处置产废过秤','刘洋','2026-09-10 20:25:07','80370','2026-09-10 20:25:07','80370','2026-09-10 20:25:07',0x00,2010),(23,'PRODUCE','EMERGENCY','EV-2026-0910-002','EM-EV-2026-0910-002-1',NULL,NULL,5.200,0.000,5.200,'MANUAL',NULL,'http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','应急事件 EV-2026-0910-002 应急处置产废过秤','刘洋','2026-09-10 20:25:07','80370','2026-09-10 20:25:07','80370','2026-09-10 20:25:07',0x00,2010),(28,'PRODUCE','EMERGENCY','EV-2026-0910-001','EM-EV-2026-0910-001-1',NULL,NULL,4.200,0.000,4.200,'MANUAL',NULL,'http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','应急事件 EV-2026-0910-001 应急处置产废过秤','刘洋','2026-09-10 20:25:24','80370','2026-09-10 20:25:24','80370','2026-09-10 20:25:24',0x00,2010),(29,'PRODUCE','EMERGENCY','EV-2026-0910-002','EM-EV-2026-0910-002-1',NULL,NULL,5.200,0.000,5.200,'MANUAL',NULL,'http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','应急事件 EV-2026-0910-002 应急处置产废过秤','刘洋','2026-09-10 20:25:24','80370','2026-09-10 20:25:24','80370','2026-09-10 20:25:24',0x00,2010),(34,'PRODUCE','EMERGENCY','EV-2026-0910-001','EM-EV-2026-0910-001-1',NULL,NULL,4.200,0.000,4.200,'MANUAL',NULL,'http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','应急事件 EV-2026-0910-001 应急处置产废过秤','刘洋','2026-09-10 20:42:22','80370','2026-09-10 20:42:22','80370','2026-09-10 20:42:22',0x00,2010),(35,'PRODUCE','EMERGENCY','EV-2026-0910-002','EM-EV-2026-0910-002-1',NULL,NULL,5.200,0.000,5.200,'MANUAL',NULL,'http://127.0.0.1:48080/admin-api/infra/file/4/get/20260910/%E6%A3%80%E6%B5%8B%E6%8A%A5%E5%91%8A%E6%89%AB%E6%8F%8F%E4%BB%B6.png','应急事件 EV-2026-0910-002 应急处置产废过秤','刘洋','2026-09-10 20:42:22','80370','2026-09-10 20:42:22','80370','2026-09-10 20:42:22',0x00,2010),(36,'INBOUND','LEDGER','LD-2026-0910-001','BUCKET-AC-0910-02',NULL,'SCALE-01',285.600,25.600,260.000,'MANUAL',NULL,NULL,'地磅未联网，手工登记','刘洋','2026-09-10 15:00:00','80370','2026-09-10 20:42:22','80370','2026-09-10 20:42:22',0x00,2010),(37,'OUTBOUND','LEDGER','LD-2026-0910-001','BUCKET-AC-0910-02',NULL,'SCALE-01',284.200,25.600,258.600,'MANUAL','京A·X7392',NULL,'地磅未联网，手工登记','刘洋','2026-09-10 16:00:00','80370','2026-09-10 20:42:22','80370','2026-09-10 20:42:22',0x00,2010),(38,'INBOUND','LEDGER','LD-2026-0910-002','BUCKET-OIL-0910-01',NULL,'SCALE-01',205.500,25.000,180.500,'MANUAL',NULL,NULL,'地磅未联网，手工登记','刘洋','2026-09-10 17:00:00','80370','2026-09-10 20:42:22','80370','2026-09-10 20:42:22',0x00,2010),(39,'OUTBOUND','LEDGER','LD-2026-0910-002','BUCKET-OIL-0910-01',NULL,'SCALE-01',204.200,25.000,179.200,'MANUAL','京A·X7392',NULL,'地磅未联网，手工登记','刘洋','2026-09-10 15:00:00','80370','2026-09-10 20:42:22','80370','2026-09-10 20:42:22',0x00,2010);
/*!40000 ALTER TABLE `mes_set_weigh_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_tm_tool`
--

DROP TABLE IF EXISTS `mes_tm_tool`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_tm_tool` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工具编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工具名称',
  `brand` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '品牌',
  `specification` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '型号规格',
  `tool_type_id` bigint NOT NULL COMMENT '工具类型编号',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '数量',
  `available_quantity` int DEFAULT NULL COMMENT '可用数量',
  `mainten_type` tinyint DEFAULT NULL COMMENT '保养维护类型',
  `next_mainten_period` int DEFAULT NULL COMMENT '下次保养周期（次数）',
  `next_mainten_date` datetime DEFAULT NULL COMMENT '下次保养日期',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=207 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 工具台账';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_tm_tool`
--

LOCK TABLES `mes_tm_tool` WRITE;
/*!40000 ALTER TABLE `mes_tm_tool` DISABLE KEYS */;
INSERT INTO `mes_tm_tool` VALUES (200,NULL,'XX刀具','XX','XXX',200,60,60,1,NULL,NULL,1,'','1','2023-11-11 11:10:38','1','2023-11-11 11:10:38',0x00,1),(201,'T00060','XXX模具','XXX牌','XXX型号',201,1,1,1,NULL,NULL,1,'','1','2023-11-11 11:12:04','1','2023-11-11 11:12:04',0x00,1),(202,'T00061','精密夹具A','米思米','JG-100',202,1,1,2,50000,NULL,1,'产线A使用','1','2023-12-01 09:00:00','1','2023-12-01 09:00:00',0x00,1),(203,'T00062','精密夹具B','米思米','JG-200',202,1,0,2,30000,NULL,2,'已领用给张三','1','2023-12-01 09:05:00','1','2024-01-15 10:00:00',0x00,1),(204,'T00063','测试千分尺','三丰','MDC-25',204,1,0,1,NULL,'2024-06-01 00:00:00',3,'送修中','1','2024-01-10 08:30:00','1','2024-03-20 14:00:00',0x00,1),(205,'T00064','旧量具','国产','LJ-50',205,1,0,1,NULL,NULL,4,'已报废','1','2022-10-01 10:00:00','1','2024-02-01 16:00:00',0x00,1),(206,NULL,'通用刀片','山特维克','CNMG120408',200,100,85,NULL,NULL,NULL,1,'批量耗材，无需单独编码','1','2024-03-01 08:00:00','1','2024-03-01 08:00:00',0x00,1);
/*!40000 ALTER TABLE `mes_tm_tool` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_tm_tool_type`
--

DROP TABLE IF EXISTS `mes_tm_tool_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_tm_tool_type` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型名称',
  `code_flag` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否编码管理',
  `mainten_type` tinyint DEFAULT NULL COMMENT '保养维护类型',
  `mainten_period` int DEFAULT NULL COMMENT '保养周期',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=206 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 工具类型';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_tm_tool_type`
--

LOCK TABLES `mes_tm_tool_type` WRITE;
/*!40000 ALTER TABLE `mes_tm_tool_type` DISABLE KEYS */;
INSERT INTO `mes_tm_tool_type` VALUES (200,'TT002','刀具',0x00,NULL,NULL,'','1','2022-05-11 00:24:04','1','2022-05-11 00:24:04',0x00,1),(201,'TT022','模具',0x01,2,500000,'','1','2022-05-11 00:28:22','1','2022-08-16 18:56:56',0x00,1),(202,'TT024','夹具',0x01,2,30,'','1','2022-05-11 00:28:46','1','2022-08-16 19:58:17',0x00,1),(204,'TT039','测试工具',0x01,1,13,'','1','2022-08-19 15:04:41','1','2022-08-19 15:04:41',0x00,1),(205,'TT049','量具',0x01,1,33,'','1','2022-08-22 09:52:47','1','2022-08-22 09:52:47',0x00,1);
/*!40000 ALTER TABLE `mes_tm_tool_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_arrival_notice`
--

DROP TABLE IF EXISTS `mes_wm_arrival_notice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_arrival_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知单编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '通知单名称',
  `purchase_order_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '采购订单编号',
  `vendor_id` bigint DEFAULT NULL COMMENT '供应商编号（关联 mes_md_vendor.id）',
  `arrival_date` datetime DEFAULT NULL COMMENT '到货日期',
  `contact_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系人',
  `contact_telephone` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系电话',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`code`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=110 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 到货通知单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_arrival_notice`
--

LOCK TABLES `mes_wm_arrival_notice` WRITE;
/*!40000 ALTER TABLE `mes_wm_arrival_notice` DISABLE KEYS */;
INSERT INTO `mes_wm_arrival_notice` VALUES (1,'AN2026020001','2 月份钢板到货','PO20260101',1,'2026-02-15 00:00:00','张三','13800138000',2,'含需检物料，草稿状态','1','2026-02-22 14:53:50','1','2026-02-27 23:05:55',0x00,1),(2,'AN2026020002','2 月份螺丝到货','PO20260102',2,'2026-02-18 00:00:00','李四','13900139000',2,'待质检，IQC 已完成','1','2026-02-22 14:53:50','1','2026-02-26 08:23:36',0x00,1),(3,'AN2026020003','2 月份铝材到货','PO20260103',1,'2026-02-20 00:00:00','王五','13700137000',2,'待入库，等待入库执行完成','1','2026-02-22 14:53:50','1','2026-02-22 14:53:50',0x00,1),(4,'AN2026020004','1 月份铜线到货','PO20260050',2,'2026-01-25 00:00:00','赵六','13600136000',3,'已完成入库','1','2026-02-22 14:53:50','1','2026-02-22 14:53:50',0x00,1),(5,'AN2026020005','2 月份标准件到货','PO20260104',1,'2026-02-22 00:00:00','张三','13800138000',3,'全部免检物料，草稿状态','1','2026-02-22 14:53:50','1','2026-02-26 13:18:53',0x00,1),(6,'ANFCnFEfb9NT','xxx',NULL,200,'2026-02-18 00:00:00',NULL,NULL,0,'','1','2026-02-23 00:49:25','1','2026-02-23 00:49:25',0x00,1),(100,'AN2026020100','待检-钢材批次到货','PO20260201',1,'2026-02-20 08:00:00','张三','13800138000',2,'3 行全部需检，用于待检任务测试','1','2026-02-23 07:30:48','1','2026-02-26 08:23:38',0x00,1),(101,'AN2026020101','待检-电子元件到货','PO20260202',2,'2026-02-21 10:00:00','李四','13900139000',2,'1 行需检 + 1 行免检','1','2026-02-23 07:30:48','1','2026-02-26 08:23:39',0x00,1),(102,'AN2026020102','待检-紧固件到货','PO20260203',1,'2026-02-22 14:00:00','王五','13700137000',2,'1 行已关联 IQC + 1 行未关联','1','2026-02-23 07:30:48','1','2026-02-26 08:23:40',0x00,1),(103,'ANSXI1qnG1Ue','测试到货通知-无检验',NULL,200,'2026-02-25 00:00:00','张三','13800138000',3,'','1','2026-02-25 20:00:46','1','2026-02-26 00:24:44',0x00,1),(104,'AN3asCTmLzYb','111',NULL,201,'2026-01-28 00:00:00',NULL,NULL,2,'','1','2026-02-26 01:10:29','1','2026-02-26 01:10:43',0x00,1),(105,'ANpcHrG0Q7S2','IQC Test Notice',NULL,200,'2026-03-23 00:00:00',NULL,NULL,3,'','1','2026-03-23 19:28:57','1','2026-03-27 22:32:18',0x00,1),(106,'ANbhv1kzQHtc','AABBC',NULL,200,'2026-03-04 00:00:00',NULL,NULL,3,'','1','2026-03-23 19:44:08','1','2026-03-23 19:51:39',0x00,1),(107,'ANETKfDzjmKB','IQC Test Notice',NULL,200,'2026-03-23 00:00:00',NULL,NULL,3,'','1','2026-03-23 19:47:25','1','2026-03-29 19:35:01',0x00,1),(108,'ANiiCf2vISKg','IQC',NULL,200,'2026-03-23 00:00:00',NULL,NULL,2,'','1','2026-03-23 19:52:48','1','2026-03-23 19:56:35',0x00,1),(109,'AN20260329000001','ABC',NULL,200,'2026-03-04 00:00:00',NULL,NULL,3,'','1','2026-03-29 19:35:17','1','2026-03-29 19:35:24',0x00,1);
/*!40000 ALTER TABLE `mes_wm_arrival_notice` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_arrival_notice_line`
--

DROP TABLE IF EXISTS `mes_wm_arrival_notice_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_arrival_notice_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `notice_id` bigint NOT NULL COMMENT '到货通知单编号（关联 mes_wm_arrival_notice.id）',
  `item_id` bigint NOT NULL COMMENT '物料编号（关联 mes_md_item.id）',
  `arrival_quantity` decimal(14,2) DEFAULT NULL COMMENT '到货数量',
  `qualified_quantity` decimal(14,2) DEFAULT NULL COMMENT '合格数量',
  `iqc_check_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否需要来料检验',
  `iqc_id` bigint DEFAULT NULL COMMENT '来料检验单编号（关联 mes_qc_iqc.id）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_notice_id` (`notice_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=114 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 到货通知单行';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_arrival_notice_line`
--

LOCK TABLES `mes_wm_arrival_notice_line` WRITE;
/*!40000 ALTER TABLE `mes_wm_arrival_notice_line` DISABLE KEYS */;
INSERT INTO `mes_wm_arrival_notice_line` VALUES (1,1,1,500.00,NULL,0x01,NULL,'钢板-需来料检验','1','2026-02-22 14:53:50','1','2026-02-22 14:53:50',0x00,1),(2,1,2,200.00,200.00,0x00,NULL,'螺栓-免检物料','1','2026-02-22 14:53:50','1','2026-02-22 14:53:50',0x00,1),(3,2,3,1000.00,980.00,0x01,1,'六角螺丝-检验合格','1','2026-02-22 14:53:50','1','2026-02-22 14:53:50',0x00,1),(4,2,4,500.00,500.00,0x00,NULL,'垫片-免检','1','2026-02-22 14:53:50','1','2026-02-22 14:53:50',0x00,1),(5,3,5,800.00,800.00,0x01,2,'铝板-检验合格','1','2026-02-22 14:53:50','1','2026-02-22 14:53:50',0x00,1),(6,3,6,300.00,300.00,0x00,NULL,'铝棒-免检','1','2026-02-22 14:53:50','1','2026-02-22 14:53:50',0x00,1),(7,4,7,600.00,580.00,0x01,3,'铜线-已入库完成','1','2026-02-22 14:53:50','1','2026-02-22 14:53:50',0x00,1),(8,5,2,300.00,300.00,0x00,NULL,'螺栓-免检','1','2026-02-22 14:53:50','1','2026-02-22 14:53:50',0x00,1),(9,5,4,400.00,400.00,0x00,NULL,'垫片-免检','1','2026-02-22 14:53:50','1','2026-02-22 14:53:50',0x00,1),(100,100,1,500.00,NULL,0x01,NULL,'钢板-待检','1','2026-02-23 07:30:48','1','2026-02-23 07:30:48',0x00,1),(101,100,3,300.00,NULL,0x01,NULL,'六角螺丝-待检','1','2026-02-23 07:30:48','1','2026-02-23 07:30:48',0x00,1),(102,100,5,200.00,NULL,0x01,NULL,'铝板-待检','1','2026-02-23 07:30:48','1','2026-02-23 07:30:48',0x00,1),(103,101,7,600.00,NULL,0x01,NULL,'铜线-待检','1','2026-02-23 07:30:48','1','2026-02-23 07:30:48',0x00,1),(104,101,2,400.00,400.00,0x00,NULL,'螺栓-免检','1','2026-02-23 07:30:48','1','2026-02-23 07:30:48',0x00,1),(105,102,3,800.00,780.00,0x01,1,'六角螺丝-已关联IQC','1','2026-02-23 07:30:48','1','2026-02-23 07:30:48',0x00,1),(106,102,4,250.00,NULL,0x01,NULL,'垫片-待检','1','2026-02-23 07:30:48','1','2026-02-23 07:30:48',0x00,1),(107,103,69,100.00,100.00,0x00,NULL,'','1','2026-02-25 20:01:01','1','2026-02-25 20:01:01',0x00,1),(108,104,69,222.00,222.00,0x00,NULL,'','1','2026-02-26 01:10:34','1','2026-02-26 01:10:34',0x00,1),(109,105,69,10.00,10.00,0x01,209,'','1','2026-03-23 19:30:23','1','2026-03-27 22:32:18',0x00,1),(110,106,69,1.00,1.00,0x01,207,'','1','2026-03-23 19:44:14','1','2026-03-23 19:51:39',0x00,1),(111,108,69,100.00,NULL,0x01,NULL,'','1','2026-03-23 19:55:00','1','2026-03-23 19:55:00',0x00,1),(112,107,69,10.00,10.00,0x00,NULL,'','1','2026-03-29 19:34:59','1','2026-03-29 19:34:59',0x00,1),(113,109,69,111.00,111.00,0x00,NULL,'','1','2026-03-29 19:35:22','1','2026-03-29 19:35:22',0x00,1);
/*!40000 ALTER TABLE `mes_wm_arrival_notice_line` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_barcode`
--

DROP TABLE IF EXISTS `mes_wm_barcode`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_barcode` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `config_id` bigint DEFAULT NULL COMMENT '条码配置编号',
  `format` tinyint NOT NULL COMMENT '条码格式',
  `biz_type` smallint NOT NULL,
  `content` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '条码内容',
  `biz_id` bigint NOT NULL COMMENT '业务编号',
  `biz_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务编码',
  `biz_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务名称',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_config_id` (`config_id`) USING BTREE,
  KEY `idx_biz_type_id` (`biz_type`,`biz_id`) USING BTREE,
  KEY `idx_content` (`content`(100)) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 条码清单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_barcode`
--

LOCK TABLES `mes_wm_barcode` WRITE;
/*!40000 ALTER TABLE `mes_wm_barcode` DISABLE KEYS */;
INSERT INTO `mes_wm_barcode` VALUES (1,1,1,101,'WH-WH001',1001,'WH001','一号仓库',0,'主仓库','1','2026-03-05 14:49:52','1','2026-03-05 14:50:14',0x00,1),(2,1,1,101,'WH-WH002',1002,'WH002','二号仓库',0,'备用仓库','1','2026-03-05 14:49:52','1','2026-03-05 14:50:14',0x00,1),(3,1,2,101,'WH-WH003',1003,'WH003','三号仓库',1,'停用仓库','1','2026-03-05 14:49:52','1','2026-03-05 14:50:14',0x00,1),(4,2,1,102,'AREA-A01',2001,'A01','A区-01库位',0,'一号库位','1','2026-03-05 14:49:52','1','2026-03-05 14:50:14',0x00,1),(5,2,1,102,'AREA-A02',2002,'A02','A区-02库位',0,'二号库位','1','2026-03-05 14:49:52','1','2026-03-05 14:50:14',0x00,1),(6,2,1,102,'AREA-B01',2003,'B01','B区-01库位',0,'三号库位','1','2026-03-05 14:49:52','1','2026-03-05 14:50:14',0x00,1),(7,2,3,102,'AREA-B02',2004,'B02','B区-02库位',0,'四号库位','1','2026-03-05 14:49:52','1','2026-03-05 14:50:14',0x00,1),(8,3,1,103,'PKG-PKG001',3001,'PKG001','装箱单-001',0,'第一批装箱','1','2026-03-05 14:49:52','1','2026-03-05 14:50:14',0x00,1),(9,3,1,103,'PKG-PKG002',3002,'PKG002','装箱单-002',0,'第二批装箱','1','2026-03-05 14:49:52','1','2026-03-05 14:50:14',0x00,1),(10,3,2,103,'PKG-PKG003',3003,'PKG003','装箱单-003',0,'第三批装箱','1','2026-03-05 14:49:52','1','2026-03-05 14:50:14',0x00,1),(11,4,1,104,'STK-STK001',4001,'STK001','库存-001',0,'库存记录1','1','2026-03-05 14:49:52','1','2026-03-05 14:50:14',0x00,1),(12,4,1,104,'STK-STK002',4002,'STK002','库存-002',0,'库存记录2','1','2026-03-05 14:49:52','1','2026-03-05 14:50:14',0x00,1),(13,4,3,104,'STK-STK003',4003,'STK003','库存-003',0,'库存记录3','1','2026-03-05 14:49:52','1','2026-03-05 14:50:14',0x00,1),(14,5,1,105,'BATCH-B001',5001,'B001','批次-001',0,'2024年1月批次','1','2026-03-05 14:49:52','1','2026-03-05 14:50:14',0x00,1),(15,5,1,105,'BATCH-B002',5002,'B002','批次-002',0,'2024年2月批次','1','2026-03-05 14:49:52','1','2026-03-05 14:50:14',0x00,1),(16,5,2,105,'BATCH-B003',5003,'B003','批次-003',1,'已过期批次','1','2026-03-05 14:49:52','1','2026-03-05 14:50:14',0x00,1),(17,2,1,102,'AREA-WH-RAW',701,'WH-RAW','原料仓',0,'','1','2026-03-07 17:16:01','1','2026-03-07 17:23:08',0x00,1),(18,2,1,102,'AREA-WH-FIN',702,'WH-FIN','成品仓',0,'','1','2026-03-07 18:58:45','1','2026-03-07 18:58:45',0x00,1),(19,5,1,105,'BATCH-PKG202603080007',4,'PKG202603080007',NULL,0,'','1','2026-03-08 13:04:38','1','2026-03-08 13:04:38',0x00,1),(20,5,1,105,'BATCH-PKG202603310001',5,'PKG202603310001',NULL,0,'','1','2026-03-31 20:06:23','1','2026-03-31 20:06:23',0x00,1),(21,43,1,400,'MAC-M00001',9,'M00001','ABCED',0,'','1','2026-04-02 23:20:15','1','2026-04-02 23:20:15',0x00,1),(22,43,1,400,'MAC-M00002',10,'M00002','AAA',0,'','1','2026-04-02 23:37:18','1','2026-04-02 23:37:18',0x00,1),(23,40,1,300,'CARD-CARD20260404000001',4,'CARD20260404000001','CARD20260404000001',0,'','1','2026-04-04 20:30:36','1','2026-04-04 20:30:36',0x00,1),(24,37,1,105,'PKG-PKG202604060001',6,'PKG202604060001',NULL,0,'','1','2026-04-06 00:52:34','1','2026-04-06 00:52:34',0x00,1),(25,37,1,105,'PKG-PKG202604060002',7,'PKG202604060002',NULL,0,'','1','2026-04-06 01:07:52','1','2026-04-06 01:07:52',0x00,1),(26,39,1,107,'BATCH-BATCH_ITEM_72',8,'BATCH_ITEM_72','钢筋',0,'','1','2026-04-06 16:57:36','1','2026-04-06 16:57:36',0x00,1),(27,39,1,107,'BATCH-PC202600003',10,'PC202600003','ABC',0,'','1','2026-04-17 09:38:09','1','2026-04-17 09:38:09',0x00,1),(28,45,1,600,'ITEM-IF2022082437',69,'IF2022082437','色粉【黑色】',0,'','1','2026-05-21 10:32:37','1','2026-05-21 10:32:37',0x00,1),(29,34,1,102,'WH-WIP_VIRTUAL_WAREHOUSE',703,'WIP_VIRTUAL_WAREHOUSE','虚拟线边仓库',0,'','1','2026-05-29 08:38:03','1','2026-05-29 08:38:03',0x00,1);
/*!40000 ALTER TABLE `mes_wm_barcode` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_barcode_config`
--

DROP TABLE IF EXISTS `mes_wm_barcode_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_barcode_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `format` tinyint NOT NULL COMMENT '条码格式',
  `biz_type` smallint NOT NULL,
  `content_format` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容格式模板（支持{BUSINESSCODE}占位符）',
  `content_example` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '内容样例',
  `auto_generate_flag` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否自动生成',
  `default_template` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '默认打印模板',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_biz_type` (`biz_type`,`deleted`,`tenant_id`) USING BTREE,
  KEY `idx_tenant` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=52 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 条码配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_barcode_config`
--

LOCK TABLES `mes_wm_barcode_config` WRITE;
/*!40000 ALTER TABLE `mes_wm_barcode_config` DISABLE KEYS */;
INSERT INTO `mes_wm_barcode_config` VALUES (34,1,102,'WH-{BUSINESSCODE}','WH-WH001',0x01,NULL,0,'仓库条码配置','1','2026-03-31 13:29:48','1','2026-03-31 13:29:48',0x00,1),(35,1,103,'LOC-{BUSINESSCODE}','LOC-L001',0x01,NULL,0,'库区条码配置','1','2026-03-31 13:29:48','1','2026-03-31 13:29:48',0x00,1),(36,1,104,'AREA-{BUSINESSCODE}','AREA-A01',0x01,NULL,0,'库位条码配置','1','2026-03-31 13:29:48','1','2026-03-31 13:29:48',0x00,1),(37,1,105,'PKG-{BUSINESSCODE}','PKG-P001',0x01,NULL,0,'装箱单条码配置','1','2026-03-31 13:29:48','1','2026-03-31 13:29:48',0x00,1),(38,1,106,'STK-{BUSINESSCODE}','STK-S001',0x01,NULL,0,'库存条码配置','1','2026-03-31 13:29:48','1','2026-03-31 13:29:48',0x00,1),(39,1,107,'BATCH-{BUSINESSCODE}','BATCH-B001',0x01,NULL,0,'批次条码配置','1','2026-03-31 13:29:48','1','2026-03-31 13:29:48',0x00,1),(40,1,300,'CARD-{BUSINESSCODE}','CARD-C001',0x01,NULL,0,'流转卡条码配置','1','2026-03-31 13:29:48','1','2026-03-31 13:29:48',0x00,1),(41,1,301,'WO-{BUSINESSCODE}','WO-W001',0x01,NULL,0,'工单条码配置','1','2026-03-31 13:29:48','1','2026-03-31 13:29:48',0x00,1),(42,1,302,'TO-{BUSINESSCODE}','TO-T001',0x01,NULL,0,'流转单条码配置','1','2026-03-31 13:29:48','1','2026-03-31 13:29:48',0x00,1),(43,1,400,'MAC-{BUSINESSCODE}','MAC-M001',0x01,NULL,0,'设备条码配置','1','2026-03-31 13:29:48','1','2026-03-31 13:29:48',0x00,1),(44,1,500,'TOOL-{BUSINESSCODE}','TOOL-T001',0x01,NULL,0,'工装条码配置','1','2026-03-31 13:29:48','1','2026-03-31 13:29:48',0x00,1),(45,1,600,'ITEM-{BUSINESSCODE}','ITEM-I001',0x01,NULL,0,'物料条码配置','1','2026-03-31 13:29:48','1','2026-03-31 13:29:48',0x00,1),(46,1,601,'VEN-{BUSINESSCODE}','VEN-V001',0x01,NULL,0,'供应商条码配置','1','2026-03-31 13:29:48','1','2026-03-31 13:29:48',0x00,1),(47,1,602,'WS-{BUSINESSCODE}','WS-W001',0x01,NULL,0,'工作站条码配置','1','2026-03-31 13:29:48','1','2026-03-31 13:29:48',0x00,1),(48,1,603,'WSH-{BUSINESSCODE}','WSH-W001',0x01,NULL,0,'车间条码配置','1','2026-03-31 13:29:48','1','2026-03-31 13:29:48',0x00,1),(49,1,604,'USER-{BUSINESSCODE}','USER-U001',0x01,NULL,0,'人员条码配置','1','2026-03-31 13:29:48','1','2026-03-31 13:29:48',0x00,1),(50,1,605,'CLI-{BUSINESSCODE}','CLI-C001',0x01,NULL,0,'客户条码配置','1','2026-03-31 13:29:48','1','2026-03-31 13:29:48',0x00,1),(51,1,109,'SN-{BUSINESSCODE}','SN-SN20260305000001',0x01,NULL,0,'SN码条码配置','1','2026-06-13 10:25:39','1','2026-06-13 10:25:39',0x00,1);
/*!40000 ALTER TABLE `mes_wm_barcode_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_batch`
--

DROP TABLE IF EXISTS `mes_wm_batch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '批次ID',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '批次编码',
  `item_id` bigint NOT NULL COMMENT '物料ID',
  `produce_date` datetime DEFAULT NULL COMMENT '生产日期',
  `expire_date` datetime DEFAULT NULL COMMENT '有效期',
  `receipt_date` datetime DEFAULT NULL COMMENT '入库日期',
  `vendor_id` bigint DEFAULT NULL COMMENT '供应商ID',
  `client_id` bigint DEFAULT NULL COMMENT '客户ID',
  `sales_order_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '销售订单编号',
  `purchase_order_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '采购订单编号',
  `work_order_id` bigint DEFAULT NULL COMMENT '生产工单ID',
  `task_id` bigint DEFAULT NULL COMMENT '生产任务ID',
  `workstation_id` bigint DEFAULT NULL COMMENT '工作站ID',
  `tool_id` bigint DEFAULT NULL COMMENT '工具ID',
  `mold_id` bigint DEFAULT NULL COMMENT '模具ID',
  `lot_number` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '生产批号',
  `quality_status` int DEFAULT NULL COMMENT '质检状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `pollution_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '环保污染状态：CLEAN(无污染)/POLLUTED(有污染受控)；空=未判定',
  `pollution_location` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '环保去向/库位快照',
  `pollution_marked` bit(1) DEFAULT NULL COMMENT '环保标记（有污染/需管控）',
  `pollution_src_record` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '源污染判定记录编号(PC-...)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_code` (`code`,`tenant_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1011 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='批次管理表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_batch`
--

LOCK TABLES `mes_wm_batch` WRITE;
/*!40000 ALTER TABLE `mes_wm_batch` DISABLE KEYS */;
INSERT INTO `mes_wm_batch` VALUES (1,'PC',100,NULL,'2026-03-12 00:00:00',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'1','2026-03-13 23:49:42','1','2026-03-13 23:49:42',0x00,1,NULL,NULL,NULL,NULL),(2,'PC20260',100,NULL,'2026-02-26 00:00:00',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'1','2026-03-13 23:54:53','1','2026-03-13 23:54:53',0x00,1,NULL,NULL,NULL,NULL),(3,'PC202600001',75,'2026-03-21 00:00:00',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'1','2026-03-21 15:07:46','1','2026-03-21 15:07:46',0x00,1,NULL,NULL,NULL,NULL),(4,'PC202600002',75,'2026-03-24 00:00:00',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'1','2026-03-24 23:17:24','1','2026-03-24 23:17:24',0x00,1,NULL,NULL,NULL,NULL),(5,'BATCH_ITEM_1',1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'','2026-03-30 02:46:05','','2026-09-03 16:33:50',0x00,1,NULL,NULL,NULL,NULL),(6,'BATCH_ITEM_2',2,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'','2026-03-30 02:46:05','','2026-09-03 16:32:58',0x00,1,NULL,NULL,NULL,NULL),(7,'BATCH_ITEM_94',94,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'','2026-03-30 02:46:05','','2026-09-20 17:05:52',0x00,1,NULL,NULL,NULL,NULL),(8,'BATCH_ITEM_72',72,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'','2026-03-30 02:46:05','','2026-09-20 15:43:41',0x00,1,NULL,NULL,NULL,NULL),(9,'RAW-BATCH-001',94,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1,NULL,NULL,NULL,NULL,NULL,1,NULL,'1','2026-04-05 01:32:43','1','2026-09-03 16:33:44',0x00,1,NULL,NULL,NULL,NULL),(10,'PC202600003',100,NULL,'2026-04-17 00:00:00',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'1','2026-04-17 09:38:09','1','2026-09-03 16:32:50',0x00,1,NULL,NULL,NULL,NULL),(1001,'PC',1100,NULL,'2026-03-12 00:00:00',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'1','2026-03-13 23:49:42','1','2026-09-20 17:00:52',0x00,2010,NULL,NULL,NULL,NULL),(1002,'PC20260',1100,NULL,'2026-02-26 00:00:00',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'1','2026-03-13 23:54:53','1','2026-03-13 23:54:53',0x00,2010,NULL,NULL,NULL,NULL),(1003,'PC202600001',1075,'2026-03-21 00:00:00',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'1','2026-03-21 15:07:46','1','2026-09-20 11:40:10',0x00,2010,NULL,NULL,NULL,NULL),(1004,'PC202600002',1075,'2026-03-24 00:00:00',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'1','2026-03-24 23:17:24','1','2026-09-20 15:30:28',0x00,2010,NULL,NULL,NULL,NULL),(1005,'BATCH_ITEM_1',1001,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'','2026-03-30 02:46:05','','2026-09-10 08:34:38',0x00,2010,NULL,NULL,NULL,NULL),(1006,'BATCH_ITEM_2',1002,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'','2026-03-30 02:46:05','','2026-09-03 16:32:58',0x00,2010,NULL,NULL,NULL,NULL),(1007,'BATCH_ITEM_94',1094,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'','2026-03-30 02:46:05','','2026-09-20 17:06:00',0x00,2010,'POLLUTED','危废暂存间A-02',0x01,'PC-20260920170600858'),(1008,'BATCH_ITEM_72',1072,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'','2026-03-30 02:46:05','','2026-09-20 17:06:01',0x00,2010,NULL,NULL,NULL,NULL),(1009,'RAW-BATCH-001',1094,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1,NULL,'1','2026-04-05 01:32:43','1','2026-09-20 17:01:55',0x00,2010,NULL,NULL,NULL,NULL),(1010,'PC202600003',1100,NULL,'2026-04-17 00:00:00',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'1','2026-04-17 09:38:09','1','2026-09-20 17:06:02',0x00,2010,'POLLUTED','危废暂存间C-03',0x01,'PC-20260920170601449');
/*!40000 ALTER TABLE `mes_wm_batch` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_item_consume`
--

DROP TABLE IF EXISTS `mes_wm_item_consume`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_item_consume` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `work_order_id` bigint NOT NULL COMMENT '生产工单编号',
  `task_id` bigint NOT NULL COMMENT '生产任务编号',
  `workstation_id` bigint NOT NULL COMMENT '工作站编号',
  `process_id` bigint NOT NULL COMMENT '工序编号',
  `feedback_id` bigint NOT NULL COMMENT '报工记录编号',
  `consume_date` datetime NOT NULL COMMENT '消耗日期',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_feedback_id` (`feedback_id`) USING BTREE,
  KEY `idx_work_order_id` (`work_order_id`) USING BTREE,
  KEY `idx_task_id` (`task_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 物料消耗记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_item_consume`
--

LOCK TABLES `mes_wm_item_consume` WRITE;
/*!40000 ALTER TABLE `mes_wm_item_consume` DISABLE KEYS */;
INSERT INTO `mes_wm_item_consume` VALUES (1,1,4,3,3,7,'2026-03-19 23:02:57',4,'','1','2026-03-19 23:02:57','1','2026-03-19 23:02:57',0x00,1),(18,1,4,3,3,5,'2026-03-21 15:07:46',4,'','1','2026-03-21 15:07:46','1','2026-03-21 15:07:46',0x00,1),(19,1,4,3,3,6,'2026-03-21 15:09:48',4,'','1','2026-03-21 15:09:48','1','2026-03-21 15:09:48',0x00,1),(20,1,4,3,3,8,'2026-03-24 23:17:24',4,'','1','2026-03-24 23:17:24','1','2026-03-24 23:17:24',0x00,1),(21,1,1,1,1,9,'2026-03-24 23:19:17',4,'','1','2026-03-24 23:19:17','1','2026-03-24 23:19:17',0x00,1),(22,17,8,8,4,12,'2026-05-26 00:33:47',4,'验收造数：待检验消耗单','1','2026-05-26 00:33:47','1','2026-05-26 00:33:47',0x00,1),(23,17,7,7,1,13,'2026-05-26 00:33:47',4,'验收造数：已完成消耗单','1','2026-05-26 00:33:47','1','2026-05-26 00:33:47',0x00,1);
/*!40000 ALTER TABLE `mes_wm_item_consume` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_item_consume_detail`
--

DROP TABLE IF EXISTS `mes_wm_item_consume_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_item_consume_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `consume_id` bigint NOT NULL COMMENT '消耗记录编号',
  `line_id` bigint NOT NULL COMMENT '消耗记录行编号',
  `material_stock_id` bigint DEFAULT NULL COMMENT '库存台账编号',
  `item_id` bigint NOT NULL COMMENT '物料编号',
  `quantity` decimal(14,2) NOT NULL COMMENT '消耗数量',
  `batch_id` bigint DEFAULT NULL COMMENT '批次编号',
  `batch_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '批次号',
  `warehouse_id` bigint NOT NULL COMMENT '仓库编号',
  `location_id` bigint NOT NULL COMMENT '库区编号',
  `area_id` bigint NOT NULL COMMENT '库位编号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_consume_id` (`consume_id`) USING BTREE,
  KEY `idx_line_id` (`line_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 物料消耗记录明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_item_consume_detail`
--

LOCK TABLES `mes_wm_item_consume_detail` WRITE;
/*!40000 ALTER TABLE `mes_wm_item_consume_detail` DISABLE KEYS */;
INSERT INTO `mes_wm_item_consume_detail` VALUES (1,1,1,NULL,94,10.00,9,'RAW-BATCH-001',1,1,1,'','1','2026-04-05 01:32:43','1','2026-04-05 01:32:43',0x00,1);
/*!40000 ALTER TABLE `mes_wm_item_consume_detail` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_item_consume_line`
--

DROP TABLE IF EXISTS `mes_wm_item_consume_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_item_consume_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `consume_id` bigint NOT NULL COMMENT '消耗记录编号',
  `item_id` bigint NOT NULL COMMENT '物料编号',
  `quantity` decimal(14,2) NOT NULL COMMENT '消耗数量',
  `batch_id` bigint DEFAULT NULL COMMENT '批次编号',
  `batch_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '批次号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_consume_id` (`consume_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 物料消耗记录行';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_item_consume_line`
--

LOCK TABLES `mes_wm_item_consume_line` WRITE;
/*!40000 ALTER TABLE `mes_wm_item_consume_line` DISABLE KEYS */;
INSERT INTO `mes_wm_item_consume_line` VALUES (1,1,72,0.50,NULL,'','','1','2026-03-19 23:02:57','1','2026-03-19 23:02:57',0x00,1),(18,18,72,0.50,NULL,'','','1','2026-03-21 15:07:46','1','2026-03-21 15:07:46',0x00,1),(19,19,72,0.50,NULL,'','','1','2026-03-21 15:09:48','1','2026-03-21 15:09:48',0x00,1),(20,20,72,1.00,NULL,'','','1','2026-03-24 23:17:24','1','2026-03-24 23:17:24',0x00,1),(21,21,72,45.00,NULL,'','','1','2026-03-24 23:19:17','1','2026-03-24 23:19:17',0x00,1),(22,22,72,7.50,NULL,'IC-ACCEPT-UNCHECK','验收造数：待检验消耗行','1','2026-05-26 00:33:47','1','2026-05-26 00:33:47',0x00,1),(23,23,72,10.00,NULL,'IC-ACCEPT-FINISHED','验收造数：已完成消耗行','1','2026-05-26 00:33:47','1','2026-05-26 00:33:47',0x00,1);
/*!40000 ALTER TABLE `mes_wm_item_consume_line` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_item_receipt`
--

DROP TABLE IF EXISTS `mes_wm_item_receipt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_item_receipt` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '入库单编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '入库单名称',
  `iqc_id` bigint DEFAULT NULL COMMENT '来料检验单编号（关联 mes_qc_iqc.id）',
  `notice_id` bigint DEFAULT NULL COMMENT '到货通知单编号（关联 mes_wm_arrival_notice.id）',
  `purchase_order_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '采购订单号',
  `vendor_id` bigint DEFAULT NULL COMMENT '供应商编号（关联 mes_md_vendor.id）',
  `receipt_date` datetime DEFAULT NULL COMMENT '入库日期',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`code`) USING BTREE,
  KEY `idx_notice_id` (`notice_id`) USING BTREE,
  KEY `idx_vendor_id` (`vendor_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9201005 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 采购入库单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_item_receipt`
--

LOCK TABLES `mes_wm_item_receipt` WRITE;
/*!40000 ALTER TABLE `mes_wm_item_receipt` DISABLE KEYS */;
INSERT INTO `mes_wm_item_receipt` VALUES (1,'IR2026020001','铝材入库-草稿',NULL,3,NULL,200,'2026-02-20 00:00:00',0,'草稿状态，可测 CRUD + submit','1','2026-02-22 14:54:33','1','2026-02-22 23:06:31',0x00,1),(2,'IR2026020002','螺丝入库-待上架',1,2,NULL,2,'2026-02-19 00:00:00',4,'待上架，明细数量已匹配，可测 shelving','1','2026-02-22 14:54:33','1','2026-02-26 08:17:43',0x00,1),(3,'IR2026020003','铝材入库-待入库',2,3,NULL,1,'2026-02-21 00:00:00',5,'待入库，可测 execute（会 finish 通知 3）','1','2026-02-22 14:54:33','1','2026-02-26 05:18:31',0x00,1),(4,'IR2026020004','铜线入库-已完成',3,4,NULL,2,'2026-01-26 00:00:00',5,'已完成入库','1','2026-02-22 14:54:33','1','2026-02-26 08:17:43',0x00,1),(5,'IR2026020005','钢板入库-已取消',NULL,1,NULL,1,'2026-02-22 00:00:00',5,'已取消','1','2026-02-22 14:54:33','1','2026-02-26 05:18:31',0x00,1),(6,'IRo3krPXYf2D','xx',NULL,NULL,NULL,200,'2026-02-03 00:00:00',3,'','1','2026-02-22 23:07:11','1','2026-02-26 08:17:43',0x00,1),(7,'IRsr2UM5pbmU','测试入库-无检验（必填）',NULL,103,NULL,200,'2026-02-08 00:00:00',5,'','1','2026-02-25 20:03:40','1','2026-02-26 08:17:43',0x00,1),(8,'IRLqesOjVdQU','111',NULL,3,NULL,200,'2026-02-10 00:00:00',0,'','1','2026-02-26 00:29:51','1','2026-02-26 00:29:51',0x00,1),(9,'IRoUJ96EoC20','11',NULL,NULL,NULL,200,NULL,0,'','1','2026-02-26 00:40:34','1','2026-02-26 00:41:36',0x00,1),(10,'IRjrLOepPdD5',NULL,NULL,NULL,NULL,200,'2026-02-10 00:00:00',5,'','1','2026-02-26 00:41:49','1','2026-02-26 08:17:43',0x00,1),(11,'IRmZ3ZLok1RM','1122',NULL,104,NULL,201,'2026-02-03 00:00:00',3,'','1','2026-02-26 00:43:00','1','2026-02-26 08:17:43',0x00,1),(12,'111','23213',NULL,104,NULL,201,'2026-01-28 00:00:00',2,'','1','2026-02-26 01:19:23','1','2026-02-27 23:06:31',0x00,1),(13,'IRtxch5t3oqA','32132321',NULL,4,NULL,200,'2026-03-03 00:00:00',0,'','1','2026-03-13 23:20:40','1','2026-03-13 23:20:40',0x00,1),(14,'IR5rhFfpNJJq','3213213',NULL,NULL,NULL,200,'2026-03-02 00:00:00',3,'','1','2026-03-13 23:46:19','1','2026-03-29 18:23:55',0x00,1),(15,'IR20260329000001','XXXX',NULL,NULL,'AAA',200,'2026-03-18 00:00:00',4,'','1','2026-03-29 16:19:18','1','2026-03-29 18:06:09',0x00,1),(16,'IR-20260412-001','ä¸´æ—¶é‡‡è´­å…¥åº“å•çš„æ¨¡æ‹Ÿæ•°æ®',NULL,NULL,'PO-123456',1,'2026-04-11 18:28:00',0,'','','2026-04-11 18:28:00','1','2026-04-17 21:55:59',0x01,1),(9190000,'IR-HZ-20260430001','期初结存归位',NULL,NULL,NULL,9200000,'2026-04-30 09:00:00',4,'补 mes_wm_material_stock 130/131 的来源单据','80370','2026-04-30 09:00:00','80370','2026-04-30 09:00:00',0x00,2010),(9201000,'IR-HZ-20260512001','采购入库单',NULL,NULL,'PO-HZ-20260512',9200000,'2026-05-12 09:00:00',4,'仓库测试数据','80370','2026-05-12 09:00:00','80370','2026-05-12 09:00:00',0x00,2010),(9201001,'IR-HZ-20260520001','采购入库单',NULL,NULL,'PO-HZ-20260520',9200000,'2026-05-20 09:00:00',4,'仓库测试数据','80370','2026-05-20 09:00:00','80370','2026-05-20 09:00:00',0x00,2010),(9201002,'IR-HZ-20260603001','采购入库单',NULL,NULL,'PO-HZ-20260603',9200000,'2026-06-03 09:00:00',4,'仓库测试数据','80370','2026-06-03 09:00:00','80370','2026-06-03 09:00:00',0x00,2010),(9201003,'470430','🐡zzz 🐡zzz',NULL,NULL,NULL,9200000,'2026-09-02 18:01:41',0,'','80370','2026-09-11 18:01:54','80370','2026-09-11 18:02:24',0x00,2010),(9201004,'4704304','🐡zzz 🐡zzz',NULL,NULL,NULL,9200000,'2026-09-10 18:24:05',0,'','80370','2026-09-11 18:24:09','80370','2026-09-11 18:48:09',0x00,2010);
/*!40000 ALTER TABLE `mes_wm_item_receipt` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_item_receipt_detail`
--

DROP TABLE IF EXISTS `mes_wm_item_receipt_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_item_receipt_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `line_id` bigint NOT NULL COMMENT '入库单行编号（关联 mes_wm_item_receipt_line.id）',
  `receipt_id` bigint NOT NULL COMMENT '入库单编号（关联 mes_wm_item_receipt.id）',
  `item_id` bigint NOT NULL COMMENT '物料编号（关联 mes_md_item.id）',
  `quantity` decimal(14,2) DEFAULT NULL COMMENT '上架数量',
  `batch_id` bigint DEFAULT NULL COMMENT '批次编号',
  `warehouse_id` bigint DEFAULT NULL COMMENT '仓库编号（关联 mes_wm_warehouse.id）',
  `location_id` bigint DEFAULT NULL COMMENT '库区编号（关联 mes_wm_warehouse_location.id）',
  `area_id` bigint DEFAULT NULL COMMENT '库位编号（关联 mes_wm_warehouse_area.id）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_line_id` (`line_id`) USING BTREE,
  KEY `idx_receipt_id` (`receipt_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9203003 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 采购入库明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_item_receipt_detail`
--

LOCK TABLES `mes_wm_item_receipt_detail` WRITE;
/*!40000 ALTER TABLE `mes_wm_item_receipt_detail` DISABLE KEYS */;
INSERT INTO `mes_wm_item_receipt_detail` VALUES (1,3,2,3,600.00,NULL,701,711,721,'上架至原料区 A-01','1','2026-02-22 14:54:33','1','2026-02-22 14:54:33',0x00,1),(2,3,2,3,400.00,NULL,701,711,722,'上架至原料区 A-02','1','2026-02-22 14:54:33','1','2026-02-22 14:54:33',0x00,1),(3,4,2,4,500.00,NULL,701,711,722,'上架至原料区 A-02','1','2026-02-22 14:54:33','1','2026-02-22 14:54:33',0x00,1),(4,5,3,5,500.00,NULL,701,712,722,'上架至原料区 B-01','1','2026-02-22 14:54:33','1','2026-02-22 14:54:33',0x00,1),(5,5,3,5,300.00,NULL,701,712,723,'上架至原料区 B-02','1','2026-02-22 14:54:33','1','2026-02-22 14:54:33',0x00,1),(6,6,3,6,300.00,NULL,701,712,723,'上架至原料区 B-02','1','2026-02-22 14:54:33','1','2026-02-22 14:54:33',0x00,1),(7,7,4,7,400.00,NULL,701,711,721,'上架至原料区 A-01','1','2026-02-22 14:54:33','1','2026-02-22 14:54:33',0x00,1),(8,7,4,7,200.00,NULL,701,711,722,'上架至原料区 A-02','1','2026-02-22 14:54:33','1','2026-02-22 14:54:33',0x00,1),(9,9,6,69,123.00,NULL,702,713,724,'','1','2026-02-23 01:22:25','1','2026-02-23 01:40:02',0x01,1),(10,9,6,69,4.00,NULL,702,713,724,'','1','2026-02-23 01:55:09','1','2026-02-23 02:03:16',0x00,1),(11,9,6,69,321321.00,NULL,702,713,724,'','1','2026-02-23 02:03:24','1','2026-02-23 02:15:31',0x01,1),(12,9,6,69,555.00,NULL,701,712,723,'','1','2026-02-23 02:15:27','1','2026-02-23 02:15:27',0x00,1),(13,10,7,69,222.00,NULL,702,713,724,'','1','2026-02-26 00:24:22','1','2026-02-26 00:24:37',0x00,1),(14,11,10,69,1.00,NULL,702,713,724,'','1','2026-02-26 00:42:45','1','2026-02-26 00:42:45',0x00,1),(15,13,12,69,1.00,NULL,702,713,724,'','1','2026-02-27 23:11:16','1','2026-02-27 23:11:16',0x00,1),(16,19,15,94,1.00,NULL,702,713,724,'','1','2026-03-29 18:04:37','1','2026-03-29 18:04:37',0x00,1),(17,14,14,94,11.00,NULL,703,714,725,'','1','2026-03-29 18:23:46','1','2026-03-29 18:23:53',0x00,1),(9190200,9190100,9190000,1094,10.00,1009,704,716,727,'','80370','2026-04-30 09:00:00','80370','2026-04-30 09:00:00',0x00,2010),(9190201,9190101,9190000,1100,10.00,1001,704,716,727,'','80370','2026-04-30 09:00:00','80370','2026-04-30 09:00:00',0x00,2010),(9203000,9202000,9201000,1100,120.00,1001,704,716,727,'','80370','2026-05-12 09:00:00','80370','2026-05-12 09:00:00',0x00,2010),(9203001,9202001,9201001,1075,300.00,1003,704,716,727,'','80370','2026-05-20 09:00:00','80370','2026-05-20 09:00:00',0x00,2010),(9203002,9202002,9201002,1094,500.00,1007,704,716,727,'','80370','2026-06-03 09:00:00','80370','2026-06-03 09:00:00',0x00,2010);
/*!40000 ALTER TABLE `mes_wm_item_receipt_detail` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_item_receipt_line`
--

DROP TABLE IF EXISTS `mes_wm_item_receipt_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_item_receipt_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `receipt_id` bigint NOT NULL COMMENT '入库单编号（关联 mes_wm_item_receipt.id）',
  `arrival_notice_line_id` bigint DEFAULT NULL COMMENT '到货通知单行编号',
  `item_id` bigint NOT NULL COMMENT '物料编号（关联 mes_md_item.id）',
  `received_quantity` decimal(14,2) DEFAULT NULL COMMENT '入库数量',
  `batch_id` bigint DEFAULT NULL COMMENT '批次编号',
  `batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次编码',
  `production_date` datetime DEFAULT NULL COMMENT '生产日期',
  `expire_date` datetime DEFAULT NULL COMMENT '有效期',
  `lot_number` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '生产批号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_receipt_id` (`receipt_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9202005 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 采购入库单行';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_item_receipt_line`
--

LOCK TABLES `mes_wm_item_receipt_line` WRITE;
/*!40000 ALTER TABLE `mes_wm_item_receipt_line` DISABLE KEYS */;
INSERT INTO `mes_wm_item_receipt_line` VALUES (1,1,5,69,800.00,NULL,NULL,'2026-01-15 00:00:00','2027-01-15 00:00:00','PB20260115-A','铝板入库行','1','2026-02-22 14:54:33','1','2026-02-22 22:58:56',0x00,1),(2,1,6,70,300.00,NULL,NULL,'2026-01-16 00:00:00','2027-06-16 00:00:00','PB20260116-B','铝棒入库行','1','2026-02-22 14:54:33','1','2026-02-22 22:58:59',0x00,1),(3,2,3,3,1000.00,NULL,NULL,'2026-01-10 00:00:00','2027-01-10 00:00:00','PB20260110-C','六角螺丝入库','1','2026-02-22 14:54:33','1','2026-02-22 14:54:33',0x00,1),(4,2,4,4,500.00,NULL,NULL,'2026-01-12 00:00:00','2028-01-12 00:00:00','PB20260112-D','垫片入库','1','2026-02-22 14:54:33','1','2026-02-22 14:54:33',0x00,1),(5,3,5,5,800.00,NULL,NULL,'2026-01-15 00:00:00','2027-01-15 00:00:00','PB20260115-E','铝板入库-待执行','1','2026-02-22 14:54:33','1','2026-02-22 14:54:33',0x00,1),(6,3,6,6,300.00,NULL,NULL,'2026-01-16 00:00:00','2027-06-16 00:00:00','PB20260116-F','铝棒入库-待执行','1','2026-02-22 14:54:33','1','2026-02-22 14:54:33',0x00,1),(7,4,7,7,600.00,NULL,NULL,'2025-12-20 00:00:00','2026-12-20 00:00:00','PB20251220-G','铜线入库-已完成','1','2026-02-22 14:54:33','1','2026-02-22 14:54:33',0x00,1),(8,5,1,1,500.00,NULL,NULL,'2026-01-20 00:00:00','2027-01-20 00:00:00','PB20260120-H','钢板入库-已取消','1','2026-02-22 14:54:33','1','2026-02-22 14:54:33',0x00,1),(9,6,NULL,69,5.00,NULL,NULL,'2026-02-03 00:00:00','2026-02-13 00:00:00',NULL,'','1','2026-02-22 23:14:39','1','2026-02-22 23:14:39',0x00,1),(10,7,NULL,69,222.00,NULL,NULL,NULL,NULL,NULL,'','1','2026-02-25 21:53:46','1','2026-02-25 21:53:46',0x00,1),(11,10,NULL,69,1.00,NULL,NULL,NULL,NULL,NULL,'','1','2026-02-26 00:42:10','1','2026-02-26 00:42:10',0x00,1),(12,11,108,69,3.00,NULL,NULL,NULL,NULL,'1','','1','2026-02-26 01:11:17','1','2026-02-26 01:11:17',0x00,1),(13,12,108,69,222.00,NULL,NULL,NULL,NULL,NULL,'','1','2026-02-27 23:06:17','1','2026-02-27 23:06:17',0x00,1),(14,14,NULL,94,11.00,NULL,NULL,NULL,NULL,NULL,'','1','2026-03-13 23:47:17','1','2026-03-13 23:47:53',0x00,1),(15,14,NULL,100,10.00,1,'PC',NULL,'2026-03-12 00:00:00',NULL,'','1','2026-03-13 23:49:42','1','2026-03-13 23:52:19',0x01,1),(16,14,NULL,100,100.00,2,'PC20260',NULL,'2026-02-26 00:00:00',NULL,'','1','2026-03-13 23:54:53','1','2026-03-14 00:23:09',0x01,1),(17,14,NULL,94,10.00,NULL,NULL,'2026-03-19 00:00:00','2026-03-04 00:00:00','112','','1','2026-03-14 00:23:02','1','2026-03-14 00:23:05',0x01,1),(18,14,NULL,94,10.00,NULL,NULL,NULL,NULL,NULL,'','1','2026-03-14 00:23:16','1','2026-03-14 00:23:19',0x01,1),(19,15,NULL,94,1.00,NULL,NULL,'2026-03-29 00:00:00','2026-03-12 00:00:00','xxx','','1','2026-03-29 16:23:56','1','2026-03-29 16:23:56',0x00,1),(9190100,9190000,NULL,1094,10.00,1009,'RAW-BATCH-001',NULL,NULL,NULL,'','80370','2026-04-30 09:00:00','80370','2026-04-30 09:00:00',0x00,2010),(9190101,9190000,NULL,1100,10.00,1001,'PC',NULL,NULL,NULL,'','80370','2026-04-30 09:00:00','80370','2026-04-30 09:00:00',0x00,2010),(9202000,9201000,NULL,1100,120.00,1001,'PC',NULL,NULL,NULL,'','80370','2026-05-12 09:00:00','80370','2026-05-12 09:00:00',0x00,2010),(9202001,9201001,NULL,1075,300.00,1003,'PC202600001',NULL,NULL,NULL,'','80370','2026-05-20 09:00:00','80370','2026-05-20 09:00:00',0x00,2010),(9202002,9201002,NULL,1094,500.00,1007,'BATCH_ITEM_94',NULL,NULL,NULL,'','80370','2026-06-03 09:00:00','80370','2026-06-03 09:00:00',0x00,2010),(9202003,9201003,NULL,1094,12.00,NULL,NULL,NULL,NULL,NULL,'','80370','2026-09-11 18:02:16','80370','2026-09-11 18:02:16',0x00,2010),(9202004,9201004,NULL,1094,123.00,NULL,NULL,NULL,NULL,NULL,'','80370','2026-09-11 18:24:32','80370','2026-09-11 18:24:32',0x00,2010);
/*!40000 ALTER TABLE `mes_wm_item_receipt_line` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_material_stock`
--

DROP TABLE IF EXISTS `mes_wm_material_stock`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_material_stock` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `item_type_id` bigint DEFAULT NULL COMMENT '物料分类编号（mes_md_item_type.id）',
  `item_id` bigint NOT NULL COMMENT '物料编号（mes_md_item.id）',
  `batch_id` bigint DEFAULT NULL COMMENT '批次编号（mes_wm_batch.id）',
  `batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `warehouse_id` bigint NOT NULL COMMENT '仓库编号（mes_wm_warehouse.id）',
  `location_id` bigint DEFAULT NULL COMMENT '库区编号（mes_wm_warehouse_location.id）',
  `area_id` bigint DEFAULT NULL COMMENT '库位编号（mes_wm_warehouse_area.id）',
  `vendor_id` bigint DEFAULT NULL COMMENT '供应商编号（mes_md_vendor.id）',
  `quantity` decimal(14,4) NOT NULL DEFAULT '0.0000' COMMENT '在库数量',
  `receipt_time` datetime DEFAULT NULL COMMENT '入库时间',
  `frozen` bit(1) DEFAULT b'0' COMMENT '是否冻结',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_stock_composite` (`tenant_id`,`item_id`,`batch_id`,`warehouse_id`,`location_id`,`area_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE,
  KEY `idx_warehouse_id` (`warehouse_id`) USING BTREE,
  KEY `idx_batch_id` (`batch_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9213030 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 库存台账（仓库现有量）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_material_stock`
--

LOCK TABLES `mes_wm_material_stock` WRITE;
/*!40000 ALTER TABLE `mes_wm_material_stock` DISABLE KEYS */;
INSERT INTO `mes_wm_material_stock` VALUES (1,1,70,5,'BATCH_ITEM_1',701,711,721,1,500.0000,'2026-01-15 10:00:00',0x00,'1','2026-02-20 01:02:46','1','2026-03-31 15:26:14',0x00,1),(2,1,2,6,'BATCH_ITEM_2',701,711,722,NULL,200.0000,'2026-02-01 14:30:00',0x00,'1','2026-02-20 01:02:46','1','2026-03-30 02:46:05',0x00,1),(3,1,1,5,'BATCH_ITEM_1',702,713,724,1,150.0000,'2026-02-10 09:00:00',0x00,'1','2026-02-20 01:02:46','1','2026-09-20 15:30:29',0x00,1),(4,275,69,1,'TEST',702,713,724,200,0.0000,'2026-02-26 00:24:44',0x00,'1','2026-02-26 00:24:44','1','2026-03-30 13:54:28',0x00,1),(5,272,100,1,'PC',701,NULL,NULL,NULL,800.0000,'2026-03-01 10:00:00',0x01,'1','2026-03-10 11:14:37','1','2026-09-11 16:03:01',0x00,1),(7,272,100,1,'PC',702,NULL,NULL,NULL,200.0000,'2026-03-08 09:15:00',0x01,'1','2026-03-10 11:14:37','1','2026-09-11 16:03:01',0x00,1),(9,282,94,7,'BATCH_ITEM_94',702,713,724,NULL,321321444.0000,'2026-03-22 21:38:40',0x00,'1','2026-03-22 21:38:40','1','2026-03-31 01:41:22',0x00,1),(10,282,94,7,'BATCH_ITEM_94',701,712,723,NULL,0.0000,'2026-03-22 23:11:51',0x00,'1','2026-03-22 23:11:51','1','2026-03-30 03:11:49',0x00,1),(11,274,72,8,'BATCH_ITEM_72',703,714,725,NULL,-46.0000,'2026-03-24 23:17:24',0x00,'1','2026-03-24 23:17:24','1','2026-03-30 02:46:05',0x00,1),(12,277,75,4,'PC202600002',703,714,725,NULL,2.0000,'2026-03-24 23:17:24',0x00,'1','2026-03-24 23:17:24','1','2026-03-29 02:21:39',0x00,1),(14,282,94,7,'BATCH_ITEM_94',703,714,725,NULL,1.0000,'2026-03-30 11:11:50',0x00,'1','2026-03-30 11:11:50','1','2026-03-30 03:11:49',0x00,1),(16,275,69,0,'B-TEST',702,711,721,0,100.0000,'2026-03-31 15:28:21',0x00,'admin','2026-03-31 15:28:21','admin','2026-03-31 15:28:21',0x00,1),(17,275,70,0,'B-TEST',702,711,721,0,100.0000,'2026-03-31 15:28:21',0x00,'admin','2026-03-31 15:28:21','admin','2026-03-31 15:28:21',0x00,1),(18,275,71,0,'B-TEST',702,711,721,0,100.0000,'2026-03-31 15:28:21',0x00,'admin','2026-03-31 15:28:21','admin','2026-03-31 15:28:21',0x00,1),(19,274,72,0,'B-TEST',702,711,721,0,100.0000,'2026-03-31 15:28:21',0x00,'admin','2026-03-31 15:28:21','admin','2026-03-31 15:28:21',0x00,1),(20,276,73,0,'B-TEST',702,711,721,0,100.0000,'2026-03-31 15:28:21',0x00,'admin','2026-03-31 15:28:21','admin','2026-03-31 15:28:21',0x00,1),(21,276,74,0,'B-TEST',702,711,721,0,100.0000,'2026-03-31 15:28:21',0x00,'admin','2026-03-31 15:28:21','admin','2026-03-31 15:28:21',0x00,1),(22,277,75,0,'B-TEST',702,711,721,0,100.0000,'2026-03-31 15:28:21',0x00,'admin','2026-03-31 15:28:21','admin','2026-03-31 15:28:21',0x00,1),(23,282,94,0,'B-TEST',702,711,721,0,100.0000,'2026-03-31 15:28:21',0x00,'admin','2026-03-31 15:28:21','admin','2026-03-31 15:28:21',0x00,1),(24,282,95,0,'B-TEST',702,711,721,0,100.0000,'2026-03-31 15:28:21',0x00,'admin','2026-03-31 15:28:21','admin','2026-03-31 15:28:21',0x00,1),(25,276,96,0,'B-TEST',702,711,721,0,100.0000,'2026-03-31 15:28:21',0x00,'admin','2026-03-31 15:28:21','admin','2026-03-31 15:28:21',0x00,1),(26,272,100,0,'B-TEST',702,711,721,0,100.0000,'2026-03-31 15:28:21',0x00,'admin','2026-03-31 15:28:21','admin','2026-03-31 15:28:21',0x00,1),(27,275,101,0,'B-TEST',702,711,721,0,100.0000,'2026-03-31 15:28:21',0x00,'admin','2026-03-31 15:28:21','admin','2026-03-31 15:28:21',0x00,1),(28,274,102,0,'B-TEST',702,711,721,0,100.0000,'2026-03-31 15:28:21',0x00,'admin','2026-03-31 15:28:21','admin','2026-03-31 15:28:21',0x00,1),(29,273,103,0,'B-TEST',702,711,721,0,100.0000,'2026-03-31 15:28:21',0x00,'admin','2026-03-31 15:28:21','admin','2026-03-31 15:28:21',0x00,1),(30,200,104,0,'B-TEST',702,711,721,0,100.0000,'2026-03-31 15:28:21',0x00,'admin','2026-03-31 15:28:21','admin','2026-03-31 15:28:21',0x00,1),(31,274,105,0,'B-TEST',702,711,721,0,100.0000,'2026-03-31 15:28:21',0x00,'admin','2026-03-31 15:28:21','admin','2026-03-31 15:28:21',0x00,1),(32,274,106,0,'B-TEST',702,711,721,0,100.0000,'2026-03-31 15:28:21',0x00,'admin','2026-03-31 15:28:21','admin','2026-03-31 15:28:21',0x00,1),(48,275,69,999,'B-TEST-2',702,711,721,0,50.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(49,275,70,999,'B-TEST-2',702,711,721,0,50.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(50,275,71,999,'B-TEST-2',702,711,721,0,50.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(51,274,72,999,'B-TEST-2',702,711,721,0,50.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(52,276,73,999,'B-TEST-2',702,711,721,0,50.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(53,276,74,999,'B-TEST-2',702,711,721,0,50.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(54,277,75,999,'B-TEST-2',702,711,721,0,50.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(55,282,94,999,'B-TEST-2',702,711,721,0,50.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(56,282,95,999,'B-TEST-2',702,711,721,0,50.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(57,276,96,999,'B-TEST-2',702,711,721,0,50.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(58,272,100,999,'B-TEST-2',702,711,721,0,50.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(59,275,101,999,'B-TEST-2',702,711,721,0,50.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(60,274,102,999,'B-TEST-2',702,711,721,0,50.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(61,273,103,999,'B-TEST-2',702,711,721,0,50.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(62,200,104,999,'B-TEST-2',702,711,721,0,50.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(63,274,105,999,'B-TEST-2',702,711,721,0,50.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(64,274,106,999,'B-TEST-2',702,711,721,0,50.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(79,275,69,888,'B-TEST-3',702,711,721,0,80.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(80,275,70,888,'B-TEST-3',702,711,721,0,80.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(81,275,71,888,'B-TEST-3',702,711,721,0,80.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(82,274,72,888,'B-TEST-3',702,711,721,0,80.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(83,276,73,888,'B-TEST-3',702,711,721,0,80.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(84,276,74,888,'B-TEST-3',702,711,721,0,80.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(85,277,75,888,'B-TEST-3',702,711,721,0,80.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(86,282,94,888,'B-TEST-3',702,711,721,0,80.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(87,282,95,888,'B-TEST-3',702,711,721,0,80.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(88,276,96,888,'B-TEST-3',702,711,721,0,80.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(89,272,100,888,'B-TEST-3',702,711,721,0,80.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(90,275,101,888,'B-TEST-3',702,711,721,0,80.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(91,274,102,888,'B-TEST-3',702,711,721,0,80.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(92,273,103,888,'B-TEST-3',702,711,721,0,80.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(93,200,104,888,'B-TEST-3',702,711,721,0,80.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(94,274,105,888,'B-TEST-3',702,711,721,0,80.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(95,274,106,888,'B-TEST-3',702,711,721,0,80.0000,'2026-03-31 15:29:46',0x00,'admin','2026-03-31 15:29:46','admin','2026-03-31 15:29:46',0x00,1),(130,282,1094,1009,'RAW-BATCH-001',704,716,727,9200000,10.0000,'2026-04-30 09:00:00',0x00,'80370','2026-04-30 09:00:00','80370','2026-09-20 17:06:02',0x00,2010),(9213000,272,1100,1001,'PC',704,716,727,9200000,60.0000,'2026-05-12 09:00:00',0x01,'80370','2026-09-10 09:00:00','80370','2026-09-11 18:24:21',0x00,2010),(9213001,277,1075,1003,'PC202600001',704,716,727,9200000,300.0000,'2026-05-20 09:00:00',0x00,'80370','2026-09-10 09:00:00','80370','2026-09-20 17:06:01',0x00,2010),(9213002,282,1094,1007,'BATCH_ITEM_94',704,716,727,9200000,230.0000,'2026-06-03 09:00:00',0x01,'80370','2026-09-10 09:00:00','80370','2026-09-20 17:06:00',0x00,2010),(9213003,274,1072,NULL,NULL,704,716,727,NULL,80.0000,'2026-06-18 09:00:00',0x00,'80370','2026-09-10 09:00:00','80370','2026-09-10 09:00:00',0x00,2010),(9213005,282,1094,1007,'BATCH_ITEM_94',705,717,728,NULL,70.0000,'2026-07-08 09:00:00',0x01,'80370','2026-09-10 09:00:00','80370','2026-09-20 17:06:00',0x00,2010),(9213007,272,1100,1001,'PC',705,717,729,NULL,40.0000,'2026-07-15 09:00:00',0x01,'80370','2026-09-10 09:00:00','80370','2026-09-11 18:24:21',0x00,2010),(9213028,NULL,100,1009,'RAW-BATCH-001',701,NULL,NULL,NULL,10.0000,'2026-09-20 17:01:56',0x00,'e2e','2026-09-20 17:01:56','','2026-09-20 17:06:02',0x00,2010),(9213029,NULL,100,1001,'PC',701,NULL,NULL,NULL,10.0000,'2026-09-20 17:01:56',0x01,'e2e','2026-09-20 17:01:56','','2026-09-20 17:05:47',0x00,2010);
/*!40000 ALTER TABLE `mes_wm_material_stock` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_misc_issue`
--

DROP TABLE IF EXISTS `mes_wm_misc_issue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_misc_issue` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '出库单编号',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '出库单名称',
  `type` int NOT NULL COMMENT '杂项类型',
  `source_doc_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源单据类型',
  `source_doc_id` bigint DEFAULT NULL COMMENT '来源单据ID',
  `source_doc_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源单据编号',
  `issue_date` datetime DEFAULT NULL COMMENT '出库日期',
  `status` int NOT NULL COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_code` (`code`,`deleted`,`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9207003 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 杂项出库单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_misc_issue`
--

LOCK TABLES `mes_wm_misc_issue` WRITE;
/*!40000 ALTER TABLE `mes_wm_misc_issue` DISABLE KEYS */;
INSERT INTO `mes_wm_misc_issue` VALUES (1,'MI2026030201','测试杂项出库单',1,NULL,NULL,NULL,'1970-01-01 08:00:00',4,'测试创建杂项出库单','1','2026-03-02 22:14:27','1','2026-03-03 07:34:33',0x00,1),(2,'MI2026030202','测试报废出库单',1,NULL,NULL,NULL,'1970-01-01 08:00:00',4,'测试报废出库','1','2026-03-02 22:14:28','1','2026-03-22 20:10:24',0x00,1),(3,'MIokj1zVk4I6','eee',1,NULL,NULL,NULL,NULL,4,NULL,'1','2026-03-03 19:01:24','1','2026-03-03 19:11:27',0x00,1),(4,'MI47CkggsQpp','1231321',1,NULL,NULL,NULL,'2026-03-10 00:00:00',4,NULL,'1','2026-03-22 20:10:43','1','2026-03-22 20:10:57',0x00,1),(5,'MIwCYpUvm6gE','AAA',1,NULL,NULL,NULL,NULL,3,NULL,'1','2026-03-22 21:36:23','1','2026-03-22 21:37:07',0x00,1),(6,'MIhtTkVvZZgk','123',1,NULL,NULL,NULL,'2026-03-17 00:00:00',3,NULL,'1','2026-03-22 21:50:45','1','2026-03-30 22:48:11',0x00,1),(7,'AABBB','EEE',1,NULL,NULL,NULL,'2026-04-02 00:00:00',3,NULL,'1','2026-03-30 23:04:55','1','2026-03-30 23:05:13',0x00,1),(8,'MISCI20260417001','ABC',1,NULL,NULL,NULL,'2026-04-16 00:00:00',0,NULL,'1','2026-04-17 08:41:42','1','2026-04-17 08:41:42',0x00,1),(9207000,'MI-HZ-20260805001','其他出库单',1,NULL,NULL,NULL,'2026-08-05 09:00:00',4,'仓库测试数据','80370','2026-08-05 09:00:00','80370','2026-08-05 09:00:00',0x00,2010),(9207001,'MI-HZ-20260820001','其他出库单',1,NULL,NULL,NULL,'2026-08-20 09:00:00',4,'仓库测试数据','80370','2026-08-20 09:00:00','80370','2026-08-20 09:00:00',0x00,2010),(9207002,'MI-HZ-20260902001','其他出库单',1,NULL,NULL,NULL,'2026-09-02 09:00:00',4,'仓库测试数据','80370','2026-09-02 09:00:00','80370','2026-09-02 09:00:00',0x00,2010);
/*!40000 ALTER TABLE `mes_wm_misc_issue` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_misc_issue_detail`
--

DROP TABLE IF EXISTS `mes_wm_misc_issue_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_misc_issue_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `issue_id` bigint NOT NULL COMMENT '出库单编号',
  `line_id` bigint NOT NULL COMMENT '出库单行编号',
  `material_stock_id` bigint DEFAULT NULL COMMENT '库存记录ID',
  `item_id` bigint NOT NULL COMMENT '物料编号',
  `quantity` decimal(14,2) NOT NULL COMMENT '出库数量',
  `batch_id` bigint DEFAULT NULL COMMENT '批次编号',
  `batch_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `warehouse_id` bigint DEFAULT NULL COMMENT '仓库编号',
  `location_id` bigint DEFAULT NULL COMMENT '库区编号',
  `area_id` bigint DEFAULT NULL COMMENT '库位编号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_issue_id` (`issue_id`) USING BTREE,
  KEY `idx_line_id` (`line_id`) USING BTREE,
  KEY `idx_material_stock_id` (`material_stock_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9209003 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 杂项出库明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_misc_issue_detail`
--

LOCK TABLES `mes_wm_misc_issue_detail` WRITE;
/*!40000 ALTER TABLE `mes_wm_misc_issue_detail` DISABLE KEYS */;
INSERT INTO `mes_wm_misc_issue_detail` VALUES (1,3,4,NULL,69,1.00,NULL,NULL,NULL,NULL,NULL,'','1','2026-03-03 19:06:15','1','2026-03-03 19:11:23',0x00,1),(2,4,5,NULL,69,123.00,NULL,NULL,702,713,724,'','1','2026-03-22 20:10:50','1','2026-03-22 20:10:50',0x00,1),(3,5,6,NULL,69,123.00,NULL,NULL,701,712,723,'','1','2026-03-22 21:36:28','1','2026-03-22 21:36:48',0x00,1),(4,6,7,NULL,69,321321.00,NULL,NULL,702,713,724,'','1','2026-03-22 21:50:56','1','2026-03-22 21:50:56',0x00,1),(5,7,8,14,94,123.00,NULL,'BATCH_ITEM_94',703,714,725,'','1','2026-03-30 23:05:11','1','2026-03-30 23:05:11',0x00,1),(6,8,9,1,70,123.00,NULL,'BATCH_ITEM_1',701,711,721,'','1','2026-04-17 08:42:16','1','2026-04-17 08:42:16',0x00,1),(9209000,9207000,9208000,9213002,1094,150.00,1007,'BATCH_ITEM_94',704,716,727,'','80370','2026-08-05 09:00:00','80370','2026-08-05 09:00:00',0x00,2010),(9209001,9207001,9208001,9213005,1094,50.00,1007,'BATCH_ITEM_94',705,717,728,'','80370','2026-08-20 09:00:00','80370','2026-08-20 09:00:00',0x00,2010),(9209002,9207002,9208002,9213000,1100,30.00,1001,'PC',704,716,727,'','80370','2026-09-02 09:00:00','80370','2026-09-02 09:00:00',0x00,2010);
/*!40000 ALTER TABLE `mes_wm_misc_issue_detail` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_misc_issue_line`
--

DROP TABLE IF EXISTS `mes_wm_misc_issue_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_misc_issue_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `issue_id` bigint NOT NULL COMMENT '出库单编号',
  `source_doc_line_id` bigint DEFAULT NULL COMMENT '来源单据行ID',
  `material_stock_id` bigint DEFAULT NULL COMMENT '库存记录ID',
  `item_id` bigint NOT NULL COMMENT '物料编号',
  `quantity` decimal(15,2) NOT NULL COMMENT '出库数量',
  `batch_id` bigint DEFAULT NULL COMMENT '批次编号',
  `batch_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `warehouse_id` bigint DEFAULT NULL COMMENT '仓库编号',
  `location_id` bigint DEFAULT NULL COMMENT '库区编号',
  `area_id` bigint DEFAULT NULL COMMENT '库位编号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_issue_id` (`issue_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9208003 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 杂项出库单行';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_misc_issue_line`
--

LOCK TABLES `mes_wm_misc_issue_line` WRITE;
/*!40000 ALTER TABLE `mes_wm_misc_issue_line` DISABLE KEYS */;
INSERT INTO `mes_wm_misc_issue_line` VALUES (1,1,NULL,NULL,69,10.00,NULL,NULL,702,713,724,NULL,'1','2026-03-03 12:51:11','1','2026-03-03 12:51:11',0x00,1),(4,3,NULL,NULL,69,1.00,NULL,NULL,NULL,NULL,NULL,NULL,'1','2026-03-03 19:06:15','1','2026-03-03 19:11:23',0x00,1),(5,4,NULL,NULL,69,123.00,NULL,NULL,702,713,724,NULL,'1','2026-03-22 20:10:50','1','2026-03-22 20:10:50',0x00,1),(6,5,NULL,NULL,69,123.00,NULL,NULL,701,712,723,NULL,'1','2026-03-22 21:36:28','1','2026-03-22 21:36:48',0x00,1),(7,6,NULL,NULL,69,321321.00,NULL,NULL,702,713,724,NULL,'1','2026-03-22 21:50:56','1','2026-03-22 21:50:56',0x00,1),(8,7,NULL,14,94,123.00,NULL,'BATCH_ITEM_94',703,714,725,NULL,'1','2026-03-30 23:05:11','1','2026-03-30 23:05:11',0x00,1),(9,8,NULL,1,70,123.00,NULL,'BATCH_ITEM_1',701,711,721,NULL,'1','2026-04-17 08:42:16','1','2026-04-17 08:42:16',0x00,1),(9208000,9207000,NULL,9213002,1094,150.00,1007,'BATCH_ITEM_94',704,716,727,NULL,'80370','2026-08-05 09:00:00','80370','2026-08-05 09:00:00',0x00,2010),(9208001,9207001,NULL,9213005,1094,50.00,1007,'BATCH_ITEM_94',705,717,728,NULL,'80370','2026-08-20 09:00:00','80370','2026-08-20 09:00:00',0x00,2010),(9208002,9207002,NULL,9213000,1100,30.00,1001,'PC',704,716,727,NULL,'80370','2026-09-02 09:00:00','80370','2026-09-02 09:00:00',0x00,2010);
/*!40000 ALTER TABLE `mes_wm_misc_issue_line` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_misc_receipt`
--

DROP TABLE IF EXISTS `mes_wm_misc_receipt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_misc_receipt` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '入库单编码',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '入库单名称',
  `type` int NOT NULL COMMENT '杂项类型',
  `source_doc_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '来源单据类型',
  `source_doc_id` bigint DEFAULT NULL COMMENT '来源单据 ID',
  `source_doc_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '来源单据编号',
  `receipt_date` datetime NOT NULL COMMENT '入库日期',
  `status` int NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_code` (`code`,`deleted`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_receipt_date` (`receipt_date`) USING BTREE,
  KEY `idx_source_doc` (`source_doc_id`,`source_doc_type`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9204001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MES 杂项入库单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_misc_receipt`
--

LOCK TABLES `mes_wm_misc_receipt` WRITE;
/*!40000 ALTER TABLE `mes_wm_misc_receipt` DISABLE KEYS */;
INSERT INTO `mes_wm_misc_receipt` VALUES (1,'MRRvmpXXBVDn','1321',1,NULL,NULL,NULL,'2026-03-09 00:00:00',4,NULL,'1','2026-03-03 19:31:29','1','2026-03-03 19:31:45',0x00,1),(2,'MRbJ5HI6aOMZ','AAA',1,NULL,NULL,NULL,'2026-03-15 00:00:00',4,NULL,'1','2026-03-22 20:08:52','1','2026-03-22 20:09:34',0x00,1),(3,'MRbWmZ1QXwg8','31221',1,NULL,NULL,NULL,'2026-03-11 00:00:00',3,NULL,'1','2026-03-22 21:37:32','1','2026-03-22 21:38:02',0x00,1),(4,'MRlD8OQUII96','32132',1,NULL,NULL,NULL,'2026-03-03 00:00:00',4,NULL,'1','2026-03-22 21:38:24','1','2026-03-22 21:38:40',0x00,1),(5,'MROO8b4vMA5x','321321',1,'EEE',NULL,'ABC','2026-03-03 00:00:00',4,NULL,'1','2026-03-22 21:51:34','1','2026-03-31 09:41:23',0x00,1),(6,'MISCR20260417001','EEE',1,NULL,NULL,NULL,'2026-04-23 00:00:00',0,NULL,'1','2026-04-17 08:43:37','1','2026-05-29 23:41:02',0x00,1),(9204000,'MR-HZ-20260618001','其他入库单',1,NULL,NULL,NULL,'2026-06-18 09:00:00',4,'仓库测试数据','80370','2026-06-18 09:00:00','80370','2026-06-18 09:00:00',0x00,2010);
/*!40000 ALTER TABLE `mes_wm_misc_receipt` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_misc_receipt_detail`
--

DROP TABLE IF EXISTS `mes_wm_misc_receipt_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_misc_receipt_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `receipt_id` bigint NOT NULL COMMENT '入库单编号',
  `line_id` bigint NOT NULL COMMENT '入库单行编号',
  `item_id` bigint NOT NULL COMMENT '物料编号',
  `quantity` decimal(14,2) NOT NULL COMMENT '入库数量',
  `batch_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `warehouse_id` bigint DEFAULT NULL COMMENT '仓库编号',
  `location_id` bigint DEFAULT NULL COMMENT '库区编号',
  `area_id` bigint DEFAULT NULL COMMENT '库位编号',
  `production_date` datetime DEFAULT NULL COMMENT '生产日期',
  `expire_date` datetime DEFAULT NULL COMMENT '有效期',
  `production_batch_number` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '生产批号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_receipt_id` (`receipt_id`) USING BTREE,
  KEY `idx_line_id` (`line_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9206001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 杂项入库明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_misc_receipt_detail`
--

LOCK TABLES `mes_wm_misc_receipt_detail` WRITE;
/*!40000 ALTER TABLE `mes_wm_misc_receipt_detail` DISABLE KEYS */;
INSERT INTO `mes_wm_misc_receipt_detail` VALUES (1,1,1,69,123.00,NULL,702,713,724,NULL,NULL,NULL,'','1','2026-03-03 19:31:39','1','2026-03-03 19:31:39',0x00,1),(2,2,2,69,123.00,NULL,702,NULL,NULL,NULL,NULL,NULL,'','1','2026-03-22 20:09:00','1','2026-03-22 12:09:13',0x01,1),(3,2,3,69,123.00,'123',702,713,724,NULL,NULL,NULL,'','1','2026-03-22 20:09:22','1','2026-03-22 20:09:22',0x00,1),(4,3,4,69,1.00,NULL,702,713,724,NULL,NULL,NULL,'','1','2026-03-22 21:37:40','1','2026-03-22 21:37:40',0x00,1),(5,4,5,94,123.00,NULL,702,713,724,NULL,NULL,NULL,'312321','1','2026-03-22 21:38:34','1','2026-03-22 21:38:34',0x00,1),(6,5,6,94,321321321.00,NULL,702,713,724,NULL,NULL,NULL,'','1','2026-03-22 21:51:43','1','2026-03-22 21:51:43',0x00,1),(7,6,7,103,123.00,NULL,702,713,724,NULL,NULL,NULL,'1213','1','2026-04-17 08:43:50','1','2026-05-29 23:40:52',0x00,1),(9206000,9204000,9205000,1072,80.00,NULL,704,716,727,NULL,NULL,NULL,'','80370','2026-06-18 09:00:00','80370','2026-06-18 09:00:00',0x00,2010);
/*!40000 ALTER TABLE `mes_wm_misc_receipt_detail` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_misc_receipt_line`
--

DROP TABLE IF EXISTS `mes_wm_misc_receipt_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_misc_receipt_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `receipt_id` bigint NOT NULL COMMENT '入库单编号',
  `item_id` bigint NOT NULL COMMENT '物料编号',
  `quantity` decimal(20,6) NOT NULL COMMENT '入库数量',
  `batch_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '批次号',
  `warehouse_id` bigint NOT NULL COMMENT '仓库编号',
  `location_id` bigint DEFAULT NULL COMMENT '库区编号',
  `area_id` bigint DEFAULT NULL COMMENT '库位编号',
  `production_date` datetime DEFAULT NULL COMMENT '生产日期（暂不使用）',
  `expire_date` datetime DEFAULT NULL COMMENT '有效期（暂不使用）',
  `lot_number` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '生产批号（暂不使用）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_receipt_id` (`receipt_id`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE,
  KEY `idx_warehouse` (`warehouse_id`,`location_id`,`area_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9205001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MES 杂项入库单行';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_misc_receipt_line`
--

LOCK TABLES `mes_wm_misc_receipt_line` WRITE;
/*!40000 ALTER TABLE `mes_wm_misc_receipt_line` DISABLE KEYS */;
INSERT INTO `mes_wm_misc_receipt_line` VALUES (1,1,69,123.000000,NULL,702,713,724,NULL,NULL,NULL,NULL,'1','2026-03-03 19:31:39','1','2026-03-03 19:31:39',0x00,1),(2,2,69,123.000000,NULL,702,NULL,NULL,NULL,NULL,NULL,NULL,'1','2026-03-22 20:09:00','1','2026-03-22 20:09:13',0x01,1),(3,2,69,123.000000,'123',702,713,724,NULL,NULL,NULL,NULL,'1','2026-03-22 20:09:22','1','2026-03-22 20:09:22',0x00,1),(4,3,69,1.000000,NULL,702,713,724,NULL,NULL,NULL,NULL,'1','2026-03-22 21:37:40','1','2026-03-22 21:37:40',0x00,1),(5,4,94,123.000000,NULL,702,713,724,NULL,NULL,NULL,'312321','1','2026-03-22 21:38:34','1','2026-03-22 21:38:34',0x00,1),(6,5,94,321321321.000000,NULL,702,713,724,NULL,NULL,NULL,NULL,'1','2026-03-22 21:51:43','1','2026-03-22 21:51:43',0x00,1),(7,6,103,123.000000,NULL,702,713,724,NULL,NULL,NULL,'1213','1','2026-04-17 08:43:50','1','2026-05-29 23:40:52',0x00,1),(9205000,9204000,1072,80.000000,NULL,704,716,727,NULL,NULL,NULL,NULL,'80370','2026-06-18 09:00:00','80370','2026-06-18 09:00:00',0x00,2010);
/*!40000 ALTER TABLE `mes_wm_misc_receipt_line` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_outsource_issue`
--

DROP TABLE IF EXISTS `mes_wm_outsource_issue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_outsource_issue` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '发料单ID',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发料单编号',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发料单名称',
  `vendor_id` bigint NOT NULL COMMENT '供应商ID',
  `work_order_id` bigint DEFAULT NULL COMMENT '生产工单ID',
  `issue_date` datetime DEFAULT NULL COMMENT '发料日期',
  `status` int NOT NULL DEFAULT '0' COMMENT '单据状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_code` (`code`,`tenant_id`) USING BTREE,
  KEY `idx_vendor_id` (`vendor_id`) USING BTREE,
  KEY `idx_workorder_id` (`work_order_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 外协发料单主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_outsource_issue`
--

LOCK TABLES `mes_wm_outsource_issue` WRITE;
/*!40000 ALTER TABLE `mes_wm_outsource_issue` DISABLE KEYS */;
INSERT INTO `mes_wm_outsource_issue` VALUES (1,'WOS202603020001','外协发料单001-修改',200,NULL,'1970-01-01 08:00:00',4,'测试发料单-已修改','1','2026-03-02 22:14:59','1','2026-03-02 22:17:02',0x00,1),(2,'WOS202603020002','外协发料单002',201,NULL,'1970-01-01 08:00:00',0,'测试删除','1','2026-03-02 22:17:46','1','2026-03-02 22:17:57',0x01,1),(3,'WOSZsffkmATpU','呃呃',200,NULL,'2026-03-18 00:00:00',2,'','1','2026-03-03 20:35:46','1','2026-03-04 01:18:08',0x00,1),(4,'WOScd6ieKFrT1','123',200,123456,'2026-03-10 00:00:00',0,'','1','2026-03-04 01:22:47','1','2026-03-04 01:27:27',0x01,1),(5,'WOScrc6yVP8ir','123',200,33,'2026-03-04 00:00:00',3,'32131','1','2026-03-04 01:27:21','1','2026-03-04 09:48:33',0x00,1),(6,'OSI202603310002','abce',200,1,NULL,2,'呃呃呃','1','2026-03-31 22:43:14','1','2026-03-31 22:51:51',0x00,1),(7,'OSI202603310003','ABCED',201,7,'2026-03-12 00:00:00',2,'','1','2026-03-31 23:16:09','1','2026-03-31 23:16:28',0x00,1),(8,'OSI202603310004','啊呃呃呃呃呃呃呃呃呃呃呃呃',201,7,NULL,0,'','1','2026-03-31 23:17:25','1','2026-04-17 01:35:47',0x00,1);
/*!40000 ALTER TABLE `mes_wm_outsource_issue` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_outsource_issue_detail`
--

DROP TABLE IF EXISTS `mes_wm_outsource_issue_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_outsource_issue_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `line_id` bigint NOT NULL COMMENT '行ID',
  `issue_id` bigint NOT NULL COMMENT '发料单ID',
  `material_stock_id` bigint DEFAULT NULL COMMENT '库存ID',
  `item_id` bigint NOT NULL COMMENT '物料ID',
  `quantity` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '数量',
  `batch_id` bigint DEFAULT NULL COMMENT '批次ID',
  `warehouse_id` bigint NOT NULL COMMENT '仓库ID',
  `location_id` bigint DEFAULT NULL COMMENT '库位ID',
  `area_id` bigint DEFAULT NULL COMMENT '库区ID',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_line_id` (`line_id`) USING BTREE,
  KEY `idx_issue_id` (`issue_id`) USING BTREE,
  KEY `idx_material_stock_id` (`material_stock_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE,
  KEY `idx_warehouse_id` (`warehouse_id`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 外协发料单明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_outsource_issue_detail`
--

LOCK TABLES `mes_wm_outsource_issue_detail` WRITE;
/*!40000 ALTER TABLE `mes_wm_outsource_issue_detail` DISABLE KEYS */;
INSERT INTO `mes_wm_outsource_issue_detail` VALUES (1,1,1,1,69,100.00,NULL,701,NULL,NULL,'测试发料明细','1','2026-03-02 22:16:49','1','2026-03-02 22:16:49',0x00,1),(2,3,5,NULL,69,19.00,NULL,702,713,724,'','1','2026-03-04 09:44:45','1','2026-03-04 09:47:56',0x00,1),(3,7,8,NULL,70,500.00,5,701,711,721,'','1','2026-03-31 23:26:19','1','2026-03-31 23:26:29',0x00,1),(4,7,8,17,70,100.00,0,702,711,721,'','1','2026-03-31 23:40:29','1','2026-03-31 23:40:29',0x00,1),(5,7,8,17,70,100.00,0,702,711,721,'','1','2026-03-31 23:41:15','1','2026-03-31 23:41:15',0x00,1),(6,7,8,17,70,100.00,0,702,711,721,'','1','2026-03-31 23:41:20','1','2026-03-31 23:41:20',0x00,1),(7,7,8,17,70,100.00,0,702,711,721,'','1','2026-03-31 23:42:58','1','2026-03-31 23:42:58',0x00,1),(8,7,8,17,70,100.00,0,702,711,721,'','1','2026-03-31 23:44:05','1','2026-03-31 23:44:05',0x00,1);
/*!40000 ALTER TABLE `mes_wm_outsource_issue_detail` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_outsource_issue_line`
--

DROP TABLE IF EXISTS `mes_wm_outsource_issue_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_outsource_issue_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '行ID',
  `issue_id` bigint NOT NULL COMMENT '发料单ID',
  `material_stock_id` bigint DEFAULT NULL COMMENT '库存ID',
  `item_id` bigint NOT NULL COMMENT '物料ID',
  `quantity` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '发料数量',
  `batch_id` bigint DEFAULT NULL COMMENT '批次ID',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_issue_id` (`issue_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 外协发料单行表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_outsource_issue_line`
--

LOCK TABLES `mes_wm_outsource_issue_line` WRITE;
/*!40000 ALTER TABLE `mes_wm_outsource_issue_line` DISABLE KEYS */;
INSERT INTO `mes_wm_outsource_issue_line` VALUES (1,1,NULL,69,100.00,NULL,'测试发料行','1','2026-03-02 22:16:25','1','2026-03-02 22:16:25',0x00,1),(2,3,NULL,69,123.00,NULL,'321321','1','2026-03-03 20:36:35','1','2026-03-03 20:36:35',0x00,1),(3,5,NULL,69,19.00,NULL,'','1','2026-03-04 01:36:34','1','2026-03-04 01:36:34',0x00,1),(4,6,NULL,69,1.00,NULL,'','1','2026-03-31 22:51:42','1','2026-03-31 22:51:42',0x00,1),(5,7,NULL,69,1.00,NULL,'','1','2026-03-31 23:16:26','1','2026-03-31 23:16:26',0x00,1),(6,8,NULL,70,1.00,NULL,'','1','2026-03-31 23:17:31','1','2026-03-31 23:17:31',0x00,1),(7,8,NULL,70,2.00,NULL,'','1','2026-03-31 23:17:35','1','2026-03-31 23:17:35',0x00,1);
/*!40000 ALTER TABLE `mes_wm_outsource_issue_line` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_outsource_receipt`
--

DROP TABLE IF EXISTS `mes_wm_outsource_receipt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_outsource_receipt` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '入库单编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '入库单名称',
  `work_order_id` bigint DEFAULT NULL COMMENT '外协工单编号',
  `vendor_id` bigint DEFAULT NULL COMMENT '供应商编号',
  `receipt_date` datetime DEFAULT NULL COMMENT '入库日期',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`code`) USING BTREE,
  KEY `idx_vendor_id` (`vendor_id`) USING BTREE,
  KEY `idx_workorder_id` (`work_order_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 外协入库单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_outsource_receipt`
--

LOCK TABLES `mes_wm_outsource_receipt` WRITE;
/*!40000 ALTER TABLE `mes_wm_outsource_receipt` DISABLE KEYS */;
INSERT INTO `mes_wm_outsource_receipt` VALUES (1,'WXRK202603020001','外协加工入库单-测试（已修改）',NULL,1,'1970-01-01 08:00:00',3,'测试修改外协入库单','1','2026-03-02 22:11:34','1','2026-03-02 22:19:58',0x00,1),(2,'WXRK202603020002','外协入库测试单（已修改）',NULL,1,'1970-01-01 08:00:00',0,'测试修改备注','1','2026-03-02 22:19:53','1','2026-03-02 22:20:11',0x00,1),(3,'ORKmNEby6zRM','112',NULL,200,'2026-03-04 00:00:00',0,'321321312','1','2026-03-03 20:29:34','1','2026-03-03 20:29:34',0x00,1),(4,'ORtzeQxWKcWI','呃呃呃',NULL,200,'2026-03-10 00:00:00',4,'32312','1','2026-03-03 21:08:32','1','2026-03-03 22:46:50',0x00,1),(5,'ORmX7bwErplO','EEE',1,200,'2026-03-18 00:00:00',2,'','1','2026-03-24 08:52:24','1','2026-04-17 01:39:09',0x00,1),(6,'ORGo10IXNGtn','AAA',1,200,'2026-02-25 00:00:00',1,'','1','2026-03-24 08:53:31','1','2026-03-24 08:58:36',0x00,1),(7,'WWO','WWO',1,200,'2026-03-04 00:00:00',1,'','1','2026-03-24 08:59:28','1','2026-03-24 08:59:41',0x00,1);
/*!40000 ALTER TABLE `mes_wm_outsource_receipt` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_outsource_receipt_detail`
--

DROP TABLE IF EXISTS `mes_wm_outsource_receipt_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_outsource_receipt_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `line_id` bigint NOT NULL COMMENT '入库单行编号',
  `receipt_id` bigint NOT NULL COMMENT '入库单编号',
  `item_id` bigint NOT NULL COMMENT '物料编号',
  `quantity` decimal(14,2) DEFAULT NULL COMMENT '上架数量',
  `batch_id` bigint DEFAULT NULL COMMENT '批次编号',
  `warehouse_id` bigint DEFAULT NULL COMMENT '仓库编号',
  `location_id` bigint DEFAULT NULL COMMENT '库区编号',
  `area_id` bigint DEFAULT NULL COMMENT '库位编号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_line_id` (`line_id`) USING BTREE,
  KEY `idx_receipt_id` (`receipt_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 外协入库明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_outsource_receipt_detail`
--

LOCK TABLES `mes_wm_outsource_receipt_detail` WRITE;
/*!40000 ALTER TABLE `mes_wm_outsource_receipt_detail` DISABLE KEYS */;
INSERT INTO `mes_wm_outsource_receipt_detail` VALUES (1,1,1,1,100.00,NULL,1,1,1,'测试明细数据','admin','2026-03-02 14:19:30','','2026-03-02 14:19:30',0x00,1),(2,2,4,69,123.00,NULL,702,713,724,'','1','2026-03-03 22:42:19','1','2026-03-03 22:42:19',0x00,1),(3,3,5,69,1.00,NULL,702,713,724,'','1','2026-03-24 08:53:06','1','2026-03-24 08:53:06',0x00,1);
/*!40000 ALTER TABLE `mes_wm_outsource_receipt_detail` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_outsource_receipt_line`
--

DROP TABLE IF EXISTS `mes_wm_outsource_receipt_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_outsource_receipt_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `receipt_id` bigint NOT NULL COMMENT '入库单编号',
  `item_id` bigint NOT NULL COMMENT '物料编号',
  `quantity` decimal(14,2) DEFAULT NULL COMMENT '入库数量',
  `batch_id` bigint DEFAULT NULL COMMENT '批次编号',
  `batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次编码',
  `production_date` datetime DEFAULT NULL COMMENT '生产日期',
  `expire_date` datetime DEFAULT NULL COMMENT '有效期',
  `lot_number` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '生产批号',
  `iqc_id` bigint DEFAULT NULL COMMENT '来料检验单编号',
  `iqc_check_flag` bit(1) DEFAULT b'0' COMMENT '是否需要来料检验',
  `quality_status` tinyint DEFAULT NULL COMMENT '质量状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_receipt_id` (`receipt_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 外协入库单行';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_outsource_receipt_line`
--

LOCK TABLES `mes_wm_outsource_receipt_line` WRITE;
/*!40000 ALTER TABLE `mes_wm_outsource_receipt_line` DISABLE KEYS */;
INSERT INTO `mes_wm_outsource_receipt_line` VALUES (1,1,1,100.00,NULL,NULL,'2026-03-02 00:00:00','2027-03-02 00:00:00','BATCH001',NULL,NULL,NULL,'测试行数据','admin','2026-03-02 14:19:30','','2026-03-02 14:19:30',0x00,1),(2,4,69,1.00,NULL,NULL,'2026-03-04 00:00:00','2026-03-12 00:00:00','3',NULL,NULL,1,'','1','2026-03-03 21:47:29','1','2026-03-03 21:47:48',0x00,1),(3,5,69,1.00,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1,'','1','2026-03-24 08:52:32','1','2026-03-24 08:52:32',0x00,1),(4,6,69,1.00,NULL,NULL,NULL,NULL,NULL,NULL,0x01,0,'','1','2026-03-24 08:53:37','1','2026-03-24 08:58:32',0x00,1),(5,7,69,2.00,NULL,NULL,NULL,NULL,NULL,NULL,0x01,0,'','1','2026-03-24 08:59:36','1','2026-03-24 08:59:36',0x00,1),(6,3,100,1.00,10,'PC202600003','2026-04-16 00:00:00','2026-04-17 00:00:00',NULL,NULL,0x00,1,'','1','2026-04-17 09:38:09','1','2026-04-17 09:38:09',0x00,1);
/*!40000 ALTER TABLE `mes_wm_outsource_receipt_line` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_package`
--

DROP TABLE IF EXISTS `mes_wm_package`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_package` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '装箱单 ID',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '装箱单编号',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父箱 ID（0 表示顶级箱）',
  `package_date` datetime DEFAULT NULL COMMENT '装箱日期',
  `sales_order_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '销售订单编号',
  `invoice_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发票编号',
  `client_id` bigint DEFAULT NULL COMMENT '客户 ID',
  `length` decimal(12,2) DEFAULT NULL COMMENT '箱长度',
  `width` decimal(12,2) DEFAULT NULL COMMENT '箱宽度',
  `height` decimal(12,2) DEFAULT NULL COMMENT '箱高度',
  `size_unit_id` bigint DEFAULT NULL COMMENT '尺寸单位 ID',
  `net_weight` decimal(12,2) DEFAULT NULL COMMENT '净重',
  `gross_weight` decimal(12,2) DEFAULT NULL COMMENT '毛重',
  `weight_unit_id` bigint DEFAULT NULL COMMENT '重量单位 ID',
  `inspector_user_id` bigint DEFAULT NULL COMMENT '检查员用户 ID',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户 ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_code` (`code`,`deleted`,`tenant_id`) USING BTREE,
  KEY `idx_parent_id` (`parent_id`) USING BTREE,
  KEY `idx_client_id` (`client_id`) USING BTREE,
  KEY `idx_package_date` (`package_date`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES - 装箱单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_package`
--

LOCK TABLES `mes_wm_package` WRITE;
/*!40000 ALTER TABLE `mes_wm_package` DISABLE KEYS */;
INSERT INTO `mes_wm_package` VALUES (1,'PKGl4uxKEk1ck',3,'2026-03-10 00:00:00',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,4,NULL,'1','2026-03-08 10:53:45','1','2026-03-08 11:58:39',0x00,1),(2,'PKGwDvyH9z962',0,'2026-03-04 00:00:00',NULL,NULL,207,NULL,NULL,NULL,200,NULL,NULL,200,NULL,0,NULL,'1','2026-03-08 10:54:00','1','2026-04-06 00:52:20',0x00,1),(3,'PKGSWNYn4Nbpm',2,'2026-03-18 00:00:00',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,4,NULL,'1','2026-03-08 11:57:57','1','2026-03-08 12:18:55',0x00,1),(4,'PKG202603080007',0,'2026-03-20 00:00:00',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,NULL,'1','2026-03-08 13:04:38','1','2026-03-08 13:04:38',0x00,1),(5,'PKG202603310001',4,'2026-03-12 00:00:00',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,4,NULL,'1','2026-03-31 20:06:23','1','2026-04-07 09:48:27',0x00,1),(6,'PKG202604060001',0,'2026-04-14 00:00:00',NULL,NULL,207,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,NULL,'1','2026-04-06 00:52:34','1','2026-04-06 00:52:40',0x00,1),(7,'PKG202604060002',0,'2026-04-14 00:00:00',NULL,NULL,207,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,NULL,'1','2026-04-06 01:07:52','1','2026-04-06 01:07:59',0x00,1);
/*!40000 ALTER TABLE `mes_wm_package` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_package_line`
--

DROP TABLE IF EXISTS `mes_wm_package_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_package_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细行 ID',
  `package_id` bigint NOT NULL COMMENT '装箱单 ID',
  `material_stock_id` bigint DEFAULT NULL COMMENT '库存记录 ID',
  `item_id` bigint NOT NULL COMMENT '产品物料 ID',
  `quantity` decimal(12,2) NOT NULL COMMENT '装箱数量',
  `work_order_id` bigint DEFAULT NULL COMMENT '生产工单 ID',
  `expire_date` date DEFAULT NULL COMMENT '有效期',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户 ID',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_package_id` (`package_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE,
  KEY `idx_work_order_id` (`work_order_id`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES - 装箱明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_package_line`
--

LOCK TABLES `mes_wm_package_line` WRITE;
/*!40000 ALTER TABLE `mes_wm_package_line` DISABLE KEYS */;
INSERT INTO `mes_wm_package_line` VALUES (1,2,NULL,94,123.00,1,'2026-03-12','3232','1','2026-03-08 12:21:54','1','2026-03-08 12:21:54',0x00,1);
/*!40000 ALTER TABLE `mes_wm_package_line` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_product_issue`
--

DROP TABLE IF EXISTS `mes_wm_product_issue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_product_issue` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '领料单编号',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '领料单名称',
  `workstation_id` bigint DEFAULT NULL COMMENT '工作站ID',
  `work_order_id` bigint DEFAULT NULL COMMENT '生产工单ID',
  `task_id` bigint DEFAULT NULL COMMENT '生产任务 ID',
  `issue_date` datetime DEFAULT NULL COMMENT '领料日期',
  `required_time` datetime DEFAULT NULL COMMENT '需求时间',
  `status` int NOT NULL DEFAULT '0' COMMENT '单据状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_code` (`code`) USING BTREE,
  KEY `idx_workstation_id` (`workstation_id`) USING BTREE,
  KEY `idx_workorder_id` (`work_order_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 领料出库单主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_product_issue`
--

LOCK TABLES `mes_wm_product_issue` WRITE;
/*!40000 ALTER TABLE `mes_wm_product_issue` DISABLE KEYS */;
INSERT INTO `mes_wm_product_issue` VALUES (1,'ISSUE-2026-0001','工单WO-001领料单',1,1,NULL,'2026-02-26 10:00:00','2026-02-26 14:00:00',0,'测试领料单1','1','2026-02-26 16:43:29','1','2026-02-27 23:13:45',0x01,1),(2,'ISSUE-2026-0002','工单WO-002领料单',2,2,NULL,'2026-02-26 11:00:00','2026-02-26 15:00:00',3,'测试领料单2','1','2026-02-26 16:43:29','1','2026-03-30 10:59:22',0x00,1),(3,'ISSUE-2026-0003','工单WO-003领料单',1,3,NULL,'2026-02-26 12:00:00','2026-02-26 16:00:00',3,'测试领料单3','1','2026-02-26 16:43:29','1','2026-02-26 16:43:29',0x00,1),(4,'ISSUE-2026-0004','工单WO-004领料单',3,4,NULL,'2026-02-26 13:00:00','2026-02-26 17:00:00',4,'测试领料单4','1','2026-02-26 16:43:29','1','2026-02-26 16:43:29',0x00,1),(5,'PIvQ6NaiEFGL','112',2,1,NULL,NULL,'1970-01-01 08:00:00',5,'','1','2026-02-27 20:07:49','1','2026-02-27 23:52:52',0x00,1),(6,'PIKp8EIm2d3A','132',NULL,1,NULL,NULL,'1970-01-01 08:00:00',3,'32321','1','2026-02-28 00:50:45','1','2026-02-28 00:51:44',0x00,1),(7,'PIF6HVZGjbf9','111',4,1,NULL,NULL,'1970-01-01 08:00:00',3,'','1','2026-02-28 01:14:36','1','2026-03-30 10:59:25',0x00,1),(8,'PI20260330000002','0303',1,1,NULL,'2026-03-30 11:11:50','1970-01-01 08:00:00',4,'','1','2026-03-30 11:02:15','1','2026-03-30 11:11:50',0x00,1),(9,'PI20260416000002','Test Picking',1,8,NULL,NULL,'2026-04-01 00:00:00',2,'','1','2026-04-16 01:51:57','1','2026-04-16 01:58:38',0x00,1),(10,'PI20260417000001','abc',6,8,NULL,NULL,'2026-04-16 00:00:00',0,'','1','2026-04-17 15:27:44','1','2026-09-03 15:46:57',0x00,1);
/*!40000 ALTER TABLE `mes_wm_product_issue` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_product_issue_detail`
--

DROP TABLE IF EXISTS `mes_wm_product_issue_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_product_issue_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `issue_id` bigint NOT NULL COMMENT '领料单ID',
  `line_id` bigint NOT NULL COMMENT '行ID',
  `material_stock_id` bigint DEFAULT NULL COMMENT '库存记录ID',
  `item_id` bigint NOT NULL COMMENT '物料ID',
  `quantity` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '领料数量',
  `batch_id` bigint DEFAULT NULL COMMENT '批次ID',
  `batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `warehouse_id` bigint DEFAULT NULL COMMENT '仓库ID',
  `location_id` bigint DEFAULT NULL COMMENT '库区ID',
  `area_id` bigint DEFAULT NULL COMMENT '库位ID',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_issue_id` (`issue_id`) USING BTREE,
  KEY `idx_line_id` (`line_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE,
  KEY `idx_warehouse_id` (`warehouse_id`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 领料出库明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_product_issue_detail`
--

LOCK TABLES `mes_wm_product_issue_detail` WRITE;
/*!40000 ALTER TABLE `mes_wm_product_issue_detail` DISABLE KEYS */;
INSERT INTO `mes_wm_product_issue_detail` VALUES (1,3,6,5001,106,60.00,1006,NULL,1,101,1001,'仓库A-库区1-库位1','1','2026-02-26 16:43:29','1','2026-09-03 15:46:37',0x00,1),(2,3,6,5002,106,60.00,1006,NULL,1,101,1002,'仓库A-库区1-库位2','1','2026-02-26 16:43:29','1','2026-02-26 16:43:29',0x00,1),(3,3,7,5003,107,90.00,1007,NULL,1,102,1003,'仓库A-库区2-库位3','1','2026-02-26 16:43:29','1','2026-02-26 16:43:29',0x00,1),(4,3,8,5004,108,60.00,1008,NULL,2,201,2001,'仓库B-库区1-库位1','1','2026-02-26 16:43:29','1','2026-02-26 16:43:29',0x00,1),(5,4,9,5005,109,100.00,1009,NULL,1,101,1001,'仓库A-库区1-库位1','1','2026-02-26 16:43:29','1','2026-02-26 16:43:29',0x00,1),(6,4,9,5006,109,80.00,1009,NULL,1,101,1002,'仓库A-库区1-库位2','1','2026-02-26 16:43:29','1','2026-02-26 16:43:29',0x00,1),(7,4,10,5007,110,75.00,1010,NULL,2,201,2001,'仓库B-库区1-库位1','1','2026-02-26 16:43:29','1','2026-02-26 16:43:29',0x00,1),(8,5,11,NULL,70,12.00,NULL,'123',702,713,724,'','1','2026-02-27 23:14:31','1','2026-02-27 23:45:04',0x00,1),(9,5,11,NULL,70,10.00,NULL,NULL,702,713,724,'','1','2026-02-27 23:15:03','1','2026-02-27 23:15:03',0x00,1),(10,7,13,NULL,94,1.00,NULL,NULL,701,712,723,'','1','2026-03-30 10:28:02','1','2026-03-30 10:28:02',0x00,1),(11,7,13,NULL,94,1.00,7,'BATCH_ITEM_94',701,712,723,'','1','2026-03-30 10:47:49','1','2026-03-30 10:47:49',0x00,1),(12,7,13,NULL,94,1.00,7,'BATCH_ITEM_94',701,712,723,'','1','2026-03-30 10:47:57','1','2026-03-30 10:47:57',0x00,1),(13,8,14,NULL,94,1.00,7,'BATCH_ITEM_94',701,712,723,'','1','2026-03-30 11:02:31','1','2026-03-30 11:02:31',0x00,1);
/*!40000 ALTER TABLE `mes_wm_product_issue_detail` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_product_issue_line`
--

DROP TABLE IF EXISTS `mes_wm_product_issue_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_product_issue_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `issue_id` bigint NOT NULL COMMENT '领料单ID',
  `item_id` bigint NOT NULL COMMENT '物料ID',
  `quantity` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '领料数量',
  `batch_id` bigint DEFAULT NULL COMMENT '批次ID',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_issue_id` (`issue_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 领料出库单行表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_product_issue_line`
--

LOCK TABLES `mes_wm_product_issue_line` WRITE;
/*!40000 ALTER TABLE `mes_wm_product_issue_line` DISABLE KEYS */;
INSERT INTO `mes_wm_product_issue_line` VALUES (1,1,101,100.00,NULL,'物料A','1','2026-02-26 16:43:29','1','2026-02-27 15:13:44',0x01,1),(2,1,102,50.00,NULL,'物料B','1','2026-02-26 16:43:29','1','2026-02-27 15:13:44',0x01,1),(3,1,103,200.00,NULL,'物料C','1','2026-02-26 16:43:29','1','2026-02-27 15:13:44',0x01,1),(4,2,104,150.00,NULL,'物料D','1','2026-02-26 16:43:29','1','2026-02-26 16:43:29',0x00,1),(5,2,105,80.00,NULL,'物料E','1','2026-02-26 16:43:29','1','2026-02-26 16:43:29',0x00,1),(6,3,106,120.00,NULL,'物料F','1','2026-02-26 16:43:29','1','2026-02-26 16:43:29',0x00,1),(7,3,107,90.00,NULL,'物料G','1','2026-02-26 16:43:29','1','2026-02-26 16:43:29',0x00,1),(8,3,108,60.00,NULL,'物料H','1','2026-02-26 16:43:29','1','2026-02-26 16:43:29',0x00,1),(9,4,109,180.00,NULL,'物料I','1','2026-02-26 16:43:29','1','2026-02-26 16:43:29',0x00,1),(10,4,110,75.00,NULL,'物料J','1','2026-02-26 16:43:29','1','2026-02-26 16:43:29',0x00,1),(11,5,70,10.00,NULL,'222','1','2026-02-27 23:05:18','1','2026-02-27 23:05:18',0x00,1),(12,6,70,3.00,NULL,'','1','2026-02-28 00:50:50','1','2026-02-28 00:50:50',0x00,1),(13,7,94,1.00,NULL,'','1','2026-03-30 10:00:38','1','2026-03-30 10:00:38',0x00,1),(14,8,94,1.00,NULL,'','1','2026-03-30 11:02:23','1','2026-03-30 11:02:23',0x00,1),(15,9,103,10.00,NULL,'','1','2026-04-16 01:54:31','1','2026-04-16 01:54:31',0x00,1),(16,10,103,123.00,NULL,'','1','2026-04-17 15:27:51','1','2026-04-17 15:27:51',0x00,1);
/*!40000 ALTER TABLE `mes_wm_product_issue_line` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_product_produce`
--

DROP TABLE IF EXISTS `mes_wm_product_produce`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_product_produce` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `work_order_id` bigint DEFAULT NULL COMMENT '生产工单 ID',
  `feedback_id` bigint DEFAULT NULL COMMENT '报工记录 ID',
  `task_id` bigint DEFAULT NULL COMMENT '生产任务 ID',
  `workstation_id` bigint DEFAULT NULL COMMENT '工作站 ID',
  `process_id` bigint DEFAULT NULL COMMENT '工序 ID',
  `produce_date` datetime DEFAULT NULL COMMENT '生产日期',
  `status` int NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_work_order_id` (`work_order_id`) USING BTREE,
  KEY `idx_workstation_id` (`workstation_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 生产入库单主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_product_produce`
--

LOCK TABLES `mes_wm_product_produce` WRITE;
/*!40000 ALTER TABLE `mes_wm_product_produce` DISABLE KEYS */;
INSERT INTO `mes_wm_product_produce` VALUES (17,1,5,4,3,3,'2026-03-21 15:07:46',4,'','1','2026-03-21 15:07:46','1','2026-03-21 15:07:46',0x00,1),(18,1,6,4,3,3,'2026-03-21 15:09:48',4,'','1','2026-03-21 15:09:48','1','2026-03-21 15:09:48',0x00,1),(19,1,8,4,3,3,'2026-03-24 23:17:24',4,'','1','2026-03-24 23:17:24','1','2026-03-24 23:17:24',0x00,1),(20,17,12,8,8,4,'2026-05-26 00:33:47',0,'验收造数：待检验产出单','1','2026-05-26 00:33:47','1','2026-05-26 00:33:47',0x00,1),(21,17,13,7,7,1,'2026-05-26 00:33:47',4,'验收造数：已完成产出单','1','2026-05-26 00:33:47','1','2026-05-26 00:33:47',0x00,1);
/*!40000 ALTER TABLE `mes_wm_product_produce` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_product_produce_detail`
--

DROP TABLE IF EXISTS `mes_wm_product_produce_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_product_produce_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `produce_id` bigint NOT NULL COMMENT '入库单 ID',
  `line_id` bigint NOT NULL COMMENT '行 ID',
  `item_id` bigint NOT NULL COMMENT '物料 ID',
  `quantity` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '入库数量',
  `batch_id` bigint DEFAULT NULL COMMENT '批次 ID',
  `batch_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `warehouse_id` bigint DEFAULT NULL COMMENT '仓库 ID',
  `location_id` bigint DEFAULT NULL COMMENT '库区 ID',
  `area_id` bigint DEFAULT NULL COMMENT '库位 ID',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_produce_id` (`produce_id`) USING BTREE,
  KEY `idx_line_id` (`line_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE,
  KEY `idx_warehouse_id` (`warehouse_id`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 生产入库明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_product_produce_detail`
--

LOCK TABLES `mes_wm_product_produce_detail` WRITE;
/*!40000 ALTER TABLE `mes_wm_product_produce_detail` DISABLE KEYS */;
INSERT INTO `mes_wm_product_produce_detail` VALUES (1,17,1,75,1.00,3,'PC202600001',NULL,NULL,NULL,'','1','2026-03-21 15:07:46','1','2026-03-21 15:07:46',0x00,1),(2,18,2,75,1.00,3,'PC202600001',NULL,NULL,NULL,'','1','2026-03-21 15:09:48','1','2026-03-21 15:09:48',0x00,1),(3,19,3,75,1.00,4,'PC202600002',703,714,725,'','1','2026-03-24 23:17:24','1','2026-03-24 23:17:24',0x00,1),(4,19,4,75,1.00,4,'PC202600002',703,714,725,'','1','2026-03-24 23:17:24','1','2026-03-24 23:17:24',0x00,1);
/*!40000 ALTER TABLE `mes_wm_product_produce_detail` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_product_produce_line`
--

DROP TABLE IF EXISTS `mes_wm_product_produce_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_product_produce_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `produce_id` bigint NOT NULL COMMENT '入库单 ID',
  `feedback_id` bigint DEFAULT NULL COMMENT '报工记录 ID',
  `item_id` bigint NOT NULL COMMENT '物料 ID',
  `quantity` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '入库数量',
  `batch_id` bigint DEFAULT NULL COMMENT '批次 ID',
  `batch_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `expire_date` datetime DEFAULT NULL COMMENT '过期日期',
  `lot_number` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '生产批号',
  `quality_status` int DEFAULT NULL COMMENT '质量状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_produce_id` (`produce_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 生产入库单行表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_product_produce_line`
--

LOCK TABLES `mes_wm_product_produce_line` WRITE;
/*!40000 ALTER TABLE `mes_wm_product_produce_line` DISABLE KEYS */;
INSERT INTO `mes_wm_product_produce_line` VALUES (1,17,5,75,1.00,3,'PC202600001',NULL,NULL,1,'','1','2026-03-21 15:07:46','1','2026-03-21 15:07:46',0x00,1),(2,18,6,75,1.00,3,'PC202600001',NULL,NULL,2,'','1','2026-03-21 15:09:48','1','2026-03-21 15:09:48',0x00,1),(3,19,8,75,1.00,4,'PC202600002',NULL,NULL,2,'','1','2026-03-24 23:17:24','1','2026-03-24 23:17:24',0x00,1),(4,19,8,75,1.00,4,'PC202600002',NULL,NULL,1,'','1','2026-03-24 23:17:24','1','2026-03-24 23:17:24',0x00,1),(5,20,12,75,15.00,NULL,'PC-ACCEPT-UNCHECK','2026-11-22 00:00:00','LOT-FB-UNCHECK',0,'验收造数：待检验产出行','1','2026-05-26 00:33:47','1','2026-05-26 00:33:47',0x00,1),(6,21,13,75,18.00,NULL,'PC-ACCEPT-PASS','2026-11-22 00:00:00','LOT-FB-FINISHED',1,'验收造数：合格产出行','1','2026-05-26 00:33:47','1','2026-05-26 00:33:47',0x00,1),(7,21,13,75,2.00,NULL,'PC-ACCEPT-FAIL','2026-11-22 00:00:00','LOT-FB-FINISHED',2,'验收造数：不合格产出行','1','2026-05-26 00:33:47','1','2026-05-26 00:33:47',0x00,1);
/*!40000 ALTER TABLE `mes_wm_product_produce_line` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_product_receipt`
--

DROP TABLE IF EXISTS `mes_wm_product_receipt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_product_receipt` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '入库单ID',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '入库单编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '入库单名称',
  `work_order_id` bigint DEFAULT NULL COMMENT '生产工单ID',
  `item_id` bigint DEFAULT NULL COMMENT '产品物料ID',
  `receipt_date` datetime DEFAULT NULL COMMENT '入库日期',
  `status` int DEFAULT '0' COMMENT '状态（0草稿 2待上架 3待执行入库 4已完成 5已取消）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_code` (`code`) USING BTREE,
  KEY `idx_work_order_id` (`work_order_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品入库单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_product_receipt`
--

LOCK TABLES `mes_wm_product_receipt` WRITE;
/*!40000 ALTER TABLE `mes_wm_product_receipt` DISABLE KEYS */;
INSERT INTO `mes_wm_product_receipt` VALUES (1,'PR202603010001','产品入库单-工单001',1001,2001,'2026-03-01 10:00:00',0,'测试入库单1','1','2026-03-01 04:15:25','1','2026-03-01 04:15:25',0x00,1),(2,'PR202603010002','产品入库单-工单002',1002,2002,'2026-03-01 11:00:00',2,'测试入库单2','1','2026-03-01 04:15:25','1','2026-03-01 04:15:25',0x00,1),(3,'PR202603010003','产品入库单-工单003',1003,2003,'2026-03-01 12:00:00',4,'测试入库单3','1','2026-03-01 04:15:25','1','2026-03-01 04:15:25',0x00,1),(4,'PRWZbODiG5NB','3231',6,NULL,'2026-03-10 00:00:00',4,'','1','2026-03-01 12:33:14','1','2026-03-01 13:12:40',0x00,1),(5,'PRyhCGt9TzjO','呃呃呃',1,75,'2026-03-11 00:00:00',3,'','1','2026-03-01 13:13:12','1','2026-03-01 13:13:29',0x00,1),(6,'PRF0d2CB7sar','aaa',2,73,'2026-03-11 00:00:00',4,'','1','2026-03-01 13:13:53','1','2026-03-01 13:15:00',0x00,1),(7,'PRoDYEoZY1BK','eee',1,75,'2026-03-11 00:00:00',2,'','1','2026-03-01 13:15:59','1','2026-03-01 13:16:08',0x00,1),(8,'PR20260330000001','AAAEEE',1,75,'2026-02-24 00:00:00',3,'','1','2026-03-30 15:17:24','1','2026-03-30 15:27:44',0x00,1);
/*!40000 ALTER TABLE `mes_wm_product_receipt` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_product_receipt_detail`
--

DROP TABLE IF EXISTS `mes_wm_product_receipt_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_product_receipt_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `line_id` bigint NOT NULL COMMENT '行ID',
  `receipt_id` bigint NOT NULL COMMENT '入库单ID',
  `item_id` bigint NOT NULL COMMENT '物料ID',
  `quantity` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '上架数量',
  `batch_id` bigint DEFAULT NULL COMMENT '批次ID',
  `warehouse_id` bigint DEFAULT NULL COMMENT '仓库ID',
  `location_id` bigint DEFAULT NULL COMMENT '库区ID',
  `area_id` bigint DEFAULT NULL COMMENT '库位ID',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_line_id` (`line_id`) USING BTREE,
  KEY `idx_recpt_id` (`receipt_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE,
  KEY `idx_batch_id` (`batch_id`) USING BTREE,
  KEY `idx_warehouse_id` (`warehouse_id`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品入库单明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_product_receipt_detail`
--

LOCK TABLES `mes_wm_product_receipt_detail` WRITE;
/*!40000 ALTER TABLE `mes_wm_product_receipt_detail` DISABLE KEYS */;
INSERT INTO `mes_wm_product_receipt_detail` VALUES (1,3,2,2002,100.00,3003,4001,5001,6001,'明细1','1','2026-03-01 04:15:25','1','2026-03-01 04:15:25',0x00,1),(2,3,2,2002,100.00,3003,4001,5002,6002,'明细2','1','2026-03-01 04:15:25','1','2026-03-01 04:15:25',0x00,1),(3,4,2,2003,150.00,3004,4002,5003,6003,'明细3','1','2026-03-01 04:15:25','1','2026-03-01 04:15:25',0x00,1),(4,5,3,2003,300.00,3005,4003,5004,6004,'明细4','1','2026-03-01 04:15:25','1','2026-03-01 04:15:25',0x00,1),(5,6,4,69,123.00,NULL,702,713,724,'','1','2026-03-01 12:46:01','1','2026-03-01 12:46:01',0x00,1),(6,6,4,69,22.00,NULL,702,713,724,'','1','2026-03-01 12:46:26','1','2026-03-01 12:46:26',0x00,1),(7,6,4,69,123.00,NULL,702,713,724,'','1','2026-03-01 13:00:36','1','2026-03-01 13:00:36',0x00,1),(8,10,8,69,1.00,NULL,703,714,725,'','1','2026-03-30 15:27:41','1','2026-03-30 15:27:41',0x00,1);
/*!40000 ALTER TABLE `mes_wm_product_receipt_detail` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_product_receipt_line`
--

DROP TABLE IF EXISTS `mes_wm_product_receipt_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_product_receipt_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '行ID',
  `receipt_id` bigint NOT NULL COMMENT '入库单ID',
  `item_id` bigint NOT NULL COMMENT '物料ID',
  `material_stock_id` bigint DEFAULT NULL COMMENT '库存物资记录编号',
  `quantity` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '入库数量',
  `batch_id` bigint DEFAULT NULL COMMENT '批次ID',
  `batch_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_recpt_id` (`receipt_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE,
  KEY `idx_batch_id` (`batch_id`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品入库单行';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_product_receipt_line`
--

LOCK TABLES `mes_wm_product_receipt_line` WRITE;
/*!40000 ALTER TABLE `mes_wm_product_receipt_line` DISABLE KEYS */;
INSERT INTO `mes_wm_product_receipt_line` VALUES (1,1,2001,NULL,100.00,3001,'BATCH20260301001','行1','1','2026-03-01 04:15:25','1','2026-03-30 15:48:28',0x01,1),(2,1,94,NULL,1.00,7,'BATCH_ITEM_94','行2','1','2026-03-01 04:15:25','1','2026-03-30 15:48:29',0x01,1),(3,2,2002,NULL,200.00,3003,'BATCH20260301003','行3','1','2026-03-01 04:15:25','1','2026-03-01 04:15:25',0x00,1),(4,2,2003,NULL,150.00,3004,'BATCH20260301004','行4','1','2026-03-01 04:15:25','1','2026-03-01 04:15:25',0x00,1),(5,3,2003,NULL,300.00,3005,'BATCH20260301005','行5','1','2026-03-01 04:15:25','1','2026-03-01 04:15:25',0x00,1),(6,4,69,NULL,22.00,NULL,NULL,'','1','2026-03-01 12:42:04','1','2026-03-01 12:42:04',0x00,1),(7,5,69,NULL,123.00,NULL,NULL,'','1','2026-03-01 13:13:18','1','2026-03-01 13:13:18',0x00,1),(8,6,69,NULL,123.00,NULL,NULL,'','1','2026-03-01 13:13:59','1','2026-03-01 13:13:59',0x00,1),(9,7,69,NULL,123.00,NULL,NULL,'','1','2026-03-01 13:16:03','1','2026-03-01 13:16:03',0x00,1),(10,8,69,NULL,1.00,NULL,'ABCE','','1','2026-03-30 15:27:16','1','2026-03-30 15:27:16',0x00,1),(11,1,94,9,123.00,7,'BATCH_ITEM_94','123','1','2026-03-30 15:48:40','1','2026-03-30 15:48:40',0x00,1),(12,1,94,9,123.00,7,'BATCH_ITEM_94','啊啊啊啊','1','2026-03-30 15:50:18','1','2026-03-30 15:50:18',0x00,1);
/*!40000 ALTER TABLE `mes_wm_product_receipt_line` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_product_sales`
--

DROP TABLE IF EXISTS `mes_wm_product_sales`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_product_sales` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '出库单号',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '出库单名称',
  `client_id` bigint NOT NULL COMMENT '客户ID',
  `sales_order_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '销售订单号',
  `sales_date` datetime NOT NULL COMMENT '出库日期',
  `notice_id` bigint DEFAULT NULL COMMENT '发货通知单ID',
  `contact_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '收货人',
  `contact_telephone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系方式',
  `contact_address` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '收货地址',
  `carrier` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '承运商',
  `shipping_number` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '运输单号',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_code` (`code`,`deleted`,`tenant_id`) USING BTREE COMMENT '出库单号唯一索引',
  KEY `idx_client_id` (`client_id`) USING BTREE,
  KEY `idx_notice_id` (`notice_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_sales_date` (`sales_date`) USING BTREE,
  KEY `idx_create_time` (`create_time`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=100005 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 销售出库单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_product_sales`
--

LOCK TABLES `mes_wm_product_sales` WRITE;
/*!40000 ALTER TABLE `mes_wm_product_sales` DISABLE KEYS */;
INSERT INTO `mes_wm_product_sales` VALUES (1,'PS202603020001','更新后的销售出库单',200,'SO202603020001','1970-01-01 08:00:00',1,'李四','13900139000',NULL,'中通快递','ZTO9876543210',0,'测试更新销售出库单','1','2026-03-02 17:12:07','1','2026-03-02 17:12:48',0x01,1),(2,'PSe6mfNzWE7n','3313213',207,NULL,'2026-03-11 00:00:00',2,'xx','ee',NULL,NULL,NULL,5,NULL,'1','2026-03-02 17:15:00','1','2026-03-02 18:46:19',0x00,1),(3,'PSatKKrWp6sH','呃呃呃',207,NULL,'2026-03-11 00:00:00',NULL,NULL,NULL,NULL,'555','3333',3,NULL,'1','2026-03-02 18:46:50','1','2026-04-17 01:54:29',0x00,1),(99999,'SALES-TEST-OQC','OQC出库单测试',200,NULL,'2026-03-27 10:02:54',NULL,NULL,NULL,NULL,NULL,NULL,3,NULL,'','2026-03-27 10:02:54','','2026-03-27 10:02:54',0x00,1),(100000,'PS20260330000003','AAAA',200,'SO202603020001','2026-03-31 00:00:00',1,'张三','13800138000','北京市朝阳区测试地址',NULL,NULL,0,NULL,'1','2026-03-30 20:58:50','1','2026-03-30 20:58:50',0x00,1),(100001,'PS20260330000004','ABCD',200,NULL,'2026-03-11 00:00:00',NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,'1','2026-03-30 21:05:31','1','2026-03-30 21:08:14',0x00,1),(100002,'PS20260330000005','ABC',207,NULL,'2026-03-17 00:00:00',NULL,NULL,NULL,NULL,NULL,NULL,0,NULL,'1','2026-03-30 21:09:02','1','2026-03-30 21:09:02',0x00,1),(100003,'A0','A0',207,NULL,'2026-03-18 00:00:00',NULL,NULL,NULL,NULL,'AAABBB','EEE',10,NULL,'1','2026-03-30 21:10:54','1','2026-04-17 01:54:54',0x00,1),(100004,'PS-20260412-001','ä¸´æ—¶é”€å”®å‡ºåº“å•çš„æ¨¡æ‹Ÿæ•°æ®',1,'SO-123456','2026-04-11 18:28:00',NULL,NULL,NULL,NULL,NULL,NULL,0,NULL,'','2026-04-11 18:28:00','1','2026-04-17 09:50:07',0x01,1);
/*!40000 ALTER TABLE `mes_wm_product_sales` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_product_sales_detail`
--

DROP TABLE IF EXISTS `mes_wm_product_sales_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_product_sales_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `line_id` bigint NOT NULL COMMENT '出库单行ID',
  `sales_id` bigint NOT NULL COMMENT '出库单ID',
  `item_id` bigint NOT NULL COMMENT '物料ID',
  `quantity` decimal(20,6) NOT NULL COMMENT '数量',
  `material_stock_id` bigint DEFAULT NULL COMMENT '库存记录ID',
  `batch_id` bigint DEFAULT NULL COMMENT '批次ID',
  `batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `warehouse_id` bigint NOT NULL COMMENT '仓库ID',
  `location_id` bigint DEFAULT NULL COMMENT '库区ID',
  `area_id` bigint DEFAULT NULL COMMENT '库位ID',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_line_id` (`line_id`) USING BTREE,
  KEY `idx_sales_id` (`sales_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE,
  KEY `idx_batch_id` (`batch_id`) USING BTREE,
  KEY `idx_warehouse_id` (`warehouse_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 销售出库明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_product_sales_detail`
--

LOCK TABLES `mes_wm_product_sales_detail` WRITE;
/*!40000 ALTER TABLE `mes_wm_product_sales_detail` DISABLE KEYS */;
INSERT INTO `mes_wm_product_sales_detail` VALUES (1,1,2,69,1.000000,NULL,NULL,NULL,702,713,724,NULL,'1','2026-03-02 17:53:47','1','2026-03-02 17:53:55',0x00,1),(2,2,3,69,123.000000,NULL,NULL,NULL,702,713,724,NULL,'1','2026-03-02 18:47:49','1','2026-09-03 15:44:58',0x00,1),(3,100002,100003,69,736.000000,4,1,'TEST',702,713,724,NULL,'1','2026-03-30 21:54:11','1','2026-03-30 21:54:11',0x00,1);
/*!40000 ALTER TABLE `mes_wm_product_sales_detail` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_product_sales_line`
--

DROP TABLE IF EXISTS `mes_wm_product_sales_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_product_sales_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `sales_id` bigint NOT NULL COMMENT '出库单ID',
  `notice_line_id` bigint DEFAULT NULL COMMENT '发货通知单行ID',
  `item_id` bigint NOT NULL COMMENT '物料ID',
  `quantity` decimal(20,6) NOT NULL COMMENT '出库数量',
  `batch_id` bigint DEFAULT NULL COMMENT '批次ID',
  `batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `material_stock_id` bigint DEFAULT NULL COMMENT '库存记录ID',
  `oqc_check_flag` tinyint(1) DEFAULT NULL COMMENT '是否出厂检验',
  `oqc_id` bigint DEFAULT NULL COMMENT '出厂检验单ID',
  `quality_status` int DEFAULT NULL COMMENT '质检状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_sales_id` (`sales_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE,
  KEY `idx_batch_id` (`batch_id`) USING BTREE,
  KEY `idx_material_stock_id` (`material_stock_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=100003 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 销售出库单行';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_product_sales_line`
--

LOCK TABLES `mes_wm_product_sales_line` WRITE;
/*!40000 ALTER TABLE `mes_wm_product_sales_line` DISABLE KEYS */;
INSERT INTO `mes_wm_product_sales_line` VALUES (1,2,NULL,69,1.000000,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'1','2026-03-02 17:18:22','1','2026-03-02 17:18:22',0x00,1),(2,3,NULL,69,123.000000,123,NULL,NULL,NULL,NULL,NULL,NULL,'1','2026-03-02 18:47:30','1','2026-03-02 18:47:30',0x00,1),(99999,99999,NULL,69,100.500000,NULL,NULL,NULL,1,NULL,NULL,NULL,'','2026-03-27 10:02:54','','2026-03-27 10:02:54',0x00,1),(100000,100001,NULL,69,10.000000,1,NULL,NULL,NULL,NULL,NULL,NULL,'1','2026-03-30 21:08:09','1','2026-03-30 21:08:09',0x00,1),(100001,100002,NULL,69,1.000000,1,'TTTT',NULL,1,NULL,NULL,NULL,'1','2026-03-30 21:09:06','1','2026-03-30 22:09:39',0x00,1),(100002,100003,NULL,69,1.000000,1,NULL,NULL,NULL,NULL,NULL,NULL,'1','2026-03-30 21:11:22','1','2026-03-30 21:11:22',0x00,1);
/*!40000 ALTER TABLE `mes_wm_product_sales_line` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_return_issue`
--

DROP TABLE IF EXISTS `mes_wm_return_issue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_return_issue` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '退料单编号',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '退料单名称',
  `work_order_id` bigint DEFAULT NULL COMMENT '生产工单 ID',
  `workstation_id` bigint DEFAULT NULL COMMENT '工作站 ID',
  `type` int DEFAULT NULL COMMENT '退料类型',
  `return_date` datetime DEFAULT NULL COMMENT '退料日期',
  `status` int NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_code` (`code`) USING BTREE,
  KEY `idx_work_order_id` (`work_order_id`) USING BTREE,
  KEY `idx_workstation_id` (`workstation_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 生产退料单主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_return_issue`
--

LOCK TABLES `mes_wm_return_issue` WRITE;
/*!40000 ALTER TABLE `mes_wm_return_issue` DISABLE KEYS */;
INSERT INTO `mes_wm_return_issue` VALUES (1,'RI-20260228-001','生产退料单-草稿',1,1,1,'2026-02-28 10:00:00',0,'测试退料单-草稿','1','2026-02-28 14:12:22','1','2026-02-28 14:12:22',0x00,1),(2,'RI-20260228-002','生产退料单-已完成',2,2,2,'2026-02-28 11:00:00',3,'测试退料单-已完成','1','2026-02-28 14:12:22','1','2026-04-17 07:37:35',0x00,1),(3,'RI-20260228-003','生产退料单-已取消',1,1,3,'2026-02-28 12:00:00',5,'测试退料单-已取消','1','2026-02-28 14:12:22','1','2026-02-28 14:12:22',0x00,1),(4,'RIpV4sw33lVy','XXX',1,NULL,1,'1970-01-01 08:00:00',1,'','1','2026-02-28 22:46:01','1','2026-02-28 23:13:38',0x00,1),(5,'RIKygemCMm69','111',1,1,1,'1970-01-01 08:00:00',5,'','1','2026-03-01 00:20:56','1','2026-03-01 00:22:38',0x00,1),(6,'RIOSAqiWoaXI','12323',1,1,2,NULL,4,'','1','2026-03-01 00:52:07','1','2026-03-01 00:54:50',0x00,1),(7,'RITmmKTJb4p0','aaa',1,NULL,1,NULL,1,'','1','2026-03-26 13:02:39','1','2026-03-26 13:02:51',0x00,1),(8,'RI20260330000001','EEE',1,2,1,'1970-01-01 08:00:00',5,'','1','2026-03-30 11:52:28','1','2026-03-30 12:03:02',0x00,1),(9,'RI20260330000002','AAA',1,1,1,'1970-01-01 08:00:00',2,'','1','2026-03-30 12:07:40','1','2026-04-14 03:24:07',0x00,1);
/*!40000 ALTER TABLE `mes_wm_return_issue` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_return_issue_detail`
--

DROP TABLE IF EXISTS `mes_wm_return_issue_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_return_issue_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `issue_id` bigint NOT NULL COMMENT '退料单 ID',
  `line_id` bigint NOT NULL COMMENT '行 ID',
  `material_stock_id` bigint DEFAULT NULL COMMENT '库存记录 ID',
  `item_id` bigint NOT NULL COMMENT '物料 ID',
  `quantity` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '退料数量',
  `batch_id` bigint DEFAULT NULL COMMENT '批次 ID',
  `batch_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `warehouse_id` bigint DEFAULT NULL COMMENT '仓库 ID',
  `location_id` bigint DEFAULT NULL COMMENT '库区 ID',
  `area_id` bigint DEFAULT NULL COMMENT '库位 ID',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_issue_id` (`issue_id`) USING BTREE,
  KEY `idx_line_id` (`line_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE,
  KEY `idx_warehouse_id` (`warehouse_id`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 生产退料明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_return_issue_detail`
--

LOCK TABLES `mes_wm_return_issue_detail` WRITE;
/*!40000 ALTER TABLE `mes_wm_return_issue_detail` DISABLE KEYS */;
INSERT INTO `mes_wm_return_issue_detail` VALUES (1,1,1,NULL,101,60.00,NULL,'BATCH-001',1,101,1001,'仓库 A-库区 1-库位 1','1','2026-02-28 14:12:22','1','2026-02-28 14:12:22',0x00,1),(2,1,1,NULL,101,40.00,NULL,'BATCH-001',1,101,1002,'仓库 A-库区 1-库位 2','1','2026-02-28 14:12:22','1','2026-02-28 14:12:22',0x00,1),(3,2,3,NULL,103,200.00,NULL,'BATCH-002',1,101,1001,'仓库 A-库区 1-库位 1','1','2026-02-28 14:12:22','1','2026-02-28 14:12:22',0x00,1),(4,2,4,NULL,104,30.00,NULL,'BATCH-002',2,201,2001,'仓库 B-库区 1-库位 1','1','2026-02-28 14:12:22','1','2026-02-28 14:12:22',0x00,1),(5,5,7,NULL,69,1.00,NULL,'32321',702,713,724,'','1','2026-03-01 00:22:04','1','2026-03-01 00:22:04',0x00,1),(6,6,8,NULL,70,1.00,NULL,NULL,702,713,724,'','1','2026-03-01 00:52:45','1','2026-03-01 00:52:45',0x00,1),(7,9,11,14,94,1.00,7,'BATCH_ITEM_94',703,714,725,'','1','2026-04-14 03:28:34','1','2026-04-14 03:28:34',0x00,1);
/*!40000 ALTER TABLE `mes_wm_return_issue_detail` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_return_issue_line`
--

DROP TABLE IF EXISTS `mes_wm_return_issue_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_return_issue_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `issue_id` bigint NOT NULL COMMENT '退料单 ID',
  `material_stock_id` bigint DEFAULT NULL COMMENT '库存记录 ID',
  `item_id` bigint NOT NULL COMMENT '物料 ID',
  `quantity` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '退料数量',
  `batch_id` bigint DEFAULT NULL COMMENT '批次 ID',
  `batch_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次编码',
  `rqc_id` bigint DEFAULT NULL COMMENT '退货检验单 ID',
  `rqc_check_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否需要质检',
  `quality_status` int DEFAULT NULL COMMENT '质量状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_issue_id` (`issue_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 生产退料单行表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_return_issue_line`
--

LOCK TABLES `mes_wm_return_issue_line` WRITE;
/*!40000 ALTER TABLE `mes_wm_return_issue_line` DISABLE KEYS */;
INSERT INTO `mes_wm_return_issue_line` VALUES (1,1,NULL,101,100.00,NULL,NULL,NULL,0x00,1,'余料退回-合格','1','2026-02-28 14:12:22','1','2026-02-28 23:01:33',0x01,1),(2,1,NULL,102,50.00,NULL,NULL,NULL,0x01,0,'需要质检','1','2026-02-28 14:12:22','1','2026-02-28 23:01:31',0x01,1),(3,2,NULL,103,200.00,NULL,NULL,NULL,0x00,2,'不良退料','1','2026-02-28 14:12:22','1','2026-02-28 14:12:22',0x00,1),(4,2,NULL,104,30.00,NULL,NULL,NULL,0x00,1,'余料退回','1','2026-02-28 14:12:22','1','2026-02-28 14:12:22',0x00,1),(5,3,NULL,105,80.00,NULL,NULL,NULL,0x00,1,'其他退料','1','2026-02-28 14:12:22','1','2026-02-28 14:12:22',0x00,1),(6,4,NULL,70,5.00,NULL,NULL,NULL,0x01,0,'','1','2026-02-28 22:51:18','1','2026-02-28 22:56:10',0x00,1),(7,5,NULL,69,323.00,NULL,NULL,NULL,0x00,1,'1133','1','2026-03-01 00:21:04','1','2026-03-01 00:21:04',0x00,1),(8,6,NULL,70,1.00,NULL,NULL,NULL,0x00,2,'2','1','2026-03-01 00:52:12','1','2026-03-01 00:52:12',0x00,1),(9,7,NULL,69,10.00,NULL,NULL,NULL,0x01,0,'','1','2026-03-26 13:02:45','1','2026-03-26 13:02:45',0x00,1),(10,8,9,94,123.00,7,'BATCH_ITEM_94',NULL,0x01,0,'','1','2026-03-30 11:53:29','1','2026-03-30 11:59:27',0x00,1),(11,9,9,94,123.00,7,'BATCH_ITEM_94',NULL,0x00,1,'','1','2026-03-30 12:07:50','1','2026-03-30 12:07:50',0x00,1),(12,1,14,94,1.00,7,'BATCH_ITEM_94',NULL,0x00,1,'','1','2026-04-17 15:34:51','1','2026-04-17 15:34:51',0x00,1);
/*!40000 ALTER TABLE `mes_wm_return_issue_line` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_return_sales`
--

DROP TABLE IF EXISTS `mes_wm_return_sales`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_return_sales` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '退货单ID',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '退货单编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '退货单名称',
  `sales_order_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '销售订单编号',
  `client_id` bigint DEFAULT NULL COMMENT '客户ID',
  `return_date` datetime DEFAULT NULL COMMENT '退货日期',
  `return_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '退货原因',
  `status` int DEFAULT '0' COMMENT '状态（0草稿 1待执行 2待上架 3已完成 4已取消）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`code`) USING BTREE,
  KEY `idx_client_id` (`client_id`) USING BTREE,
  KEY `idx_so_code` (`sales_order_code`) USING BTREE,
  KEY `idx_return_date` (`return_date`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='销售退货单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_return_sales`
--

LOCK TABLES `mes_wm_return_sales` WRITE;
/*!40000 ALTER TABLE `mes_wm_return_sales` DISABLE KEYS */;
INSERT INTO `mes_wm_return_sales` VALUES (1,'RS202603010001','销售退货单-客户001','SO202603010001',1001,'2026-03-01 10:00:00','质量问题',0,'测试退货单1','1','2026-03-01 10:30:34','1','2026-03-01 10:30:34',0x00,1),(2,'RS202603010002','销售退货单-客户002','SO202603010002',1002,'2026-03-01 11:00:00','规格不符',2,'测试退货单2','1','2026-03-01 10:30:34','1','2026-03-01 10:30:34',0x00,1),(3,'RS202603010003','销售退货单-客户003','SO202603010003',1003,'2026-03-01 12:00:00','客户原因',3,'测试退货单3','1','2026-03-01 10:30:34','1','2026-03-01 10:30:34',0x00,1),(4,'RS20260301001','测试退货单001',NULL,200,'1970-01-01 08:00:00','产品质量问题',0,'','1','2026-03-01 18:33:14','1','2026-03-01 18:33:14',0x00,1),(7,'RS202603010099','测试销售退货单','SO202603010001',200,'1970-01-01 08:00:00','质量问题',2,'测试备注','1','2026-03-01 18:36:00','1','2026-03-30 19:15:14',0x00,1),(8,'RS202603010101','测试销售退货单','SO202603010001',200,'1970-01-01 08:00:00','质量问题',4,'测试备注','1','2026-03-01 18:36:32','1','2026-03-01 18:36:33',0x00,1),(9,'RS202603010102','测试取消退货单','SO202603010002',200,'1970-01-01 08:00:00','测试取消',5,'测试取消','1','2026-03-01 18:36:33','1','2026-03-01 18:36:33',0x00,1),(10,'RSZFhj41kGEq','32321',NULL,200,'1970-01-01 08:00:00',NULL,4,'','1','2026-03-01 22:12:15','1','2026-03-01 22:54:53',0x00,1),(11,'RSrRH6uk69T5','AAA',NULL,200,'1970-01-01 08:00:00','321321312',1,'','1','2026-03-26 21:43:36','1','2026-03-26 22:23:33',0x00,1),(12,'RS20260330001','ABC',NULL,200,'1970-01-01 08:00:00','AAAA',1,'','1','2026-03-30 19:13:12','1','2026-03-30 19:15:04',0x00,1),(13,'RS-20260412-001','ä¸´æ—¶é”€å”®é€€è´§å•çš„æ¨¡æ‹Ÿæ•°æ®','SO-123456',1,'2026-04-11 18:28:00',NULL,0,'','','2026-04-11 18:28:00','1','2026-04-17 09:55:32',0x01,1);
/*!40000 ALTER TABLE `mes_wm_return_sales` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_return_sales_detail`
--

DROP TABLE IF EXISTS `mes_wm_return_sales_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_return_sales_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `return_id` bigint NOT NULL COMMENT '退货单ID',
  `line_id` bigint NOT NULL COMMENT '行ID',
  `item_id` bigint NOT NULL COMMENT '物料ID',
  `quantity` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '上架数量',
  `batch_id` bigint DEFAULT NULL COMMENT '批次ID',
  `batch_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `warehouse_id` bigint DEFAULT NULL COMMENT '仓库ID',
  `location_id` bigint DEFAULT NULL COMMENT '库区ID',
  `area_id` bigint DEFAULT NULL COMMENT '库位ID',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_line_id` (`line_id`) USING BTREE,
  KEY `idx_return_id` (`return_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE,
  KEY `idx_batch_id` (`batch_id`) USING BTREE,
  KEY `idx_warehouse_id` (`warehouse_id`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='销售退货单明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_return_sales_detail`
--

LOCK TABLES `mes_wm_return_sales_detail` WRITE;
/*!40000 ALTER TABLE `mes_wm_return_sales_detail` DISABLE KEYS */;
INSERT INTO `mes_wm_return_sales_detail` VALUES (1,2,3,2002,100.00,3003,NULL,4001,5001,6001,'明细1','1','2026-03-01 10:30:34','1','2026-03-01 10:30:34',0x00,1),(2,2,3,2002,100.00,3003,NULL,4001,5002,6002,'明细2','1','2026-03-01 10:30:34','1','2026-03-01 10:30:34',0x00,1),(3,2,4,2003,150.00,3004,NULL,4002,5003,6003,'明细3','1','2026-03-01 10:30:34','1','2026-03-01 10:30:34',0x00,1),(4,3,5,2003,300.00,3005,NULL,4003,5004,6004,'明细4','1','2026-03-01 10:30:34','1','2026-03-01 10:30:34',0x00,1),(5,8,6,69,100.00,NULL,NULL,701,711,721,'测试明细','1','2026-03-01 18:36:33','1','2026-03-01 18:36:33',0x00,1),(6,10,9,69,123.00,NULL,NULL,702,713,724,'','1','2026-03-01 22:54:50','1','2026-03-01 22:54:50',0x00,1);
/*!40000 ALTER TABLE `mes_wm_return_sales_detail` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_return_sales_line`
--

DROP TABLE IF EXISTS `mes_wm_return_sales_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_return_sales_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '行ID',
  `return_id` bigint NOT NULL COMMENT '退货单ID',
  `item_id` bigint NOT NULL COMMENT '物料ID',
  `quantity` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '退货数量',
  `batch_id` bigint DEFAULT NULL COMMENT '批次ID',
  `batch_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `rqc_id` bigint DEFAULT NULL COMMENT '退货检验单 ID',
  `rqc_check_flag` bit(1) DEFAULT b'0' COMMENT '是否需要质检',
  `quality_status` int DEFAULT NULL COMMENT '质检状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_return_id` (`return_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE,
  KEY `idx_batch_id` (`batch_id`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='销售退货单行';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_return_sales_line`
--

LOCK TABLES `mes_wm_return_sales_line` WRITE;
/*!40000 ALTER TABLE `mes_wm_return_sales_line` DISABLE KEYS */;
INSERT INTO `mes_wm_return_sales_line` VALUES (1,1,2001,100.00,3001,NULL,NULL,0x00,2,'行1','1','2026-03-01 10:30:34','1','2026-03-24 09:12:54',0x00,1),(2,1,2002,50.00,3002,NULL,NULL,0x00,NULL,'行2','1','2026-03-01 10:30:34','1','2026-03-24 09:12:54',0x00,1),(3,2,2002,200.00,3003,NULL,NULL,0x00,2,'行3','1','2026-03-01 10:30:34','1','2026-03-24 09:12:54',0x00,1),(4,2,2003,150.00,3004,NULL,NULL,0x00,NULL,'行4','1','2026-03-01 10:30:34','1','2026-03-24 09:12:54',0x00,1),(5,3,2003,300.00,3005,NULL,NULL,0x00,NULL,'行5','1','2026-03-01 10:30:34','1','2026-03-24 09:12:54',0x00,1),(6,8,69,100.00,NULL,NULL,NULL,0x00,NULL,'测试行','1','2026-03-01 18:36:32','1','2026-03-24 09:12:54',0x00,1),(7,9,69,50.00,NULL,NULL,NULL,0x00,NULL,'','1','2026-03-01 18:36:33','1','2026-03-24 09:12:54',0x00,1),(8,7,69,10.00,NULL,NULL,NULL,0x00,NULL,'','1','2026-03-01 22:01:26','1','2026-03-01 22:01:26',0x00,1),(9,10,69,333.00,NULL,NULL,NULL,0x00,NULL,'','1','2026-03-01 22:12:25','1','2026-03-01 22:12:25',0x00,1),(10,11,94,1.00,NULL,NULL,NULL,0x01,0,'','1','2026-03-26 22:23:29','1','2026-03-26 22:23:29',0x00,1),(11,12,70,12.00,5,NULL,NULL,0x01,0,'','1','2026-03-30 19:14:52','1','2026-03-30 19:14:56',0x00,1);
/*!40000 ALTER TABLE `mes_wm_return_sales_line` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_return_vendor`
--

DROP TABLE IF EXISTS `mes_wm_return_vendor`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_return_vendor` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '退货单编号',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '退货单名称',
  `purchase_order_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '采购订单号',
  `vendor_id` bigint DEFAULT NULL COMMENT '供应商 ID',
  `return_date` datetime DEFAULT NULL COMMENT '退货日期',
  `return_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '退货原因',
  `transport_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '物流单号',
  `transport_telephone` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '联系电话',
  `status` int NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_vendor_id` (`vendor_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 供应商退货单主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_return_vendor`
--

LOCK TABLES `mes_wm_return_vendor` WRITE;
/*!40000 ALTER TABLE `mes_wm_return_vendor` DISABLE KEYS */;
INSERT INTO `mes_wm_return_vendor` VALUES (1,'RTV-20260228-001','供应商退货单-草稿','PO-20260201-001',200,'2026-02-28 10:00:00','来料不良','','',5,'测试退货单-草稿','1','2026-02-28 09:59:24','1','2026-02-28 18:08:21',0x00,1),(2,'RTV-20260228-002','供应商退货单-已完成','PO-20260201-002',2,'2026-02-28 11:00:00','规格不符','SF1234567890','13800138000',4,'测试退货单-已完成','1','2026-02-28 09:59:24','1','2026-02-28 09:59:24',0x00,1),(3,'RTV-20260228-003','供应商退货单-已取消','PO-20260201-003',1,'2026-02-28 12:00:00','数量多余','','',5,'测试退货单-已取消','1','2026-02-28 09:59:24','1','2026-02-28 09:59:24',0x00,1),(4,'RVfZlUJ5mhRB','QQ','',200,'1970-01-01 08:00:00','','','',4,'','1','2026-02-28 18:00:17','1','2026-02-28 18:07:06',0x00,1),(5,'RV20260329000001','ABC','',200,'1970-01-01 08:00:00','','','',3,'','1','2026-03-29 21:29:40','1','2026-03-29 21:42:49',0x00,1),(6,'RV20260329000010','呃呃呃呃呃呃','',200,'1970-01-01 08:00:00','','','',2,'','1','2026-03-29 22:37:21','1','2026-03-29 22:40:18',0x00,1),(7,'RV20260329000011','EEEE','',200,'1970-01-01 08:00:00','','','',4,'','1','2026-03-29 22:44:59','1','2026-03-29 23:04:44',0x00,1),(8,'RV20260406000001','ABE','',202,'1970-01-01 08:00:00','','','',2,'','1','2026-04-06 10:41:04','1','2026-04-06 10:41:17',0x00,1),(9,'RV20260406000002','啊兜底','',200,'1970-01-01 08:00:00','','','',0,'','1','2026-04-06 16:58:33','1','2026-04-06 16:58:33',0x00,1);
/*!40000 ALTER TABLE `mes_wm_return_vendor` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_return_vendor_detail`
--

DROP TABLE IF EXISTS `mes_wm_return_vendor_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_return_vendor_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `return_id` bigint NOT NULL COMMENT '退货单 ID',
  `line_id` bigint NOT NULL COMMENT '行 ID',
  `material_stock_id` bigint DEFAULT NULL COMMENT '库存记录 ID',
  `item_id` bigint NOT NULL COMMENT '物料 ID',
  `quantity` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '拣货数量',
  `batch_id` bigint DEFAULT NULL COMMENT '批次 ID',
  `batch_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '批次号',
  `warehouse_id` bigint DEFAULT NULL COMMENT '仓库 ID',
  `location_id` bigint DEFAULT NULL COMMENT '库区 ID',
  `area_id` bigint DEFAULT NULL COMMENT '库位 ID',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_return_id` (`return_id`) USING BTREE,
  KEY `idx_line_id` (`line_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE,
  KEY `idx_warehouse_id` (`warehouse_id`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 供应商退货明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_return_vendor_detail`
--

LOCK TABLES `mes_wm_return_vendor_detail` WRITE;
/*!40000 ALTER TABLE `mes_wm_return_vendor_detail` DISABLE KEYS */;
INSERT INTO `mes_wm_return_vendor_detail` VALUES (1,1,1,NULL,101,60.00,NULL,'BATCH-001',1,101,1001,'仓库 A-库区 1-库位 1','1','2026-02-28 09:59:24','1','2026-02-28 09:59:24',0x00,1),(2,1,1,NULL,101,40.00,NULL,'BATCH-001',1,101,1002,'仓库 A-库区 1-库位 2','1','2026-02-28 09:59:24','1','2026-02-28 09:59:24',0x00,1),(3,2,3,NULL,103,200.00,NULL,'BATCH-002',1,101,1001,'仓库 A-库区 1-库位 1','1','2026-02-28 09:59:24','1','2026-02-28 09:59:24',0x00,1),(4,2,4,NULL,104,30.00,NULL,'BATCH-002',2,201,2001,'仓库 B-库区 1-库位 1','1','2026-02-28 09:59:24','1','2026-02-28 09:59:24',0x00,1),(5,4,6,NULL,69,555.00,NULL,'',702,713,724,'','1','2026-02-28 18:06:04','1','2026-02-28 18:06:04',0x00,1),(6,1,2,NULL,69,1.00,NULL,'',702,713,724,'','1','2026-02-28 18:08:04','1','2026-02-28 18:08:04',0x00,1),(7,5,7,NULL,69,10.00,NULL,'',703,714,725,'','1','2026-03-29 21:42:40','1','2026-03-29 21:42:40',0x00,1),(8,7,9,4,69,1.00,1,'TEST',702,713,724,'','1','2026-03-29 23:04:37','1','2026-03-29 23:04:37',0x00,1);
/*!40000 ALTER TABLE `mes_wm_return_vendor_detail` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_return_vendor_line`
--

DROP TABLE IF EXISTS `mes_wm_return_vendor_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_return_vendor_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `return_id` bigint NOT NULL COMMENT '退货单 ID',
  `item_id` bigint NOT NULL COMMENT '物料 ID',
  `quantity` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '退货数量',
  `batch_id` bigint DEFAULT NULL COMMENT '批次 ID',
  `batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '批次号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_return_id` (`return_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 供应商退货单行表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_return_vendor_line`
--

LOCK TABLES `mes_wm_return_vendor_line` WRITE;
/*!40000 ALTER TABLE `mes_wm_return_vendor_line` DISABLE KEYS */;
INSERT INTO `mes_wm_return_vendor_line` VALUES (1,1,101,100.00,NULL,'','物料 A 退货','1','2026-02-28 09:59:24','1','2026-02-28 18:07:14',0x01,1),(2,1,69,50.00,NULL,'','物料 B 退货','1','2026-02-28 09:59:24','1','2026-02-28 18:07:21',0x00,1),(3,2,103,200.00,NULL,'','物料 C 退货','1','2026-02-28 09:59:24','1','2026-02-28 09:59:24',0x00,1),(4,2,104,30.00,NULL,'','物料 D 退货','1','2026-02-28 09:59:24','1','2026-02-28 09:59:24',0x00,1),(5,3,105,80.00,NULL,'','物料 E 退货','1','2026-02-28 09:59:24','1','2026-02-28 09:59:24',0x00,1),(6,4,69,555.00,NULL,'','','1','2026-02-28 18:02:32','1','2026-02-28 18:02:32',0x00,1),(7,5,69,10.00,NULL,'','','1','2026-03-29 21:41:36','1','2026-03-29 21:41:36',0x00,1),(8,6,69,1.00,NULL,'','2','1','2026-03-29 22:40:16','1','2026-03-29 22:40:16',0x00,1),(9,7,69,12.00,3,'PC202600001','','1','2026-03-29 22:50:20','1','2026-03-29 22:53:38',0x00,1),(10,8,102,1.00,NULL,'','','1','2026-04-06 10:41:15','1','2026-04-06 10:41:15',0x00,1),(11,9,95,1.00,2,'PC20260','AABBB','1','2026-04-06 16:58:50','1','2026-04-06 16:58:50',0x00,1);
/*!40000 ALTER TABLE `mes_wm_return_vendor_line` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_sales_notice`
--

DROP TABLE IF EXISTS `mes_wm_sales_notice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_sales_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知单编号',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知单名称',
  `sales_order_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '销售订单编号',
  `client_id` bigint DEFAULT NULL COMMENT '客户 ID',
  `sales_date` date DEFAULT NULL COMMENT '发货日期',
  `recipient_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '收货人',
  `recipient_telephone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系方式',
  `recipient_address` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '收货地址',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '单据状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户 ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_code` (`code`,`tenant_id`) USING BTREE,
  KEY `idx_client_id` (`client_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_sales_date` (`sales_date`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发货通知单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_sales_notice`
--

LOCK TABLES `mes_wm_sales_notice` WRITE;
/*!40000 ALTER TABLE `mes_wm_sales_notice` DISABLE KEYS */;
INSERT INTO `mes_wm_sales_notice` VALUES (1,'SN202603020001','测试发货通知单','SO202603020001',200,'1970-01-01','张三','13800138000','北京市朝阳区测试地址',1,'测试备注','1','2026-03-02 11:50:15','1','2026-03-02 11:52:44',0x00,1),(2,'SN202603020002','修改后的草稿通知单','SO202603020002',207,'1970-01-01','修改后的收货人','13500135000','杭州市西湖区测试地址',1,'修改后的备注','1','2026-03-02 11:56:16','1','2026-03-02 12:02:59',0x00,1),(3,'SN20260330000003','BCE',NULL,200,'2026-03-10',NULL,NULL,NULL,3,NULL,'1','2026-03-30 17:06:00','1','2026-03-30 17:52:26',0x00,1),(4,'SN20260330000004','AABBEEE',NULL,200,'2026-03-18',NULL,NULL,NULL,3,NULL,'1','2026-03-30 18:03:04','1','2026-03-30 18:17:34',0x00,1),(5,'SN20260417000001','ABCDE',NULL,208,'2026-04-15',NULL,NULL,NULL,0,NULL,'1','2026-04-17 09:45:04','1','2026-04-17 09:45:04',0x00,1);
/*!40000 ALTER TABLE `mes_wm_sales_notice` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_sales_notice_line`
--

DROP TABLE IF EXISTS `mes_wm_sales_notice_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_sales_notice_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `notice_id` bigint NOT NULL COMMENT '通知单 ID',
  `item_id` bigint NOT NULL COMMENT '物料 ID',
  `batch_id` bigint DEFAULT NULL COMMENT '批次 ID',
  `batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `quantity` decimal(12,2) NOT NULL COMMENT '发货数量',
  `oqc_check_flag` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否检验',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户 ID',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_notice_id` (`notice_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE,
  KEY `idx_batch_id` (`batch_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发货通知单行';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_sales_notice_line`
--

LOCK TABLES `mes_wm_sales_notice_line` WRITE;
/*!40000 ALTER TABLE `mes_wm_sales_notice_line` DISABLE KEYS */;
INSERT INTO `mes_wm_sales_notice_line` VALUES (1,1,69,NULL,'BATCH001',100.00,0x01,'测试行备注','1','2026-03-02 11:50:39','1','2026-03-02 11:50:39',0x00,1),(2,2,69,NULL,NULL,3.00,0x01,NULL,'1','2026-03-02 12:02:22','1','2026-03-02 12:02:22',0x00,1),(3,3,94,7,'BATCH_ITEM_94',123.00,0x01,'biubiubiu','1','2026-03-30 17:52:24','1','2026-03-30 17:52:24',0x00,1),(4,4,69,8,'BATCH_ITEM_72',10.00,0x01,NULL,'1','2026-03-30 18:03:14','1','2026-03-30 18:08:21',0x00,1),(5,5,103,NULL,NULL,123.00,0x01,NULL,'1','2026-04-17 09:45:15','1','2026-04-17 09:45:15',0x00,1);
/*!40000 ALTER TABLE `mes_wm_sales_notice_line` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_sn`
--

DROP TABLE IF EXISTS `mes_wm_sn`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_sn` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `uuid` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次 UUID（用于标记同一批次生成的 SN 码）',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SN 码（唯一）',
  `item_id` bigint NOT NULL COMMENT '物料编号',
  `batch_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `work_order_id` bigint DEFAULT NULL COMMENT '生产工单编号',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_code` (`code`,`deleted`,`tenant_id`) USING BTREE,
  KEY `idx_item_batch_create` (`item_id`,`batch_code`,`create_time`) USING BTREE COMMENT '物料批次创建时间组合索引',
  KEY `idx_work_order` (`work_order_id`) USING BTREE COMMENT '工单索引',
  KEY `idx_tenant` (`tenant_id`) USING BTREE COMMENT '租户索引',
  KEY `idx_uuid` (`uuid`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=369 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES SN 码';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_sn`
--

LOCK TABLES `mes_wm_sn` WRITE;
/*!40000 ALTER TABLE `mes_wm_sn` DISABLE KEYS */;
INSERT INTO `mes_wm_sn` VALUES (151,'1ebdb59516c54de98a911d943ee6e933','SN20260305000151',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(152,'1ebdb59516c54de98a911d943ee6e933','SN20260305000152',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(153,'1ebdb59516c54de98a911d943ee6e933','SN20260305000153',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(154,'1ebdb59516c54de98a911d943ee6e933','SN20260305000154',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(155,'1ebdb59516c54de98a911d943ee6e933','SN20260305000155',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(156,'1ebdb59516c54de98a911d943ee6e933','SN20260305000156',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(157,'1ebdb59516c54de98a911d943ee6e933','SN20260305000157',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(158,'1ebdb59516c54de98a911d943ee6e933','SN20260305000158',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(159,'1ebdb59516c54de98a911d943ee6e933','SN20260305000159',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(160,'1ebdb59516c54de98a911d943ee6e933','SN20260305000160',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(161,'1ebdb59516c54de98a911d943ee6e933','SN20260305000161',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(162,'1ebdb59516c54de98a911d943ee6e933','SN20260305000162',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(163,'1ebdb59516c54de98a911d943ee6e933','SN20260305000163',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(164,'1ebdb59516c54de98a911d943ee6e933','SN20260305000164',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(165,'1ebdb59516c54de98a911d943ee6e933','SN20260305000165',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(166,'1ebdb59516c54de98a911d943ee6e933','SN20260305000166',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(167,'1ebdb59516c54de98a911d943ee6e933','SN20260305000167',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(168,'1ebdb59516c54de98a911d943ee6e933','SN20260305000168',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(169,'1ebdb59516c54de98a911d943ee6e933','SN20260305000169',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(170,'1ebdb59516c54de98a911d943ee6e933','SN20260305000170',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(171,'1ebdb59516c54de98a911d943ee6e933','SN20260305000171',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(172,'1ebdb59516c54de98a911d943ee6e933','SN20260305000172',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(173,'1ebdb59516c54de98a911d943ee6e933','SN20260305000173',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(174,'1ebdb59516c54de98a911d943ee6e933','SN20260305000174',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(175,'1ebdb59516c54de98a911d943ee6e933','SN20260305000175',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(176,'1ebdb59516c54de98a911d943ee6e933','SN20260305000176',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(177,'1ebdb59516c54de98a911d943ee6e933','SN20260305000177',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(178,'1ebdb59516c54de98a911d943ee6e933','SN20260305000178',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(179,'1ebdb59516c54de98a911d943ee6e933','SN20260305000179',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(180,'1ebdb59516c54de98a911d943ee6e933','SN20260305000180',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(181,'1ebdb59516c54de98a911d943ee6e933','SN20260305000181',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(182,'1ebdb59516c54de98a911d943ee6e933','SN20260305000182',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(183,'1ebdb59516c54de98a911d943ee6e933','SN20260305000183',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(184,'1ebdb59516c54de98a911d943ee6e933','SN20260305000184',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(185,'1ebdb59516c54de98a911d943ee6e933','SN20260305000185',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(186,'1ebdb59516c54de98a911d943ee6e933','SN20260305000186',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(187,'1ebdb59516c54de98a911d943ee6e933','SN20260305000187',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(188,'1ebdb59516c54de98a911d943ee6e933','SN20260305000188',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(189,'1ebdb59516c54de98a911d943ee6e933','SN20260305000189',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(190,'1ebdb59516c54de98a911d943ee6e933','SN20260305000190',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(191,'1ebdb59516c54de98a911d943ee6e933','SN20260305000191',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(192,'1ebdb59516c54de98a911d943ee6e933','SN20260305000192',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(193,'1ebdb59516c54de98a911d943ee6e933','SN20260305000193',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(194,'1ebdb59516c54de98a911d943ee6e933','SN20260305000194',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(195,'1ebdb59516c54de98a911d943ee6e933','SN20260305000195',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(196,'1ebdb59516c54de98a911d943ee6e933','SN20260305000196',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(197,'1ebdb59516c54de98a911d943ee6e933','SN20260305000197',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(198,'1ebdb59516c54de98a911d943ee6e933','SN20260305000198',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(199,'1ebdb59516c54de98a911d943ee6e933','SN20260305000199',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(200,'1ebdb59516c54de98a911d943ee6e933','SN20260305000200',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(201,'1ebdb59516c54de98a911d943ee6e933','SN20260305000201',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(202,'1ebdb59516c54de98a911d943ee6e933','SN20260305000202',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(203,'1ebdb59516c54de98a911d943ee6e933','SN20260305000203',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(204,'1ebdb59516c54de98a911d943ee6e933','SN20260305000204',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(205,'1ebdb59516c54de98a911d943ee6e933','SN20260305000205',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(206,'1ebdb59516c54de98a911d943ee6e933','SN20260305000206',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(207,'1ebdb59516c54de98a911d943ee6e933','SN20260305000207',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(208,'1ebdb59516c54de98a911d943ee6e933','SN20260305000208',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(209,'1ebdb59516c54de98a911d943ee6e933','SN20260305000209',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(210,'1ebdb59516c54de98a911d943ee6e933','SN20260305000210',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(211,'1ebdb59516c54de98a911d943ee6e933','SN20260305000211',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(212,'1ebdb59516c54de98a911d943ee6e933','SN20260305000212',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(213,'1ebdb59516c54de98a911d943ee6e933','SN20260305000213',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(214,'1ebdb59516c54de98a911d943ee6e933','SN20260305000214',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(215,'1ebdb59516c54de98a911d943ee6e933','SN20260305000215',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(216,'1ebdb59516c54de98a911d943ee6e933','SN20260305000216',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(217,'1ebdb59516c54de98a911d943ee6e933','SN20260305000217',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(218,'1ebdb59516c54de98a911d943ee6e933','SN20260305000218',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(219,'1ebdb59516c54de98a911d943ee6e933','SN20260305000219',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(220,'1ebdb59516c54de98a911d943ee6e933','SN20260305000220',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(221,'1ebdb59516c54de98a911d943ee6e933','SN20260305000221',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(222,'1ebdb59516c54de98a911d943ee6e933','SN20260305000222',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(223,'1ebdb59516c54de98a911d943ee6e933','SN20260305000223',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(224,'1ebdb59516c54de98a911d943ee6e933','SN20260305000224',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(225,'1ebdb59516c54de98a911d943ee6e933','SN20260305000225',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(226,'1ebdb59516c54de98a911d943ee6e933','SN20260305000226',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(227,'1ebdb59516c54de98a911d943ee6e933','SN20260305000227',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(228,'1ebdb59516c54de98a911d943ee6e933','SN20260305000228',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(229,'1ebdb59516c54de98a911d943ee6e933','SN20260305000229',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(230,'1ebdb59516c54de98a911d943ee6e933','SN20260305000230',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(231,'1ebdb59516c54de98a911d943ee6e933','SN20260305000231',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(232,'1ebdb59516c54de98a911d943ee6e933','SN20260305000232',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(233,'1ebdb59516c54de98a911d943ee6e933','SN20260305000233',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(234,'1ebdb59516c54de98a911d943ee6e933','SN20260305000234',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(235,'1ebdb59516c54de98a911d943ee6e933','SN20260305000235',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(236,'1ebdb59516c54de98a911d943ee6e933','SN20260305000236',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(237,'1ebdb59516c54de98a911d943ee6e933','SN20260305000237',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(238,'1ebdb59516c54de98a911d943ee6e933','SN20260305000238',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(239,'1ebdb59516c54de98a911d943ee6e933','SN20260305000239',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(240,'1ebdb59516c54de98a911d943ee6e933','SN20260305000240',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(241,'1ebdb59516c54de98a911d943ee6e933','SN20260305000241',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(242,'1ebdb59516c54de98a911d943ee6e933','SN20260305000242',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(243,'1ebdb59516c54de98a911d943ee6e933','SN20260305000243',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(244,'1ebdb59516c54de98a911d943ee6e933','SN20260305000244',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(245,'1ebdb59516c54de98a911d943ee6e933','SN20260305000245',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(246,'1ebdb59516c54de98a911d943ee6e933','SN20260305000246',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(247,'1ebdb59516c54de98a911d943ee6e933','SN20260305000247',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(248,'1ebdb59516c54de98a911d943ee6e933','SN20260305000248',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(249,'1ebdb59516c54de98a911d943ee6e933','SN20260305000249',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(250,'1ebdb59516c54de98a911d943ee6e933','SN20260305000250',69,'100',NULL,'1','2026-03-05 13:29:20','1','2026-03-05 13:29:20',0x00,1),(251,'doc-snap-sn-20260415-a','SN20260415001001',69,'DOC-20260415-A',NULL,'admin','2026-04-15 11:02:42','admin','2026-04-15 11:02:42',0x00,1),(252,'doc-snap-sn-20260415-a','SN20260415001002',69,'DOC-20260415-A',NULL,'admin','2026-04-15 11:02:42','admin','2026-04-15 11:02:42',0x00,1),(253,'doc-snap-sn-20260415-a','SN20260415001003',69,'DOC-20260415-A',NULL,'admin','2026-04-15 11:02:42','admin','2026-04-15 11:02:42',0x00,1),(254,'doc-snap-sn-20260415-a','SN20260415001004',69,'DOC-20260415-A',NULL,'admin','2026-04-15 11:02:42','admin','2026-04-15 11:02:42',0x00,1),(255,'doc-snap-sn-20260415-a','SN20260415001005',69,'DOC-20260415-A',NULL,'admin','2026-04-15 11:02:42','admin','2026-04-15 11:02:42',0x00,1),(256,'doc-snap-sn-20260415-a','SN20260415001006',69,'DOC-20260415-A',NULL,'admin','2026-04-15 11:02:42','admin','2026-04-15 11:02:42',0x00,1),(257,'doc-snap-sn-20260415-a','SN20260415001007',69,'DOC-20260415-A',NULL,'admin','2026-04-15 11:02:42','admin','2026-04-15 11:02:42',0x00,1),(258,'doc-snap-sn-20260415-a','SN20260415001008',69,'DOC-20260415-A',NULL,'admin','2026-04-15 11:02:42','admin','2026-04-15 11:02:42',0x00,1),(259,'doc-snap-sn-20260415-b','SN20260415002001',69,'DOC-20260415-B',NULL,'admin','2026-04-15 11:02:42','admin','2026-04-15 11:02:42',0x00,1),(260,'doc-snap-sn-20260415-b','SN20260415002002',69,'DOC-20260415-B',NULL,'admin','2026-04-15 11:02:42','admin','2026-04-15 11:02:42',0x00,1),(261,'doc-snap-sn-20260415-b','SN20260415002003',69,'DOC-20260415-B',NULL,'admin','2026-04-15 11:02:42','admin','2026-04-15 11:02:42',0x00,1),(262,'doc-snap-sn-20260415-b','SN20260415002004',69,'DOC-20260415-B',NULL,'admin','2026-04-15 11:02:42','admin','2026-04-15 11:02:42',0x00,1),(263,'doc-snap-sn-20260415-b','SN20260415002005',69,'DOC-20260415-B',NULL,'admin','2026-04-15 11:02:42','admin','2026-04-15 11:02:42',0x00,1),(264,'doc-snap-sn-20260415-b','SN20260415002006',69,'DOC-20260415-B',NULL,'admin','2026-04-15 11:02:42','admin','2026-04-15 11:02:42',0x00,1),(265,'doc-snap-sn-20260415-b','SN20260415002007',69,'DOC-20260415-B',NULL,'admin','2026-04-15 11:02:42','admin','2026-04-15 11:02:42',0x00,1),(266,'doc-snap-sn-20260415-b','SN20260415002008',69,'DOC-20260415-B',NULL,'admin','2026-04-15 11:02:42','admin','2026-04-15 11:02:42',0x00,1),(267,'doc-snap-sn-20260415-b','SN20260415002009',69,'DOC-20260415-B',NULL,'admin','2026-04-15 11:02:42','admin','2026-04-15 11:02:42',0x00,1),(268,'doc-snap-sn-20260415-b','SN20260415002010',69,'DOC-20260415-B',NULL,'admin','2026-04-15 11:02:42','admin','2026-04-15 11:02:42',0x00,1),(269,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000001',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(270,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000002',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(271,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000003',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(272,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000004',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(273,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000005',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(274,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000006',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(275,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000007',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(276,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000008',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(277,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000009',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(278,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000010',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(279,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000011',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(280,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000012',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(281,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000013',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(282,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000014',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(283,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000015',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(284,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000016',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(285,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000017',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(286,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000018',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(287,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000019',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(288,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000020',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(289,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000021',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(290,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000022',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(291,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000023',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(292,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000024',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(293,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000025',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(294,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000026',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(295,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000027',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(296,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000028',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(297,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000029',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(298,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000030',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(299,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000031',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(300,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000032',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(301,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000033',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(302,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000034',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(303,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000035',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(304,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000036',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(305,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000037',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(306,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000038',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(307,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000039',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(308,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000040',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(309,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000041',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(310,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000042',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(311,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000043',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(312,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000044',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(313,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000045',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(314,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000046',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(315,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000047',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(316,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000048',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(317,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000049',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(318,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000050',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(319,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000051',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(320,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000052',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(321,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000053',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(322,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000054',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(323,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000055',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(324,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000056',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(325,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000057',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(326,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000058',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(327,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000059',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(328,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000060',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(329,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000061',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(330,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000062',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(331,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000063',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(332,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000064',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(333,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000065',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(334,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000066',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(335,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000067',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(336,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000068',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(337,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000069',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(338,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000070',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(339,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000071',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(340,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000072',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(341,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000073',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(342,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000074',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(343,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000075',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(344,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000076',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(345,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000077',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(346,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000078',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(347,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000079',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(348,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000080',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(349,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000081',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(350,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000082',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(351,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000083',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(352,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000084',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(353,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000085',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(354,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000086',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(355,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000087',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(356,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000088',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(357,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000089',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(358,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000090',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(359,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000091',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(360,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000092',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(361,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000093',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(362,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000094',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(363,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000095',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(364,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000096',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(365,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000097',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(366,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000098',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(367,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000099',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1),(368,'6578fe3d133c4e3f800e2e3cb5f9873a','SN20260415000100',95,'EEE',NULL,'1','2026-04-15 19:31:40','1','2026-04-15 19:31:40',0x00,1);
/*!40000 ALTER TABLE `mes_wm_sn` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_stock_taking_plan`
--

DROP TABLE IF EXISTS `mes_wm_stock_taking_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_stock_taking_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '方案编码',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '方案名称',
  `type` tinyint NOT NULL COMMENT '盘点类型',
  `start_time` datetime DEFAULT NULL COMMENT '计划开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '计划结束时间',
  `blind_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否盲盘',
  `frozen` bit(1) DEFAULT b'0' COMMENT '是否冻结库存',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_create_time` (`create_time`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 库存盘点方案表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_stock_taking_plan`
--

LOCK TABLES `mes_wm_stock_taking_plan` WRITE;
/*!40000 ALTER TABLE `mes_wm_stock_taking_plan` DISABLE KEYS */;
INSERT INTO `mes_wm_stock_taking_plan` VALUES (1,'PLAN202603001','2026年第一季度全盘',1,'2026-03-10 08:00:00','2026-03-15 18:00:00',0x00,0x01,0,'第一季度全面盘点','1','2026-03-09 10:00:00','1','2026-03-31 18:13:42',0x00,1),(2,'PLAN202603002','原料仓抽盘',1,'2026-03-20 08:00:00','2026-03-22 18:00:00',0x01,0x00,0,'原料仓抽样盘点（盲盘）','1','2026-03-09 11:00:00','1','2026-03-10 00:38:19',0x00,1),(3,'PLAN202603003','成品仓循环盘点',3,'2026-03-25 08:00:00','2026-03-30 18:00:00',0x00,0x00,1,'成品仓循环盘点（已禁用）','1','2026-03-09 12:00:00','1','2026-03-10 00:38:24',0x01,1),(4,'PDP202603310001','EEE',1,NULL,NULL,0x00,0x00,1,NULL,'1','2026-03-31 18:23:53','1','2026-03-31 18:23:58',0x00,1);
/*!40000 ALTER TABLE `mes_wm_stock_taking_plan` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_stock_taking_plan_param`
--

DROP TABLE IF EXISTS `mes_wm_stock_taking_plan_param`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_stock_taking_plan_param` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `plan_id` bigint NOT NULL COMMENT '盘点方案编号',
  `type` smallint NOT NULL COMMENT '参数值类型',
  `value_id` bigint NOT NULL COMMENT '参数值编号',
  `value_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '参数值编码',
  `value_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '参数值名称',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_plan_id` (`plan_id`) USING BTREE,
  KEY `idx_type` (`type`) USING BTREE,
  KEY `idx_create_time` (`create_time`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 库存盘点方案参数表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_stock_taking_plan_param`
--

LOCK TABLES `mes_wm_stock_taking_plan_param` WRITE;
/*!40000 ALTER TABLE `mes_wm_stock_taking_plan_param` DISABLE KEYS */;
INSERT INTO `mes_wm_stock_taking_plan_param` VALUES (1,1,1,1,'WH001','原料仓','全盘-仓库1','1','2026-03-09 10:00:00','1','2026-03-09 22:42:20',0x01,1),(2,1,1,2,'WH002','成品仓','全盘-仓库2','1','2026-03-09 10:00:00','1','2026-03-09 22:42:21',0x01,1),(3,2,1,1,'WH001','原料仓','抽盘-仓库','1','2026-03-09 11:00:00','1','2026-03-09 22:07:03',0x01,1),(4,2,4,101,'MAT001','钢材A','抽盘-物料1','1','2026-03-09 11:00:00','1','2026-03-09 22:48:46',0x01,1),(5,2,4,102,'MAT002','钢材B','抽盘-物料2','1','2026-03-09 11:00:00','1','2026-03-09 22:48:45',0x01,1),(6,3,1,2,'WH002','成品仓','循环盘点-仓库','1','2026-03-09 12:00:00','1','2026-03-09 16:38:23',0x01,1),(7,3,2,201,'AREA001','A区','循环盘点-库区','1','2026-03-09 12:00:00','1','2026-03-09 16:38:23',0x01,1),(8,2,103,713,'LOC-FIN-A','成品 A 区','','1','2026-03-09 22:28:20','1','2026-03-09 22:28:29',0x00,1),(9,2,102,702,'WH-FIN','成品仓','','1','2026-03-09 22:55:04','1','2026-03-09 22:55:04',0x00,1),(10,2,103,713,'LOC-FIN-A','成品 A 区','','1','2026-03-09 22:55:10','1','2026-03-09 22:55:10',0x00,1),(11,2,104,724,'AREA-FIN-A-01','成品A-01','123','1','2026-03-09 22:55:18','1','2026-03-09 22:55:18',0x00,1),(12,2,600,69,'IF2022082437','色粉【黑色】','','1','2026-03-09 22:55:53','1','2026-03-09 22:55:53',0x00,1),(13,1,600,69,'IF2022082437','色粉【黑色】','','1','2026-03-10 19:11:36','1','2026-03-10 19:11:36',0x00,1),(14,4,102,703,'WIP_VIRTUAL_WAREHOUSE','虚拟线边仓库','EEE','1','2026-03-31 18:24:18','1','2026-03-31 18:24:18',0x00,1),(15,4,103,714,'WIP_VIRTUAL_LOCATION','虚拟线边库区','','1','2026-03-31 18:24:30','1','2026-03-31 18:24:30',0x00,1),(16,4,104,725,'WIP_VIRTUAL_AREA','虚拟线边库位','','1','2026-03-31 18:24:44','1','2026-03-31 18:24:44',0x00,1),(17,4,107,2,'PC20260','PC20260','EEE','1','2026-03-31 18:24:52','1','2026-03-31 18:24:52',0x00,1),(18,4,900,2,'2','不合格','EEE','1','2026-03-31 18:27:10','1','2026-03-31 18:27:24',0x00,1);
/*!40000 ALTER TABLE `mes_wm_stock_taking_plan_param` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_stock_taking_task`
--

DROP TABLE IF EXISTS `mes_wm_stock_taking_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_stock_taking_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务编码',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务名称',
  `taking_date` datetime NOT NULL COMMENT '盘点日期',
  `type` tinyint NOT NULL COMMENT '盘点类型',
  `user_id` bigint DEFAULT NULL COMMENT '盘点人编号',
  `plan_id` bigint NOT NULL COMMENT '盘点方案ID',
  `blind_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否盲盘',
  `frozen` bit(1) DEFAULT b'0' COMMENT '是否冻结库存',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_plan_id` (`plan_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_create_time` (`create_time`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 库存盘点任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_stock_taking_task`
--

LOCK TABLES `mes_wm_stock_taking_task` WRITE;
/*!40000 ALTER TABLE `mes_wm_stock_taking_task` DISABLE KEYS */;
INSERT INTO `mes_wm_stock_taking_task` VALUES (1,'TASK2026031001','原料仓全盘任务','2026-03-10 08:00:00',1,1,1,0x00,0x01,'2026-03-10 08:30:00','2026-03-10 17:30:00',4,'已完成，差异已调整','1','2026-03-09 10:00:00','1','2026-03-10 17:30:00',0x00,1),(2,'TASK2026031101','成品仓全盘任务','2026-03-11 08:00:00',1,2,1,0x00,0x01,'2026-03-11 08:30:00',NULL,1,'盘点进行中','1','2026-03-09 10:00:00','1','2026-03-11 09:00:00',0x00,1),(3,'TASK2026031201','辅料仓全盘任务','1970-01-01 08:00:00',1,1,3,0x00,0x00,NULL,NULL,2,'待开始盘点','1','2026-03-09 10:00:00','1','2026-03-12 01:06:56',0x00,1),(4,'TASK2026032001','原料仓A区抽盘','2026-03-20 08:00:00',2,3,2,0x01,0x00,'2026-03-20 08:30:00','2026-03-20 12:00:00',4,'盲盘完成，无差异','1','2026-03-09 11:00:00','1','2026-03-20 12:00:00',0x00,1),(5,'TASK2026032101','原料仓B区抽盘','2026-03-21 08:00:00',2,3,2,0x01,0x00,'2026-03-21 08:30:00',NULL,1,'盲盘进行中','1','2026-03-09 11:00:00','1','2026-03-21 09:00:00',0x00,1),(6,'TASK2026030501','成品仓循环盘点-第1轮','2026-03-05 08:00:00',1,2,3,0x00,0x00,'2026-03-05 08:30:00','2026-03-05 16:00:00',4,'第1轮盘点完成','1','2026-03-04 10:00:00','1','2026-03-05 16:00:00',0x00,1),(7,'TASK2026030601','成品仓循环盘点-第2轮','2026-03-06 08:00:00',1,2,3,0x00,0x00,'2026-03-06 08:30:00','2026-03-06 11:00:00',5,'方案禁用，任务取消','1','2026-03-04 10:00:00','1','2026-03-06 11:00:00',0x00,1),(13,'STOHHQIEQp0q','消息','1970-01-01 08:00:00',1,1,1,0x00,0x01,'2026-03-10 08:00:00','2026-03-15 18:00:00',4,'123321','1','2026-03-10 19:15:09','1','2026-03-12 01:05:28',0x00,1),(15,'PDT202604060001','2026年第一季度全盘','1970-01-01 08:00:00',1,1,1,0x00,0x01,'2026-03-10 08:00:00','2026-03-15 18:00:00',0,NULL,'1','2026-04-06 12:05:17','1','2026-04-06 12:05:17',0x00,1);
/*!40000 ALTER TABLE `mes_wm_stock_taking_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_stock_taking_task_line`
--

DROP TABLE IF EXISTS `mes_wm_stock_taking_task_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_stock_taking_task_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `task_id` bigint NOT NULL COMMENT '盘点任务编号',
  `material_stock_id` bigint DEFAULT NULL COMMENT '库存编号',
  `item_id` bigint NOT NULL COMMENT '物料编号',
  `batch_id` bigint DEFAULT NULL COMMENT '批次编号',
  `batch_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次编码',
  `quantity` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT '账面数量',
  `taking_quantity` decimal(24,6) DEFAULT NULL COMMENT '盘点数量',
  `warehouse_id` bigint NOT NULL COMMENT '仓库编号',
  `location_id` bigint DEFAULT NULL COMMENT '库区编号',
  `area_id` bigint DEFAULT NULL COMMENT '库位编号',
  `status` tinyint NOT NULL COMMENT '盘点状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_task_id` (`task_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 盘点任务行';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_stock_taking_task_line`
--

LOCK TABLES `mes_wm_stock_taking_task_line` WRITE;
/*!40000 ALTER TABLE `mes_wm_stock_taking_task_line` DISABLE KEYS */;
INSERT INTO `mes_wm_stock_taking_task_line` VALUES (1,13,4,69,NULL,NULL,737.000000,NULL,702,713,724,0,NULL,'1','2026-03-10 19:15:09','1','2026-03-10 23:51:23',0x01,1),(2,13,NULL,69,NULL,NULL,123.000000,1.000000,702,713,724,3,NULL,'1','2026-03-10 23:51:19','1','2026-03-10 23:51:19',0x00,1),(3,3,NULL,69,NULL,NULL,1.000000,1.000000,702,713,724,1,'222222','1','2026-03-12 01:06:49','1','2026-03-12 01:06:49',0x00,1),(4,15,16,69,0,NULL,100.000000,0.000000,702,711,721,3,NULL,'1','2026-04-06 12:05:17','1','2026-04-06 12:05:17',0x00,1),(5,15,79,69,888,NULL,80.000000,0.000000,702,711,721,3,NULL,'1','2026-04-06 12:05:17','1','2026-04-06 12:05:17',0x00,1),(6,15,48,69,999,NULL,50.000000,0.000000,702,711,721,3,NULL,'1','2026-04-06 12:05:17','1','2026-04-06 12:05:17',0x00,1),(7,15,NULL,100,1,NULL,800.000000,NULL,701,NULL,NULL,0,NULL,'1','2026-04-06 12:05:25','1','2026-04-06 12:07:45',0x01,1),(8,15,NULL,100,1,NULL,200.000000,NULL,702,NULL,NULL,0,NULL,'1','2026-04-06 12:05:25','1','2026-04-06 12:07:48',0x01,1),(9,15,NULL,75,4,NULL,2.000000,NULL,703,714,725,0,NULL,'1','2026-04-06 12:05:30','1','2026-04-06 12:07:50',0x01,1),(10,15,NULL,94,7,NULL,1.000000,NULL,703,714,725,0,NULL,'1','2026-04-06 12:05:30','1','2026-04-06 12:07:52',0x01,1),(11,15,5,100,1,NULL,800.000000,NULL,701,NULL,NULL,3,NULL,'1','2026-04-06 12:14:28','1','2026-04-06 12:14:32',0x01,1),(12,15,12,75,4,NULL,2.000000,NULL,703,714,725,3,NULL,'1','2026-04-06 12:14:35','1','2026-04-06 12:14:35',0x00,1),(13,15,5,100,1,NULL,800.000000,NULL,701,NULL,NULL,3,NULL,'1','2026-05-30 09:12:48','1','2026-05-30 09:12:48',0x00,1);
/*!40000 ALTER TABLE `mes_wm_stock_taking_task_line` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_stock_taking_task_result`
--

DROP TABLE IF EXISTS `mes_wm_stock_taking_task_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_stock_taking_task_result` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `task_id` bigint NOT NULL COMMENT '盘点任务编号',
  `line_id` bigint DEFAULT NULL COMMENT '盘点任务行编号',
  `material_stock_id` bigint DEFAULT NULL COMMENT '库存编号',
  `item_id` bigint NOT NULL COMMENT '物料编号',
  `batch_id` bigint DEFAULT NULL COMMENT '批次编号',
  `batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次编码',
  `warehouse_id` bigint NOT NULL COMMENT '仓库编号',
  `location_id` bigint DEFAULT NULL COMMENT '库区编号',
  `area_id` bigint DEFAULT NULL COMMENT '库位编号',
  `quantity` decimal(10,2) NOT NULL COMMENT '差异数量',
  `taking_quantity` decimal(10,2) NOT NULL COMMENT '盘点数量',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_task_id` (`task_id`) USING BTREE COMMENT '盘点任务编号索引'
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 盘点结果表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_stock_taking_task_result`
--

LOCK TABLES `mes_wm_stock_taking_task_result` WRITE;
/*!40000 ALTER TABLE `mes_wm_stock_taking_task_result` DISABLE KEYS */;
INSERT INTO `mes_wm_stock_taking_task_result` VALUES (1,13,2,NULL,69,NULL,NULL,702,713,724,10.00,0.00,'111','1','2026-03-11 09:18:07','1','2026-03-12 01:05:10',0x01,1),(2,13,2,NULL,69,NULL,NULL,702,713,724,123.00,1.00,'123321321','1','2026-03-12 01:04:53','1','2026-03-12 01:04:55',0x01,1),(3,13,2,NULL,69,NULL,NULL,702,713,724,123.00,5.00,'32312321','1','2026-03-12 01:05:02','1','2026-03-12 01:05:13',0x01,1),(4,13,2,NULL,69,NULL,NULL,702,713,724,123.00,1.00,NULL,'1','2026-03-12 01:05:18','1','2026-03-12 01:05:18',0x00,1),(5,3,3,NULL,69,NULL,NULL,702,713,724,1.00,1.00,NULL,'1','2026-03-12 01:07:04','1','2026-03-12 01:07:04',0x00,1);
/*!40000 ALTER TABLE `mes_wm_stock_taking_task_result` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_transaction`
--

DROP TABLE IF EXISTS `mes_wm_transaction`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_transaction` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `type` int NOT NULL COMMENT '事务类型',
  `biz_type` int DEFAULT NULL COMMENT '业务类型',
  `biz_id` bigint DEFAULT NULL COMMENT '来源业务主单 ID',
  `biz_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源业务单号',
  `biz_line_id` bigint DEFAULT NULL COMMENT '来源业务行 ID',
  `material_stock_id` bigint DEFAULT NULL COMMENT '库存记录 ID',
  `related_transaction_id` bigint DEFAULT NULL COMMENT '关联的事务 ID',
  `item_id` bigint NOT NULL COMMENT '物料 ID',
  `quantity` decimal(14,4) NOT NULL COMMENT '本次变动数量（正数=入库，负数=出库）',
  `batch_id` bigint DEFAULT NULL COMMENT '批次 ID',
  `batch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `warehouse_id` bigint NOT NULL COMMENT '仓库 ID',
  `location_id` bigint DEFAULT NULL COMMENT '库区 ID',
  `area_id` bigint DEFAULT NULL COMMENT '库位 ID',
  `transaction_time` datetime DEFAULT NULL COMMENT '事务发生时间',
  `erp_time` datetime DEFAULT NULL COMMENT 'ERP 账期',
  `receipt_time` datetime DEFAULT NULL COMMENT '入库时间',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE,
  KEY `idx_warehouse_id` (`warehouse_id`) USING BTREE,
  KEY `idx_biz` (`biz_type`,`biz_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9214011 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 库存事务流水';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_transaction`
--

LOCK TABLES `mes_wm_transaction` WRITE;
/*!40000 ALTER TABLE `mes_wm_transaction` DISABLE KEYS */;
INSERT INTO `mes_wm_transaction` VALUES (1,1,114,2,'MRbJ5HI6aOMZ',3,4,NULL,69,123.0000,1,'TEST',702,713,724,'2026-03-22 20:09:34',NULL,NULL,'1','2026-03-22 20:09:34','1','2026-03-30 02:46:05',0x00,1),(2,2,113,4,'MI47CkggsQpp',5,4,NULL,69,-123.0000,1,'TEST',702,713,724,'2026-03-22 20:10:57',NULL,NULL,'1','2026-03-22 20:10:57','1','2026-03-30 02:46:05',0x00,1),(3,1,114,4,'MRlD8OQUII96',5,9,NULL,94,123.0000,7,'BATCH_ITEM_94',702,713,724,'2026-03-22 21:38:40',NULL,NULL,'1','2026-03-22 21:38:40','1','2026-03-30 02:46:05',0x00,1),(5,3,111,9001007,'TRkOGr5InPcE',9001107,9,NULL,94,-1.0000,7,'BATCH_ITEM_94',702,713,724,'2026-03-22 23:11:51',NULL,NULL,'1','2026-03-22 23:11:51','1','2026-03-30 02:46:05',0x00,1),(6,4,112,9001007,'TRkOGr5InPcE',9001107,10,5,94,1.0000,7,'BATCH_ITEM_94',701,712,723,'2026-03-22 23:11:51',NULL,NULL,'1','2026-03-22 23:11:51','1','2026-03-30 02:46:05',0x00,1),(7,2,122,20,'',20,11,NULL,72,-1.0000,8,'BATCH_ITEM_72',703,714,725,'2026-03-24 23:17:24',NULL,NULL,'1','2026-03-24 23:17:24','1','2026-03-30 02:46:05',0x00,1),(8,1,123,19,'',3,12,NULL,75,1.0000,4,'PC202600002',703,714,725,'2026-03-24 23:17:24',NULL,NULL,'1','2026-03-24 23:17:24','1','2026-03-24 23:17:24',0x00,1),(9,1,123,19,'',4,12,NULL,75,1.0000,4,'PC202600002',703,714,725,'2026-03-24 23:17:24',NULL,NULL,'1','2026-03-24 23:17:24','1','2026-03-24 23:17:24',0x00,1),(10,2,122,21,'',21,11,NULL,72,-45.0000,8,'BATCH_ITEM_72',703,714,725,'2026-03-24 23:19:17',NULL,NULL,'1','2026-03-24 23:19:17','1','2026-03-30 02:46:05',0x00,1),(11,1,110,15,'IR20260329000001',19,9,NULL,94,1.0000,7,'BATCH_ITEM_94',702,713,724,'2026-03-29 18:06:09',NULL,NULL,'1','2026-03-29 18:06:09','1','2026-03-30 02:46:05',0x00,1),(12,2,124,7,'RV20260329000011',9,4,NULL,69,-1.0000,1,'TEST',702,713,724,'2026-03-29 23:04:43',NULL,NULL,'1','2026-03-29 23:04:44','1','2026-03-29 23:04:44',0x00,1),(13,2,115,8,'PI20260330000002',14,10,NULL,94,-1.0000,7,'BATCH_ITEM_94',701,712,723,'2026-03-30 11:11:50',NULL,NULL,'1','2026-03-30 11:11:50','1','2026-03-30 11:11:50',0x00,1),(14,1,115,8,'PI20260330000002',14,14,13,94,1.0000,7,'BATCH_ITEM_94',703,714,725,'2026-03-30 11:11:50',NULL,NULL,'1','2026-03-30 11:11:50','1','2026-03-30 11:11:50',0x00,1),(16,2,118,100003,'A0',100002,4,NULL,69,-736.0000,1,'PC',702,713,724,'2026-03-30 21:54:28',NULL,NULL,'1','2026-03-30 21:54:28','1','2026-03-30 21:54:28',0x00,1),(17,1,114,5,'MROO8b4vMA5x',6,9,NULL,94,321321321.0000,NULL,NULL,702,713,724,'2026-03-31 09:41:23',NULL,NULL,'1','2026-03-31 09:41:23','1','2026-03-31 09:41:23',0x00,1),(9190300,1,110,9190000,'IR-HZ-20260430001',9190100,130,NULL,1094,10.0000,1009,'RAW-BATCH-001',704,716,727,'2026-04-30 09:00:00',NULL,NULL,'80370','2026-04-30 09:00:00','80370','2026-04-30 09:00:00',0x00,2010),(9190301,1,110,9190000,'IR-HZ-20260430001',9190101,9213000,NULL,1100,10.0000,1001,'PC',704,716,727,'2026-04-30 09:00:00',NULL,NULL,'80370','2026-04-30 09:00:00','80370','2026-04-30 09:00:00',0x00,2010),(9214000,1,110,9201000,'IR-HZ-20260512001',9202000,9213000,NULL,1100,120.0000,1001,'PC',704,716,727,'2026-05-12 09:00:00',NULL,NULL,'80370','2026-05-12 09:00:00','80370','2026-05-12 09:00:00',0x00,2010),(9214001,1,110,9201001,'IR-HZ-20260520001',9202001,9213001,NULL,1075,300.0000,1003,'PC202600001',704,716,727,'2026-05-20 09:00:00',NULL,NULL,'80370','2026-05-20 09:00:00','80370','2026-05-20 09:00:00',0x00,2010),(9214002,1,110,9201002,'IR-HZ-20260603001',9202002,9213002,NULL,1094,500.0000,1007,'BATCH_ITEM_94',704,716,727,'2026-06-03 09:00:00',NULL,NULL,'80370','2026-06-03 09:00:00','80370','2026-06-03 09:00:00',0x00,2010),(9214003,1,114,9204000,'MR-HZ-20260618001',9205000,9213003,NULL,1072,80.0000,NULL,NULL,704,716,727,'2026-06-18 09:00:00',NULL,NULL,'80370','2026-06-18 09:00:00','80370','2026-06-18 09:00:00',0x00,2010),(9214004,3,111,9210000,'TR-HZ-20260708001',9211000,9213002,NULL,1094,-120.0000,1007,'BATCH_ITEM_94',704,716,727,'2026-07-08 09:00:00',NULL,NULL,'80370','2026-07-08 09:00:00','80370','2026-07-08 09:00:00',0x00,2010),(9214005,4,112,9210000,'TR-HZ-20260708001',9211000,9213005,9214004,1094,120.0000,1007,'BATCH_ITEM_94',705,717,728,'2026-07-08 09:00:00',NULL,NULL,'80370','2026-07-08 09:00:00','80370','2026-07-08 09:00:00',0x00,2010),(9214006,3,111,9210001,'TR-HZ-20260715001',9211001,9213000,NULL,1100,-40.0000,1001,'PC',704,716,727,'2026-07-15 09:00:00',NULL,NULL,'80370','2026-07-15 09:00:00','80370','2026-07-15 09:00:00',0x00,2010),(9214007,4,112,9210001,'TR-HZ-20260715001',9211001,9213007,9214006,1100,40.0000,1001,'PC',705,717,729,'2026-07-15 09:00:00',NULL,NULL,'80370','2026-07-15 09:00:00','80370','2026-07-15 09:00:00',0x00,2010),(9214008,2,113,9207000,'MI-HZ-20260805001',9208000,9213002,NULL,1094,-150.0000,1007,'BATCH_ITEM_94',704,716,727,'2026-08-05 09:00:00',NULL,NULL,'80370','2026-08-05 09:00:00','80370','2026-08-05 09:00:00',0x00,2010),(9214009,2,113,9207001,'MI-HZ-20260820001',9208001,9213005,NULL,1094,-50.0000,1007,'BATCH_ITEM_94',705,717,728,'2026-08-20 09:00:00',NULL,NULL,'80370','2026-08-20 09:00:00','80370','2026-08-20 09:00:00',0x00,2010),(9214010,2,113,9207002,'MI-HZ-20260902001',9208002,9213000,NULL,1100,-30.0000,1001,'PC',704,716,727,'2026-09-02 09:00:00',NULL,NULL,'80370','2026-09-02 09:00:00','80370','2026-09-02 09:00:00',0x00,2010);
/*!40000 ALTER TABLE `mes_wm_transaction` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_transfer`
--

DROP TABLE IF EXISTS `mes_wm_transfer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_transfer` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '调拨单编号',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '调拨单名称',
  `type` tinyint NOT NULL COMMENT '调拨类型',
  `delivery_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否配送',
  `recipient_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '收货人',
  `recipient_telephone` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系电话',
  `destination_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目的地',
  `carrier` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '承运商',
  `shipping_number` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '运输单号',
  `confirm_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否已确认',
  `transfer_date` datetime DEFAULT NULL COMMENT '调拨日期',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`code`) USING BTREE,
  KEY `idx_transfer_date` (`transfer_date`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_type` (`type`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9210002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 调拨单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_transfer`
--

LOCK TABLES `mes_wm_transfer` WRITE;
/*!40000 ALTER TABLE `mes_wm_transfer` DISABLE KEYS */;
INSERT INTO `mes_wm_transfer` VALUES (9001001,'TR202603080001','内部调拨测试单',1,0x00,NULL,NULL,NULL,NULL,NULL,0x00,'2026-03-08 11:55:25',2,'内部调拨测试数据','1','2026-03-08 11:55:25','1','2026-03-08 11:55:25',0x00,1),(9001002,'TR202603080002','外部调拨测试单',2,0x01,'张三','13800000000','苏州市工业园区测试地址','顺丰','SF202603080001',0x01,'2026-03-08 11:55:25',3,'外部调拨测试数据','1','2026-03-08 11:55:25','1','2026-03-08 11:55:25',0x00,1),(9001003,'TRUYLDMn9kCH','呃呃呃',1,0x00,NULL,NULL,NULL,NULL,NULL,0x00,'1970-01-01 08:00:00',0,'','1','2026-03-08 20:17:07','1','2026-03-08 20:17:07',0x00,1),(9001004,'TRhmRNkKyIfO','111',2,0x01,'12','321','3123231',NULL,NULL,0x01,'1970-01-01 08:00:00',2,'','1','2026-03-08 21:58:04','1','2026-03-08 22:27:14',0x00,1),(9001005,'TRALcyZ45UwL','XXX01',2,0x01,'32132','3213213','321321321',NULL,NULL,0x01,'1970-01-01 08:00:00',4,'','1','2026-03-08 21:58:32','1','2026-03-08 21:58:32',0x00,1),(9001006,'TRiwBTVEp5un','XXX',1,0x00,NULL,NULL,NULL,NULL,NULL,0x00,'1970-01-01 08:00:00',3,'','1','2026-03-08 22:24:06','1','2026-03-08 22:24:06',0x00,1),(9001007,'TRkOGr5InPcE','123',1,0x00,NULL,NULL,NULL,NULL,NULL,0x00,'1970-01-01 08:00:00',4,'321321','1','2026-03-22 23:11:15','1','2026-03-22 23:11:31',0x00,1),(9210000,'TR-HZ-20260708001','库内调拨单',1,0x00,NULL,NULL,NULL,NULL,NULL,0x01,'2026-07-08 09:00:00',4,'仓库测试数据','80370','2026-07-08 09:00:00','80370','2026-07-08 09:00:00',0x00,2010),(9210001,'TR-HZ-20260715001','库内调拨单',1,0x00,NULL,NULL,NULL,NULL,NULL,0x01,'2026-07-15 09:00:00',4,'仓库测试数据','80370','2026-07-15 09:00:00','80370','2026-07-15 09:00:00',0x00,2010);
/*!40000 ALTER TABLE `mes_wm_transfer` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_transfer_detail`
--

DROP TABLE IF EXISTS `mes_wm_transfer_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_transfer_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `line_id` bigint NOT NULL COMMENT '调拨单行编号（关联 mes_wm_transfer_line.id）',
  `transfer_id` bigint NOT NULL COMMENT '调拨单编号（关联 mes_wm_transfer.id）',
  `item_id` bigint NOT NULL COMMENT '物料编号（关联 mes_md_item.id）',
  `quantity` decimal(14,2) DEFAULT NULL COMMENT '调拨数量',
  `batch_id` bigint DEFAULT NULL COMMENT '批次编号',
  `to_warehouse_id` bigint DEFAULT NULL COMMENT '移入仓库编号（关联 mes_wm_warehouse.id）',
  `to_location_id` bigint DEFAULT NULL COMMENT '移入库区编号（关联 mes_wm_warehouse_location.id）',
  `to_area_id` bigint DEFAULT NULL COMMENT '移入库位编号（关联 mes_wm_warehouse_area.id）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_line_id` (`line_id`) USING BTREE,
  KEY `idx_transfer_id` (`transfer_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9212002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 调拨明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_transfer_detail`
--

LOCK TABLES `mes_wm_transfer_detail` WRITE;
/*!40000 ALTER TABLE `mes_wm_transfer_detail` DISABLE KEYS */;
INSERT INTO `mes_wm_transfer_detail` VALUES (9001201,9001101,9001001,20001,6.00,NULL,30003,31003,32004,'内部调拨明细1','1','2026-03-08 11:55:25','1','2026-03-08 11:55:25',0x00,1),(9001202,9001101,9001001,20001,4.00,NULL,30003,31003,32005,'内部调拨明细2','1','2026-03-08 11:55:25','1','2026-03-08 11:55:25',0x00,1),(9001203,9001103,9001002,20003,8.00,NULL,30004,31004,32006,'外部调拨明细1','1','2026-03-08 11:55:25','1','2026-03-08 11:55:25',0x00,1),(9001204,9001104,9001005,69,10.00,NULL,702,713,724,'','1','2026-03-08 22:20:13','1','2026-03-08 22:20:13',0x00,1),(9001205,9001106,9001004,69,1.00,NULL,702,713,724,'','1','2026-03-08 22:32:06','1','2026-03-08 22:32:06',0x00,1),(9001206,9001105,9001006,69,10.00,NULL,702,713,724,'','1','2026-03-22 23:10:59','1','2026-03-22 23:10:59',0x00,1),(9001207,9001107,9001007,94,1.00,NULL,701,712,723,'','1','2026-03-22 23:11:47','1','2026-03-22 23:11:47',0x00,1),(9212000,9211000,9210000,1094,120.00,NULL,705,717,728,'','80370','2026-07-08 09:00:00','80370','2026-07-08 09:00:00',0x00,2010),(9212001,9211001,9210001,1100,40.00,NULL,705,717,729,'','80370','2026-07-15 09:00:00','80370','2026-07-15 09:00:00',0x00,2010);
/*!40000 ALTER TABLE `mes_wm_transfer_detail` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_transfer_line`
--

DROP TABLE IF EXISTS `mes_wm_transfer_line`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_transfer_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `transfer_id` bigint NOT NULL COMMENT '调拨单编号（关联 mes_wm_transfer.id）',
  `material_stock_id` bigint DEFAULT NULL COMMENT '来源库存记录编号（关联 mes_wm_material_stock.id）',
  `item_id` bigint NOT NULL COMMENT '物料编号（关联 mes_md_item.id）',
  `quantity` decimal(14,2) DEFAULT NULL COMMENT '调拨数量',
  `batch_id` bigint DEFAULT NULL COMMENT '批次编号',
  `from_warehouse_id` bigint DEFAULT NULL COMMENT '移出仓库编号（关联 mes_wm_warehouse.id）',
  `from_location_id` bigint DEFAULT NULL COMMENT '移出库区编号（关联 mes_wm_warehouse_location.id）',
  `from_area_id` bigint DEFAULT NULL COMMENT '移出库位编号（关联 mes_wm_warehouse_area.id）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_transfer_id` (`transfer_id`) USING BTREE,
  KEY `idx_material_stock_id` (`material_stock_id`) USING BTREE,
  KEY `idx_item_id` (`item_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9211002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 调拨单行';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_transfer_line`
--

LOCK TABLES `mes_wm_transfer_line` WRITE;
/*!40000 ALTER TABLE `mes_wm_transfer_line` DISABLE KEYS */;
INSERT INTO `mes_wm_transfer_line` VALUES (9001101,9001001,10001,20001,10.00,NULL,30001,31001,32001,'内部调拨行1','1','2026-03-08 11:55:25','1','2026-03-08 11:55:25',0x00,1),(9001102,9001001,10002,20002,5.00,NULL,30001,31001,32002,'内部调拨行2','1','2026-03-08 11:55:25','1','2026-03-08 11:55:25',0x00,1),(9001103,9001002,10003,20003,8.00,NULL,30002,31002,32003,'外部调拨行1','1','2026-03-08 11:55:25','1','2026-03-08 11:55:25',0x00,1),(9001104,9001005,NULL,69,10.00,NULL,702,713,724,'12323','1','2026-03-08 22:08:47','1','2026-03-08 22:08:47',0x00,1),(9001105,9001006,NULL,69,10.00,NULL,702,713,724,'12323','1','2026-03-08 22:24:16','1','2026-03-08 22:24:16',0x00,1),(9001106,9001004,NULL,69,10.00,NULL,702,713,724,'','1','2026-03-08 22:27:12','1','2026-03-08 22:27:12',0x00,1),(9001107,9001007,NULL,94,1.00,NULL,702,713,724,'123','1','2026-03-22 23:11:29','1','2026-03-22 23:11:29',0x00,1),(9001108,9001003,1,70,500.00,5,701,711,721,'','1','2026-04-07 00:31:31','1','2026-04-07 00:31:31',0x00,1),(9211000,9210000,9213002,1094,120.00,1007,704,716,727,'','80370','2026-07-08 09:00:00','80370','2026-07-08 09:00:00',0x00,2010),(9211001,9210001,9213000,1100,40.00,1001,704,716,727,'','80370','2026-07-15 09:00:00','80370','2026-07-15 09:00:00',0x00,2010);
/*!40000 ALTER TABLE `mes_wm_transfer_line` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_warehouse`
--

DROP TABLE IF EXISTS `mes_wm_warehouse`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_warehouse` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '仓库编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '仓库名称',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '仓库地址',
  `area` decimal(14,2) DEFAULT NULL COMMENT '仓库面积（平方米）',
  `charge_user_id` bigint DEFAULT NULL COMMENT '负责人用户编号（system_users.id）',
  `frozen` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否冻结',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`code`) USING BTREE,
  UNIQUE KEY `uk_tenant_name` (`tenant_id`,`name`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=706 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 仓库主数据表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_warehouse`
--

LOCK TABLES `mes_wm_warehouse` WRITE;
/*!40000 ALTER TABLE `mes_wm_warehouse` DISABLE KEYS */;
INSERT INTO `mes_wm_warehouse` VALUES (701,'WH-RAW','原料仓','A 区 1 号',1200.00,1,0,'原材料存放仓','1','2026-02-17 13:45:34','1','2026-02-17 13:45:34',0x00,1),(702,'WH-FIN','成品仓','B 区 2 号',900.00,1,0,'成品发运仓','1','2026-02-17 13:45:34','1','2026-03-28 20:16:59',0x00,1),(703,'WIP_VIRTUAL_WAREHOUSE','虚拟线边仓库',NULL,NULL,NULL,0,'系统自动初始化的虚拟线边仓库（用于生产报工与在制品管理解耦）','1','2026-03-24 23:17:24','1','2026-03-24 23:17:24',0x00,1),(704,'WH-HZ-RAW','原料仓','华瀚厂区',NULL,NULL,0,'污染监控演示用仓库','system','2026-09-10 08:41:49','','2026-09-10 08:41:49',0x00,2010),(705,'WH-HZ-HAZ','危废暂存仓','华瀚厂区东侧',NULL,NULL,0,'受控仓：与普通仓物理隔离','system','2026-09-10 08:41:49','','2026-09-10 08:41:49',0x00,2010);
/*!40000 ALTER TABLE `mes_wm_warehouse` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_warehouse_area`
--

DROP TABLE IF EXISTS `mes_wm_warehouse_area`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_warehouse_area` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '库位编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '库位名称',
  `location_id` bigint NOT NULL COMMENT '所属库区编号（mes_wm_warehouse_location.id）',
  `area` decimal(14,2) DEFAULT NULL COMMENT '库位面积（平方米）',
  `max_load` decimal(14,2) DEFAULT NULL COMMENT '最大载重',
  `position_x` int DEFAULT NULL COMMENT '库位位置 X',
  `position_y` int DEFAULT NULL COMMENT '库位位置 Y',
  `position_z` int DEFAULT NULL COMMENT '库位位置 Z',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `frozen` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否冻结',
  `allow_item_mixing` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否允许物料混放',
  `allow_batch_mixing` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否允许批次混放',
  `pollution_control` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否污染管控库位（1=受控/危废暂存，污染品只能入此处，普通品不得占用）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  `storage_zone` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'GENERAL' COMMENT '储存专区：GENERAL 一般区 / EXPLOSION_PROOF 防爆区 / ISOLATION 隔离区。与 pollution_control 相互独立，同一库位可既是受控库位又是防爆区',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_location_code` (`tenant_id`,`location_id`,`code`) USING BTREE,
  UNIQUE KEY `uk_tenant_location_name` (`tenant_id`,`location_id`,`name`) USING BTREE,
  KEY `idx_location_id` (`location_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=8000 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 库位主数据表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_warehouse_area`
--

LOCK TABLES `mes_wm_warehouse_area` WRITE;
/*!40000 ALTER TABLE `mes_wm_warehouse_area` DISABLE KEYS */;
INSERT INTO `mes_wm_warehouse_area` VALUES (721,'AREA-RAW-A-01','原料A-01',711,20.00,1000.00,1,1,1,0,0,1,1,0,'','1','2026-02-17 13:45:34','1','2026-02-17 13:45:34',0x00,1,'GENERAL'),(722,'AREA-RAW-A-02','原料A-02',711,20.00,1000.00,1,2,1,0,0,1,1,0,'','1','2026-02-17 13:45:34','1','2026-02-17 13:45:34',0x00,1,'GENERAL'),(723,'AREA-RAW-B-01','原料B-01',712,25.00,1200.00,2,1,1,0,0,1,1,0,'','1','2026-02-17 13:45:34','1','2026-02-17 13:45:34',0x00,1,'GENERAL'),(724,'AREA-FIN-A-01','成品A-01',713,30.00,1500.00,3,1,1,0,0,1,1,1,'','1','2026-02-17 13:45:34','1','2026-09-10 08:41:49',0x00,1,'GENERAL'),(725,'WIP_VIRTUAL_AREA','虚拟线边库位',714,NULL,NULL,NULL,NULL,NULL,0,0,1,1,0,'系统自动初始化的虚拟线边库位','1','2026-03-24 23:17:24','1','2026-03-28 23:26:41',0x00,1,'GENERAL'),(726,'AREA-RAW-A-03','原料A-03',711,18.00,1200.00,1,1,3,0,0,1,1,0,'文档截图测试数据','admin','2026-04-15 11:02:42','admin','2026-04-15 11:02:42',0x00,1,'GENERAL'),(727,'AREA-HZ-RAW-A-01','原料A-01',716,NULL,NULL,NULL,NULL,NULL,0,0,1,1,0,'普通库位','system','2026-09-10 08:41:49','','2026-09-20 17:00:44',0x00,2010,'GENERAL'),(728,'AREA-HZ-HAZ-01','危废暂存间A-01',717,NULL,NULL,NULL,NULL,NULL,0,0,0,0,1,'受控库位：危废暂存','system','2026-09-10 08:41:49','','2026-09-10 08:41:49',0x00,2010,'GENERAL'),(729,'AREA-HZ-HAZ-02','危废暂存间A-02',717,NULL,NULL,NULL,NULL,NULL,0,0,0,0,1,'受控库位：危废暂存','system','2026-09-10 08:41:49','','2026-09-10 08:41:49',0x00,2010,'GENERAL'),(730,'AREA-HZ-HAZ-03','危废暂存间C-03',717,NULL,NULL,NULL,NULL,NULL,0,0,0,0,1,'受控库位：危废暂存','system','2026-09-10 08:41:49','','2026-09-10 08:41:49',0x00,2010,'GENERAL');
/*!40000 ALTER TABLE `mes_wm_warehouse_area` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mes_wm_warehouse_location`
--

DROP TABLE IF EXISTS `mes_wm_warehouse_location`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mes_wm_warehouse_location` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '库区编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '库区名称',
  `warehouse_id` bigint NOT NULL COMMENT '所属仓库编号（mes_wm_warehouse.id）',
  `area` decimal(14,2) DEFAULT NULL COMMENT '库区面积（平方米）',
  `frozen` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否冻结',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_warehouse_code` (`tenant_id`,`warehouse_id`,`code`) USING BTREE,
  UNIQUE KEY `uk_tenant_warehouse_name` (`tenant_id`,`warehouse_id`,`name`) USING BTREE,
  KEY `idx_warehouse_id` (`warehouse_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=718 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES 库区主数据表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mes_wm_warehouse_location`
--

LOCK TABLES `mes_wm_warehouse_location` WRITE;
/*!40000 ALTER TABLE `mes_wm_warehouse_location` DISABLE KEYS */;
INSERT INTO `mes_wm_warehouse_location` VALUES (711,'LOC-RAW-A','原料 A 区',701,400.00,0,'大宗原料区','1','2026-02-17 13:45:34','1','2026-02-17 13:45:34',0x00,1),(712,'LOC-RAW-B','原料 B 区',701,300.00,0,'小料区','1','2026-02-17 13:45:34','1','2026-02-17 13:45:34',0x00,1),(713,'LOC-FIN-A','成品 A 区',702,500.00,0,'待发货区','1','2026-02-17 13:45:34','1','2026-02-17 13:45:34',0x00,1),(714,'WIP_VIRTUAL_LOCATION','虚拟线边库区',703,NULL,0,'系统自动初始化的虚拟线边库区','1','2026-03-24 23:17:24','1','2026-03-24 23:17:24',0x00,1),(715,'LOC-RAW-C','原料 C 区',701,420.00,0,'文档截图测试数据','admin','2026-04-15 11:02:42','admin','2026-04-15 11:02:42',0x00,1),(716,'LOC-HZ-RAW-A','原料 A 区',704,NULL,0,'普通库区','system','2026-09-10 08:41:49','','2026-09-10 08:41:49',0x00,2010),(717,'LOC-HZ-HAZ','危废暂存区',705,NULL,0,'受控库区：仅收污染/危废品','system','2026-09-10 08:41:49','','2026-09-10 08:41:49',0x00,2010);
/*!40000 ALTER TABLE `mes_wm_warehouse_location` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-20 17:07:41

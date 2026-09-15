-- ============================================================`r`n-- 商机状态组与阶段配置数据（crm_business_status_type / crm_business_status）`r`n-- 来源：2026-09-15 手工配置的最终版数据导出`r`n-- 状态组：201 油气管道防腐销售流程 / 202 市政保温工程流程 / 203 检测技术服务流程`r`n-- 说明：先清理 id>=201 的旧数据再写入，保证各环境完全一致，可重复执行`r`n-- ============================================================`r`n`r`nDELETE FROM crm_business_status WHERE type_id >= 201;`r`nDELETE FROM crm_business_status_type WHERE id >= 201;`r`n`r`n-- MySQL dump 10.13  Distrib 8.4.11, for Linux (x86_64)
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
-- Dumping data for table `crm_business_status_type`
--
-- WHERE:  id>=201

LOCK TABLES `crm_business_status_type` WRITE;
/*!40000 ALTER TABLE `crm_business_status_type` DISABLE KEYS */;
REPLACE INTO `crm_business_status_type` VALUES (201,'油气管道防腐销售流程','','1','2026-09-09 12:00:00','1','2026-09-09 11:11:29',2012,_binary '\0');
REPLACE INTO `crm_business_status_type` VALUES (202,'市政保温工程流程','','1','2026-09-09 12:00:00','1','2026-09-09 11:11:29',2012,_binary '\0');
REPLACE INTO `crm_business_status_type` VALUES (203,'检测技术服务流程','','1','2026-09-09 12:00:00','1','2026-09-09 11:11:29',2012,_binary '\0');
/*!40000 ALTER TABLE `crm_business_status_type` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-15 10:52:53
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
-- Dumping data for table `crm_business_status`
--
-- WHERE:  type_id>=201

LOCK TABLES `crm_business_status` WRITE;
/*!40000 ALTER TABLE `crm_business_status` DISABLE KEYS */;
REPLACE INTO `crm_business_status` VALUES (2011,201,'初步接洽',10.000000,0,'1','2026-09-09 12:00:00','1','2026-09-09 12:00:00',2012,_binary '\0');
REPLACE INTO `crm_business_status` VALUES (2012,201,'需求确认',40.000000,1,'1','2026-09-09 12:00:00','1','2026-09-10 09:01:29',2012,_binary '\0');
REPLACE INTO `crm_business_status` VALUES (2013,201,'方案评估',60.000000,2,'1','2026-09-09 12:00:00','1','2026-09-10 09:01:29',2012,_binary '\0');
REPLACE INTO `crm_business_status` VALUES (2014,201,'商务谈判',70.000000,3,'1','2026-09-09 12:00:00','1','2026-09-09 12:00:00',2012,_binary '\0');
REPLACE INTO `crm_business_status` VALUES (2015,201,'赢单',100.000000,4,'1','2026-09-09 12:00:00','1','2026-09-09 12:00:00',2012,_binary '\0');
REPLACE INTO `crm_business_status` VALUES (2016,201,'输单',0.000000,5,'1','2026-09-09 12:00:00','1','2026-09-10 10:08:21',2012,_binary '\0');
REPLACE INTO `crm_business_status` VALUES (2021,202,'初步接洽',10.000000,0,'1','2026-09-09 12:00:00','1','2026-09-09 12:00:00',2012,_binary '\0');
REPLACE INTO `crm_business_status` VALUES (2022,202,'方案报价',40.000000,1,'1','2026-09-09 12:00:00','1','2026-09-09 12:00:00',2012,_binary '\0');
REPLACE INTO `crm_business_status` VALUES (2023,202,'商务谈判',70.000000,2,'1','2026-09-09 12:00:00','1','2026-09-09 12:00:00',2012,_binary '\0');
REPLACE INTO `crm_business_status` VALUES (2024,202,'赢单',100.000000,3,'1','2026-09-09 12:00:00','1','2026-09-09 12:00:00',2012,_binary '\0');
REPLACE INTO `crm_business_status` VALUES (2025,202,'输单',0.000000,4,'1','2026-09-09 12:00:00','1','2026-09-10 10:08:21',2012,_binary '\0');
REPLACE INTO `crm_business_status` VALUES (2031,203,'需求沟通',20.000000,0,'1','2026-09-09 12:00:00','1','2026-09-09 12:00:00',2012,_binary '\0');
REPLACE INTO `crm_business_status` VALUES (2032,203,'方案报价',60.000000,1,'1','2026-09-09 12:00:00','1','2026-09-09 12:00:00',2012,_binary '\0');
REPLACE INTO `crm_business_status` VALUES (2033,203,'赢单',100.000000,2,'1','2026-09-09 12:00:00','1','2026-09-09 12:00:00',2012,_binary '\0');
REPLACE INTO `crm_business_status` VALUES (2034,203,'输单',0.000000,3,'1','2026-09-09 12:00:00','1','2026-09-10 10:08:21',2012,_binary '\0');
/*!40000 ALTER TABLE `crm_business_status` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-15 10:52:53

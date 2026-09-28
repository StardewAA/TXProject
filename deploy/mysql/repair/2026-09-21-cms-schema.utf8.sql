
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
DROP TABLE IF EXISTS `cms_ai_chat_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cms_ai_chat_message` (
  `message_id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `role` varchar(16) NOT NULL COMMENT 'user/assistant/system',
  `content` text NOT NULL,
  `references` json DEFAULT NULL COMMENT '引用来源：[{"type":"standard","id":1,"no":"GB 50264"}]',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`message_id`),
  KEY `idx_session` (`session_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI对话消息';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cms_ai_chat_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cms_ai_chat_session` (
  `session_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `session_title` varchar(128) DEFAULT NULL COMMENT '会话标题（取第一条问题前20字）',
  `message_count` int NOT NULL DEFAULT '0',
  `last_message_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`session_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI对话会话';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cms_ai_faq`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cms_ai_faq` (
  `faq_id` bigint NOT NULL AUTO_INCREMENT,
  `question` varchar(512) NOT NULL,
  `answer` text NOT NULL,
  `category` varchar(64) DEFAULT NULL COMMENT '分类：政策/标准/材料/施工/合规',
  `priority` int NOT NULL DEFAULT '5' COMMENT '优先级1-10，越高越优先返回',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '0禁用 1启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`faq_id`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI人工FAQ';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cms_ai_knowledge_chunk`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cms_ai_knowledge_chunk` (
  `chunk_id` bigint NOT NULL AUTO_INCREMENT,
  `source_id` bigint NOT NULL COMMENT '关联知识源',
  `content` text NOT NULL COMMENT '切片文本内容',
  `chunk_index` int NOT NULL COMMENT '切片序号',
  `page_num` int DEFAULT NULL COMMENT '所在页码',
  `chapter_title` varchar(256) DEFAULT NULL COMMENT '所属章节标题',
  `metadata` json DEFAULT NULL COMMENT '扩展元数据：{"standardNo":"","section":""}',
  `token_count` int NOT NULL DEFAULT '0' COMMENT 'token数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`chunk_id`),
  KEY `idx_source_id` (`source_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI知识切片';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cms_ai_knowledge_source`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cms_ai_knowledge_source` (
  `source_id` bigint NOT NULL AUTO_INCREMENT,
  `source_type` tinyint NOT NULL COMMENT '类型：1文档上传 2人工FAQ 3爬虫政策 4标准自动导入',
  `title` varchar(256) NOT NULL COMMENT '知识源标题',
  `file_id` bigint DEFAULT NULL COMMENT '附件ID（文档类型）',
  `standard_no` varchar(64) DEFAULT NULL COMMENT '关联标准号（标准类型）',
  `chunk_count` int NOT NULL DEFAULT '0' COMMENT '切片数量',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0待解析 1解析中 2已就绪 3解析失败',
  `error_msg` varchar(1024) DEFAULT NULL COMMENT '失败原因',
  `parse_progress` tinyint NOT NULL DEFAULT '0' COMMENT '解析进度0-100',
  `create_by` bigint NOT NULL,
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`source_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI知识源';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cms_category_dict`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cms_category_dict` (
  `dict_id` bigint NOT NULL AUTO_INCREMENT,
  `dict_type` varchar(64) NOT NULL COMMENT '字典类型：industry_field/protect_object/scene/material_category',
  `dict_code` varchar(64) NOT NULL COMMENT '字典编码',
  `dict_name` varchar(128) NOT NULL COMMENT '字典名称',
  `parent_id` bigint DEFAULT NULL COMMENT '父级ID（支持两级）',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `remark` varchar(512) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '0禁用 1启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`dict_id`),
  UNIQUE KEY `uk_type_code` (`dict_type`,`dict_code`)
) ENGINE=InnoDB AUTO_INCREMENT=37 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CMS分类字典';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cms_crawler_raw_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cms_crawler_raw_data` (
  `raw_id` bigint NOT NULL AUTO_INCREMENT,
  `source_id` bigint NOT NULL,
  `data_type` varchar(32) NOT NULL,
  `title` varchar(512) NOT NULL,
  `content` longtext,
  `source_url` varchar(512) DEFAULT NULL,
  `publish_date` date DEFAULT NULL,
  `crawl_time` datetime DEFAULT NULL,
  `raw_data` json DEFAULT NULL COMMENT '原始采集数据JSON',
  `audit_status` tinyint NOT NULL DEFAULT '0' COMMENT '0待审核 1通过 2驳回',
  `audit_remark` varchar(512) DEFAULT NULL,
  `audit_by` bigint DEFAULT NULL,
  `audit_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`raw_id`),
  KEY `idx_audit` (`audit_status`),
  KEY `idx_source` (`source_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='爬虫原始数据（待审核）';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cms_crawler_source`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cms_crawler_source` (
  `source_id` bigint NOT NULL AUTO_INCREMENT,
  `source_name` varchar(128) NOT NULL COMMENT '采集源名称',
  `spider_name` varchar(64) NOT NULL COMMENT '对应Python爬虫名称',
  `target_url` varchar(512) NOT NULL COMMENT '目标网站URL',
  `data_type` varchar(32) NOT NULL COMMENT '数据类型：policy/standard/material/quota',
  `cron_expr` varchar(64) NOT NULL DEFAULT '0 0 2 * * ?' COMMENT '执行cron表达式',
  `pages` int NOT NULL DEFAULT '3' COMMENT '每次爬取页数',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '0禁用 1启用',
  `last_run_time` datetime DEFAULT NULL COMMENT '上次执行时间',
  `last_run_status` tinyint DEFAULT NULL COMMENT '上次状态：1成功 2失败',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`source_id`),
  UNIQUE KEY `uk_spider_name` (`spider_name`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='爬虫采集源配置';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cms_crawler_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cms_crawler_task` (
  `task_id` bigint NOT NULL AUTO_INCREMENT,
  `source_id` bigint NOT NULL,
  `start_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end_time` datetime DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0运行中 1成功 2失败',
  `success_count` int NOT NULL DEFAULT '0' COMMENT '成功采集数',
  `fail_count` int NOT NULL DEFAULT '0' COMMENT '失败数',
  `error_msg` varchar(2000) DEFAULT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`task_id`),
  KEY `idx_source` (`source_id`,`start_time`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='爬虫任务执行记录';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cms_download_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cms_download_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `content_type` varchar(32) NOT NULL COMMENT 'standard/material/process/quota/policy',
  `content_id` bigint NOT NULL,
  `file_id` bigint NOT NULL,
  `ip_address` varchar(64) DEFAULT NULL,
  `download_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`log_id`),
  KEY `idx_user_date` (`user_id`,`download_time`),
  KEY `idx_content` (`content_type`,`content_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='下载日志';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cms_material`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cms_material` (
  `material_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `material_name` varchar(128) NOT NULL COMMENT '材料名称',
  `material_category` tinyint NOT NULL COMMENT '大类：1无机保温 2有机保温 3防腐涂料 4衬里材料 5防腐管件 6预制制品',
  `material_subcategory` varchar(64) NOT NULL COMMENT '子类，如岩棉、聚氨酯',
  `brand` varchar(64) DEFAULT NULL COMMENT '品牌',
  `manufacturer` varchar(128) DEFAULT NULL COMMENT '生产厂家',
  `density` decimal(10,2) DEFAULT NULL COMMENT '密度 kg/m³',
  `thermal_conductivity` decimal(10,4) DEFAULT NULL COMMENT '导热系数 W/(m·K)',
  `temp_min` int DEFAULT NULL COMMENT '最低耐温 ℃',
  `temp_max` int DEFAULT NULL COMMENT '最高耐温 ℃',
  `compressive_strength` decimal(10,2) DEFAULT NULL COMMENT '抗压强度 MPa',
  `adhesion` decimal(10,2) DEFAULT NULL COMMENT '附着力 MPa',
  `corrosion_resistance` varchar(256) DEFAULT NULL COMMENT '耐腐蚀性能描述',
  `combustion_grade` varchar(32) DEFAULT NULL COMMENT '燃烧等级：A级不燃/B1级难燃',
  `service_life` varchar(64) DEFAULT NULL COMMENT '使用寿命',
  `applicable_condition` text NOT NULL COMMENT '适用工况描述',
  `industry_field` json NOT NULL COMMENT '适用领域数组',
  `protect_object` json NOT NULL COMMENT '防护对象数组',
  `construction_process` text COMMENT '施工工艺要求（付费可见）',
  `inspection_standard` varchar(256) DEFAULT NULL COMMENT '检测标准编号',
  `inspection_report_id` bigint DEFAULT NULL COMMENT '检测报告附件ID（付费可见）',
  `advantages` text COMMENT '优点',
  `disadvantages` text COMMENT '缺点',
  `recommend_scene` text COMMENT '推荐应用场景',
  `reference_price` decimal(10,2) DEFAULT NULL COMMENT '参考市场价',
  `price_unit` varchar(32) DEFAULT NULL COMMENT '价格单位',
  `price_update_time` date DEFAULT NULL COMMENT '价格更新时间',
  `product_images` json DEFAULT NULL COMMENT '产品图片URL数组',
  `product_manual_id` bigint DEFAULT NULL COMMENT '产品说明书附件ID',
  `related_standard_ids` json DEFAULT NULL COMMENT '关联标准ID数组',
  `view_count` int NOT NULL DEFAULT '0',
  `collect_count` int NOT NULL DEFAULT '0',
  `audit_status` tinyint NOT NULL DEFAULT '3',
  `audit_remark` varchar(512) DEFAULT NULL,
  `publish_status` tinyint NOT NULL DEFAULT '0',
  `publish_time` datetime DEFAULT NULL,
  `create_by` bigint NOT NULL,
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`material_id`),
  KEY `idx_category` (`material_category`),
  KEY `idx_temp_range` (`temp_min`,`temp_max`),
  KEY `idx_publish` (`publish_status`,`audit_status`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='材料参数数据库';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cms_policy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cms_policy` (
  `policy_id` bigint NOT NULL AUTO_INCREMENT COMMENT '政策ID',
  `content_type` varchar(16) NOT NULL DEFAULT '金融政策' COMMENT '内容类型：法律法规/金融政策',
  `title` varchar(256) NOT NULL COMMENT '政策标题',
  `policy_level` tinyint DEFAULT NULL COMMENT '层级：1国家 2省 3市 4区县',
  `publish_dept` varchar(128) DEFAULT NULL COMMENT '发布部门',
  `category` varchar(64) DEFAULT NULL COMMENT '主题分类',
  `effective_status` varchar(20) NOT NULL DEFAULT '现行有效' COMMENT '效力状态：现行有效/已修订/已废止/即将失效',
  `publish_date` date DEFAULT NULL COMMENT '发布日期',
  `effective_date` date DEFAULT NULL COMMENT '实施日期',
  `implement_date` date DEFAULT NULL COMMENT '实施日期',
  `content` longtext COMMENT '政策正文内容',
  `summary` varchar(500) DEFAULT NULL COMMENT '内容摘要（列表用，不含全文）',
  `source_url` varchar(512) DEFAULT NULL COMMENT '原文链接',
  `source_name` varchar(128) DEFAULT NULL COMMENT '来源网站名称',
  `attachment_urls` json DEFAULT NULL COMMENT '附件URL列表',
  `view_count` int NOT NULL DEFAULT '0' COMMENT '浏览量',
  `download_count` int NOT NULL DEFAULT '0' COMMENT '下载量',
  `is_top` tinyint NOT NULL DEFAULT '0' COMMENT '是否置顶：0否 1是',
  `audit_status` tinyint NOT NULL DEFAULT '3' COMMENT '审核：0待审 1通过 2驳回 3草稿',
  `audit_remark` varchar(512) DEFAULT NULL COMMENT '审核备注（驳回原因）',
  `publish_status` tinyint NOT NULL DEFAULT '0' COMMENT '发布：0未发布 1已发布 2已下架',
  `publish_time` datetime DEFAULT NULL COMMENT '发布时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID（爬虫采集为NULL）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常 1删除',
  `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`policy_id`),
  KEY `idx_level` (`policy_level`),
  KEY `idx_publish_date` (`publish_date`),
  KEY `idx_publish` (`publish_status`,`audit_status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=70 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='政策资讯';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cms_process_resource`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cms_process_resource` (
  `resource_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title` varchar(256) NOT NULL COMMENT '资源名称',
  `resource_type` tinyint NOT NULL COMMENT '类型：1施工工序 2技术交底模板 3通病防治 4优秀案例 5不合格对比 6整改方案',
  `industry_field` json NOT NULL COMMENT '适用领域数组',
  `protect_object` json NOT NULL COMMENT '防护对象数组',
  `scene` json NOT NULL COMMENT '施工场景数组',
  `difficulty` tinyint NOT NULL COMMENT '难度：1入门 2初级 3中级 4高级 5专家',
  `process_steps` json DEFAULT NULL COMMENT '工序步骤数组：[{"step":"","point":"","quality":"","image":""}]',
  `content` longtext NOT NULL COMMENT '正文富文本HTML',
  `video_id` bigint DEFAULT NULL COMMENT '配套视频ID',
  `attachment_ids` json DEFAULT NULL COMMENT '附件模板ID数组（付费下载）',
  `related_standard_ids` json DEFAULT NULL COMMENT '关联标准ID数组',
  `related_material_ids` json DEFAULT NULL COMMENT '关联材料ID数组',
  `source` varchar(128) DEFAULT NULL COMMENT '来源：企业投稿/运营原创/专家供稿',
  `author` varchar(64) DEFAULT NULL COMMENT '作者',
  `is_free` tinyint NOT NULL DEFAULT '1' COMMENT '是否免费：0付费 1免费',
  `view_count` int NOT NULL DEFAULT '0',
  `like_count` int NOT NULL DEFAULT '0',
  `collect_count` int NOT NULL DEFAULT '0',
  `audit_status` tinyint NOT NULL DEFAULT '3',
  `audit_remark` varchar(512) DEFAULT NULL,
  `publish_status` tinyint NOT NULL DEFAULT '0',
  `publish_time` datetime DEFAULT NULL,
  `create_by` bigint NOT NULL,
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`resource_id`),
  KEY `idx_type` (`resource_type`),
  KEY `idx_difficulty` (`difficulty`),
  KEY `idx_publish` (`publish_status`,`audit_status`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工艺工法与案例库';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cms_quota`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cms_quota` (
  `quota_id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(256) NOT NULL COMMENT '资料名称',
  `quota_type` tinyint NOT NULL COMMENT '类别：1资质要求 2施工定额 3人工单价 4材料市场价 5规费标准 6报验流程 7资料模板',
  `region` varchar(64) NOT NULL COMMENT '适用地区，全国为"全国"',
  `publish_date` date NOT NULL COMMENT '发布时间',
  `valid_date` date DEFAULT NULL COMMENT '有效期至',
  `file_id` bigint DEFAULT NULL COMMENT '文件附件ID',
  `description` text COMMENT '说明备注',
  `is_free` tinyint NOT NULL DEFAULT '1',
  `audit_status` tinyint NOT NULL DEFAULT '3',
  `audit_remark` varchar(512) DEFAULT NULL COMMENT '审核备注（驳回原因）',
  `publish_status` tinyint NOT NULL DEFAULT '0',
  `publish_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`quota_id`),
  KEY `idx_type_region` (`quota_type`,`region`),
  KEY `idx_publish` (`publish_status`,`audit_status`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资质定额资料库';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cms_quota_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cms_quota_detail` (
  `detail_id` bigint NOT NULL AUTO_INCREMENT,
  `quota_id` bigint NOT NULL COMMENT '关联主表',
  `quota_no` varchar(64) NOT NULL COMMENT '定额编号',
  `item_name` varchar(256) NOT NULL COMMENT '项目名称',
  `unit` varchar(32) NOT NULL COMMENT '计量单位',
  `base_price` decimal(10,2) NOT NULL COMMENT '基价（元）',
  `labor_cost` decimal(10,2) NOT NULL COMMENT '人工费',
  `material_cost` decimal(10,2) NOT NULL COMMENT '材料费',
  `machine_cost` decimal(10,2) NOT NULL COMMENT '机械费',
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`detail_id`),
  KEY `idx_quota_id` (`quota_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定额明细表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cms_standard`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cms_standard` (
  `standard_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `standard_no` varchar(64) NOT NULL COMMENT '标准编号，如GB 50264-2013',
  `standard_name` varchar(256) NOT NULL COMMENT '标准名称',
  `standard_type` tinyint NOT NULL COMMENT '类型：1国标 2行标 3地方标 4团标 5企业标',
  `industry_code` varchar(32) DEFAULT NULL COMMENT '行标行业代号：JGJ/SH/HG/DL/SY等',
  `publish_org` varchar(128) NOT NULL COMMENT '发布机构',
  `publish_date` date NOT NULL COMMENT '发布日期',
  `implement_date` date NOT NULL COMMENT '实施日期',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1现行 2即将实施 3废止 4被替代',
  `abolish_date` date DEFAULT NULL COMMENT '废止日期',
  `replace_standard_id` bigint DEFAULT NULL COMMENT '替代标准ID',
  `industry_field` json NOT NULL COMMENT '适用领域数组，如["PETROCHEM","BUILDING"]',
  `protect_object` json NOT NULL COMMENT '防护对象数组，如["PIPELINE","STORAGE_TANK"]',
  `scene` json NOT NULL COMMENT '施工场景数组，如["HIGH_TEMP","MID_TEMP"]',
  `summary` text COMMENT '标准摘要',
  `keywords` varchar(512) DEFAULT NULL COMMENT '关键词，逗号分隔',
  `file_id` bigint DEFAULT NULL COMMENT '全文附件ID（关联文件服务）',
  `cover_image` varchar(256) DEFAULT NULL COMMENT '封面图URL',
  `view_count` int NOT NULL DEFAULT '0' COMMENT '浏览量',
  `download_count` int NOT NULL DEFAULT '0' COMMENT '下载量',
  `subscribe_count` int NOT NULL DEFAULT '0' COMMENT '订阅数',
  `is_top` tinyint NOT NULL DEFAULT '0' COMMENT '是否置顶：0否 1是',
  `audit_status` tinyint NOT NULL DEFAULT '3' COMMENT '审核：0待审 1通过 2驳回 3草稿',
  `audit_remark` varchar(512) DEFAULT NULL COMMENT '审核备注',
  `publish_status` tinyint NOT NULL DEFAULT '0' COMMENT '发布：0未发布 1已发布 2已下架',
  `publish_time` datetime DEFAULT NULL COMMENT '发布时间',
  `create_by` bigint NOT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常 1删除',
  `tenant_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`standard_id`),
  UNIQUE KEY `uk_standard_no` (`standard_no`),
  KEY `idx_status` (`status`),
  KEY `idx_publish` (`publish_status`,`audit_status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='行业标准规范库';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cms_user_favorite`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cms_user_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `content_type` varchar(32) NOT NULL,
  `content_id` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_content` (`user_id`,`content_type`,`content_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户收藏';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cms_user_subscribe`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cms_user_subscribe` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `content_type` varchar(32) NOT NULL,
  `content_id` bigint NOT NULL,
  `subscribe_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_content` (`user_id`,`content_type`,`content_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户订阅';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


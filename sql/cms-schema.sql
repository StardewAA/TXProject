-- ============================================================================
-- CMS 数据库最终版（依据《CMS三大核心模块-技术实现方案》V1.0）
-- 执行范围：删除旧版临时表 → 按文档原句重建 17 张表 → 补充文档缺失但代码引用的 cms_policy
-- 注意：本脚本会 DROP 所有 cms% 表（执行前均为空表，无数据损失）
-- 日期：2026-09-03
-- ============================================================================

-- ---------- 清理旧表 ----------
DROP TABLE IF EXISTS `cms_standard`;
DROP TABLE IF EXISTS `cms_material`;
DROP TABLE IF EXISTS `cms_process_resource`;
DROP TABLE IF EXISTS `cms_quota`;
DROP TABLE IF EXISTS `cms_quota_detail`;
DROP TABLE IF EXISTS `cms_category_dict`;
DROP TABLE IF EXISTS `cms_download_log`;
DROP TABLE IF EXISTS `cms_user_subscribe`;
DROP TABLE IF EXISTS `cms_user_favorite`;
DROP TABLE IF EXISTS `cms_ai_knowledge_source`;
DROP TABLE IF EXISTS `cms_ai_knowledge_chunk`;
DROP TABLE IF EXISTS `cms_ai_faq`;
DROP TABLE IF EXISTS `cms_ai_chat_session`;
DROP TABLE IF EXISTS `cms_ai_chat_message`;
DROP TABLE IF EXISTS `cms_crawler_source`;
DROP TABLE IF EXISTS `cms_crawler_task`;
DROP TABLE IF EXISTS `cms_crawler_raw_data`;
DROP TABLE IF EXISTS `cms_policy`;

-- ============================================================================
-- 第一部分：四大子库（文档 1.1 节原句）
-- ============================================================================

-- 1.1.1 行业标准规范库
CREATE TABLE `cms_standard` (
  `standard_id`       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `standard_no`       VARCHAR(64)  NOT NULL COMMENT '标准编号，如GB 50264-2013',
  `standard_name`     VARCHAR(256) NOT NULL COMMENT '标准名称',
  `standard_type`     TINYINT      NOT NULL COMMENT '类型：1国标 2行标 3地方标 4团标 5企业标',
  `industry_code`     VARCHAR(32)  DEFAULT NULL COMMENT '行标行业代号：JGJ/SH/HG/DL/SY等',
  `publish_org`       VARCHAR(128) NOT NULL COMMENT '发布机构',
  `publish_date`      DATE         NOT NULL COMMENT '发布日期',
  `implement_date`    DATE         NOT NULL COMMENT '实施日期',
  `status`            TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1现行 2即将实施 3废止 4被替代',
  `abolish_date`      DATE         DEFAULT NULL COMMENT '废止日期',
  `replace_standard_id` BIGINT     DEFAULT NULL COMMENT '替代标准ID',
  `industry_field`    JSON         NOT NULL COMMENT '适用领域数组，如["PETROCHEM","BUILDING"]',
  `protect_object`    JSON         NOT NULL COMMENT '防护对象数组，如["PIPELINE","STORAGE_TANK"]',
  `scene`             JSON         NOT NULL COMMENT '施工场景数组，如["HIGH_TEMP","MID_TEMP"]',
  `summary`           TEXT         DEFAULT NULL COMMENT '标准摘要',
  `keywords`          VARCHAR(512) DEFAULT NULL COMMENT '关键词，逗号分隔',
  `file_id`           BIGINT       DEFAULT NULL COMMENT '全文附件ID（关联文件服务）',
  `cover_image`       VARCHAR(256) DEFAULT NULL COMMENT '封面图URL',
  `view_count`        INT          NOT NULL DEFAULT 0 COMMENT '浏览量',
  `download_count`    INT          NOT NULL DEFAULT 0 COMMENT '下载量',
  `subscribe_count`   INT          NOT NULL DEFAULT 0 COMMENT '订阅数',
  `is_top`            TINYINT      NOT NULL DEFAULT 0 COMMENT '是否置顶：0否 1是',
  `audit_status`      TINYINT      NOT NULL DEFAULT 3 COMMENT '审核：0待审 1通过 2驳回 3草稿',
  `audit_remark`      VARCHAR(512) DEFAULT NULL COMMENT '审核备注',
  `publish_status`    TINYINT      NOT NULL DEFAULT 0 COMMENT '发布：0未发布 1已发布 2已下架',
  `publish_time`      DATETIME     DEFAULT NULL COMMENT '发布时间',
  `create_by`         BIGINT       NOT NULL COMMENT '创建人ID',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`         BIGINT       DEFAULT NULL COMMENT '更新人ID',
  `update_time`       DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `tenant_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
  `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
  PRIMARY KEY (`standard_id`),
  UNIQUE KEY `uk_standard_no` (`standard_no`),
  KEY `idx_status` (`status`),
  KEY `idx_publish` (`publish_status`, `audit_status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行业标准规范库';

-- 1.1.2 材料参数数据库
CREATE TABLE `cms_material` (
  `material_id`         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `material_name`       VARCHAR(128)  NOT NULL COMMENT '材料名称',
  `material_category`   TINYINT       NOT NULL COMMENT '大类：1无机保温 2有机保温 3防腐涂料 4衬里材料 5防腐管件 6预制制品',
  `material_subcategory` VARCHAR(64)  NOT NULL COMMENT '子类，如岩棉、聚氨酯',
  `brand`               VARCHAR(64)   DEFAULT NULL COMMENT '品牌',
  `manufacturer`        VARCHAR(128)  DEFAULT NULL COMMENT '生产厂家',
  `density`             DECIMAL(10,2) DEFAULT NULL COMMENT '密度 kg/m³',
  `thermal_conductivity` DECIMAL(10,4) DEFAULT NULL COMMENT '导热系数 W/(m·K)',
  `temp_min`            INT           DEFAULT NULL COMMENT '最低耐温 ℃',
  `temp_max`            INT           DEFAULT NULL COMMENT '最高耐温 ℃',
  `compressive_strength` DECIMAL(10,2) DEFAULT NULL COMMENT '抗压强度 MPa',
  `adhesion`            DECIMAL(10,2) DEFAULT NULL COMMENT '附着力 MPa',
  `corrosion_resistance` VARCHAR(256) DEFAULT NULL COMMENT '耐腐蚀性能描述',
  `combustion_grade`    VARCHAR(32)   DEFAULT NULL COMMENT '燃烧等级：A级不燃/B1级难燃',
  `service_life`        VARCHAR(64)   DEFAULT NULL COMMENT '使用寿命',
  `applicable_condition` TEXT         NOT NULL COMMENT '适用工况描述',
  `industry_field`      JSON          NOT NULL COMMENT '适用领域数组',
  `protect_object`      JSON          NOT NULL COMMENT '防护对象数组',
  `construction_process` TEXT         DEFAULT NULL COMMENT '施工工艺要求（付费可见）',
  `inspection_standard` VARCHAR(256)  DEFAULT NULL COMMENT '检测标准编号',
  `inspection_report_id` BIGINT       DEFAULT NULL COMMENT '检测报告附件ID（付费可见）',
  `advantages`          TEXT          DEFAULT NULL COMMENT '优点',
  `disadvantages`       TEXT          DEFAULT NULL COMMENT '缺点',
  `recommend_scene`     TEXT          DEFAULT NULL COMMENT '推荐应用场景',
  `reference_price`     DECIMAL(10,2) DEFAULT NULL COMMENT '参考市场价',
  `price_unit`          VARCHAR(32)   DEFAULT NULL COMMENT '价格单位',
  `price_update_time`   DATE          DEFAULT NULL COMMENT '价格更新时间',
  `product_images`      JSON          DEFAULT NULL COMMENT '产品图片URL数组',
  `product_manual_id`   BIGINT        DEFAULT NULL COMMENT '产品说明书附件ID',
  `related_standard_ids` JSON         DEFAULT NULL COMMENT '关联标准ID数组',
  `view_count`          INT           NOT NULL DEFAULT 0,
  `collect_count`       INT           NOT NULL DEFAULT 0,
  `audit_status`        TINYINT       NOT NULL DEFAULT 3,
  `audit_remark`        VARCHAR(512)  DEFAULT NULL,
  `publish_status`      TINYINT       NOT NULL DEFAULT 0,
  `publish_time`        DATETIME      DEFAULT NULL,
  `create_by`           BIGINT        NOT NULL,
  `create_time`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`         DATETIME      DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    `tenant_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `update_by`       BIGINT       DEFAULT NULL COMMENT '更新人ID',
  `deleted`             TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`material_id`),
  KEY `idx_category` (`material_category`),
  KEY `idx_temp_range` (`temp_min`, `temp_max`),
  KEY `idx_publish` (`publish_status`, `audit_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='材料参数数据库';

-- 1.1.3 工艺工法与案例库
CREATE TABLE `cms_process_resource` (
  `resource_id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title`               VARCHAR(256) NOT NULL COMMENT '资源名称',
  `resource_type`       TINYINT      NOT NULL COMMENT '类型：1施工工序 2技术交底模板 3通病防治 4优秀案例 5不合格对比 6整改方案',
  `industry_field`      JSON         NOT NULL COMMENT '适用领域数组',
  `protect_object`      JSON         NOT NULL COMMENT '防护对象数组',
  `scene`               JSON         NOT NULL COMMENT '施工场景数组',
  `difficulty`          TINYINT      NOT NULL COMMENT '难度：1入门 2初级 3中级 4高级 5专家',
  `process_steps`       JSON         DEFAULT NULL COMMENT '工序步骤数组：[{"step":"","point":"","quality":"","image":""}]',
  `content`             LONGTEXT     NOT NULL COMMENT '正文富文本HTML',
  `video_id`            BIGINT       DEFAULT NULL COMMENT '配套视频ID',
  `attachment_ids`      JSON         DEFAULT NULL COMMENT '附件模板ID数组（付费下载）',
  `related_standard_ids` JSON        DEFAULT NULL COMMENT '关联标准ID数组',
  `related_material_ids` JSON        DEFAULT NULL COMMENT '关联材料ID数组',
  `source`              VARCHAR(128) DEFAULT NULL COMMENT '来源：企业投稿/运营原创/专家供稿',
  `author`              VARCHAR(64)  DEFAULT NULL COMMENT '作者',
  `is_free`             TINYINT      NOT NULL DEFAULT 1 COMMENT '是否免费：0付费 1免费',
  `view_count`          INT          NOT NULL DEFAULT 0,
  `like_count`          INT          NOT NULL DEFAULT 0,
  `collect_count`       INT          NOT NULL DEFAULT 0,
  `audit_status`        TINYINT      NOT NULL DEFAULT 3,
  `audit_remark`        VARCHAR(512) DEFAULT NULL,
  `publish_status`      TINYINT      NOT NULL DEFAULT 0,
  `publish_time`        DATETIME     DEFAULT NULL,
  `create_by`           BIGINT       NOT NULL,
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`         DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    `tenant_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `update_by`       BIGINT       DEFAULT NULL COMMENT '更新人ID',
  `deleted`             TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`resource_id`),
  KEY `idx_type` (`resource_type`),
  KEY `idx_difficulty` (`difficulty`),
  KEY `idx_publish` (`publish_status`, `audit_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工艺工法与案例库';

-- 1.1.4 资质定额资料库（主表）
CREATE TABLE `cms_quota` (
  `quota_id`        BIGINT       NOT NULL AUTO_INCREMENT,
  `title`           VARCHAR(256) NOT NULL COMMENT '资料名称',
  `quota_type`      TINYINT      NOT NULL COMMENT '类别：1资质要求 2施工定额 3人工单价 4材料市场价 5规费标准 6报验流程 7资料模板',
  `region`          VARCHAR(64)  NOT NULL COMMENT '适用地区，全国为"全国"',
  `publish_date`    DATE         NOT NULL COMMENT '发布时间',
  `valid_date`      DATE         DEFAULT NULL COMMENT '有效期至',
  `file_id`         BIGINT       DEFAULT NULL COMMENT '文件附件ID',
  `description`     TEXT         DEFAULT NULL COMMENT '说明备注',
  `is_free`         TINYINT      NOT NULL DEFAULT 1,
  `audit_status`    TINYINT      NOT NULL DEFAULT 3,
  `audit_remark`    VARCHAR(512) DEFAULT NULL COMMENT '审核备注（驳回原因）',
  `publish_status`  TINYINT      NOT NULL DEFAULT 0,
  `publish_time`    DATETIME     DEFAULT NULL,
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    `tenant_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `create_by`       BIGINT       DEFAULT NULL COMMENT '创建人ID',
    `update_by`       BIGINT       DEFAULT NULL COMMENT '更新人ID',
  `deleted`         TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`quota_id`),
  KEY `idx_type_region` (`quota_type`, `region`),
  KEY `idx_publish` (`publish_status`, `audit_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资质定额资料库';

-- 定额明细子表（quota_type=2时使用）
CREATE TABLE `cms_quota_detail` (
  `detail_id`    BIGINT         NOT NULL AUTO_INCREMENT,
  `quota_id`     BIGINT         NOT NULL COMMENT '关联主表',
  `quota_no`     VARCHAR(64)    NOT NULL COMMENT '定额编号',
  `item_name`    VARCHAR(256)   NOT NULL COMMENT '项目名称',
  `unit`         VARCHAR(32)    NOT NULL COMMENT '计量单位',
  `base_price`   DECIMAL(10,2)  NOT NULL COMMENT '基价（元）',
  `labor_cost`   DECIMAL(10,2)  NOT NULL COMMENT '人工费',
  `material_cost` DECIMAL(10,2) NOT NULL COMMENT '材料费',
  `machine_cost` DECIMAL(10,2)  NOT NULL COMMENT '机械费',
    `tenant_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
  `create_by`       BIGINT       DEFAULT NULL COMMENT '创建人ID',
  `update_by`       BIGINT       DEFAULT NULL COMMENT '更新人ID',
PRIMARY KEY (`detail_id`),
  KEY `idx_quota_id` (`quota_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定额明细表';

-- 1.1.5 分类字典表（统一管理行业领域/防护对象/施工场景/材料类别等）
CREATE TABLE `cms_category_dict` (
  `dict_id`     BIGINT       NOT NULL AUTO_INCREMENT,
  `dict_type`   VARCHAR(64)  NOT NULL COMMENT '字典类型：industry_field/protect_object/scene/material_category',
  `dict_code`   VARCHAR(64)  NOT NULL COMMENT '字典编码',
  `dict_name`   VARCHAR(128) NOT NULL COMMENT '字典名称',
  `parent_id`   BIGINT       DEFAULT NULL COMMENT '父级ID（支持两级）',
  `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `remark`      VARCHAR(512) DEFAULT NULL,
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `tenant_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
  `create_by`       BIGINT       DEFAULT NULL COMMENT '创建人ID',
  `update_by`       BIGINT       DEFAULT NULL COMMENT '更新人ID',
PRIMARY KEY (`dict_id`),
  UNIQUE KEY `uk_type_code` (`dict_type`, `dict_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS分类字典';

-- 下载日志表
CREATE TABLE `cms_download_log` (
  `log_id`        BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT       NOT NULL,
  `content_type`  VARCHAR(32)  NOT NULL COMMENT 'standard/material/process/quota/policy',
  `content_id`    BIGINT       NOT NULL,
  `file_id`       BIGINT       NOT NULL,
  `ip_address`    VARCHAR(64)  DEFAULT NULL,
  `download_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `tenant_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
  `create_by`       BIGINT       DEFAULT NULL COMMENT '创建人ID',
  `update_by`       BIGINT       DEFAULT NULL COMMENT '更新人ID',
PRIMARY KEY (`log_id`),
  KEY `idx_user_date` (`user_id`, `download_time`),
  KEY `idx_content` (`content_type`, `content_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='下载日志';

-- 用户订阅表
CREATE TABLE `cms_user_subscribe` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`        BIGINT      NOT NULL,
  `content_type`   VARCHAR(32) NOT NULL,
  `content_id`     BIGINT      NOT NULL,
  `subscribe_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `tenant_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
  `create_by`       BIGINT       DEFAULT NULL COMMENT '创建人ID',
  `update_by`       BIGINT       DEFAULT NULL COMMENT '更新人ID',
PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_content` (`user_id`, `content_type`, `content_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户订阅';

-- 用户收藏表
CREATE TABLE `cms_user_favorite` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT      NOT NULL,
  `content_type` VARCHAR(32) NOT NULL,
  `content_id`  BIGINT      NOT NULL,
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `tenant_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
  `create_by`       BIGINT       DEFAULT NULL COMMENT '创建人ID',
  `update_by`       BIGINT       DEFAULT NULL COMMENT '更新人ID',
PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_content` (`user_id`, `content_type`, `content_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏';

-- ============================================================================
-- 第二部分：AI 知识库（文档 2.2.2 节原句）
-- ============================================================================

-- AI知识源表
CREATE TABLE `cms_ai_knowledge_source` (
  `source_id`       BIGINT       NOT NULL AUTO_INCREMENT,
  `source_type`     TINYINT      NOT NULL COMMENT '类型：1文档上传 2人工FAQ 3爬虫政策 4标准自动导入',
  `title`           VARCHAR(256) NOT NULL COMMENT '知识源标题',
  `file_id`         BIGINT       DEFAULT NULL COMMENT '附件ID（文档类型）',
  `standard_no`     VARCHAR(64)  DEFAULT NULL COMMENT '关联标准号（标准类型）',
  `chunk_count`     INT          NOT NULL DEFAULT 0 COMMENT '切片数量',
  `status`          TINYINT      NOT NULL DEFAULT 0 COMMENT '0待解析 1解析中 2已就绪 3解析失败',
  `error_msg`       VARCHAR(1024) DEFAULT NULL COMMENT '失败原因',
  `parse_progress`  TINYINT      NOT NULL DEFAULT 0 COMMENT '解析进度0-100',
  `create_by`       BIGINT       NOT NULL,
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    `tenant_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `update_by`       BIGINT       DEFAULT NULL COMMENT '更新人ID',
  `deleted`         TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`source_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI知识源';

-- AI知识切片表
CREATE TABLE `cms_ai_knowledge_chunk` (
  `chunk_id`        BIGINT       NOT NULL AUTO_INCREMENT,
  `source_id`       BIGINT       NOT NULL COMMENT '关联知识源',
  `content`         TEXT         NOT NULL COMMENT '切片文本内容',
  `chunk_index`     INT          NOT NULL COMMENT '切片序号',
  `page_num`        INT          DEFAULT NULL COMMENT '所在页码',
  `chapter_title`   VARCHAR(256) DEFAULT NULL COMMENT '所属章节标题',
  `metadata`        JSON         DEFAULT NULL COMMENT '扩展元数据：{"standardNo":"","section":""}',
  `token_count`     INT          NOT NULL DEFAULT 0 COMMENT 'token数',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `tenant_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
  `create_by`       BIGINT       DEFAULT NULL COMMENT '创建人ID',
  `update_by`       BIGINT       DEFAULT NULL COMMENT '更新人ID',
PRIMARY KEY (`chunk_id`),
  KEY `idx_source_id` (`source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI知识切片';

-- 人工FAQ表
CREATE TABLE `cms_ai_faq` (
  `faq_id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `question`        VARCHAR(512) NOT NULL,
  `answer`          TEXT         NOT NULL,
  `category`        VARCHAR(64)  DEFAULT NULL COMMENT '分类：政策/标准/材料/施工/合规',
  `priority`        INT          NOT NULL DEFAULT 5 COMMENT '优先级1-10，越高越优先返回',
  `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `tenant_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
  `create_by`       BIGINT       DEFAULT NULL COMMENT '创建人ID',
  `update_by`       BIGINT       DEFAULT NULL COMMENT '更新人ID',
PRIMARY KEY (`faq_id`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI人工FAQ';

-- AI对话会话表
CREATE TABLE `cms_ai_chat_session` (
  `session_id`      BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`         BIGINT       NOT NULL,
  `session_title`   VARCHAR(128) DEFAULT NULL COMMENT '会话标题（取第一条问题前20字）',
  `message_count`   INT          NOT NULL DEFAULT 0,
  `last_message_time` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `tenant_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
  `create_by`       BIGINT       DEFAULT NULL COMMENT '创建人ID',
  `update_by`       BIGINT       DEFAULT NULL COMMENT '更新人ID',
PRIMARY KEY (`session_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话会话';

-- AI对话消息表
CREATE TABLE `cms_ai_chat_message` (
  `message_id`      BIGINT       NOT NULL AUTO_INCREMENT,
  `session_id`      BIGINT       NOT NULL,
  `user_id`         BIGINT       NOT NULL,
  `role`            VARCHAR(16)  NOT NULL COMMENT 'user/assistant/system',
  `content`         TEXT         NOT NULL,
  `references`      JSON         DEFAULT NULL COMMENT '引用来源：[{"type":"standard","id":1,"no":"GB 50264"}]',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `tenant_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
  `create_by`       BIGINT       DEFAULT NULL COMMENT '创建人ID',
  `update_by`       BIGINT       DEFAULT NULL COMMENT '更新人ID',
PRIMARY KEY (`message_id`),
  KEY `idx_session` (`session_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话消息';

-- ============================================================================
-- 第三部分：爬虫（文档 3.3.1 节原句）
-- ============================================================================

CREATE TABLE `cms_crawler_source` (
  `source_id`     BIGINT       NOT NULL AUTO_INCREMENT,
  `source_name`   VARCHAR(128) NOT NULL COMMENT '采集源名称',
  `spider_name`   VARCHAR(64)  NOT NULL COMMENT '对应Python爬虫名称',
  `target_url`    VARCHAR(512) NOT NULL COMMENT '目标网站URL',
  `data_type`     VARCHAR(32)  NOT NULL COMMENT '数据类型：policy/standard/material/quota',
  `cron_expr`     VARCHAR(64)  NOT NULL DEFAULT '0 0 2 * * ?' COMMENT '执行cron表达式',
  `pages`         INT          NOT NULL DEFAULT 3 COMMENT '每次爬取页数',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  `last_run_time` DATETIME     DEFAULT NULL COMMENT '上次执行时间',
  `last_run_status` TINYINT    DEFAULT NULL COMMENT '上次状态：1成功 2失败',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    `tenant_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
  `create_by`       BIGINT       DEFAULT NULL COMMENT '创建人ID',
  `update_by`       BIGINT       DEFAULT NULL COMMENT '更新人ID',
PRIMARY KEY (`source_id`),
  UNIQUE KEY `uk_spider_name` (`spider_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='爬虫采集源配置';

CREATE TABLE `cms_crawler_task` (
  `task_id`       BIGINT       NOT NULL AUTO_INCREMENT,
  `source_id`     BIGINT       NOT NULL,
  `start_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end_time`      DATETIME     DEFAULT NULL,
  `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '0运行中 1成功 2失败',
  `success_count` INT          NOT NULL DEFAULT 0 COMMENT '成功采集数',
  `fail_count`    INT          NOT NULL DEFAULT 0 COMMENT '失败数',
  `error_msg`     VARCHAR(2000) DEFAULT NULL,
    `tenant_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
  `create_by`       BIGINT       DEFAULT NULL COMMENT '创建人ID',
  `update_by`       BIGINT       DEFAULT NULL COMMENT '更新人ID',
PRIMARY KEY (`task_id`),
  KEY `idx_source` (`source_id`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='爬虫任务执行记录';

CREATE TABLE `cms_crawler_raw_data` (
  `raw_id`        BIGINT       NOT NULL AUTO_INCREMENT,
  `source_id`     BIGINT       NOT NULL,
  `data_type`     VARCHAR(32)  NOT NULL,
  `title`         VARCHAR(512) NOT NULL,
  `content`       LONGTEXT,
  `source_url`    VARCHAR(512),
  `publish_date`  DATE,
  `crawl_time`    DATETIME,
  `raw_data`      JSON         COMMENT '原始采集数据JSON',
  `audit_status`  TINYINT      NOT NULL DEFAULT 0 COMMENT '0待审核 1通过 2驳回',
  `audit_remark`  VARCHAR(512),
  `audit_by`      BIGINT,
  `audit_time`    DATETIME,
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `tenant_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
  `create_by`       BIGINT       DEFAULT NULL COMMENT '创建人ID',
  `update_by`       BIGINT       DEFAULT NULL COMMENT '更新人ID',
PRIMARY KEY (`raw_id`),
  KEY `idx_audit` (`audit_status`),
  KEY `idx_source` (`source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='爬虫原始数据（待审核）';

-- ============================================================================
-- 补充表：cms_policy（文档 3.3.4 审核发布代码引用了 CmsPolicy，但未给出建表语句）
-- 字段依据：爬虫 PolicyItem 结构 + 文档公共字段约定
-- ============================================================================
CREATE TABLE `cms_policy` (
  `policy_id`       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '政策ID',
  `title`           VARCHAR(256) NOT NULL COMMENT '政策标题',
  `policy_level`    TINYINT      DEFAULT NULL COMMENT '层级：1国家 2省 3市 4区县',
  `publish_dept`    VARCHAR(128) DEFAULT NULL COMMENT '发布部门',
  `category`        VARCHAR(64)  DEFAULT NULL COMMENT '主题分类',
  `content_type`    VARCHAR(16)  NOT NULL DEFAULT '金融政策' COMMENT '内容类型：法律法规/金融政策',
  `effective_status` VARCHAR(20) NOT NULL DEFAULT '现行有效' COMMENT '效力状态：现行有效/已修订/已废止/即将失效',
  `publish_date`    DATE         DEFAULT NULL COMMENT '发布日期',
  `implement_date`  DATE         DEFAULT NULL COMMENT '实施日期',
  `effective_date`  DATE         DEFAULT NULL COMMENT '实施日期（法规）',
  `content`         LONGTEXT     COMMENT '政策全文',
  `summary`         VARCHAR(500) DEFAULT NULL COMMENT '内容摘要（列表用）',
  `source_url`      VARCHAR(512) DEFAULT NULL COMMENT '原文链接',
  `source_name`     VARCHAR(128) DEFAULT NULL COMMENT '来源网站名称',
  `attachment_urls` JSON         DEFAULT NULL COMMENT '附件URL列表',
  `view_count`      INT          NOT NULL DEFAULT 0 COMMENT '浏览量',
  `download_count`  INT          NOT NULL DEFAULT 0 COMMENT '下载量',
  `is_top`          TINYINT      NOT NULL DEFAULT 0 COMMENT '是否置顶：0否 1是',
  `audit_status`    TINYINT      NOT NULL DEFAULT 3 COMMENT '审核：0待审 1通过 2驳回 3草稿',
  `audit_remark`    VARCHAR(512) DEFAULT NULL COMMENT '审核备注（驳回原因）',
  `publish_status`  TINYINT      NOT NULL DEFAULT 0 COMMENT '发布：0未发布 1已发布 2已下架',
  `publish_time`    DATETIME     DEFAULT NULL COMMENT '发布时间',
  `create_by`       BIGINT       DEFAULT NULL COMMENT '创建人ID（爬虫采集为NULL）',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       BIGINT       DEFAULT NULL COMMENT '更新人ID',
  `update_time`     DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `tenant_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
  `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
  PRIMARY KEY (`policy_id`),
  KEY `idx_level` (`policy_level`),
  KEY `idx_publish_date` (`publish_date`),
  KEY `idx_publish` (`publish_status`, `audit_status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='政策资讯';

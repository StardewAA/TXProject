-- WMS AI 客服配置表：指定「仓储 AI 助手」使用 ai_model 中的哪个模型
--
-- 背景：WmsAiServiceImpl 原先通过环境变量 txgy.ai.siliconflow.* 读取模型/Key，
-- 运维需要改容器环境变量才能换模型。本表把「用哪个模型」交给界面配置：
-- ai_model 记录模型与 platform，ai_model.key_id 关联 ai_api_key 提供 Key 与 BaseURL，
-- 因此天然支持接入其他厂商（OpenAI / DeepSeek / 通义 / 智谱 / Ollama / DouBao …）。
--
-- 单租户单条配置；查不到时后端回落环境变量，保证升级过程不中断。

CREATE TABLE IF NOT EXISTS `wms_ai_config` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `model_id`      bigint       DEFAULT NULL COMMENT '使用的 ai_model.id；为空时回落环境变量',
  `enabled`       bit(1)       NOT NULL DEFAULT b'1' COMMENT '是否启用界面配置：1 启用则忽略环境变量',
  `timeout_seconds` int        DEFAULT NULL COMMENT '调用超时（秒），空则用默认值',
  `remark`        varchar(255) DEFAULT NULL COMMENT '备注',
  `creator`       varchar(64)  DEFAULT '',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater`       varchar(64)  DEFAULT '',
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`       bit(1)       NOT NULL DEFAULT b'0',
  `tenant_id`     bigint       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'WMS AI 客服配置';

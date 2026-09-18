-- =====================================================================
-- 关键岗位字典注册（承接 2026-09-23-bidding-dict-trace-and-notice-status.sql）
-- =====================================================================
-- 背景：安许风险联动页（views/bidding/safety-risk）的④关键岗位任职/变更登记，下拉选项此前是前端硬编码数组
--       KEY_POST_OPTIONS（"逐字取自 KeyPostEnum"）。第 11 道门禁（枚举硬编码棘轮）已把它登记在册，
--       本脚本把该枚举登记为字典，前端改走字典取值 —— 消费自己立的规矩。
-- 取值来源：KeyPostEnum（不臆造，逐字对齐；含 Javadoc 里"为什么做成枚举而不是自由文本"的口径：
--   频繁变更统计依赖"同一岗位"的确定性判定，自由文本会把"项目经理/项目负责人/项目经理(执行)"判成三个岗位）。
--   项目经理（注册建造师）= PROJECT_MANAGER、技术负责人 = TECH_LEADER、专职安全生产管理人员 = SAFETY_OFFICER、
--   质量负责人 = QUALITY_OFFICER、施工员 = CONSTRUCTION_ENGINEER。
-- 注意：后端 KeyPostEnum.isValid 只认这些 code（不接受自由文本），故本字典只做"展示与下拉"，
--       不改变入参校验；unknown code 后端 labelOf 回落原值。
-- 设计说明：
--   1) id 沿用 100 段分块约定：本类占 43xx（43 01~43 05），不复用旧 id；
--   2) 幂等：INSERT IGNORE，可重复执行；
--   3) 前端保留 KEY_POST_OPTIONS 作为"字典尚未下发环境"的兜底（与备案状态下拉同一口径），
--      门禁基线里该文件仍计 1 处但理由改为"已降级为兜底、主路径走字典"。
-- 执行库：ruoyi-vue-pro。
-- 校验：SELECT dict_value, dict_label FROM bidding_dict_data WHERE dict_type = 'key_post' ORDER BY sort;  -- 期望 5 行
-- =====================================================================

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(710000000000004301,0,'key_post','PROJECT_MANAGER','项目经理','processing',b'0',1,b'1','注册建造师；防挂靠监控的核心岗位','1',NOW(),'1',NOW(),b'0'),
(710000000000004302,0,'key_post','TECH_LEADER','技术负责人','processing',b'0',2,b'1','项目技术负责人','1',NOW(),'1',NOW(),b'0'),
(710000000000004303,0,'key_post','SAFETY_OFFICER','专职安全员','warning',b'0',3,b'1','专职安全生产管理人员（与安许证联动核查）','1',NOW(),'1',NOW(),b'0'),
(710000000000004304,0,'key_post','QUALITY_OFFICER','质量负责人','default',b'0',4,b'1','项目质量负责人','1',NOW(),'1',NOW(),b'0'),
(710000000000004305,0,'key_post','CONSTRUCTION_ENGINEER','施工员','default',b'0',5,b'1','施工现场管理人员','1',NOW(),'1',NOW(),b'0');

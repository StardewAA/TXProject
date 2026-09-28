-- =====================================================================
-- 2026-09-28 证书预警两类字典注册（alert_level / alert_type）
--
-- 规范依据：《招投标模块开发规范》用户硬约束"枚举/阈值一律动态配置，前端不得硬编码中文与颜色"；
--           门禁 sql/tools/check-dict-permission.js「字典覆盖率」——前端引用的 dict_type 必须已注册。
-- 前端消费：apps/web-antd/src/views/bidding/certificate/index.vue
--           dictOptions('alert_type') / dictLabel('alert_level', record.alertLevel) / dictLabel('alert_type', record.alertType)。
-- 后端同源：CertificateAlertLevelEnum（INFO/WARNING/CRITICAL）、CertificateAlertTypeEnum（EXPIRY/REVIEW/RENEWAL/QUAL_EXPIRY）；
--           档位由 cert_alert_config.thresholds 推导（v3.3 §九），QUAL_EXPIRY 对应 cert_type IS NULL 的资质类到期行。
-- 幂等：INSERT IGNORE（按 id 去重），可重复执行。
-- 镜像：同批内容已并入 sql/master-credential-sync.sql（一键包），重建新库不会缺这两类字典。
-- 执行库：ruoyi-vue-pro。
-- =====================================================================
SET NAMES utf8mb4;

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- id 用 2906~912：2901~905 已是 risk_item_status（2026-09-28-safety-risk-item.sql），2801~803 是 material_result，撞号会被 INSERT IGNORE 静默跳过
-- alert_level（3 条）
(710000000000002906,0,'alert_level','INFO','提示','info',b'0',1,b'1','提前量首档（v3.3 §九阈值档位）','1',NOW(),'1',NOW(),b'0'),
(710000000000002907,0,'alert_level','WARNING','警告','warning',b'0',2,b'1','中档提醒','1',NOW(),'1',NOW(),b'0'),
(710000000000002908,0,'alert_level','CRITICAL','严重','error',b'0',3,b'1','临期/逾期高档提醒','1',NOW(),'1',NOW(),b'0'),
-- alert_type（4 条）
(710000000000002909,0,'alert_type','EXPIRY','到期预警','warning',b'0',1,b'1','valid_to 到期（v3.3 §九）','1',NOW(),'1',NOW(),b'0'),
(710000000000002910,0,'alert_type','REVIEW','年检预警','info',b'0',2,b'1','next_review_date 年检','1',NOW(),'1',NOW(),b'0'),
(710000000000002911,0,'alert_type','RENEWAL','续期预警','processing',b'0',3,b'1','续期窗口提醒','1',NOW(),'1',NOW(),b'0'),
(710000000000002912,0,'alert_type','QUAL_EXPIRY','资质到期预警','warning',b'0',4,b'1','企业资质/安许/人员注册到期（v3.3 §十五）','1',NOW(),'1',NOW(),b'0');

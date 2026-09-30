-- =====================================================================
-- 2026-09-28 风险规则「触发条件类型」字典注册（risk_rule_cond_type）
--
-- 规范依据：《招投标模块开发规范》用户硬约束"枚举/阈值一律动态配置，前端不得硬编码中文与颜色"；
--           门禁 sql/tools/check-dict-permission.js「字典覆盖率」——前端引用的 dict_type 必须已注册。
-- 前端消费：apps/web-antd/src/views/bidding/safety-risk/index.vue —— condTypeOptions 优先取
--           dictOptions('risk_rule_cond_type')，未注册时回落到**硬编码** COND_TYPE_FALLBACK（本脚本消除该硬编码）。
-- 口径衔接：Q23 裁定维持"阈值键值"（Q23a，见《后端变更通知-给前端.md》第 25 条）——
--           THRESHOLD=与 rule-config 的 ruleKey 阈值比较（当前唯一后端已实现形态）；
--           EXPRESSION/NATURAL 为前端预留的展示形态，后端暂不落库（仅登记字典，避免中文硬编码）。
-- 幂等：INSERT IGNORE（按 id 去重），可重复执行。
-- 镜像：同批内容已并入 sql/master-credential-sync.sql（一键包），重建新库不会缺该类字典。
-- 执行库：ruoyi-vue-pro。
-- =====================================================================
SET NAMES utf8mb4;

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- id 用 2913~915：2901~905 risk_item_status、2906~912 alert_level/alert_type 已占用（2026-09-28 同日两脚本）
(710000000000002913,0,'risk_rule_cond_type','THRESHOLD','阈值比较','info',b'0',1,b'1','与 rule-config 的 ruleKey 阈值比较（Q23a 当前唯一落库形态）','1',NOW(),'1',NOW(),b'0'),
(710000000000002914,0,'risk_rule_cond_type','EXPRESSION','表达式','warning',b'0',2,b'1','表达式形态（前端展示预留，后端暂不落库）','1',NOW(),'1',NOW(),b'0'),
(710000000000002915,0,'risk_rule_cond_type','NATURAL','自然语言','default',b'0',3,b'1','自然语言描述形态（前端展示预留，后端暂不落库）','1',NOW(),'1',NOW(),b'0');

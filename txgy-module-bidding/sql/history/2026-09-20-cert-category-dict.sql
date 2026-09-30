-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 证件大类字典（cert_category）：给「证照类型」加一层业务分类
-- 依据：《招投标模块开发规范》§5 三-7（代码型字段存 code，字典 key 前缀 bidding_）
--   用户裁定（2026-09-20）：电子证照域证件按四类归口 —— 身份类 / 资质类 / 业绩类 / 资信类
-- 背景：cert_type 原把「身份/资质/业绩/资信」四种语义平铺为 11 项，前端筛选只能硬编码；
--   且「资质」「业绩」在 qualification 域另有独立台账，同一概念两处存在 → 增加本层归口。
-- 说明：cert_type 的新增大类归属见 cert_type_category 映射（下段），不改变 cert_type 既有取值。
-- ============================================================================

-- 1) 证件大类字典（4 类）
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(710000000000008101,0,'cert_category','IDENTITY','身份类','primary',b'0',1,b'1','主体身份证明类：营业执照/法定代表人身份证明/授权委托书','1',NOW(),'1',NOW(),b'0'),
(710000000000008102,0,'cert_category','QUALIFICATION','资质类','success',b'0',2,b'1','企业资质与许可类：体系认证/安全生产许可证/各类资质证书','1',NOW(),'1',NOW(),b'0'),
(710000000000008103,0,'cert_category','PERFORMANCE','PERFORMANCE','warning',b'0',3,b'1','业绩证明类：业绩证明/中标通知书/合同（与 qualification 业绩台账同源）','1',NOW(),'1',NOW(),b'0'),
(710000000000008104,0,'cert_category','CREDIT','资信类','info',b'0',4,b'1','资信与佐证类：财务审计报告/纳税信用/社保证明/荣誉证书','1',NOW(),'1',NOW(),b'0');

-- 2) cert_type → cert_category 归属映射字典（值=大类 code）
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(710000000000008111,0,'cert_type_category','ADMIN_LICENSE','IDENTITY','default',b'0',1,b'1','营业执照→身份类','1',NOW(),'1',NOW(),b'0'),
(710000000000008112,0,'cert_type_category','LEGAL_PERSON_ID','IDENTITY','default',b'0',2,b'1','法定代表人身份证明→身份类','1',NOW(),'1',NOW(),b'0'),
(710000000000008113,0,'cert_type_category','AUTH_LETTER','IDENTITY','default',b'0',3,b'1','法人授权委托书→身份类','1',NOW(),'1',NOW(),b'0'),
(710000000000008114,0,'cert_type_category','SYSTEM_CERT','QUALIFICATION','default',b'0',4,b'1','体系认证证书→资质类','1',NOW(),'1',NOW(),b'0'),
(710000000000008115,0,'cert_type_category','SAFETY_PERMIT','QUALIFICATION','default',b'0',5,b'1','安全生产许可证→资质类','1',NOW(),'1',NOW(),b'0'),
(710000000000008116,0,'cert_type_category','PERSON_QUAL','QUALIFICATION','default',b'0',6,b'1','人员资格证书→资质类','1',NOW(),'1',NOW(),b'0'),
(710000000000008117,0,'cert_type_category','PERFORMANCE','PERFORMANCE','default',b'0',7,b'1','业绩证明→业绩类','1',NOW(),'1',NOW(),b'0'),
(710000000000008118,0,'cert_type_category','AUDIT_REPORT','CREDIT','default',b'0',8,b'1','财务审计报告→资信类','1',NOW(),'1',NOW(),b'0'),
(710000000000008119,0,'cert_type_category','SOCIAL_SECURITY','CREDIT','default',b'0',9,b'1','社保证明→资信类','1',NOW(),'1',NOW(),b'0'),
(710000000000008120,0,'cert_type_category','HONOR','CREDIT','default',b'0',10,b'1','荣誉证书→资信类','1',NOW(),'1',NOW(),b'0'),
(710000000000008121,0,'cert_type_category','TAX_CREDIT','CREDIT','default',b'0',11,b'1','纳税信用证明→资信类','1',NOW(),'1',NOW(),b'0');

-- ============================================================================
-- 校验 SELECT（执行后人工核对）：应 4 行 + 11 行
-- SELECT dict_type, COUNT(*) FROM bidding_dict_data WHERE dict_type IN ('cert_category','cert_type_category') AND deleted=0 GROUP BY dict_type;
-- ============================================================================

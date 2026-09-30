-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 变更留痕字典（cert_change_type / cert_op_source）
-- 背景：cert_change_logs 一直有写入但**无读出口**；2026-09-21 补
--   GET /bidding/certificate/change-log/page 后，changeType/opSource 两个 code 字段
--   需要中文名（与证照域其它读接口同口径：字典是唯一权威来源，规范 §5 三-7）。
-- 取值来源：CertChangeLogDO 字段注释 + CertChangeTypeEnum / CertificateChangeSourceEnum。
-- ============================================================================

-- 1) 变更类型（对齐 CertChangeTypeEnum：CREATE/UPDATE/STATUS_CHANGE/REVOKE）
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(710000000000008201,0,'cert_change_type','CREATE','建档','success',b'0',1,b'1','首次建档（oldValue 为空）','1',NOW(),'1',NOW(),b'0'),
(710000000000008202,0,'cert_change_type','UPDATE','字段更新','primary',b'0',2,b'1','普通字段变更（留痕为字段级，一次更新多行）','1',NOW(),'1',NOW(),b'0'),
(710000000000008203,0,'cert_change_type','STATUS_CHANGE','状态流转','warning',b'0',3,b'1','台账状态变更（吊销/注销记 REVOKE）','1',NOW(),'1',NOW(),b'0'),
(710000000000008204,0,'cert_change_type','REVOKE','吊销/注销','error',b'0',4,b'1','吊销或注销（不可逆终态）','1',NOW(),'1',NOW(),b'0');

-- 2) 操作来源（对齐 CertificateChangeSourceEnum：MANUAL/IMPORT/OCR/API_SYNC/SYSTEM）
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(710000000000008211,0,'cert_op_source','MANUAL','手工操作','primary',b'0',1,b'1','用户在管理端手工录入/修改','1',NOW(),'1',NOW(),b'0'),
(710000000000008212,0,'cert_op_source','IMPORT','批量导入','success',b'0',2,b'1','Excel 批量导入（同批 batchId 归组）','1',NOW(),'1',NOW(),b'0'),
(710000000000008213,0,'cert_op_source','OCR','OCR 识别','info',b'0',3,b'1','扫描件 OCR 自动识别录入','1',NOW(),'1',NOW(),b'0'),
(710000000000008214,0,'cert_op_source','API_SYNC','接口同步','warning',b'0',4,b'1','外部平台/四库一平台接口同步','1',NOW(),'1',NOW(),b'0'),
(710000000000008215,0,'cert_op_source','SYSTEM','系统自动','default',b'0',5,b'1','定时任务/系统推导（如到期状态推导）','1',NOW(),'1',NOW(),b'0');

-- 校验 SELECT（执行后人工核对）：应 4 行 + 5 行
-- SELECT dict_type, COUNT(*) FROM bidding_dict_data WHERE dict_type IN ('cert_change_type','cert_op_source') AND deleted=0 GROUP BY dict_type;

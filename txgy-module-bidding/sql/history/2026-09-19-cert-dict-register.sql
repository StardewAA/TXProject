-- =====================================================================
-- 电子证照域字典类型注册与种子（补齐契约 v1.0 §1.1 与 v1.1 §12.0 的字典缺口）
-- =====================================================================
-- 依据：用户核查结论第 3 条——cert_type / holder_type / material_* / review_result 以及
--       安许风险联动的 safety_risk_flag / person_risk_flag / risk_level **未注册且无种子**，
--       前端只能降级显示 code（拿不到中文名、标签色、终态标记）；
--       用户硬约束"状态码/枚举值必须支持动态配置，严禁硬编码"。
-- 取值来源（不臆造）：
--   cert_type        ← EnterpriseMaterialCategoryEnum.resolveByCertType（11 个分支值）
--   material_type    ← EnterpriseMaterialTypeEnum 常量（7 个：code/中文名/对应 cert_type）
--   material_category← EnterpriseMaterialCategoryEnum（IDENTITY/QUALIFICATION/STRENGTH）
--   holder_type      ← CertificateDO.holderType 的 @Schema 口径（ORG/PERSON/EXTERNAL）
--   review_result    ← 年检结论字段口径（1 通过 / 0 不通过）
--   safety_risk_flag / person_risk_flag / risk_level ← 本次新增枚举类（SafetyRiskFlagEnum /
--                      PersonRiskFlagEnum / RiskLevelEnum）
-- 设计说明：
--   1) id 块预留 7100000000000020xx ~ 7100000000000029xx —— v1.0 的 68 条种子占用 001~134，
--      本文件不复用其 id，避免 INSERT IGNORE 静默丢行；
--   2) color_type 即契约 §1.1 的 tagType，terminal 供前端禁用流转按钮；
--   3) 幂等：INSERT IGNORE，可重复执行。
-- 执行库：ruoyi-vue-pro（与 2026-09-16-rule-config-center.sql 同库；bidding_dict_data 由该脚本建表）。
-- =====================================================================

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- cert_type（11 条）
(710000000000002001,0,'cert_type','ADMIN_LICENSE','营业执照','success',b'0',1,b'1','企业身份证明；材料分类=身份','1',NOW(),'1',NOW(),b'0'),
(710000000000002002,0,'cert_type','LEGAL_PERSON_ID','法定代表人身份证明','success',b'0',2,b'1','材料分类=身份','1',NOW(),'1',NOW(),b'0'),
(710000000000002003,0,'cert_type','AUTH_LETTER','法人授权委托书','success',b'0',3,b'1','员工代表企业投标的效力凭证；材料分类=身份','1',NOW(),'1',NOW(),b'0'),
(710000000000002004,0,'cert_type','SYSTEM_CERT','体系认证证书','info',b'0',4,b'1','材料分类=资质','1',NOW(),'1',NOW(),b'0'),
(710000000000002005,0,'cert_type','SAFETY_PERMIT','安全生产许可证','info',b'0',5,b'1','材料分类=资质','1',NOW(),'1',NOW(),b'0'),
(710000000000002006,0,'cert_type','PERSON_QUAL','人员资格证书','info',b'0',6,b'1','材料分类=资质；中文名待业务确认','1',NOW(),'1',NOW(),b'0'),
(710000000000002007,0,'cert_type','AUDIT_REPORT','财务审计报告','default',b'0',7,b'1','材料分类=实力','1',NOW(),'1',NOW(),b'0'),
(710000000000002008,0,'cert_type','PERFORMANCE','业绩证明','default',b'0',8,b'1','材料分类=实力；中文名待业务确认','1',NOW(),'1',NOW(),b'0'),
(710000000000002009,0,'cert_type','SOCIAL_SECURITY','社保证明','default',b'0',9,b'1','材料分类=实力','1',NOW(),'1',NOW(),b'0'),
(710000000000002010,0,'cert_type','HONOR','荣誉证书','default',b'0',10,b'1','材料分类=实力；中文名待业务确认','1',NOW(),'1',NOW(),b'0'),
(710000000000002011,0,'cert_type','TAX_CREDIT','纳税信用证明','default',b'0',11,b'1','材料分类=实力；中文名待业务确认','1',NOW(),'1',NOW(),b'0'),
-- holder_type（3 条）
(710000000000002101,0,'holder_type','ORG','单位','info',b'0',1,b'1','持证主体为企业/机构','1',NOW(),'1',NOW(),b'0'),
(710000000000002102,0,'holder_type','PERSON','个人','info',b'0',2,b'1','持证主体为自然人','1',NOW(),'1',NOW(),b'0'),
(710000000000002103,0,'holder_type','EXTERNAL','外部机构','default',b'0',3,b'1','非本企业主体的外部持证方','1',NOW(),'1',NOW(),b'0'),
-- review_result（2 条）
(710000000000002201,0,'review_result','1','通过','success',b'0',1,b'1','年检结论取值以字符串提交（后端字段为 Integer）','1',NOW(),'1',NOW(),b'0'),
(710000000000002202,0,'review_result','0','不通过','error',b'0',2,b'1','同上','1',NOW(),'1',NOW(),b'0'),
-- material_category（3 条）
(710000000000002301,0,'material_category','IDENTITY','身份证明','info',b'0',1,b'1','对应 EnterpriseMaterialCategoryEnum.IDENTITY','1',NOW(),'1',NOW(),b'0'),
(710000000000002302,0,'material_category','QUALIFICATION','资质证明','info',b'0',2,b'1','对应 QUALIFICATION','1',NOW(),'1',NOW(),b'0'),
(710000000000002303,0,'material_category','STRENGTH','实力证明','info',b'0',3,b'1','对应 STRENGTH','1',NOW(),'1',NOW(),b'0'),
-- material_type（7 条）
(710000000000002401,0,'material_type','BUSINESS_LICENSE','营业执照','default',b'0',1,b'1','cert_type=ADMIN_LICENSE','1',NOW(),'1',NOW(),b'0'),
(710000000000002402,0,'material_type','LEGAL_PERSON_ID','法定代表人身份证明','default',b'0',2,b'1','cert_type=LEGAL_PERSON_ID','1',NOW(),'1',NOW(),b'0'),
(710000000000002403,0,'material_type','AUTH_LETTER','法人授权委托书','default',b'0',3,b'1','cert_type=AUTH_LETTER','1',NOW(),'1',NOW(),b'0'),
(710000000000002404,0,'material_type','QUAL_CERT','建筑业企业资质证书','default',b'0',4,b'1','cert_type=SYSTEM_CERT','1',NOW(),'1',NOW(),b'0'),
(710000000000002405,0,'material_type','SAFETY_PERMIT','安全生产许可证','default',b'0',5,b'1','cert_type=SAFETY_PERMIT','1',NOW(),'1',NOW(),b'0'),
(710000000000002406,0,'material_type','AUDIT_REPORT','财务审计报告','default',b'0',6,b'1','cert_type=AUDIT_REPORT','1',NOW(),'1',NOW(),b'0'),
(710000000000002407,0,'material_type','SOCIAL_SECURITY','社保证明','default',b'0',7,b'1','cert_type=SOCIAL_SECURITY','1',NOW(),'1',NOW(),b'0'),
-- safety_risk_flag（5 条）
(710000000000002501,0,'safety_risk_flag','NORMAL','正常','success',b'0',1,b'1','三类人员配备达标','1',NOW(),'1',NOW(),b'0'),
(710000000000002502,0,'safety_risk_flag','YELLOW','黄色预警','warning',b'0',2,b'1','任一类低于配备要求（宽限期内由后端降档）','1',NOW(),'1',NOW(),b'0'),
(710000000000002503,0,'safety_risk_flag','RED','红色预警','error',b'0',3,b'1','任一类为 0（劳务分包豁免类型除外）','1',NOW(),'1',NOW(),b'0'),
(710000000000002504,0,'safety_risk_flag','INVALID','失效','error',b'1',4,b'1','三类全为 0 且逾期不整改；终态','1',NOW(),'1',NOW(),b'0'),
(710000000000002505,0,'safety_risk_flag','FREQUENT','频繁预警','warning',b'0',5,b'1','窗口内预警次数达阈值（趋势标签，不阻断）','1',NOW(),'1',NOW(),b'0'),
-- person_risk_flag（3 条）
(710000000000002601,0,'person_risk_flag','NORMAL','正常','success',b'0',1,b'1','窗口内更换受聘企业次数未达阈值','1',NOW(),'1',NOW(),b'0'),
(710000000000002602,0,'person_risk_flag','ABNORMAL','一年异常','error',b'0',2,b'1','12 个月内更换受聘企业次数达阈值（挂证嫌疑）','1',NOW(),'1',NOW(),b'0'),
(710000000000002603,0,'person_risk_flag','FREQUENT','频繁异常','warning',b'0',3,b'1','多次触发异常','1',NOW(),'1',NOW(),b'0'),
-- risk_level（4 条）
(710000000000002701,0,'risk_level','LOW','低','success',b'0',1,b'1','周检提前量取 risk.level 映射','1',NOW(),'1',NOW(),b'0'),
(710000000000002702,0,'risk_level','MEDIUM','一般','info',b'0',2,b'1','同上','1',NOW(),'1',NOW(),b'0'),
(710000000000002703,0,'risk_level','HIGH','较大','warning',b'0',3,b'1','同上','1',NOW(),'1',NOW(),b'0'),
(710000000000002704,0,'risk_level','MAJOR','重大','error',b'0',4,b'1','同上','1',NOW(),'1',NOW(),b'0');

-- ---------------------------------------------------------------------
-- 校验（执行后：dict_type 数应为 21 = v1.0 的 14 + 本次 7；行数应 +38）
-- ---------------------------------------------------------------------
-- SELECT dict_type, COUNT(*) FROM bidding_dict_data WHERE deleted = b'0' GROUP BY dict_type ORDER BY dict_type;
-- SELECT COUNT(*) FROM bidding_dict_data WHERE id BETWEEN 710000000000002001 AND 710000000000002799;

-- ---------------------------------------------------------------------
-- 补充（2026-09-13）：材料完备性「总体结论」与「逐项状态」两类字典
--   取值逐字来自 EnterpriseMaterialCompletenessRespVO 的 @Schema 说明：
--     总体结论 PASS 齐备 / RISK 部分缺失或临近到期 / FAIL 必备材料缺失；
--     逐项状态 READY 齐备 / EXPIRING 临近到期 / MISSING 缺失 / EXPIRED 已过期。
--   规范出处：契约 §1.1 字典类型表；用户硬约束"枚举与阈值一律动态配置"（前端不得硬编码中文与颜色）。
--   同期后端：MetaDictServiceImpl 新增 material_result / material_status 枚举映射，
--   并把"受支持类型"改为「枚举 ∪ 字典表已配置」，使纯字面量字典（cert_type 等）也能下发。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- material_result（3 条）
(710000000000002801,0,'material_result','PASS','齐备','success',b'0',1,b'1','材料完备性总体结论：必备材料齐备','1',NOW(),'1',NOW(),b'0'),
(710000000000002802,0,'material_result','RISK','部分缺失或临近到期','warning',b'0',2,b'1','可投标但需提示风险','1',NOW(),'1',NOW(),b'0'),
(710000000000002803,0,'material_result','FAIL','必备材料缺失','danger',b'0',3,b'1','不满足投标条件','1',NOW(),'1',NOW(),b'0'),
-- material_status（4 条）
(710000000000002804,0,'material_status','READY','齐备','success',b'0',1,b'1','材料在册且未临近到期','1',NOW(),'1',NOW(),b'0'),
(710000000000002805,0,'material_status','EXPIRING','临近到期','warning',b'0',2,b'1','进入到期预警窗口，窗口天数由规则配置驱动','1',NOW(),'1',NOW(),b'0'),
(710000000000002806,0,'material_status','MISSING','缺失','danger',b'0',3,b'1','未匹配到任何在册材料','1',NOW(),'1',NOW(),b'0'),
(710000000000002807,0,'material_status','EXPIRED','已过期','danger',b'0',4,b'1','有效期止早于基准日','1',NOW(),'1',NOW(),b'0');

-- ---------------------------------------------------------------------
-- 校验（执行后：dict_type 数应为 23 = v1.0 的 14 + 09-19 的 7 + 本次 2；行数 = v1.0 基线 + 45 行）
-- ---------------------------------------------------------------------
-- SELECT dict_type, COUNT(*) FROM bidding_dict_data WHERE deleted = b'0' GROUP BY dict_type ORDER BY dict_type;

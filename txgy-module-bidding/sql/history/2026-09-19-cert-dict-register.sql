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
-- 2026-09-20 核对说明：本段**永远插不进去**，且这是正确的——表上有唯一键
--   uk_dict(tenant_id, dict_type, dict_value, deleted)，而 IDENTITY/QUALIFICATION/STRENGTH
--   三个值已由 2026-09-16-rule-config-center.sql 提供（id ...0131~0133，标签为 身份类/资质类/实力类）。
--   INSERT IGNORE 按唯一键跳过，不报错。若要改用下面这版更细的标签（身份证明/资质证明/实力证明），
--   得先 DELETE 旧行或 UPDATE 标签，不能靠本段生效。
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
-- 2026-09-20 号段修正：原用 ...2801~2803，已被 2026-09-18-subject-company.sql 的
--   subject_company_type 先行占用（同一主键 ⇒ INSERT IGNORE 静默跳过 ⇒ 该类型在库里 0 行，
--   后端 MetaDictServiceImpl 与前端 material-check.vue 都拿不到中文）。
--   按 check-seed-id-collision.js 的建议改为 ...2916~2918（紧接 2901~2915 之后，库/两包均空闲）。
(710000000000002916,0,'material_result','PASS','齐备','success',b'0',1,b'1','材料完备性总体结论：必备材料齐备','1',NOW(),'1',NOW(),b'0'),
(710000000000002917,0,'material_result','RISK','部分缺失或临近到期','warning',b'0',2,b'1','可投标但需提示风险','1',NOW(),'1',NOW(),b'0'),
(710000000000002918,0,'material_result','FAIL','必备材料缺失','danger',b'0',3,b'1','不满足投标条件','1',NOW(),'1',NOW(),b'0'),
-- material_status（4 条）
(710000000000002804,0,'material_status','READY','齐备','success',b'0',1,b'1','材料在册且未临近到期','1',NOW(),'1',NOW(),b'0'),
(710000000000002805,0,'material_status','EXPIRING','临近到期','warning',b'0',2,b'1','进入到期预警窗口，窗口天数由规则配置驱动','1',NOW(),'1',NOW(),b'0'),
(710000000000002806,0,'material_status','MISSING','缺失','danger',b'0',3,b'1','未匹配到任何在册材料','1',NOW(),'1',NOW(),b'0'),
(710000000000002807,0,'material_status','EXPIRED','已过期','danger',b'0',4,b'1','有效期止早于基准日','1',NOW(),'1',NOW(),b'0');

-- qual_status（6 条）：资质主档状态。出处：《电子证件全流程管理_数据库设计.md》表 15-1 line 453
--   "VALID/EXPIRING_SOON/EXPIRED/PENDING_RENEW(待续期)/LIMITED(限期)/REVOKED"，中文逐字取自该处。
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(710000000000002808,0,'qual_status','VALID','有效','success',b'0',1,b'1','资质在有效期内','1',NOW(),'1',NOW(),b'0'),
(710000000000002809,0,'qual_status','EXPIRING_SOON','即将到期','warning',b'0',2,b'1','进入到期预警窗口，窗口天数由规则配置驱动','1',NOW(),'1',NOW(),b'0'),
(710000000000002810,0,'qual_status','EXPIRED','已过期','danger',b'0',3,b'1','有效期止早于当前日期','1',NOW(),'1',NOW(),b'0'),
(710000000000002811,0,'qual_status','PENDING_RENEW','待续期','info',b'0',4,b'1','已发起延续申报，主档置待续期（表 15-7 联动）','1',NOW(),'1',NOW(),b'0'),
(710000000000002812,0,'qual_status','LIMITED','限期','danger',b'0',5,b'1','被主管部门限期整改','1',NOW(),'1',NOW(),b'0'),
(710000000000002813,0,'qual_status','REVOKED','已吊销','danger',b'1',6,b'1','终态：吊销后不可恢复，需重新申请','1',NOW(),'1',NOW(),b'0');
-- qual_apply_type（6 条）：延续/升级等申报类型。取值 = 代码枚举 QualificationApplyTypeEnum；
--   中文为常规译名（NEW 新办/UPGRADE 升级/RENEW 延续/ADDITION 增项/CHANGE 变更/CANCEL 注销），
--   如与业务口径不一致，以本表后续维护为准（前端一律读字典，不写死）。
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(710000000000002814,0,'qual_apply_type','NEW','新办','info',b'0',1,b'1','首次申请该资质','1',NOW(),'1',NOW(),b'0'),
(710000000000002815,0,'qual_apply_type','UPGRADE','升级','info',b'0',2,b'1','等级提升申报','1',NOW(),'1',NOW(),b'0'),
(710000000000002816,0,'qual_apply_type','RENEW','延续','info',b'0',3,b'1','到期延续；当前后端仅支持该类型','1',NOW(),'1',NOW(),b'0'),
(710000000000002817,0,'qual_apply_type','ADDITION','增项','info',b'0',4,b'1','增加资质类别','1',NOW(),'1',NOW(),b'0'),
(710000000000002818,0,'qual_apply_type','CHANGE','变更','info',b'0',5,b'1','企业名称/地址等变更换证','1',NOW(),'1',NOW(),b'0'),
(710000000000002819,0,'qual_apply_type','CANCEL','注销','danger',b'1',6,b'1','主动注销资质','1',NOW(),'1',NOW(),b'0');
-- qual_apply_status（8 条）：申报流状态。出处：表 15-7 与 QualificationApplyStatusEnum javadoc
--   "DRAFT/SUBMITTED/ACCEPTED 受理/REVIEWING 审核/APPROVED/PUBLISHED 公示/ISSUED 领证/REJECTED 退回"。
--   terminal：ISSUED 已办结、REJECTED 已退回（该申报单终态；可另行发起新申报）。
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(710000000000002820,0,'qual_apply_status','DRAFT','草稿','info',b'0',1,b'1','未提交，可编辑','1',NOW(),'1',NOW(),b'0'),
(710000000000002821,0,'qual_apply_status','SUBMITTED','已提交','info',b'0',2,b'1','待受理/待审批','1',NOW(),'1',NOW(),b'0'),
(710000000000002822,0,'qual_apply_status','ACCEPTED','已受理','info',b'0',3,b'1','主管部门已受理','1',NOW(),'1',NOW(),b'0'),
(710000000000002823,0,'qual_apply_status','REVIEWING','审核中','warning',b'0',4,b'1','实质审核阶段','1',NOW(),'1',NOW(),b'0'),
(710000000000002824,0,'qual_apply_status','APPROVED','已批准','success',b'0',5,b'1','批准但尚未办结','1',NOW(),'1',NOW(),b'0'),
(710000000000002825,0,'qual_apply_status','PUBLISHED','公示中','info',b'0',6,b'1','公示期','1',NOW(),'1',NOW(),b'0'),
(710000000000002826,0,'qual_apply_status','ISSUED','已办结','success',b'1',7,b'1','已领证，回写主档有效期','1',NOW(),'1',NOW(),b'0'),
(710000000000002827,0,'qual_apply_status','REJECTED','已退回','danger',b'1',8,b'1','退回原因进备注链，可重新申报','1',NOW(),'1',NOW(),b'0');
-- safety_permit_status（5 条）：安全生产许可证状态。出处：SafetyPermitStatusEnum 与安许表（14-8）。
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(710000000000002828,0,'safety_permit_status','VALID','有效','success',b'0',1,b'1','安许证在有效期内','1',NOW(),'1',NOW(),b'0'),
(710000000000002829,0,'safety_permit_status','EXPIRING_SOON','即将到期','warning',b'0',2,b'1','进入到期预警窗口','1',NOW(),'1',NOW(),b'0'),
(710000000000002830,0,'safety_permit_status','EXPIRED','已过期','danger',b'0',3,b'1','有效期止早于当前日期','1',NOW(),'1',NOW(),b'0'),
(710000000000002831,0,'safety_permit_status','SUSPENDED','暂扣','danger',b'0',4,b'1','暂扣期间不可用于投标，可恢复','1',NOW(),'1',NOW(),b'0'),
(710000000000002832,0,'safety_permit_status','REVOKED','已吊销','danger',b'1',5,b'1','终态：吊销后不可恢复','1',NOW(),'1',NOW(),b'0');

-- ---------------------------------------------------------------------
-- 校验（执行后：dict_type 数应为 27 = v1.0 的 14 + 09-19 的 7 + 材料 2 + 资质流程 4；行数 = v1.0 基线 + 70 行）
-- ---------------------------------------------------------------------
-- SELECT dict_type, COUNT(*) FROM bidding_dict_data WHERE deleted = b'0' GROUP BY dict_type ORDER BY dict_type;

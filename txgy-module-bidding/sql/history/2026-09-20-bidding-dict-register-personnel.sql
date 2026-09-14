-- =====================================================================
-- 员工资质域字典类型注册与种子（承接 2026-09-19-cert-dict-register.sql）
-- =====================================================================
-- 依据：逐条比对「员工资质页（员工档案 + 履历：培训·社保·劳动合同）」与「资质台账页」实际请求的字典类型
--       与 bidding_dict_data 已注册集，发现 5 类缺种子 —— 前端只能降级显示英文 code（拿不到中文名与标签色）。
-- 取值来源（不臆造，逐条给出出处）：
--   personnel_status   ← PersonnelStatusEnum（ACTIVE/LEAVE/SUSPENDED/RETIRED；RETIRED 按枚举迁移表为终态）
--   social_pay_status  ← PersonnelSocialSecurityStatusEnum（NORMAL/SUSPENDED/TRANSFERRED/NONE）
--   gender             ← PersonnelProfileDO.gender 注释口径（MALE/FEMALE/UNKNOWN）
--   edu_level          ← PersonnelEduLevelEnum（DOCTOR/MASTER/BACHELOR/COLLEGE/SECONDARY/OTHER；remark 记 rank）
--   qual_serial        ← EnterpriseQualificationDO.qualSerial 注释口径（CONSTRUCTION/SURVEY/DESIGN/SUPERVISION/TESTING）
-- 不登记的两类（有意）：
--   qual_grade         —— 值为国标表110 的等级（特级/一级/二级…），无稳定 code 口径，登记反而制造第二套编码；
--                         前端候选走 /bidding/qualification/standard/options，展示回落原码。
--   qual_apply_type / qual_apply_status —— 已于 2026-09-19 注册（本次仅修正前端引用名，见前端提交）。
-- 设计说明：
--   1) 沿用 09-19 文件的 id 分块约定，本次占用 7100000000000030xx ~ 7100000000000034xx，不复用旧 id；
--   2) 幂等：INSERT IGNORE，可重复执行；
--   3) 前端一律 dictLabel/dictTag/dictOptions 优先、缺字典回落 code，本表是枚举的唯一动态来源
--      （用户硬约束：状态码/枚举值必须支持动态配置，严禁硬编码）。
-- 执行库：ruoyi-vue-pro（与 2026-09-19-cert-dict-register.sql 同库）。
-- 校验：执行后 dict_type 应新增 5 类；SELECT dict_type, COUNT(*) FROM bidding_dict_data
--       WHERE dict_type IN ('personnel_status','social_pay_status','gender','edu_level','qual_serial')
--       GROUP BY dict_type ORDER BY dict_type;  -- 期望 4 / 4 / 3 / 6 / 5
-- =====================================================================

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- personnel_status（4 条）
(710000000000003001,0,'personnel_status','ACTIVE','在职','success',b'0',1,b'1','正常在职，可承担岗位与证书挂载','1',NOW(),'1',NOW(),b'0'),
(710000000000003002,0,'personnel_status','LEAVE','离职','default',b'0',2,b'1','离职后一般不再回岗，复职需重新登记','1',NOW(),'1',NOW(),b'0'),
(710000000000003003,0,'personnel_status','SUSPENDED','停职','warning',b'0',3,b'1','停职期间不计入在岗人数','1',NOW(),'1',NOW(),b'0'),
(710000000000003004,0,'personnel_status','RETIRED','退休','default',b'1',4,b'1','终态：按 PersonnelStatusEnum 迁移表不可再流转','1',NOW(),'1',NOW(),b'0'),
-- social_pay_status（4 条）
(710000000000003101,0,'social_pay_status','NORMAL','正常缴纳','success',b'0',1,b'1','社保缴纳单位与申报主体一致（人证合一证据链）','1',NOW(),'1',NOW(),b'0'),
(710000000000003102,0,'social_pay_status','SUSPENDED','断缴','warning',b'0',2,b'1','断缴月份不计入连续缴纳','1',NOW(),'1',NOW(),b'0'),
(710000000000003103,0,'social_pay_status','TRANSFERRED','已转出','info',b'0',3,b'1','人员已转出至其他缴纳主体','1',NOW(),'1',NOW(),b'0'),
(710000000000003104,0,'social_pay_status','NONE','无缴纳','default',b'0',4,b'1','查无缴纳记录（挂证风险信号）','1',NOW(),'1',NOW(),b'0'),
-- gender（3 条）
(710000000000003201,0,'gender','MALE','男','default',b'0',1,b'1','取值口径见 PersonnelProfileDO.gender 注释','1',NOW(),'1',NOW(),b'0'),
(710000000000003202,0,'gender','FEMALE','女','default',b'0',2,b'1','同上','1',NOW(),'1',NOW(),b'0'),
(710000000000003203,0,'gender','UNKNOWN','未知','default',b'0',3,b'1','未采集/不适用','1',NOW(),'1',NOW(),b'0'),
-- edu_level（6 条；remark 记 PersonnelEduLevelEnum 的 rank，供"学历层次不低于"类判定）
(710000000000003301,0,'edu_level','DOCTOR','博士','success',b'0',1,b'1','PersonnelEduLevelEnum rank=5','1',NOW(),'1',NOW(),b'0'),
(710000000000003302,0,'edu_level','MASTER','硕士','success',b'0',2,b'1','rank=4','1',NOW(),'1',NOW(),b'0'),
(710000000000003303,0,'edu_level','BACHELOR','本科','info',b'0',3,b'1','rank=3','1',NOW(),'1',NOW(),b'0'),
(710000000000003304,0,'edu_level','COLLEGE','大专','info',b'0',4,b'1','rank=2','1',NOW(),'1',NOW(),b'0'),
(710000000000003305,0,'edu_level','SECONDARY','中专/高中','default',b'0',5,b'1','rank=1','1',NOW(),'1',NOW(),b'0'),
(710000000000003306,0,'edu_level','OTHER','其他','default',b'0',6,b'1','rank=0','1',NOW(),'1',NOW(),b'0'),
-- qual_serial（5 条）
(710000000000003401,0,'qual_serial','CONSTRUCTION','施工','success',b'0',1,b'1','EnterpriseQualificationDO.qualSerial 口径','1',NOW(),'1',NOW(),b'0'),
(710000000000003402,0,'qual_serial','SURVEY','勘察','info',b'0',2,b'1','同上','1',NOW(),'1',NOW(),b'0'),
(710000000000003403,0,'qual_serial','DESIGN','设计','info',b'0',3,b'1','同上','1',NOW(),'1',NOW(),b'0'),
(710000000000003404,0,'qual_serial','SUPERVISION','监理','info',b'0',4,b'1','同上','1',NOW(),'1',NOW(),b'0'),
(710000000000003405,0,'qual_serial','TESTING','检测','default',b'0',5,b'1','同上','1',NOW(),'1',NOW(),b'0');

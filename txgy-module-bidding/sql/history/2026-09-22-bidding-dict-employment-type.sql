-- =====================================================================
-- 用工形式字典注册与种子（承接 2026-09-20-bidding-dict-register-personnel.sql）
-- =====================================================================
-- 依据：后端 PersonnelemploymentType 类（PersonnelEmploymentTypeEnum）与
--       PersonnelProfileDO.employment_type 列注释，两者 code 口径逐字一致：
--       FULL_TIME / PART_TIME / LABOR_DISPATCH / RETIRED_REHIRE / OTHER。
-- 取值来源（不臆造，逐条给出出处）：
--   FULL_TIME       ← 枚举注释"全职（本企业正式员工，资质指标可用）"；isFullTime(code) 仅此值为真
--   PART_TIME       ← 枚举注释"兼职"
--   LABOR_DISPATCH  ← 枚举注释"劳务派遣（劳动关系在派遣单位，属挂靠高风险形态）"
--   RETIRED_REHIRE  ← 枚举注释"退休返聘"
--   OTHER           ← 枚举注释"其他"
-- 风险着色依据（不是配色偏好）：PersonnelEmploymentTypeEnum.isOutsourceRisk(code) 为真的两种形态
--   （LABOR_DISPATCH / PART_TIME）标 danger —— 枚举 Javadoc 明写"劳务派遣/兼职人员即使证书齐全，
--   也应在核验中判为风险项"；RETIRED_REHIRE 标 warning（可上岗但不计入本单位全职口径）。
-- 为什么要注册：前端「员工档案」本次新增"用工形式"列与录入项（此前该字段前端既不能录也不能看），
--   若字典未注册，页面只能降级显示英文 code —— 与用户硬约束"枚举必须支持动态配置、严禁硬编码"冲突。
-- 设计说明：
--   1) 沿用 09-19/09-20 的 id 分块约定，本次占用 7100000000000035xx，不复用旧 id；
--   2) 幂等：INSERT IGNORE，可重复执行；
--   3) 本表是"用工形式"枚举的唯一动态来源，前端一律 dictLabel/dictTag/dictOptions 优先、缺字典回落 code。
-- 执行库：ruoyi-vue-pro（与前面各字典注册脚本同库）。
-- 校验：SELECT dict_value, dict_label, color_type FROM bidding_dict_data
--       WHERE dict_type = 'employment_type' AND deleted = b'0' ORDER BY sort;  -- 期望 5 行
-- =====================================================================

INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(710000000000003501,0,'employment_type','FULL_TIME','全职','success',b'0',1,b'1','本企业正式员工，可计入资质人员指标（等价 15-3 is_full_time=TRUE）','1',NOW(),'1',NOW(),b'0'),
(710000000000003502,0,'employment_type','PART_TIME','兼职','danger',b'0',2,b'1','挂靠/人证分离高风险形态（PersonnelEmploymentTypeEnum.isOutsourceRisk）','1',NOW(),'1',NOW(),b'0'),
(710000000000003503,0,'employment_type','LABOR_DISPATCH','劳务派遣','danger',b'0',3,b'1','劳动关系在派遣单位，属挂靠高风险形态（isOutsourceRisk）','1',NOW(),'1',NOW(),b'0'),
(710000000000003504,0,'employment_type','RETIRED_REHIRE','退休返聘','warning',b'0',4,b'1','可承担岗位，但不计入本单位全职口径（is_full_time 为假）','1',NOW(),'1',NOW(),b'0'),
(710000000000003505,0,'employment_type','OTHER','其他','default',b'0',5,b'1','其它用工形式，核验时按非全职处理','1',NOW(),'1',NOW(),b'0');

package cn.iocoder.txgy.module.hrm.enums;

import cn.iocoder.txgy.framework.common.exception.ErrorCode;

/**
 * HRM 错误码枚举类
 *
 * HRM 系统，使用 1-050-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== 员工档案 1-050-100-000 ==========
    ErrorCode EMPLOYEE_NOT_EXISTS = new ErrorCode(1_050_100_000, "员工档案不存在");
    ErrorCode EMPLOYEE_JOB_NUMBER_DUPLICATE = new ErrorCode(1_050_100_001, "员工工号已存在");
    ErrorCode EMPLOYEE_USER_DUPLICATE = new ErrorCode(1_050_100_002, "系统用户已绑定员工档案");
    ErrorCode EMPLOYEE_MOBILE_DUPLICATE = new ErrorCode(1_050_100_003, "手机号已绑定员工档案");
    ErrorCode EMPLOYEE_IMPORT_LIST_IS_EMPTY = new ErrorCode(1_050_100_006, "导入员工档案数据不能为空");
    ErrorCode EMPLOYEE_IMPORT_DUPLICATE_STRATEGY_INVALID =
            new ErrorCode(1_050_100_033, "员工导入重复数据处理策略不合法");
    ErrorCode EMPLOYEE_LEADER_INVALID = new ErrorCode(1_050_100_014, "直属上级不能是员工本人或下属");
    ErrorCode EMPLOYEE_ENTRY_STATUS_INVALID = new ErrorCode(1_050_100_037, "员工【{}】不是在职状态");
    ErrorCode EMPLOYEE_IMPORT_REFERENCE_NOT_EXISTS =
            new ErrorCode(1_050_100_038, "导入字段【{}】的值【{}】不存在");
    // ========== 员工生命周期 1-050-100-004 ==========
    ErrorCode EMPLOYEE_CANDIDATE_CONVERTED = new ErrorCode(1_050_100_005, "招聘候选人已转为员工档案");
    ErrorCode EMPLOYEE_REHIRE_STATUS_INVALID = new ErrorCode(1_050_100_007, "只有已离职员工可以办理再入职");
    ErrorCode EMPLOYEE_CONFIRM_ENTRY_STATUS_INVALID = new ErrorCode(1_050_100_009, "只有待入职员工可以确认入职");
    ErrorCode EMPLOYEE_QUIT_STATUS_INVALID = new ErrorCode(1_050_100_010, "只有在职或待离职员工可以设置离职");
    ErrorCode EMPLOYEE_QUIT_CANCEL_STATUS_INVALID = new ErrorCode(1_050_100_011, "只有待离职员工可以取消离职");
    ErrorCode EMPLOYEE_CHANGE_STATUS_INVALID = new ErrorCode(1_050_100_025, "员工当前状态不允许办理【{}】");
    ErrorCode EMPLOYEE_CHANGE_TYPE_INVALID = new ErrorCode(1_050_100_026, "员工异动类型【{}】不合法");
    ErrorCode EMPLOYEE_QUIT_TIME_INVALID = new ErrorCode(1_050_100_028, "离职时间范围不合法");
    ErrorCode EMPLOYEE_RESOURCE_BELONG_INVALID = new ErrorCode(1_050_100_029, "员工{}不属于指定员工");
    ErrorCode EMPLOYEE_PERSONAL_NOTE_NOT_EXISTS = new ErrorCode(1_050_100_030, "员工个人备忘不存在");
    ErrorCode EMPLOYEE_CREATE_USER_LIST_DUPLICATE =
            new ErrorCode(1_050_100_031, "批量建档的后台用户不能重复");
    ErrorCode EMPLOYEE_CREATE_USER_NOT_EXISTS = new ErrorCode(1_050_100_032, "批量建档的后台用户不存在");

    // ========== 员工教育经历 1-050-100-016 ==========
    ErrorCode EMPLOYEE_EDUCATION_EXPERIENCE_NOT_EXISTS =
            new ErrorCode(1_050_100_016, "员工教育经历不存在");

    // ========== 员工工作经历 1-050-100-017 ==========
    ErrorCode EMPLOYEE_WORK_EXPERIENCE_NOT_EXISTS = new ErrorCode(1_050_100_017, "员工工作经历不存在");

    // ========== 员工培训经历 1-050-100-018 ==========
    ErrorCode EMPLOYEE_TRAINING_EXPERIENCE_NOT_EXISTS =
            new ErrorCode(1_050_100_018, "员工培训经历不存在");

    // ========== 员工证书 1-050-100-019 ==========
    ErrorCode EMPLOYEE_CERTIFICATE_NOT_EXISTS = new ErrorCode(1_050_100_019, "员工证书不存在");

    // ========== 员工联系人 1-050-100-020 ==========
    ErrorCode EMPLOYEE_CONTACT_NOT_EXISTS = new ErrorCode(1_050_100_020, "员工联系人不存在");

    // ========== 员工合同 1-050-100-021 ==========
    ErrorCode EMPLOYEE_CONTRACT_NOT_EXISTS = new ErrorCode(1_050_100_021, "员工合同不存在");

    // ========== 员工离职信息 1-050-100-023 ==========
    ErrorCode EMPLOYEE_QUIT_INFO_NOT_EXISTS = new ErrorCode(1_050_100_023, "员工离职信息不存在");

    // ========== 员工字段配置 1-050-100-024 ==========
    ErrorCode EMPLOYEE_FIELD_CONFIG_INVALID = new ErrorCode(1_050_100_024, "员工字段配置【{}】不合法");
    ErrorCode EMPLOYEE_FIELD_NOT_VISIBLE = new ErrorCode(1_050_100_034, "员工字段【{}】当前未显示，不能提交");
    ErrorCode EMPLOYEE_PROFILE_FIELD_REQUIRED = new ErrorCode(1_050_100_035, "员工档案字段【{}】不能为空");

    // ========== 招聘职位 1-050-200-000 ==========
    ErrorCode RECRUIT_POST_NOT_EXISTS = new ErrorCode(1_050_200_000, "招聘职位不存在");
    ErrorCode RECRUIT_POST_STATUS_TRANSITION_INVALID =
            new ErrorCode(1_050_200_028, "当前招聘职位状态不允许变更为目标状态");
    ErrorCode RECRUIT_POST_NOT_PUBLISHED = new ErrorCode(1_050_200_029, "招聘职位未发布，不能关联候选人");

    // ========== 招聘候选人 1-050-200-001 ==========
    ErrorCode RECRUIT_CANDIDATE_NOT_EXISTS = new ErrorCode(1_050_200_001, "招聘候选人不存在");
    ErrorCode RECRUIT_CANDIDATE_STATUS_TRANSITION_INVALID =
            new ErrorCode(1_050_200_011, "候选人当前状态不允许流转到【{}】");
    ErrorCode RECRUIT_CANDIDATE_HAS_EMPLOYEE =
            new ErrorCode(1_050_200_012, "候选人已生成员工档案，不能删除");
    ErrorCode RECRUIT_CANDIDATE_DELETE_STATUS_INVALID =
            new ErrorCode(1_050_200_016, "候选人当前状态不允许删除");
    ErrorCode RECRUIT_CANDIDATE_CONVERT_STATUS_INVALID =
            new ErrorCode(1_050_200_017, "候选人当前状态不允许转为员工");
    ErrorCode RECRUIT_CANDIDATE_ELIMINATE_STATUS_INVALID =
            new ErrorCode(1_050_200_018, "候选人当前状态不允许淘汰");
    ErrorCode RECRUIT_CANDIDATE_MOBILE_DUPLICATE =
            new ErrorCode(1_050_200_026, "候选人手机号已存在");

    // ========== 招聘面试 1-050-200-002 ==========
    ErrorCode RECRUIT_INTERVIEW_NOT_EXISTS = new ErrorCode(1_050_200_002, "招聘面试不存在");
    ErrorCode RECRUIT_INTERVIEW_FINISHED = new ErrorCode(1_050_200_009, "已完成的招聘面试不允许修改安排");
    ErrorCode RECRUIT_INTERVIEW_CANCEL_REASON_REQUIRED =
            new ErrorCode(1_050_200_010, "取消面试时必须填写取消原因");
    ErrorCode RECRUIT_INTERVIEW_STATE_INVALID =
            new ErrorCode(1_050_200_013, "候选人当前状态不允许安排或修改面试");
    ErrorCode RECRUIT_INTERVIEW_NOT_CURRENT =
            new ErrorCode(1_050_200_014, "只能操作候选人当前轮次的面试");
    ErrorCode RECRUIT_INTERVIEW_RESULT_INVALID =
            new ErrorCode(1_050_200_015, "当前面试状态或面试结果不允许提交");
    ErrorCode RECRUIT_INTERVIEW_MAX_ROUNDS_EXCEEDED =
            new ErrorCode(1_050_200_031, "招聘面试最多安排三轮");

    // ========== 招聘渠道 1-050-200-003 ==========
    ErrorCode RECRUIT_CHANNEL_NOT_EXISTS = new ErrorCode(1_050_200_003, "招聘渠道不存在");
    ErrorCode RECRUIT_CHANNEL_SYSTEM_NAME_UPDATE_FORBIDDEN =
            new ErrorCode(1_050_200_005, "系统内置招聘渠道不允许修改名称");
    ErrorCode RECRUIT_CHANNEL_SYSTEM_DELETE_FORBIDDEN =
            new ErrorCode(1_050_200_006, "系统内置招聘渠道不允许删除");
    ErrorCode RECRUIT_CHANNEL_TRANSFER_SELF =
            new ErrorCode(1_050_200_007, "承接招聘渠道不能是待删除渠道");
    ErrorCode RECRUIT_CHANNEL_TRANSFER_DISABLED = new ErrorCode(1_050_200_008, "承接招聘渠道已停用");
    ErrorCode RECRUIT_CHANNEL_DISABLED = new ErrorCode(1_050_200_030, "招聘渠道已停用");

    // ========== 招聘职位类型 1-050-200-004 ==========
    ErrorCode RECRUIT_POST_TYPE_NOT_EXISTS = new ErrorCode(1_050_200_004, "招聘职位类型不存在");
    ErrorCode RECRUIT_POST_TYPE_PARENT_ERROR = new ErrorCode(1_050_200_019, "父职位类型不能是自身或下属节点");
    ErrorCode RECRUIT_POST_TYPE_PARENT_NOT_EXISTS = new ErrorCode(1_050_200_020, "父职位类型不存在");
    ErrorCode RECRUIT_POST_TYPE_NAME_DUPLICATE = new ErrorCode(1_050_200_021, "同级职位类型名称已存在");
    ErrorCode RECRUIT_POST_TYPE_HAS_CHILDREN = new ErrorCode(1_050_200_022, "职位类型存在子类型，不能删除");
    ErrorCode RECRUIT_POST_TYPE_USED = new ErrorCode(1_050_200_023, "职位类型已被职位使用，不能删除");
    ErrorCode RECRUIT_POST_TYPE_DISABLED = new ErrorCode(1_050_200_024, "职位类型已停用");
    ErrorCode RECRUIT_POST_TYPE_NOT_LEAF = new ErrorCode(1_050_200_025, "职位类型必须选择叶子节点");
    ErrorCode RECRUIT_RESUME_NOT_EXISTS = new ErrorCode(1_050_200_027, "招聘简历版本不存在");

    // ========== 个人简历版本 1-050-200-034 ==========
    ErrorCode PERSONAL_RESUME_VERSION_NOT_EXISTS = new ErrorCode(1_050_200_034, "个人简历版本不存在");
    ErrorCode PERSONAL_RESUME_VERSION_CONTENT_EMPTY = new ErrorCode(1_050_200_035, "简历内容为空，无法保存版本");
    ErrorCode PERSONAL_RESUME_VERSION_PARENT_INVALID = new ErrorCode(1_050_200_036, "父版本不存在或不属于当前用户");
    ErrorCode PERSONAL_RESUME_VERSION_IDEMPOTENCY_CONFLICT =
            new ErrorCode(1_050_200_037, "该请求已提交过，请勿重复提交");
    ErrorCode RECRUIT_APPLICATION_RESUME_REQUIRED = new ErrorCode(1_050_200_038, "请选择用于投递的简历");

    // ========== 招聘 Agent 1-050_200-039 ==========
    ErrorCode AGENT_TASK_NOT_EXISTS = new ErrorCode(1_050_200_039, "Agent 任务不存在");
    ErrorCode AGENT_TASK_STATUS_INVALID = new ErrorCode(1_050_200_040, "Agent 任务状态不允许该操作");
    ErrorCode AGENT_TASK_IDEMPOTENCY_CONFLICT = new ErrorCode(1_050_200_041, "该请求已提交过，请勿重复提交");
    ErrorCode AGENT_MODEL_NOT_CONFIGURED = new ErrorCode(1_050_200_042, "AI 模型尚未配置，请联系管理员");
    ErrorCode AGENT_MODEL_CALL_FAILED = new ErrorCode(1_050_200_043, "AI 模型调用失败，请稍后重试");
    ErrorCode AGENT_MODEL_OUTPUT_INVALID = new ErrorCode(1_050_200_044, "AI 返回内容格式不符合要求");
    ErrorCode AGENT_SENSITIVE_MASK_FAILED = new ErrorCode(1_050_200_045, "敏感信息脱敏失败，已阻止本次模型调用");
    ErrorCode AGENT_RESUME_CONTENT_EMPTY = new ErrorCode(1_050_200_046, "简历内容为空或不足，无法分析");
    ErrorCode AGENT_POST_NOT_AVAILABLE = new ErrorCode(1_050_200_047, "目标职位不存在或已停止招聘");
    ErrorCode AGENT_MODEL_QUOTA_EXHAUSTED =
            new ErrorCode(1_050_200_048, "AI 模型额度不足或已用尽，请在模型平台充值或调整计费模式");

    // ========== 简历优化确认 1-050-200-049 ==========
    ErrorCode AGENT_OPTIMIZE_TASK_MISMATCH = new ErrorCode(1_050_200_049, "优化任务与简历版本不匹配");
    ErrorCode AGENT_OPTIMIZE_DECISION_INVALID = new ErrorCode(1_050_200_050, "确认结果不合法");
    ErrorCode AGENT_OPTIMIZE_ORIGINAL_NOT_FOUND = new ErrorCode(1_050_200_051, "原值在源简历版本中不存在");
    ErrorCode AGENT_OPTIMIZE_CONTENT_INVALID = new ErrorCode(1_050_200_052, "确认后简历内容不合法");

    // ========== 招聘 Agent 管理端 1-050-200-053 ==========
    ErrorCode AGENT_APPLICATION_NOT_EXISTS = new ErrorCode(1_050_200_053, "应聘记录不存在或无权访问");
    ErrorCode AGENT_APPLICATION_RESUME_UNAVAILABLE =
            new ErrorCode(1_050_200_054, "该应聘记录关联的简历不是在线结构化简历，暂不支持 AI 分析");

    // ========== 招聘沟通 1-050-200-060 ==========
    ErrorCode RECRUIT_CHAT_CONVERSATION_NOT_EXISTS = new ErrorCode(1_050_200_060, "招聘沟通会话不存在");
    ErrorCode RECRUIT_CHAT_NOT_PARTICIPANT = new ErrorCode(1_050_200_061, "无权查看该招聘沟通会话");
    ErrorCode RECRUIT_CHAT_SELF_NOT_ALLOWED = new ErrorCode(1_050_200_062, "不能与自己发起招聘沟通");
    ErrorCode RECRUIT_CHAT_OWNER_NOT_EXISTS = new ErrorCode(1_050_200_063, "该职位暂未配置可沟通的招聘负责人");
    ErrorCode RECRUIT_CHAT_ENDED = new ErrorCode(1_050_200_064, "该招聘沟通已结束");
    ErrorCode RECRUIT_CHAT_APPLY_NOT_QUALIFIED = new ErrorCode(1_050_200_065, "需等待招聘方回复后才能投递简历");
    ErrorCode RECRUIT_CHAT_END_REASON_REQUIRED = new ErrorCode(1_050_200_066, "结束沟通必须填写原因");
    ErrorCode RECRUIT_CHAT_CANDIDATE_FIRST = new ErrorCode(1_050_200_067, "需等待求职者先发起沟通");
    ErrorCode RECRUIT_CHAT_RESUME_NOT_EXISTS = new ErrorCode(1_050_200_068, "请先等待求职者发送简历");
    ErrorCode RECRUIT_CHAT_CANDIDATE_PROFILE_INCOMPLETE =
            new ErrorCode(1_050_200_069, "求职者资料不完整，暂时无法转为候选人");

    // ========== 招聘应聘流程 1-050-200-020 ==========
    ErrorCode RECRUIT_APPLICATION_NOT_EXISTS = new ErrorCode(1_050_200_020, "招聘应聘记录不存在");
    ErrorCode RECRUIT_APPLICATION_DUPLICATE = new ErrorCode(1_050_200_021, "该候选人存在同职位进行中的应聘");
    ErrorCode RECRUIT_APPLICATION_STATUS_TRANSITION_INVALID =
            new ErrorCode(1_050_200_022, "应聘当前状态不允许流转到【{}】");
    ErrorCode RECRUIT_APPLICATION_REASON_REQUIRED = new ErrorCode(1_050_200_023, "该操作必须填写原因");
    ErrorCode RECRUIT_OFFER_NOT_EXISTS = new ErrorCode(1_050_200_024, "招聘 Offer 不存在");
    ErrorCode RECRUIT_OFFER_STATE_INVALID = new ErrorCode(1_050_200_025, "招聘 Offer 状态不允许该操作");
    ErrorCode RECRUIT_OFFER_DUPLICATE = new ErrorCode(1_050_200_026, "该应聘已存在有效 Offer");
    ErrorCode RECRUIT_OFFER_POST_MISMATCH = new ErrorCode(1_050_200_032, "Offer 职位必须与应聘职位一致");
    ErrorCode RECRUIT_OFFER_DATA_INVALID = new ErrorCode(1_050_200_033, "Offer 薪资、有效期或入职日期不合法");

    // ========== 考勤组 1-050-300-002 ==========
    ErrorCode ATTENDANCE_GROUP_NOT_EXISTS = new ErrorCode(1_050_300_002, "考勤组不存在");
    ErrorCode ATTENDANCE_NAME_DUPLICATE = new ErrorCode(1_050_300_007, "考勤名称已存在");
    ErrorCode ATTENDANCE_DEFAULT_CANNOT_DELETE = new ErrorCode(1_050_300_008, "默认考勤配置不能删除");

    // ========== 考勤打卡 1-050-300-003 ==========
    ErrorCode ATTENDANCE_CLOCK_NOT_EXISTS = new ErrorCode(1_050_300_003, "打卡记录不存在");
    ErrorCode ATTENDANCE_CLOCK_NON_MANUAL_MODIFY_FORBIDDEN = new ErrorCode(1_050_300_028,
            "只有手工录入的打卡记录可以修改或删除");
    ErrorCode ATTENDANCE_CLOCK_TIME_INVALID = new ErrorCode(1_050_300_033,
            "应打卡时间或实际打卡时间不在员工班次窗口内");
    ErrorCode ATTENDANCE_CLOCK_REST_DAY = new ErrorCode(1_050_300_034, "今日休息，无需打卡");
    ErrorCode ATTENDANCE_CLOCK_NOT_ALLOWED = new ErrorCode(1_050_300_035, "当前不在可打卡时间");
    ErrorCode ATTENDANCE_CLOCK_POINT_INVALID = new ErrorCode(1_050_300_036, "不在考勤打卡范围内");
    ErrorCode ATTENDANCE_CLOCK_WIFI_INVALID = new ErrorCode(1_050_300_037, "未连接指定的考勤 WiFi");
    ErrorCode ATTENDANCE_CLOCK_GROUP_NOT_EXISTS = new ErrorCode(1_050_300_038, "未匹配到考勤组，无法打卡");

    // ========== 考勤节假日 1-050-300-006 ==========
    ErrorCode ATTENDANCE_HOLIDAY_NOT_EXISTS = new ErrorCode(1_050_300_006, "考勤节假日不存在");
    ErrorCode ATTENDANCE_HOLIDAY_DATE_DUPLICATE = new ErrorCode(1_050_300_029, "该日期已配置为考勤节假日");

    // ========== 考勤请假 1-050-300-030 ==========
    ErrorCode ATTENDANCE_LEAVE_NOT_EXISTS = new ErrorCode(1_050_300_030, "请假申请不存在");
    ErrorCode ATTENDANCE_LEAVE_TIME_CONFLICT = new ErrorCode(1_050_300_031, "请假时间与已有申请重叠");
    ErrorCode ATTENDANCE_LEAVE_STATUS_INVALID = new ErrorCode(1_050_300_032, "只有审批中的请假申请可以取消");

    // ========== 薪资配置 1-050-400-000 ==========
    ErrorCode SALARY_CONFIG_NOT_EXISTS = new ErrorCode(1_050_400_000, "薪资配置不存在");
    ErrorCode SALARY_CONFIG_EXISTS = new ErrorCode(1_050_400_022, "薪资配置已存在");

    // ========== 薪资组 1-050-400-001 ==========
    ErrorCode SALARY_GROUP_NOT_EXISTS = new ErrorCode(1_050_400_001, "薪资组不存在");
    ErrorCode SALARY_GROUP_NAME_DUPLICATE = new ErrorCode(1_050_400_002, "薪资组名称已存在");
    ErrorCode SALARY_GROUP_EMPLOYEE_CONFLICT = new ErrorCode(1_050_400_020, "所选员工已属于其他薪资组");
    ErrorCode SALARY_GROUP_DEPT_CONFLICT = new ErrorCode(1_050_400_021, "所选部门或其上下级部门已属于其他薪资组");

    // ========== 薪资项 1-050-400-003 ==========
    ErrorCode SALARY_OPTION_NOT_EXISTS = new ErrorCode(1_050_400_003, "薪资项不存在");
    ErrorCode SALARY_OPTION_CATEGORY_INVALID = new ErrorCode(1_050_400_028, "薪资项分类不存在或不可用");
    ErrorCode SALARY_OPTION_CODE_OCCUPIED = new ErrorCode(1_050_400_029,
            "薪资项编码 {} 已被历史或自定义数据占用，请先完成数据迁移");
    ErrorCode SALARY_OPTION_STANDARD_CANNOT_MODIFY =
            new ErrorCode(1_050_400_029, "标准薪资项不允许修改或删除");

    // ========== 薪资计税规则 1-050-400-005 ==========
    ErrorCode SALARY_TAX_RULE_NOT_EXISTS = new ErrorCode(1_050_400_005, "计税规则不存在");
    ErrorCode SALARY_TAX_RULE_NAME_DUPLICATE = new ErrorCode(1_050_400_023, "计税规则名称已存在");
    ErrorCode SALARY_TAX_RULE_USED = new ErrorCode(1_050_400_027, "计税规则已被薪资组使用，不能删除");

    // ========== 员工薪资信息 1-050-400-006 ==========
    ErrorCode SALARY_EMPLOYEE_INFO_NOT_EXISTS = new ErrorCode(1_050_400_006, "员工薪资信息不存在");
    ErrorCode SALARY_DATA_ILLEGAL = new ErrorCode(1_050_400_014, "薪资数据不合法");
    ErrorCode SALARY_IMPORT_LIST_IS_EMPTY = new ErrorCode(1_050_400_015, "导入薪资档案数据不能为空");

    // ========== 定薪调薪记录 1-050-400-007 ==========
    ErrorCode SALARY_CHANGE_RECORD_NOT_EXISTS = new ErrorCode(1_050_400_007, "定薪调薪记录不存在");
    ErrorCode SALARY_PENDING_CHANGE_RECORD_EXISTS = new ErrorCode(1_050_400_016, "员工存在待生效的定薪调薪记录");
    ErrorCode SALARY_CHANGE_RECORD_STATUS_INVALID = new ErrorCode(1_050_400_017, "当前定薪调薪记录状态不允许执行该操作");
    ErrorCode SALARY_CHANGE_EFFECT_DATE_INVALID =
            new ErrorCode(1_050_400_030, "调薪生效日期不能为空，且不能早于最近工资月份");

    // ========== 月度工资表 1-050-400-008 ==========
    ErrorCode SALARY_MONTH_RECORD_NOT_EXISTS = new ErrorCode(1_050_400_008, "月度工资表不存在");
    ErrorCode SALARY_MONTH_RECORD_EXISTS = new ErrorCode(1_050_400_009, "该月份工资表已存在");
    ErrorCode SALARY_MONTH_EMP_RECORD_NOT_EXISTS = new ErrorCode(1_050_400_010, "员工月度工资记录不存在");
    ErrorCode SALARY_MONTH_RECORD_STATUS_INVALID = new ErrorCode(1_050_400_018, "当前工资表状态不允许执行该操作");
    ErrorCode SALARY_MONTH_RECORD_NOT_LATEST =
            new ErrorCode(1_050_400_024, "只能删除最新的月度工资表");
    ErrorCode SALARY_MONTH_RECORD_CANNOT_DELETE_ONLY =
            new ErrorCode(1_050_400_025, "至少需要保留一张月度工资表");

    // ========== 工资条 1-050-400-011 ==========
    ErrorCode SALARY_SLIP_TEMPLATE_NOT_EXISTS = new ErrorCode(1_050_400_011, "工资条模板不存在");
    ErrorCode SALARY_SLIP_RECORD_NOT_EXISTS = new ErrorCode(1_050_400_012, "工资条发放记录不存在");
    ErrorCode SALARY_SLIP_NOT_EXISTS = new ErrorCode(1_050_400_013, "工资条不存在");
    ErrorCode SALARY_SLIP_ALREADY_SENT = new ErrorCode(1_050_400_030, "员工工资条已经发放，请勿重复操作");
    ErrorCode SALARY_SLIP_EMPLOYEE_ACCOUNT_NOT_EXISTS = new ErrorCode(1_050_400_031,
            "所选员工未绑定后台账号，无法发放工资条");

    // ========== 社保方案 1-050-500-000 ==========
    ErrorCode INSURANCE_SCHEME_NOT_EXISTS = new ErrorCode(1_050_500_000, "社保方案不存在");
    ErrorCode INSURANCE_SCHEME_NAME_DUPLICATE = new ErrorCode(1_050_500_001, "社保方案名称已存在");
    ErrorCode INSURANCE_SCHEME_USED = new ErrorCode(1_050_500_002, "社保方案已被员工使用，不能删除");
    ErrorCode INSURANCE_SCHEME_PROJECT_EMPTY = new ErrorCode(1_050_500_011, "社保方案至少需要一个社保项目");
    ErrorCode INSURANCE_SCHEME_PROJECT_TYPE_DUPLICATE = new ErrorCode(1_050_500_012, "社保方案项目类型重复");
    ErrorCode INSURANCE_SCHEME_AREA_INVALID = new ErrorCode(1_050_500_024, "参保地区必须选择城市或区县");

    // ========== 员工社保信息 1-050-500-003 ==========
    ErrorCode INSURANCE_EMPLOYEE_INFO_NOT_EXISTS = new ErrorCode(1_050_500_003, "员工参保信息不存在");
    ErrorCode INSURANCE_EMPLOYEE_INFO_EMPLOYEE_MISMATCH = new ErrorCode(1_050_500_015,
            "员工参保信息不能变更所属员工");
    ErrorCode INSURANCE_EMP_STATUS_ILLEGAL = new ErrorCode(1_050_500_018, "员工参保状态不合法");
    ErrorCode INSURANCE_EMPLOYEE_SCHEME_NOT_CONFIGURED = new ErrorCode(1_050_500_025,
            "员工尚未配置有效的社保方案");

    // ========== 月度社保表 1-050-500-004 ==========
    ErrorCode INSURANCE_MONTH_RECORD_NOT_EXISTS = new ErrorCode(1_050_500_004, "月度社保表不存在");
    ErrorCode INSURANCE_MONTH_RECORD_EXISTS = new ErrorCode(1_050_500_005, "该月份社保表已存在");
    ErrorCode INSURANCE_MONTH_EMP_RECORD_NOT_EXISTS = new ErrorCode(1_050_500_006, "员工月度社保记录不存在");
    ErrorCode INSURANCE_DATA_ILLEGAL = new ErrorCode(1_050_500_007, "社保数据不合法");
    ErrorCode INSURANCE_MONTH_RECORD_ARCHIVED = new ErrorCode(1_050_500_009, "月度社保表已归档，不能修改");
    ErrorCode INSURANCE_MONTH_YEAR_MONTH_ILLEGAL = new ErrorCode(1_050_500_017, "社保表年月不合法");
    ErrorCode INSURANCE_MONTH_RECORD_NOT_LATEST = new ErrorCode(1_050_500_020, "只能删除最新的月度社保表");
    ErrorCode INSURANCE_MONTH_RECORD_CANNOT_DELETE_ONLY = new ErrorCode(1_050_500_021,
            "至少需要保留一张月度社保表");
    ErrorCode INSURANCE_FIRST_MONTH_RECORD_EXISTS = new ErrorCode(1_050_500_023,
            "已存在社保表，请新建次月社保表");
    ErrorCode INSURANCE_MONTH_EMPLOYEE_NOT_ELIGIBLE = new ErrorCode(1_050_500_026,
            "员工在所选月份不符合参保条件");

    // ========== 员工工资卡 1-050-500-008 ==========
    ErrorCode EMPLOYEE_SALARY_CARD_NOT_EXISTS = new ErrorCode(1_050_500_008, "员工工资卡不存在");
    ErrorCode EMPLOYEE_SALARY_CARD_EMPLOYEE_MISMATCH = new ErrorCode(1_050_500_016,
            "员工工资卡不能变更所属员工");

    // ========== 绩效考核模板 1-050-600-000 ==========
    ErrorCode PERFORMANCE_ASSESSMENT_TEMPLATE_NOT_EXISTS = new ErrorCode(1_050_600_000, "绩效考核模板不存在");
    ErrorCode PERFORMANCE_ASSESSMENT_TEMPLATE_NAME_DUPLICATE =
            new ErrorCode(1_050_600_001, "绩效考核模板名称已存在");

    // ========== 绩效结果模板 1-050-600-002 ==========
    ErrorCode PERFORMANCE_RESULT_TEMPLATE_NOT_EXISTS = new ErrorCode(1_050_600_002, "绩效结果模板不存在");
    ErrorCode PERFORMANCE_RESULT_TEMPLATE_NAME_DUPLICATE =
            new ErrorCode(1_050_600_003, "绩效结果模板名称已存在");

    // ========== 绩效计划 1-050-600-004 ==========
    ErrorCode PERFORMANCE_PLAN_NOT_EXISTS = new ErrorCode(1_050_600_004, "绩效计划不存在");
    ErrorCode PERFORMANCE_PLAN_NAME_DUPLICATE = new ErrorCode(1_050_600_005, "绩效计划名称已存在");
    ErrorCode PERFORMANCE_PLAN_STARTED_CANNOT_MODIFY =
            new ErrorCode(1_050_600_014, "绩效计划启动后不能修改计划配置或参评员工");
    ErrorCode PERFORMANCE_PLAN_STATUS_NOT_ALLOW_DELETE =
            new ErrorCode(1_050_600_025, "当前状态的绩效计划不允许删除");

    // ========== 员工绩效考核 1-050-600-006 ==========
    ErrorCode PERFORMANCE_ASSESSMENT_NOT_EXISTS = new ErrorCode(1_050_600_006, "员工绩效考核不存在");
    ErrorCode PERFORMANCE_DATA_ILLEGAL = new ErrorCode(1_050_600_007, "绩效数据不合法");
    ErrorCode PERFORMANCE_RESULT_LEVEL_NOT_MATCH =
            new ErrorCode(1_050_600_028, "绩效总分未匹配到结果等级，请检查绩效结果模板");

    // ========== 绩效流程 1-050-600-008 ==========
    ErrorCode PERFORMANCE_STAGE_ACTION_INVALID = new ErrorCode(1_050_600_008, "当前绩效阶段不允许执行该操作");
    ErrorCode PERFORMANCE_PROCESS_RUNNING_CANNOT_MODIFY =
            new ErrorCode(1_050_600_009, "绩效流程处理中，不能修改当前记录");
    ErrorCode PERFORMANCE_PROCESS_CONFIG_INVALID =
            new ErrorCode(1_050_600_024, "绩效流程配置不合法");
    ErrorCode PERFORMANCE_STAGE_NO_PERMISSION =
            new ErrorCode(1_050_600_027, "当前用户不是该绩效阶段的处理人");
    ErrorCode PERFORMANCE_STAGE_HANDLER_USER_NOT_BOUND =
            new ErrorCode(1_050_600_026, "绩效必办节点处理人【{}】未绑定后台账号");

    // ========== 绩效评分阶段 1-050-600-011 ==========
    ErrorCode PERFORMANCE_REVIEW_STAGE_CONFIG_INVALID = new ErrorCode(1_050_600_011, "绩效评分流程配置不合法");
    ErrorCode PERFORMANCE_REVIEW_STAGE_NOT_EXISTS = new ErrorCode(1_050_600_012, "绩效评分任务不存在");
    ErrorCode PERFORMANCE_REVIEW_STAGE_NO_PERMISSION =
            new ErrorCode(1_050_600_013, "当前用户不是该绩效阶段的评分人");
    ErrorCode PERFORMANCE_REVIEW_STAGE_REJECT_INVALID =
            new ErrorCode(1_050_600_015, "当前绩效评分阶段不能驳回上一阶段");

    // ========== 绩效阶段流转 1-050-600-016 ==========
    ErrorCode PERFORMANCE_INTERVIEW_NOT_READY =
            new ErrorCode(1_050_600_016, "绩效结果审核尚未全部完成，无法发起绩效面谈");
    ErrorCode PERFORMANCE_ARCHIVE_NOT_READY =
            new ErrorCode(1_050_600_017, "绩效面谈或申诉尚未全部结束，无法归档");
    ErrorCode PERFORMANCE_SCORING_NOT_READY =
            new ErrorCode(1_050_600_018, "指标制定或目标确认尚未全部完成，无法开启评分");

    // ========== 绩效指标 1-050-600-019 ==========
    ErrorCode PERFORMANCE_QUOTA_SETTING_INVALID =
            new ErrorCode(1_050_600_019, "绩效指标制定数据不合法");

    // ========== 绩效目标确认 1-050-600-020 ==========
    ErrorCode PERFORMANCE_TARGET_CONFIRM_CONFIG_INVALID =
            new ErrorCode(1_050_600_020, "绩效目标确认人配置不合法");
    ErrorCode PERFORMANCE_TARGET_CONFIRM_NO_PERMISSION =
            new ErrorCode(1_050_600_021, "当前用户不是该绩效目标的确认人");

    // ========== 证件类型 1-050-700-000 ==========
    ErrorCode CERTIFICATE_TYPE_NOT_EXISTS = new ErrorCode(1_050_700_000, "证件类型不存在");
    ErrorCode CERTIFICATE_TYPE_NAME_DUPLICATE = new ErrorCode(1_050_700_001, "证件类型名称已存在");
    ErrorCode CERTIFICATE_TYPE_USED = new ErrorCode(1_050_700_002, "证件类型已被证件台账使用，不能删除");
    ErrorCode CERTIFICATE_TYPE_DISABLED = new ErrorCode(1_050_700_003, "证件类型【{}】已停用");
    ErrorCode CERTIFICATE_TYPE_SPECIAL_OPERATION_REVIEW_REQUIRED =
            new ErrorCode(1_050_700_004, "特种作业证件必须开启复审并配置复审周期");
    ErrorCode CERTIFICATE_TYPE_CODE_DUPLICATE = new ErrorCode(1_050_700_005, "证件类型编码已存在");

    // ========== 证件台账 1-050-700-010 ==========
    ErrorCode CERTIFICATE_LEDGER_NOT_EXISTS = new ErrorCode(1_050_700_010, "证件台账记录不存在");
    ErrorCode CERTIFICATE_LEDGER_END_TIME_INVALID =
            new ErrorCode(1_050_700_011, "证件有效结束日期不能早于有效开始日期");
    ErrorCode CERTIFICATE_LEDGER_ATTACHMENT_REQUIRED =
            new ErrorCode(1_050_700_012, "证件类型【{}】要求上传证件附件");
    ErrorCode CERTIFICATE_LEDGER_CANCELLED =
            new ErrorCode(1_050_700_013, "证件已注销，不能办理续证或复审");
    ErrorCode CERTIFICATE_LEDGER_RENEWAL_REQUIRED =
            new ErrorCode(1_050_700_014, "证件类型【{}】需要复审，请先办理续证或复审");
    ErrorCode CERTIFICATE_LEDGER_STATUS_INVALID =
            new ErrorCode(1_050_700_015, "证件状态只能手工设置为注销或恢复为有效");

    // ========== 证件续证/复审 1-050-700-020 ==========
    ErrorCode CERTIFICATE_RENEWAL_NOT_EXISTS = new ErrorCode(1_050_700_020, "证件续证/复审记录不存在");
    ErrorCode CERTIFICATE_RENEWAL_TIME_INVALID =
            new ErrorCode(1_050_700_021, "续证/复审后的有效期不能早于原证件有效期");
    ErrorCode CERTIFICATE_RENEWAL_REVIEW_TIME_REQUIRED =
            new ErrorCode(1_050_700_022, "复审类型必须填写复审日期");
    ErrorCode CERTIFICATE_RENEWAL_REVIEW_TIME_NOT_ALLOWED =
            new ErrorCode(1_050_700_023, "续证类型不允许填写复审日期");
    ErrorCode CERTIFICATE_RENEWAL_TYPE_INVALID = new ErrorCode(1_050_700_024, "续证/复审类型不合法");

    // ========== 证件预警 1-050-700-030 ==========
    ErrorCode CERTIFICATE_WARNING_NOT_EXISTS = new ErrorCode(1_050_700_030, "证件预警记录不存在");
    ErrorCode CERTIFICATE_WARNING_STATUS_INVALID =
            new ErrorCode(1_050_700_031, "当前预警状态不允许执行该操作");
    ErrorCode CERTIFICATE_WARNING_ADVANCE_DAYS_INVALID =
            new ErrorCode(1_050_700_032, "证件类型【{}】的提前预警天数不合法");

    // ========== HRM 配置 1-050-800-000 ==========
    ErrorCode HRM_CONFIG_NOT_EXISTS = new ErrorCode(1_050_800_000, "HRM 配置不存在");

    // ========== 调薪模板 1-050-800-002 ==========
    ErrorCode SALARY_CHANGE_TEMPLATE_NOT_EXISTS = new ErrorCode(1_050_800_002, "调薪模板不存在");
    ErrorCode SALARY_CHANGE_TEMPLATE_DEFAULT_CANNOT_DELETE =
            new ErrorCode(1_050_800_003, "默认调薪模板不能删除");
    ErrorCode SALARY_CHANGE_TEMPLATE_OPTION_INVALID =
            new ErrorCode(1_050_800_004, "调薪模板包含无效或重复的薪资项");

    // ========== HRM 首页 1-050-900-001 ==========
    ErrorCode HOME_CALENDAR_DATE_RANGE_ILLEGAL = new ErrorCode(1_050_900_001, "HRM 首页日期范围不合法");

}

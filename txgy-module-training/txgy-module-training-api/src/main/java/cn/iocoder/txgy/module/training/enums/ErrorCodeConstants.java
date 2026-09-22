package cn.iocoder.txgy.module.training.enums;

import cn.iocoder.txgy.framework.common.exception.ErrorCode;

/**
 * Training 错误码枚举类
 *
 * training 模块，使用 1-061-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== 课程（course）1-061-001-000 ==========
    ErrorCode COURSE_NOT_EXISTS = new ErrorCode(1_061_001_000, "课程不存在");
    ErrorCode COURSE_NOT_CURRENT_VERSION = new ErrorCode(1_061_001_001, "仅允许对当前生效版本进行操作");
    ErrorCode COURSE_STATUS_NOT_ALLOW_UPDATE = new ErrorCode(1_061_001_002, "课程当前状态【{}】不允许编辑，需先撤审或退回草稿");
    ErrorCode COURSE_STATUS_NOT_ALLOW_DELETE = new ErrorCode(1_061_001_003, "课程当前状态【{}】不允许删除");
    ErrorCode COURSE_STATUS_NOT_ALLOW_SUBMIT = new ErrorCode(1_061_001_004, "课程当前状态【{}】不允许提交审核");
    ErrorCode COURSE_STATUS_NOT_ALLOW_WITHDRAW = new ErrorCode(1_061_001_005, "课程当前状态【{}】不允许撤审");
    ErrorCode COURSE_STATUS_NOT_ALLOW_APPROVE = new ErrorCode(1_061_001_006, "课程当前状态【{}】不允许审核通过");
    ErrorCode COURSE_STATUS_NOT_ALLOW_REJECT = new ErrorCode(1_061_001_007, "课程当前状态【{}】不允许驳回");
    ErrorCode COURSE_STATUS_NOT_ALLOW_ONLINE = new ErrorCode(1_061_001_008, "课程当前状态【{}】不允许上架");
    ErrorCode COURSE_STATUS_NOT_ALLOW_OFFLINE = new ErrorCode(1_061_001_009, "课程当前状态【{}】不允许下架");
    ErrorCode COURSE_REJECT_REASON_REQUIRED = new ErrorCode(1_061_001_010, "驳回原因不能为空");
    ErrorCode COURSE_MEDIA_REQUIRED = new ErrorCode(1_061_001_011, "课程尚未绑定任何媒资，不能上架");
    ErrorCode COURSE_ONLY_ONLINE_CAN_COMMENT = new ErrorCode(1_061_001_012, "仅【已上架】课程支持评论");
    ErrorCode COURSE_STATUS_NOT_ALLOW_PUBLISH = new ErrorCode(1_061_001_013, "课程当前状态【{}】不允许发布新版本");

    // ========== 课程分类（course category）1-061-004-000 ==========
    ErrorCode COURSE_CATEGORY_NOT_EXISTS = new ErrorCode(1_061_004_000, "课程分类不存在");
    ErrorCode COURSE_CATEGORY_NAME_EXISTS = new ErrorCode(1_061_004_001, "同一父级下已存在同名课程分类");
    ErrorCode COURSE_CATEGORY_HAS_COURSE = new ErrorCode(1_061_004_002, "该分类下存在课程，禁止删除（请先转移课程）");
    ErrorCode COURSE_CATEGORY_HAS_CHILDREN = new ErrorCode(1_061_004_003, "该分类下存在子分类，禁止删除（请先删除子分类）");
    ErrorCode COURSE_CATEGORY_PARENT_NOT_EXISTS = new ErrorCode(1_061_004_004, "父级课程分类不存在");
    ErrorCode COURSE_CATEGORY_LEVEL_MAX = new ErrorCode(1_061_004_005, "课程分类最多支持三级（域/组/课）");

    // ========== 课程媒资（course media）1-061-002-000 ==========
    ErrorCode COURSE_MEDIA_NOT_EXISTS = new ErrorCode(1_061_002_000, "课程媒资不存在");
    ErrorCode COURSE_MEDIA_URL_DUPLICATE = new ErrorCode(1_061_002_001, "已存在同 URL 的媒资，请勿重复绑定");
    ErrorCode COURSE_MEDIA_TYPE_INVALID = new ErrorCode(1_061_002_002, "媒资类型不合法，仅支持 1文档 2视频 3图片 4VR/3D仿真");
    ErrorCode COURSE_MEDIA_URL_REQUIRED = new ErrorCode(1_061_002_003, "非 VR 类型媒资必须填写访问地址");
    ErrorCode COURSE_MEDIA_REF_REQUIRED = new ErrorCode(1_061_002_004, "VR 媒资必须填写项目编号（对接 VR 平台，占位）");

    // ========== 课程评论（course comment）1-061-003-000 ==========
    ErrorCode COURSE_COMMENT_NOT_EXISTS = new ErrorCode(1_061_003_000, "课程评论不存在");
    ErrorCode COURSE_COMMENT_DELETE_NOT_ALLOW = new ErrorCode(1_061_003_001, "仅可删除自己的评论");
    ErrorCode COURSE_COMMENT_ALREADY_DELETED = new ErrorCode(1_061_003_002, "评论已删除，请勿重复操作");

    // ========== 课程生命周期（course lifecycle）1-061-005-000 ==========
    ErrorCode COURSE_DELETE_HAS_LEARNING_REF = new ErrorCode(1_061_005_000, "课程已被学员学习引用，禁止删除（如需下线请走下架流程）");

    // ========== 资源中心-分类（resource category）1-061-006-000 ==========
    ErrorCode RESOURCE_CATEGORY_TYPE_INVALID = new ErrorCode(1_061_006_000, "分类类型不合法，仅支持 doc 文档/video 视频/question 试题");
    ErrorCode RESOURCE_CATEGORY_NOT_EXISTS = new ErrorCode(1_061_006_001, "分类不存在");
    ErrorCode RESOURCE_CATEGORY_NAME_EXISTS = new ErrorCode(1_061_006_002, "已存在同类型下同名分类");
    ErrorCode RESOURCE_CATEGORY_HAS_CHILDREN = new ErrorCode(1_061_006_003, "该分类存在子分类，禁止删除（请先删除子分类）");
    ErrorCode RESOURCE_CATEGORY_HAS_RESOURCE = new ErrorCode(1_061_006_004, "该分类下仍存在资源，禁止删除（请先转移或删除资源）");
    ErrorCode RESOURCE_CATEGORY_PARENT_INVALID = new ErrorCode(1_061_006_005, "父分类不合法：仅支持选择一级、二级分类，不支持四级及以上层级");

    // ========== 资源中心-文档（document resource）1-061-007-000 ==========
    ErrorCode DOCUMENT_NOT_EXISTS = new ErrorCode(1_061_007_000, "文档资源不存在");
    ErrorCode DOCUMENT_FILE_TYPE_INVALID = new ErrorCode(1_061_007_001, "仅支持上传 PDF / Word(doc,docx) / WPS(wps) / 图片(jpg,png) 文档");
    ErrorCode DOCUMENT_FILE_SIZE_EXCEED = new ErrorCode(1_061_007_002, "文档大小超出上限（50MB）");
    ErrorCode DOCUMENT_STATUS_CHANGE_INVALID = new ErrorCode(1_061_007_003, "文档当前状态不允许执行该操作");
    ErrorCode DOCUMENT_REFERENCED_DELETE_FORBIDDEN = new ErrorCode(1_061_007_004, "该文档已被引用，禁止删除（可下架或归档）");

    // ========== 资源中心-视频（video resource）1-061-008-000 ==========
    ErrorCode VIDEO_NOT_EXISTS = new ErrorCode(1_061_008_000, "视频资源不存在");
    ErrorCode VIDEO_FILE_TYPE_INVALID = new ErrorCode(1_061_008_001, "仅支持上传 MP4/MOV 视频（建议 H.264 编码）");
    ErrorCode VIDEO_FILE_SIZE_EXCEED = new ErrorCode(1_061_008_002, "视频大小超出上限（500MB）");
    ErrorCode VIDEO_STATUS_CHANGE_INVALID = new ErrorCode(1_061_008_003, "视频当前状态不允许执行该操作");
    ErrorCode VIDEO_REFERENCED_DELETE_FORBIDDEN = new ErrorCode(1_061_008_004, "该视频已被引用，禁止删除（可下架或归档）");
    ErrorCode VIDEO_CREDIT_HOURS_INVALID = new ErrorCode(1_061_008_005, "学时值必须大于 0 且最多保留两位小数");
    ErrorCode VIDEO_TYPE_INVALID = new ErrorCode(1_061_008_006, "视频类型不合法：1 短视频 2 录播");
    ErrorCode VIDEO_DEFINITION_INVALID = new ErrorCode(1_061_008_007, "清晰度不合法：仅支持 2160P/1080P/720P/480P/360P");

    // ========== 资源中心-试题（question）1-061-009-000 ==========
    ErrorCode QUESTION_NOT_EXISTS = new ErrorCode(1_061_009_000, "试题不存在");
    ErrorCode QUESTION_TYPE_INVALID = new ErrorCode(1_061_009_001, "题型不合法：仅支持 single单选/multiple多选/judge判断/qa实操问答/case案例分析");
    ErrorCode QUESTION_DIFFICULTY_INVALID = new ErrorCode(1_061_009_002, "难度不合法：1易 2中 3难");
    ErrorCode QUESTION_REFERENCED_DELETE_FORBIDDEN = new ErrorCode(1_061_009_003, "该题已被试卷引用，禁止删除（请改为停用）");
    ErrorCode QUESTION_STATUS_CHANGE_INVALID = new ErrorCode(1_061_009_004, "试题当前状态不允许执行该操作");
    ErrorCode QUESTION_IMPORT_EMPTY = new ErrorCode(1_061_009_005, "导入文件为空或不含有效数据行");
    ErrorCode QUESTION_IMPORT_PARSE_ERROR = new ErrorCode(1_061_009_006, "导入文件解析失败，请使用系统下载的标准 Excel 模板");
    ErrorCode QUESTION_IMPORT_HEADER_INVALID = new ErrorCode(1_061_009_013, "未识别到导入表头，请使用【下载模板】的 Excel 格式（表头行需包含“题型”“题干”列）");
    // AI 智能导入（文档 → 试题）
    ErrorCode QUESTION_AI_NOT_CONFIGURED = new ErrorCode(1_061_009_007, "未配置 AI 大模型（请联系管理员配置模型服务地址与密钥）");
    ErrorCode QUESTION_AI_DOC_TYPE_INVALID = new ErrorCode(1_061_009_008, "AI 导入仅支持 PDF / Word(doc,docx) / WPS(wps) 文档");
    ErrorCode QUESTION_AI_DOC_SIZE_EXCEED = new ErrorCode(1_061_009_009, "AI 导入文档大小超出上限（20MB）");
    ErrorCode QUESTION_AI_DOC_EMPTY = new ErrorCode(1_061_009_010, "未能从文档中提取到有效文本，请确认文档内容可读");
    ErrorCode QUESTION_AI_EXTRACT_FAILED = new ErrorCode(1_061_009_011, "AI 大模型提取试题失败，请稍后重试或改用模板导入");
    ErrorCode QUESTION_AI_RESULT_EMPTY = new ErrorCode(1_061_009_012, "AI 未从文档中识别出试题，可补充试题描述后重试");

    // ========== 资源中心-标签（tag）1-061-010-000 ==========
    ErrorCode RESOURCE_TAG_NOT_EXISTS = new ErrorCode(1_061_010_000, "标签不存在");
    ErrorCode RESOURCE_TAG_DIMENSION_INVALID = new ErrorCode(1_061_010_001, "标签维度不合法：work_type工种/course课程/safety安全/process工艺");
    ErrorCode RESOURCE_TAG_NAME_EXISTS = new ErrorCode(1_061_010_002, "已存在同维度同名标签");
    ErrorCode RESOURCE_TAG_DELETE_HAS_QUESTION = new ErrorCode(1_061_010_003, "该标签已被试题引用，禁止删除");

    // ========== 资源中心-文件/通用（file & common）1-061-011-000 ==========
    ErrorCode RESOURCE_FILE_NOT_FOUND = new ErrorCode(1_061_011_000, "资源文件不存在或已被清理");
    ErrorCode RESOURCE_FILE_EMPTY = new ErrorCode(1_061_011_001, "上传文件不能为空");
    ErrorCode RESOURCE_REFERENCED_FORBIDDEN = new ErrorCode(1_061_011_002, "该资源已被引用，禁止物理删除");

    // ========== 培训班（class）1-061-012-000 ==========
    ErrorCode PLAN_CLASS_NOT_EXISTS = new ErrorCode(1_061_012_000, "培训班不存在");
    ErrorCode PLAN_CLASS_NAME_DUPLICATE = new ErrorCode(1_061_012_001, "已存在同名培训班");
    ErrorCode PLAN_CLASS_STATUS_NOT_ALLOW_UPDATE = new ErrorCode(1_061_012_002, "培训班当前状态【{}】不允许编辑");
    ErrorCode PLAN_CLASS_STATUS_NOT_ALLOW_DELETE = new ErrorCode(1_061_012_003, "培训班当前状态【{}】不允许删除，仅草稿 / 已完成 / 已取消可删除");
    ErrorCode PLAN_CLASS_STATUS_NOT_ALLOW_START = new ErrorCode(1_061_012_004, "培训班当前状态【{}】不允许开班");
    ErrorCode PLAN_CLASS_STATUS_NOT_ALLOW_FINISH = new ErrorCode(1_061_012_005, "培训班当前状态【{}】不允许结业");
    ErrorCode PLAN_CLASS_STATUS_NOT_ALLOW_CANCEL = new ErrorCode(1_061_012_006, "培训班当前状态【{}】不允许取消");
    ErrorCode PLAN_CLASS_STATUS_NOT_ALLOW_STUDENT = new ErrorCode(1_061_012_007, "培训班当前状态【{}】不允许管理学员");
    ErrorCode PLAN_CLASS_COURSE_REQUIRED_START = new ErrorCode(1_061_012_008, "开班前至少绑定 1 门课程");
    ErrorCode PLAN_CLASS_DEADLINE_REQUIRED_START = new ErrorCode(1_061_012_009, "开班前必须设置培训截止时间");
    ErrorCode PLAN_CLASS_COURSE_NOT_EXISTS = new ErrorCode(1_061_012_010, "培训班课程绑定记录不存在");
    ErrorCode PLAN_CLASS_COURSE_DUPLICATE = new ErrorCode(1_061_012_011, "课程已绑定到该培训班，请勿重复添加");
    ErrorCode PLAN_CLASS_COURSE_ONLY_ONLINE = new ErrorCode(1_061_012_012, "仅【已上架】课程可绑定到培训班");
    ErrorCode PLAN_CLASS_COURSE_CLASS_FROZEN = new ErrorCode(1_061_012_013, "培训班已开班，课程清单不可调整");

    // ========== 培训班学员（class student）1-061-013-000 ==========
    ErrorCode PLAN_STUDENT_NOT_EXISTS = new ErrorCode(1_061_013_000, "培训班学员记录不存在");
    ErrorCode PLAN_STUDENT_DUPLICATE = new ErrorCode(1_061_013_001, "学员已在该培训班中，请勿重复添加");
    ErrorCode PLAN_STUDENT_ALREADY_REMOVED = new ErrorCode(1_061_013_002, "学员已移出该培训班");
    ErrorCode PLAN_STUDENT_COURSE_REQUIRED = new ErrorCode(1_061_013_003, "培训班尚未绑定课程，无法添加学员（学员入班需自动生成学习任务）");
    ErrorCode PLAN_TASK_NOT_EXISTS = new ErrorCode(1_061_013_004, "学员学习任务不存在");
    ErrorCode PLAN_STUDENT_LIMIT_EXCEEDED = new ErrorCode(1_061_013_008, "单个培训班在班学员上限 {} 人，当前已 {} 人，本次最多还可加入 {} 人");
    ErrorCode PLAN_STUDENT_IMPORT_ALLOC_EMPTY = new ErrorCode(1_061_013_009, "请先在导入名单中为要入班的学员选择培训班");
    ErrorCode PLAN_STUDENT_IMPORT_ALLOC_CLASS_NOT_ALLOWED = new ErrorCode(1_061_013_010, "指定的培训班【{}】当前状态【{}】不允许维护学员，请改选草稿或进行中的培训班");
    ErrorCode PLAN_STUDENT_IMPORT_ALLOC_LIMIT_EXCEEDED = new ErrorCode(1_061_013_011, "培训班【{}】在班学员上限 {} 人，当前已 {} 人，本次最多还可指定 {} 人");

    // ========== 督促提醒（remind）1-061-014-000 ==========
    ErrorCode PLAN_REMIND_NO_TARGET = new ErrorCode(1_061_014_000, "没有可督促的未完成学员");
    ErrorCode PLAN_REMIND_CLASS_FINISHED = new ErrorCode(1_061_014_001, "培训班已结业，无需督促");
    ErrorCode PLAN_REMIND_CLASS_NOT_ACTIVE = new ErrorCode(1_061_014_002, "培训班已取消或已结业，不支持督促");

    // ========== 上岗资格核查（qualification）1-061-015-000 ==========
    ErrorCode PLAN_QUALIFICATION_USER_NOT_EXISTS = new ErrorCode(1_061_015_000, "人员不存在或已停用");

    // ========== 学员学习中心（learner learning）1-061-016-000 ==========
    ErrorCode LEARNER_TASK_NOT_EXISTS = new ErrorCode(1_061_016_000, "学习任务不存在");
    ErrorCode LEARNER_TASK_DUPLICATE = new ErrorCode(1_061_016_001, "该学员已存在该课程学习任务，请勿重复添加");
    ErrorCode LEARNER_COURSE_NOT_ONLINE = new ErrorCode(1_061_016_002, "仅【已上架】课程可发起学习");
    ErrorCode LEARNER_COURSE_MEDIA_NOT_SUPPORT = new ErrorCode(1_061_016_003, "该媒资类型暂不支持在线学习");
    ErrorCode LEARNER_SESSION_NOT_EXISTS = new ErrorCode(1_061_016_004, "学习会话不存在");
    ErrorCode LEARNER_SESSION_ENDED = new ErrorCode(1_061_016_005, "学习会话已结束");
    ErrorCode LEARNER_SESSION_ACTIVE_EXISTS = new ErrorCode(1_061_016_006, "存在进行中的学习会话，请先结束后再开始新的学习");
    ErrorCode LEARNER_VERIFY_ANSWER_WRONG = new ErrorCode(1_061_016_007, "验证答案错误，请重试");
    ErrorCode LEARNER_VERIFY_FAILED_LIMIT = new ErrorCode(1_061_016_008, "验证失败次数过多，本次会话判定无效");
    ErrorCode LEARNER_VERIFY_NOT_REQUIRED = new ErrorCode(1_061_016_009, "当前会话无需验证");
    ErrorCode LEARNER_CHECKIN_NOT_ALLOW = new ErrorCode(1_061_016_010, "本会话累计学习不足 15 秒，暂不支持打卡");
    ErrorCode LEARNER_LIVE_ROOM_NOT_EXISTS = new ErrorCode(1_061_016_011, "直播房间不存在");
    ErrorCode LEARNER_LIVE_ROOM_STATUS_INVALID = new ErrorCode(1_061_016_012, "直播房间当前状态不允许该操作");
    ErrorCode LEARNER_LIVE_NOT_STARTED = new ErrorCode(1_061_016_013, "直播尚未开始，无法进入");
    ErrorCode LEARNER_LIVE_ENDED = new ErrorCode(1_061_016_014, "直播已结束");
    ErrorCode LEARNER_DOC_TOTAL_PAGES_INVALID = new ErrorCode(1_061_016_015, "文档总页数不合法");
    ErrorCode LEARNER_CONFIG_KEY_INVALID = new ErrorCode(1_061_016_016, "学习配置项不合法");
    ErrorCode LEARNER_SESSION_LOCKED = new ErrorCode(1_061_016_017, "学习会话异常锁定，请结束当前会话后重试");
    ErrorCode LEARNER_DANMAKU_DISABLED = new ErrorCode(1_061_016_018, "弹幕功能未开启，暂时无法发送");

    // ========== 成长等级（growth level）1-061-017-000 ==========
    ErrorCode GROWTH_LEVEL_NOT_EXISTS = new ErrorCode(1_061_017_000, "成长等级不存在");
    ErrorCode GROWTH_LEVEL_CODE_EXISTS = new ErrorCode(1_061_017_001, "已存在同编码成长等级");
    ErrorCode GROWTH_LEVEL_NAME_EXISTS = new ErrorCode(1_061_017_002, "已存在同名称成长等级");
    ErrorCode GROWTH_LEVEL_NO_EXISTS = new ErrorCode(1_061_017_003, "已存在同序号成长等级");
    ErrorCode GROWTH_LEVEL_HAS_COURSE = new ErrorCode(1_061_017_004, "该等级已被课程成长等级标记引用，禁止删除（请先调整课程等级）");

    // ========== 基础数据字典（岗位工种 / 工种资格证 / 学习地图）1-061-018-000 ==========
    ErrorCode JOB_NOT_EXISTS = new ErrorCode(1_061_018_000, "岗位工种不存在");
    ErrorCode JOB_CODE_EXISTS = new ErrorCode(1_061_018_001, "已存在同编码岗位工种");
    ErrorCode JOB_NAME_EXISTS = new ErrorCode(1_061_018_002, "已存在同名称岗位工种");
    ErrorCode JOB_HAS_CERT = new ErrorCode(1_061_018_003, "该工种下存在资格证要求，禁止删除（请先清理资格证）");
    ErrorCode JOB_HAS_LEARNING_MAP = new ErrorCode(1_061_018_004, "该工种下存在学习地图课程，禁止删除（请先清理学习地图）");

    ErrorCode JOB_CERT_NOT_EXISTS = new ErrorCode(1_061_018_010, "工种资格证不存在");
    ErrorCode JOB_CERT_EXISTS = new ErrorCode(1_061_018_011, "该工种下已存在同名资格证要求");

    ErrorCode LEARNING_MAP_NOT_EXISTS = new ErrorCode(1_061_018_020, "学习地图记录不存在");
    ErrorCode LEARNING_MAP_EXISTS = new ErrorCode(1_061_018_021, "该工种下该阶段已存在同一课程");
    ErrorCode LEARNING_MAP_STAGE_INVALID = new ErrorCode(1_061_018_022, "成长阶段非法，仅支持 entry/core/advanced/technician");
    ErrorCode LEARNING_MAP_COURSE_NOT_EXISTS = new ErrorCode(1_061_018_023, "课程不存在（需为课程分类中已维护的课程级编码）");

}

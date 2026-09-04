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
    ErrorCode COURSE_CATEGORY_NAME_EXISTS = new ErrorCode(1_061_004_001, "已存在同名课程分类");
    ErrorCode COURSE_CATEGORY_HAS_COURSE = new ErrorCode(1_061_004_002, "该分类下存在课程，禁止删除（请先转移课程）");

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
    ErrorCode RESOURCE_CATEGORY_PARENT_INVALID = new ErrorCode(1_061_006_005, "父分类不合法：仅支持一级父分类，不支持三级及以上层级");

    // ========== 资源中心-文档（document resource）1-061-007-000 ==========
    ErrorCode DOCUMENT_NOT_EXISTS = new ErrorCode(1_061_007_000, "文档资源不存在");
    ErrorCode DOCUMENT_FILE_TYPE_INVALID = new ErrorCode(1_061_007_001, "仅支持上传 PDF/JPG/PNG 文档");
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

    // ========== 资源中心-试题（question）1-061-009-000 ==========
    ErrorCode QUESTION_NOT_EXISTS = new ErrorCode(1_061_009_000, "试题不存在");
    ErrorCode QUESTION_TYPE_INVALID = new ErrorCode(1_061_009_001, "题型不合法：仅支持 single单选/multiple多选/judge判断/qa实操问答/case案例分析");
    ErrorCode QUESTION_DIFFICULTY_INVALID = new ErrorCode(1_061_009_002, "难度不合法：1易 2中 3难");
    ErrorCode QUESTION_REFERENCED_DELETE_FORBIDDEN = new ErrorCode(1_061_009_003, "该题已被试卷引用，禁止删除（请改为停用）");
    ErrorCode QUESTION_STATUS_CHANGE_INVALID = new ErrorCode(1_061_009_004, "试题当前状态不允许执行该操作");
    ErrorCode QUESTION_IMPORT_EMPTY = new ErrorCode(1_061_009_005, "导入文件为空或不含有效数据行");
    ErrorCode QUESTION_IMPORT_PARSE_ERROR = new ErrorCode(1_061_009_006, "导入文件解析失败，请使用系统下载的标准 Excel 模板");

    // ========== 资源中心-标签（tag）1-061-010-000 ==========
    ErrorCode RESOURCE_TAG_NOT_EXISTS = new ErrorCode(1_061_010_000, "标签不存在");
    ErrorCode RESOURCE_TAG_DIMENSION_INVALID = new ErrorCode(1_061_010_001, "标签维度不合法：work_type工种/course课程/safety安全/process工艺");
    ErrorCode RESOURCE_TAG_NAME_EXISTS = new ErrorCode(1_061_010_002, "已存在同维度同名标签");
    ErrorCode RESOURCE_TAG_DELETE_HAS_QUESTION = new ErrorCode(1_061_010_003, "该标签已被试题引用，禁止删除");

    // ========== 资源中心-文件/通用（file & common）1-061-011-000 ==========
    ErrorCode RESOURCE_FILE_NOT_FOUND = new ErrorCode(1_061_011_000, "资源文件不存在或已被清理");
    ErrorCode RESOURCE_FILE_EMPTY = new ErrorCode(1_061_011_001, "上传文件不能为空");
    ErrorCode RESOURCE_REFERENCED_FORBIDDEN = new ErrorCode(1_061_011_002, "该资源已被引用，禁止物理删除");

}

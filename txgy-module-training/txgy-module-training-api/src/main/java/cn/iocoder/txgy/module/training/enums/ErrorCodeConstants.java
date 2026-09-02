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

    // ========== 课程媒资（course media）1-061-002-000 ==========
    ErrorCode COURSE_MEDIA_NOT_EXISTS = new ErrorCode(1_061_002_000, "课程媒资不存在");
    ErrorCode COURSE_MEDIA_URL_DUPLICATE = new ErrorCode(1_061_002_001, "已存在同 URL 的媒资，请勿重复绑定");
    ErrorCode COURSE_MEDIA_TYPE_INVALID = new ErrorCode(1_061_002_002, "媒资类型不合法，仅支持 1文档 2视频 3图片");

    // ========== 课程评论（course comment）1-061-003-000 ==========
    ErrorCode COURSE_COMMENT_NOT_EXISTS = new ErrorCode(1_061_003_000, "课程评论不存在");
    ErrorCode COURSE_COMMENT_DELETE_NOT_ALLOW = new ErrorCode(1_061_003_001, "仅可删除自己的评论");

}

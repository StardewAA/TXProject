package cn.iocoder.txgy.module.member.enums;

import cn.iocoder.txgy.framework.common.exception.ErrorCode;

/**
 * Member 错误码枚举类
 * <p>
 * member 系统，使用 1-004-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== 用户相关  1-004-001-000 ============
    ErrorCode USER_NOT_EXISTS = new ErrorCode(1_004_001_000, "用户不存在");
    ErrorCode USER_MOBILE_NOT_EXISTS = new ErrorCode(1_004_001_001, "手机号未注册用户");
    ErrorCode USER_MOBILE_USED = new ErrorCode(1_004_001_002, "修改手机失败，该手机号({})已经被使用");
    ErrorCode USER_POINT_NOT_ENOUGH = new ErrorCode(1_004_001_003, "用户积分余额不足");
    ErrorCode USER_EMAIL_USED = new ErrorCode(1_004_001_004, "修改邮箱失败，该邮箱({})已经被使用");

    // ========== AUTH 模块 1-004-003-000 ==========
    ErrorCode AUTH_LOGIN_BAD_CREDENTIALS = new ErrorCode(1_004_003_000, "登录失败，账号密码不正确");
    ErrorCode AUTH_LOGIN_USER_DISABLED = new ErrorCode(1_004_003_001, "登录失败，账号被禁用");
    ErrorCode AUTH_SOCIAL_USER_NOT_FOUND = new ErrorCode(1_004_003_005, "登录失败，解析不到三方登录信息");
    ErrorCode AUTH_MOBILE_USED = new ErrorCode(1_004_003_007, "手机号已经被使用");
    ErrorCode AUTH_TENANT_MISMATCH = new ErrorCode(1_004_003_008, "账号与当前商城不匹配，请返回账号所属商城");
    ErrorCode MALL_TENANT_UNAVAILABLE = new ErrorCode(1_004_003_009, "商城入口暂不可用，请稍后重试");
    ErrorCode MALL_AUTH_LEGACY_DISABLED = new ErrorCode(1_004_003_010, "请使用商城新版认证入口，该旧入口已停用");
    ErrorCode MALL_AUTH_CODE_INVALID = new ErrorCode(1_004_003_011, "验证码无效、已使用或已过期，请重新获取");
    ErrorCode MALL_AUTH_RATE_LIMITED = new ErrorCode(1_004_003_012, "操作过于频繁，请稍后重试");
    ErrorCode MALL_AUTH_SEND_FAILED = new ErrorCode(1_004_003_013, "验证码发送未确认，请稍后重新获取");

    // ========== 用户收件地址 1-004-004-000 ==========
    ErrorCode ADDRESS_NOT_EXISTS = new ErrorCode(1_004_004_000, "用户收件地址不存在");

    //========== 用户标签 1-004-006-000 ==========
    ErrorCode TAG_NOT_EXISTS = new ErrorCode(1_004_006_000, "用户标签不存在");
    ErrorCode TAG_NAME_EXISTS = new ErrorCode(1_004_006_001, "用户标签已经存在");
    ErrorCode TAG_HAS_USER = new ErrorCode(1_004_006_002, "用户标签下存在用户，无法删除");

    //========== 积分配置 1-004-007-000 ==========

    //========== 积分记录 1-004-008-000 ==========
    ErrorCode POINT_RECORD_BIZ_NOT_SUPPORT = new ErrorCode(1_004_008_000, "用户积分记录业务类型不支持");
    ErrorCode POINT_RECORD_REQUEST_INVALID = new ErrorCode(1_004_008_001, "积分变更参数不合法，请检查租户、用户、业务编号和积分方向");
    ErrorCode POINT_RECORD_BIZ_CONFLICT = new ErrorCode(1_004_008_002, "该积分业务已经处理，重复请求的积分数量不一致");
    ErrorCode POINT_RECORD_BALANCE_OVERFLOW = new ErrorCode(1_004_008_003, "积分变更后的余额超出支持范围");

    //========== 签到配置 1-004-009-000 ==========
    ErrorCode SIGN_IN_CONFIG_NOT_EXISTS = new ErrorCode(1_004_009_000, "签到天数规则不存在");
    ErrorCode SIGN_IN_CONFIG_EXISTS = new ErrorCode(1_004_009_001, "签到天数规则已存在");

    //========== 签到配置 1-004-010-000 ==========
    ErrorCode SIGN_IN_RECORD_TODAY_EXISTS = new ErrorCode(1_004_010_000, "今日已签到，请勿重复签到");

    //========== 用户等级 1-004-011-000 ==========
    ErrorCode LEVEL_NOT_EXISTS = new ErrorCode(1_004_011_000, "用户等级不存在");
    ErrorCode LEVEL_NAME_EXISTS = new ErrorCode(1_004_011_001, "用户等级名称[{}]已被使用");
    ErrorCode LEVEL_VALUE_EXISTS = new ErrorCode(1_004_011_002, "用户等级值[{}]已被[{}]使用");
    ErrorCode LEVEL_EXPERIENCE_MIN = new ErrorCode(1_004_011_003, "升级经验必须大于上一个等级[{}]设置的升级经验[{}]");
    ErrorCode LEVEL_EXPERIENCE_MAX = new ErrorCode(1_004_011_004, "升级经验必须小于下一个等级[{}]设置的升级经验[{}]");
    ErrorCode LEVEL_HAS_USER = new ErrorCode(1_004_011_005, "用户等级下存在用户，无法删除");

    ErrorCode EXPERIENCE_BIZ_NOT_SUPPORT = new ErrorCode(1_004_011_201, "用户经验业务类型不支持");

    //========== 用户分组 1-004-012-000 ==========
    ErrorCode GROUP_NOT_EXISTS = new ErrorCode(1_004_012_000, "用户分组不存在");
    ErrorCode GROUP_HAS_USER = new ErrorCode(1_004_012_001, "用户分组下存在用户，无法删除");

}

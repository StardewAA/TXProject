package cn.iocoder.txgy.module.cms.enums;

/** 法规版本的效力状态；由经审核的来源资料确定，不由大模型猜测。 */
public enum CmsLawStatusEnum {
    NOT_YET_EFFECTIVE,
    EFFECTIVE,
    EXPIRED,
    REPEALED,
    UNKNOWN
}

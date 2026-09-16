package cn.iocoder.txgy.module.cms.enums;

/** 标准有效状态，与 CMS 审核/发布状态分开维护。 */
public enum CmsStandardStatusEnum {
    NOT_YET_EFFECTIVE, EFFECTIVE, SUPERSEDED, WITHDRAWN, UNKNOWN
}

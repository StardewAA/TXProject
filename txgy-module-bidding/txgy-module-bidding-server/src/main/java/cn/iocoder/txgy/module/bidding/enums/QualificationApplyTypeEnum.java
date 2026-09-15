package cn.iocoder.txgy.module.bidding.enums;

/**
 * 资质业务申报类型（qualification_apply_record.apply_type 的 code 取值）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-7（NEW 新办/UPGRADE 升级/RENEW 延续/
 * ADDITION 增项/CHANGE 变更/CANCEL 注销）。
 */
public enum QualificationApplyTypeEnum {
    NEW, UPGRADE, RENEW, ADDITION, CHANGE, CANCEL
}

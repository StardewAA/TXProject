package cn.iocoder.txgy.module.bidding.enums;

/**
 * 续期类型（cert_lifecycle_record.renewal_type 的 code 取值）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 5（renewal_type：RENEW 续期 / REISSUE 补换证）。
 * <p>为什么这样写：续期与补换证都改变有效期，但补换证还会产生新证书编号（new_cert_no），
 * 分开记录便于统计"因遗失补办"这类非正常换证频次（合规审计关注点）。
 * <p>业务目标：办结时按类型决定是否回写证书编号，避免把补换证误当成普通延续。
 */
public enum CertRenewalTypeEnum {

    /** 延续（有效期续期） */
    RENEW,
    /** 补换证（遗失补办/换发新证，可能产生新编号） */
    REISSUE
}

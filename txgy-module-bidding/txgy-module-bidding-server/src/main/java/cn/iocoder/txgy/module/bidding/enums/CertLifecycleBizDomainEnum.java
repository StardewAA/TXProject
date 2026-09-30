package cn.iocoder.txgy.module.bidding.enums;

/**
 * 生命周期记录的业务对象域（cert_lifecycle_record.biz_domain 的 code 取值）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §一（表 1 证照台账）、表 15-1（企业资质主档）、
 * 表 15-8（安全生产许可证）；用户口径"覆盖招投标全流程的企业材料 + 员工证书"。
 * <p>为什么这样写：四类业务对象（证照台账/员工证书/企业资质/安许证）都有"变更-续期-注销-年检"需求，
 * 但分属不同表；用域码 + biz_id 组合定位，避免为每个域复制一套生命周期表（同 cert_alert_records 的 ref_* 范式）。
 * <p>业务目标：一处即可查询"某企业全部证件的历史变更与年检情况"。
 */
public enum CertLifecycleBizDomainEnum {

    /** 通用证照台账 certificate_info（营业执照/资质证书影像/荣誉等） */
    CERT,
    /** 员工证书 personnel_certificate（身份证/学历/职称/执业/安全证等） */
    PERSON_CERT,
    /** 企业资质主档 enterprise_qualification */
    ENTERPRISE_QUAL,
    /** 安全生产许可证 qualification_safety_permit */
    SAFETY_PERMIT
}

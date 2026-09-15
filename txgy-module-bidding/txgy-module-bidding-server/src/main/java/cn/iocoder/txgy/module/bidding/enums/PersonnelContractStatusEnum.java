package cn.iocoder.txgy.module.bidding.enums;

/**
 * 劳动合同状态（personnel_labor_contract.contract_status 的 code 取值）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-3（人员须为本单位在职员工，挂靠核查）；
 * 《中华人民共和国劳动合同法》第十条——建立劳动关系应当订立书面劳动合同（合规核验需核验合同主体）。
 * <p>为什么这样写：社保与劳动合同是"员工确实属于本企业"的两个独立证据（社保可能由关联公司代缴），
 * 必须分别落表、分别核验，不能只留一个布尔字段。
 * <p>业务目标：杜绝"人证分离"——申报资质/投标时同时核验社保缴纳单位与劳动合同主体是否均为申报企业。
 */
public enum PersonnelContractStatusEnum {

    /** 履行中（有效期内） */
    ACTIVE,
    /** 已到期（未续签） */
    EXPIRED,
    /** 已解除/终止 */
    TERMINATED;

    /** 是否可作为有效劳动关系凭证（须同时看合同是否覆盖核验基准日，由服务层判定）。 */
    public static boolean isUsable(String code) {
        return ACTIVE.name().equals(code);
    }
}

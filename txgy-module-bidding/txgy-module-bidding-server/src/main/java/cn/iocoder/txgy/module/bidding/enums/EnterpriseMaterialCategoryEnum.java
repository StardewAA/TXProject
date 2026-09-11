package cn.iocoder.txgy.module.bidding.enums;

/**
 * 企业资质材料大类（企业材料台账分类：身份类/资质类/实力类）。
 *
 * <p>规范出处：用户业务口径——《资质管理规范文件》目录（企业身份证明、企业资质证明、企业实力证明三类材料）；
 * 《公共资源交易平台系统数据规范（V2.0）》主体信息数据集（ProjectSubjectExtVO/BidderExtVO/SupplierExtVO
 * 含 QUAL_* 五件套、SubjectAttachItemVO 证书扫描件）《资质管理/03-字段口径映射与差异建议.md》§三。
 * <p>为什么这样写：certificate_info.cert_type 是"证照大类"（ADMIN_LICENSE/SYSTEM_CERT/...），
 * 而业务侧管理界面需要"这是证明我是谁 / 我有资格 / 我底子厚"的三分类视图，两者是多对一关系；
 * 用枚举在代码层做映射（扩码不改 DDL，遵循 §17.6 码表口径），避免给已验证的台账表加列。
 * <p>业务目标：把企业材料按"身份/资质/实力"三线归集，支撑投标前材料完备性自查与台账检索。
 */
public enum EnterpriseMaterialCategoryEnum {

    /** 身份类：营业执照、法定代表人身份证明、法人授权委托书 */
    IDENTITY,
    /** 资质类：资质证书、安全生产许可证 */
    QUALIFICATION,
    /** 实力类：财务审计报告、业绩证明、社保证明、荣誉 */
    STRENGTH,
    /** 其他（未归类） */
    OTHER;

    /**
     * 证照大类 code → 材料大类。新增码值放这里统一映射，业务代码不散落 if-else。
     */
    public static EnterpriseMaterialCategoryEnum resolveByCertType(String certType) {
        if (certType == null) {
            return OTHER;
        }
        return switch (certType) {
            case "ADMIN_LICENSE", "LEGAL_PERSON_ID", "AUTH_LETTER" -> IDENTITY;
            case "SYSTEM_CERT", "SAFETY_PERMIT", "PERSON_QUAL" -> QUALIFICATION;
            case "AUDIT_REPORT", "PERFORMANCE", "SOCIAL_SECURITY", "HONOR", "TAX_CREDIT" -> STRENGTH;
            default -> OTHER;
        };
    }
}

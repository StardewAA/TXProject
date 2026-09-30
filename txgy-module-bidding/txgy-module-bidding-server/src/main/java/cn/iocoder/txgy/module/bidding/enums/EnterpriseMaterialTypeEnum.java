package cn.iocoder.txgy.module.bidding.enums;

import java.util.Arrays;
import java.util.List;

/**
 * 企业材料类型（投标前必备材料清单的判定依据；驱动"完备性自查"逐项核验）。
 *
 * <p>规范出处：用户业务口径——《资质管理规范文件》目录三分类材料（身份/资质/实力）；
 * 《电子招标投标办法》（20号令）第42条（企业资质证书、从业人员资格等应当公开与提交）；
 * 《建筑业企业资质管理规定》（住建部令第22号）+《安全生产许可证条例》（国务院令第397号）——
 * 企业投标须同时具备资质证书与安全生产许可证；《电子证件全流程管理_数据库设计.md》v3.3
 * 表 15-1/15-4/15-5/15-8（资质证书/业绩/资产/安许的落库位置）。
 * <p>为什么这样写：每类材料的"在哪张表、用什么键判定、是否必备"是固定业务知识，
 * 用枚举固化后，完备性检查与材料台账共用同一口径（改口径只改一处）。
 * <p>业务目标：投标前一次性自查"身份-资质-实力"三类材料是否齐备且在有效期内，
 * 避免因漏材料被资格审查否决（这是 Excel 台账最容易出的错）。
 */
public enum EnterpriseMaterialTypeEnum {

    /** 营业执照（企业身份证明；证书台账 cert_type=ADMIN_LICENSE） */
    BUSINESS_LICENSE(EnterpriseMaterialCategoryEnum.IDENTITY, "营业执照", "ADMIN_LICENSE",
            MaterialSource.CERT, true),
    /** 法定代表人身份证明（cert_type=LEGAL_PERSON_ID） */
    LEGAL_PERSON_ID(EnterpriseMaterialCategoryEnum.IDENTITY, "法定代表人身份证明", "LEGAL_PERSON_ID",
            MaterialSource.CERT, true),
    /** 法人授权委托书（员工代表企业投标的效力凭证，需法人签字盖章；cert_type=AUTH_LETTER） */
    AUTH_LETTER(EnterpriseMaterialCategoryEnum.IDENTITY, "法人授权委托书", "AUTH_LETTER",
            MaterialSource.CERT, true),
    /** 资质证书（企业资质主档 15-1，或台账 cert_type=SYSTEM_CERT） */
    QUAL_CERT(EnterpriseMaterialCategoryEnum.QUALIFICATION, "建筑业企业资质证书", "SYSTEM_CERT",
            MaterialSource.QUAL, true),
    /** 安全生产许可证（15-8） */
    SAFETY_PERMIT(EnterpriseMaterialCategoryEnum.QUALIFICATION, "安全生产许可证", "SAFETY_PERMIT",
            MaterialSource.PERMIT, true),
    /** 财务审计报告（实力证明：无亏空、资金可运转；cert_type=AUDIT_REPORT） */
    AUDIT_REPORT(EnterpriseMaterialCategoryEnum.STRENGTH, "财务审计报告", "AUDIT_REPORT",
            MaterialSource.CERT, true),
    /** 业绩证明（合同/验收报告，15-4 业绩台账佐证） */
    PERFORMANCE(EnterpriseMaterialCategoryEnum.STRENGTH, "工程业绩证明", null,
            MaterialSource.PERFORMANCE, true),
    /** 社保证明（员工花名册式佐证：证书人员确为本企业正式员工） */
    SOCIAL_SECURITY(EnterpriseMaterialCategoryEnum.STRENGTH, "社保证明", "SOCIAL_SECURITY",
            MaterialSource.CERT, true);

    /** 材料判定来源：决定去哪张表核验（用 code 而非类引用，保持枚举无 DAL 依赖）。 */
    public enum MaterialSource {
        /** certificate_info 证照台账（按 cert_type 判存在+有效期） */
        CERT,
        /** enterprise_qualification 企业资质主档（在有效期内即视为具备） */
        QUAL,
        /** qualification_safety_permit 安全生产许可证 */
        PERMIT,
        /** qualification_performance 业绩台账（存在业绩即视为具备） */
        PERFORMANCE
    }

    private final EnterpriseMaterialCategoryEnum category;
    private final String label;
    private final String certType;
    private final MaterialSource source;
    private final boolean mandatory;

    EnterpriseMaterialTypeEnum(EnterpriseMaterialCategoryEnum category, String label, String certType,
                               MaterialSource source, boolean mandatory) {
        this.category = category;
        this.label = label;
        this.certType = certType;
        this.source = source;
        this.mandatory = mandatory;
    }

    public EnterpriseMaterialCategoryEnum getCategory() {
        return category;
    }

    public String getLabel() {
        return label;
    }

    /** 对应 certificate_info.cert_type（非 CERT 来源为 null）。 */
    public String getCertType() {
        return certType;
    }

    public MaterialSource getSource() {
        return source;
    }

    /** 是否投标必备材料（完备性检查的默认清单）。 */
    public boolean isMandatory() {
        return mandatory;
    }

    /** 投标前必备材料清单（身份+资质+实力三类全覆盖）。 */
    public static List<EnterpriseMaterialTypeEnum> mandatoryMaterials() {
        return Arrays.stream(values()).filter(EnterpriseMaterialTypeEnum::isMandatory).toList();
    }
}

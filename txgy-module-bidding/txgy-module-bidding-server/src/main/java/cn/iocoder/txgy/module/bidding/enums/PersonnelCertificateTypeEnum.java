package cn.iocoder.txgy.module.bidding.enums;

import java.util.Arrays;

/**
 * 员工证书类型（personnel_certificate.cert_type 的 code 取值；带二级分类与元数据约束）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §一（证照分类：人员类证书走人员扩展）、表 15-3
 * （注册执业/职称/技工）；《电子招标投标办法》（20号令）第42条要求公开"取得有关职称、职业资格的从业人员
 * 姓名与电子证书编号"；《陕西省公共资源交易电子监管系统数据规范 V2.0》PractitionerQual（执业人员）数据集
 * 口径见《资质管理/03-字段口径映射与差异建议.md》§三。
 * <p>为什么这样写：证书类型既是"分类检索"的键，又决定元数据必填项（身份证/学历必须录有效期、
 * 培训记录无须发证机关）与合规核验策略，所以把约束内聚到枚举字段，避免散落在服务里的 if-else。
 * <p>业务目标：实现"证书信息集中与动态管理"——分类（category）、检索、到期预警、投标配证都以此为准。
 */
public enum PersonnelCertificateTypeEnum {

    /** 身份证（身份类：有效期即证件有效期，过期即身份证明失效） */
    ID_CARD("IDENTITY", "身份证", true, true),
    /** 学历证书 */
    EDUCATION_CERT("EDUCATION", "学历证书", false, false),
    /** 学位证书 */
    DEGREE_CERT("EDUCATION", "学位证书", false, false),
    /** 职称证书（初级/中级/高级，资质人员指标要素） */
    TITLE_CERT("TITLE", "职称证书", true, false),
    /** 注册执业证书（注册建造师/结构师等，含注册单位，防挂证核验重点） */
    REG_PRACTICE_CERT("PRACTICE", "注册执业证书", true, false),
    /** 安全生产考核合格证（建筑施工企业主要负责人/项目负责人/专职安全员 A/B/C 证） */
    SAFETY_ASSESS_CERT("SAFETY", "安全生产考核合格证", true, false),
    /** 职业技能等级证书（技工） */
    SKILL_CERT("SKILL", "职业技能等级证书", true, false),
    /** 培训合格证书 */
    TRAINING_CERT("TRAINING", "培训合格证书", true, false),
    /** 其他 */
    OTHER("OTHER", "其他", false, false);

    private final String category;
    private final String label;
    /** 是否要求登记有效期（预警扫描依赖；身份证/执业/职称/安全类必填） */
    private final boolean expiryRequired;
    /** 是否企业身份类必备材料（身份证为人员身份证明的等价物） */
    private final boolean identityRequired;

    PersonnelCertificateTypeEnum(String category, String label, boolean expiryRequired, boolean identityRequired) {
        this.category = category;
        this.label = label;
        this.expiryRequired = expiryRequired;
        this.identityRequired = identityRequired;
    }

    public String getCategory() {
        return category;
    }

    public String getLabel() {
        return label;
    }

    public boolean isExpiryRequired() {
        return expiryRequired;
    }

    public boolean isIdentityRequired() {
        return identityRequired;
    }

    /** 宽松解析：未知/空值返回 OTHER（字典扩容时旧代码不炸，遵循 §17.6 码表口径）。 */
    public static PersonnelCertificateTypeEnum resolve(String code) {
        if (code == null) {
            return OTHER;
        }
        return Arrays.stream(values()).filter(v -> v.name().equals(code)).findFirst().orElse(OTHER);
    }
}

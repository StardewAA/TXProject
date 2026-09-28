package cn.iocoder.txgy.module.bidding.enums;

/**
 * 规则配置分组（bidding_rule_config.rule_group 的 code 取值）。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§8.1（规则配置中心表结构）；契约《电子证照模块-前端接口契约-v1.0.md》§1.2。
 * <p>为什么这样写：配置键会随模块增多而快速膨胀（安全/检查/预警/设备/加注件/标书/加密），
 * 若不分组建模，前端配置页只能一次性拉全量、运维也无法按职责授权；分组是"按模块自治"的最小粒度。
 * <p>业务目标：前端按页面只拉自己那组配置，运维按分组授权与发布，互不干扰。
 */
public enum RuleConfigGroupEnum {

    /** 电子证照台账域（到期窗口、验真窗口、文件限制） */
    CERT,
    /** 到期预警域（多级阈值、法定下限、加注件预警档位） */
    ALERT,
    /** 员工资质域（员工证书临近到期、复审提醒） */
    PERSONNEL,
    /** 资质域（资质/安许/材料的到期与自查口径；种子中 qual.* 键即本组） */
    QUAL,
    /** 安许 A/B/C 与 risk_flag 域（方案 §1.2） */
    SAFETY,
    /** 现场检查/周检 Job 域（方案 §2.2） */
    INSPECTION,
    /** 设备台账域（方案 §4.2） */
    EQUIPMENT,
    /** 加注件域（方案 §5.2） */
    ANNOTATION,
    /** 标书引用拦截域（方案 §6.2） */
    BIDDOC,
    /** 加解密/KMS 域（方案 §7.2） */
    CRYPTO;

    /** 判断分组 code 是否合法（前端传参校验，避免脏值落库）。 */
    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        for (RuleConfigGroupEnum item : values()) {
            if (item.name().equals(code)) {
                return true;
            }
        }
        return false;
    }
}

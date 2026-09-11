package cn.iocoder.txgy.framework.ruleconfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 规则键常量与代码兜底默认值。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§8.1(b)（四级回落：租户 → 灰度 → 平台 → 代码兜底）；
 * 契约《电子证照模块-前端接口契约-v1.0.md》§1.2（关键键表）。
 * <p>为什么需要"代码兜底"：配置表尚未初始化（新环境/迁移中）时，若直接抛错会导致整个电子证照模块不可用；
 * 兜底值保证系统"能跑"，但它**不是业务默认值的来源**——业务默认值以 DDL 种子为准（见 sql/2026-09-16-rule-config-center.sql §4）。
 * <p>业务目标：配置中心不可用时业务降级可用，同时用单元测试锁死"兜底值 == SQL 种子值"，防止两处漂移（配置中心最常见的腐坏方式）。
 */
public final class RuleConfigKeys {

    // ========== 电子证照域 ==========
    /** 距有效期止 ≤ N 天视为"即将到期"（列表角标与筛选口径） */
    public static final String CERT_EXPIRING_WINDOW_DAYS = "cert.expiring.window.days";
    /** 到期预警多级提前天数（默认 [90,60,30,7]） */
    public static final String CERT_ALERT_THRESHOLDS = "cert.alert.thresholds";
    /** 各证照类别的法定预警下限（保存预警天数时不得低于此值） */
    public static final String CERT_ALERT_LEGAL_FLOOR_DAYS = "cert.alert.legal.floor.days";
    /** 同证同渠道重复验真的幂等窗口（秒） */
    public static final String CERT_VERIFY_IDEMPOTENT_WINDOW_SECONDS = "cert.verify.idempotent.window.seconds";
    /** 单文件大小上限（MB） */
    public static final String CERT_FILE_MAX_SIZE_MB = "cert.file.max.size.mb";
    /** 允许上传的文件格式 */
    public static final String CERT_FILE_ALLOW_FORMATS = "cert.file.allow.formats";
    /** 加注件最长有效天数（用户口径：90 天，过期重新申请、不自动续期） */
    public static final String CERT_ANNOTATION_MAX_DAYS = "cert.annotation.max.days";
    /** 加注件到期预警档位 */
    public static final String CERT_ANNOTATION_WARN_DAYS = "cert.annotation.warn.days";
    /** 主证吊销/暂扣时加注件是否级联失效 */
    public static final String CERT_ANNOTATION_CASCADE_ON_REVOKE = "cert.annotation.cascade.on.revoke";

    // ========== 员工资质域 ==========
    /** 员工证书"临近到期"窗口（天） */
    public static final String PERSONNEL_CERT_EXPIRING_WINDOW_DAYS = "personnel.cert.expiring.window.days";
    /** 员工证书复审/年审提前提醒天数 */
    public static final String PERSONNEL_CERT_REVIEW_WARN_DAYS = "personnel.cert.review.warn.days";

    /**
     * 代码兜底默认值（JSON 文本，与 sql/2026-09-16-rule-config-center.sql 的种子行逐键一致）。
     *
     * <p>为什么用 LinkedHashMap 且保持与 DDL 相同的顺序：便于人工逐行比对，也便于测试按序遍历断言。
     */
    private static final Map<String, String> FALLBACK = new LinkedHashMap<>();

    static {
        FALLBACK.put(CERT_EXPIRING_WINDOW_DAYS, "60");
        FALLBACK.put(CERT_ALERT_THRESHOLDS, "[90,60,30,7]");
        FALLBACK.put(CERT_ALERT_LEGAL_FLOOR_DAYS,
                "{\"SEWAGE_PERMIT\":60,\"BOILER_REVIEW\":60,\"SPECIAL_EQUIPMENT\":180,"
                        + "\"SAFETY_PERMIT\":90,\"REG_PRACTICE_CERT\":90,\"ID_CARD\":90}");
        FALLBACK.put(CERT_VERIFY_IDEMPOTENT_WINDOW_SECONDS, "60");
        FALLBACK.put(CERT_FILE_MAX_SIZE_MB, "50");
        FALLBACK.put(CERT_FILE_ALLOW_FORMATS, "[\"PDF\",\"JPG\",\"PNG\",\"OFD\"]");
        FALLBACK.put(CERT_ANNOTATION_MAX_DAYS, "90");
        FALLBACK.put(CERT_ANNOTATION_WARN_DAYS, "[30,7,1]");
        FALLBACK.put(CERT_ANNOTATION_CASCADE_ON_REVOKE, "true");
        FALLBACK.put(PERSONNEL_CERT_EXPIRING_WINDOW_DAYS, "30");
        FALLBACK.put(PERSONNEL_CERT_REVIEW_WARN_DAYS, "60");
    }

    private RuleConfigKeys() {
    }

    /** 取代码兜底值（JSON 文本）；未知键返回 null。 */
    public static String fallback(String ruleKey) {
        return FALLBACK.get(ruleKey);
    }

    /** 全部兜底键值（测试与运维对账用，返回不可变视图）。 */
    public static Map<String, String> allFallbacks() {
        return java.util.Collections.unmodifiableMap(FALLBACK);
    }
}

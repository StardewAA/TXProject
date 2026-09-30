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
    /**
     * 扫描置"即将到期"状态的**流转**口径：距有效期止 ≤ N 天且状态为 ACTIVE → 状态置 EXPIRING_SOON。
     *
     * <p>为什么与 {@link #CERT_EXPIRING_WINDOW_DAYS} 分开（两者不得合并）：
     * ① 语义不同——前者是**状态流转**（改的是全租户证照状态，可能联动其它流程），后者是**展示口径**
     *    （列表角标/筛选，契约 §3.11 由前端传窗口天数、后端有意不读）；
     * ② 风险不同——展示口径调宽只是多几个角标，流转口径调宽会把大批证照改状态，故各自独立可配；
     * ③ 出处不同——本键默认 30 来自 51 号文 ZZZT 口径（扫描 ≤30 天置即将到期），展示口径默认 60。
     * 合并成一个键的后果是"改角标顺带改状态"，属于把两个决策绑在一起，故不合并。
     */
    public static final String CERT_EXPIRING_STATUS_DAYS = "cert.expiring.status.days";

    /**
     * 企业材料完备性自查的"临近到期"窗口（天）：材料有效期止距基准日 ≤ N 天 → 该材料判 EXPIRING、整体结论 RISK。
     *
     * <p>为什么单开一键（不与 {@link #CERT_EXPIRING_WINDOW_DAYS} 合并，尽管两者默认值都是 60）：
     * 语义不同——本键判的是**投标资格自查的提前期**（企业有没有足够时间补办材料，结论直接影响能否投标），
     * 后者是**台账列表的角标/筛选**（展示口径，且契约 §3.11 由前端传参、后端不读）。
     * 数值相同只是巧合；两处风险不同（一个影响资格审查结论，一个只影响界面提示），故各自可配。
     * 默认值取原硬编码值 60 —— 接线**行为不变**。
     *
     * <p>为什么键前缀是 {@code qual.}（不是 {@code enterprise.material.}）：分组校验
     * {@code RuleConfigServiceImpl#groupMatches} 按**键前缀**判组（QUAL 组要求 {@code qual.} 前缀），
     * 前缀不符会导致 {@code GET /bidding/meta/configs?group=QUAL} 的兜底补齐路径**漏掉本键**
     * （种子行存在时按列取得到，但库未执行种子时列表就少一项）。命名必须服从这条机械约定。
     */
    public static final String QUAL_MATERIAL_EXPIRING_WINDOW_DAYS = "qual.material.expiring.window.days";

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

    // ========== 企业资质域 ==========
    /**
     * 资质主档"临近到期"窗口（天）：≤ 该值由资质到期扫描把状态联动为 EXPIRING_SOON。
     *
     * <p>规范出处：《电子资质七大模块技术方案.md》§8.1（阈值集中受控）。与人员证书域同范式
     * （{@link #PERSONNEL_CERT_EXPIRING_WINDOW_DAYS}）；新增于 2026-09-13，
     * 原因是资质扫描此前把该窗口硬编码在服务里，运维无法调整（假配置）。
     */
    public static final String QUAL_EXPIRING_WINDOW_DAYS = "qual.expiring.window.days";

    /**
     * 兼任岗位 → 安许考核合格证细分（A/B/C 类）矩阵（JSON 对象，必须含 "DEFAULT" 缺省项）。
     *
     * <p>规范出处：用户业务口径"兼任两个及以上岗位的，必须持另一岗位的考核合格证书"；
     * 用户硬性要求"所有口径可动态配置、严禁硬编码"；《电子资质七大模块技术方案.md》§8.1。
     * <p>为什么新增：原实现把该矩阵写成 {@code SAFETY_OFFICER.equals(post) ? "C" : "C"}——
     * 两个分支同值，等于"所有岗位都要 C 类"写死在代码里；业务确认矩阵（如项目负责人需 B 类）时
     * 必须改代码发版，而配证校验直接决定投标能否被拦截，口径变更不应依赖发版。
     * <p>当前默认值**保持与旧行为一致**（全部 C 类）：不替业务拍板，只把口径从代码搬到配置。
     */
    public static final String PERSONNEL_MULTI_POST_CERT_SUBTYPE_MAPPING =
            "personnel.multi.post.cert.subtype.mapping";

    /**
     * 安全生产许可证"临近到期"窗口（天）：≤ 该值由安许到期扫描把许可状态联动为 EXPIRING_SOON。
     *
     * <p>规范出处：《电子资质七大模块技术方案.md》§8.1（阈值集中受控）。
     * <p>为什么**不**复用资质的键：安许是独立法律对象（《安全生产许可证条例》，法定下限见
     * {@link #CERT_ALERT_LEGAL_FLOOR_DAYS} 中 SAFETY_PERMIT=90），与资质改革口径不同步；
     * 共用一个键会导致"改资质窗口顺手改了安许"，这正是本模块要避免的耦合。
     */
    public static final String SAFETY_PERMIT_EXPIRING_WINDOW_DAYS = "safety.permit.expiring.window.days";

    // ========== 安许 A/B/C 与 risk_flag 域（方案 §1.2）==========
    /** A/B/C 三类人员最低配备（JSON：{"A":1,"B":1,"C":1}） */
    public static final String SAFETY_ABC_REQUIRED = "safety.abc.required";
    /** 参与判定的角色集合 */
    public static final String SAFETY_ABC_ROLES = "safety.abc.roles";
    /** 豁免矩阵：企业类型 → 允许为 0 的角色（劳务分包 B 证为 0 不触发红） */
    public static final String SAFETY_ABC_EXEMPT_ENT_TYPES = "safety.abc.exempt.ent.types";
    /** 法定代表人变更致人数不足的宽限天数（180） */
    public static final String SAFETY_ABC_GRACE_LEGAL_PERSON_CHANGE_DAYS = "safety.abc.grace.legal.person.change.days";
    /** 允许触发宽限的原因白名单 */
    public static final String SAFETY_ABC_GRACE_REASON_WHITELIST = "safety.abc.grace.reason.whitelist";
    /** 频繁预警次数阈值（4） */
    public static final String SAFETY_ABC_FREQUENT_ALERT_THRESHOLD = "safety.abc.frequent.alert.threshold";
    /** 频繁预警统计窗口（365 天） */
    public static final String SAFETY_ABC_FREQUENT_WINDOW_DAYS = "safety.abc.frequent.window.days";
    /** 三类全 0 后允许整改天数（90） */
    public static final String SAFETY_ABC_INVALID_GRACE_DAYS = "safety.abc.invalid.grace.days";
    /** 阻断业务的风险档位集合 */
    public static final String SAFETY_ABC_BLOCK_FLAGS = "safety.abc.block.flags";
    /** 人员更换受聘企业次数阈值（4） */
    public static final String PERSONNEL_JOB_CHANGE_THRESHOLD = "personnel.job.change.threshold";
    /** 人员更换受聘企业统计窗口（365 天） */
    public static final String PERSONNEL_JOB_CHANGE_WINDOW_DAYS = "personnel.job.change.window.days";
    /** 同一项目同一关键岗位变更次数阈值（2） */
    public static final String PROJECT_KEY_POST_CHANGE_THRESHOLD = "project.key.post.change.threshold";
    /** 关键岗位变更统计窗口（90 天） */
    public static final String PROJECT_KEY_POST_CHANGE_WINDOW_DAYS = "project.key.post.change.window.days";
    /** 多岗是否必须持另一岗位证书 */
    public static final String PERSONNEL_MULTI_POST_REQUIRE_CERT = "personnel.multi.post.require.cert";
    /** 风险档 → 等级映射 */
    public static final String RISK_LEVEL_MAPPING = "risk.level.mapping";

    /**
     * 代码兜底默认值（JSON 文本，与 sql/2026-09-16-rule-config-center.sql 的种子行逐键一致）。
     *
     * <p>为什么用 LinkedHashMap 且保持与 DDL 相同的顺序：便于人工逐行比对，也便于测试按序遍历断言。
     */
    private static final Map<String, String> FALLBACK = new LinkedHashMap<>();

    static {
        FALLBACK.put(CERT_EXPIRING_WINDOW_DAYS, "60");
        FALLBACK.put(CERT_EXPIRING_STATUS_DAYS, "30");
        FALLBACK.put(QUAL_MATERIAL_EXPIRING_WINDOW_DAYS, "60");
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
        FALLBACK.put(QUAL_EXPIRING_WINDOW_DAYS, "30");
        // 与旧行为一致（全部 C 类）；矩阵待业务确认，改配置即生效
        FALLBACK.put(PERSONNEL_MULTI_POST_CERT_SUBTYPE_MAPPING,
                "{\"DEFAULT\":\"C\",\"SAFETY_OFFICER\":\"C\",\"PROJECT_MANAGER\":\"C\","
                        + "\"TECH_LEADER\":\"C\",\"QUALITY_OFFICER\":\"C\",\"CONSTRUCTION_ENGINEER\":\"C\"}");
        FALLBACK.put(SAFETY_PERMIT_EXPIRING_WINDOW_DAYS, "30");
        FALLBACK.put(SAFETY_ABC_REQUIRED, "{\"A\":1,\"B\":1,\"C\":1}");
        FALLBACK.put(SAFETY_ABC_ROLES, "[\"A\",\"B\",\"C\"]");
        FALLBACK.put(SAFETY_ABC_EXEMPT_ENT_TYPES, "{\"LABOR_SUBCONTRACT\":[\"B\"]}");
        FALLBACK.put(SAFETY_ABC_GRACE_LEGAL_PERSON_CHANGE_DAYS, "180");
        FALLBACK.put(SAFETY_ABC_GRACE_REASON_WHITELIST, "[\"LEGAL_PERSON_CHANGE\"]");
        FALLBACK.put(SAFETY_ABC_FREQUENT_ALERT_THRESHOLD, "4");
        FALLBACK.put(SAFETY_ABC_FREQUENT_WINDOW_DAYS, "365");
        FALLBACK.put(SAFETY_ABC_INVALID_GRACE_DAYS, "90");
        FALLBACK.put(SAFETY_ABC_BLOCK_FLAGS, "[\"INVALID\",\"RED\"]");
        FALLBACK.put(PERSONNEL_JOB_CHANGE_THRESHOLD, "4");
        FALLBACK.put(PERSONNEL_JOB_CHANGE_WINDOW_DAYS, "365");
        FALLBACK.put(PROJECT_KEY_POST_CHANGE_THRESHOLD, "2");
        FALLBACK.put(PROJECT_KEY_POST_CHANGE_WINDOW_DAYS, "90");
        FALLBACK.put(PERSONNEL_MULTI_POST_REQUIRE_CERT, "true");
        FALLBACK.put(RISK_LEVEL_MAPPING,
                "{\"NORMAL\":\"LOW\",\"YELLOW\":\"MEDIUM\",\"RED\":\"HIGH\",\"INVALID\":\"MAJOR\","
                        + "\"FREQUENT\":\"MEDIUM\",\"ABNORMAL\":\"MEDIUM\"}");
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

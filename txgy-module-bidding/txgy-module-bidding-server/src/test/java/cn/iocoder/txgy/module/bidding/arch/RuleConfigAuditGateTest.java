package cn.iocoder.txgy.module.bidding.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规则读取审计门禁（登记制）。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§8.2（审计与追溯：规则读取落 {`bidding_rule_config_audit`}）；
 * 《招投标模块开发规范》§4.5/§5（参数集中受控、禁止硬编码）。
 *
 * <p>为什么需要这道门禁（真实教训）：`bidding_rule_config_audit` 建表后**长期零引用**——
 * 表在、DDL 在、注释在，但没有任何代码写它，于是"争议时可追溯"只是纸面能力。
 * 2026-09-13 补齐写入后，光靠人工排查才发现业务侧有 11 处直连读取；
 * 只要没有门禁，下一次重构仍会悄悄绕过审计。故把两个不变量固化成测试：
 * ① **直连读取必须登记**（新增直连读取 = 显式登记并写明理由，不允许"顺手加一个"）；
 * ② **判定场景必须全部走带审计的读取入口**（少一个场景就说明有一类判定不再可追溯）。
 *
 * <p>为什么不是"所有业务读取都必须审计"：配置读取在热路径上（含缓存），逐次落库会把一次内存读
 * 变成一次 DB 写。展示类读取（配置页/页面初始化下发）不参与判定，登记为豁免；
 * 只有**会对外产生结论**的判定点才强制留证。
 *
 * <p>业务目标：审计能力不会因为一次重构而失效。
 */
class RuleConfigAuditGateTest {

    private static final Path MAIN_ROOT = Path.of("src", "main", "java", "cn", "iocoder", "txgy", "module", "bidding");

    /** 直连读取的匹配式：getXxx / listByGroup / history（判定入口 resolveWithAudit 不含 ruleConfigService. 前缀，不会被误伤）。 */
    private static final Pattern DIRECT_READ = Pattern.compile("ruleConfigService\\.(get\\w+|listByGroup|history)\\(");

    /** 带审计读取的全部出现（含三个私有 helper 的定义处）。 */
    private static final Pattern AUDITED_OCCURRENCE = Pattern.compile("audited(?:Int|Bool)?\\(");

    /**
     * 三个私有 helper 的定义处：返回类型 + 方法名。
     *
     * <p>为什么不写成 {@code (?<!private )} 之类的紧邻排除：定义行是「private int auditedInt(」，
     * "private " 与方法名之间还隔着返回类型，紧邻排除会漏掉、把定义也当调用点（自检时就是这么被抓出来的）。
     */
    private static final Pattern AUDITED_DEFINITION =
            Pattern.compile("(?:RuleConfigItemRespVO|int|boolean)\\s+audited(?:Int|Bool)?\\(");

    /** 判定点使用的场景常量：出现在 audited*(...) 实参里的 BIZ_SCENE_*。 */
    private static final Pattern SCENE_IN_CALL = Pattern.compile("audited(?:Int|Bool)?\\([^;]*?(BIZ_SCENE_[A-Z_]+)");

    /**
     * 每个判定场景的**最少留证点**（登记制：新增判定类要加进来，删掉任何一类会立刻失败）。
     *
     * <p>数字口径：2026-09-13 实测 安许重算 7 / 人员风险 4 / 项目关键岗位变更 2。
     * 取"下限"而非等值，是为了允许在同一类判定里继续增加留证点，但**不允许**整类消失。
     */
    private static final Map<String, Integer> MIN_SCENE_CALL_SITES = Map.of(
            "BIZ_SCENE_SAFETY_RISK_EVAL", 7,
            "BIZ_SCENE_PERSONNEL_RISK", 4,
            "BIZ_SCENE_PROJECT_KEY_POST_CHANGE", 2);

    /**
     * 已登记的直连读取文件（相对 {@code .../module/bidding} 的路径 → 豁免理由）。
     *
     * <p>登记要求：必须说明"为什么这次读取不需要留证"。若某次读取的结果会进入对外结论，请改用
     * `RuleConfigService#resolveWithAudit(ruleKey, bizScene)`，而不是往这里加一行。
     */
    private static final Map<String, String> REGISTERED_DIRECT_READERS = new LinkedHashMap<>();

    static {
        REGISTERED_DIRECT_READERS.put("controller/admin/ruleconfig/RuleConfigController.java",
                "配置管理页读当前生效值（读配置本身不是判定依据；给配置页写审计只会污染追溯视图）");
        REGISTERED_DIRECT_READERS.put("controller/admin/meta/MetaController.java",
                "页面初始化下发整组配置：只读展示，不产生任何业务结论");
        REGISTERED_DIRECT_READERS.put("service/personnel/PersonnelAlertServiceImpl.java",
                "法定下限映射读取（决策 ① 第 2 步）：legalFloorDays() 在扫描循环里逐证书调用，走 resolveWithAudit "
                        + "会把每次扫描每本证书都写一条审计（一屏几百条），污染追溯视图；"
                        + "审计留证点已由同扫描内的 resolveWithAudit(PERSONNEL_CERT_EXPIRING_WINDOW_DAYS, "
                        + "PERSONNEL_ALERT_SCAN) 承担（状态联动窗口才是会改库的判定）；"
                        + "下限只是把档位向上钳制（有界副作用），且读不到配置时完全回落硬编码默认（行为等价），"
                        + "写侧校验另有 RuleConfigServiceImpl 保存时 getIntMap 兜底");
    }

    @Test
    void directReads_mustBeRegistered() throws IOException {
        Set<String> actual = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(MAIN_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file);
                if (DIRECT_READ.matcher(text).find()) {
                    actual.add(MAIN_ROOT.relativize(file).toString().replace('\\', '/'));
                }
            }
        }

        Set<String> unregistered = new LinkedHashSet<>(actual);
        unregistered.removeAll(REGISTERED_DIRECT_READERS.keySet());
        Set<String> stale = new LinkedHashSet<>(REGISTERED_DIRECT_READERS.keySet());
        stale.removeAll(actual);

        assertTrue(unregistered.isEmpty(),
                "发现未登记的规则直连读取：%s。若该读取会进入对外结论，请改用 RuleConfigService#resolveWithAudit(key, bizScene) 留证；"
                        .formatted(unregistered)
                        + "确属展示类读取的，再登记到 REGISTERED_DIRECT_READERS 并写明理由。");
        assertTrue(stale.isEmpty(),
                "以下文件已登记但不再直连读取，请从 REGISTERED_DIRECT_READERS 移除（避免白名单腐烂）：" + stale);
    }

    @Test
    void decisionScenes_mustAllGoThroughAuditedRead() throws IOException {
        String riskService = Files.readString(MAIN_ROOT.resolve("service/safety/SafetyPermitRiskServiceImpl.java"));

        Map<String, Integer> sceneCounts = new LinkedHashMap<>();
        Matcher matcher = SCENE_IN_CALL.matcher(riskService);
        while (matcher.find()) {
            sceneCounts.merge(matcher.group(1), 1, Integer::sum);
        }
        assertEquals(MIN_SCENE_CALL_SITES.keySet(), sceneCounts.keySet(),
                "安许 A/B/C 重算、人员风险判定、项目关键岗位变更三类判定必须全部走带审计的读取入口；"
                        + "新增判定类别请登记到 MIN_SCENE_CALL_SITES，少一类即意味着该类判定在争议时无法举证");

        MIN_SCENE_CALL_SITES.forEach((scene, min) -> assertTrue(
                sceneCounts.getOrDefault(scene, 0) >= min,
                "场景 %s 的留证点只有 %d 处（要求 ≥%d）：该判定类被改回直连读取，读取审计表会重新变成空表"
                        .formatted(scene, sceneCounts.getOrDefault(scene, 0), min)));

        long callSites = countAuditedCallSites(riskService);
        assertTrue(callSites >= 14,
                "带审计的读取调用点只有 %d 处（实测基线 15）：helper 被删或判定点被改回直连读取都会落到此处"
                        .formatted(callSites));
    }

    @Test
    void auditTable_mustHaveWriterAndMappedDo() throws IOException {
        String impl = Files.readString(MAIN_ROOT.resolve("service/ruleconfig/RuleConfigServiceImpl.java"));
        assertTrue(impl.contains("ruleConfigAuditMapper.insert("),
                "读取审计表必须存在真实写入者（该表曾长期零引用：建了表、没人写，"+"'争议可追溯'只是纸面能力）");

        String doFile = Files.readString(MAIN_ROOT.resolve("dal/dataobject/ruleconfig/BiddingRuleConfigAuditDO.java"));
        assertTrue(doFile.contains("@TableName(\"bidding_rule_config_audit\")"),
                "读取审计 DO 必须映射到 bidding_rule_config_audit（映射被改动会让审计悄悄写进别的表）");
    }

    /** 统计带审计的读取调用点 = 全部出现 − 三个 helper 的定义处。 */
    private long countAuditedCallSites(String source) {
        return count(AUDITED_OCCURRENCE, source) - count(AUDITED_DEFINITION, source);
    }

    private long count(Pattern pattern, String source) {
        long total = 0;
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            total++;
        }
        return total;
    }
}

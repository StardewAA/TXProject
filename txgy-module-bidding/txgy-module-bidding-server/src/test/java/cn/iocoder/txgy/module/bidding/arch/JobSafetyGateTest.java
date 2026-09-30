package cn.iocoder.txgy.module.bidding.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 定时任务安全门禁：含 @Scheduled 的类必须具备"可停"能力，且**会失败的批次**必须兜异常。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§1.3（Job 设计：cron 可配 + 开关字段）；
 * 各方案回滚步骤均要求"停 Job"止血（§1.5 等）；既有先例 SafetyRiskJob 的开关 + try/catch 范式。
 *
 * <p>为什么需要这条门禁：调度代码的失效方式是**静默**的——
 * ① 没有开关 → 生产出问题只能改代码发版，止血窗口以"发版周期"计；
 * ② 异常逃逸 → Spring 调度可能按策略跳过后续执行，预警长期静默且无人报错。
 * 二者都不会让任何测试变红，故必须由门禁钉住（"能停"与"不静默死掉"是上线前提，不是代码风格）。
 *
 * <p>门禁口径（**代理指标，如实说明**）：无法静态验证行为正确，只验证结构存在——
 * ① 同类中须有默认 false 的开关字段（@Value 注解含 :false，其后声明 boolean xxxEnabled）；
 * ② 至少一处 if (!xxxEnabled) 守卫；
 * ③ **引用了 @Resource 注入字段的 @Scheduled 方法**必须在方法体内 try/catch 兜住
 *    （只打日志的方法没有失败可能，不强制——"为了过门禁而写无意义的 try"本身就是坏味道）。
 * <p>局限：③ 是"是否可能失败"的代理判断（按是否引用注入字段），复杂方法仍可能漏判；
 * 本门禁保证的是不会"忘了写开关 / 忘了兜异常"，新增 Job 仍需人工评审。
 */
class JobSafetyGateTest {

    private static final Path MAIN_ROOT = Path.of("src/main/java");

    @Test
    void everyScheduledClass_mustHaveOffSwitch_andSwallowExceptions() throws IOException {
        List<String> problems = new ArrayList<>();
        Set<String> scanned = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(MAIN_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = stripComments(Files.readString(file));
                List<String> bodies = scheduledBodies(text);
                if (bodies.isEmpty()) {
                    continue;
                }
                String name = MAIN_ROOT.relativize(file).toString().replace('\\', '/');
                scanned.add(name);
                if (!hasOffSwitch(text)) {
                    problems.add(name + "：有 @Scheduled 但缺少默认 false 的开关字段"
                            + "（@Value 开关 + private boolean xxxEnabled）——生产无法一键停任务");
                }
                if (!hasEnabledGuard(text)) {
                    problems.add(name + "：有开关字段却没有 if (!xxxEnabled) 守卫——开关成了装饰品，不生效");
                }
                Set<String> injected = resourceFields(text);
                for (String body : bodies) {
                    if (referencesAny(body, injected) && !body.contains("try")) {
                        problems.add(name + "：某 @Scheduled 方法调用了注入的服务却没有 try/catch"
                                + "——异常逃逸会让调度框架跳过后续执行，任务静默失效。方法体片段="
                                + body.substring(0, Math.min(60, body.length())).replace('\n', ' '));
                    }
                }
            }
        }
        assertTrue(scanned.size() >= 2,
                "只扫到 " + scanned.size() + " 个含 @Scheduled 的类（实测基线 2：SafetyRiskJob、CredentialAlertJob）——"
                        + "扫描路径写错会让本门禁变成永远通过的空门禁。实际=" + scanned);
        assertTrue(problems.isEmpty(), "定时任务安全门禁未通过：" + String.join("；", problems));
    }

    /**
     * 自检：合成样本上验证检测逻辑真的会失败。
     *
     * <p>为什么值得写：一条不会失败的门禁比没有门禁更糟（它给人虚假的安全感）。
     * 这里钉住四个关键判定——无开关判否、注释里的开关不算数、引用注入字段却无 try 判否、
     * 只打日志的方法不误报。
     */
    @Test
    void selfCheck_detectionLogic_failsOnSyntheticViolations() {
        String noSwitchNoTry = "class X { @Scheduled(cron = \"0 0 2 * * ?\") public void j() { work(); } }";
        assertFalse(hasOffSwitch(noSwitchNoTry), "没有开关字段必须判否");
        assertFalse(hasEnabledGuard(noSwitchNoTry), "没有守卫必须判否");

        // 样本按真实源码分行写：门禁的守卫判定是"整行"口径（避免匹配到字符串里的片段）
        String healthy = "class Y {\n"
                + "  @Resource private FooService fooService;\n"
                + "  @Value(\"${bidding.y.scheduler.enabled:false}\")\n"
                + "  private boolean schedulerEnabled;\n"
                + "  @Scheduled(cron = \"${bidding.y.cron:0 0 2 * * ?}\")\n"
                + "  public void j() {\n"
                + "    if (!schedulerEnabled) { return; }\n"
                + "    try { fooService.scan(); } catch (Exception ex) { log.error(\"e\", ex); }\n"
                + "  }\n}";
        assertTrue(hasOffSwitch(healthy), "规范的开关字段必须判是");
        assertTrue(hasEnabledGuard(healthy), "规范的守卫必须判是");
        List<String> healthyBodies = scheduledBodies(healthy);
        assertTrue(healthyBodies.size() == 1 && healthyBodies.get(0).contains("try"),
                "cron 里带花括号也必须能正确取出方法体（字符串感知），实际=" + healthyBodies);

        String riskyWithoutTry = "class R {\n  @Resource private FooService fooService;\n"
                + "  @Scheduled(cron = \"c\") void j() { fooService.scan(); }\n}";
        assertTrue(referencesAny(scheduledBodies(riskyWithoutTry).get(0), resourceFields(riskyWithoutTry)),
                "引用了注入字段必须被判为'可能失败'");

        String logOnly = "class L {\n  @Resource private FooService fooService;\n"
                + "  @Scheduled(cron = \"c\") void j() { log.info(\"tick\"); }\n}";
        assertFalse(referencesAny(scheduledBodies(logOnly).get(0), resourceFields(logOnly)),
                "只打日志的方法不应被强加 try（避免为过门禁写无意义代码）");

        String switchOnlyInComment = "class Z { /* @Value :false private boolean schedulerEnabled; */"
                + " @Scheduled(cron = \"c\") void j() { try { } catch (Exception e) { } } }";
        assertFalse(hasOffSwitch(stripComments(switchOnlyInComment)),
                "注释里写的开关不能算（否则门禁可被'假装满足'绕过）");
    }

    /** 是否有"默认 false 的开关字段"：@Value 注解含 :false，且其后 4 行内声明 boolean xxxEnabled。 */
    private static boolean hasOffSwitch(String text) {
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("@Value") && lines[i].contains(":false")) {
                for (int j = i; j < Math.min(lines.length, i + 4); j++) {
                    if (lines[j].contains("boolean") && lines[j].contains("nabled")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 是否有 if (!xxxEnabled) 守卫（整行判定，避免匹配到字符串里的片段）。 */
    private static boolean hasEnabledGuard(String text) {
        for (String line : text.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("if (!") && trimmed.contains("nabled")) {
                return true;
            }
        }
        return false;
    }

    /** 类里 @Resource 注入的字段名（"有失败可能的调用"的代理）。 */
    private static Set<String> resourceFields(String text) {
        Set<String> fields = new LinkedHashSet<>();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].contains("@Resource")) {
                continue;
            }
            for (int j = i; j < Math.min(lines.length, i + 4); j++) {
                String line = lines[j].trim();
                if (line.contains(";") && !line.contains("(")) {
                    String decl = line.substring(0, line.indexOf(';')).trim();
                    int space = decl.lastIndexOf(' ');
                    if (space > 0) {
                        fields.add(decl.substring(space + 1).trim());
                    }
                    break;
                }
            }
        }
        return fields;
    }

    /** 取出每个 @Scheduled 方法的完整方法体（花括号配对，且**感知字符串字面量**——cron 值里可能含花括号）。 */
    private static List<String> scheduledBodies(String text) {
        List<String> bodies = new ArrayList<>();
        int cursor = text.indexOf("@Scheduled");
        while (cursor >= 0) {
            int open = indexOfBraceOutsideString(text, cursor);
            if (open < 0) {
                break;
            }
            int close = matchBrace(text, open);
            bodies.add(text.substring(open, close));
            cursor = text.indexOf("@Scheduled", close);
        }
        return bodies;
    }

    /** 找 from 之后第一个不在字符串里的 '{'。 */
    private static int indexOfBraceOutsideString(String text, int from) {
        boolean inString = false;
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{') {
                return i;
            }
        }
        return -1;
    }

    /** 从 open 处的 '{' 配对到对应的 '}'，返回闭区间子串（同样跳过字符串字面量）。 */
    private static int matchBrace(String text, int open) {
        int depth = 0;
        boolean inString = false;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return text.length();
    }

    private static boolean referencesAny(String body, Set<String> names) {
        for (String name : names) {
            if (body.contains(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 去行注释与块注释。
     *
     * <p>为什么必须去注释：javadoc 里常引用 @Scheduled 与开关写法，
     * 若不去注释，"文档里写了"会被当成"代码里有"，门禁就成了摆设。
     * <p>**局限（如实登记）**：不做字符串解析，字符串字面量里的 "//" 会被误当注释起点；
     * 本门禁只扫 main 源码，此类字符串极少，且误判方向偏严格（可能误报而非漏报）。
     */
    private static String stripComments(String source) {
        StringBuilder withoutLineComments = new StringBuilder();
        for (String line : source.split("\n", -1)) {
            int cut = line.indexOf("//");
            withoutLineComments.append(cut >= 0 ? line.substring(0, cut) : line).append('\n');
        }
        String text = withoutLineComments.toString();
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (true) {
            int start = text.indexOf("/*", i);
            if (start < 0) {
                out.append(text.substring(i));
                return out.toString();
            }
            out.append(text, i, start);
            int end = text.indexOf("*/", start + 2);
            if (end < 0) {
                return out.toString();
            }
            i = end + 2;
        }
    }
}

package cn.iocoder.txgy.module.bidding.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 服务能力出口门禁：{@code service/**} 接口里的每个方法，**生产代码里必须有调用者**（登记制）。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§8.4（质量门禁）；本文件顶部登记表即"已知无出口能力"的账目。
 *
 * <p>为什么需要这条门禁：本项目已连续出现四类同源缺陷，其中两类的形态就是「实现存在、没有出口」——
 * ① {@code CertificateAlertService#markAlertHandled}（契约写着"可闭环处理"，实现含版本 CAS，
 *    却**零调用者**，前端能按 handled 过滤却无法把预警置为已处理）；
 * ② {@code CredentialAlertJob} 之前的扫描/派发服务（服务与端点都在，就是没有调度会触发）。
 * （注：本条与上一条同理——本项目已多次出现"契约/文档写着已实现，实际没有出口"，故用门禁兜住。）
 * 这两类缺陷**编译通过、全部单测通过、文档还写着"已实现"**，只有把"调用者"当成一等公民查一遍才会暴露。
 * 本门禁把这件事变成可执行的检查：新增一个没人调用的服务方法 → 立刻失败。
 *
 * <p>判定口径（**代理指标，如实说明局限**）：
 * ① 只扫本模块 {@code service/**}（排除 {@code api/**}——那是跨模块契约，调用方在别的模块）；
 * ② 按**方法名**判定，不区分重载（同名重载会互相掩盖）；
 * ③ "调用"= 任意 main 源码里出现 {@code name(} 且该行不是该方法自身的声明（{@code public void name(} 之类）；
 * ④ 看不到的调用方式：反射、SpEL、跨模块、以及本模块之外的调用方——故登记表里的 CROSS 项需人工确认。
 */
class DeadServiceMethodGateTest {

    private static final Path MAIN_ROOT = Path.of("src/main/java");

    /**
     * 已知"无生产调用者"的服务方法登记表（key = 接口简单名#方法名）。
     *
     * <p>登记制而非豁免：新出现的无出口方法必须显式登记并写明理由/归属；
     * 已登记的项一旦有了调用者，本门禁会要求把它删掉（防白名单腐烂）。
     */
    private static final Map<String, String> UNCALLED_REGISTRY = new LinkedHashMap<>();

    static {
        UNCALLED_REGISTRY.put("CertFileVersionService#markScanResult",
                "DEBT：病毒扫描结果回写无生产出口（扫描组件未接入），现仅测试调用——INFECTED 置隔离的链路缺上游");
        UNCALLED_REGISTRY.put("CrawlTaskService#markRunning",
                "OTHER：采集域（externalfetch）属另一域维护，不在电子资质域盘点范围，由该域负责人确认");
        UNCALLED_REGISTRY.put("MetaDictService#supportedTypes",
                "DEBT：v1.1.3 已把字典「受支持类型」口径改为「枚举 ∪ 字典表已配置」，此方法疑为被取代的遗留面，待确认删除");
        UNCALLED_REGISTRY.put("RuleConfigService#getLong",
                "DEBT：无审计的直读 helper，与「判定点必须走 resolveWithAudit」的审计门禁方向相反（会诱导裸读），待改造或删除");
        UNCALLED_REGISTRY.put("RuleConfigService#getDecimal", "DEBT：同上（无审计直读 helper）");
        UNCALLED_REGISTRY.put("RuleConfigService#getBool", "DEBT：同上（无审计直读 helper）");
        UNCALLED_REGISTRY.put("RuleConfigService#getString", "DEBT：同上（无审计直读 helper）");
        UNCALLED_REGISTRY.put("RuleConfigService#getIntList",
                "DEBT：同上（无审计直读 helper）；仅单测调用，生产读取走 resolveWithAudit/audited* 系列");
        UNCALLED_REGISTRY.put("RuleConfigService#validateAlertThresholds",
                "DEBT：法定下限校验**未接入保存路径**（saveOrPublish 未调用），当前只有前端提示——后端不设防");
        // 2026-09-25 已接线：BiddingProcessTrackServiceImpl.assertBidPortalAllowed() 调用
        // assertBidderEligible（报名/递交资格门），登记移除（见建档门禁注释）。
        UNCALLED_REGISTRY.put("BidOpeningService#getOpening",
                "RESERVED：开标房间-按开标记录ID查询详情；当前 Controller 出口走 getByTenderProject（按招标项目编号，前端列表明细够用），" +
                        "按 id 直查为预留能力（2026-09-18 最严格流程开标房间新增），待前端按 id 详情页接线后移除本登记");
        UNCALLED_REGISTRY.put("BidReviewTaskService#updateTask",
                "OTHER：标书审查域（bidreview）属另一工作线维护；updateTask 注释为「供流水线复用的更新」，" +
                        "当前流水线各阶段直接调 taskMapper/专用方法，此接口为预留复用面，由该域负责人确认接线或删除");
        UNCALLED_REGISTRY.put("PrequalService#getByCode",
                "RESERVED：资格预审-按预审编号查询；当前 Controller 出口走 get(id) 按主键查（前端详情页够用），" +
                        "按编号直查为 merge 合入的预留能力，待前端按编号详情页接线后移除本登记");
    }

    @Test
    void everyServiceMethod_mustHaveProductionCaller_orBeRegistered() throws IOException {
        Set<String> dead = new LinkedHashSet<>();
        Set<String> found = new LinkedHashSet<>();

        List<Path> sources = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_ROOT)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(sources::add);
        }
        Map<Path, String> texts = new LinkedHashMap<>();
        for (Path p : sources) {
            texts.put(p, stripComments(Files.readString(p)));
        }

        for (Path iface : sources) {
            String relative = MAIN_ROOT.relativize(iface).toString().replace('\\', '/');
            if (!relative.startsWith("cn/iocoder/txgy/module/bidding/service/")
                    || !iface.getFileName().toString().endsWith("Service.java")
                    || relative.contains("/api/")) {
                continue;
            }
            String body = texts.get(iface);
            if (!body.contains("interface ")) {
                continue;
            }
            String ifaceName = iface.getFileName().toString().replace(".java", "");
            for (String method : declaredMethods(body)) {
                String key = ifaceName + "#" + method;
                found.add(key);
                if (!hasCaller(texts, iface, method)) {
                    dead.add(key);
                }
            }
        }

        Set<String> stale = new LinkedHashSet<>(UNCALLED_REGISTRY.keySet());
        stale.removeAll(found);
        assertTrue(stale.isEmpty(), "登记表里有已不存在的方法，请从 UNCALLED_REGISTRY 删除：" + stale);

        // 已登记但已经不再是"死方法"（恢复了调用者）→ 必须从登记表移除，否则白名单会腐烂并掩盖新的无出口方法
        Set<String> nowCalled = new LinkedHashSet<>(UNCALLED_REGISTRY.keySet());
        nowCalled.retainAll(found);
        nowCalled.removeAll(dead);
        assertEquals(Set.of(), nowCalled,
                "以下方法已恢复调用者，请从 UNCALLED_REGISTRY 移除（避免白名单腐烂、掩盖新的无出口方法）：" + nowCalled);

        Set<String> unregistered = new LinkedHashSet<>(dead);
        unregistered.removeAll(UNCALLED_REGISTRY.keySet());
        assertTrue(unregistered.isEmpty(),
                "发现无生产调用者的服务方法：" + unregistered
                        + "。这属于「实现存在、没有出口」的缺陷形态（编译与单测都不会发现）："
                        + "要么补上调用链（入口/调度/读出口/处理出口），要么删除该死代码；"
                        + "确属跨模块调用或有意保留的，登记到 UNCALLED_REGISTRY 并写明理由。");
    }

    /**
     * 自检：合成样本上验证判定逻辑真的会失败。
     *
     * <p>为什么必须有：本门禁的第一版（探索脚本）把"方法声明"也当成了"调用"，结果**永远判不出死方法**
     * （实测漏掉了 markAlertHandled）。声明/调用的区分是本门禁最容易写错的地方，故用样本钉住。
     */
    @Test
    void selfCheck_declarationIsNotACall_andBareCallIs() {
        assertTrue(isDeclaration("void markAlertHandled(Long alertId, Integer version);", "markAlertHandled"),
                "接口方法声明必须被判为声明");
        assertTrue(isDeclaration("public void markAlertHandled(Long alertId, Integer version) {", "markAlertHandled"),
                "带修饰符的覆写声明必须被判为声明");
        assertTrue(isDeclaration("List<String> supportedTypes();", "supportedTypes"),
                "泛型返回类型的声明必须被判为声明");
        assertFalse(isDeclaration("certificateAlertService.markAlertHandled(7L, 0, 42L, null);", "markAlertHandled"),
                "接收者调用必须被判为调用（首版就是在这里漏判）");
        assertFalse(isDeclaration("return certificateAlertService.markAlertHandled(7L, 0, 42L, null);", "markAlertHandled"),
                "return 前缀的调用必须被判为调用");
        assertFalse(isDeclaration("markAlertHandled(7L, 0, 42L, null);", "markAlertHandled"),
                "同类的裸调用必须被判为调用");
    }

    /** 提取接口里声明的方法名（接口无方法体，逐行取"第一个 ( 之前"的标识符）。 */
    private static Set<String> declaredMethods(String body) {
        Set<String> names = new LinkedHashSet<>();
        for (String line : body.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("@") || trimmed.startsWith("*")
                    || trimmed.startsWith("//") || startsWithKeyword(trimmed)) {
                continue;
            }
            int paren = trimmed.indexOf('(');
            if (paren <= 0) {
                continue;
            }
            int start = paren;
            while (start > 0 && (Character.isLetterOrDigit(trimmed.charAt(start - 1)) || trimmed.charAt(start - 1) == '_')) {
                start--;
            }
            String name = trimmed.substring(start, paren);
            if (!name.isEmpty() && isDeclaration(trimmed, name)) {
                names.add(name);
            }
        }
        return names;
    }

    /** 该行是否把 name 当"声明"（而不是调用）。 */
    private static boolean isDeclaration(String trimmedLine, String name) {
        if (startsWithKeyword(trimmedLine)) {
            return false;
        }
        int idx = trimmedLine.indexOf(name + "(");
        if (idx <= 0) {
            return false;
        }
        char prev = trimmedLine.charAt(idx - 1);
        // 名字前面紧跟 . : = ( , + 等 → 是调用/表达式，不是声明（声明前只能是类型或空格）
        if (prev == '.' || prev == ':' || prev == '=' || prev == '(' || prev == ',' || prev == '+') {
            return false;
        }
        String before = trimmedLine.substring(0, idx).trim();
        if (before.isEmpty()) {
            return false; // 裸调用
        }
        for (char c : before.toCharArray()) {
            boolean typeLike = Character.isLetterOrDigit(c) || c == '_' || c == '<' || c == '>'
                    || c == '[' || c == ']' || c == ',' || c == '.' || Character.isWhitespace(c);
            if (!typeLike) {
                return false;
            }
        }
        return true;
    }

    /** 语句关键字开头 → 一定是调用/语句，不可能是方法声明。 */
    private static boolean startsWithKeyword(String trimmed) {
        for (String keyword : new String[]{"return", "if", "for", "while", "switch", "throw", "else",
                "new", "var", "assert", "this", "super", "log", "case", "yield", "package", "import"}) {
            if (trimmed.equals(keyword) || trimmed.startsWith(keyword + " ") || trimmed.startsWith(keyword + ".")
                    || trimmed.startsWith(keyword + "(")) {
                return true;
            }
        }
        return false;
    }

    /** 除声明处以外，main 里是否还有 name( 的调用。 */
    private static boolean hasCaller(Map<Path, String> texts, Path iface, String name) {
        for (Map.Entry<Path, String> entry : texts.entrySet()) {
            if (entry.getKey().equals(iface)) {
                continue;
            }
            for (String line : entry.getValue().split("\n", -1)) {
                if (line.contains(name + "(") && !isDeclaration(line.trim(), name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 去行注释与块注释（与其它门禁同一实现口径，避免把 javadoc 里的示例当成证据）。 */
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

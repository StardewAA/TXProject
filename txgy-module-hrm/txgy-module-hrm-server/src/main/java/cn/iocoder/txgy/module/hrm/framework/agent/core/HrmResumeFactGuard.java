package cn.iocoder.txgy.module.hrm.framework.agent.core;

import cn.hutool.core.util.StrUtil;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简历优化的事实防编造护栏。
 *
 * <p>A02 只允许改写表述，不允许新增事实。但模型输出是自由文本，
 * 无法用结构化字段完全约束，因此这里用启发式规则做兜底检测：
 * 如果改写后的内容出现了原文没有的年份或机构名称，就判定为疑似编造并拒绝该条建议。</p>
 *
 * <p><b>注意这是启发式，不是证明。</b>它能拦住「凭空多出一家公司」「凭空多出 2024 年」
 * 这类最典型的编造，但无法保证覆盖所有事实类型。真正的第一道防线仍是提示词约束。</p>
 */
public final class HrmResumeFactGuard {

    /** 四位年份 */
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(19|20)\\d{2}(?!\\d)");

    /** 机构名称：以常见组织后缀结尾的连续片段 */
    private static final Pattern ORGANIZATION = Pattern.compile(
            "[\\u4e00-\\u9fa5A-Za-z0-9（）()]{2,20}?"
                    + "(?:有限公司|股份有限公司|集团|公司|大学|学院|学校|研究院|研究所|事务所|工程处|项目部|事业部)");

    /**
     * 具体证书与资质名称。
     *
     * <p>必须匹配到**具体的证书名**（如「二级建造师」），不能只匹配「证书」这类通用名词——
     * 否则「二级建造师证」改写成「二级建造师证书」会被误判成新增事实。</p>
     */
    private static final Pattern CERTIFICATE = Pattern.compile(
            "(?:一级|二级|三级|注册|高级|中级|初级)?"
                    + "(?:建造师|造价师|监理工程师|安全工程师|结构工程师|电气工程师|注册会计师)"
                    + "(?:证|书|证书)?");

    private HrmResumeFactGuard() {
    }

    /**
     * 建议值是否引入了原值中没有的事实。
     *
     * @param originalValue  原文片段
     * @param suggestedValue 建议值
     * @return 若疑似新增事实返回 true
     */
    public static boolean introducesNewFact(String originalValue, String suggestedValue) {
        if (StrUtil.isBlank(suggestedValue)) {
            return false;
        }
        return findNewFact(originalValue, suggestedValue) != null;
    }

    /**
     * 指出越界的那一项，用于把「校验不合格」变成可排查的日志。
     *
     * <p>原先只返回 boolean，调用方上报的是一句笼统的「内容格式不符合要求」，
     * 出问题时分不清是年份、机构还是证书被判成新增，只能靠猜 —— 已经为此白排查过一轮。</p>
     *
     * @return 形如 {@code "year:2024"} / {@code "org:某某公司"} / {@code "cert:二级建造师"}；
     *         没有越界时返回 null
     */
    public static String findNewFact(String originalValue, String suggestedValue) {
        if (StrUtil.isBlank(suggestedValue)) {
            return null;
        }
        String hit = firstNewToken(YEAR, "year", originalValue, suggestedValue);
        if (hit == null) {
            hit = firstNewToken(ORGANIZATION, "org", originalValue, suggestedValue);
        }
        if (hit == null) {
            hit = firstNewToken(CERTIFICATE, "cert", originalValue, suggestedValue);
        }
        return hit;
    }

    private static String firstNewToken(Pattern pattern, String prefix, String originalValue, String suggestedValue) {
        Set<String> originalTokens = extract(pattern, originalValue);
        for (String token : extract(pattern, suggestedValue)) {
            if (!isTraceable(token, originalTokens)) {
                return prefix + ":" + token;
            }
        }
        return null;
    }

    /**
     * 这个词能否在原文的同类词里找到出处。
     *
     * <p><b>按包含关系判断，不要求逐字相等。</b>机构正则用的是懒惰量词
     * `{2,20}?`，同一个机构在**不同前后文**里会被截成不同的 token ——
     * 原文里是「陕西华油防腐工程有限公司」，模型写「先后在陕西华油防腐工程有限公司…」
     * 就会被截成后者，逐字比对必然判成「新增机构」。实测这条在「把整段简历正文
     * 整理成字段」的场景里频繁误杀（原文越长、上下文越杂，越容易触发）。</p>
     *
     * <p>改成包含关系后，真正编造的机构（原文里完全不出现）依然会被拦下：
     * 它既不包含任何原文 token，也不被任何原文 token 包含。</p>
     */
    private static boolean isTraceable(String token, Set<String> originalTokens) {
        for (String original : originalTokens) {
            if (original.contains(token) || token.contains(original)) {
                return true;
            }
        }
        return false;
    }

    private static boolean introducesNewToken(Pattern pattern, String originalValue, String suggestedValue) {
        Set<String> originalTokens = extract(pattern, originalValue);
        for (String token : extract(pattern, suggestedValue)) {
            if (!originalTokens.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /** 提取文本中的年份，便于测试与排查 */
    public static Set<String> extractYears(String text) {
        return extract(YEAR, text);
    }

    /** 提取文本中的机构名称，便于测试与排查 */
    public static Set<String> extractOrganizations(String text) {
        return extract(ORGANIZATION, text);
    }

    /**
     * 比对前先把 JSON 转义序列还原成空白。
     *
     * <p>素材传进来的是 `answersContent`，也就是**整个负载 JSON 序列化后的字符串** ——
     * 里面的换行是 `\n` **两个字符**，而 `n` 正好落在机构正则的字符类
     * `[一-龥A-Za-z0-9（）()]` 里。于是「行首的延安大学」被提成 **`n延安大学`**，
     * 与模型输出里干净的 `延安大学` 对不上，整份结果被判成「新增机构」直接拒绝。</p>
     *
     * <p>简历里的公司与学校**几乎总在行首**，所以这条不是偶发：
     * 素材越接近整篇简历正文（如附件结构化场景），越是必然踩中。</p>
     */
    private static String normalizeEscapes(String text) {
        return StrUtil.nullToEmpty(text)
                .replace("\\r", " ")
                .replace("\\n", " ")
                .replace("\\t", " ")
                .replace("\\\"", " ");
    }

    private static Set<String> extract(Pattern pattern, String text) {
        Set<String> tokens = new LinkedHashSet<>();
        if (StrUtil.isBlank(text)) {
            return tokens;
        }
        Matcher matcher = pattern.matcher(normalizeEscapes(text));
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }
}

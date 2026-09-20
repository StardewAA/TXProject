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
        return introducesNewToken(YEAR, originalValue, suggestedValue)
                || introducesNewToken(ORGANIZATION, originalValue, suggestedValue)
                || introducesNewToken(CERTIFICATE, originalValue, suggestedValue);
    }

    /** 提取文本中的年份，便于测试与排查 */
    public static Set<String> extractYears(String text) {
        return extract(YEAR, text);
    }

    /** 提取文本中的机构名称，便于测试与排查 */
    public static Set<String> extractOrganizations(String text) {
        return extract(ORGANIZATION, text);
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

    private static Set<String> extract(Pattern pattern, String text) {
        Set<String> tokens = new LinkedHashSet<>();
        if (StrUtil.isBlank(text)) {
            return tokens;
        }
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }
}

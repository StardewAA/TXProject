package cn.iocoder.txgy.module.hrm.util;

import cn.hutool.core.util.StrUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感信息脱敏工具。
 *
 * <p>简历与职位正文在送入模型前必须脱敏。这里的策略是：
 * 能识别的一律替换为掩码；无法保证脱敏干净时由调用方拒绝调用模型（失败关闭），
 * 不允许「大概脱敏了」就发出去。</p>
 *
 * <p>注意：结构化字段（手机号、邮箱、地址等）应通过白名单直接排除，
 * 本工具主要处理自由文本里夹带的敏感信息。</p>
 */
public final class HrmSensitiveTextMasker {

    /** 手机号：允许中间有空格或短横线 */
    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d[\\s-]?\\d{4}[\\s-]?\\d{4}(?!\\d)");

    /** 邮箱 */
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /** 18 位身份证（末位可为 X） */
    private static final Pattern ID_CARD_18 = Pattern.compile("(?<!\\d)\\d{6}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx](?!\\d)");

    /** 15 位身份证 */
    private static final Pattern ID_CARD_15 = Pattern.compile("(?<!\\d)\\d{6}\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}(?!\\d)");

    private static final Pattern SENSITIVE_LABEL = Pattern.compile(
            "(?m)(性别|年龄|民族|宗教|婚姻状况|婚育|户籍|政治面貌|身高|体重)\\s*[:：]\\s*[^\\r\\n，,；;]{1,24}");

    /**
     * 门牌级详细地址。
     *
     * <p>要求「区域标记（省/市/区/县/旗）」与「街道标记（路/街/号/栋/室 等）」同时出现，
     * 避免把「在西安市工作了三年」这类普通表述误判成地址。</p>
     */
    private static final Pattern DETAILED_ADDRESS = Pattern.compile(
            "[\\u4e00-\\u9fa5]{2,12}(?:省|市|区|县|旗)"
                    + "[\\u4e00-\\u9fa5\\d]{1,30}?"
                    + "(?:路|街道|街|巷|大道|号|栋|幢|单元|室|楼|村|镇)"
                    + "[\\u4e00-\\u9fa5\\d\\-]{0,20}");

    private HrmSensitiveTextMasker() {
    }

    /**
     * 对文本脱敏。null 或空白原样返回，避免调用方额外判空。
     */
    public static String mask(String text) {
        if (StrUtil.isBlank(text)) {
            return text;
        }
        String result = text;
        result = replaceAll(ID_CARD_18, result, HrmSensitiveTextMasker::maskIdCard);
        result = replaceAll(ID_CARD_15, result, HrmSensitiveTextMasker::maskIdCard);
        result = replaceAll(MOBILE, result, HrmSensitiveTextMasker::maskMobile);
        result = replaceAll(EMAIL, result, HrmSensitiveTextMasker::maskEmail);
        result = replaceAll(DETAILED_ADDRESS, result, HrmSensitiveTextMasker::maskAddress);
        return result;
    }

    /** 对上传简历提取出的纯文本做二次字段级脱敏。 */
    public static String maskResumeText(String text) {
        if (StrUtil.isBlank(text)) return text;
        String result = mask(text);
        return SENSITIVE_LABEL.matcher(result).replaceAll("$1：****");
    }

    /**
     * 脱敏后仍能识别出高置信度敏感信息时为 true。
     *
     * <p>用于失败关闭：检测到就禁止调用模型，而不是带着风险继续。</p>
     */
    public static boolean stillContainsSensitive(String text) {
        if (StrUtil.isBlank(text)) {
            return false;
        }
        return MOBILE.matcher(text).find()
                || EMAIL.matcher(text).find()
                || ID_CARD_18.matcher(text).find()
                || ID_CARD_15.matcher(text).find();
    }

    /** 保留前 3 位与后 4 位 */
    public static String maskMobile(String mobile) {
        if (StrUtil.isBlank(mobile)) {
            return mobile;
        }
        String digits = mobile.replaceAll("[\\s-]", "");
        if (digits.length() < 7) {
            return "****";
        }
        return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 4);
    }

    /** 保留首字符与域名 */
    public static String maskEmail(String email) {
        if (StrUtil.isBlank(email)) {
            return email;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "****";
        }
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        String head = local.substring(0, 1);
        return head + "***" + domain;
    }

    /** 保留前 6 位与后 4 位 */
    public static String maskIdCard(String idCard) {
        if (StrUtil.isBlank(idCard)) {
            return idCard;
        }
        String value = idCard.trim();
        if (value.length() <= 10) {
            return "****";
        }
        return value.substring(0, 6) + "********" + value.substring(value.length() - 4);
    }

    /** 只保留到区县层级，门牌等更细粒度信息整体掩码 */
    public static String maskAddress(String address) {
        if (StrUtil.isBlank(address)) {
            return address;
        }
        // 优先保留到区县；没有区县时退回保留到市/省
        for (String regionSuffix : new String[]{"区", "县", "旗", "市", "省"}) {
            Matcher matcher = Pattern.compile("(.*?" + regionSuffix + ")").matcher(address);
            if (matcher.find()) {
                return matcher.group(1) + "****";
            }
        }
        return "****";
    }

    private static String replaceAll(Pattern pattern, String text, java.util.function.Function<String, String> replacer) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacer.apply(matcher.group())));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }
}

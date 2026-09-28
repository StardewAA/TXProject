package cn.iocoder.txgy.module.hrm.util;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简历「规则化字段提取」（不依赖 AI）
 *
 * <p>用于入职建档时自动补全员工档案：把简历文本（在线简历内容或附件 PDF 抽取出的文本）里
 * 能稳定识别的字段按规则抽出来，只填空字段、不覆盖已有值，也不做任何推断性编造。</p>
 *
 * <p>可识别：身份证号（并据此推算出生日期/性别/年龄）、出生日期、性别、年龄、民族、籍贯、
 * 现住址、邮箱、手机号、最高学历、毕业院校、所学专业、证书名称。</p>
 *
 * @author OPENLAB BS
 */
public final class HrmResumeRuleExtractor {

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");
    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)(1[3-9]\\d)[-\\s]?(\\d{4})[-\\s]?(\\d{4})(?!\\d)");
    private static final Pattern ID_NUMBER = Pattern.compile("(?<!\\d)(\\d{17}[\\dXx])(?!\\d)");
    private static final Pattern BIRTHDAY = Pattern.compile(
            "(?:出生日期|出生年月|出生时间|生日)[：:\\s]*(\\d{4})\\s*[-./年]\\s*(\\d{1,2})(?:\\s*[-./月]\\s*(\\d{1,2}))?");
    private static final Pattern SEX = Pattern.compile("(?:性别)[：:\\s]*([男女])");
    private static final Pattern AGE = Pattern.compile("(?:年龄)[：:\\s]*(\\d{1,2})|(?<!\\d)(\\d{2})\\s*岁");
    private static final Pattern NATION = Pattern.compile("(?:民族)[：:\\s]*([\\u4e00-\\u9fa5]{1,8}族)");
    private static final Pattern NATIVE_PLACE = Pattern.compile(
            "(?:籍贯|户籍所在地|户籍地|户籍)[：:\\s]*([^\\n，,；;：:]{2,20})");
    private static final Pattern ADDRESS = Pattern.compile(
            "(?:现居住地|现住址|现住地址|通讯地址|家庭住址|联系地址|住址)[：:\\s]*([^\\n]{4,60})");
    private static final Pattern EDUCATION = Pattern.compile(
            "(?:最高学历|学历|文化程度)[：:\\s]*([^\\n，,；;]{2,10})");
    private static final Pattern SCHOOL = Pattern.compile(
            "([\\u4e00-\\u9fa5]{2,20}(?:大学|学院|学校|职业技术学院))");
    private static final Pattern MAJOR = Pattern.compile(
            "(?:所学专业|专业)[：:\\s]*([^\\n，,；;]{2,20})|([\\u4e00-\\u9fa5]{2,12})专业");

    /**
     * 常见证书关键词：命中即视为简历里写明了该类证书（仅记录名称，不编造编号/有效期）
     */
    private static final List<String> CERTIFICATE_KEYWORDS = List.of(
            "一级建造师", "二级建造师", "建造师", "造价工程师", "监理工程师", "安全工程师",
            "注册会计师", "注册消防工程师", "注册安全工程师", "结构工程师", "电气工程师",
            "安全生产考核合格证", "安全员证", "特种作业操作证", "高处作业证", "电工证", "焊工证",
            "起重机械作业证", "压力容器操作证", "叉车证", "危险化学品作业证",
            "人力资源管理师", "会计从业资格证", "会计专业技术资格", "初级会计", "中级会计",
            "教师资格证", "计算机等级证书", "英语四级", "英语六级", "CET-4", "CET-6",
            "驾驶证", "普通话等级证书");

    @Data
    @Accessors(chain = true)
    public static class Extracted {
        /** 证件类型（身份证=1），识别到身份证号时填充 */
        private Integer idType;
        private String idNumber;
        private LocalDateTime birthday;
        private Integer sex;
        private Integer age;
        private String nation;
        private String nativePlace;
        private String address;
        private String email;
        private String mobile;
        private Integer highestEducation;
        private String graduateSchool;
        private String major;
        private List<String> certificates = new ArrayList<>();

        /** 是否提取到任何可用字段 */
        public boolean isEmpty() {
            return idNumber == null && birthday == null && sex == null && age == null
                    && StrUtil.isAllBlank(nation, nativePlace, address, email, mobile, graduateSchool, major)
                    && highestEducation == null && certificates.isEmpty();
        }
    }

    public static Extracted extract(String rawText) {
        Extracted result = new Extracted();
        if (StrUtil.isBlank(rawText)) {
            return result;
        }
        // 1) 统一全角符号；2) 逐行压缩「汉字之间的空格」——OCR 常见 "出 生年 月"、"籍 贯"，
        //    不压缩就会导致标签匹配不到（只在行内压缩，保留换行边界）。
        String text = java.util.Arrays.stream(rawText.replace('\u3000', ' ').replace("－", "-")
                        .split("\n", -1))
                .map(line -> line.replaceAll("(?<=[\\u4e00-\\u9fa5])\\s+(?=[\\u4e00-\\u9fa5])", ""))
                .collect(java.util.stream.Collectors.joining("\n"));
        extractIdCard(text, result);
        extractByPatterns(text, result);
        extractCertificates(text, result);
        return result;
    }

    /** 身份证号 → 出生日期、性别、年龄 */
    private static void extractIdCard(String text, Extracted result) {
        Matcher matcher = ID_NUMBER.matcher(text);
        if (!matcher.find()) {
            return;
        }
        String idNumber = matcher.group(1).toUpperCase();
        if (!isValidIdCard(idNumber)) {
            return;
        }
        result.setIdType(1).setIdNumber(idNumber);
        try {
            LocalDate birthday = LocalDate.of(
                    Integer.parseInt(idNumber.substring(6, 10)),
                    Integer.parseInt(idNumber.substring(10, 12)),
                    Integer.parseInt(idNumber.substring(12, 14)));
            result.setBirthday(birthday.atStartOfDay());
            result.setAge(Period.between(birthday, LocalDate.now()).getYears());
        } catch (RuntimeException ignored) {
            // 生日非法时忽略，不影响其它字段
        }
        int sexDigit = idNumber.charAt(16) - '0';
        result.setSex(sexDigit % 2 == 1 ? 1 : 2);
    }

    private static void extractByPatterns(String text, Extracted result) {
        Matcher email = EMAIL.matcher(text);
        if (email.find()) {
            result.setEmail(email.group());
        }
        Matcher mobile = MOBILE.matcher(text);
        if (mobile.find()) {
            // 兼容 138-9225-1297 / 138 9225 1297 这类 OCR 结果
            result.setMobile(mobile.group(1) + mobile.group(2) + mobile.group(3));
        }
        if (result.getBirthday() == null) {
            Matcher birthday = BIRTHDAY.matcher(text);
            if (birthday.find()) {
                try {
                    int day = birthday.group(3) == null ? 1 : Integer.parseInt(birthday.group(3));
                    LocalDate date = LocalDate.of(Integer.parseInt(birthday.group(1)),
                            Integer.parseInt(birthday.group(2)), Math.min(day, 28));
                    result.setBirthday(date.atStartOfDay());
                } catch (RuntimeException ignored) {
                    // 忽略非法日期
                }
            }
        }
        if (result.getSex() == null) {
            Matcher sex = SEX.matcher(text);
            if (sex.find()) {
                result.setSex("男".equals(sex.group(1)) ? 1 : 2);
            }
        }
        if (result.getAge() == null) {
            Matcher age = AGE.matcher(text);
            if (age.find()) {
                String value = age.group(1) != null ? age.group(1) : age.group(2);
                result.setAge(Integer.parseInt(value));
            }
        }
        result.setNation(firstGroup(NATION, text));
        result.setNativePlace(trimPlace(firstGroup(NATIVE_PLACE, text)));
        result.setAddress(StrUtil.maxLength(StrUtil.trim(firstGroup(ADDRESS, text)), 200));
        // 学历：优先取"最高学历/学历"标注；否则在全文里找学历关键词
        Integer education = HrmOnboardFillUtils.mapEducationTextToEmployeeEducation(firstGroup(EDUCATION, text));
        if (education == null) {
            education = HrmOnboardFillUtils.mapEducationTextToEmployeeEducation(text);
        }
        result.setHighestEducation(education);
        result.setGraduateSchool(trimPlace(firstGroup(SCHOOL, text)));
        String major = firstGroup(MAJOR, text);
        result.setMajor(StrUtil.maxLength(trimPlace(major), 60));
    }

    private static void extractCertificates(String text, Extracted result) {
        Set<String> matched = new LinkedHashSet<>();
        for (String keyword : CERTIFICATE_KEYWORDS) {
            if (text.contains(keyword)) {
                matched.add(keyword);
            }
        }
        // 去掉被更长关键词包含的短词（例如同时命中"建造师"与"一级建造师"时只保留后者）
        List<String> certificates = new ArrayList<>();
        for (String candidate : matched) {
            boolean covered = matched.stream()
                    .anyMatch(other -> !other.equals(candidate) && other.contains(candidate));
            if (!covered) {
                certificates.add(candidate);
            }
        }
        result.setCertificates(certificates);
    }

    private static String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        for (int i = 1; i <= matcher.groupCount(); i++) {
            if (matcher.group(i) != null && !matcher.group(i).isBlank()) {
                return matcher.group(i);
            }
        }
        return null;
    }

    private static String trimPlace(String value) {
        if (value == null) {
            return null;
        }
        // OCR 常在字段尾部带噪声字符（~ · | 、等），统一清理
        String cleaned = StrUtil.trim(value.replaceAll("[\\s]+", " "));
        cleaned = StrUtil.trim(cleaned.replaceAll("[~·|｜、,，;；:：]+$", ""));
        return StrUtil.isBlank(cleaned) ? null : cleaned;
    }

    /** 身份证校验位（GB 11643-1999），避免把订单号/学号误判成身份证 */
    private static boolean isValidIdCard(String idNumber) {
        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] checkCodes = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            char c = idNumber.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            sum += (c - '0') * weights[i];
        }
        return checkCodes[sum % 11] == idNumber.charAt(17);
    }

    private HrmResumeRuleExtractor() {
    }

}

package cn.iocoder.txgy.module.hrm.service.agent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Set;

/**
 * 把 AI 解析出的筛选条件应用到本地候选人数据上。
 *
 * <p>这是「模型只看条件、数据不出内网」的落点：模型产出的只是一组字段条件，
 * 是否命中由本类拿真实数据判断，因此户籍、性别、年龄这些属性从头到尾没有离开过服务端。</p>
 */
public final class HrmConversationFilterMatcher {

    /**
     * 985 高校名单（39 所）。
     *
     * <p>刻意写成常量而不是交给模型判断——院校层次是可枚举的确定事实，
     * 让模型凭常识回答既不稳定也不可复核。</p>
     */
    private static final Set<String> TIER_985_SCHOOLS = Set.of(
            "清华大学", "北京大学", "中国人民大学", "北京航空航天大学", "北京理工大学",
            "中国农业大学", "北京师范大学", "中央民族大学", "南开大学", "天津大学",
            "大连理工大学", "东北大学", "吉林大学", "哈尔滨工业大学", "复旦大学",
            "同济大学", "上海交通大学", "华东师范大学", "南京大学", "东南大学",
            "浙江大学", "中国科学技术大学", "厦门大学", "山东大学", "中国海洋大学",
            "武汉大学", "华中科技大学", "湖南大学", "中南大学", "国防科技大学",
            "中山大学", "华南理工大学", "四川大学", "电子科技大学", "重庆大学",
            "西安交通大学", "西北工业大学", "西北农林科技大学", "兰州大学");

    /** 关键词类字段，用于判断「这次到底有没有解析出条件」 */
    private static final List<String> KEYWORD_FIELDS = List.of(
            "domicileKeywords", "schoolTierKeywords", "schoolKeywords", "educationKeywords",
            "genderKeywords", "postNameKeywords", "expectedCityKeywords", "resumeKeywords");

    private HrmConversationFilterMatcher() {
    }

    /** 一次筛选用到的候选人字段 */
    @Data
    @Accessors(chain = true)
    public static class Candidate {
        /** 毕业院校 */
        private String school;
        /** 学历名称，如「本科」 */
        private String educationName;
        /** 1 男 2 女 */
        private Integer sex;
        private Integer age;
        /** 工作年限 */
        private Integer workTime;
        /** 应聘职位 */
        private String postName;
        /** 户籍地，来自求职者的在线简历，可能为空 */
        private String domicile;
        /** 期望工作城市 */
        private List<String> expectedCities;
        /** 简历原文，用于关键词命中 */
        private String resumeText;
    }

    /**
     * 是否命中全部条件。
     *
     * <p>条件要求了某个字段、而候选人没有该数据时判定为不命中：宁可少列一个人，
     * 也不要把「无法判断」当成「符合」。返回结果里会另行统计这类缺失，供界面提示。</p>
     */
    public static boolean matches(JSONObject filters, Candidate candidate) {
        return matchKeywords(filters, "domicileKeywords", candidate.getDomicile())
                && matchSchoolTier(filters, candidate.getSchool())
                && matchKeywords(filters, "schoolKeywords", candidate.getSchool())
                && matchKeywords(filters, "educationKeywords", candidate.getEducationName())
                && matchGender(filters, candidate.getSex())
                && matchRange(filters, "ageMin", "ageMax", candidate.getAge())
                && matchRange(filters, "workTimeMin", "workTimeMax", candidate.getWorkTime())
                && matchKeywords(filters, "postNameKeywords", candidate.getPostName())
                && matchAnyKeywords(filters, "expectedCityKeywords", candidate.getExpectedCities())
                && matchKeywords(filters, "resumeKeywords", candidate.getResumeText());
    }

    /** 条件里是否真的带了可执行的维度；全空说明没解析出条件，调用方应提示用户换个说法 */
    public static boolean hasAnyCondition(JSONObject filters) {
        if (KEYWORD_FIELDS.stream().anyMatch(field -> hasKeyword(filters, field))) {
            return true;
        }
        return !filters.isNull("ageMin") || !filters.isNull("ageMax")
                || !filters.isNull("workTimeMin") || !filters.isNull("workTimeMax");
    }

    private static boolean hasKeyword(JSONObject filters, String key) {
        JSONArray array = filters.getJSONArray(key);
        return array != null && !array.isEmpty();
    }

    /** 目标值包含任一关键词即命中；条件为空视为不限 */
    private static boolean matchKeywords(JSONObject filters, String key, String target) {
        if (!hasKeyword(filters, key)) {
            return true;
        }
        if (StrUtil.isBlank(target)) {
            return false;
        }
        for (Object item : filters.getJSONArray(key)) {
            if (target.contains(String.valueOf(item))) {
                return true;
            }
        }
        return false;
    }

    /** 目标列表里任一元素包含关键词即命中（用于期望城市这类多值字段） */
    private static boolean matchAnyKeywords(JSONObject filters, String key, List<String> targets) {
        if (!hasKeyword(filters, key)) {
            return true;
        }
        if (targets == null || targets.isEmpty()) {
            return false;
        }
        for (String target : targets) {
            if (StrUtil.isBlank(target)) {
                continue;
            }
            for (Object item : filters.getJSONArray(key)) {
                if (target.contains(String.valueOf(item))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchSchoolTier(JSONObject filters, String school) {
        if (!hasKeyword(filters, "schoolTierKeywords")) {
            return true;
        }
        if (StrUtil.isBlank(school)) {
            return false;
        }
        for (Object item : filters.getJSONArray("schoolTierKeywords")) {
            if ("985".equals(String.valueOf(item)) && TIER_985_SCHOOLS.contains(school.trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchGender(JSONObject filters, Integer sex) {
        if (!hasKeyword(filters, "genderKeywords")) {
            return true;
        }
        if (sex == null) {
            return false;
        }
        for (Object item : filters.getJSONArray("genderKeywords")) {
            String text = String.valueOf(item);
            if ((sex == 1 && text.contains("男")) || (sex == 2 && text.contains("女"))) {
                return true;
            }
        }
        return false;
    }

    /** 区间条件：未提到的边界不参与过滤；区间要求了但候选人没填该字段时不命中 */
    private static boolean matchRange(JSONObject filters, String minKey, String maxKey, Integer value) {
        boolean hasMin = !filters.isNull(minKey);
        boolean hasMax = !filters.isNull(maxKey);
        if (!hasMin && !hasMax) {
            return true;
        }
        if (value == null) {
            return false;
        }
        if (hasMin && value < filters.getInt(minKey)) {
            return false;
        }
        return !hasMax || value <= filters.getInt(maxKey);
    }
}

package cn.iocoder.txgy.module.hrm.framework.agent.core;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.iocoder.txgy.module.hrm.util.HrmResumeAgentPayloadUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_MODEL_OUTPUT_INVALID;

/**
 * AI 简历草稿结果校验。
 *
 * <p>生成结果仍是模型输出，不能直接当成事实写入用户简历。本校验器同时检查
 * 结构、长度、敏感信息和事实来源：公司、时间、学历、证书、技能及成果中的
 * 关键内容必须能在用户回答中找到，疑似新增事实的结果直接拒绝。</p>
 *
 * <p><b>为什么每处拒绝都带一行 warn 日志</b>：这个校验器一共有十来条规则，
 * 但它们对外只抛同一个错误码，用户界面也只会显示一句「内容格式不符合要求」。
 * 一旦线上失败，完全看不出是结构不对、字段找不到依据、还是被判新增了事实，
 * 只能靠猜（已经为此白排查过一轮）。日志里只记**规则名、字段名与长度**，
 * 以及年份/数字这类本身不敏感的 token，不记简历正文 ——
 * 与模型客户端「绝不记录请求负载」是同一条底线。</p>
 */
@Slf4j
public final class HrmResumeGenerateResultValidator {

    private static final int MAX_HEADLINE_LENGTH = 200;
    private static final int MAX_SUMMARY_LENGTH = 1200;
    /**
     * 单个条目字段（描述、成果、在校经历…）的长度上限。
     *
     * <p>原先是 500，按「从问答生成」的口径定的 —— 那边模型是**写**一段简短说明。
     * 但结构化场景是**搬运**：素材就是整段简历正文，模型会把原文里一整块项目描述
     * 原样搬进 description，实测出现过 603 字，于是被判超长、整份结果拒绝。</p>
     *
     * <p>放到 3000：比任何一段简历正文都宽松（素材本身也就封顶 8000），
     * 同时仍然拦得住「模型把整篇简历塞进一个字段」这种失控输出。</p>
     */
    private static final int MAX_FIELD_LENGTH = 3000;
    private static final int MAX_SKILLS = 12;
    private static final int MAX_EXPERIENCES = 8;
    private static final int MAX_PROJECTS = 8;
    private static final int MAX_EDUCATION = 5;
    private static final Pattern NUMBER = Pattern.compile("(?<![\\d.])\\d+(?:\\.\\d+)?%?(?![\\d.])");
    /** 归一化时一并去掉的排版符号（中英文标点 + 空白），见 normalize 的说明 */
    private static final Pattern PUNCTUATION = Pattern.compile(
            "[\\s\\p{Punct}、。，；：？！“”‘’「」『』（）〔〕【】《》〈〉·—～…]+");

    private static final Set<String> ROOT_FIELDS = Set.of(
            "headline", "summary", "skills", "experiences", "projects", "educationExperiences");
    private static final Set<String> EXPERIENCE_FIELDS = Set.of(
            "company", "position", "start", "end", "description", "achievements");
    private static final Set<String> PROJECT_FIELDS = Set.of(
            "name", "role", "start", "end", "description", "achievements", "highlight");
    private static final Set<String> EDUCATION_FIELDS = Set.of(
            "school", "degree", "major", "start", "end", "experience");

    private HrmResumeGenerateResultValidator() {
    }

    /** 校验并返回可以交给前端编辑的草稿结果。 */
    public static JSONObject validate(String rawContent, String answersContent) {
        JSONObject root = HrmRecruitAgentResultValidationSupport.parseResultObject(rawContent);
        requireExactFields(root, ROOT_FIELDS, "root");
        if (HrmRecruitAgentResultValidationSupport.containsForbiddenKey(root)
                || HrmResumeAgentPayloadUtils.containsSensitive(root)) {
            fail("root.sensitive", "");
        }

        String source = StrUtil.nullToEmpty(answersContent);
        String headline = requireText(root, "headline", MAX_HEADLINE_LENGTH, true, "root");
        String summary = requireText(root, "summary", MAX_SUMMARY_LENGTH, true, "root");
        root.set("skills", sanitizedSkills(root.getJSONArray("skills"), source));
        validateExperiences(root.getJSONArray("experiences"), source);
        validateProjects(root.getJSONArray("projects"), source);
        validateEducation(root.getJSONArray("educationExperiences"), source);

        // 防止模型在摘要、亮点等自由文本中额外写入新的年份、机构、证书或数字成果。
        String newFact = HrmResumeFactGuard.findNewFact(source, root.toString());
        if (newFact != null) {
            fail("fact.newFact", newFact);
        }
        String newNumber = firstNewNumber(source, headline + "\n" + summary + "\n" + root.toString());
        if (newNumber != null) {
            fail("fact.newNumber", "number=" + newNumber);
        }
        return root;
    }

    /**
     * 校验不合格：记一行可排查的日志再抛业务异常。
     *
     * @param rule   规则名，直接对应下面某个方法
     * @param detail 结构信息（字段名、长度、年份/数字这类 token），不放简历正文
     */
    private static void fail(String rule, String detail) {
        log.warn("[hrm][agent] 生成结果校验不合格：rule={}, detail={}", rule, detail);
        throw exception(AGENT_MODEL_OUTPUT_INVALID);
    }

    /**
     * 校验技能标签，并把「在素材里找不到出处」的那些丢掉。
     *
     * <p>结构与长度不合格仍然整份拒绝（那是模型没按契约输出）；但单个标签溯源不上，
     * 只丢那一条 —— 技能是一串短标签，模型很容易把素材里的长句做断句或标点上的规范化，
     * 于是一个标签对不上就让整份初稿失败，用户看到的是「AI 挂了」。
     * 丢掉它既不会把编造的内容写进简历，也不影响其余部分可用。</p>
     */
    private static JSONArray sanitizedSkills(JSONArray skills, String source) {
        if (skills == null) {
            fail("skills.shape", "not-array");
        }
        // 契约里写了最多 12 项，但模型并不总遵守（实测出现过一次 25 项）。
        // 截断而不是整份拒绝，理由同上：列表长度是可以自己收住的小问题。
        if (skills.size() > MAX_SKILLS) {
            log.warn("[hrm][agent] 技能条数超出上限，已截断：size={}, max={}", skills.size(), MAX_SKILLS);
        }
        int limit = Math.min(skills.size(), MAX_SKILLS);
        JSONArray kept = new JSONArray();
        for (int index = 0; index < limit; index++) {
            Object item = skills.get(index);
            if (!(item instanceof String skill)) {
                fail("skills.type", "index=" + index);
            }
            String text = (String) item;
            if (!isText(text, MAX_FIELD_LENGTH)) {
                fail("skills.length", "index=" + index + " len=" + text.length());
            }
            if (!isContainedInSource(text, source)) {
                log.warn("[hrm][agent] 丢弃无法溯源的技能标签：index={}, len={}", index, text.length());
                continue;
            }
            kept.add(text);
        }
        return kept;
    }

    private static void validateExperiences(JSONArray experiences, String source) {
        validateArray(experiences, MAX_EXPERIENCES, EXPERIENCE_FIELDS, "experiences");
        for (int index = 0; index < experiences.size(); index++) {
            JSONObject experience = (JSONObject) experiences.get(index);
            String path = "experiences[" + index + "]";
            validateFactField(experience, "company", source, path);
            validateFactField(experience, "position", source, path);
            validateFactField(experience, "start", source, path);
            validateFactField(experience, "end", source, path);
            validateTextField(experience, "description", path);
            validateTextField(experience, "achievements", path);
        }
    }

    private static void validateProjects(JSONArray projects, String source) {
        validateArray(projects, MAX_PROJECTS, PROJECT_FIELDS, "projects");
        for (int index = 0; index < projects.size(); index++) {
            JSONObject project = (JSONObject) projects.get(index);
            String path = "projects[" + index + "]";
            validateFactField(project, "name", source, path);
            validateFactField(project, "role", source, path);
            validateFactField(project, "start", source, path);
            validateFactField(project, "end", source, path);
            validateTextField(project, "description", path);
            validateTextField(project, "achievements", path);
            validateTextField(project, "highlight", path);
        }
    }

    private static void validateEducation(JSONArray education, String source) {
        validateArray(education, MAX_EDUCATION, EDUCATION_FIELDS, "educationExperiences");
        for (int index = 0; index < education.size(); index++) {
            JSONObject record = (JSONObject) education.get(index);
            String path = "educationExperiences[" + index + "]";
            validateFactField(record, "school", source, path);
            validateFactField(record, "degree", source, path);
            validateFactField(record, "major", source, path);
            validateFactField(record, "start", source, path);
            validateFactField(record, "end", source, path);
            validateTextField(record, "experience", path);
        }
    }

    private static void validateArray(JSONArray array, int maxSize, Set<String> fields, String label) {
        if (array == null || array.size() > maxSize) {
            fail(label + ".shape", "size=" + (array == null ? "null" : array.size()));
        }
        for (int index = 0; index < array.size(); index++) {
            Object item = array.get(index);
            if (!(item instanceof JSONObject object)) {
                fail(label + ".type", "index=" + index);
            }
            requireExactFields((JSONObject) item, fields, label + "[" + index + "]");
        }
    }

    private static void validateFactField(JSONObject object, String field, String source, String path) {
        String value = requireText(object, field, MAX_FIELD_LENGTH, false, path);
        if (StrUtil.isNotBlank(value) && !isContainedInSource(value, source)) {
            // 字段名与长度足以定位，不回显正文
            fail("fact.notInSource", path + "." + field + " len=" + value.length());
        }
    }

    private static void validateTextField(JSONObject object, String field, String path) {
        requireText(object, field, MAX_FIELD_LENGTH, false, path);
    }

    private static String requireText(JSONObject object, String field, int maxLength, boolean nonBlank, String path) {
        Object value = object.get(field);
        if (!(value instanceof String text)) {
            fail("text.type", path + "." + field + " type=" + (value == null ? "null" : value.getClass().getSimpleName()));
            return "";
        }
        if (text.length() > maxLength || (nonBlank && StrUtil.isBlank(text))) {
            fail("text.length", path + "." + field + " len=" + text.length());
        }
        return text;
    }

    private static boolean isText(String value, int maxLength) {
        return StrUtil.isNotBlank(value) && value.length() <= maxLength;
    }

    private static boolean isContainedInSource(String value, String source) {
        return normalize(value).length() > 0 && normalize(source).contains(normalize(value));
    }

    /**
     * 比对前一并去掉空白与标点。
     *
     * <p>这条规则管的是「内容是不是来自用户素材」，不是「标点有没有写成一模一样」。
     * 只去空白的话，模型把素材里的「点蚀、缝隙腐蚀」写成「点蚀,缝隙腐蚀」、
     * 或把「（牺牲阳极 / 外加电流）」的括号去掉，就会被判成「素材里找不到」——
     * 实测这是生成结果被拒的**主要来源**（日志里 skills.notInSource 占绝大多数）。</p>
     */
    private static String normalize(String value) {
        return PUNCTUATION.matcher(StrUtil.nullToEmpty(value)).replaceAll("").toLowerCase();
    }

    /** 返回第一个在素材里找不到的数字；都找得到时返回 null */
    private static String firstNewNumber(String source, String result) {
        Set<String> sourceNumbers = extractNumbers(source);
        Matcher matcher = NUMBER.matcher(result);
        while (matcher.find()) {
            if (!sourceNumbers.contains(normalizeNumber(matcher.group()))) {
                return matcher.group();
            }
        }
        return null;
    }

    /**
     * 数字的等价形式：去掉前导零。
     *
     * <p>`2022-07` 这类日期，模型很自然会写成「2022 年 7 月」—— 于是数字从 `07` 变成 `7`，
     * 而素材里只有 `07`，逐字比对就判成「新增数字」。同一件事的两种写法不该算编造。</p>
     */
    private static String normalizeNumber(String value) {
        Matcher matcher = Pattern.compile("0*(\\d+(?:\\.\\d+)?)(%?)").matcher(value);
        return matcher.matches() ? matcher.group(1) + matcher.group(2) : value;
    }

    private static Set<String> extractNumbers(String text) {
        Set<String> numbers = new LinkedHashSet<>();
        Matcher matcher = NUMBER.matcher(StrUtil.nullToEmpty(text));
        while (matcher.find()) {
            numbers.add(normalizeNumber(matcher.group()));
        }
        return numbers;
    }

    private static void requireExactFields(JSONObject object, Set<String> fields, String path) {
        if (!object.keySet().equals(fields)) {
            // 只记键名，不记值；键名是结构信息，正是排查所需要的
            fail("fields.exact", path + " actual=" + object.keySet());
        }
    }
}

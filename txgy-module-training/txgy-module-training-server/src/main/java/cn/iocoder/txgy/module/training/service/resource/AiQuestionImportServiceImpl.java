package cn.iocoder.txgy.module.training.service.resource;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.training.config.AiModelProperties;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.AiQuestionImportReqVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.AiQuestionItemVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.AiQuestionParseRespVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.QuestionSaveReqVO;
import cn.iocoder.txgy.module.training.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.training.enums.resource.ResourceConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 试题 AI 智能导入 Service 实现
 *
 * @author 云培训学院课题小组
 */
@Service
@Slf4j
public class AiQuestionImportServiceImpl implements AiQuestionImportService {

    /** AI 导入支持的文档类型 */
    private static final Set<String> AI_DOC_EXTS = new HashSet<>(Arrays.asList("pdf", "doc", "docx", "wps"));

    /** 本地兜底：题干行（1. / 1、/ 一、） */
    private static final Pattern STEM_PATTERN = Pattern.compile("^\\s*(\\d{1,3})[.、．](.+)$");
    /** 本地兜底：选项行（A. xxx / A、xxx） */
    private static final Pattern OPTION_PATTERN = Pattern.compile("^\\s*([A-D])[.、．：:]\\s*(.+)$");
    /** 本地兜底：答案行（选项答案 A-D） */
    private static final Pattern ANSWER_PATTERN = Pattern.compile("^\\s*(?:答案|参考答案|正确答案)\\s*[:：]?\\s*([A-D]{1,4})\\s*$");
    /** 本地兜底：判断题答案（对/错/正确/错误/√/×） */
    private static final Pattern JUDGE_ANSWER_PATTERN = Pattern.compile("^\\s*(?:答案|参考答案|正确答案)?\\s*[:：]?\\s*(对|错|正确|错误|√|×|T|F|true|false)\\s*$", Pattern.CASE_INSENSITIVE);
    /** 本地兜底：填空题下划线（___ 或 ____） */
    private static final Pattern FILL_BLANK_PATTERN = Pattern.compile("_{2,}");
    /** 本地兜底：填空题空括号（（ ）或 ( )） */
    private static final Pattern FILL_BLANK_PAREN_PATTERN = Pattern.compile("[（(]\\s*[）)]");
    /** 本地兜底：文本答案行（答案：xxx，非纯 A-D） */
    private static final Pattern TEXT_ANSWER_PATTERN = Pattern.compile("^\\s*(?:答案|参考答案|正确答案)\\s*[:：]?\\s*(.+)$");
    /** 本地兜底：简答题答案行（答：xxx） */
    private static final Pattern SHORT_ANSWER_LINE_PATTERN = Pattern.compile("^答\\s*[:：]?\\s*(.+)$");
    /** 本地兜底：章节标题行（一、填空题 / 二、简答题） */
    private static final Pattern CHAPTER_PATTERN = Pattern.compile("^\\s*[一二三四五六七八九十]+[、.．]\\s*(.+)$");
    /** 本地兜底：文档中红色加粗标记的答案（【答案：xxx】） */
    private static final Pattern ANSWER_MARK_PATTERN = Pattern.compile("【答案：([^】]+)】");

    @Resource
    private AiModelProperties aiProperties;
    @Resource
    private QuestionService questionService;

    @Override
    public AiQuestionParseRespVO parseDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_FILE_EMPTY);
        }
        String ext = ResourceConstants.getFileExt(file.getOriginalFilename());
        if (!AI_DOC_EXTS.contains(ext)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUESTION_AI_DOC_TYPE_INVALID);
        }
        if (file.getSize() > ResourceConstants.MAX_AI_DOC_SIZE) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUESTION_AI_DOC_SIZE_EXCEED);
        }

        AiQuestionParseRespVO resp = new AiQuestionParseRespVO();
        resp.setFileName(file.getOriginalFilename());
        resp.setFileType(ext);

        String text;
        try {
            text = extractText(file, ext);
        } catch (Exception e) {
            log.warn("[parseDocument][文档解析失败 fileName={}]", file.getOriginalFilename(), e);
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUESTION_AI_DOC_EMPTY);
        }
        if (StrUtil.isBlank(text)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUESTION_AI_DOC_EMPTY);
        }
        resp.setTextLength(text.length());

        List<AiQuestionItemVO> items = null;
        boolean aiEnabled = aiProperties.available();
        resp.setAiEnabled(aiEnabled);
        if (aiEnabled) {
            try {
                items = callAiModel(text);
            } catch (Exception e) {
                log.warn("[parseDocument][AI 提取失败，降级本地规则] {}", e.getMessage());
            }
        }
        boolean fallback = items == null || items.isEmpty();
        if (fallback) {
            items = parseByRule(text);
        }
        if (items == null || items.isEmpty()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUESTION_AI_RESULT_EMPTY);
        }
        resp.setItems(items);
        resp.setFallback(fallback);
        resp.setMessage(fallback
                ? (aiEnabled ? "AI 未识别到试题，已按本地规则解析出 " + items.size() + " 题，请核对后导入"
                             : "未配置 AI 大模型，已按本地规则解析出 " + items.size() + " 题，请核对后导入")
                : "AI 识别完成，共 " + items.size() + " 题，请核对后导入");
        return resp;
    }

    @Override
    public int importQuestions(AiQuestionImportReqVO reqVO) {
        List<AiQuestionItemVO> items = reqVO == null ? null : reqVO.getItems();
        if (items == null || items.isEmpty()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUESTION_AI_RESULT_EMPTY);
        }
        int success = 0;
        for (AiQuestionItemVO item : items) {
            if (item == null || StrUtil.isBlank(item.getStem())) {
                continue;
            }
            QuestionSaveReqVO saveReqVO = new QuestionSaveReqVO();
            saveReqVO.setQtype(item.getQtype() == null ? 1 : item.getQtype());
            saveReqVO.setStem(StrUtil.trim(item.getStem()));
            saveReqVO.setAnswer(StrUtil.trimToEmpty(item.getAnswer()));
            saveReqVO.setAnalysis(StrUtil.trimToEmpty(item.getAnalysis()));
            saveReqVO.setDifficulty(item.getDifficulty() == null ? 2 : item.getDifficulty());
            if (item.getOptions() != null && !item.getOptions().isEmpty()) {
                saveReqVO.setOptionsJson(JSONUtil.toJsonStr(item.getOptions()));
            }
            // 整批统一：所属分类与标签与「新增试题」保持同一套数据
            saveReqVO.setCategoryId(reqVO.getCategoryId());
            saveReqVO.setTagIds(reqVO.getTagIds());
            questionService.createQuestion(saveReqVO);
            success++;
        }
        return success;
    }

    // ========== 文档文本提取 ==========

    private String extractText(MultipartFile file, String ext) throws Exception {
        byte[] bytes = file.getBytes();
        if ("pdf".equals(ext)) {
            return extractPdfText(bytes);
        }
        // docx / wps（新版 WPS 多为 OOXML 结构）
        if ("docx".equals(ext) || "wps".equals(ext)) {
            try (InputStream in = new ByteArrayInputStream(bytes);
                 XWPFDocument doc = new XWPFDocument(in)) {
                return extractDocxTextWithColor(doc);
            } catch (Exception ignored) {
                // 老版 .wps 为 OLE2 结构，回退 HWPF 解析
                return extractDocText(bytes);
            }
        }
        return extractDocText(bytes);
    }

    /** 提取 docx 文本，红色加粗部分标记为答案（【答案：xxx】） */
    private String extractDocxTextWithColor(XWPFDocument doc) {
        StringBuilder sb = new StringBuilder();
        for (XWPFParagraph paragraph : doc.getParagraphs()) {
            StringBuilder paraSb = new StringBuilder();
            for (XWPFRun run : paragraph.getRuns()) {
                String text = run.text();
                if (text == null || text.isEmpty()) {
                    continue;
                }
                // 红色加粗 → 答案部分
                String color = run.getColor();
                boolean isAnswer = "C00000".equalsIgnoreCase(color) && run.isBold();
                if (isAnswer) {
                    paraSb.append("【答案：").append(text).append("】");
                } else {
                    paraSb.append(text);
                }
            }
            String paraText = paraSb.toString().trim();
            if (!paraText.isEmpty()) {
                sb.append(paraText).append("\n");
            }
        }
        return sb.toString();
    }

    private String extractDocText(byte[] bytes) throws Exception {
        try (InputStream in = new ByteArrayInputStream(bytes);
             WordExtractor extractor = new WordExtractor(in)) {
            return extractor.getText();
        }
    }

    private String extractPdfText(byte[] bytes) throws Exception {
        // 反射方式调用 PDFBox：未引入该依赖时给出明确错误，避免影响其它格式导入
        try {
            Class<?> pdDocumentClass = Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
            Object document = pdDocumentClass.getMethod("load", byte[].class).invoke(null, (Object) bytes);
            Class<?> stripperClass = Class.forName("org.apache.pdfbox.text.PDFTextStripper");
            Object stripper = stripperClass.getDeclaredConstructor().newInstance();
            String text = (String) stripperClass.getMethod("getText", pdDocumentClass).invoke(stripper, document);
            pdDocumentClass.getMethod("close").invoke(document);
            return text;
        } catch (ClassNotFoundException e) {
            log.warn("[extractPdfText][未引入 PDFBox 依赖，无法解析 PDF]");
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUESTION_AI_DOC_EMPTY);
        }
    }

    // ========== AI 大模型调用 ==========

    private List<AiQuestionItemVO> callAiModel(String text) {
        String content = text.length() > aiProperties.getMaxTextLength()
                ? text.substring(0, aiProperties.getMaxTextLength()) : text;
        String url = aiProperties.getBaseUrl() + aiProperties.getChatPath();
        Map<String, Object> body = new HashMap<>();
        body.put("model", aiProperties.getModel());
        body.put("temperature", 0.2);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", buildSystemPrompt()));
        messages.add(Map.of("role", "user", "content", "请从以下资料中提取试题：\n" + content));
        body.put("messages", messages);

        try (HttpResponse response = HttpRequest.post(url)
                .header("Authorization", "Bearer " + aiProperties.getApiKey())
                .header("Content-Type", "application/json")
                .timeout(aiProperties.getTimeoutMs())
                .body(JSONUtil.toJsonStr(body))
                .execute()) {
            if (!response.isOk()) {
                throw new IllegalStateException("AI 接口返回 " + response.getStatus());
            }
            String respBody = response.body();
            JSONObject json = JSONUtil.parseObj(respBody);
            JSONArray choices = json.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IllegalStateException("AI 返回内容为空");
            }
            String answer = choices.getJSONObject(0).getJSONObject("message").getStr("content");
            return parseAiResult(answer);
        } catch (Exception e) {
            log.warn("[callAiModel][调用失败] {}", e.getMessage());
            return null;
        }
    }

    private String buildSystemPrompt() {
        return "你是培训考试命题助手。请从用户提供的资料中提取试题，严格按以下 JSON 数组格式输出，不要输出任何解释性文字：\n"
                + "[{\"qtype\":1,\"stem\":\"题干\",\"options\":[{\"key\":\"A\",\"text\":\"...\",\"isCorrect\":true}],"
                + "\"answer\":\"A\",\"analysis\":\"解析\",\"difficulty\":2}]\n"
                + "规则：qtype 取值 1单选 2多选 3判断 4实操问答 5案例分析 6填空 7简答；选项使用 A/B/C/D；"
                + "判断题 qtype=3、answer 固定为「对」或「错」、options 留空数组；"
                + "填空题 qtype=6、answer 多个空用竖线|分隔、options 留空数组；"
                + "简答/实操问答/案例分析 qtype=4/5/7、answer 为参考答案文本、options 留空数组；"
                + "difficulty 取值 1易 2中 3难；最多提取 "
                + aiProperties.getMaxQuestions() + " 题。";
    }

    private List<AiQuestionItemVO> parseAiResult(String answer) {
        if (StrUtil.isBlank(answer)) {
            return null;
        }
        String json = answer.trim();
        // 去除 ```json ... ``` 包裹
        if (json.contains("```")) {
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
        }
        try {
            JSONArray array = JSONUtil.parseArray(json);
            if (array == null || array.isEmpty()) {
                return null;
            }
            List<AiQuestionItemVO> items = new ArrayList<>();
            int limit = aiProperties.getMaxQuestions() == null ? 30 : aiProperties.getMaxQuestions();
            for (int i = 0; i < array.size() && items.size() < limit; i++) {
                JSONObject obj = array.getJSONObject(i);
                if (obj == null || StrUtil.isBlank(obj.getStr("stem"))) {
                    continue;
                }
                AiQuestionItemVO item = JSONUtil.toBean(obj, AiQuestionItemVO.class);
                if (item.getQtype() == null) {
                    item.setQtype(1);
                }
                if (item.getDifficulty() == null) {
                    item.setDifficulty(2);
                }
                items.add(item);
            }
            return items;
        } catch (Exception e) {
            log.warn("[parseAiResult][解析 AI 结果失败] {}", e.getMessage());
            return null;
        }
    }

    // ========== 本地规则兜底解析 ==========

    private List<AiQuestionItemVO> parseByRule(String text) {
        List<AiQuestionItemVO> items = new ArrayList<>();
        if (StrUtil.isBlank(text)) {
            return items;
        }
        String[] lines = text.split("\\r?\\n");
        AiQuestionItemVO current = null;
        StringBuilder stemBuffer = new StringBuilder();
        StringBuilder answerBuffer = new StringBuilder(); // 收集多行答案（含表格形式）
        List<AiQuestionItemVO.OptionItem> options = new ArrayList<>();
        boolean hasTextAnswer = false;
        boolean hasJudgeAnswer = false;
        boolean answerStarted = false; // 是否已开始收集答案（遇到"答："后为true）
        int chapterQtype = 0; // 当前章节题型（0=未设置，按题干特征判断）
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher stemMatcher = STEM_PATTERN.matcher(line);
            Matcher optionMatcher = OPTION_PATTERN.matcher(line);
            Matcher answerMatcher = ANSWER_PATTERN.matcher(line);
            Matcher judgeAnswerMatcher = JUDGE_ANSWER_PATTERN.matcher(line);
            Matcher textAnswerMatcher = TEXT_ANSWER_PATTERN.matcher(line);
            Matcher shortAnswerMatcher = SHORT_ANSWER_LINE_PATTERN.matcher(line);
            Matcher chapterMatcher = CHAPTER_PATTERN.matcher(line);
            // 0. 章节标题行（一、填空题 / 二、简答题），优先识别
            if (chapterMatcher.matches()) {
                // 先将收集的答案设置到当前题目
                if (current != null && answerBuffer.length() > 0) {
                    current.setAnswer(answerBuffer.toString().trim());
                    hasTextAnswer = true;
                }
                // 先 flush 当前题目（使用旧的 chapterQtype），避免章节切换时上一题被误判
                flushQuestion(items, current, stemBuffer, options, hasTextAnswer, hasJudgeAnswer, chapterQtype);
                current = null;
                stemBuffer = new StringBuilder();
                answerBuffer = new StringBuilder();
                options = new ArrayList<>();
                hasTextAnswer = false;
                hasJudgeAnswer = false;
                answerStarted = false;
                // 再更新章节题型
                String title = chapterMatcher.group(1);
                if (title.contains("填空")) {
                    chapterQtype = 6;
                } else if (title.contains("案例")) {
                    chapterQtype = 5;
                } else if (title.contains("实操") || title.contains("问答")) {
                    chapterQtype = 4;
                } else if (title.contains("简答")) {
                    chapterQtype = 7;
                } else if (title.contains("多选")) {
                    chapterQtype = 2;
                } else if (title.contains("单选")) {
                    chapterQtype = 1;
                } else if (title.contains("判断")) {
                    chapterQtype = 3;
                } else {
                    chapterQtype = 0;
                }
                continue;
            }
            // 1. 选项答案行（答案：A / AB）
            if (answerMatcher.matches() && current != null) {
                current.setAnswer(answerMatcher.group(1));
                markCorrect(options, current.getAnswer());
                continue;
            }
            // 2. 判断题答案行（答案：对 / 错 / √ / ×）
            if (judgeAnswerMatcher.matches() && current != null && options.isEmpty()) {
                String ans = normalizeJudgeAnswer(judgeAnswerMatcher.group(1));
                current.setAnswer(ans);
                hasJudgeAnswer = true;
                continue;
            }
            // 3. 简答题答案行（答：xxx），支持答案为空后接多行/表格形式
            if (shortAnswerMatcher.matches() && current != null && options.isEmpty()) {
                String ans = shortAnswerMatcher.group(1).trim();
                answerStarted = true;
                if (StrUtil.isNotBlank(ans)) {
                    answerBuffer.append(ans);
                    current.setAnswer(ans);
                    hasTextAnswer = true;
                }
                continue;
            }
            // 4. 文本答案行（答案：xxx，非纯 A-D）
            if (textAnswerMatcher.matches() && current != null) {
                String ans = textAnswerMatcher.group(1).trim();
                if (StrUtil.isNotBlank(ans) && !ans.matches("^[A-D]{1,4}$")) {
                    current.setAnswer(ans);
                    hasTextAnswer = true;
                }
                continue;
            }
            // 5. 选项行（A. xxx）
            if (optionMatcher.matches()) {
                AiQuestionItemVO.OptionItem option = new AiQuestionItemVO.OptionItem();
                option.setKey(optionMatcher.group(1));
                option.setText(optionMatcher.group(2).trim());
                option.setIsCorrect(false);
                options.add(option);
                continue;
            }
            // 6. 题干行（1. xxx）
            if (stemMatcher.matches()) {
                // 先将收集的多行答案设置到当前题目
                if (current != null && answerBuffer.length() > 0) {
                    current.setAnswer(answerBuffer.toString().trim());
                    hasTextAnswer = true;
                }
                flushQuestion(items, current, stemBuffer, options, hasTextAnswer, hasJudgeAnswer, chapterQtype);
                current = new AiQuestionItemVO();
                current.setQtype(1);
                current.setDifficulty(2);
                stemBuffer = new StringBuilder(stemMatcher.group(2).trim());
                answerBuffer = new StringBuilder();
                options = new ArrayList<>();
                hasTextAnswer = false;
                hasJudgeAnswer = false;
                answerStarted = false;
                continue;
            }
            // 7. 续行：答案收集模式下追加到答案，否则追加到题干
            if (current != null && options.isEmpty() && !hasJudgeAnswer) {
                if (answerStarted) {
                    // 已开始收集答案（遇到过"答："），后续行追加到答案
                    if (answerBuffer.length() > 0) {
                        answerBuffer.append("\n");
                    }
                    answerBuffer.append(line);
                    hasTextAnswer = true;
                } else if (!hasTextAnswer) {
                    // 未开始收集答案且无文本答案，追加到题干
                    stemBuffer.append(line);
                }
            }
        }
        // 最后一题：将收集的多行答案设置到当前题目
        if (current != null && answerBuffer.length() > 0) {
            current.setAnswer(answerBuffer.toString().trim());
            hasTextAnswer = true;
        }
        flushQuestion(items, current, stemBuffer, options, hasTextAnswer, hasJudgeAnswer, chapterQtype);
        return items;
    }

    private void flushQuestion(List<AiQuestionItemVO> items, AiQuestionItemVO current,
                               StringBuilder stemBuffer, List<AiQuestionItemVO.OptionItem> options,
                               boolean hasTextAnswer, boolean hasJudgeAnswer, int chapterQtype) {
        if (current == null) {
            return;
        }
        String stem = stemBuffer.toString().trim();
        if (StrUtil.isBlank(stem)) {
            return;
        }
        // 处理文档中红色加粗标记的答案（【答案：xxx】）
        Matcher answerMarkMatcher = ANSWER_MARK_PATTERN.matcher(stem);
        List<String> markedAnswers = new ArrayList<>();
        while (answerMarkMatcher.find()) {
            markedAnswers.add(answerMarkMatcher.group(1).trim());
        }
        if (!markedAnswers.isEmpty()) {
            // 将题干中的【答案：xxx】替换为下划线
            stem = ANSWER_MARK_PATTERN.matcher(stem).replaceAll("____");
            // 如果当前答案为空，用标记的答案填充（多个空用|分隔）
            if (StrUtil.isBlank(current.getAnswer())) {
                current.setAnswer(String.join("|", markedAnswers));
            }
        }
        current.setStem(stem);
        if (!options.isEmpty()) {
            // 有选项 → 单选或多选（优先级最高，不受章节影响）
            current.setOptions(options);
            if (StrUtil.isBlank(current.getAnswer())) {
                current.setAnswer(options.get(0).getKey());
                markCorrect(options, current.getAnswer());
            }
            // 答案含多个字母 → 多选题
            if (current.getAnswer() != null && current.getAnswer().length() > 1
                    && current.getAnswer().matches("^[A-D]{2,4}$")) {
                current.setQtype(2);
            } else {
                current.setQtype(1);
            }
        } else if (chapterQtype > 0) {
            // 有章节题型 → 优先按章节判断（支持无填空标记的填空题、无答案前缀的简答题）
            current.setQtype(chapterQtype);
            if (chapterQtype == 6 || chapterQtype == 7) {
                current.setAnswer(cleanTextAnswer(current.getAnswer()));
            } else if (chapterQtype == 3) {
                current.setAnswer(normalizeJudgeAnswer(current.getAnswer()));
            }
        } else if (hasJudgeAnswer) {
            // 判断题答案 → 判断题
            current.setQtype(3);
            current.setAnswer(normalizeJudgeAnswer(current.getAnswer()));
        } else if (FILL_BLANK_PATTERN.matcher(stem).find() || FILL_BLANK_PAREN_PATTERN.matcher(stem).find()) {
            // 题干含下划线或空括号 → 填空题
            current.setQtype(6);
            current.setAnswer(cleanTextAnswer(current.getAnswer()));
        } else if (hasTextAnswer || StrUtil.isNotBlank(current.getAnswer())) {
            // 有文本答案 → 简答题
            current.setQtype(7);
            current.setAnswer(cleanTextAnswer(current.getAnswer()));
        } else {
            // 无选项无答案，默认单选（交由人工核对）
            current.setQtype(1);
        }
        // 填空题答案为空时，用题干作为答案（兼容完整陈述句形式的填空题）
        if (current.getQtype() != null && current.getQtype() == 6 && StrUtil.isBlank(current.getAnswer())) {
            current.setAnswer(stem);
        }
        items.add(current);
    }

    /** 归一化判断题答案：统一为「对」或「错」 */
    private String normalizeJudgeAnswer(String answer) {
        if (StrUtil.isBlank(answer)) {
            return "对";
        }
        String a = answer.trim();
        if ("对".equals(a) || "正确".equals(a) || "√".equals(a) || "T".equalsIgnoreCase(a) || "true".equalsIgnoreCase(a)) {
            return "对";
        }
        return "错";
    }

    /** 清理文本答案：去掉「答案：」「案：」「答：」等前缀（兼容docx解析时拆行的情况） */
    private String cleanTextAnswer(String answer) {
        if (StrUtil.isBlank(answer)) {
            return answer;
        }
        String result = answer.trim();
        // 按长度从长到短依次去掉前缀
        result = result.replaceAll("^(参考答案|正确答案)\\s*[:：]?\\s*", "");
        result = result.replaceAll("^答案\\s*[:：]?\\s*", "");
        result = result.replaceAll("^案\\s*[:：]\\s*", "");
        result = result.replaceAll("^答\\s*[:：]\\s*", "");
        return result.trim();
    }

    private void markCorrect(List<AiQuestionItemVO.OptionItem> options, String answer) {
        if (options == null || StrUtil.isBlank(answer)) {
            return;
        }
        for (AiQuestionItemVO.OptionItem option : options) {
            option.setIsCorrect(answer.contains(option.getKey()));
        }
    }

}

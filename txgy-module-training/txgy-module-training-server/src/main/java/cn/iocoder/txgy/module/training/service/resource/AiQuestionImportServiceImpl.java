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
    /** 本地兜底：答案行 */
    private static final Pattern ANSWER_PATTERN = Pattern.compile("(?:答案|参考答案|正确答案)\\s*[:：]?\\s*([A-D]{1,4})");

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
                try (XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
                    return extractor.getText();
                }
            } catch (Exception ignored) {
                // 老版 .wps 为 OLE2 结构，回退 HWPF 解析
                return extractDocText(bytes);
            }
        }
        return extractDocText(bytes);
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
        List<AiQuestionItemVO.OptionItem> options = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher stemMatcher = STEM_PATTERN.matcher(line);
            Matcher optionMatcher = OPTION_PATTERN.matcher(line);
            Matcher answerMatcher = ANSWER_PATTERN.matcher(line);
            if (answerMatcher.find() && current != null) {
                current.setAnswer(answerMatcher.group(1));
                markCorrect(options, current.getAnswer());
                continue;
            }
            if (optionMatcher.matches()) {
                AiQuestionItemVO.OptionItem option = new AiQuestionItemVO.OptionItem();
                option.setKey(optionMatcher.group(1));
                option.setText(optionMatcher.group(2).trim());
                option.setIsCorrect(false);
                options.add(option);
                continue;
            }
            if (stemMatcher.matches()) {
                // 结算上一题
                flushQuestion(items, current, stemBuffer, options);
                current = new AiQuestionItemVO();
                current.setQtype(1);
                current.setDifficulty(2);
                stemBuffer = new StringBuilder(stemMatcher.group(2).trim());
                options = new ArrayList<>();
                continue;
            }
            // 题干续行
            if (current != null && options.isEmpty()) {
                stemBuffer.append(line);
            }
        }
        flushQuestion(items, current, stemBuffer, options);
        return items;
    }

    private void flushQuestion(List<AiQuestionItemVO> items, AiQuestionItemVO current,
                               StringBuilder stemBuffer, List<AiQuestionItemVO.OptionItem> options) {
        if (current == null) {
            return;
        }
        String stem = stemBuffer.toString().trim();
        if (StrUtil.isBlank(stem)) {
            return;
        }
        current.setStem(stem);
        if (!options.isEmpty()) {
            current.setOptions(options);
            // 未识别到答案时默认第一个选项，交由人工核对
            if (StrUtil.isBlank(current.getAnswer())) {
                current.setAnswer(options.get(0).getKey());
                markCorrect(options, current.getAnswer());
            }
        }
        items.add(current);
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

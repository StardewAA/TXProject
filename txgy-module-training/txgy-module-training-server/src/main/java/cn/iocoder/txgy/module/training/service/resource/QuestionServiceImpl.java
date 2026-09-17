package cn.iocoder.txgy.module.training.service.resource;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.QuestionImportRespVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.QuestionPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.QuestionRespVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.QuestionSaveReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.QuestionDO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.QuestionTagDO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.ResourceTagDO;
import cn.iocoder.txgy.module.training.dal.mysql.resource.QuestionMapper;
import cn.iocoder.txgy.module.training.dal.mysql.resource.QuestionTagMapper;
import cn.iocoder.txgy.module.training.dal.mysql.resource.ResourceReferenceMapper;
import cn.iocoder.txgy.module.training.dal.mysql.resource.ResourceTagMapper;
import cn.iocoder.txgy.module.training.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.training.enums.resource.QuestionDifficultyEnum;
import cn.iocoder.txgy.module.training.enums.resource.QuestionTypeEnum;
import cn.iocoder.txgy.module.training.enums.resource.ResourceConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 试题 Service 实现
 *
 * <p>题型：1 单选 2 多选 3 判断 4 实操问答 5 案例分析；
 * 标签：经 training_question_tag 关联，题库页按标签筛选。</p>
 *
 * @author 云培训学院课题小组
 */
@Slf4j
@Service
public class QuestionServiceImpl implements QuestionService {

    /** 导入模板列定义（与 downloadImportTemplate 保持一致） */
    private static final String[] TEMPLATE_HEADERS =
            {"题型", "题干", "选项A", "选项B", "选项C", "选项D", "正确答案", "解析", "难度", "标签"};

    /** 失败原因回传前端的最大长度（避免 MyBatis 大段 SQL 上下文撑爆提示） */
    private static final int ERROR_MESSAGE_MAX = 200;

    @Resource
    private QuestionMapper questionMapper;
    @Resource
    private QuestionTagMapper questionTagMapper;
    @Resource
    private ResourceTagMapper tagMapper;
    @Resource
    private ResourceReferenceMapper referenceMapper;
    @Resource
    private ResourceCategoryService categoryService;
    @Resource
    private OperationLogService operationLogService;
    @Resource
    private PlatformTransactionManager transactionManager;

    // ========== 查询 ==========

    @Override
    public PageResult<QuestionRespVO> getQuestionPage(QuestionPageReqVO reqVO) {
        // 1. 标签过滤：按标签关联的试题 ID 过滤
        if (reqVO.getTagId() != null) {
            List<QuestionTagDO> questionTags = questionTagMapper.selectListByTagId(reqVO.getTagId());
            if (CollUtil.isEmpty(questionTags)) {
                return PageResult.empty();
            }
            reqVO.setQuestionIds(questionTags.stream().map(QuestionTagDO::getQuestionId).collect(Collectors.toList()));
        }
        // 2. 分页查询
        PageResult<QuestionDO> page = questionMapper.selectPage(reqVO);
        if (CollUtil.isEmpty(page.getList())) {
            return PageResult.empty(page.getTotal());
        }
        // 3. 组装标签信息
        return new PageResult<>(convertResp(page.getList()), page.getTotal());
    }

    @Override
    public QuestionRespVO getQuestion(Long id) {
        QuestionDO question = questionMapper.selectById(id);
        if (question == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUESTION_NOT_EXISTS);
        }
        return convertResp(Collections.singletonList(question)).get(0);
    }

    // ========== 新增/修改/状态/删除 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createQuestion(QuestionSaveReqVO reqVO) {
        QuestionTypeEnum type = checkType(reqVO.getQtype());
        checkDifficulty(reqVO.getDifficulty());
        checkChoicePayload(type, reqVO);
        // 分类校验（question 类型，支持三级分类）
        categoryService.validateCategory(reqVO.getCategoryId(), ResourceConstants.TYPE_QUESTION);
        // 1. 试题
        QuestionDO question = new QuestionDO();
        question.setQtype(reqVO.getQtype());
        question.setStem(StrUtil.trim(reqVO.getStem()));
        question.setOptionsJson(StrUtil.nullToEmpty(reqVO.getOptionsJson()));
        question.setAnswer(StrUtil.trim(reqVO.getAnswer()));
        question.setAnalysis(StrUtil.trim(reqVO.getAnalysis()));
        question.setDifficulty(reqVO.getDifficulty());
        question.setCategoryId(reqVO.getCategoryId());
        question.setStatus(1); // 默认启用
        question.setCreatorId(SecurityFrameworkUtils.getLoginUserId());
        question.setCreatorName(SecurityFrameworkUtils.getLoginUserNickname());
        questionMapper.insert(question);
        // 2. 标签
        bindTags(question.getId(), reqVO.getTagIds());
        operationLogService.createLog(ResourceConstants.ACTION_CREATE, ResourceConstants.TARGET_QUESTION,
                question.getId(), "新增试题《" + shortText(question.getStem(), 30) + "》");
        return question.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuestion(QuestionSaveReqVO reqVO) {
        QuestionDO old = validateExists(reqVO.getId());
        QuestionTypeEnum type = checkType(reqVO.getQtype());
        checkDifficulty(reqVO.getDifficulty());
        checkChoicePayload(type, reqVO);
        // 分类校验（question 类型，支持三级分类）
        categoryService.validateCategory(reqVO.getCategoryId(), ResourceConstants.TYPE_QUESTION);
        QuestionDO update = new QuestionDO();
        update.setId(reqVO.getId());
        update.setQtype(reqVO.getQtype());
        update.setStem(StrUtil.trim(reqVO.getStem()));
        update.setOptionsJson(StrUtil.nullToEmpty(reqVO.getOptionsJson()));
        update.setAnswer(StrUtil.trim(reqVO.getAnswer()));
        update.setAnalysis(StrUtil.trim(reqVO.getAnalysis()));
        update.setDifficulty(reqVO.getDifficulty());
        update.setCategoryId(reqVO.getCategoryId());
        questionMapper.updateById(update);
        // 重新绑定标签：先逻辑删除旧的，再插入新的
        questionTagMapper.deleteByQuestionId(reqVO.getId());
        bindTags(reqVO.getId(), reqVO.getTagIds());
        operationLogService.createLog(ResourceConstants.ACTION_UPDATE, ResourceConstants.TARGET_QUESTION,
                reqVO.getId(), "更新试题《" + shortText(old.getStem(), 30) + "》");
    }

    @Override
    public void changeQuestionStatus(Long id, String action) {
        QuestionDO question = validateExists(id);
        int newStatus;
        String logAction;
        if (ResourceConstants.ACTION_ENABLE.equals(action)) {
            newStatus = 1;
            logAction = ResourceConstants.ACTION_ENABLE;
        } else if (ResourceConstants.ACTION_DISABLE.equals(action)) {
            newStatus = 2;
            logAction = ResourceConstants.ACTION_DISABLE;
        } else {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUESTION_STATUS_CHANGE_INVALID);
        }
        if (Objects.equals(question.getStatus(), newStatus)) {
            return;
        }
        QuestionDO update = new QuestionDO();
        update.setId(id);
        update.setStatus(newStatus);
        questionMapper.updateById(update);
        operationLogService.createLog(logAction, ResourceConstants.TARGET_QUESTION, id,
                (newStatus == 1 ? "启用" : "停用") + "试题《" + shortText(question.getStem(), 30) + "》");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteQuestion(Long id) {
        QuestionDO question = validateExists(id);
        Long refCount = referenceMapper.selectCountByResource(ResourceConstants.TARGET_QUESTION, id);
        if (refCount != null && refCount > 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUESTION_REFERENCED_DELETE_FORBIDDEN);
        }
        // 逻辑删除试题及其标签关联（保留引用记录，便于审计恢复）
        questionTagMapper.deleteByQuestionId(id);
        questionMapper.deleteById(id);
        operationLogService.createLog(ResourceConstants.ACTION_DELETE, ResourceConstants.TARGET_QUESTION,
                id, "删除试题《" + shortText(question.getStem(), 30) + "》");
    }

    // ========== Excel 模板与批量导入 ==========

    @Override
    public byte[] downloadImportTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("试题导入模板");
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            // 表头
            Row header = sheet.createRow(0);
            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(TEMPLATE_HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 22 * 256);
            }
            sheet.setColumnWidth(1, 60 * 256);
            // 示例行
            String[][] samples = {
                    {"单选", "高处作业时安全带应做到（　）？", "高挂低用", "低挂高用", "随意悬挂", "捆绑在吊物上", "A", "安全规范要求高挂低用。", "易", "高处作业"},
                    {"多选", "动火作业前必须确认（　）。", "作业票已审批", "配备灭火器材", "监护人在场", "天气情况", "A,B,C", "动火作业安全管理要求。", "中", "动火作业"},
                    {"判断", "进入受限空间作业前必须进行气体检测。", "", "", "", "", "对", "先通风、再检测、后作业。", "易", "受限空间"},
                    {"填空", "防腐涂装表面处理常用的除锈等级有____和____两级。", "", "", "", "", "Sa2.5|Sa3", "喷射除锈常用等级，按顺序填写用竖线分隔。", "中", "防腐工艺"},
                    {"简答", "简述高压无气喷涂的操作要点。", "", "", "", "", "调整压力、保持枪距、匀速移动、注意重叠率。", "按作业指导书评分要点给分。", "难", "喷涂工艺"},
                    {"实操问答", "简述防腐涂装作业前的表面处理流程。", "", "", "", "", "喷射除锈至 Sa2.5 级并除尘除潮", "按作业指导书评分。", "难", "防腐工艺"},
                    {"案例分析", "某储罐区检修时发生火灾，请分析可能原因。", "", "", "", "", "未办理动火票、未清理可燃物、监护缺失", "涉及动火审批、可燃物隔离、监护责任。", "难", "动火作业"}
            };
            for (int r = 0; r < samples.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < samples[r].length; c++) {
                    row.createCell(c).setCellValue(samples[r][c]);
                }
            }
            // 填写说明
            int noteRow = samples.length + 2;
            Row note = sheet.createRow(noteRow);
            Cell noteCell = note.createCell(0);
            noteCell.setCellValue("填写说明：题型仅支持【单选/多选/判断/实操问答/案例分析/填空/简答】；难度仅支持【易/中/难】；"
                    + "判断题答案填【对】或【错】；单选/多选答案填选项字母（多选用英文逗号分隔，如 A,B,C）；"
                    + "填空题多个空的答案用竖线 | 按顺序分隔（如 答案1|答案2）；简答/实操问答/案例分析填参考答案文本；"
                    + "标签用英文或中文逗号分隔，不存在会自动创建。导入时请删除示例行。");
            sheet.addMergedRegion(new CellRangeAddress(noteRow, noteRow, 0, 9));
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("[downloadImportTemplate][生成模板失败]", e);
            throw new IllegalStateException("生成导入模板失败", e);
        }
    }

    @Override
    public QuestionImportRespVO importQuestions(MultipartFile file) {
        QuestionImportRespVO result = new QuestionImportRespVO();
        if (file == null || file.isEmpty()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUESTION_IMPORT_EMPTY);
        }
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            DataFormatter formatter = new DataFormatter();
            // 先定位真正的数据表：非模板文件直接给出明确提示，避免被解析成一堆无意义的行错误
            Sheet sheet = locateDataSheet(workbook, formatter);
            if (sheet == null) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUESTION_IMPORT_HEADER_INVALID);
            }
            int success = 0;
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                String stem = cellStr(row, 1, formatter).trim();
                if (StrUtil.isBlank(stem)) {
                    continue; // 空行跳过
                }
                try {
                    // 每行独立事务：失败行整体回滚，避免出现「试题已入库但标签未绑定」的半截数据
                    rowTransactionTemplate().executeWithoutResult(status -> {
                        QuestionDO question = parseRow(row, formatter);
                        questionMapper.insert(question);
                        bindTagsByName(question.getId(), parseTags(cellStr(row, 9, formatter)));
                    });
                    success++;
                } catch (Exception e) {
                    // 单行失败不影响后续行
                    log.warn("[importQuestions][第 {} 行导入失败: {}]", rowIndex + 1, e.getMessage());
                    result.getErrors().add(new QuestionImportRespVO.ErrorItem(rowIndex + 1, readableError(e)));
                }
            }
            if (success == 0 && CollUtil.isEmpty(result.getErrors())) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUESTION_IMPORT_EMPTY);
            }
            result.setSuccessCount(success);
            if (success > 0) {
                operationLogService.createLog(ResourceConstants.ACTION_IMPORT, ResourceConstants.TARGET_QUESTION,
                        null, "Excel 批量导入试题，成功 " + success + " 条，失败 " + result.getErrors().size() + " 条");
            }
            return result;
        } catch (cn.iocoder.txgy.framework.common.exception.ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("[importQuestions][解析 Excel 失败]", e);
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUESTION_IMPORT_PARSE_ERROR);
        }
    }

    /**
     * 单行导入事务模板：REQUIRES_NEW 保证每行独立提交，
     * 既不影响已成功的行，也不会在失败时留下半截数据
     */
    private TransactionTemplate rowTransactionTemplate() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    /**
     * 定位数据表：取首个「表头含 题型 + 题干」的工作表，
     * 兼容封面页/说明页在前、以及数据不在第一个 Sheet 的文件
     */
    private Sheet locateDataSheet(Workbook workbook, DataFormatter formatter) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (sheet != null && isTemplateHeader(sheet.getRow(sheet.getFirstRowNum()), formatter)) {
                return sheet;
            }
        }
        return null;
    }

    private boolean isTemplateHeader(Row row, DataFormatter formatter) {
        if (row == null) {
            return false;
        }
        return cellStr(row, 0, formatter).contains("题型") && cellStr(row, 1, formatter).contains("题干");
    }

    /**
     * 失败原因可读化：业务校验异常原样返回；SQL/框架异常只取最内层原因，
     * 避免把 MyBatis 的整段 SQL 堆栈回传给前端
     */
    private String readableError(Exception e) {
        if (e instanceof IllegalArgumentException) {
            return e.getMessage();
        }
        String raw = e.getMessage();
        int causeAt = raw == null ? -1 : raw.lastIndexOf("### Cause:");
        if (causeAt >= 0) {
            raw = raw.substring(causeAt + "### Cause:".length());
        }
        String message = "该行写入失败，请检查该行内容后重试";
        if (StrUtil.isNotBlank(raw)) {
            for (String line : raw.split("\n")) {
                String text = line.trim();
                if (StrUtil.isNotBlank(text) && !text.startsWith("###")) {
                    message = text;
                    break;
                }
            }
        }
        return message.length() <= ERROR_MESSAGE_MAX ? message : message.substring(0, ERROR_MESSAGE_MAX) + "…";
    }

    // ========== 私有方法 ==========

    private QuestionDO parseRow(Row row, DataFormatter formatter) {
        QuestionTypeEnum type = parseType(cellStr(row, 0, formatter));
        String stem = cellStr(row, 1, formatter).trim();
        if (StrUtil.isBlank(stem)) {
            throw new IllegalArgumentException("题干不能为空");
        }
        String optionA = cellStr(row, 2, formatter).trim();
        String optionB = cellStr(row, 3, formatter).trim();
        String optionC = cellStr(row, 4, formatter).trim();
        String optionD = cellStr(row, 5, formatter).trim();
        String answer = cellStr(row, 6, formatter).trim();
        String analysis = cellStr(row, 7, formatter).trim();
        String difficultyText = cellStr(row, 8, formatter).trim();
        // 1. 难度
        Integer difficulty = parseDifficulty(difficultyText);
        // 2. 题型分支校验并组装选项 JSON
        QuestionDO question = new QuestionDO();
        question.setQtype(type.getType());
        question.setStem(stem);
        question.setAnalysis(analysis);
        question.setDifficulty(difficulty);
        question.setStatus(1);
        question.setCreatorId(SecurityFrameworkUtils.getLoginUserId());
        question.setCreatorName(SecurityFrameworkUtils.getLoginUserNickname());
        switch (type) {
            case SINGLE, MULTIPLE -> {
                List<String> keys = new ArrayList<>();
                List<String> texts = new ArrayList<>();
                String[][] pairs = {{"A", optionA}, {"B", optionB}, {"C", optionC}, {"D", optionD}};
                for (String[] pair : pairs) {
                    if (StrUtil.isNotBlank(pair[1])) {
                        keys.add(pair[0]);
                        texts.add(pair[1]);
                    }
                }
                if (keys.size() < 2) {
                    throw new IllegalArgumentException("选择题至少需填写两个选项");
                }
                if (StrUtil.isBlank(answer)) {
                    throw new IllegalArgumentException("选择题答案不能为空");
                }
                Set<String> correctKeys = Arrays.stream(answer.toUpperCase().split("[,，、\\s]+"))
                        .filter(StrUtil::isNotBlank).collect(Collectors.toSet());
                if (correctKeys.isEmpty() || !keys.containsAll(correctKeys)) {
                    throw new IllegalArgumentException("答案须为已有选项字母，如 " + String.join("/", keys));
                }
                if (type == QuestionTypeEnum.SINGLE && correctKeys.size() != 1) {
                    throw new IllegalArgumentException("单选题答案只能填写一个选项字母");
                }
                if (type == QuestionTypeEnum.MULTIPLE && correctKeys.size() < 2) {
                    throw new IllegalArgumentException("多选题答案至少填写两个选项字母");
                }
                List<Map<String, Object>> options = new ArrayList<>();
                for (int i = 0; i < keys.size(); i++) {
                    Map<String, Object> option = new LinkedHashMap<>();
                    option.put("key", keys.get(i));
                    option.put("text", texts.get(i));
                    option.put("isCorrect", correctKeys.contains(keys.get(i)));
                    options.add(option);
                }
                question.setOptionsJson(cn.hutool.json.JSONUtil.toJsonStr(options));
                question.setAnswer(String.join(",", correctKeys.stream().sorted().toList()));
            }
            case JUDGE -> {
                if (StrUtil.isBlank(answer)) {
                    throw new IllegalArgumentException("判断题答案不能为空");
                }
                String normalized = normalizeJudge(answer);
                if (normalized == null) {
                    throw new IllegalArgumentException("判断题答案仅支持：对/错");
                }
                question.setAnswer(normalized);
            }
            default -> { // QA / CASE / FILL_BLANK / SHORT_ANSWER
                if (StrUtil.isBlank(answer)) {
                    throw new IllegalArgumentException("问答/案例/填空/简答题答案不能为空");
                }
                question.setAnswer(answer);
            }
        }
        return question;
    }

    private QuestionTypeEnum parseType(String text) {
        QuestionTypeEnum byCode = QuestionTypeEnum.fromCode(text.trim().toLowerCase());
        if (byCode != null) {
            return byCode;
        }
        return switch (text.trim()) {
            case "单选" -> QuestionTypeEnum.SINGLE;
            case "多选" -> QuestionTypeEnum.MULTIPLE;
            case "判断" -> QuestionTypeEnum.JUDGE;
            case "实操问答" -> QuestionTypeEnum.QA;
            case "案例分析" -> QuestionTypeEnum.CASE;
            case "填空", "填空题" -> QuestionTypeEnum.FILL_BLANK;
            case "简答", "简答题", "问答" -> QuestionTypeEnum.SHORT_ANSWER;
            default -> throw new IllegalArgumentException("题型不合法：" + (StrUtil.isBlank(text) ? "空" : text)
                    + "（仅支持 单选/多选/判断/实操问答/案例分析/填空/简答）");
        };
    }

    private Integer parseDifficulty(String text) {
        return switch (text.trim()) {
            case "易", "1" -> QuestionDifficultyEnum.EASY.getDifficulty();
            case "中", "2" -> QuestionDifficultyEnum.MEDIUM.getDifficulty();
            case "难", "3" -> QuestionDifficultyEnum.HARD.getDifficulty();
            default -> throw new IllegalArgumentException("难度不合法：" + (StrUtil.isBlank(text) ? "空" : text)
                    + "（仅支持 易/中/难）");
        };
    }

    private String normalizeJudge(String answer) {
        String a = answer.trim();
        if ("对".equals(a) || "√".equals(a) || "正确".equals(a) || "true".equalsIgnoreCase(a)) {
            return "对";
        }
        if ("错".equals(a) || "×".equals(a) || "错误".equals(a) || "false".equalsIgnoreCase(a)) {
            return "错";
        }
        return null;
    }

    private List<String> parseTags(String tagText) {
        if (StrUtil.isBlank(tagText)) {
            return new ArrayList<>();
        }
        return Arrays.stream(tagText.split("[,，]"))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 按标签名查找或创建（默认 course 课程维度），并绑定到试题
     */
    private void bindTagsByName(Long questionId, List<String> tagNames) {
        if (CollUtil.isEmpty(tagNames)) {
            return;
        }
        List<Long> tagIds = new ArrayList<>();
        for (String name : tagNames) {
            ResourceTagDO tag = tagMapper.selectByNameAndDimension(name, ResourceConstants.DIM_COURSE);
            if (tag == null) {
                tag = new ResourceTagDO();
                tag.setName(name);
                tag.setDimension(ResourceConstants.DIM_COURSE);
                tag.setSort(0);
                tagMapper.insert(tag);
            }
            tagIds.add(tag.getId());
        }
        bindTags(questionId, tagIds);
    }

    private void bindTags(Long questionId, List<Long> tagIds) {
        if (CollUtil.isEmpty(tagIds)) {
            return;
        }
        // 标签必须存在（非法 ID 直接报错）
        for (Long tagId : tagIds.stream().filter(Objects::nonNull).distinct().toList()) {
            if (tagMapper.selectById(tagId) == null) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_TAG_NOT_EXISTS);
            }
            QuestionTagDO relation = new QuestionTagDO();
            relation.setQuestionId(questionId);
            relation.setTagId(tagId);
            questionTagMapper.insert(relation);
        }
    }

    private List<QuestionRespVO> convertResp(List<QuestionDO> questions) {
        List<Long> questionIds = questions.stream().map(QuestionDO::getId).toList();
        Map<Long, List<QuestionTagDO>> relationMap = questionTagMapper.selectListByQuestionIds(questionIds)
                .stream().collect(Collectors.groupingBy(QuestionTagDO::getQuestionId));
        Set<Long> tagIdSet = relationMap.values().stream()
                .flatMap(Collection::stream).map(QuestionTagDO::getTagId).collect(Collectors.toSet());
        Map<Long, ResourceTagDO> tagMap = tagIdSet.isEmpty() ? Collections.emptyMap()
                : tagMapper.selectBatchIds(tagIdSet).stream()
                .collect(Collectors.toMap(ResourceTagDO::getId, x -> x));
        List<QuestionRespVO> result = new ArrayList<>();
        for (QuestionDO question : questions) {
            QuestionRespVO respVO = BeanUtil.copyProperties(question, QuestionRespVO.class);
            List<Long> tagIds = relationMap.getOrDefault(question.getId(), Collections.emptyList())
                    .stream().map(QuestionTagDO::getTagId).toList();
            respVO.setTagIds(new ArrayList<>(tagIds));
            respVO.setTagNames(tagIds.stream().map(id -> {
                ResourceTagDO tag = tagMap.get(id);
                return tag == null ? "" : tag.getName();
            }).filter(StrUtil::isNotBlank).collect(Collectors.joining(",")));
            result.add(respVO);
        }
        return result;
    }

    private QuestionTypeEnum checkType(Integer qtype) {
        QuestionTypeEnum type = QuestionTypeEnum.fromType(qtype);
        if (type == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUESTION_TYPE_INVALID);
        }
        return type;
    }

    private void checkDifficulty(Integer difficulty) {
        if (difficulty == null
                || (difficulty != QuestionDifficultyEnum.EASY.getDifficulty()
                && difficulty != QuestionDifficultyEnum.MEDIUM.getDifficulty()
                && difficulty != QuestionDifficultyEnum.HARD.getDifficulty())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUESTION_DIFFICULTY_INVALID);
        }
    }

    /**
     * 非选择类题型不允许携带选项；选项 JSON 为可选增强字段，单多选由创建方负责（与 Excel 导入兼容）
     */
    private void checkChoicePayload(QuestionTypeEnum type, QuestionSaveReqVO reqVO) {
        boolean isChoice = type == QuestionTypeEnum.SINGLE || type == QuestionTypeEnum.MULTIPLE;
        if (!isChoice && StrUtil.isNotBlank(reqVO.getOptionsJson())) {
            // 宽容处理：容忍前端误传，仅提示（不强制）
            log.warn("[checkChoicePayload][非选择题型 qtype({}) 携带了选项，忽略]", type.getType());
        }
    }

    private QuestionDO validateExists(Long id) {
        QuestionDO question = questionMapper.selectById(id);
        if (question == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUESTION_NOT_EXISTS);
        }
        return question;
    }

    private String cellStr(Row row, int index, DataFormatter formatter) {
        Cell cell = row.getCell(index);
        return cell == null ? "" : formatter.formatCellValue(cell);
    }

    private String shortText(String text, int max) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

}

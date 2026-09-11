package cn.iocoder.txgy.module.training.controller.admin.resource;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.AiQuestionItemVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.AiQuestionParseRespVO;
import cn.iocoder.txgy.module.training.service.resource.AiQuestionImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 考试题库 AI 智能导入
 *
 * <p>支持上传 PDF / Word(doc,docx) / WPS(wps) 文档，调用 AI 大模型自动提取试题，
 * 预览确认后批量入库；AI 未配置时自动降级为本地规则解析。</p>
 *
 * @author 云培训学院课题小组
 */
@Tag(name = "管理后台 - 考试题库 AI 智能导入")
@RestController
@RequestMapping("/training/question/ai")
@Validated
public class QuestionAiController {

    @Resource
    private AiQuestionImportService aiQuestionImportService;

    @PostMapping("/parse")
    @Operation(summary = "上传文档并解析试题（PDF/Word/WPS，仅预览不入库）")
    @PreAuthorize("@ss.hasPermission('training:resource-question:create')")
    public CommonResult<AiQuestionParseRespVO> parseDocument(@RequestParam("file") MultipartFile file) {
        return success(aiQuestionImportService.parseDocument(file));
    }

    @PostMapping("/import")
    @Operation(summary = "确认导入：将解析后的试题批量入库")
    @PreAuthorize("@ss.hasPermission('training:resource-question:create')")
    public CommonResult<Integer> importQuestions(@RequestBody List<AiQuestionItemVO> items) {
        return success(aiQuestionImportService.importQuestions(items));
    }

}

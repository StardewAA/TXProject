package cn.iocoder.txgy.module.training.controller.admin.resource;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.QuestionImportRespVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.QuestionPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.QuestionRespVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.QuestionSaveReqVO;
import cn.iocoder.txgy.module.training.service.resource.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 考试题库")
@RestController
@RequestMapping("/training/question")
@Validated
public class QuestionController {

    @Resource
    private QuestionService questionService;

    @GetMapping("/page")
    @Operation(summary = "试题分页（按题型/难度/状态/标签筛选）")
    @PreAuthorize("@ss.hasPermission('training:question:query')")
    public CommonResult<PageResult<QuestionRespVO>> getQuestionPage(@Validated QuestionPageReqVO pageReqVO) {
        return success(questionService.getQuestionPage(pageReqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "试题详情")
    @Parameter(name = "id", description = "试题 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:question:query')")
    public CommonResult<QuestionRespVO> getQuestion(@RequestParam("id") Long id) {
        return success(questionService.getQuestion(id));
    }

    @PostMapping("/create")
    @Operation(summary = "新增试题")
    @PreAuthorize("@ss.hasPermission('training:question:create')")
    public CommonResult<Long> createQuestion(@Valid @RequestBody QuestionSaveReqVO reqVO) {
        return success(questionService.createQuestion(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新试题")
    @PreAuthorize("@ss.hasPermission('training:question:update')")
    public CommonResult<Boolean> updateQuestion(@Valid @RequestBody QuestionSaveReqVO reqVO) {
        questionService.updateQuestion(reqVO);
        return success(true);
    }

    @PostMapping("/status")
    @Operation(summary = "试题启用/停用（ENABLE / DISABLE）")
    @Parameter(name = "action", description = "动作：ENABLE/DISABLE", required = true, example = "DISABLE")
    @PreAuthorize("@ss.hasPermission('training:question:update')")
    public CommonResult<Boolean> changeQuestionStatus(@RequestParam("id") Long id,
                                                      @RequestParam("action") String action) {
        questionService.changeQuestionStatus(id, action);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除试题（被试卷引用时禁止删除）")
    @Parameter(name = "id", description = "试题 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:question:delete')")
    public CommonResult<Boolean> deleteQuestion(@RequestParam("id") Long id) {
        questionService.deleteQuestion(id);
        return success(true);
    }

    @GetMapping("/import-template")
    @Operation(summary = "下载试题导入 Excel 模板")
    @PreAuthorize("@ss.hasPermission('training:question:query')")
    public void downloadImportTemplate(HttpServletResponse response) throws Exception {
        byte[] data = questionService.downloadImportTemplate();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode("试题导入模板.xlsx", "UTF-8"));
        response.setContentLength(data.length);
        response.getOutputStream().write(data);
        response.getOutputStream().flush();
    }

    @PostMapping("/import")
    @Operation(summary = "Excel 批量导入试题（先校验后入库，返回失败行明细）")
    @PreAuthorize("@ss.hasPermission('training:question:create')")
    public CommonResult<QuestionImportRespVO> importQuestions(@RequestParam("file") MultipartFile file) {
        return success(questionService.importQuestions(file));
    }

}

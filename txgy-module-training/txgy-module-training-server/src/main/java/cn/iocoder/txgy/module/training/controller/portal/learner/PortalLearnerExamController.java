package cn.iocoder.txgy.module.training.controller.portal.learner;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.QuestionPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.QuestionRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.ExamEligibilityRespVO;
import cn.iocoder.txgy.module.training.service.learner.LearnerExamService;
import cn.iocoder.txgy.module.training.service.resource.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.Callable;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "学员端 - 岗位认证考试资格")
@RestController
@RequestMapping("/training/portal/learner/exam")
@Validated
public class PortalLearnerExamController {

    /** 个人学员（tenant_id=0）读取集团统一维护的课程目录。 */
    private static final Long PLATFORM_CATALOG_TENANT_ID = 1L;

    @Resource
    private LearnerExamService learnerExamService;
    @Resource
    private QuestionService questionService;

    @GetMapping("/exam-jobs")
    @Operation(summary = "获得可报考的岗位/工种列表（来自上架课程适用岗位去重）")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<List<String>> getExamJobs() {
        return success(executeInCatalogTenant(learnerExamService::getExamJobs));
    }

    @GetMapping("/exam-eligibility")
    @Operation(summary = "获得岗位认证考试资格（按认证等级递进解锁，系统自行判断岗位相关课程，每级最多 2 门；每级一场 90 分钟线上理论考试）")
    @Parameter(name = "jobCode", description = "岗位/工种名称", required = true, example = "防腐涂装工")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<ExamEligibilityRespVO> getExamEligibility(
            @RequestParam("jobCode") @NotBlank(message = "岗位/工种不能为空") String jobCode) {
        return success(executeInCatalogTenant(() ->
                learnerExamService.getExamEligibility(getLoginUserId(), jobCode)));
    }

    @GetMapping("/question-page")
    @Operation(summary = "试题分页（学员考试组卷使用，仅暴露启用状态的题库数据）")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<PageResult<QuestionRespVO>> getQuestionPage(@Validated QuestionPageReqVO pageReqVO) {
        return success(executeInCatalogTenant(() -> questionService.getQuestionPage(pageReqVO)));
    }

    @GetMapping("/question-get")
    @Operation(summary = "试题详情（学员考试组卷同步试题镜像使用）")
    @Parameter(name = "id", description = "试题 ID", required = true, example = "1")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<QuestionRespVO> getQuestion(@RequestParam("id") Long id) {
        return success(executeInCatalogTenant(() -> questionService.getQuestion(id)));
    }

    private <T> T executeInCatalogTenant(Callable<T> callable) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null && tenantId == 0L) {
            return TenantUtils.execute(PLATFORM_CATALOG_TENANT_ID, callable);
        }
        try {
            return callable.call();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}

package cn.iocoder.txgy.module.hrm.controller.admin.portal.agent;

import cn.iocoder.txgy.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalRecruitAgentChatReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalRecruitAgentChatRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalRecruitAgentSubmitReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalRecruitAgentTaskRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalResumeOptimizeApplyReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalResumeOptimizeReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalResumeGenerateReqVO;
import cn.iocoder.txgy.module.hrm.service.agent.HrmRecruitAgentAnalyzeService;
import cn.iocoder.txgy.module.hrm.service.agent.HrmRecruitAgentChatService;
import cn.iocoder.txgy.module.hrm.service.agent.HrmResumeOptimizeApplyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 员工端 - 招聘 Agent。
 *
 * <p>归属一律以登录用户为准；不接受前端传入的用户编号。
 * AI 结论只作参考，不会自动投递、淘汰或改变任何招聘状态。</p>
 */
@DataPermission(enable = false)
@PreAuthorize("isAuthenticated()")
@Tag(name = "员工端 - 招聘 Agent")
@RestController
@RequestMapping("/hrm/portal/recruit-agent")
@TenantIgnore
@Validated
public class HrmPortalRecruitAgentController {

    @Resource
    private HrmRecruitAgentAnalyzeService analyzeService;
    @Resource
    private HrmResumeOptimizeApplyService optimizeApplyService;
    @Resource
    private HrmRecruitAgentChatService chatService;

    @PostMapping("/chat")
    @Operation(summary = "求职助理对话",
            description = "就求职者的一句话给出回复；一问一答，不建任务、不落库，回复仅供参考")
    public CommonResult<HrmPortalRecruitAgentChatRespVO> chat(
            @Valid @RequestBody HrmPortalRecruitAgentChatReqVO reqVO) {
        return success(chatService.chat(getLoginUserId(), reqVO.getMessage(), reqVO.getImageDataUrl()));
    }

    @PostMapping("/resume/analyze")
    @Operation(summary = "提交简历诊断", description = "基于本人已固化的简历版本与有效职位生成诊断报告")
    public CommonResult<Long> analyzeResume(@Valid @RequestBody HrmPortalRecruitAgentSubmitReqVO reqVO) {
        return success(analyzeService.submitResumeAnalyze(getLoginUserId(), reqVO));
    }

    @PostMapping("/job/match")
    @Operation(summary = "提交职位匹配", description = "生成本人简历与目标职位的匹配报告，含已匹配、缺失与待确认项")
    public CommonResult<Long> matchJob(@Valid @RequestBody HrmPortalRecruitAgentSubmitReqVO reqVO) {
        return success(analyzeService.submitJobMatch(getLoginUserId(), reqVO));
    }

    @PostMapping("/application/precheck")
    @Operation(summary = "提交投递前检查",
            description = "检查必填信息、明显矛盾与主要差距；结论只作提醒，不会阻断投递")
    public CommonResult<Long> preCheckBeforeApply(@Valid @RequestBody HrmPortalRecruitAgentSubmitReqVO reqVO) {
        return success(analyzeService.submitPreSubmitCheck(getLoginUserId(), reqVO));
    }

    @PostMapping("/resume/optimize")
    @Operation(summary = "提交简历优化",
            description = "按模块产出可逐项确认的改写建议；只改写表述，不新增公司、时间、学历、证书、技能与成果")
    public CommonResult<Long> optimizeResume(@Valid @RequestBody HrmPortalResumeOptimizeReqVO reqVO) {
        return success(analyzeService.submitResumeOptimize(getLoginUserId(), reqVO));
    }

    @PostMapping("/resume/generate")
    @Operation(summary = "根据回答生成 AI 简历草稿",
            description = "只使用求职方向与经历描述生成结构化草稿，联系方式由个人资料保留")
    public CommonResult<Long> generateResume(@Valid @RequestBody HrmPortalResumeGenerateReqVO reqVO) {
        return success(analyzeService.submitResumeGenerate(getLoginUserId(), reqVO));
    }

    @PostMapping("/resume/optimize/apply")
    @Operation(summary = "确认优化建议并生成新版本",
            description = "按建议项逐条接受、拒绝或手工调整，确认结果固化为个人简历新版本；不改动源版本")
    public CommonResult<Long> applyOptimizeDecisions(
            @Valid @RequestBody HrmPortalResumeOptimizeApplyReqVO reqVO) {
        return success(optimizeApplyService.applyDecisions(getLoginUserId(), reqVO));
    }

    @GetMapping("/task/get")
    @Operation(summary = "查询 Agent 任务状态与结果")
    public CommonResult<HrmPortalRecruitAgentTaskRespVO> getTask(
            @Parameter(description = "任务编号") @RequestParam("taskId") Long taskId) {
        return success(analyzeService.getTaskResult(getLoginUserId(), taskId));
    }
}

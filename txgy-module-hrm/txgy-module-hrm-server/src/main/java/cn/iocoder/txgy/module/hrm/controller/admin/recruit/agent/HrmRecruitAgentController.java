package cn.iocoder.txgy.module.hrm.controller.admin.recruit.agent;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.agent.vo.HrmRecruitAgentApplicationReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.agent.vo.HrmRecruitAgentTaskRespVO;
import cn.iocoder.txgy.module.hrm.service.agent.HrmRecruitAgentAdminService;
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
 * 管理后台 - 招聘 Agent。
 *
 * <p>AI 结论只作参考：不会自动淘汰、录用、改变候选人 / 应聘 / 面试 / Offer 状态，
 * 也不做候选人批量排名或自动排序。数据范围限定在当前登录租户内。</p>
 */
@Tag(name = "管理后台 - HRM 招聘 Agent")
@RestController
@RequestMapping("/hrm/recruit/agent")
@Validated
public class HrmRecruitAgentController {

    @Resource
    private HrmRecruitAgentAdminService agentService;

    @PostMapping("/candidate/summary")
    @Operation(summary = "生成候选人摘要",
            description = "基于应聘记录关联的候选人简历快照生成摘要，区分简历事实、缺失信息与 AI 推断")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:agent:candidate-summary')")
    public CommonResult<Long> candidateSummary(
            @Valid @RequestBody HrmRecruitAgentApplicationReqVO reqVO) {
        return success(agentService.submitCandidateSummary(getLoginUserId(), reqVO));
    }

    @PostMapping("/application/match")
    @Operation(summary = "生成候选人与职位匹配报告",
            description = "按应聘记录生成单候选人匹配报告，含优势、缺口、风险、待核实项与逐项依据")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:agent:application-match')")
    public CommonResult<Long> applicationMatch(
            @Valid @RequestBody HrmRecruitAgentApplicationReqVO reqVO) {
        return success(agentService.submitApplicationMatch(getLoginUserId(), reqVO));
    }

    @PostMapping("/application/interview-question")
    @Operation(summary = "生成面试问题建议",
            description = "生成专业、项目、岗位能力与核实类问题，标明目的与关注点；不会写入面试评价")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:agent:interview-question')")
    public CommonResult<Long> interviewQuestion(
            @Valid @RequestBody HrmRecruitAgentApplicationReqVO reqVO) {
        return success(agentService.submitInterviewQuestion(getLoginUserId(), reqVO));
    }

    @GetMapping("/task/get")
    @Operation(summary = "查询 Agent 任务与结果",
            description = "只返回当前登录租户内的任务；重新分析请换幂等键重新提交")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:agent:audit-query')")
    public CommonResult<HrmRecruitAgentTaskRespVO> getTask(
            @Parameter(description = "任务编号") @RequestParam("taskId") Long taskId) {
        return success(agentService.getTaskResult(taskId));
    }
}

package cn.iocoder.txgy.module.bidding.controller.admin.flow;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.flow.vo.FlowLogRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.flow.vo.FlowNextActionRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.flow.vo.FlowTimerRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.flow.vo.FlowTransitionReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.flow.vo.FlowTransitionRespVO;
import cn.iocoder.txgy.module.bidding.service.flow.FlowTimerService;
import cn.iocoder.txgy.module.bidding.service.flow.FlowTransitionService;
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

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - bidding 招标全流程（状态流转）")
@RestController
@RequestMapping("/bidding/flow")
@Validated
public class FlowTransitionController {

    @Resource
    private FlowTransitionService flowTransitionService;
    @Resource
    private FlowTimerService flowTimerService;

    @PostMapping("/transition")
    @PreAuthorize("@ss.hasPermission('bidding:flow:transition')")
    @Operation(summary = "状态流转执行（测试模式=手动下一步/审批通过；PROD 由定时任务与审批接口调用）")
    @Idempotent
    public CommonResult<FlowTransitionRespVO> transition(@Valid @RequestBody FlowTransitionReqVO reqVO) {
        return success(flowTransitionService.transition(reqVO));
    }

    @GetMapping("/next-actions")
    @PreAuthorize("@ss.hasPermission('bidding:flow:query')")
    @Operation(summary = "查询当前状态可执行的动作列表（前端「下一步」按钮数据源）")
    public CommonResult<List<FlowNextActionRespVO>> nextActions(
            @RequestParam("bizType") @Parameter(description = "流转实体类型：PROJECT/TENDER_PROJECT/BID_SECTION") String bizType,
            @RequestParam("bizCode") @Parameter(description = "业务编号") String bizCode) {
        return success(flowTransitionService.nextActions(bizType, bizCode));
    }

    @GetMapping("/timers")
    @PreAuthorize("@ss.hasPermission('bidding:flow:query')")
    @Operation(summary = "查询时间驱动调度记录（用户配置的投标起止时间；WAITING/DONE/SKIPPED）")
    public CommonResult<List<FlowTimerRespVO>> timers(
            @RequestParam("bizType") @Parameter(description = "流转实体类型") String bizType,
            @RequestParam("bizCode") @Parameter(description = "业务编号") String bizCode) {
        return success(flowTimerService.listTimers(bizType, bizCode));
    }

    @GetMapping("/logs")
    @PreAuthorize("@ss.hasPermission('bidding:flow:query')")
    @Operation(summary = "查询流转留痕（按实体，时间倒序）")
    public CommonResult<List<FlowLogRespVO>> logs(
            @RequestParam("bizType") @Parameter(description = "流转实体类型") String bizType,
            @RequestParam("bizCode") @Parameter(description = "业务编号") String bizCode) {
        return success(flowTransitionService.logs(bizType, bizCode));
    }
}

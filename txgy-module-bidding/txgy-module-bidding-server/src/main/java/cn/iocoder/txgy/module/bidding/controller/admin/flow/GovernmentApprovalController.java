package cn.iocoder.txgy.module.bidding.controller.admin.flow;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.flow.vo.GovernmentApprovalReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.flow.vo.GovernmentApprovalRespVO;
import cn.iocoder.txgy.module.bidding.service.flow.GovernmentApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - bidding 招标全流程（政府审批预留）")
@RestController
@RequestMapping("/bidding/flow")
@Validated
public class GovernmentApprovalController {

    @Resource
    private GovernmentApprovalService governmentApprovalService;

    @PostMapping("/approval")
    @PreAuthorize("@ss.hasPermission('bidding:flow:approval')")
    @Operation(summary = "政府审批回调（预留接口；当前 stub 直接返回通过，接入真实审批系统后替换）")
    @Idempotent
    public CommonResult<GovernmentApprovalRespVO> approval(@Valid @RequestBody GovernmentApprovalReqVO reqVO) {
        return success(governmentApprovalService.approval(reqVO));
    }
}

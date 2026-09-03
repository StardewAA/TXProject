package cn.iocoder.txgy.module.bidding.controller.admin.filing;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.filing.vo.FilingResultReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.filing.vo.FilingSubmitReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.filing.vo.FilingSubmitRespVO;
import cn.iocoder.txgy.module.bidding.service.filing.FilingService;
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

/**
 * 备案（filing）：BID-ENT-REG-01 备案提交 / BID-SUP-FILE-01 备案结果回推。
 */
@Tag(name = "管理后台 - bidding 备案")
@RestController
@RequestMapping("/bidding/filing")
@Validated
public class FilingController {

    @Resource
    private FilingService filingService;

    @PostMapping("/submit")
    @Operation(summary = "备案提交（BID-ENT-REG-01，幂等键=备案类型+项目编号）")
    @PreAuthorize("@ss.hasPermission('bidding:filing:create')")
    @Idempotent
    public CommonResult<FilingSubmitRespVO> submit(@Valid @RequestBody FilingSubmitReqVO reqVO) {
        return success(filingService.submitFiling(reqVO));
    }

    @PostMapping("/receive-result")
    @Operation(summary = "备案结果回推（BID-SUP-FILE-01：监管 → 企业后台）")
    @PreAuthorize("@ss.hasPermission('bidding:filing:update')")
    @Idempotent
    public CommonResult<Boolean> receiveResult(@Valid @RequestBody FilingResultReqVO reqVO) {
        filingService.receiveFilingResult(reqVO);
        return success(true);
    }

}

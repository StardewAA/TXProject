package cn.iocoder.txgy.module.erp.controller.admin.purchase;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.mall.MallPurchaseDemandCancelReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.mall.MallPurchaseDemandReceiveReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.mall.MallPurchaseDemandRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.mall.MallPurchaseDemandResultReqVO;
import cn.iocoder.txgy.module.erp.service.purchase.ErpMallPurchaseDemandService;
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

@Tag(name = "ERP - Mall shortage procurement demand")
@RestController
@RequestMapping("/erp/mall-purchase-demand")
@Validated
public class ErpMallPurchaseDemandController {

    @Resource
    private ErpMallPurchaseDemandService mallPurchaseDemandService;

    @PostMapping("/receive")
    @Operation(summary = "Receive a mall shortage procurement demand")
    @PreAuthorize("@ss.hasPermission('erp:mall-purchase-demand:receive')")
    public CommonResult<MallPurchaseDemandRespVO> receiveDemand(
            @Valid @RequestBody MallPurchaseDemandReceiveReqVO reqVO) {
        return success(mallPurchaseDemandService.receiveDemand(reqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "Get a mall shortage procurement demand")
    @Parameter(name = "erpRequestNo", required = true, example = "MCPQ20260904000001")
    @PreAuthorize("@ss.hasPermission('erp:mall-purchase-demand:query')")
    public CommonResult<MallPurchaseDemandRespVO> getDemand(@RequestParam("erpRequestNo") String erpRequestNo) {
        return success(mallPurchaseDemandService.getDemand(erpRequestNo));
    }

    @PostMapping("/cancel")
    @Operation(summary = "Request cancellation of a mall shortage procurement demand")
    @PreAuthorize("@ss.hasPermission('erp:mall-purchase-demand:cancel')")
    public CommonResult<MallPurchaseDemandRespVO> requestCancel(@RequestParam("erpRequestNo") String erpRequestNo,
                                                                  @Valid @RequestBody MallPurchaseDemandCancelReqVO reqVO) {
        return success(mallPurchaseDemandService.requestCancel(erpRequestNo, reqVO));
    }

    @PostMapping("/result")
    @Operation(summary = "Update a mall shortage procurement demand result")
    @Parameter(name = "erpRequestNo", required = true, example = "MCPQ20260904000001")
    @PreAuthorize("@ss.hasPermission('erp:mall-purchase-demand:update')")
    public CommonResult<MallPurchaseDemandRespVO> updateResult(
            @RequestParam("erpRequestNo") String erpRequestNo,
            @Valid @RequestBody MallPurchaseDemandResultReqVO reqVO) {
        return success(mallPurchaseDemandService.updateResult(erpRequestNo, reqVO));
    }

}

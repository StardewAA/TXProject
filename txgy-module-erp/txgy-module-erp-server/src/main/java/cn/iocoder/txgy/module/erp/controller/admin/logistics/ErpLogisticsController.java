package cn.iocoder.txgy.module.erp.controller.admin.logistics;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsDetailRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsEventRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsEventPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsEventPageRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsExceptionHandleReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsNodeReceiveReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsPageRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsReceiptReceiveReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsTaskReceiveReqVO;
import cn.iocoder.txgy.module.erp.service.logistics.ErpLogisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - ERP 物流任务与轨迹")
@RestController
@RequestMapping("/erp/logistics")
@Validated
public class ErpLogisticsController {

    @Resource
    private ErpLogisticsService logisticsService;

    @PostMapping("/task")
    @Operation(summary = "接收物流任务关联")
    @PreAuthorize("@ss.hasPermission('erp:logistics:compensate')")
    public CommonResult<ErpLogisticsEventRespVO> receiveTask(
            @Valid @RequestBody ErpLogisticsTaskReceiveReqVO reqVO) {
        return success(logisticsService.receiveTask(reqVO));
    }

    @PostMapping("/node")
    @Operation(summary = "接收物流关键节点")
    @PreAuthorize("@ss.hasPermission('erp:logistics:compensate')")
    public CommonResult<ErpLogisticsEventRespVO> receiveNode(
            @Valid @RequestBody ErpLogisticsNodeReceiveReqVO reqVO) {
        return success(logisticsService.receiveNode(reqVO));
    }

    @PostMapping("/receipt")
    @Operation(summary = "接收物流签收结果")
    @PreAuthorize("@ss.hasPermission('erp:logistics:compensate')")
    public CommonResult<ErpLogisticsEventRespVO> receiveReceipt(
            @Valid @RequestBody ErpLogisticsReceiptReceiveReqVO reqVO) {
        return success(logisticsService.receiveReceipt(reqVO));
    }

    @GetMapping("/page")
    @Operation(summary = "获得物流任务分页")
    @PreAuthorize("@ss.hasPermission('erp:logistics:query')")
    public CommonResult<PageResult<ErpLogisticsPageRespVO>> getLogisticsPage(
            @Valid ErpLogisticsPageReqVO reqVO) {
        return success(logisticsService.getLogisticsPage(reqVO));
    }

    @GetMapping("/by-order")
    @Operation(summary = "按 ERP 单据查询物流详情")
    @PreAuthorize("@ss.hasPermission('erp:logistics:query')")
    public CommonResult<ErpLogisticsDetailRespVO> getLogisticsByOrder(
            @RequestParam("sourceOrderType") @NotBlank String sourceOrderType,
            @RequestParam("sourceOrderNo") @NotBlank String sourceOrderNo) {
        return success(logisticsService.getLogisticsByOrder(sourceOrderType, sourceOrderNo));
    }

    @GetMapping("/track")
    @Operation(summary = "获得物流轨迹与关键节点详情")
    @Parameter(name = "id", description = "物流关联编号", required = true)
    @PreAuthorize("@ss.hasPermission('erp:logistics:track')")
    public CommonResult<ErpLogisticsDetailRespVO> getLogisticsTrack(@RequestParam("id") Long id) {
        return success(logisticsService.getLogisticsDetail(id));
    }

    @GetMapping("/event-result")
    @Operation(summary = "查询物流事件处理结果")
    @PreAuthorize("@ss.hasPermission('erp:logistics:compensate')")
    public CommonResult<ErpLogisticsEventRespVO> getEventResult(
            @RequestParam("requestId") @NotBlank String requestId) {
        return success(logisticsService.getEventResult(requestId));
    }

    @GetMapping("/event-page")
    @Operation(summary = "获得物流事件处理记录分页")
    @PreAuthorize("@ss.hasPermission('erp:logistics:compensate')")
    public CommonResult<PageResult<ErpLogisticsEventPageRespVO>> getEventPage(
            @Valid ErpLogisticsEventPageReqVO reqVO) {
        return success(logisticsService.getEventPage(reqVO));
    }

    @PostMapping("/event/retry")
    @Operation(summary = "重试待补偿物流事件")
    @PreAuthorize("@ss.hasPermission('erp:logistics:compensate')")
    public CommonResult<ErpLogisticsEventRespVO> retryPendingEvent(@RequestParam("id") Long id) {
        return success(logisticsService.retryPendingEvent(id));
    }

    @PutMapping("/exception/handle")
    @Operation(summary = "处理物流异常")
    @PreAuthorize("@ss.hasPermission('erp:logistics:exception-handle')")
    public CommonResult<Boolean> handleException(
            @Valid @RequestBody ErpLogisticsExceptionHandleReqVO reqVO) {
        logisticsService.handleException(reqVO);
        return success(true);
    }

}

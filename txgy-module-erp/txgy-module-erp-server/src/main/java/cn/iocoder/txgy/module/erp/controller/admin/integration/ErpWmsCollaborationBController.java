package cn.iocoder.txgy.module.erp.controller.admin.integration;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.erp.controller.admin.integration.vo.ErpWmsCollaborationBConfirmReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.integration.vo.ErpWmsCollaborationBConfirmRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.integration.vo.ErpWmsCollaborationBPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.integration.vo.ErpWmsCollaborationBDetailRespVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.integration.ErpWmsCollaborationBDO;
import cn.iocoder.txgy.module.erp.controller.admin.integration.vo.ErpWmsInboundApplyReqVO;
import cn.iocoder.txgy.module.erp.service.integration.ErpWmsBusinessDocumentService;
import cn.iocoder.txgy.module.erp.service.integration.ErpWmsCollaborationBService;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationAcceptedRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
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

@Tag(name = "管理后台 - ERP 供应链协同单")
@RestController
@RequestMapping("/erp/wms-collaboration-b")
@Validated
public class ErpWmsCollaborationBController {

    @Resource
    private ErpWmsCollaborationBService collaborationBService;

    @Resource
    private ErpWmsBusinessDocumentService businessDocumentService;

    @PostMapping("/apply-inbound")
    @Operation(summary = "ERP 采购入库单手动申请入库到 WMS（按钮触发，先审批后申请）")
    @PreAuthorize("@ss.hasPermission('erp:collaboration-order:apply-inbound')")
    public CommonResult<WmsIntegrationAcceptedRespDTO> applyInboundToWms(
            @Valid @RequestBody ErpWmsInboundApplyReqVO request) {
        return success(businessDocumentService.applyInboundToWms(
                request.getPurchaseInId(), request.getTargetErpWarehouseId(), request.getRemark()));
    }

    @PutMapping("/confirm")
    @Operation(summary = "业务确认协同单；ACCEPTED 生成采购订单草稿 / REJECTED 拒绝 / NEED_SUPPLEMENT 要求补充资料")
    @PreAuthorize("@ss.hasPermission('erp:collaboration-order:purchase-confirm')")
    public CommonResult<ErpWmsCollaborationBConfirmRespVO> confirm(
            @Valid @RequestBody ErpWmsCollaborationBConfirmReqVO request) {
        return success(collaborationBService.confirm(request));
    }

    @GetMapping("/page")
    @Operation(summary = "获得供应链协同单分页（一站式接单工作台数据源）")
    @PreAuthorize("@ss.hasPermission('erp:collaboration-order:query')")
    public CommonResult<PageResult<ErpWmsCollaborationBDO>> getCollaborationPage(
            @Valid ErpWmsCollaborationBPageReqVO pageReqVO) {
        return success(collaborationBService.getCollaborationPage(pageReqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "获得供应链协同单详情（含明细、确认日志与关联订单状态快照）")
    @PreAuthorize("@ss.hasPermission('erp:collaboration-order:query')")
    public CommonResult<ErpWmsCollaborationBDetailRespVO> getCollaborationDetail(@RequestParam("id") Long id) {
        return success(collaborationBService.getCollaborationDetail(id));
    }
}

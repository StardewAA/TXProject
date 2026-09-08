package cn.iocoder.txgy.module.erp.controller.admin.integration;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.erp.controller.admin.integration.vo.ErpWmsCollaborationBConfirmReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.integration.vo.ErpWmsCollaborationBConfirmRespVO;
import cn.iocoder.txgy.module.erp.service.integration.ErpWmsCollaborationBService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - ERP/WMS 协作单 B")
@RestController
@RequestMapping("/erp/wms-collaboration-b")
@Validated
public class ErpWmsCollaborationBController {

    @Resource
    private ErpWmsCollaborationBService collaborationBService;

    @PutMapping("/confirm")
    @Operation(summary = "业务确认协作单 B；接受后生成采购订单草稿")
    @PreAuthorize("@ss.hasPermission('erp:purchase-order:create')")
    public CommonResult<ErpWmsCollaborationBConfirmRespVO> confirm(
            @Valid @RequestBody ErpWmsCollaborationBConfirmReqVO request) {
        return success(collaborationBService.confirm(request));
    }
}

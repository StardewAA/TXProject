package cn.iocoder.txgy.module.erp.api.wms;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpCollaborationBReceiveReqDTO;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpCollaborationBReceiveRespDTO;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpInventoryDecisionCallbackReqDTO;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpWmsExecutionCallbackReqDTO;
import cn.iocoder.txgy.module.erp.service.integration.ErpSaleWmsIntegrationService;
import cn.iocoder.txgy.module.erp.service.integration.ErpWmsBusinessDocumentService;
import cn.iocoder.txgy.module.erp.service.integration.ErpWmsCollaborationBService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@RestController
@Validated
public class ErpWmsCallbackApiImpl implements ErpWmsCallbackApi {

    @Resource
    private ErpSaleWmsIntegrationService saleWmsIntegrationService;
    @Resource
    private ErpWmsCollaborationBService collaborationBService;
    @Resource
    private ErpWmsBusinessDocumentService businessDocumentService;

    @Override
    public CommonResult<Boolean> callbackInventoryDecision(ErpInventoryDecisionCallbackReqDTO reqDTO) {
        validateTenant(reqDTO.getTenantId());
        saleWmsIntegrationService.receiveInventoryDecision(reqDTO);
        return success(true);
    }

    @Override
    public CommonResult<Boolean> callbackExecutionResult(ErpWmsExecutionCallbackReqDTO reqDTO) {
        validateTenant(reqDTO.getTenantId());
        if (businessDocumentService.supports(reqDTO.getOrderType())) {
            businessDocumentService.receiveExecutionResult(reqDTO);
        } else {
            saleWmsIntegrationService.receiveExecutionResult(reqDTO);
        }
        return success(true);
    }

    @Override
    public CommonResult<ErpCollaborationBReceiveRespDTO> receiveCollaborationB(ErpCollaborationBReceiveReqDTO reqDTO) {
        validateTenant(reqDTO.getTenantId());
        return success(collaborationBService.receive(reqDTO));
    }

    private void validateTenant(Long requestTenantId) {
        Long contextTenantId = TenantContextHolder.getRequiredTenantId();
        if (!contextTenantId.equals(requestTenantId)) {
            throw new IllegalArgumentException("请求租户与调用上下文不一致");
        }
    }

}

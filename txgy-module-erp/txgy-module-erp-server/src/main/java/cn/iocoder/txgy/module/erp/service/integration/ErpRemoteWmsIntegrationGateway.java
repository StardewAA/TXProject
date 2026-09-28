package cn.iocoder.txgy.module.erp.service.integration;

import cn.iocoder.txgy.module.wms.api.integration.WmsIntegrationApi;
import cn.iocoder.txgy.module.wms.api.integration.dto.*;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "txgy.integration.wms", name = "mode", havingValue = "remote")
public class ErpRemoteWmsIntegrationGateway implements ErpWmsIntegrationGateway {

    @Resource
    private WmsIntegrationApi wmsIntegrationApi;

    @Override
    public WmsIntegrationAcceptedRespDTO upsertMaterial(WmsMaterialUpsertReqDTO reqDTO) {
        return wmsIntegrationApi.upsertMaterial(reqDTO).getCheckedData();
    }

    @Override
    public WmsIntegrationAcceptedRespDTO upsertPartner(WmsPartnerUpsertReqDTO reqDTO) {
        return wmsIntegrationApi.upsertPartner(reqDTO).getCheckedData();
    }

    @Override
    public WmsIntegrationAcceptedRespDTO upsertWarehouse(WmsWarehouseUpsertReqDTO reqDTO) {
        return wmsIntegrationApi.upsertWarehouse(reqDTO).getCheckedData();
    }

    @Override
    public WmsInventoryDecisionRespDTO createInventoryDecision(WmsInventoryDecisionReqDTO reqDTO) {
        return wmsIntegrationApi.createInventoryDecision(reqDTO).getCheckedData();
    }

    @Override
    public WmsIntegrationAcceptedRespDTO releaseReservation(WmsReservationReleaseReqDTO reqDTO) {
        return wmsIntegrationApi.releaseReservation(reqDTO).getCheckedData();
    }

    @Override
    public WmsIntegrationAcceptedRespDTO createPurchaseReceipt(WmsOrderRequestDTO reqDTO) {
        return wmsIntegrationApi.createPurchaseReceipt(reqDTO).getCheckedData();
    }

    @Override
    public WmsIntegrationAcceptedRespDTO createCustomerReturnReceipt(WmsOrderRequestDTO reqDTO) {
        return wmsIntegrationApi.createCustomerReturnReceipt(reqDTO).getCheckedData();
    }

    @Override
    public WmsIntegrationAcceptedRespDTO createPurchaseReturnShipment(WmsOrderRequestDTO reqDTO) {
        return wmsIntegrationApi.createPurchaseReturnShipment(reqDTO).getCheckedData();
    }

    @Override
    public WmsIntegrationRequestRespDTO getRequestResult(String requestId) {
        return wmsIntegrationApi.getRequestResult(requestId).getCheckedData();
    }

    @Override
    public WmsInventoryDecisionRespDTO getInventoryDecision(Long tenantId, String saleOrderNo) {
        return wmsIntegrationApi.getInventoryDecision(tenantId, saleOrderNo).getCheckedData();
    }

    @Override
    public WmsIntegrationRequestRespDTO getOrderExecution(Long tenantId, String sourceOrderNo, String orderType) {
        return wmsIntegrationApi.getOrderExecution(tenantId, sourceOrderNo, orderType).getCheckedData();
    }

}

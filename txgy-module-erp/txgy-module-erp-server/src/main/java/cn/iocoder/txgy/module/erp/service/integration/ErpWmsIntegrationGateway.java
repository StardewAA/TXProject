package cn.iocoder.txgy.module.erp.service.integration;

import cn.iocoder.txgy.module.wms.api.integration.dto.*;

public interface ErpWmsIntegrationGateway {

    WmsIntegrationAcceptedRespDTO upsertMaterial(WmsMaterialUpsertReqDTO reqDTO);

    WmsIntegrationAcceptedRespDTO upsertPartner(WmsPartnerUpsertReqDTO reqDTO);

    WmsIntegrationAcceptedRespDTO upsertWarehouse(WmsWarehouseUpsertReqDTO reqDTO);

    WmsInventoryDecisionRespDTO createInventoryDecision(WmsInventoryDecisionReqDTO reqDTO);

    WmsIntegrationAcceptedRespDTO releaseReservation(WmsReservationReleaseReqDTO reqDTO);

    WmsIntegrationAcceptedRespDTO createPurchaseReceipt(WmsOrderRequestDTO reqDTO);

    WmsIntegrationAcceptedRespDTO createCustomerReturnReceipt(WmsOrderRequestDTO reqDTO);

    WmsIntegrationAcceptedRespDTO createPurchaseReturnShipment(WmsOrderRequestDTO reqDTO);

    WmsIntegrationRequestRespDTO getRequestResult(String requestId);

    WmsInventoryDecisionRespDTO getInventoryDecision(Long tenantId, String saleOrderNo);

    WmsIntegrationRequestRespDTO getOrderExecution(Long tenantId, String sourceOrderNo, String orderType);

}

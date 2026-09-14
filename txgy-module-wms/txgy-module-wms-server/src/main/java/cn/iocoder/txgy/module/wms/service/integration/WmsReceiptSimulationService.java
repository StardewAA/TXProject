package cn.iocoder.txgy.module.wms.service.integration;

import cn.iocoder.txgy.module.wms.controller.admin.order.receipt.vo.order.WmsReceiptSimulationReqVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.wms.api.integration.WmsIntegrationApiImpl;
import cn.iocoder.txgy.module.wms.api.integration.dto.*;
import cn.iocoder.txgy.module.wms.dal.mysql.md.warehouse.WmsWarehouseMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.*;
import cn.iocoder.txgy.module.wms.dal.mysql.order.receipt.WmsReceiptOrderMapper;
import cn.iocoder.txgy.module.wms.dal.dataobject.order.receipt.WmsReceiptOrderDO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Map;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.*;

@Service
@Validated
public class WmsReceiptSimulationService {
    @Resource private WmsIntegrationApiImpl integrationApi;
    @Resource private WmsReceiptOrderMapper receiptMapper;
    @Resource private cn.iocoder.txgy.module.wms.dal.mysql.order.shipment.WmsShipmentOrderMapper shipmentMapper;
    @Resource private WmsWarehouseMapper warehouseMapper;
    @Resource private WmsItemSkuMapper skuMapper;
    @Resource private WmsItemMapper itemMapper;

    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> simulate(@Valid WmsReceiptSimulationReqVO request) {
        String sourceNo="SIM-"+request.getSourceOrderNo().trim();
        String type = request.getOrderType();
        String source = type.startsWith("PRODUCTION_") ? "MES" : "ERP";
        boolean receipt = type.endsWith("RECEIPT");
        String targetNo="WMS-"+source+"-"+sourceNo;
        var existing = receipt ? receiptMapper.selectByNo(targetNo) : null;
        var shipment = receipt ? null : shipmentMapper.selectByNo(targetNo);
        if((existing!=null && !Boolean.TRUE.equals(existing.getSimulation()))
                || (shipment!=null && !Boolean.TRUE.equals(shipment.getSimulation()))) {
            throw exception(INTEGRATION_RECEIPT_REQUEST_INVALID,"来源单号已被真实业务使用");
        }
        var warehouse=warehouseMapper.selectById(request.getWarehouseId());
        if(warehouse==null) throw exception(WAREHOUSE_NOT_EXISTS);
        var items=new ArrayList<WmsIntegrationDocumentItemDTO>();
        int line=0;
        for(var input:request.getDetails()) {
            var sku=skuMapper.selectById(input.getSkuId());
            if(sku==null) throw exception(ITEM_SKU_NOT_EXISTS);
            var item=itemMapper.selectById(sku.getItemId());
            if(item==null) throw exception(ITEM_NOT_EXISTS);
            if(item.getQualityRequired()==null) {
                throw exception(INTEGRATION_RECEIPT_REQUEST_INVALID,"请先明确物料是否需要质检");
            }
            items.add(new WmsIntegrationDocumentItemDTO().setLineNo(++line).setSourceLineNo(line)
                    .setMaterialCode(item.getCode()).setSkuCode(sku.getCode()).setMaterialType(item.getMaterialType())
                    .setMaterialNameSnapshot(item.getName()).setSpecificationSnapshot(sku.getName())
                    .setUnitCode(item.getUnit()).setUnitNameSnapshot(item.getUnit())
                    .setQualityRequiredSnapshot(item.getQualityRequired()).setWarehouseCode(warehouse.getCode())
                    .setRequiredQuantity(input.getQuantity()).setPrice(input.getPrice()));
        }
        var dto=new WmsOrderRequestDTO().setSourceOrderNo(sourceNo).setBusinessChainNo(sourceNo)
                .setOrderType(type).setEventVersion(1L).setItems(items);
        dto.setTenantId(TenantContextHolder.getRequiredTenantId());
        dto.setSourceModule(source); dto.setRequestId("SIM-"+UUID.randomUUID()); dto.setOccurredAt(LocalDateTime.now());
        switch (type) {
            case "PURCHASE_RECEIPT" -> integrationApi.createPurchaseReceipt(dto).getCheckedData();
            case "PRODUCTION_RECEIPT" -> integrationApi.createProductionReceipt(dto).getCheckedData();
            case "SALE_SHIPMENT" -> integrationApi.createSaleShipment(dto).getCheckedData();
            case "PRODUCTION_SHIPMENT" -> integrationApi.createProductionShipment(dto).getCheckedData();
            default -> throw exception(INTEGRATION_FEATURE_NOT_ENABLED);
        }
        if (!receipt) {
            var created = shipmentMapper.selectByNo(targetNo);
            if (created == null) throw exception(SHIPMENT_ORDER_NOT_EXISTS);
            shipmentMapper.updateById(new cn.iocoder.txgy.module.wms.dal.dataobject.order.shipment.WmsShipmentOrderDO()
                    .setId(created.getId()).setSimulation(true));
            return Map.of("id", created.getId(), "targetOrderNo", targetNo);
        }
        var order=receiptMapper.selectByNo(targetNo);
        if(order==null) throw exception(RECEIPT_ORDER_NOT_EXISTS);
        receiptMapper.updateById(new WmsReceiptOrderDO().setId(order.getId()).setSimulation(true)
                .setNotificationStatus("SIMULATED"));
        return Map.of("id",order.getId(),"targetOrderNo",targetNo);
    }
}

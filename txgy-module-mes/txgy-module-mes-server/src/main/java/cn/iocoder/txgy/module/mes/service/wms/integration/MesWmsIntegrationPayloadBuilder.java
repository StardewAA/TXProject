package cn.iocoder.txgy.module.mes.service.wms.integration;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.item.MesMdItemDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.unitmeasure.MesMdUnitMeasureDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.workorder.MesProWorkOrderBomDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.workorder.MesProWorkOrderDO;
import cn.iocoder.txgy.module.mes.enums.wms.MesWmsIntegrationOrderTypeEnum;
import cn.iocoder.txgy.module.mes.service.md.item.MesMdItemService;
import cn.iocoder.txgy.module.mes.service.md.unitmeasure.MesMdUnitMeasureService;
import cn.iocoder.txgy.module.mes.service.pro.workorder.MesProWorkOrderBomService;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationDocumentItemDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsOrderRequestDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MES 工单 → WMS 集成请求报文构造器
 *
 * <p>将 MES 工单 / BOM 转换为 WMS {@link WmsOrderRequestDTO}，完成 MES 物料模型到 WMS 物料模型的字段映射。
 * 物料编码、单位编码取自 MES 主数据；materialType 按业务类型简化映射（领料=RAW 原料、入库=FG 成品）。</p>
 *
 * @author OPENLAB BS
 */
@Component
public class MesWmsIntegrationPayloadBuilder {

    @Resource
    private MesProWorkOrderBomService workOrderBomService;
    @Resource
    private MesMdItemService itemService;
    @Resource
    private MesMdUnitMeasureService unitMeasureService;

    /**
     * 构造生产领料出库申请报文（PRODUCTION_SHIPMENT）：以工单 BOM 的每条原料为明细
     */
    public WmsOrderRequestDTO buildShipmentPayload(MesProWorkOrderDO workOrder, String businessChainNo) {
        List<MesProWorkOrderBomDO> bomList = workOrderBomService.getWorkOrderBomListByWorkOrderId(workOrder.getId());
        List<WmsIntegrationDocumentItemDTO> items = new ArrayList<>();
        if (CollUtil.isNotEmpty(bomList)) {
            Map<Long, MesMdItemDO> itemMap = itemService.getItemMap(
                    bomList.stream().map(MesProWorkOrderBomDO::getItemId).filter(Objects::nonNull).toList());
            List<Long> unitIds = bomList.stream()
                    .map(b -> itemMap.get(b.getItemId()))
                    .filter(Objects::nonNull)
                    .map(MesMdItemDO::getUnitMeasureId)
                    .filter(Objects::nonNull)
                    .toList();
            Map<Long, MesMdUnitMeasureDO> unitMap = unitMeasureService.getUnitMeasureMap(unitIds);
            int lineNo = 1;
            for (MesProWorkOrderBomDO bom : bomList) {
                MesMdItemDO item = itemMap.get(bom.getItemId());
                if (item == null) {
                    continue;
                }
                MesMdUnitMeasureDO unit = unitMap.get(item.getUnitMeasureId());
                items.add(toItem(lineNo++, item, unit, bom.getQuantity(), "RAW"));
            }
        }
        return buildRequest(workOrder, businessChainNo,
                MesWmsIntegrationOrderTypeEnum.PRODUCTION_SHIPMENT.getCode(), items);
    }

    /**
     * 构造生产完工入库申请报文（PRODUCTION_RECEIPT）：以工单产品本身为明细
     */
    public WmsOrderRequestDTO buildReceiptPayload(MesProWorkOrderDO workOrder, String businessChainNo) {
        List<WmsIntegrationDocumentItemDTO> items = new ArrayList<>();
        MesMdItemDO product = itemService.getItem(workOrder.getProductId());
        if (product != null) {
            MesMdUnitMeasureDO unit = product.getUnitMeasureId() == null ? null
                    : unitMeasureService.getUnitMeasureMap(List.of(product.getUnitMeasureId()))
                        .get(product.getUnitMeasureId());
            items.add(toItem(1, product, unit, workOrder.getQuantity(), "FG"));
        }
        return buildRequest(workOrder, businessChainNo,
                MesWmsIntegrationOrderTypeEnum.PRODUCTION_RECEIPT.getCode(), items);
    }

    private WmsIntegrationDocumentItemDTO toItem(int lineNo, MesMdItemDO item, MesMdUnitMeasureDO unit,
                                                java.math.BigDecimal quantity, String materialType) {
        WmsIntegrationDocumentItemDTO dto = new WmsIntegrationDocumentItemDTO();
        dto.setLineNo(lineNo);
        dto.setMaterialCode(item.getCode());
        dto.setSkuCode(item.getCode());
        dto.setMaterialType(materialType);
        dto.setMaterialNameSnapshot(item.getName());
        dto.setSpecificationSnapshot(item.getSpecification());
        dto.setUnitCode(unit == null ? null : unit.getCode());
        dto.setUnitNameSnapshot(unit == null ? null : unit.getName());
        dto.setRequiredQuantity(quantity);
        return dto;
    }

    private WmsOrderRequestDTO buildRequest(MesProWorkOrderDO workOrder, String businessChainNo,
                                            String orderType, List<WmsIntegrationDocumentItemDTO> items) {
        WmsOrderRequestDTO req = new WmsOrderRequestDTO();
        req.setRequestId("MES-" + orderType + "-" + workOrder.getCode() + "-v1");
        req.setTenantId(TenantContextHolder.getRequiredTenantId());
        req.setSourceModule("MES");
        req.setOccurredAt(LocalDateTime.now());
        req.setBusinessChainNo(businessChainNo);
        req.setSourceOrderNo(workOrder.getCode());
        req.setOrderType(orderType);
        req.setEventVersion(1L);
        req.setItems(items);
        return req;
    }

}

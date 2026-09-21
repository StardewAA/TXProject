package cn.iocoder.txgy.module.product.service.review;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.txgy.module.wms.api.stock.WmsStockReadApi;
import cn.iocoder.txgy.module.wms.api.stock.dto.WmsWarehouseReadRespDTO;
import cn.iocoder.txgy.module.wms.api.stockapplication.dto.WmsStockApplicationOptionsRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService.invalid;
import static cn.iocoder.txgy.module.wms.enums.integration.WmsIntegrationConstants.MATERIAL_FINISHED;

/**
 * 商品送审及上架前的 WMS 主数据校验。
 *
 * <p>校验只调用租户隔离的 WMS 只读接口，不要求库存余额已经存在，也不进行任何库存写入。
 * WMS 不可用、映射缺失或主数据不符合成品销售边界时一律失败关闭。</p>
 */
@Component
@RequiredArgsConstructor
public class ProductWmsListingValidator {

    private final WmsStockReadApi wmsStockReadApi;

    public void validate(List<ProductSkuDO> productSkus) {
        if (productSkus == null || productSkus.isEmpty()) throw invalid("商品缺少有效规格");
        for (ProductSkuDO productSku : productSkus) {
            if (productSku == null || productSku.getWmsSkuId() == null || productSku.getWmsWarehouseId() == null
                    || productSku.getWmsSkuId() <= 0 || productSku.getWmsWarehouseId() <= 0) {
                throw invalid("每个商品 SKU 都必须配置有效的 WMS 成品物料 SKU 和成品仓");
            }
        }

        final List<WmsWarehouseReadRespDTO> warehouses;
        final List<WmsStockApplicationOptionsRespDTO.Sku> wmsSkus;
        try {
            var warehouseResult = wmsStockReadApi.getWarehouses();
            var skuResult = wmsStockReadApi.getSkuOptions();
            warehouses = warehouseResult == null ? null : warehouseResult.getCheckedData();
            wmsSkus = skuResult == null ? null : skuResult.getCheckedData();
        } catch (RuntimeException ex) {
            throw new ServiceException(503, "WMS 主数据暂时无法核验，商品不能送审或上架");
        }
        if (warehouses == null || wmsSkus == null) {
            throw new ServiceException(503, "WMS 主数据返回不完整，商品不能送审或上架");
        }

        for (ProductSkuDO productSku : productSkus) {
            List<WmsWarehouseReadRespDTO> matchedWarehouses = warehouses.stream()
                    .filter(Objects::nonNull)
                    .filter(warehouse -> Objects.equals(warehouse.getId(), productSku.getWmsWarehouseId()))
                    .toList();
            if (matchedWarehouses.size() != 1) {
                throw invalid("商品 SKU 配置的 WMS 仓库不存在或映射不唯一");
            }
            WmsWarehouseReadRespDTO warehouse = matchedWarehouses.getFirst();
            if (!Objects.equals(warehouse.getStatus(), CommonStatusEnum.ENABLE.getStatus())
                    || !MATERIAL_FINISHED.equals(warehouse.getWarehouseType())) {
                throw invalid("商品 SKU 只能关联已启用的 WMS 成品仓");
            }

            List<WmsStockApplicationOptionsRespDTO.Sku> matchedSkus = wmsSkus.stream()
                    .filter(Objects::nonNull)
                    .filter(wmsSku -> Objects.equals(wmsSku.getSkuId(), productSku.getWmsSkuId()))
                    .toList();
            if (matchedSkus.size() != 1) {
                throw invalid("商品 SKU 配置的 WMS 物料不存在、未启用或映射不唯一");
            }
            if (!MATERIAL_FINISHED.equals(matchedSkus.getFirst().getMaterialType())) {
                throw invalid("商品 SKU 只能关联 WMS 成品物料");
            }
        }
    }
}

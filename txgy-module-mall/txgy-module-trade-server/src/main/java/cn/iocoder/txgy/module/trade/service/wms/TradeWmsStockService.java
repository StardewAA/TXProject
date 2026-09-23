package cn.iocoder.txgy.module.trade.service.wms;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.product.api.sku.ProductSkuApi;
import cn.iocoder.txgy.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.txgy.module.wms.api.stock.WmsStockReadApi;
import cn.iocoder.txgy.module.wms.api.stock.dto.WmsStockPageReqDTO;
import cn.iocoder.txgy.module.wms.api.integration.WmsUnitCodeDictionary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 商城到 WMS 的库存查询适配层。当前只核验现存库存，不执行预占、释放或出库。 */
@Service
@RequiredArgsConstructor
public class TradeWmsStockService {

    private final ProductSkuApi productSkuApi;
    private final WmsStockReadApi wmsStockReadApi;

    public void validateAvailable(List<TradeOrderItemDO> orderItems) {
        Map<Long, Integer> required = new LinkedHashMap<>();
        for (TradeOrderItemDO item : orderItems) {
            if (item.getSkuId() == null || item.getCount() == null || item.getCount() <= 0) {
                throw new ServiceException(400, "订单商品数量无效");
            }
            required.merge(item.getSkuId(), item.getCount(), Math::addExact);
        }
        validateSkuCounts(required);
    }

    public void validateSkuCounts(Map<Long, Integer> required) {
        if (required == null || required.isEmpty()) {
            throw new ServiceException(400, "待核验商品不能为空");
        }
        Map<Long, ProductSkuRespDTO> skuMap = productSkuApi.getSkuMap(required.keySet());
        for (Map.Entry<Long, Integer> entry : required.entrySet()) {
            ProductSkuRespDTO sku = skuMap.get(entry.getKey());
            if (sku == null) {
                throw new ServiceException(400, "商品规格不存在，SKU=" + entry.getKey());
            }
            if (sku.getWmsSkuId() == null || sku.getWmsWarehouseId() == null) {
                throw new ServiceException(400, "商品尚未配置 WMS 规格和仓库映射，SKU=" + entry.getKey());
            }
            WmsStockPageReqDTO query = new WmsStockPageReqDTO();
            query.setSkuId(sku.getWmsSkuId());
            query.setWarehouseId(sku.getWmsWarehouseId());
            query.setPageNo(1);
            query.setPageSize(2);
            cn.iocoder.txgy.framework.common.pojo.PageResult<cn.iocoder.txgy.module.wms.api.stock.dto.WmsStockRespDTO> result;
            try {
                result = wmsStockReadApi.getStockPage(query).getCheckedData();
            } catch (RuntimeException ex) {
                throw new ServiceException(503, "WMS 库存暂时无法核验，请稍后重试");
            }
            if (result == null || result.getList() == null || result.getList().isEmpty()) {
                throw shortage(entry.getKey(), entry.getValue(), BigDecimal.ZERO);
            }
            if (result.getList().size() != 1) {
                throw new ServiceException(500, "WMS 库存映射不唯一，请核实 SKU=" + entry.getKey());
            }
            var stock = result.getList().getFirst();
            // 旧版 WMS 若尚未支持 skuId / warehouseId 筛选，可能忽略查询参数并返回其它库存。
            // 必须按返回标识再次核验，宁可暂时阻断交易，也不能把其它 SKU 或仓库的库存当成本单库存。
            if (!Objects.equals(stock.getSkuId(), sku.getWmsSkuId())
                    || !Objects.equals(stock.getWarehouseId(), sku.getWmsWarehouseId())) {
                throw new ServiceException(503, "WMS 库存查询结果与商品映射不一致，请稍后重试");
            }
            String mallUnit = mallUnit(sku);
            WmsUnitCodeDictionary.Unit mallUnitDefinition = WmsUnitCodeDictionary.resolve(mallUnit);
            WmsUnitCodeDictionary.Unit wmsUnitDefinition = WmsUnitCodeDictionary.resolve(stock.getUnitName());
            if (mallUnitDefinition == null || wmsUnitDefinition == null
                    || !mallUnitDefinition.code().equals(wmsUnitDefinition.code())) {
                throw new ServiceException(400, "商城与 WMS 计量单位不一致，SKU=" + entry.getKey()
                        + "，商城单位=" + displayUnit(mallUnit) + "，WMS单位=" + displayUnit(stock.getUnitName())
                        + "；未配置换算规则前不能结算");
            }
            BigDecimal quantity = stock.getQuantity();
            if (quantity == null || quantity.compareTo(BigDecimal.valueOf(entry.getValue())) < 0) {
                throw shortage(entry.getKey(), entry.getValue(), quantity == null ? BigDecimal.ZERO : quantity);
            }
        }
    }

    private ServiceException shortage(Long skuId, int required, BigDecimal current) {
        return new ServiceException(400, "WMS 库存不足，SKU=" + skuId + "，需求=" + required
                + "，现存=" + current.stripTrailingZeros().toPlainString()
                + "；当前库存尚未预占，请稍后重试或联系客服");
    }

    private String mallUnit(ProductSkuRespDTO sku) {
        if (sku.getProperties() == null) return null;
        return sku.getProperties().stream()
                .filter(Objects::nonNull)
                .filter(property -> "单位".equals(property.getPropertyName())
                        || "计量单位".equals(property.getPropertyName()))
                .map(property -> property.getValueName() == null ? null : property.getValueName().strip())
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }

    private String displayUnit(String value) {
        return value == null ? "未配置" : value;
    }
}

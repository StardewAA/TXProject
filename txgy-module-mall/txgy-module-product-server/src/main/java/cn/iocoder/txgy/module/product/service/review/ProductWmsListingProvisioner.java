package cn.iocoder.txgy.module.product.service.review;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.ProductEnterpriseDO;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ProductShopDO;
import cn.iocoder.txgy.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.txgy.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.txgy.module.product.dal.mysql.enterprise.ProductEnterpriseMapper;
import cn.iocoder.txgy.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.iocoder.txgy.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.txgy.module.product.service.shop.ProductShopService;
import cn.iocoder.txgy.module.wms.api.integration.WmsIntegrationApi;
import cn.iocoder.txgy.module.wms.api.integration.WmsUnitCodeDictionary;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationDocumentItemDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsMaterialUpsertReqDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsOrderRequestDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsWarehouseUpsertReqDTO;
import cn.iocoder.txgy.module.wms.api.stock.WmsStockReadApi;
import cn.iocoder.txgy.module.wms.api.stock.dto.WmsWarehouseReadRespDTO;
import cn.iocoder.txgy.module.wms.api.stockapplication.dto.WmsStockApplicationOptionsRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService.invalid;
import static cn.iocoder.txgy.module.wms.enums.integration.WmsIntegrationConstants.*;

/** 商品送审时为尚未映射的商城 SKU 自动建立 WMS 成品主数据。 */
@Component
@RequiredArgsConstructor
public class ProductWmsListingProvisioner {

    private static final String DEFAULT_UNIT = "件";

    private final WmsIntegrationApi wmsIntegrationApi;
    private final WmsStockReadApi wmsStockReadApi;
    private final ProductSkuMapper skuMapper;
    private final ProductSpuMapper spuMapper;
    private final ProductShopService shopService;
    private final ProductEnterpriseMapper enterpriseMapper;

    public void provision(ProductSpuDO product, List<ProductSkuDO> productSkus) {
        if (productSkus == null || productSkus.isEmpty()) throw invalid("商品缺少有效规格");
        List<ProductSkuDO> missing = productSkus.stream().filter(Objects::nonNull)
                .filter(sku -> sku.getWmsSkuId() == null || sku.getWmsWarehouseId() == null
                        || sku.getWmsSkuId() <= 0 || sku.getWmsWarehouseId() <= 0)
                .toList();
        if (missing.isEmpty()) return;

        Long tenantId = TenantContextHolder.getRequiredTenantId();
        ProductShopDO shop = product.getShopId() == null ? null : shopService.getShop(product.getShopId());
        ProductEnterpriseDO enterprise = shop == null || shop.getEnterpriseId() == null
                ? null : enterpriseMapper.selectById(shop.getEnterpriseId());
        if (enterprise == null) throw invalid("商品所属企业不存在，无法建立 WMS 成品主数据");

        try {
            WmsWarehouseReadRespDTO warehouse = resolveFinishedWarehouse(tenantId, enterprise, product);
            long dataVersion = Math.max(1L, ProductSpuReviewService.version(product));
            for (ProductSkuDO sku : missing) upsertMaterial(tenantId, product, sku, dataVersion);

            List<WmsStockApplicationOptionsRespDTO.Sku> wmsSkus = checkedSkus();
            for (ProductSkuDO sku : missing) {
                String materialCode = materialCode(sku);
                List<WmsStockApplicationOptionsRespDTO.Sku> matches = wmsSkus.stream()
                        .filter(Objects::nonNull)
                        .filter(candidate -> materialCode.equals(candidate.getMaterialCode()))
                        .filter(candidate -> MATERIAL_FINISHED.equals(candidate.getMaterialType()))
                        .toList();
                if (matches.size() != 1 || matches.getFirst().getSkuId() == null) {
                    throw new ServiceException(503, "WMS 成品物料已同步但无法取得唯一 SKU 映射：" + materialCode);
                }
                Long wmsSkuId = matches.getFirst().getSkuId();
                skuMapper.updateById(new ProductSkuDO().setId(sku.getId())
                        .setWmsSkuId(wmsSkuId).setWmsWarehouseId(warehouse.getId()));
                sku.setWmsSkuId(wmsSkuId).setWmsWarehouseId(warehouse.getId());
            }
            createOpeningReceipt(tenantId, product, productSkus, missing, wmsSkus, warehouse, dataVersion);
        } catch (ServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ServiceException(503, "WMS 成品物料或成品仓自动创建失败，请稍后重试");
        }
    }

    private void createOpeningReceipt(Long tenantId, ProductSpuDO product, List<ProductSkuDO> allProductSkus,
                                      List<ProductSkuDO> createdSkus,
                                      List<WmsStockApplicationOptionsRespDTO.Sku> wmsSkus,
                                      WmsWarehouseReadRespDTO warehouse, long dataVersion) {
        List<WmsIntegrationDocumentItemDTO> items = new ArrayList<>();
        int lineNo = 1;
        for (ProductSkuDO sku : createdSkus) {
            int initialStock = Objects.requireNonNullElse(sku.getStock(), 0);
            if (initialStock < 0) throw invalid("商品 SKU 期初库存不能小于零");
            if (initialStock == 0) continue;
            String materialCode = materialCode(sku);
            WmsStockApplicationOptionsRespDTO.Sku wmsSku = wmsSkus.stream()
                    .filter(Objects::nonNull).filter(row -> materialCode.equals(row.getMaterialCode()))
                    .filter(row -> Objects.equals(row.getSkuId(), sku.getWmsSkuId()))
                    .findFirst().orElseThrow(() -> new ServiceException(503, "WMS 期初库存 SKU 映射不可读：" + materialCode));
            WmsUnitCodeDictionary.Unit unit = resolveUnit(sku);
            WmsIntegrationDocumentItemDTO item = new WmsIntegrationDocumentItemDTO();
            item.setLineNo(lineNo);
            item.setSourceLineNo(lineNo++);
            item.setMaterialCode(materialCode);
            item.setSkuCode(wmsSku.getSkuCode());
            item.setMaterialType(MATERIAL_FINISHED);
            item.setMaterialNameSnapshot(product.getName());
            item.setSpecificationSnapshot(specification(sku));
            item.setUnitCode(unit.code());
            item.setUnitNameSnapshot(unit.name());
            item.setWarehouseCode(warehouse.getCode());
            item.setPrice(sku.getCostPrice() == null ? null
                    : BigDecimal.valueOf(sku.getCostPrice()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            item.setRequiredQuantity(BigDecimal.valueOf(initialStock));
            item.setQualityRequiredSnapshot(Boolean.FALSE);
            items.add(item);
        }
        if (items.isEmpty()) return;

        String sourceOrderNo = "OPENING-" + product.getId() + "-V" + dataVersion;
        WmsOrderRequestDTO request = new WmsOrderRequestDTO();
        fillBase(request, tenantId, "MALL-OPENING-" + tenantId + "-" + product.getId() + "-" + dataVersion,
                occurredAt(product));
        request.setBusinessChainNo("MALL-PRODUCT-" + tenantId + "-" + product.getId());
        request.setSourceOrderNo(sourceOrderNo);
        request.setShopId(product.getShopId());
        request.setOrderType("MALL_OPENING_RECEIPT");
        request.setEventVersion(dataVersion);
        request.setItems(items);
        var accepted = wmsIntegrationApi.createMallOpeningReceipt(request).getCheckedData();
        if (accepted == null || !Boolean.TRUE.equals(accepted.getAccepted())) {
            throw new ServiceException(503, "WMS 未受理商品期初库存入库申请");
        }

        for (ProductSkuDO sku : createdSkus) {
            if (Objects.requireNonNullElse(sku.getStock(), 0) <= 0) continue;
            if (skuMapper.updateById(new ProductSkuDO().setId(sku.getId()).setStock(0)) != 1) {
                throw new ServiceException(500, "商城期初库存缓存重置失败");
            }
            sku.setStock(0);
        }
        int cachedStock = productSkusStock(allProductSkus);
        if (spuMapper.updateById(new ProductSpuDO().setId(product.getId()).setStock(cachedStock)) != 1) {
            throw new ServiceException(500, "商城商品库存缓存重置失败");
        }
        product.setStock(cachedStock);
    }

    private static int productSkusStock(List<ProductSkuDO> skus) {
        return skus.stream().map(ProductSkuDO::getStock).filter(Objects::nonNull)
                .reduce(0, Math::addExact);
    }

    private WmsWarehouseReadRespDTO resolveFinishedWarehouse(Long tenantId, ProductEnterpriseDO enterprise,
                                                              ProductSpuDO product) {
        List<WmsWarehouseReadRespDTO> enabled = checkedWarehouses().stream()
                .filter(Objects::nonNull)
                .filter(warehouse -> Objects.equals(warehouse.getStatus(), CommonStatusEnum.ENABLE.getStatus()))
                .filter(warehouse -> MATERIAL_FINISHED.equals(warehouse.getWarehouseType()))
                .toList();
        String enterpriseWarehouseCode = "MALL-FG-" + enterprise.getId();
        List<WmsWarehouseReadRespDTO> owned = enabled.stream()
                .filter(warehouse -> enterpriseWarehouseCode.equals(warehouse.getCode())).toList();
        if (owned.size() == 1) return owned.getFirst();
        if (owned.size() > 1) throw new ServiceException(500, "WMS 企业成品仓映射不唯一");
        if (enabled.size() == 1) return enabled.getFirst();

        WmsWarehouseUpsertReqDTO request = new WmsWarehouseUpsertReqDTO();
        fillBase(request, tenantId, "MALL-WAREHOUSE-" + tenantId + "-" + enterprise.getId(), occurredAt(product));
        request.setWarehouseCode(enterpriseWarehouseCode);
        request.setName(enterprise.getName() + "默认成品仓");
        request.setWarehouseType(MATERIAL_FINISHED);
        request.setAddress(enterprise.getAddress());
        request.setPrincipal(enterprise.getContactName());
        request.setPrincipalPhone(enterprise.getContactMobile());
        request.setStatus(STATUS_ENABLED);
        request.setSort(0);
        request.setRemark("商城商品送审自动创建；企业编号=" + enterprise.getId());
        request.setDataVersion(1L);
        var accepted = wmsIntegrationApi.upsertWarehouse(request).getCheckedData();
        if (accepted == null || !Boolean.TRUE.equals(accepted.getAccepted())) {
            throw new ServiceException(503, "WMS 未受理默认成品仓创建请求");
        }
        return checkedWarehouses().stream()
                .filter(warehouse -> enterpriseWarehouseCode.equals(warehouse.getCode()))
                .filter(warehouse -> Objects.equals(warehouse.getStatus(), CommonStatusEnum.ENABLE.getStatus()))
                .filter(warehouse -> MATERIAL_FINISHED.equals(warehouse.getWarehouseType()))
                .findFirst().orElseThrow(() -> new ServiceException(503, "WMS 默认成品仓创建后暂不可读"));
    }

    private void upsertMaterial(Long tenantId, ProductSpuDO product, ProductSkuDO sku, long dataVersion) {
        WmsUnitCodeDictionary.Unit unit = resolveUnit(sku);
        WmsMaterialUpsertReqDTO request = new WmsMaterialUpsertReqDTO();
        fillBase(request, tenantId, "MALL-MATERIAL-" + tenantId + "-" + sku.getId() + "-" + dataVersion,
                occurredAt(product));
        request.setMaterialCode(materialCode(sku));
        request.setName(product.getName());
        request.setMaterialType(MATERIAL_FINISHED);
        request.setSpecification(specification(sku));
        request.setUnitCode(unit.code());
        request.setUnitName(unit.name());
        request.setBarCode(sku.getBarCode());
        request.setQualityRequired(Boolean.FALSE);
        request.setWeight(sku.getWeight() == null ? null : BigDecimal.valueOf(sku.getWeight()));
        request.setStatus(STATUS_ENABLED);
        request.setDataVersion(dataVersion);
        var accepted = wmsIntegrationApi.upsertMaterial(request).getCheckedData();
        if (accepted == null || !Boolean.TRUE.equals(accepted.getAccepted())) {
            throw new ServiceException(503, "WMS 未受理成品物料创建请求：商城 SKU=" + sku.getId());
        }
    }

    private List<WmsWarehouseReadRespDTO> checkedWarehouses() {
        List<WmsWarehouseReadRespDTO> result = wmsStockReadApi.getWarehouses().getCheckedData();
        if (result == null) throw new ServiceException(503, "WMS 仓库主数据返回不完整");
        return result;
    }

    private List<WmsStockApplicationOptionsRespDTO.Sku> checkedSkus() {
        List<WmsStockApplicationOptionsRespDTO.Sku> result = wmsStockReadApi.getSkuOptions().getCheckedData();
        if (result == null) throw new ServiceException(503, "WMS 物料主数据返回不完整");
        return result;
    }

    private static void fillBase(cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationBaseReqDTO request,
                                 Long tenantId, String requestId, LocalDateTime occurredAt) {
        request.setRequestId(requestId);
        request.setTenantId(tenantId);
        request.setSourceModule(SOURCE_MALL);
        request.setOccurredAt(occurredAt);
    }

    private static String materialCode(ProductSkuDO sku) {
        return "MALL-SKU-" + sku.getId();
    }

    private static LocalDateTime occurredAt(ProductSpuDO product) {
        if (product.getUpdateTime() != null) return product.getUpdateTime();
        if (product.getCreateTime() != null) return product.getCreateTime();
        return LocalDateTime.of(2000, 1, 1, 0, 0);
    }

    private static WmsUnitCodeDictionary.Unit resolveUnit(ProductSkuDO sku) {
        if (sku.getProperties() != null) {
            for (ProductSkuDO.Property property : sku.getProperties()) {
                if (property == null || property.getValueName() == null) continue;
                String name = Objects.toString(property.getPropertyName(), "");
                if (!name.contains("计量单位") && !"单位".equals(name.trim())) continue;
                WmsUnitCodeDictionary.Unit unit = WmsUnitCodeDictionary.resolve(property.getValueName());
                if (unit != null) return unit;
                throw invalid("商品 SKU 的计量单位不在 WMS 单位字典中：" + property.getValueName());
            }
        }
        return Objects.requireNonNull(WmsUnitCodeDictionary.resolve(DEFAULT_UNIT));
    }

    private static String specification(ProductSkuDO sku) {
        if (sku.getProperties() == null || sku.getProperties().isEmpty()) return "默认规格";
        String text = sku.getProperties().stream().filter(Objects::nonNull)
                .map(property -> Objects.toString(property.getPropertyName(), "规格") + "："
                        + Objects.toString(property.getValueName(), ""))
                .reduce((left, right) -> left + "；" + right).orElse("默认规格");
        return text.isBlank() ? "默认规格" : text;
    }
}

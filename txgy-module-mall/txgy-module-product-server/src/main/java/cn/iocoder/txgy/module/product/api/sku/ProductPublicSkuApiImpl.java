package cn.iocoder.txgy.module.product.api.sku;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.product.api.property.dto.ProductPropertyValueDetailRespDTO;
import cn.iocoder.txgy.module.product.api.sku.dto.ProductPublicSkuLogisticsRespDTO;
import cn.iocoder.txgy.module.product.api.sku.dto.ProductPublicSkuRespDTO;
import cn.iocoder.txgy.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.txgy.module.product.dal.mysql.sku.ProductPublicSkuMapper;
import cn.iocoder.txgy.module.product.enums.spu.ProductSpuStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** 平台公开 SKU 归属解析实现。 */
@RestController
@Validated
@TenantIgnore
public class ProductPublicSkuApiImpl implements ProductPublicSkuApi {

    static final int MAX_BATCH_SIZE = 200;
    static final int REVIEW_APPROVED = 20;
    static final int ENTERPRISE_APPROVED = 1;
    static final int SHOP_ENABLED = 0;

    @Resource
    private ProductPublicSkuMapper mapper;

    @Override
    @TenantIgnore
    public List<ProductPublicSkuRespDTO> getOwnershipList(Collection<Long> skuIds) {
        return selectControlledRows(skuIds).stream().map(ProductPublicSkuApiImpl::convert).toList();
    }

    @Override
    @TenantIgnore
    public List<ProductPublicSkuLogisticsRespDTO> getLogisticsList(Collection<Long> skuIds) {
        return selectControlledRows(skuIds).stream().map(ProductPublicSkuApiImpl::convertLogistics).toList();
    }

    private List<ProductPublicSkuMapper.PublicSkuRow> selectControlledRows(Collection<Long> skuIds) {
        if (CollUtil.isEmpty(skuIds)) {
            return List.of();
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long skuId : skuIds) {
            if (skuId == null || skuId <= 0) {
                throw new IllegalArgumentException("SKU 编号必须为正整数");
            }
            normalized.add(skuId);
            if (normalized.size() > MAX_BATCH_SIZE) {
                throw new IllegalArgumentException("单次最多解析 " + MAX_BATCH_SIZE + " 个 SKU");
            }
        }
        List<ProductPublicSkuMapper.PublicSkuRow> rows = mapper.selectBySkuIds(normalized);
        if (rows == null) {
            throw new IllegalStateException("平台 SKU 归属查询返回空结果");
        }
        LinkedHashSet<Long> returned = new LinkedHashSet<>();
        rows.forEach(row -> {
            if (row == null || row.getSkuId() == null || !normalized.contains(row.getSkuId())
                    || row.getTenantId() == null || row.getTenantId() <= 0
                    || Objects.equals(row.getTenantId(), 3000L) || !returned.add(row.getSkuId())) {
                throw new IllegalStateException("平台 SKU 归属查询越界或返回重复记录");
            }
        });
        return rows;
    }

    static ProductPublicSkuRespDTO convert(ProductPublicSkuMapper.PublicSkuRow row) {
        String invalidReason = invalidReason(row);
        boolean saleable = invalidReason == null;
        ProductPublicSkuRespDTO result = new ProductPublicSkuRespDTO()
                .setTenantId(row.getTenantId()).setTenantName(row.getTenantName())
                .setEnterpriseId(row.getEnterpriseId()).setEnterpriseName(row.getEnterpriseName())
                .setShopId(row.getShopId()).setShopName(row.getShopName())
                .setSpuId(row.getSpuId()).setSkuId(row.getSkuId())
                .setSaleable(saleable).setInvalidReason(invalidReason);
        if (!saleable) {
            // 商家修改后待审核的名称、规格、图片、价格和库存不属于公开数据。
            // 旧购物车仅获取定位所需的归属标识及失效原因，直到新版本审核通过。
            return result.setSpuName(null).setProperties(List.of()).setPicUrl(null)
                    .setPrice(null).setStock(null);
        }
        return result.setSpuName(row.getSpuName()).setProperties(properties(row.getProperties()))
                .setPicUrl(row.getPicUrl()).setPrice(row.getPrice()).setStock(row.getStock());
    }

    static ProductPublicSkuLogisticsRespDTO convertLogistics(ProductPublicSkuMapper.PublicSkuRow row) {
        String invalidReason = invalidReason(row);
        boolean saleable = invalidReason == null;
        ProductPublicSkuLogisticsRespDTO result = new ProductPublicSkuLogisticsRespDTO()
                .setTenantId(row.getTenantId()).setEnterpriseId(row.getEnterpriseId())
                .setShopId(row.getShopId()).setSpuId(row.getSpuId()).setSkuId(row.getSkuId())
                .setSaleable(saleable).setInvalidReason(invalidReason);
        if (!saleable) {
            return result;
        }
        return result.setPrice(row.getPrice()).setStock(row.getStock())
                .setDeliveryTypes(row.getDeliveryTypes()).setDeliveryTemplateId(row.getDeliveryTemplateId())
                .setWeight(row.getWeight()).setVolume(row.getVolume());
    }

    private static List<ProductPropertyValueDetailRespDTO> properties(List<ProductSkuDO.Property> properties) {
        if (CollUtil.isEmpty(properties)) {
            return List.of();
        }
        return properties.stream().map(property -> new ProductPropertyValueDetailRespDTO()
                .setPropertyId(property.getPropertyId()).setPropertyName(property.getPropertyName())
                .setValueId(property.getValueId()).setValueName(property.getValueName())).toList();
    }

    static String invalidReason(ProductPublicSkuMapper.PublicSkuRow row) {
        if (Boolean.TRUE.equals(row.getSkuDeleted())) {
            return "商品规格已失效";
        }
        if (row.getSpuId() == null || Boolean.TRUE.equals(row.getSpuDeleted())) {
            return "商品已删除";
        }
        if (row.getShopId() == null || Boolean.TRUE.equals(row.getShopDeleted())
                || !Objects.equals(row.getShopStatus(), SHOP_ENABLED)) {
            return "店铺已关闭";
        }
        if (row.getEnterpriseId() == null || Boolean.TRUE.equals(row.getEnterpriseDeleted())
                || !Objects.equals(row.getEnterpriseAuditStatus(), ENTERPRISE_APPROVED)) {
            return "供应企业资质无效";
        }
        if (!Objects.equals(row.getSpuStatus(), ProductSpuStatusEnum.ENABLE.getStatus())) {
            return "商品已下架";
        }
        if (!Objects.equals(row.getReviewStatus(), REVIEW_APPROVED)
                || row.getApprovedVersion() == null
                || !Objects.equals(row.getApprovedVersion(), row.getContentVersion())) {
            return "商品审核未通过或已变更";
        }
        if (row.getStock() == null || row.getStock() <= 0) {
            return "库存不足";
        }
        if (row.getPrice() == null || row.getPrice() <= 0) {
            return "商品价格无效";
        }
        return null;
    }

}

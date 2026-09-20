package cn.iocoder.txgy.module.product.service.review;

import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.txgy.module.product.dal.dataobject.spu.ProductSpuDO;
import java.util.*;

/** 仅经营资料；不快照回写库存、销量、ERP/WMS 映射或审核状态。 */
public final class ProductReviewSnapshot {
    private ProductReviewSnapshot() {}
    public static String of(ProductSpuDO p, List<ProductSkuDO> skus) {
        var content = new TreeMap<String, Object>();
        content.put("shopId", p.getShopId()); content.put("name", p.getName());
        content.put("keyword", p.getKeyword()); content.put("introduction", p.getIntroduction());
        content.put("description", p.getDescription()); content.put("categoryId", p.getCategoryId());
        content.put("brandId", p.getBrandId()); content.put("picUrl", p.getPicUrl());
        content.put("sliderPicUrls", p.getSliderPicUrls()); content.put("sort", p.getSort());
        content.put("specType", p.getSpecType()); content.put("deliveryTypes", p.getDeliveryTypes());
        content.put("deliveryTemplateId", p.getDeliveryTemplateId()); content.put("giveIntegral", p.getGiveIntegral());
        content.put("subCommissionType", p.getSubCommissionType()); content.put("virtualSalesCount", p.getVirtualSalesCount());
        content.put("skus", skus.stream().sorted(Comparator.comparing(ProductSkuDO::getId)).map(s -> {
            var sku = new TreeMap<String, Object>();
            sku.put("id", s.getId()); sku.put("price", s.getPrice());
            sku.put("marketPrice", s.getMarketPrice()); sku.put("costPrice", s.getCostPrice());
            sku.put("barCode", s.getBarCode()); sku.put("picUrl", s.getPicUrl());
            sku.put("weight", s.getWeight()); sku.put("volume", s.getVolume());
            sku.put("firstBrokeragePrice", s.getFirstBrokeragePrice()); sku.put("secondBrokeragePrice", s.getSecondBrokeragePrice());
            sku.put("properties", s.getProperties() == null ? null : s.getProperties().stream()
                    .sorted(Comparator.comparing(ProductSkuDO.Property::getPropertyId)).toList());
            return sku;
        }).toList());
        return JsonUtils.toJsonString(content);
    }
}

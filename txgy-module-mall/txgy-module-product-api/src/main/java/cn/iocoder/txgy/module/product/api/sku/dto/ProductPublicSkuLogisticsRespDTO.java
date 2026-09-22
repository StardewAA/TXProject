package cn.iocoder.txgy.module.product.api.sku.dto;

import lombok.Data;

import java.util.List;

/**
 * 平台跨租户结算使用的商品物流快照。
 *
 * <p>调用方只能提交 SKU 编号。本 DTO 中的归属、价格、库存和物流字段均由商品服务从
 * 门户开放目录内重新解析，不能由用户端提供或覆盖。不可售记录只返回归属和失效原因，
 * 不暴露价格、库存或物流配置。</p>
 */
@Data
public class ProductPublicSkuLogisticsRespDTO {

    private Long tenantId;
    private Long enterpriseId;
    private Long shopId;
    private Long spuId;
    private Long skuId;

    /** 单位：分。 */
    private Integer price;
    private Integer stock;

    private List<Integer> deliveryTypes;
    private Long deliveryTemplateId;
    /** 单位：kg。 */
    private Double weight;
    /** 单位：m^3。 */
    private Double volume;

    private Boolean saleable;
    private String invalidReason;

}

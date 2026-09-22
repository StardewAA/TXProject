package cn.iocoder.txgy.module.product.api.sku.dto;

import cn.iocoder.txgy.module.product.api.property.dto.ProductPropertyValueDetailRespDTO;
import lombok.Data;

import java.util.List;

/**
 * 平台购物车使用的 SKU 公开归属快照。
 *
 * <p>这里只包含展示和可售校验所需字段，不返回成本价、WMS 映射或企业私有信息。</p>
 */
@Data
public class ProductPublicSkuRespDTO {

    private Long tenantId;
    private String tenantName;
    private Long enterpriseId;
    private String enterpriseName;
    private Long shopId;
    private String shopName;
    private Long spuId;
    private String spuName;
    private Long skuId;
    private List<ProductPropertyValueDetailRespDTO> properties;
    private String picUrl;
    /** 单位：分。 */
    private Integer price;
    /** 仅供服务端结算前可售校验，不是门户公开库存字段。 */
    private Integer stock;
    private Boolean saleable;
    private String invalidReason;

}

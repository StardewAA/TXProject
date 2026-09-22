package cn.iocoder.txgy.module.product.api.shop.dto;

import lombok.Data;

/** 后台经营账号可访问的有效企业店铺。 */
@Data
public class ProductAdminAuthorizedShopRespDTO {

    private Long id;
    private Long enterpriseId;
    private String name;
    private String logoUrl;
    private Integer status;

}

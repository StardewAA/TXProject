package cn.iocoder.txgy.module.product.controller.app.inquiry;

import lombok.Data;

/** 采购方创建定向询价时可邀请的有效供应店铺。 */
@Data
public class InquirySupplierShopOptionRespVO {
    private Long id;
    private Long enterpriseId;
    private String name;
    private String enterpriseName;
    private String logoUrl;
}

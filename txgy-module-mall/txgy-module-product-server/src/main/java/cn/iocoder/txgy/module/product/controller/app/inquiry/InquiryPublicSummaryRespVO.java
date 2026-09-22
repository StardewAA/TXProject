package cn.iocoder.txgy.module.product.controller.app.inquiry;

import lombok.Data;

/** 询价市场公开摘要。该模型不得加入联系人、电话、会员编号或完整地址。 */
@Data
public class InquiryPublicSummaryRespVO {
    private Long id;
    /** 询价所属真实租户，仅供已授权供应后台识别跨租户机会。 */
    private Long inquiryTenantId;
    private String no;
    private String title;
    private String enterpriseName;
    /** 仅市级等粗粒度区域；无法可靠识别时返回统一隐藏文案。 */
    private String deliveryArea;
    private String itemSummary;
    /** PLATFORM_PUBLIC / TENANT_PUBLIC / INVITED_SHOPS。 */
    private String visibility;
    private Long deadline;
    private Long expectedDate;
    /** 1 报价中，2 已截止，3 已关闭，4 已成交。 */
    private Integer status;
    private Long quoteCount;
    private Integer itemCount;
    /** 仅表示该询价仍处于可报价时间窗；实际报价还需通过企业和店铺授权校验。 */
    private Boolean canQuote;
}

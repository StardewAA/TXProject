package cn.iocoder.txgy.module.product.api.inquiry.dto;

import lombok.Data;

import java.util.List;

/** 已采纳报价的不可变成交快照。 */
@Data
public class InquiryAcceptedQuoteRespDTO {

    private Long quoteId;
    private Long inquiryId;
    /** 采购订单归属企业。 */
    private Long buyerEnterpriseId;
    /** 本次创建采购订单的实际操作会员，而不是询价最初发布人。 */
    private Long buyerMemberId;
    private Long shopId;
    private Long version;
    private String title;
    private List<Item> items;

    @Data
    public static class Item {
        private Long inquiryItemId;
        private Long skuId;
        private Integer count;
        /** 单价，单位：分。 */
        private Integer unitPrice;
    }

}

package cn.iocoder.txgy.module.product.controller.app.inquiry;

import lombok.Data;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

@Data
public class InquiryQuoteRespVO {
    private Long id;
    private Long inquiryId;
    private String inquiryNo;
    private String inquiryTitle;
    private Long shopId;
    /** 报价供应店铺所属真实租户。 */
    private Long supplierTenantId;
    private String shopName;
    /** 0 草稿，1 已提交，2 已采纳，3 未采纳，4 已撤回。 */
    private Integer status;
    private Long version;
    private Long goodsPrice;
    private Integer freightPrice;
    private Long totalPrice;
    private Long deliveryDate;
    private Long validUntil;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime submittedAt;
    private LocalDateTime acceptedAt;
    private List<Item> items;
    /** 兼容能力字段：当前访问者可继续编辑自己的报价。 */
    private Boolean canQuote;
    private Boolean canEdit;
    private Boolean canSubmit;
    private Boolean canWithdraw;
    private Boolean canMessage;
    /** 旧会员供应端仅可读取历史报价，销售经营请迁移至 system_user 后台。 */
    private Boolean legacySalesReadOnly;
    /** 旧入口只读或兼容状态的明确提示。 */
    private String salesWriteNotice;
    /** 当前访问者可采纳此报价。 */
    private Boolean canAccept;
    /** 当前访问者可将已采纳报价转为商城交易。 */
    private Boolean canConvert;

    @Data
    public static class Item {
        private Long inquiryItemId;
        private Long skuId;
        private String productName;
        private String skuName;
        private String skuSpecification;
        /** 兼容字段，值与 productName 相同。 */
        private String name;
        /** 兼容字段，值与 skuSpecification 相同。 */
        private String specification;
        private BigDecimal quantity;
        private String unit;
        private Integer count;
        private Integer unitPrice;
        private Long totalPrice;
    }
}

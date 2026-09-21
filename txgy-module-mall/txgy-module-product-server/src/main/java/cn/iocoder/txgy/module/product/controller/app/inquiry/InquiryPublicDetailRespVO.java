package cn.iocoder.txgy.module.product.controller.app.inquiry;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 询价市场公开详情。 */
@Data
public class InquiryPublicDetailRespVO {
    private InquiryPublicSummaryRespVO order;
    /** 匿名公开详情固定为空；仅供应商授权机会详情返回脱敏后的采购补充说明。 */
    private String remark;
    private List<Item> items;

    @Data
    public static class Item {
        private Long id;
        private String name;
        private String code;
        private String category;
        private String specification;
        private BigDecimal quantity;
        private String unit;
        /** 匿名公开详情固定为空；仅供应商授权机会详情返回脱敏后的行备注。 */
        private String remark;
        private String picUrl;
    }
}

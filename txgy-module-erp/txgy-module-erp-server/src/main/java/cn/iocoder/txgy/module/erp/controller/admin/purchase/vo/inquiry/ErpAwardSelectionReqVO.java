package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.inquiry;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ErpAwardSelectionReqVO {
    @NotNull(message = "询价单不能为空")
    private Long inquiryId;
    @Valid
    @NotEmpty(message = "定标明细不能为空")
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "询价明细不能为空")
        private Long inquiryItemId;
        @NotNull(message = "报价明细不能为空")
        private Long quoteItemId;
        @NotNull(message = "中标数量不能为空")
        private BigDecimal awardCount;
        private String awardReason;
    }
}

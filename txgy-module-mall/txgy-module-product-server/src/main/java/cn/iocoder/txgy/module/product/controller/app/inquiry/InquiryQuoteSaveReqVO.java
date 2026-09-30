package cn.iocoder.txgy.module.product.controller.app.inquiry;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class InquiryQuoteSaveReqVO {
    private Long id;

    @NotNull(message = "询价单不能为空")
    private Long inquiryId;

    @NotNull(message = "报价店铺不能为空")
    private Long shopId;

    /** 新建传 0，更新传服务端返回的当前版本。 */
    @NotNull(message = "报价版本不能为空")
    @Min(value = 0, message = "报价版本不正确")
    private Long version;

    @NotBlank(message = "幂等键不能为空")
    @Size(min = 8, max = 64, message = "幂等键长度须为 8 至 64 个字符")
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "幂等键仅支持字母、数字、下划线和短横线")
    private String requestKey;

    @Size(max = 1000, message = "报价说明不能超过 1000 个字符")
    private String remark;

    @NotNull(message = "预计运费不能为空")
    @Min(value = 0, message = "预计运费不能小于 0")
    private Integer freightPrice;

    @NotNull(message = "预计送达日期不能为空")
    private Long deliveryDate;

    @NotNull(message = "报价有效期不能为空")
    private Long validUntil;

    @NotEmpty(message = "请填写报价明细")
    @Size(max = 200, message = "报价明细不能超过 200 项")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "询价明细不能为空")
        private Long inquiryItemId;

        @NotNull(message = "商品规格不能为空")
        private Long skuId;

        @NotNull(message = "数量不能为空")
        @Min(value = 1, message = "数量必须为正整数")
        private Integer count;

        @NotNull(message = "单价不能为空")
        @Min(value = 1, message = "单价必须大于 0 分")
        private Integer unitPrice;
    }
}

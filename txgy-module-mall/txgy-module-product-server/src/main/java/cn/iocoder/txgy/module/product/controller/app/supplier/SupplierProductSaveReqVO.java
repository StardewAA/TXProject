package cn.iocoder.txgy.module.product.controller.app.supplier;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

/** 白名单：不接收店铺归属变更、销量、分销、WMS映射或既有库存覆盖。 */
@Data
public class SupplierProductSaveReqVO {
    private Long id;
    private Boolean specType;
    @NotNull private Long shopId;
    @NotBlank @Size(max=100) private String name;
    @NotBlank @Size(max=255) private String keyword;
    @NotBlank @Size(max=255) private String introduction;
    /** 纯文本；编辑不传时保留原详情，不接受富文本HTML。 */
    @Size(max=10000) private String descriptionText;
    @NotNull private Long categoryId;
    @NotNull private Long brandId;
    @NotBlank @Size(max=512) private String picUrl;
    /** 不传时编辑保留原轮播图；传入时第一张须为主图。 */
    @Size(min=1,max=10) private List<@NotBlank @Size(max=512) String> sliderPicUrls;
    @NotNull private Long deliveryTemplateId;
    @NotEmpty @Size(max=100) @Valid private List<Sku> skus;
    @Data public static class Sku {
        @Size(min=1,max=3) @Valid private List<Property> properties;
        private Long id;
        /** null编辑时保留原图；空字符串使用本次保存的商品主图。 */
        @Size(max=512) private String picUrl;
        @NotNull @Min(1) @Max(1000000000) private Integer price;
        @NotNull @Min(0) @Max(1000000000) private Integer marketPrice;
        /** 仅创建商品时可填写；编辑必须为空。 */
        @Min(0) @Max(1000000) private Integer initialStock;
        @Size(max=64) private String barCode;
        @NotNull @DecimalMin("0.0") @DecimalMax("1000000") private Double weight;
        @NotNull @DecimalMin("0.0") @DecimalMax("1000000") private Double volume;
    }
    @Data public static class Property {
        @NotNull @Positive private Long propertyId;
        @NotNull @Positive private Long valueId;
    }
}

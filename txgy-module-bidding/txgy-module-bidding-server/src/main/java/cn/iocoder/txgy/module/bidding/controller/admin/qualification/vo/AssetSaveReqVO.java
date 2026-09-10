package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import lombok.Data;


/**
 * 资产装备要素录入请求。
 *
 * <p>规范出处：v3.3 表 15-5。
 */
@Data
public class AssetSaveReqVO {
    @jakarta.validation.constraints.NotNull(message = "企业不能为空")
    private Long entId;
    @jakarta.validation.constraints.NotBlank(message = "类型不能为空")
    private String assetType;
    @jakarta.validation.constraints.NotBlank(message = "名称不能为空")
    private String name;
    private String spec;
    private java.math.BigDecimal quantity;
    private java.math.BigDecimal valueAmount;
    private Short statYear;
    private String evidenceFileIds;
}

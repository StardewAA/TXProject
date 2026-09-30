package cn.iocoder.txgy.module.product.controller.app.portal.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "用户 APP - 平台门户商品分页请求")
@Data
@EqualsAndHashCode(callSuper = true)
public class AppPortalProductPageReqVO extends PageParam {

    @Schema(description = "公开商品关键字", example = "防腐")
    @Size(max = 100, message = "商品关键字不能超过 100 个字符")
    private String keyword;

    @Schema(description = "商品分类编号", example = "10")
    @Positive(message = "商品分类编号必须大于 0")
    private Long categoryId;

    @Schema(description = "跨租户分类名称；同时匹配商品分类及其一级分类", example = "防腐材料")
    @Size(max = 50, message = "商品分类名称不能超过 50 个字符")
    private String categoryName;

    @Schema(description = "平台公开店铺编号", example = "401")
    @Positive(message = "店铺编号必须大于 0")
    private Long shopId;

    @Schema(description = "排序字段：price 或 sales", example = "price")
    @Pattern(regexp = "price|sales", message = "商品排序字段无效")
    private String sortField;

    @Schema(description = "是否升序；未指定时按字段默认顺序", example = "true")
    private Boolean sortAsc;
}

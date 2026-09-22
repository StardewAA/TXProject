package cn.iocoder.txgy.module.product.controller.app.portal.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
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
}

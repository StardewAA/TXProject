package cn.iocoder.txgy.module.trade.controller.app.cart.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Collection;

@Schema(description = "用户 App - 更新平台购物车选中状态 Request VO")
@Data
public class AppPlatformCartUpdateSelectedReqVO {

    @NotEmpty(message = "购物车项编号不能为空")
    @Size(max = 200, message = "单次最多操作 200 个购物车项")
    private Collection<@NotNull @Min(1) Long> ids;

    @NotNull(message = "是否选中不能为空")
    private Boolean selected;

}

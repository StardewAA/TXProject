package cn.iocoder.txgy.module.trade.controller.app.comparison.vo;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class AppProductComparisonQuoteReqVO {
    @NotNull @Size(min = 1, max = 4)
    private List<@NotNull @Positive Long> skuIds;
    @NotNull @Min(1) @Max(999)
    private Integer quantity;
    @Positive
    private Long addressId;
}

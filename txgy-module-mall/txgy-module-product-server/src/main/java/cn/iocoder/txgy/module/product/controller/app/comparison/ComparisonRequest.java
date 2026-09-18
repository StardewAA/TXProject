package cn.iocoder.txgy.module.product.controller.app.comparison;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

/** Only selection/preferences are accepted. Prices, ownership and standard claims are server-owned. */
@Data
public class ComparisonRequest {
    @NotNull @Size(max = 4)
    private List<@NotNull @Positive Long> skuIds;
    @NotNull @Min(1) @Max(999)
    private Integer quantity = 1;
    @NotNull @Pattern(regexp = "PRICE|DELIVERY|BALANCED")
    private String preference = "PRICE";
    @Min(0)
    private Long version;
}

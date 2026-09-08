package cn.iocoder.txgy.module.training.controller.admin.analytics.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class PromotionRequests {
    private PromotionRequests() {}

    public record Profile(@NotNull Long userId, @NotBlank String currentLevel,
                          @NotNull LocalDate levelSince, String jobType) {}

    public record Metric(@NotNull Long userId, @NotBlank String ruleKey,
                         @NotNull BigDecimal metricValue, @NotBlank String sourceModule,
                         String evidence) {}

    public record Rule(@NotNull Long id, @NotBlank String operator,
                       @NotNull BigDecimal ruleValue, @NotNull Boolean hard,
                       @NotNull Integer status) {}
}

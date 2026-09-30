package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.plan;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - ERP采购计划新增/修改 Request VO")
@Data
public class ErpPurchasePlanSaveReqVO {
    private Long id;
    @NotEmpty(message = "计划名称不能为空")
    private String planName;
    private String planPeriod;
    private Long departmentId;
    private Long requesterId;
    @NotNull(message = "编制时间不能为空")
    private LocalDateTime planTime;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private BigDecimal budgetAmount;
    private String purpose;
    private String fileUrl;
    private String remark;
    @Valid
    @NotEmpty(message = "采购计划明细不能为空")
    private List<Item> items;

    @Data
    public static class Item {
        private Long id;
        @NotNull(message = "产品不能为空")
        private Long productId;
        @NotNull(message = "计划数量不能为空")
        private BigDecimal count;
        private BigDecimal targetPrice;
        private BigDecimal amount;
        private LocalDateTime requiredDate;
        private String remark;
    }
}

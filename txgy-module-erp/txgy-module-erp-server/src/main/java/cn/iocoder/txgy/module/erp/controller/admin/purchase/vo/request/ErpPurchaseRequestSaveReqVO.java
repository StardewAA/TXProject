package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - ERP采购申请新增/修改 Request VO")
@Data
public class ErpPurchaseRequestSaveReqVO {
    private Long id;
    @NotNull(message = "申请时间不能为空")
    private LocalDateTime requestTime;
    private LocalDateTime requiredDate;
    private Long departmentId;
    private BigDecimal budgetAmount;
    private String purpose;
    private String fileUrl;
    private String remark;
    @Valid
    @NotEmpty(message = "采购申请明细不能为空")
    private List<Item> items;

    @Data
    public static class Item {
        private Long id;
        @NotNull(message = "产品不能为空")
        private Long productId;
        @NotNull(message = "产品单位不能为空")
        private Long productUnitId;
        @NotNull(message = "申请数量不能为空")
        private BigDecimal count;
        private BigDecimal targetPrice;
        private String remark;
    }
}

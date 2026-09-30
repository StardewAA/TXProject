package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.inspection;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - ERP到货检验新增/修改 Request VO")
@Data
public class ErpArrivalInspectionSaveReqVO {
    private Long id;
    @Schema(description = "采购订单编号")
    private Long purchaseOrderId;
    @Schema(description = "采购订单号")
    private String purchaseOrderNo;
    @Schema(description = "采购入库编号")
    private Long purchaseInId;
    @Schema(description = "供应商编号")
    private Long supplierId;
    @NotNull(message = "检验时间不能为空")
    private LocalDateTime inspectTime;
    @Schema(description = "检验人编号")
    private Long inspectorId;
    @NotNull(message = "检验方式不能为空")
    @Schema(description = "检验方式：1 全检，2 抽检")
    private Integer inspectType;
    @Schema(description = "检验结论：1 合格，2 让步接收，3 退货")
    private Integer result;
    @Schema(description = "检验结论说明")
    private String conclusion;
    @Schema(description = "来源")
    private String origin;
    @Schema(description = "附件地址")
    private String fileUrl;
    private String remark;
    @Valid
    @NotEmpty(message = "到货检验明细不能为空")
    private List<Item> items;

    @Data
    public static class Item {
        private Long id;
        @NotNull(message = "产品不能为空")
        private Long productId;
        @NotNull(message = "到货数量不能为空")
        private BigDecimal deliveryCount;
        private BigDecimal qualifiedCount;
        private BigDecimal unqualifiedCount;
        @Schema(description = "检验结论：1 合格，2 不合格")
        private Integer result;
        private String remark;
    }
}

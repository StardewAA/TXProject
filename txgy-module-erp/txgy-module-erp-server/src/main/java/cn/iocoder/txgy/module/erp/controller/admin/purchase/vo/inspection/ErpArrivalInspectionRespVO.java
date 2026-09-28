package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.inspection;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ErpArrivalInspectionRespVO {
    private Long id;
    private String no;
    @Schema(description = "采购订单编号")
    private Long purchaseOrderId;
    @Schema(description = "采购订单号")
    private String purchaseOrderNo;
    @Schema(description = "采购入库编号")
    private Long purchaseInId;
    @Schema(description = "供应商编号")
    private Long supplierId;
    private LocalDateTime inspectTime;
    @Schema(description = "检验人编号")
    private Long inspectorId;
    @Schema(description = "检验方式：1 全检，2 抽检")
    private Integer inspectType;
    @Schema(description = "检验结论：1 合格，2 让步接收，3 退货")
    private Integer result;
    @Schema(description = "检验结论说明")
    private String conclusion;
    @Schema(description = "来源")
    private String origin;
    private Integer status;
    @Schema(description = "附件地址")
    private String fileUrl;
    private String remark;
    private String creator;
    private String creatorName;
    private LocalDateTime createTime;
    private String productNames;
    private List<Item> items;

    /**
     * 检验方式名（供前端直接展示，避免前端重复维护枚举）
     */
    public String getInspectTypeName() {
        if (inspectType == null) {
            return null;
        }
        return switch (inspectType) {
            case 1 -> "全检";
            case 2 -> "抽检";
            default -> null;
        };
    }

    /**
     * 检验结论名（供前端直接展示，避免前端重复维护枚举）
     */
    public String getResultName() {
        if (result == null) {
            return null;
        }
        return switch (result) {
            case 1 -> "合格";
            case 2 -> "让步接收";
            case 3 -> "退货";
            default -> null;
        };
    }

    @Data
    public static class Item {
        private Long id;
        private Long productId;
        private String productName;
        private Long productUnitId;
        private String productUnitName;
        private BigDecimal deliveryCount;
        private BigDecimal qualifiedCount;
        private BigDecimal unqualifiedCount;
        @Schema(description = "检验结论：1 合格，2 不合格")
        private Integer result;
        private String remark;
    }
}

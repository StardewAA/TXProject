package cn.iocoder.txgy.module.erp.controller.admin.integration.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - ERP 供应链协同单详情 Response VO")
@Data
public class ErpWmsCollaborationBDetailRespVO {

    private Long id;
    private String collaborationNo;
    private String collaborationType;
    private String businessChainNo;
    private String sourceModule;
    private String sourceOrderNo;
    private String sourceEnterpriseCode;
    private String targetEnterpriseCode;
    private String currency;
    private String sourceProductionOrderNo;
    private String sourceCollaborationGNo;
    private String sourceSaleOrderNo;
    private String technicalStatus;
    private String businessStatus;
    private Long eventVersion;
    private LocalDateTime requiredAt;
    private String reason;
    private Long confirmUserId;
    private String confirmUserName;
    private LocalDateTime confirmTime;

    @Schema(description = "关联采购订单编号")
    private Long purchaseOrderId;
    @Schema(description = "关联采购订单单号")
    private String purchaseOrderNo;
    @Schema(description = "关联采购订单当前审批状态快照（实时查询）")
    private Integer purchaseOrderStatus;

    private List<Item> items;
    private List<ConfirmLog> confirmLogs;

    @Schema(description = "管理后台 - ERP 协同单明细 Response VO")
    @Data
    public static class Item {
        private Integer lineNo;
        private Integer sourceLineNo;
        private String materialCode;
        private String skuCode;
        private String materialNameSnapshot;
        private String specificationSnapshot;
        private String unitCode;
        private String unitNameSnapshot;
        private BigDecimal shortageQuantity;
        private BigDecimal referencePrice;
        private BigDecimal taxRate;
        private String deliveryAddress;
    }

    @Schema(description = "管理后台 - ERP 协同单确认日志 Response VO")
    @Data
    public static class ConfirmLog {
        private String action;
        private String reason;
        private Long confirmUserId;
        private String confirmUserName;
        private LocalDateTime createTime;
    }
}

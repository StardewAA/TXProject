package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.supplier.credit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - ERP 供应商资信 Response VO")
@Data
public class ErpSupplierCreditRespVO {

    private Long supplierId;
    private String authStatus;
    private String qualificationStatus;
    private String riskLevel;
    private String purchaseLimitStatus;
    private String freezeReason;
    private Long snapshotVersion;
    private LocalDateTime lastSyncTime;
    private List<Qualification> qualifications;
    private List<AdmissionCategory> admissionCategories;
    private List<RiskEvent> riskEvents;

    @Data
    public static class Qualification {
        private Long id;
        private String qualificationName;
        private String qualificationNo;
        private LocalDateTime validFrom;
        private LocalDateTime validTo;
        private String status;
        private Boolean requiredFlag;
        private String remark;
    }

    @Data
    public static class AdmissionCategory {
        private Long id;
        private Long categoryId;
        private String categoryName;
        private LocalDateTime validFrom;
        private LocalDateTime validTo;
        private String status;
        private String remark;
    }

    @Data
    public static class RiskEvent {
        private Long id;
        private String eventType;
        private String status;
        private Integer severity;
        private Boolean blocksPurchase;
        private LocalDateTime occurredAt;
        private LocalDateTime resolvedAt;
        private String description;
        private String resolution;
    }
}

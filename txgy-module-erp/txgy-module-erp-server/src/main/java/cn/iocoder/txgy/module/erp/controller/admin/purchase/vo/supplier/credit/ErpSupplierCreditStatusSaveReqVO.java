package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.supplier.credit;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - ERP 供应商资信状态更新 Request VO")
@Data
public class ErpSupplierCreditStatusSaveReqVO {

    @NotNull(message = "供应商编号不能为空")
    private Long supplierId;
    @NotNull(message = "数据版本不能为空")
    private Long snapshotVersion;
    private String authStatus;
    private String qualificationStatus;
    private String riskLevel;
    private String purchaseLimitStatus;
    private String freezeReason;
}

package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.supplier.credit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "管理后台 - ERP 供应商采购资格检查 Response VO")
@Data
public class ErpSupplierQualificationCheckRespVO {

    private boolean allowed;
    private String authStatus;
    private String purchaseLimitStatus;
    private List<String> warnings = new ArrayList<>();
    private List<String> blockingReasons = new ArrayList<>();

    public ErpSupplierQualificationCheckRespVO addWarning(String warning) {
        warnings.add(warning);
        return this;
    }

    public ErpSupplierQualificationCheckRespVO addBlockingReason(String reason) {
        blockingReasons.add(reason);
        return this;
    }
}

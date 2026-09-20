package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.supplier.credit;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - ERP 供应商准入品类更新 Request VO")
@Data
public class ErpSupplierAdmissionCategorySaveReqVO {

    @NotNull(message = "供应商编号不能为空")
    private Long supplierId;
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        private Long categoryId;
        private String categoryName;
        private LocalDateTime validFrom;
        private LocalDateTime validTo;
        private String status;
        private String remark;
    }
}

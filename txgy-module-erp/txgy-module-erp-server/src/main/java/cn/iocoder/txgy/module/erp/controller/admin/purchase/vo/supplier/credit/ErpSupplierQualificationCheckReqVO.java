package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.supplier.credit;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - ERP 供应商采购资格检查 Request VO")
@Data
public class ErpSupplierQualificationCheckReqVO {

    @NotNull(message = "供应商编号不能为空")
    private Long supplierId;
    @NotEmpty(message = "产品列表不能为空")
    private List<Long> productIds;
}

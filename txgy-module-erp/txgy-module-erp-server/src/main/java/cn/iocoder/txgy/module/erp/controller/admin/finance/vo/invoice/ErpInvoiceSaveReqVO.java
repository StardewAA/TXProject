package cn.iocoder.txgy.module.erp.controller.admin.finance.vo.invoice;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - ERP发票新增/修改 Request VO")
@Data
public class ErpInvoiceSaveReqVO {
    private Long id;
    /**
     * 发票类型：1 增值税专用发票 2 增值税普通发票 3 电子发票
     */
    private Integer invoiceType;
    @NotBlank(message = "发票号码不能为空")
    private String invoiceNo;
    private String invoiceCode;
    private String title;
    /**
     * 业务类型：1 采购进项 2 销售销项
     */
    private Integer bizType;
    private Long supplierId;
    private Long customerId;
    private String orderNo;
    private LocalDateTime invoiceDate;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal taxRate;
    private String fileUrl;
    private String remark;
}

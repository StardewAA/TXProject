package cn.iocoder.txgy.module.erp.controller.admin.finance.vo.invoice;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ErpInvoiceRespVO {
    private Long id;
    private String no;
    private Integer invoiceType;
    private String invoiceNo;
    private String invoiceCode;
    private String title;
    private Integer bizType;
    private Long supplierId;
    private String supplierName;
    private Long customerId;
    private String customerName;
    private String orderNo;
    private LocalDateTime invoiceDate;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal taxRate;
    @Schema(description = "发票状态：0 待认证 1 已认证 2 已入账 3 已作废")
    private Integer status;
    private LocalDateTime certifyTime;
    private String fileUrl;
    private String remark;
    private String creator;
    private String creatorName;
    private LocalDateTime createTime;
}

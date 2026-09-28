package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.evaluation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ErpSupplierEvaluationRespVO {
    private Long id;
    private String no;
    private Long supplierId;
    private String supplierName;
    private String evalPeriod;
    private LocalDateTime evalDate;
    private BigDecimal qualityScore;
    private BigDecimal deliveryScore;
    private BigDecimal priceScore;
    private BigDecimal serviceScore;
    private BigDecimal totalScore;
    @Schema(description = "评价等级：A / B / C / D")
    private String grade;
    private Long evaluatorId;
    private String comment;
    private Integer status;
    private String remark;
    private String creator;
    private String creatorName;
    private LocalDateTime createTime;
}

package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.evaluation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - ERP供应商评价新增/修改 Request VO")
@Data
public class ErpSupplierEvaluationSaveReqVO {
    private Long id;
    @NotNull(message = "供应商不能为空")
    private Long supplierId;
    /**
     * 评价周期
     */
    private String evalPeriod;
    private LocalDateTime evalDate;
    /**
     * 质量得分
     */
    private BigDecimal qualityScore;
    /**
     * 交付得分
     */
    private BigDecimal deliveryScore;
    /**
     * 价格得分
     */
    private BigDecimal priceScore;
    /**
     * 服务得分
     */
    private BigDecimal serviceScore;
    /**
     * 综合得分；不传时后端按权重自动计算
     */
    private BigDecimal totalScore;
    /**
     * 评价等级：A / B / C / D；不传时后端按综合得分自动评定
     */
    private String grade;
    private Long evaluatorId;
    /**
     * 评价说明
     */
    private String comment;
    private String remark;
}

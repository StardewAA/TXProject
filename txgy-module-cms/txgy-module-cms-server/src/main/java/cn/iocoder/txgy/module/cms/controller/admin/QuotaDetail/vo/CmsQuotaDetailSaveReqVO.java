package cn.iocoder.txgy.module.cms.controller.admin.QuotaDetail.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Schema(description = "管理后台 - 定额明细表创建/修改 Request VO")
@Data
public class CmsQuotaDetailSaveReqVO {

    @Schema(description = "主键（更新时必传）")
    private Long detailId;
    @NotNull(message = "关联主表ID不能为空")
    @Schema(description = "关联主表ID")
    private Long quotaId;
    @NotNull(message = "定额编号不能为空")
    @Schema(description = "定额编号")
    private String quotaNo;
    @NotNull(message = "项目名称不能为空")
    @Schema(description = "项目名称")
    private String itemName;
    @NotNull(message = "计量单位不能为空")
    @Schema(description = "计量单位")
    private String unit;
    @NotNull(message = "基价（元）不能为空")
    @Schema(description = "基价（元）")
    private BigDecimal basePrice;
    @NotNull(message = "人工费不能为空")
    @Schema(description = "人工费")
    private BigDecimal laborCost;
    @NotNull(message = "机械费不能为空")
    @Schema(description = "机械费")
    private BigDecimal machineCost;
}

package cn.iocoder.txgy.module.cms.controller.admin.QuotaDetail.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Schema(description = "管理后台 - 定额明细表 Response VO")
@Data
public class CmsQuotaDetailRespVO {

    @Schema(description = "主键")
    private Long detailId;
    @Schema(description = "关联主表ID")
    private Long quotaId;
    @Schema(description = "定额编号")
    private String quotaNo;
    @Schema(description = "项目名称")
    private String itemName;
    @Schema(description = "计量单位")
    private String unit;
    @Schema(description = "基价（元）")
    private BigDecimal basePrice;
    @Schema(description = "人工费")
    private BigDecimal laborCost;
    @Schema(description = "机械费")
    private BigDecimal machineCost;
    @Schema(description = "创建人")
    private String createBy;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}

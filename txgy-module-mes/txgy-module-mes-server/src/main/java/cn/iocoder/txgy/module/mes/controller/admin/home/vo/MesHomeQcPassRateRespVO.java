package cn.iocoder.txgy.module.mes.controller.admin.home.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Schema(description = "管理后台 - MES 首页质检合格率 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MesHomeQcPassRateRespVO {

    @Schema(description = "检验类型值", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer qcType;

    @Schema(description = "检验类型名", requiredMode = Schema.RequiredMode.REQUIRED, example = "来料检验")
    private String qcTypeName;

    @Schema(description = "检验单数（已完成）", requiredMode = Schema.RequiredMode.REQUIRED, example = "25")
    private Long docCount;

    @Schema(description = "检测数量合计", requiredMode = Schema.RequiredMode.REQUIRED, example = "5000")
    private BigDecimal checkQuantity;

    @Schema(description = "合格数量合计", requiredMode = Schema.RequiredMode.REQUIRED, example = "4850")
    private BigDecimal qualifiedQuantity;

    @Schema(description = "数量合格率（%）", requiredMode = Schema.RequiredMode.REQUIRED, example = "97.0")
    private BigDecimal passRate;

    @Schema(description = "单据合格率（%）", requiredMode = Schema.RequiredMode.REQUIRED, example = "92.0")
    private BigDecimal docPassRate;

}

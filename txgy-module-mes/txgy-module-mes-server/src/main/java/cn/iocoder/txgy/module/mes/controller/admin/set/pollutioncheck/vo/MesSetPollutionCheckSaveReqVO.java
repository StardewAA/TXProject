package cn.iocoder.txgy.module.mes.controller.admin.set.pollutioncheck.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Schema(description = "管理后台 - MES 安全环保检测-污染判定记录 新增/修改 Request VO")
@Data
public class MesSetPollutionCheckSaveReqVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "环节：PURCHASE_INBOUND(采购入库)/MATERIAL_ISSUE(生产领用)/WASTE_INTERMEDIATE(中间废弃物)/FINISHED_PRODUCT(成品)",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "PURCHASE_INBOUND")
    @NotEmpty(message = "环节不能为空")
    private String stage;

    @Schema(description = "关联单号")
    private String bizNo;

    @Schema(description = "批次号", example = "B20260903-001")
    private String batchNo;

    @Schema(description = "物料/产品编码", example = "MAT-0001")
    private String itemCode;

    @Schema(description = "物料/产品名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "含铅涂料")
    @NotEmpty(message = "物料/产品名称不能为空")
    private String itemName;

    @Schema(description = "规格")
    private String itemSpec;

    @Schema(description = "备注")
    private String remark;

}

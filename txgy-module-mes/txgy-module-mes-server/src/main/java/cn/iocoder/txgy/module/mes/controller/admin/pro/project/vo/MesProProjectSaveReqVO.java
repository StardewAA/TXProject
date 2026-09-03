package cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - MES 项目新增/修改 Request VO")
@Data
public class MesProProjectSaveReqVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "项目编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "PRJ20260902001")
    @NotEmpty(message = "项目编码不能为空")
    private String code;

    @Schema(description = "项目名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "某防腐保温工程-钢管保温加工")
    @NotEmpty(message = "项目名称不能为空")
    private String name;

    @Schema(description = "来源单据编号（销售订单号/报价单号）", example = "SO-202609-001")
    private String orderSourceCode;

    @Schema(description = "项目来源类型", example = "1")
    private Integer sourceType;

    @Schema(description = "项目状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "项目状态不能为空")
    private Integer status;

    @Schema(description = "备注", example = "备注")
    private String remark;

}

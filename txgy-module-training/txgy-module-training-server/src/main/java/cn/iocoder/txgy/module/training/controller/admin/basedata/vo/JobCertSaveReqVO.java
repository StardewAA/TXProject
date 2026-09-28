package cn.iocoder.txgy.module.training.controller.admin.basedata.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 工种资格证新增/修改 Request VO")
@Data
public class JobCertSaveReqVO {

    @Schema(description = "资格证 ID（修改时必填）", example = "1")
    private Long id;

    @Schema(description = "工种编码（关联岗位工种）", requiredMode = Schema.RequiredMode.REQUIRED, example = "OP-02")
    @NotEmpty(message = "工种编码不能为空")
    @Size(max = 20, message = "工种编码长度不能超过 20 个字符")
    private String jobCode;

    @Schema(description = "工种名称（冗余）", example = "防腐涂装工")
    @Size(max = 100, message = "工种名称长度不能超过 100 个字符")
    private String jobName;

    @Schema(description = "资格证名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "特种作业操作证（高处作业）")
    @NotEmpty(message = "资格证名称不能为空")
    @Size(max = 100, message = "资格证名称长度不能超过 100 个字符")
    private String certName;

    @Schema(description = "是否必须持证上岗", example = "true")
    private Boolean isRequired;

    @Schema(description = "有效期（月），空表示长期有效", example = "36")
    @Min(value = 0, message = "有效期不能小于 0")
    @Max(value = 600, message = "有效期不能超过 600 个月")
    private Integer validMonths;

    @Schema(description = "排序", example = "1")
    private Integer sort;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

}

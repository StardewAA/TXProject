package cn.iocoder.txgy.module.training.controller.admin.course.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 成长等级新增/修改 Request VO")
@Data
public class GrowthLevelSaveReqVO {

    @Schema(description = "等级 ID（修改时必填）", example = "1")
    private Long id;

    @Schema(description = "等级编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "APPRENTICE")
    @NotEmpty(message = "等级编码不能为空")
    @Size(max = 20, message = "等级编码长度不能超过 20 个字符")
    private String code;

    @Schema(description = "等级序号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "等级序号不能为空")
    @Min(value = 1, message = "等级序号不能小于 1")
    @Max(value = 9, message = "等级序号不能大于 9")
    private Integer levelNo;

    @Schema(description = "等级名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "学徒")
    @NotEmpty(message = "等级名称不能为空")
    @Size(max = 50, message = "等级名称长度不能超过 50 个字符")
    private String name;

    @Schema(description = "定位描述", example = "入门，跟随作业")
    @Size(max = 255, message = "定位描述长度不能超过 255 个字符")
    private String positionDesc;

    @Schema(description = "独立作业权限", example = "否（全程监护）")
    @Size(max = 255, message = "独立作业权限长度不能超过 255 个字符")
    private String workPermission;

    @Schema(description = "带徒上限（人）", example = "0")
    @Min(value = 0, message = "带徒上限不能小于 0")
    @Max(value = 999, message = "带徒上限不能超过 999")
    private Integer maxApprentice;

    @Schema(description = "是否需人工评审（0 否 1 是）", example = "false")
    private Boolean needReview;

    @Schema(description = "参考对标", example = "初级工前")
    @Size(max = 100, message = "参考对标长度不能超过 100 个字符")
    private String reference;

    @Schema(description = "排序", example = "1")
    private Integer sort;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

}

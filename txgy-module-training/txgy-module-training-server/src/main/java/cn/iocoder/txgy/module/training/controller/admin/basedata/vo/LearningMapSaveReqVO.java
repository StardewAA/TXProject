package cn.iocoder.txgy.module.training.controller.admin.basedata.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 学习地图新增/修改 Request VO")
@Data
public class LearningMapSaveReqVO {

    @Schema(description = "学习地图 ID（修改时必填）", example = "1")
    private Long id;

    @Schema(description = "工种编码（关联岗位工种）", requiredMode = Schema.RequiredMode.REQUIRED, example = "OP-02")
    @NotEmpty(message = "工种编码不能为空")
    @Size(max = 20, message = "工种编码长度不能超过 20 个字符")
    private String jobCode;

    @Schema(description = "工种名称（冗余）", example = "防腐涂装工")
    @Size(max = 100, message = "工种名称长度不能超过 100 个字符")
    private String jobName;

    @Schema(description = "成长阶段：entry/core/advanced/technician", requiredMode = Schema.RequiredMode.REQUIRED, example = "entry")
    @NotEmpty(message = "成长阶段不能为空")
    @Size(max = 20, message = "成长阶段长度不能超过 20 个字符")
    private String stage;

    @Schema(description = "阶段名称", example = "入门段(L1→L2)")
    @Size(max = 50, message = "阶段名称长度不能超过 50 个字符")
    private String stageName;

    @Schema(description = "阶段序号：1 入门 / 2 核心 / 3 进阶 / 4 技师", example = "1")
    @Min(value = 1, message = "阶段序号不能小于 1")
    @Max(value = 4, message = "阶段序号不能大于 4")
    private Integer stageNo;

    @Schema(description = "课程编码（关联课程分类 L3 课程级）", requiredMode = Schema.RequiredMode.REQUIRED, example = "B0101")
    @NotEmpty(message = "课程编码不能为空")
    @Size(max = 20, message = "课程编码长度不能超过 20 个字符")
    private String courseCode;

    @Schema(description = "课程名称（冗余）", example = "钢材表面处理")
    @Size(max = 200, message = "课程名称长度不能超过 200 个字符")
    private String courseName;

    @Schema(description = "是否必修", example = "true")
    private Boolean isRequired;

    @Schema(description = "阶段内排序", example = "1")
    private Integer sort;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

}

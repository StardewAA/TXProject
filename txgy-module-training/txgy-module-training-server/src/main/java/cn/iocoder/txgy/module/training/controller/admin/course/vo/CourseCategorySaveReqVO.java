package cn.iocoder.txgy.module.training.controller.admin.course.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 课程分类新增/修改 Request VO")
@Data
public class CourseCategorySaveReqVO {

    @Schema(description = "分类 ID（修改时必填）", example = "1")
    private Long id;

    @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "防腐保温")
    @NotEmpty(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称长度不能超过 50 个字符")
    private String name;

    @Schema(description = "分类编码（对接资源中心时使用）", example = "anticorrosion-insulation")
    @Size(max = 50, message = "分类编码长度不能超过 50 个字符")
    private String code;

    @Schema(description = "排序", example = "1")
    private Integer sort;

    @Schema(description = "备注")
    @Size(max = 200, message = "备注长度不能超过 200 个字符")
    private String remark;

}

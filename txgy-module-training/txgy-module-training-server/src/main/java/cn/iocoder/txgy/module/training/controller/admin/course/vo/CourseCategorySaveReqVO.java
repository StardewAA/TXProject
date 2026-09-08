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

    @Schema(description = "父级分类 ID（0=顶级域；不传默认创建一级域）", example = "0")
    private Long parentId;

    @Schema(description = "层级：1 域 / 2 组 / 3 课（可选，按父级自动推算）", example = "1")
    private Integer level;

    @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "通用基础与安全")
    @NotEmpty(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称长度不能超过 50 个字符")
    private String name;

    @Schema(description = "分类编码（规范：域 A / 组 A01 / 课 A0101）", example = "A")
    @Size(max = 50, message = "分类编码长度不能超过 50 个字符")
    private String code;

    @Schema(description = "业务类别/适用工程队（域级）", example = "全部（必修）")
    @Size(max = 200, message = "业务类别长度不能超过 200 个字符")
    private String bizCategory;

    @Schema(description = "标准学时（课程级）", example = "4")
    private Integer stdHours;

    @Schema(description = "是否必修（课程级）", example = "true")
    private Boolean isRequired;

    @Schema(description = "状态：active 启用 / inactive 停用", example = "active")
    private String status;

    @Schema(description = "排序", example = "1")
    private Integer sort;

    @Schema(description = "备注")
    @Size(max = 200, message = "备注长度不能超过 200 个字符")
    private String remark;

}

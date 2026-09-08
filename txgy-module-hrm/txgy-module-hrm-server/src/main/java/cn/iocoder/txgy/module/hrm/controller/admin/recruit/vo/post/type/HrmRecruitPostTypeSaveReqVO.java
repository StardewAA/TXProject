package cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.post.type;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - HRM 招聘职位类型新增/修改 Request VO")
@Data
public class HrmRecruitPostTypeSaveReqVO {

    @Schema(description = "职位类型编号", example = "1024")
    private Long id;

    @Schema(description = "类型名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "后端开发")
    @NotBlank(message = "职位类型名称不能为空")
    @Size(max = 255, message = "职位类型名称长度不能超过 255 个字符")
    private String name;

    @Schema(description = "父类型编号", example = "0")
    private Long parentId;

    @Schema(description = "显示顺序", example = "10")
    @Min(value = 0, message = "显示顺序不能小于 0")
    private Integer sort;
}

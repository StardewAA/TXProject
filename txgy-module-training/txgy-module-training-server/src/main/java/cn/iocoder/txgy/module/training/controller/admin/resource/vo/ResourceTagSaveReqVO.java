package cn.iocoder.txgy.module.training.controller.admin.resource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;

@Schema(description = "管理后台 - 资源标签 新增/修改 Request VO")
@Data
public class ResourceTagSaveReqVO {

    @Schema(description = "标签ID（修改时必传）")
    private Long id;

    @Schema(description = "标签名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "标签名不能为空")
    private String name;

    @Schema(description = "标签维度：work_type/course/safety/process", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "标签维度不能为空")
    private String dimension;

    @Schema(description = "排序")
    private Integer sort;

}

package cn.iocoder.txgy.module.training.controller.admin.home.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 云培训学院模块信息")
@Data
@AllArgsConstructor
public class TrainingModuleRespVO {

    @Schema(description = "模块标识", example = "resource")
    private String key;

    @Schema(description = "模块名称", example = "资源中心")
    private String name;

    @Schema(description = "模块简介")
    private String description;

    @Schema(description = "核心能力")
    private List<String> capabilities;
}

package cn.iocoder.txgy.module.training.controller.admin.home.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 云培训学院工作台概览")
@Data
@AllArgsConstructor
public class TrainingHomeOverviewRespVO {

    @Schema(description = "平台名称", example = "云培训学院")
    private String name;

    @Schema(description = "平台定位")
    private String description;

    @Schema(description = "一级业务模块数量", example = "9")
    private Integer moduleCount;

    @Schema(description = "业务模块清单")
    private List<TrainingModuleRespVO> modules;
}

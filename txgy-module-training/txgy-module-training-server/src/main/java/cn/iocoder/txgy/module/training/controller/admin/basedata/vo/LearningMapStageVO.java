package cn.iocoder.txgy.module.training.controller.admin.basedata.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 学习地图阶段分组 VO
 *
 * 用于「学习地图」页面按 入门段/核心段/进阶段/技师段 四阶段展示工种课程路径
 *
 * @author 云培训学院课题小组
 */
@Schema(description = "管理后台 - 学习地图阶段分组 VO")
@Data
public class LearningMapStageVO {

    @Schema(description = "成长阶段：entry/core/advanced/technician", example = "entry")
    private String stage;

    @Schema(description = "阶段名称", example = "入门段(L1→L2)")
    private String stageName;

    @Schema(description = "阶段序号：1 入门 / 2 核心 / 3 进阶 / 4 技师", example = "1")
    private Integer stageNo;

    @Schema(description = "本阶段课程数", example = "17")
    private Integer courseCount;

    @Schema(description = "本阶段课程清单")
    private List<LearningMapRespVO> courses = new ArrayList<>();

}

package cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - MES 项目里程碑到期预警 Response VO")
@Data
public class MesProProjectMilestoneAlertRespVO {

    @Schema(description = "里程碑编号", example = "1")
    private Long milestoneId;

    @Schema(description = "项目编号", example = "1024")
    private Long projectId;

    @Schema(description = "项目名称", example = "防腐管道保温工程一期")
    private String projectName;

    @Schema(description = "里程碑名称", example = "一期管线交付")
    private String name;

    @Schema(description = "计划完成时间")
    private LocalDateTime planDate;

    @Schema(description = "距计划完成剩余天数（负数表示已超期）", example = "2")
    private Long daysLeft;

    @Schema(description = "预警级别：warning 三天内到期 / overdue 已超期", example = "warning")
    private String level;

}

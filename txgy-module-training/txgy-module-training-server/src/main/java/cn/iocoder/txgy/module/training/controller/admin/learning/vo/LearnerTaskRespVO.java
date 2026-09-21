package cn.iocoder.txgy.module.training.controller.admin.learning.vo;

import cn.iocoder.txgy.module.training.dal.dataobject.learning.LearnerTaskDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 学员学习任务 Response VO（含冗余课程信息，用户昵称由前端系统用户接口补全）")
@Data
@EqualsAndHashCode(callSuper = true)
public class LearnerTaskRespVO extends LearnerTaskDO {

    @Schema(description = "课程标题", example = "管道防腐保温")
    private String courseTitle;

    @Schema(description = "课程封面", example = "https://...")
    private String coverUrl;

    @Schema(description = "课程讲师", example = "王讲师")
    private String lecturer;

    @Schema(description = "课程学分", example = "2.00")
    private BigDecimal courseCredit;

    @Schema(description = "课程内容类型（CourseMediaTypeEnum：1 文档 2 视频 3 图文 4 VR）", example = "2")
    private Integer courseMediaType;

    @Schema(description = "课程媒资数量", example = "5")
    private Integer courseMediaCount;

    @Schema(description = "课程总时长（分钟）", example = "60")
    private Integer courseDurationMinutes;

}

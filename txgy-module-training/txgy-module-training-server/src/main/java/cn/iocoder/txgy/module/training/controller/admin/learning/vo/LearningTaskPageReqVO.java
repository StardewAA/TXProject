package cn.iocoder.txgy.module.training.controller.admin.learning.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 学员学习任务分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class LearningTaskPageReqVO extends PageParam {

    @Schema(description = "学员用户 ID", example = "1")
    private Long userId;

    @Schema(description = "学员姓名（模糊，来自培训班入班快照）", example = "张三")
    private String userName;

    @Schema(description = "课程 ID", example = "1")
    private Long courseId;

    @Schema(description = "任务状态：0 待学习 1 学习中 2 已完成", example = "1")
    private Integer status;

    @Schema(description = "来源：assign 指派 / self 自主选学 / class 培训班任务（入班生成）", example = "assign")
    private String sourceType;

    @Schema(description = "课程标题（模糊）", example = "管道")
    private String courseTitle;

}

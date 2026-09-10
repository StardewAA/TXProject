package cn.iocoder.txgy.module.training.controller.admin.course.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 课程审核日志分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CourseAuditLogPageReqVO extends PageParam {

    @Schema(description = "课程 ID", example = "1")
    private Long courseId;

    @Schema(description = "动作：SUBMIT / WITHDRAW / APPROVE / REJECT / ONLINE / OFFLINE / PUBLISH", example = "APPROVE")
    private String action;

}

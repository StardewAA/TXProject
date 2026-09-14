package cn.iocoder.txgy.module.training.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 培训班随机收录学员 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ClassStudentRandomAddReqVO extends ClassStudentScopeReqVO {

    @Schema(description = "随机收录人数；不传或小于等于 0 时按剩余名额尽量填满（单个培训班在班学员上限 50 人）", example = "10")
    private Integer count;

}

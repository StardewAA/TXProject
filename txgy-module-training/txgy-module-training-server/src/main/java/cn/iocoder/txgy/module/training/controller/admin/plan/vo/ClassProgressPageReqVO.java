package cn.iocoder.txgy.module.training.controller.admin.plan.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 班级学习进度分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ClassProgressPageReqVO extends PageParam {

    @Schema(description = "培训班 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "培训班 ID 不能为空")
    private Long classId;

    @Schema(description = "学员姓名（模糊）", example = "张")
    private String userName;

    /**
     * 进度维度筛选：0 未开始 1 进行中 2 已完成 3 考试未通过
     */
    @Schema(description = "进度筛选：0未开始 1进行中 2已完成 3考试未通过", example = "2")
    private Integer overallStatus;

}

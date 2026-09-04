package cn.iocoder.txgy.module.training.controller.admin.plan.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 培训班学员分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ClassStudentPageReqVO extends PageParam {

    @Schema(description = "培训班 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "培训班 ID 不能为空")
    private Long classId;

    @Schema(description = "学员姓名（模糊）", example = "张")
    private String userName;

    @Schema(description = "部门/班组（模糊）", example = "管道一班")
    private String deptName;

}

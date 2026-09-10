package cn.iocoder.txgy.module.training.controller.admin.learning.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 学习任务指派 Request VO")
@Data
public class LearningAssignReqVO {

    @Schema(description = "课程 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "课程不能为空")
    private Long courseId;

    @Schema(description = "学员用户 ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "请选择至少一名学员")
    private List<Long> userIds;

    @Schema(description = "备注", example = "9 月岗前培训重点课程")
    private String remark;

}

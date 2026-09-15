package cn.iocoder.txgy.module.training.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 培训班添加学员 Request VO")
@Data
public class ClassStudentAddReqVO {

    @Schema(description = "培训班 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "培训班 ID 不能为空")
    private Long classId;

    @Schema(description = "学员用户 ID 列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[3001,3002]")
    @NotEmpty(message = "请选择要添加的学员")
    private List<Long> userIds;

    @Schema(description = "加入来源：1 手动添加，2 批量导入；不传默认 1", example = "1")
    private Integer source;

}

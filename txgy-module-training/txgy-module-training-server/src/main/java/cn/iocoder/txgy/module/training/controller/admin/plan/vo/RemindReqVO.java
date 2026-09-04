package cn.iocoder.txgy.module.training.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 培训班一键督促 Request VO")
@Data
public class RemindReqVO {

    @Schema(description = "培训班 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "培训班 ID 不能为空")
    private Long classId;

    @Schema(description = "指定督促的学员用户 ID 列表；为空则督促全部未完成学员", example = "[3001,3002]")
    private List<Long> userIds;

}

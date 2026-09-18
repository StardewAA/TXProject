package cn.iocoder.txgy.module.training.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 培训班学员「手动指定培训班入班」Request VO
 *
 * <p>导入名单解析后，由教务为每位学员手动选择要加入的培训班，仅提交已指定培训班的学员。
 *
 * @author 云培训学院课题小组
 */
@Schema(description = "管理后台 - 培训班学员手动指定培训班入班 Request VO")
@Data
public class ClassStudentImportAllocateReqVO {

    @Schema(description = "入班明细（一名学员 + 一个目标培训班，未指定培训班的学员不传）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "请先在导入名单中为要入班的学员选择培训班")
    @Valid
    private List<Item> items;

    @Schema(description = "入班明细")
    @Data
    public static class Item {

        @Schema(description = "学员用户 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "3001")
        @NotNull(message = "学员用户 ID 不能为空")
        private Long userId;

        @Schema(description = "目标培训班 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        @NotNull(message = "请为学员选择要加入的培训班")
        private Long classId;

    }

}

package cn.iocoder.txgy.module.training.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 培训班参与对象筛选 Request VO")
@Data
public class ClassStudentScopeReqVO {

    @Schema(description = "培训班 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "培训班 ID 不能为空")
    private Long classId;

    @Schema(description = "部门 ID 列表（与岗位同时填写时取交集）", example = "[100,101]")
    private List<Long> deptIds;

    @Schema(description = "是否包含所选部门的下级部门，默认 true", example = "true")
    private Boolean includeChildDept;

    @Schema(description = "岗位 ID 列表（系统岗位，与部门同时填写时取交集）", example = "[3]")
    private List<Long> postIds;

}

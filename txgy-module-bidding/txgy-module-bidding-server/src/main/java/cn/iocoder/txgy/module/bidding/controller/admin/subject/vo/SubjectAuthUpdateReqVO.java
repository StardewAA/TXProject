package cn.iocoder.txgy.module.bidding.controller.admin.subject.vo;
// [依据] 《招投标模块开发规范》§3.1（VO 规约）；授权/经办用户语义见 00-索引与引用说明.md


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 授权状态更新 Request（停用/重新启用；撤销授权=停用保留留痕）")
@Data
public class SubjectAuthUpdateReqVO {

    @Schema(description = "授权记录 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "授权记录 ID 不能为空")
    private Long id;

    @Schema(description = "目标状态：ENABLED/DISABLED", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "目标状态不能为空")
    private String authStatus;
}

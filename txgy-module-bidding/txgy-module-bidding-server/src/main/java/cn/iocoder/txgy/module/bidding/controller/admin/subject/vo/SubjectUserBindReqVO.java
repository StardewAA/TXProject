package cn.iocoder.txgy.module.bidding.controller.admin.subject.vo;
// [依据] 《招投标模块开发规范》§3.1（VO 规约）；经办用户绑定语义见 00-索引与引用说明.md


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 主体经办用户绑定 Request")
@Data
public class SubjectUserBindReqVO {

    @Schema(description = "主体所属企业租户", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "企业租户不能为空")
    private Long tenantId;

    @Schema(description = "主体类型", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "主体类型不能为空")
    private String subjectType;

    @Schema(description = "经办用户 ID（该企业租户下的 AdminUser）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "经办用户不能为空")
    private Long userId;

    @Schema(description = "经办角色：OPERATOR（默认，投标操作人）/CONTACT（联系人）")
    private String userRole;

    @Schema(description = "备注")
    private String remark;
}

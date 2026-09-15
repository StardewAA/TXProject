package cn.iocoder.txgy.module.bidding.controller.admin.subject.vo;
// [依据] 《招投标模块开发规范》§3.1（VO 规约）；经办用户绑定语义见 00-索引与引用说明.md


import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 主体经办用户分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
public class SubjectUserPageReqVO extends PageParam {

    @Schema(description = "主体所属企业租户", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long tenantId;

    @Schema(description = "主体类型（空=全部）")
    private String subjectType;

    @Schema(description = "经办角色（空=全部）")
    private String userRole;

    @Schema(description = "启用状态（空=全部）")
    private Boolean enabled;
}

package cn.iocoder.txgy.module.bidding.controller.admin.subject.vo;
// [依据] 《招投标模块开发规范》§3.1（VO 规约）；授权/经办用户语义见 00-索引与引用说明.md


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 主体授权 Request（平台核发/启停）")
@Data
public class SubjectAuthSaveReqVO {

    @Schema(description = "被授权企业租户编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "企业租户不能为空")
    private Long tenantId;

    @Schema(description = "企业名称快照（授权时点取值，供列表展示）")
    private String tenantName;

    @Schema(description = "主体类型：BIDDER/TENDERER", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "主体类型不能为空")
    private String subjectType;

    @Schema(description = "备注")
    private String remark;
}

package cn.iocoder.txgy.module.bidding.controller.admin.subject.vo;
// [依据] 《招投标模块开发规范》§3.1（VO 规约）；经办用户绑定语义见 00-索引与引用说明.md


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "主体资格 Response（投标/发布前置校验结果：已授权 + 信息完善 双条件）")
@Data
public class SubjectEligibilityRespVO {

    @Schema(description = "企业租户")
    private Long tenantId;
    @Schema(description = "企业名称快照（授权记录 tenant_name，可为 null）")
    private String tenantName;
    @Schema(description = "主体类型")
    private String subjectType;
    @Schema(description = "是否已授权且授权启用")
    private Boolean authorized;
    @Schema(description = "授权状态：ENABLED/DISABLED/NOT_AUTHORIZED")
    private String authStatus;
    @Schema(description = "档案信息是否完善")
    private Boolean profileComplete;
    @Schema(description = "是否具备资格（投标/发布）：authorized && profileComplete")
    private Boolean eligible;
    @Schema(description = "不满足时的原因 code：NOT_AUTHORIZED/AUTH_DISABLED/PROFILE_MISSING/PROFILE_INCOMPLETE（可多值）")
    private List<String> reasonCodes;
    @Schema(description = "缺失必填字段中文名（信息未完善时）")
    private List<String> missingRequiredNames;
    @Schema(description = "授权（或最近一次启用）时间；未授权为 null")
    private java.time.LocalDateTime authorizeTime;
    @Schema(description = "停用（撤销授权）时间；未停用为 null")
    private java.time.LocalDateTime disableTime;
    @Schema(description = "提示文案")
    private String message;
}

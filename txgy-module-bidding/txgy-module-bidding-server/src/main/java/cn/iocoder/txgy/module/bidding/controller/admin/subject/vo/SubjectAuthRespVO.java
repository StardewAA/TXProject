package cn.iocoder.txgy.module.bidding.controller.admin.subject.vo;
// [依据] 《招投标模块开发规范》§3.1（VO 规约）；授权/经办用户语义见 00-索引与引用说明.md


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 已授权主体 Response（管理端列表行；含档案完善度——授权≠完善）")
@Data
public class SubjectAuthRespVO {

    @Schema(description = "授权记录 ID")
    private Long id;
    @Schema(description = "被授权企业租户编号")
    private Long tenantId;
    @Schema(description = "企业名称快照")
    private String tenantName;
    @Schema(description = "主体类型")
    private String subjectType;
    @Schema(description = "授权状态：ENABLED/DISABLED")
    private String authStatus;
    @Schema(description = "档案是否存在")
    private Boolean profileExists;
    @Schema(description = "档案信息是否完善（仅授权尚不充分，须完善后方可投标/发布）")
    private Boolean profileComplete;
    @Schema(description = "缺失必填字段 code 列表")
    private List<String> missingRequiredCodes;
    @Schema(description = "缺失必填字段中文名列表")
    private List<String> missingRequiredNames;
    @Schema(description = "授权时间")
    private LocalDateTime authorizeTime;
    @Schema(description = "停用时间")
    private LocalDateTime disableTime;
    @Schema(description = "备注")
    private String remark;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}

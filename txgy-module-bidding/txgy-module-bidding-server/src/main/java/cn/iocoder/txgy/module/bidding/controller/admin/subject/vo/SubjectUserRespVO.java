package cn.iocoder.txgy.module.bidding.controller.admin.subject.vo;
// [依据] 《招投标模块开发规范》§3.1（VO 规约）；经办用户绑定语义见 00-索引与引用说明.md


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 主体经办用户 Response")
@Data
public class SubjectUserRespVO {

    @Schema(description = "绑定记录 ID")
    private Long id;
    @Schema(description = "主体所属企业租户")
    private Long tenantId;
    @Schema(description = "主体类型")
    private String subjectType;
    @Schema(description = "经办用户 ID")
    private Long userId;
    @Schema(description = "经办用户昵称快照")
    private String userNickname;
    @Schema(description = "经办角色：OPERATOR/CONTACT")
    private String userRole;
    @Schema(description = "启用开关")
    private Boolean enabled;
    @Schema(description = "备注")
    private String remark;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}

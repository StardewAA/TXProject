package cn.iocoder.txgy.module.member.controller.admin.platform.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 平台账号链接分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class MemberPlatformAccountLinkPageReqVO extends PageParam {

    @Schema(description = "链接状态；留空查询全部", example = "IDENTITY_CONFLICT")
    @Pattern(regexp = "^(READY|PENDING|CREDENTIAL_CONFLICT|IDENTITY_CONFLICT)?$", message = "链接状态不合法")
    private String linkStatus = "IDENTITY_CONFLICT";

    @Schema(description = "平台账号编号")
    private Long platformAccountId;

    @Schema(description = "来源租户编号")
    private Long sourceTenantId;

    @Schema(description = "旧会员编号")
    private Long legacyMemberId;

    @Schema(description = "人工审核单号")
    private String reviewNo;
}

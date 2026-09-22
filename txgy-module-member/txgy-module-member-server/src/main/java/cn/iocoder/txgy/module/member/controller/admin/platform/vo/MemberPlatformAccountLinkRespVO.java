package cn.iocoder.txgy.module.member.controller.admin.platform.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 平台账号链接 Response VO")
@Data
public class MemberPlatformAccountLinkRespVO {

    private Long linkId;
    private Long platformAccountId;
    private String platformNo;
    private Long legacyMemberId;
    private Long sourceTenantId;
    private String linkStatus;
    private String conflictReason;
    private String linkOrigin;
    private String identityReviewNo;
    private String identityVerifiedBy;
    private LocalDateTime identityVerifiedAt;
    private String maskedLoginName;
    private String maskedMobile;
    private Integer memberStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

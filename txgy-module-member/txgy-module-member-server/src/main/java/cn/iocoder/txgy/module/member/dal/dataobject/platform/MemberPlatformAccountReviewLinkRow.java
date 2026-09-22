package cn.iocoder.txgy.module.member.dal.dataobject.platform;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端平台账号链接只读投影。查询 SQL 不选择 member_user.password。
 */
@Data
public class MemberPlatformAccountReviewLinkRow {

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

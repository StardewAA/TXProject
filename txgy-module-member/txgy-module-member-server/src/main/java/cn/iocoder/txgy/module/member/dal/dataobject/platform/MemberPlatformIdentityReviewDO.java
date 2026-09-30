package cn.iocoder.txgy.module.member.dal.dataobject.platform;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台账号人工身份归并审计记录。
 *
 * <p>该记录只追加、不修改，用审核单号实现业务幂等。快照只包含链接关系与冲突原因，
 * 不包含密码、密码散列或验证码。</p>
 */
@Data
public class MemberPlatformIdentityReviewDO {

    private Long id;
    private String reviewNo;
    private Long targetPlatformAccountId;
    private String selectedLinkIds;
    private String linkSnapshotJson;
    private String reviewNote;
    private Long operatorUserId;
    private Long operatorTenantId;
    private String status;
    private LocalDateTime appliedAt;
    private LocalDateTime createTime;
}

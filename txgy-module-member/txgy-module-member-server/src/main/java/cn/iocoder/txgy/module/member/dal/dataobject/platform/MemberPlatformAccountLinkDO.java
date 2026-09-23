package cn.iocoder.txgy.module.member.dal.dataobject.platform;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@TableName("member_platform_account_link")
@KeySequence("member_platform_account_link_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberPlatformAccountLinkDO extends BaseDO {

    @TableId
    private Long id;
    private Long platformAccountId;
    private Long legacyMemberId;
    private Long sourceTenantId;
    private String linkStatus;
    private String conflictReason;
    /**
     * 链接产生方式。多旧会员归并到同一平台账号时，只接受 MANUAL_IDENTITY_REVIEW。
     */
    private String linkOrigin;
    /** 人工身份归并审核单号；同一次归并的所有 READY 链接必须一致。 */
    private String identityReviewNo;
    private String identityVerifiedBy;
    private LocalDateTime identityVerifiedAt;
}

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

@TableName("member_platform_account")
@KeySequence("member_platform_account_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberPlatformAccountDO extends BaseDO {

    @TableId
    private Long id;
    private String platformNo;
    /** LOGIN_NAME or MOBILE. Password hashes remain in member_user. */
    private String credentialType;
    /** Normalized platform-wide login identifier. */
    private String credentialIdentifier;
    /** Optional normalized login name; globally reserved even when mobile is the primary credential. */
    private String loginName;
    /** Optional normalized mobile; globally reserved even when login name is the primary credential. */
    private String mobile;
    private Long canonicalMemberId;
    private Integer status;
}

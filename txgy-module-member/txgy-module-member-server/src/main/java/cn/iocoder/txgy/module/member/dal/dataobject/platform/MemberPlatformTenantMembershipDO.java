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

@TableName("member_platform_tenant_membership")
@KeySequence("member_platform_tenant_membership_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberPlatformTenantMembershipDO extends BaseDO {

    @TableId
    private Long id;
    private Long platformAccountId;
    private Long tenantId;
    private Long enterpriseId;
    private String memberType;
    private Integer status;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
}

package cn.iocoder.txgy.module.member.service.platform;

import cn.iocoder.txgy.module.member.api.platform.dto.MemberPlatformAccountRespDTO;
import cn.iocoder.txgy.module.member.api.platform.dto.MemberPlatformAddressRespDTO;
import cn.iocoder.txgy.module.member.dal.dataobject.user.MemberUserDO;

public interface MemberPlatformAccountService {

    MemberPlatformAccountRespDTO resolveRequired(Long legacyMemberId, Long sourceTenantId);

    MemberPlatformAddressRespDTO resolveAddressRequired(Long legacyMemberId, Long sourceTenantId, Long addressId);

    /** Must be invoked inside the same local transaction that creates the member. */
    MemberPlatformAccountRespDTO createForRegistration(MemberUserDO member, Long sourceTenantId);

    /**
     * Synchronizes the globally reserved mobile alias after the tenant member row changes.
     * Must run in the same local transaction as the member update.
     */
    void synchronizeMobileAlias(MemberUserDO memberBeforeUpdate, String newMobile);

    /** Resolves exactly one safe canonical login source without trusting a client tenant. */
    MemberPlatformLoginCandidate resolveLoginRequired(String account);
}

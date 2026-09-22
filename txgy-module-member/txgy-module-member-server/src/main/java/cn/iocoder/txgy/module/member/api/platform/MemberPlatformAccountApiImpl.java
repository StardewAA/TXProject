package cn.iocoder.txgy.module.member.api.platform;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.member.api.platform.dto.MemberPlatformAccountRespDTO;
import cn.iocoder.txgy.module.member.api.platform.dto.MemberPlatformAddressRespDTO;
import cn.iocoder.txgy.module.member.service.platform.MemberPlatformAccountService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@RestController
@Validated
public class MemberPlatformAccountApiImpl implements MemberPlatformAccountApi {

    @Resource
    private MemberPlatformAccountService platformAccountService;

    @Override
    @TenantIgnore
    public CommonResult<MemberPlatformAccountRespDTO> resolve(Long legacyMemberId, Long sourceTenantId) {
        return success(platformAccountService.resolveRequired(legacyMemberId, sourceTenantId));
    }

    @Override
    @TenantIgnore
    public CommonResult<MemberPlatformAddressRespDTO> resolveAddress(Long legacyMemberId, Long sourceTenantId,
                                                                     Long addressId) {
        return success(platformAccountService.resolveAddressRequired(legacyMemberId, sourceTenantId, addressId));
    }
}

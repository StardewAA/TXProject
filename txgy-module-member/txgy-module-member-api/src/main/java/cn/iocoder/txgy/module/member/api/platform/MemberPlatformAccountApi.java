package cn.iocoder.txgy.module.member.api.platform;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.member.api.platform.dto.MemberPlatformAccountRespDTO;
import cn.iocoder.txgy.module.member.api.platform.dto.MemberPlatformAddressRespDTO;
import cn.iocoder.txgy.module.member.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - 商城平台账号")
public interface MemberPlatformAccountApi {

    String PREFIX = ApiConstants.PREFIX + "/platform-account";

    @GetMapping(PREFIX + "/resolve")
    @Operation(summary = "按可信旧会员会话解析商城平台账号")
    @Parameter(name = "legacyMemberId", description = "旧会员编号", required = true)
    CommonResult<MemberPlatformAccountRespDTO> resolve(
            @RequestParam("legacyMemberId") Long legacyMemberId,
            @RequestParam("sourceTenantId") Long sourceTenantId);

    @GetMapping(PREFIX + "/resolve-address")
    @Operation(summary = "按可信旧会员会话解析平台账号收货地址")
    CommonResult<MemberPlatformAddressRespDTO> resolveAddress(
            @RequestParam("legacyMemberId") Long legacyMemberId,
            @RequestParam("sourceTenantId") Long sourceTenantId,
            @RequestParam("addressId") Long addressId);
}

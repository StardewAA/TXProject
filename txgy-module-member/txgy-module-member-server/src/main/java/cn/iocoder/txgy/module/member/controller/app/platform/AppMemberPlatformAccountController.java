package cn.iocoder.txgy.module.member.controller.app.platform;

import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.member.api.platform.dto.MemberPlatformAccountRespDTO;
import cn.iocoder.txgy.module.member.service.platform.MemberPlatformAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 APP - 商城平台账号")
@RestController
@RequestMapping("/member/platform-account")
public class AppMemberPlatformAccountController {

    @Resource
    private MemberPlatformAccountService platformAccountService;

    @GetMapping("/session")
    @PreAuthorize("isAuthenticated()")
    @TenantIgnore
    @Operation(summary = "获得当前登录会员的平台账号与已审核企业身份")
    public CommonResult<MemberPlatformAccountRespDTO> getSession() {
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        if (user == null || user.getId() == null || user.getId() <= 0
                || user.getTenantId() == null || user.getTenantId() <= 0
                || !Objects.equals(user.getUserType(), UserTypeEnum.MEMBER.getValue())) {
            throw exception(UNAUTHORIZED);
        }
        return success(platformAccountService.resolveRequired(user.getId(), user.getTenantId()));
    }
}

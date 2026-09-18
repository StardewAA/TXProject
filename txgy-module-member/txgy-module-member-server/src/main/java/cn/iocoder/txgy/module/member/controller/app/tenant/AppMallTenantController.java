package cn.iocoder.txgy.module.member.controller.app.tenant;

import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallTenantConfigRespVO;
import cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallTenantSessionRespVO;
import cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallCustomerAffiliationRespVO;
import cn.iocoder.txgy.module.member.service.tenant.MallCustomerAffiliationService;
import cn.iocoder.txgy.module.member.service.tenant.MallTenantCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.AUTH_TENANT_MISMATCH;

@Tag(name = "用户 APP - 商城租户入口")
@RestController
@RequestMapping("/member/mall-tenant")
public class AppMallTenantController {

    @Resource
    private MallTenantCatalogService catalogService;

    @Resource
    private MallCustomerAffiliationService customerAffiliationService;

    @GetMapping("/customer-affiliation")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获得本人客户服务归属（不代表企业经营权限）")
    public CommonResult<AppMallCustomerAffiliationRespVO> getCustomerAffiliation() {
        var session = getSession().getCheckedData();
        return success(customerAffiliationService.getForMember(session.getUserId()));
    }

    @GetMapping("/config")
    @PermitAll
    @TenantIgnore
    @Operation(summary = "获得商城公开租户配置及可用能力")
    public CommonResult<AppMallTenantConfigRespVO> getConfig() {
        return success(catalogService.getConfig());
    }

    @GetMapping("/session")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获得当前会员会话的可信租户")
    public CommonResult<AppMallTenantSessionRespVO> getSession() {
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        if (user == null || user.getId() == null || user.getId() <= 0
                || !Objects.equals(user.getUserType(), UserTypeEnum.MEMBER.getValue())) {
            throw exception(UNAUTHORIZED);
        }
        if (user.getTenantId() == null || user.getTenantId() <= 0 || TenantContextHolder.isIgnore()
                || !Objects.equals(user.getTenantId(), TenantContextHolder.getTenantId())) {
            throw exception(AUTH_TENANT_MISMATCH);
        }
        return success(new AppMallTenantSessionRespVO().setTenantId(user.getTenantId()).setUserId(user.getId()));
    }

}

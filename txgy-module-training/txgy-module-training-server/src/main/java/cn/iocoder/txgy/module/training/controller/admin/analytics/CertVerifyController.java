package cn.iocoder.txgy.module.training.controller.admin.analytics;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.training.service.analytics.CertVerifyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 97 证书公开验真（免登录公共访问）")
@RestController
@RequestMapping("/training/analytics/cert-verify")
@Validated
public class CertVerifyController {

    @Resource
    private CertVerifyService certVerifyService;

    @GetMapping("/public")
    @Operation(summary = "证书公开验真（免登录，仅返回公开验真字段）")
    @Parameter(name = "certCode", description = "证书编号", required = true, example = "TX2026C0001")
    public CommonResult<Map<String, Object>> publicVerify(@RequestParam("certCode") String certCode,
                                                          HttpServletRequest request) {
        // 免登录入口：SecurityConfiguration 中 permitAll + Service 层 @PermitAll 双保险
        return success(certVerifyService.verify(certCode, request.getRemoteAddr(),
                request.getHeader("User-Agent")));
    }

    @GetMapping("/my")
    @Operation(summary = "登录内查看自己的证书验真结果（个人档案跳转入口）")
    @Parameter(name = "certCode", description = "证书编号", required = true, example = "TX2026C0001")
    public CommonResult<Map<String, Object>> myVerify(@RequestParam("certCode") String certCode,
                                                      HttpServletRequest request) {
        // TODO[模块九-证书验真] 登录内验真入口（校验证书归属当前学员）
        getLoginUserId();
        return success(certVerifyService.verify(certCode, request.getRemoteAddr(),
                request.getHeader("User-Agent")));
    }

}

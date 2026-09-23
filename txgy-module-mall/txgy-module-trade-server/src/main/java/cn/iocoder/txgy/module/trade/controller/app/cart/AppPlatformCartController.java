package cn.iocoder.txgy.module.trade.controller.app.cart;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.trade.controller.app.cart.vo.AppPlatformCartAddReqVO;
import cn.iocoder.txgy.module.trade.controller.app.cart.vo.AppPlatformCartListRespVO;
import cn.iocoder.txgy.module.trade.controller.app.cart.vo.AppPlatformCartUpdateCountReqVO;
import cn.iocoder.txgy.module.trade.controller.app.cart.vo.AppPlatformCartUpdateSelectedReqVO;
import cn.iocoder.txgy.module.trade.service.cart.PlatformCartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUser;

@Tag(name = "用户 App - 平台跨租户购物车")
@RestController
@RequestMapping("/trade/platform-cart")
@Validated
@TenantIgnore
@RequiredArgsConstructor
public class AppPlatformCartController {

    private final PlatformCartService cartService;

    @GetMapping("/list")
    @Operation(summary = "获得平台购物车，按租户和店铺分组")
    public CommonResult<AppPlatformCartListRespVO> list() {
        LoginUser loginUser = requiredLoginUser();
        return success(cartService.list(loginUser.getId(), loginUser.getTenantId()));
    }

    @PostMapping("/add")
    @Operation(summary = "加入平台购物车")
    public CommonResult<Long> add(@Valid @RequestBody AppPlatformCartAddReqVO request) {
        LoginUser loginUser = requiredLoginUser();
        return success(cartService.add(loginUser.getId(), loginUser.getTenantId(), request));
    }

    @PutMapping("/update-count")
    @Operation(summary = "更新平台购物车商品数量")
    public CommonResult<Boolean> updateCount(@Valid @RequestBody AppPlatformCartUpdateCountReqVO request) {
        LoginUser loginUser = requiredLoginUser();
        cartService.updateCount(loginUser.getId(), loginUser.getTenantId(), request);
        return success(true);
    }

    @PutMapping("/update-selected")
    @Operation(summary = "更新平台购物车选中状态")
    public CommonResult<Boolean> updateSelected(@Valid @RequestBody AppPlatformCartUpdateSelectedReqVO request) {
        LoginUser loginUser = requiredLoginUser();
        cartService.updateSelected(loginUser.getId(), loginUser.getTenantId(), request);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除平台购物车项")
    @Parameter(name = "ids", description = "购物车项编号", required = true)
    public CommonResult<Boolean> delete(
            @RequestParam("ids") @NotEmpty @Size(max = 200) Collection<Long> ids) {
        LoginUser loginUser = requiredLoginUser();
        cartService.delete(loginUser.getId(), loginUser.getTenantId(), ids);
        return success(true);
    }

    private static LoginUser requiredLoginUser() {
        LoginUser loginUser = getLoginUser();
        if (loginUser == null || loginUser.getId() == null || loginUser.getTenantId() == null) {
            throw new IllegalStateException("平台购物车需要有效会员会话");
        }
        return loginUser;
    }

}

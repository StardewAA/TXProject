package cn.iocoder.txgy.module.trade.service.supplier;

import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.api.shop.ProductAdminShopScopeApi;
import cn.iocoder.txgy.module.product.api.shop.dto.ProductAdminShopScopeRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 商城后台经营账号的数据范围入口。
 *
 * <p>权限码只决定能否调用某类接口；这里再校验账号在当前租户下真实拥有的企业、店铺范围。</p>
 */
@Service
public class AdminShopScopeService {

    @Resource
    private ProductAdminShopScopeApi scopeApi;

    public record Scope(long adminUserId, boolean unrestricted, Set<Long> shopIds) {
        public Scope {
            shopIds = Collections.unmodifiableSet(new LinkedHashSet<>(shopIds));
        }
    }

    public Scope current() {
        var loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null || !Objects.equals(loginUser.getUserType(), UserTypeEnum.ADMIN.getValue())) {
            throw forbidden("请使用商城后台账号");
        }
        ProductAdminShopScopeRespDTO remote = scopeApi.getScope(loginUser.getId()).getCheckedData();
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (remote == null || !Objects.equals(remote.getAdminUserId(), loginUser.getId())
                || !Objects.equals(remote.getTenantId(), tenantId)) {
            throw forbidden("后台经营账号数据范围无效，请重新登录后重试");
        }
        return new Scope(loginUser.getId(), Boolean.TRUE.equals(remote.getUnrestricted()),
                remote.getShopIds() == null ? Set.of() : remote.getShopIds());
    }

    public Scope requireShop(Long shopId) {
        if (shopId == null || shopId <= 0) {
            throw forbidden("请选择有效店铺");
        }
        Scope scope = current();
        // unrestricted 表示租户/平台治理范围，不是企业经营授权。治理账号即使误配了
        // 供应商按钮权限，也不能代替企业处理订单、售后或结算。
        if (scope.unrestricted()) {
            throw forbidden("租户或平台治理账号不能代替企业经营店铺");
        }
        if (!scope.shopIds().contains(shopId)) {
            throw forbidden("未获得该店铺经营权限");
        }
        // RPC 再按当前租户、企业状态、店铺状态和授权记录做一次强校验。
        if (!Boolean.TRUE.equals(scopeApi.validateAuthorizedShop(scope.adminUserId(), shopId).getCheckedData())) {
            throw forbidden("店铺授权已失效，请刷新权限后重试");
        }
        return scope;
    }

    public ProductAdminShopScopeApi.ReturnAddress requireReturnAddress(Long adminUserId, Long shopId) {
        ProductAdminShopScopeApi.ReturnAddress address = scopeApi.returnAddress(adminUserId, shopId).getCheckedData();
        if (address == null || address.receiverName() == null || address.receiverName().isBlank()
                || address.receiverMobile() == null || address.receiverMobile().isBlank()
                || address.address() == null || address.address().isBlank()) {
            throw SupplierTradeService.invalid("请先维护完整的店铺退货地址");
        }
        return address;
    }

    private static ServiceException forbidden(String message) {
        return new ServiceException(403, message);
    }
}

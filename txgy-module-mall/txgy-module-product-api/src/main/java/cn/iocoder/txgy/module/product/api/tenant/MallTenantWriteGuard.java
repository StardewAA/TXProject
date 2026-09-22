package cn.iocoder.txgy.module.product.api.tenant;

import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.product.enums.ErrorCodeConstants.RETIRED_MALL_TENANT_WRITE_FORBIDDEN;

/**
 * 商城退役租户写边界。
 *
 * <p>3000 曾作为公共商城租户，现仅允许历史数据读取和既有订单的必要售后处理。
 * 新的企业、店铺、商品、询价、购物车及订单写入必须 fail-closed。</p>
 */
public final class MallTenantWriteGuard {

    public static final long RETIRED_PUBLIC_TENANT_ID = 3000L;

    private MallTenantWriteGuard() {
    }

    public static void requireWritable(Long tenantId) {
        if (isRetired(tenantId)) {
            throw exception(RETIRED_MALL_TENANT_WRITE_FORBIDDEN);
        }
    }

    public static boolean isRetired(Long tenantId) {
        return Objects.equals(tenantId, RETIRED_PUBLIC_TENANT_ID);
    }

}

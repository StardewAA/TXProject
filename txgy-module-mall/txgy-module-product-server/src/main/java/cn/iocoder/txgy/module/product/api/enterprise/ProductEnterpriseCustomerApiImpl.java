package cn.iocoder.txgy.module.product.api.enterprise;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.dal.mysql.enterprise.ProductEnterpriseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import java.util.Objects;

@RestController
@RequiredArgsConstructor
public class ProductEnterpriseCustomerApiImpl implements ProductEnterpriseCustomerApi {
    private final ProductEnterpriseMapper enterprises;

    @Override
    public CommonResult<Owner> getCustomerOwner(Long id) {
        Long tenant = TenantContextHolder.getTenantId();
        if (tenant == null || tenant <= 0 || TenantContextHolder.isIgnore() || id == null || id <= 0) {
            return CommonResult.success(null);
        }
        var enterprise = enterprises.selectById(id);
        if (enterprise == null || Boolean.TRUE.equals(enterprise.getDeleted())
                || !Objects.equals(tenant, enterprise.getTenantId())) return CommonResult.success(null);
        return CommonResult.success(new Owner(enterprise.getId(), tenant, enterprise.getName(),
                Integer.valueOf(1).equals(enterprise.getAuditStatus())));
    }
}

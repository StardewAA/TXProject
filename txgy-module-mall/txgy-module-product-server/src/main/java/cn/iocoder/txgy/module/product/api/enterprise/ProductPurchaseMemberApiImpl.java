package cn.iocoder.txgy.module.product.api.enterprise;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.EnterpriseApplicationDO;
import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@Validated
public class ProductPurchaseMemberApiImpl implements ProductPurchaseMemberApi {

    @Resource
    private EnterpriseMembershipService membershipService;

    @Override
    public CommonResult<Scope> getScope(Long memberId) {
        EnterpriseApplicationDO member = membershipService.requireBuyer(memberId);
        return CommonResult.success(new Scope(TenantContextHolder.getRequiredTenantId(), memberId,
                member.getEnterpriseId()));
    }

    @Override
    public CommonResult<Boolean> validateEnterprise(Long memberId, Long enterpriseId) {
        if (enterpriseId == null || enterpriseId <= 0) {
            throw new ServiceException(403, "采购企业编号无效");
        }
        EnterpriseApplicationDO member = membershipService.requireBuyer(memberId);
        if (!Objects.equals(member.getEnterpriseId(), enterpriseId)) {
            throw new ServiceException(403, "当前采购成员不属于该企业");
        }
        return CommonResult.success(true);
    }
}

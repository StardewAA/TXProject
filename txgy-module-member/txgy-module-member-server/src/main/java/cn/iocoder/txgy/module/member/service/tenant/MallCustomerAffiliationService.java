package cn.iocoder.txgy.module.member.service.tenant;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.member.dal.dataobject.tenant.MallCustomerAffiliationDO;
import cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallCustomerAffiliationRespVO;
import cn.iocoder.txgy.module.member.dal.mysql.tenant.MallCustomerAffiliationMapper;
import cn.iocoder.txgy.module.member.framework.tenant.config.MallTenantAuthProperties;
import cn.iocoder.txgy.module.member.framework.tenant.config.MallTenantProperties;
import cn.iocoder.txgy.module.product.api.enterprise.ProductEnterpriseCustomerApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.MALL_TENANT_UNAVAILABLE;
import static cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallCustomerAffiliationRespVO.State.*;

@Service
@RequiredArgsConstructor
public class MallCustomerAffiliationService {
    private final MallTenantProperties tenants;
    private final MallTenantAuthProperties auth;
    private final ProductEnterpriseCustomerApi enterprises;
    private final MallCustomerAffiliationMapper affiliations;
    public static final String SOURCE = "PUBLIC_REGISTRATION";

    /** memberId must come from the verified member session, never a client parameter. */
    public AppMallCustomerAffiliationRespVO getForMember(Long memberId) {
        Long tenant = TenantContextHolder.getTenantId();
        if (tenant == null || tenant <= 0 || TenantContextHolder.isIgnore() || memberId == null || memberId <= 0) fail();
        if (!auth.isEnabled() || !tenants.isEnabled()) return AppMallCustomerAffiliationRespVO.of(DISABLED);
        var row = affiliations.selectForMember(tenant, memberId);
        if (row == null) return AppMallCustomerAffiliationRespVO.of(NONE);
        if (!Objects.equals(row.getTenantId(), tenant) || !Objects.equals(row.getMemberId(), memberId)
                || Boolean.TRUE.equals(row.getDeleted()) || !SOURCE.equals(row.getSource())
                || row.getServiceEnterpriseId() == null || row.getServiceEnterpriseId() <= 0) fail();
        // Historical ownership is authoritative; configuration changes never rebind it on reads.
        ProductEnterpriseCustomerApi.Owner owner;
        try {
            var response = enterprises.getCustomerOwner(row.getServiceEnterpriseId());
            if (response == null) return AppMallCustomerAffiliationRespVO.of(TEMPORARILY_UNAVAILABLE);
            owner = response.getCheckedData();
        } catch (RuntimeException error) {
            // An outage is not evidence that enterprise approval has been revoked.
            return AppMallCustomerAffiliationRespVO.of(TEMPORARILY_UNAVAILABLE);
        }
        if (owner == null || !Objects.equals(owner.id(), row.getServiceEnterpriseId())
                || !Objects.equals(owner.tenantId(), tenant) || !owner.approved()) {
            return AppMallCustomerAffiliationRespVO.of(OWNER_UNAVAILABLE);
        }
        if (owner.name() == null || owner.name().isBlank()) return AppMallCustomerAffiliationRespVO.of(TEMPORARILY_UNAVAILABLE);
        return new AppMallCustomerAffiliationRespVO(ACTIVE, owner.name());
    }

    /** Called before sending/consuming a registration code; no DB writes. */
    public Long validateRegistration(long tenant) {
        if (!auth.isEnabled()) return null; // No new DB/RPC dependency for the unchanged legacy entry.
        if (!tenants.isEnabled() || tenants.getPublicTenantId() == null || tenants.getPublicTenantId() <= 0) fail();
        if (!Objects.equals(tenants.getPublicTenantId(), tenant)) return null;
        Long enterprise = tenants.getPlatformEnterpriseId();
        if (enterprise == null || enterprise <= 0) fail();
        TenantUtils.execute(tenant, () -> { requireOwner(tenant, enterprise); });
        return enterprise;
    }

    /** Must join registration, before log/token RPCs. Never reassign existing customers on config change. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void bindRegistration(Long memberId) {
        if (!auth.isEnabled()) return;
        Long tenant = TenantContextHolder.getTenantId();
        if (tenant == null || tenant <= 0 || TenantContextHolder.isIgnore() || memberId == null || memberId <= 0) fail();
        Long enterprise = validateRegistration(tenant);
        if (enterprise == null) return;
        if (!Objects.equals(memberId, affiliations.lockMember(tenant, memberId))) fail();
        var current = affiliations.lockAffiliation(tenant, memberId);
        if (current != null) {
            if (!Objects.equals(current.getTenantId(), tenant) || !Objects.equals(current.getMemberId(), memberId)
                    || !Objects.equals(current.getServiceEnterpriseId(), enterprise) || !SOURCE.equals(current.getSource())) fail();
            return;
        }
        var row = new MallCustomerAffiliationDO().setMemberId(memberId).setServiceEnterpriseId(enterprise).setSource(SOURCE);
        row.setTenantId(tenant);
        if (affiliations.insert(row) != 1) fail();
    }

    private ProductEnterpriseCustomerApi.Owner requireOwner(long tenant, Long enterprise) {
        try {
            var response = enterprises.getCustomerOwner(enterprise);
            var owner = response == null ? null : response.getCheckedData();
            if (owner == null || !Objects.equals(owner.id(), enterprise) || !Objects.equals(owner.tenantId(), tenant)
                    || !owner.approved()) fail();
            return owner;
        } catch (RuntimeException error) {
            // Never propagate remote enterprise details or transport credentials to anonymous auth responses.
            throw exception(MALL_TENANT_UNAVAILABLE);
        }
    }

    private static void fail() { throw exception(MALL_TENANT_UNAVAILABLE); }
}

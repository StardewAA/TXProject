package cn.iocoder.txgy.module.member.service.tenant;

import cn.iocoder.txgy.framework.common.biz.system.tenant.TenantCommonApi;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallTenantConfigRespVO;
import cn.iocoder.txgy.module.member.framework.tenant.config.MallTenantProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.MALL_TENANT_UNAVAILABLE;

/** 仅查询已配置白名单；不枚举系统租户，不修改现有注册目标。 */
@Service
public class MallTenantCatalogService {

    private final MallTenantProperties properties;
    private final TenantCommonApi tenantApi;
    private final Long legacyTenantId;

    public MallTenantCatalogService(MallTenantProperties properties, TenantCommonApi tenantApi,
            @Value("${mall.registration.platform-tenant-id:1}") Long legacyTenantId) {
        this.properties = properties;
        this.tenantApi = tenantApi;
        this.legacyTenantId = legacyTenantId;
    }

    public AppMallTenantConfigRespVO getConfig() {
        AppMallTenantConfigRespVO result = new AppMallTenantConfigRespVO()
                .setEnabled(properties.isEnabled()).setLegacyTenantId(legacyTenantId);
        if (!properties.isEnabled()) {
            return result;
        }

        List<MallTenantProperties.Entry> entries = properties.getEntries();
        Long publicTenantId = properties.getPublicTenantId();
        if (entries == null || (publicTenantId != null
                && (publicTenantId <= 0 || Objects.equals(publicTenantId, legacyTenantId)))) {
            throw exception(MALL_TENANT_UNAVAILABLE);
        }
        Set<Long> ids = new HashSet<>();
        MallTenantProperties.Entry publicEntry = null;
        for (MallTenantProperties.Entry entry : entries) {
            if (entry == null || entry.getTenantId() == null || entry.getTenantId() <= 0
                    || !StringUtils.hasText(entry.getName()) || !ids.add(entry.getTenantId())) {
                throw exception(MALL_TENANT_UNAVAILABLE);
            }
            if (publicTenantId != null && Objects.equals(entry.getTenantId(), publicTenantId) && entry.isPublicVisible()) {
                publicEntry = entry;
            }
        }
        // 第 22 节取消了公共注册租户；仍兼容显式配置浏览落地项，但绝不回落到租户 1。
        if (publicTenantId != null && (publicEntry == null || !isAvailable(publicTenantId))) {
            throw exception(MALL_TENANT_UNAVAILABLE);
        }

        List<AppMallTenantConfigRespVO.Tenant> tenants = new ArrayList<>();
        for (MallTenantProperties.Entry entry : entries) {
            if (!entry.isPublicVisible()) {
                continue;
            }
            if (!Objects.equals(entry.getTenantId(), publicTenantId) && !isAvailable(entry.getTenantId())) {
                continue;
            }
            tenants.add(new AppMallTenantConfigRespVO.Tenant()
                    .setTenantId(entry.getTenantId()).setName(entry.getName())
                    .setRegistrationEnabled(entry.isRegistrationEnabled()));
        }
        result.getCapabilities().setBrowsePreview(properties.isBrowsePreviewEnabled());
        result.getCapabilities().setScopedEntry(properties.isScopedEntryEnabled());
        result.getCapabilities().setBrowseSwitch(properties.isScopedEntryEnabled() && tenants.size() > 1);
        // 注册固定归属于当前入口租户；目录只用于浏览切换，不向用户开放注册租户选择。
        result.getCapabilities().setRegistrationSelection(false);
        return result.setPublicTenantId(publicTenantId).setTenants(tenants);
    }

    private boolean isAvailable(Long tenantId) {
        try {
            CommonResult<Boolean> result = tenantApi.validTenant(tenantId);
            return result != null && Boolean.TRUE.equals(result.getCheckedData());
        } catch (RuntimeException ignored) {
            // 禁用、过期及状态服务不可用均不公开，不透传租户内部错误详情。
            return false;
        }
    }

}

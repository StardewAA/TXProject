package cn.iocoder.txgy.module.product.service.portal;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.controller.admin.portal.vo.MallPortalTenantPageReqVO;
import cn.iocoder.txgy.module.product.controller.admin.portal.vo.MallPortalTenantUpdateReqVO;
import cn.iocoder.txgy.module.product.dal.dataobject.portal.MallPortalTenantDO;
import cn.iocoder.txgy.module.product.dal.mysql.portal.MallPortalTenantAdminMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 平台公开租户目录后台服务。
 *
 * <p>本服务只能更新门户展示元数据，不创建或修改 system_tenant、企业、店铺、
 * 商品以及任何交易数据。</p>
 */
@Service
public class MallPortalTenantAdminService {

    static final long PLATFORM_ADMIN_TENANT_ID = 1L;
    static final long LEGACY_PUBLIC_TENANT_ID = 3000L;
    static final Pattern TENANT_SLUG_PATTERN =
            Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");

    private final MallPortalTenantAdminMapper mapper;
    private final Clock clock;

    @Autowired
    public MallPortalTenantAdminService(MallPortalTenantAdminMapper mapper) {
        this(mapper, Clock.systemDefaultZone());
    }

    MallPortalTenantAdminService(MallPortalTenantAdminMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @TenantIgnore
    public PageResult<MallPortalTenantDO> getPage(MallPortalTenantPageReqVO request) {
        requirePlatformAdminTenant();
        return mapper.selectPage(request);
    }

    @TenantIgnore
    public MallPortalTenantDO get(Long id) {
        requirePlatformAdminTenant();
        MallPortalTenantDO row = mapper.selectById(id);
        if (row == null) {
            throw new ServiceException(404, "门户租户目录不存在");
        }
        return row;
    }

    @TenantIgnore
    @Transactional(rollbackFor = Exception.class)
    public void update(MallPortalTenantUpdateReqVO request) {
        requirePlatformAdminTenant();
        MallPortalTenantDO existing = mapper.selectPortalForUpdate(request.getId());
        if (existing == null) {
            throw new ServiceException(404, "门户租户目录不存在");
        }

        String normalizedSlug = normalizeSlug(request.getTenantSlug());
        MallPortalTenantDO sameSlug = mapper.selectBySlugExcludingId(normalizedSlug, existing.getId());
        if (sameSlug != null) {
            throw new ServiceException(400, "路由标识已被其他租户使用");
        }

        boolean opening = CommonStatusEnum.ENABLE.getStatus().equals(request.getOpenStatus());
        MallPortalTenantAdminMapper.SystemTenantSnapshot systemTenant =
                mapper.selectSystemTenant(existing.getTenantId());
        if (opening) {
            validateCanOpen(existing.getTenantId(), systemTenant);
        }

        MallPortalTenantDO update = new MallPortalTenantDO();
        update.setId(existing.getId());
        update.setTenantId(existing.getTenantId());
        update.setTenantSlug(normalizedSlug);
        update.setTenantName(resolveTenantName(existing, systemTenant));
        update.setTenantLogo(normalizeNullable(request.getTenantLogo()));
        update.setMainCategories(normalizeNullable(request.getMainCategories()));
        update.setOpenStatus(request.getOpenStatus());
        update.setSort(request.getSort());

        try {
            int affected = mapper.updatePortalConfig(update, currentOperator());
            if (affected != 1) {
                throw new ServiceException(409, "门户租户目录已变更，请刷新后重试");
            }
        } catch (DuplicateKeyException ex) {
            // 唯一索引是并发修改的最终保护，不向前端暴露数据库异常。
            throw new ServiceException(400, "路由标识已被其他租户使用");
        }
    }

    private void validateCanOpen(Long tenantId,
                                 MallPortalTenantAdminMapper.SystemTenantSnapshot systemTenant) {
        if (Objects.equals(tenantId, LEGACY_PUBLIC_TENANT_ID)) {
            throw new ServiceException(400, "3000 号历史公共租户不能开放到平台门户");
        }
        if (systemTenant == null || Boolean.TRUE.equals(systemTenant.getDeleted())
                || !CommonStatusEnum.ENABLE.getStatus().equals(systemTenant.getStatus())
                || systemTenant.getExpireTime() != null
                && !systemTenant.getExpireTime().isAfter(LocalDateTime.now(clock))) {
            throw new ServiceException(400, "系统租户不存在、已停用或已过期，不能开放门户");
        }
    }

    /**
     * 菜单权限不足以隔离各租户的 super_admin（该角色会绕过普通菜单授权），
     * 因而跨租户门户目录必须再显式限定平台管理租户。
     */
    static void requirePlatformAdminTenant() {
        if (!Objects.equals(TenantContextHolder.getTenantId(), PLATFORM_ADMIN_TENANT_ID)) {
            throw new ServiceException(403, "仅平台管理员可以维护租户门户目录");
        }
    }

    static String normalizeSlug(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!TENANT_SLUG_PATTERN.matcher(normalized).matches()) {
            throw new ServiceException(400, "路由标识只能使用小写字母、数字和中划线，且不能以中划线开头或结尾");
        }
        return normalized;
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String resolveTenantName(
            MallPortalTenantDO existing,
            MallPortalTenantAdminMapper.SystemTenantSnapshot systemTenant) {
        if (systemTenant == null || systemTenant.getName() == null || systemTenant.getName().isBlank()) {
            return existing.getTenantName();
        }
        return systemTenant.getName().trim();
    }

    private static String currentOperator() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return userId == null ? "0" : userId.toString();
    }
}

package cn.iocoder.txgy.module.product.service.enterprise;

import cn.iocoder.txgy.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.api.shop.dto.ProductAdminShopScopeRespDTO;
import cn.iocoder.txgy.module.product.api.shop.dto.ProductAdminAuthorizedShopRespDTO;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.ProductEnterpriseAdminMemberDO;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.ProductEnterpriseDO;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ProductShopAdminAuthorizationDO;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ProductShopDO;
import cn.iocoder.txgy.module.product.dal.mysql.enterprise.ProductEnterpriseAdminMemberMapper;
import cn.iocoder.txgy.module.product.dal.mysql.enterprise.ProductEnterpriseMapper;
import cn.iocoder.txgy.module.product.dal.mysql.shop.ProductShopAdminAuthorizationMapper;
import cn.iocoder.txgy.module.product.dal.mysql.shop.ProductShopMapper;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 商城后台经营数据范围的唯一判定入口。
 *
 * <p>菜单权限决定“能做什么”，本服务决定“能对哪些企业和店铺做”。二者必须同时通过。</p>
 */
@Service
public class ProductAdminBusinessScopeService {

    private static final String ROLE_SUPER_ADMIN = "super_admin";
    private static final String ROLE_TENANT_ADMIN = "tenant_admin";

    @Resource private PermissionCommonApi permissionApi;
    @Resource private ProductEnterpriseAdminMemberMapper enterpriseMemberMapper;
    @Resource private ProductShopAdminAuthorizationMapper shopAuthorizationMapper;
    @Resource private ProductEnterpriseMapper enterpriseMapper;
    @Resource private ProductShopMapper shopMapper;
    @Resource private AdminUserApi adminUserApi;

    public Long currentAdminUserId() {
        var user = SecurityFrameworkUtils.getLoginUser();
        if (user == null || !Objects.equals(user.getUserType(), UserTypeEnum.ADMIN.getValue())) {
            throw forbidden("请使用后台管理账号");
        }
        return user.getId();
    }

    public Scope currentScope() {
        return scope(currentAdminUserId());
    }

    public Scope scope(Long adminUserId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (adminUserId == null || adminUserId <= 0) throw forbidden("后台账号无效");
        var admin = adminUserApi.getUser(adminUserId).getCheckedData();
        if (admin == null || !Integer.valueOf(0).equals(admin.getStatus())) {
            throw forbidden("后台账号不存在、已停用或不属于当前租户");
        }
        boolean unrestricted = Boolean.TRUE.equals(permissionApi.hasAnyRoles(adminUserId,
                ROLE_SUPER_ADMIN, ROLE_TENANT_ADMIN).getCheckedData());

        List<ProductEnterpriseAdminMemberDO> memberships = enterpriseMemberMapper.selectList(
                new LambdaQueryWrapperX<ProductEnterpriseAdminMemberDO>()
                        .eq(ProductEnterpriseAdminMemberDO::getAdminUserId, adminUserId)
                        .eq(ProductEnterpriseAdminMemberDO::getStatus, 0));
        if (memberships.isEmpty()) return new Scope(tenantId, adminUserId, unrestricted, Set.of(), Set.of());

        Set<Long> candidateEnterprises = new LinkedHashSet<>();
        Set<Long> enterpriseWide = new LinkedHashSet<>();
        for (ProductEnterpriseAdminMemberDO row : memberships) {
            if (row.getEnterpriseId() == null) continue;
            candidateEnterprises.add(row.getEnterpriseId());
            if (Objects.equals(row.getScopeType(), ProductEnterpriseAdminMemberDO.SCOPE_ENTERPRISE)) {
                enterpriseWide.add(row.getEnterpriseId());
            }
        }
        if (candidateEnterprises.isEmpty()) return new Scope(tenantId, adminUserId, unrestricted, Set.of(), Set.of());
        Set<Long> enterprises = new LinkedHashSet<>();
        for (ProductEnterpriseDO enterprise : enterpriseMapper.selectList(
                new LambdaQueryWrapperX<ProductEnterpriseDO>()
                        .in(ProductEnterpriseDO::getId, candidateEnterprises)
                        .eq(ProductEnterpriseDO::getAuditStatus, 1))) {
            if (Objects.equals(enterprise.getTenantId(), tenantId)) enterprises.add(enterprise.getId());
        }
        enterpriseWide.retainAll(enterprises);

        Set<Long> shops = new LinkedHashSet<>();
        if (!enterpriseWide.isEmpty()) {
            shopMapper.selectList(new LambdaQueryWrapperX<ProductShopDO>()
                    .in(ProductShopDO::getEnterpriseId, enterpriseWide)
                    .eq(ProductShopDO::getStatus, 0)).forEach(shop -> {
                if (Objects.equals(shop.getTenantId(), tenantId)) shops.add(shop.getId());
            });
        }
        List<ProductShopAdminAuthorizationDO> grants = shopAuthorizationMapper.selectList(
                new LambdaQueryWrapperX<ProductShopAdminAuthorizationDO>()
                        .eq(ProductShopAdminAuthorizationDO::getAdminUserId, adminUserId)
                        .eq(ProductShopAdminAuthorizationDO::getStatus, 0));
        if (!grants.isEmpty()) {
            Set<Long> candidateShops = new LinkedHashSet<>();
            grants.stream().filter(grant -> enterprises.contains(grant.getEnterpriseId()))
                    .map(ProductShopAdminAuthorizationDO::getShopId).filter(Objects::nonNull)
                    .forEach(candidateShops::add);
            if (!candidateShops.isEmpty()) {
                shopMapper.selectList(new LambdaQueryWrapperX<ProductShopDO>()
                        .in(ProductShopDO::getId, candidateShops)
                        .eq(ProductShopDO::getStatus, 0)).stream()
                        .filter(shop -> Objects.equals(shop.getTenantId(), tenantId)
                                && enterprises.contains(shop.getEnterpriseId())
                                && grants.stream().anyMatch(grant -> Objects.equals(grant.getShopId(), shop.getId())
                                && Objects.equals(grant.getEnterpriseId(), shop.getEnterpriseId())))
                        .map(ProductShopDO::getId).forEach(shops::add);
            }
        }
        // 即使账号同时具有租户治理角色，也保留它显式拥有的企业/店铺经营范围。
        // unrestricted 仅供查看与审核使用，不能自动转换成商品经营权限。
        return new Scope(tenantId, adminUserId, unrestricted, Set.copyOf(enterprises), Set.copyOf(shops));
    }

    public ProductShopDO requireShop(Long adminUserId, Long shopId) {
        if (shopId == null) throw forbidden("请选择获授权店铺");
        Scope scope = scope(adminUserId);
        ProductShopDO shop = shopMapper.selectById(shopId);
        if (shop == null || !Objects.equals(shop.getTenantId(), scope.tenantId())
                || !Objects.equals(shop.getStatus(), 0)
                || (!scope.unrestricted() && !scope.shopIds().contains(shopId))) {
            throw forbidden("未获得该店铺经营权限");
        }
        ProductEnterpriseDO enterprise = shop.getEnterpriseId() == null ? null
                : enterpriseMapper.selectById(shop.getEnterpriseId());
        if (enterprise == null || !Objects.equals(enterprise.getTenantId(), scope.tenantId())
                || !Objects.equals(enterprise.getAuditStatus(), 1)) {
            throw forbidden("店铺所属企业资质已失效");
        }
        return shop;
    }

    /**
     * 要求显式的商城经营授权。
     *
     * <p>租户管理员和超级管理员的 unrestricted 只代表治理侧可查看/审核本租户数据，
     * 不能据此新增、修改、上下架或删除商家商品。若治理账号确需兼任经营人员，也必须
     * 通过企业成员与店铺授权留下明确关系。</p>
     */
    public ProductShopDO requireMerchantShop(Long adminUserId, Long shopId) {
        if (shopId == null) throw forbidden("经营商品必须选择已授权店铺");
        Scope scope = scope(adminUserId);
        if (scope.unrestricted()) {
            throw forbidden("租户治理账号仅可查看和审核商品，不能代替企业经营账号操作");
        }
        if (!scope.shopIds().contains(shopId)) throw forbidden("未获得该店铺经营权限");
        ProductShopDO shop = shopMapper.selectById(shopId);
        if (shop == null || !Objects.equals(shop.getTenantId(), scope.tenantId())
                || !Objects.equals(shop.getStatus(), 0)) {
            throw forbidden("店铺不存在、已停用或不属于当前租户");
        }
        ProductEnterpriseDO enterprise = shop.getEnterpriseId() == null ? null
                : enterpriseMapper.selectById(shop.getEnterpriseId());
        if (enterprise == null || !Objects.equals(enterprise.getTenantId(), scope.tenantId())
                || !Objects.equals(enterprise.getAuditStatus(), 1)) {
            throw forbidden("店铺所属企业资质已失效");
        }
        return shop;
    }

    /**
     * 校验后台经营账号能否读取指定企业/店铺的历史记录。
     *
     * <p>与 {@link #requireMerchantShop(Long, Long)} 不同，本方法不要求企业资质和店铺
     * 当前仍有效；停用状态只阻止新增或修改，不能抹掉账号在有效授权关系下已经参与的
     * 报价历史。租户治理账号仍不能借历史接口越过显式经营授权。</p>
     */
    public ProductShopDO requireMerchantShopHistory(Long adminUserId, Long shopId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (adminUserId == null || adminUserId <= 0 || shopId == null) {
            throw forbidden("历史报价经营主体无效");
        }
        var admin = adminUserApi.getUser(adminUserId).getCheckedData();
        if (admin == null || !Integer.valueOf(0).equals(admin.getStatus())) {
            throw forbidden("后台账号不存在、已停用或不属于当前租户");
        }
        boolean unrestricted = Boolean.TRUE.equals(permissionApi.hasAnyRoles(adminUserId,
                ROLE_SUPER_ADMIN, ROLE_TENANT_ADMIN).getCheckedData());
        if (unrestricted) {
            throw forbidden("租户治理账号不能代替企业经营账号读取销售报价");
        }

        ProductShopDO shop = shopMapper.selectById(shopId);
        if (shop == null || shop.getEnterpriseId() == null || !Objects.equals(shop.getTenantId(), tenantId)) {
            throw forbidden("历史报价所属店铺不存在或不属于当前租户");
        }
        Long enterpriseId = shop.getEnterpriseId();

        ProductEnterpriseAdminMemberDO membership = enterpriseMemberMapper.selectOne(
                new LambdaQueryWrapperX<ProductEnterpriseAdminMemberDO>()
                        .eq(ProductEnterpriseAdminMemberDO::getAdminUserId, adminUserId)
                        .eq(ProductEnterpriseAdminMemberDO::getEnterpriseId, enterpriseId)
                        .eq(ProductEnterpriseAdminMemberDO::getStatus, 0)
                        .last("LIMIT 1"));
        if (membership == null || !Objects.equals(membership.getTenantId(), tenantId)) {
            throw forbidden("未获得该历史报价所属企业的经营权限");
        }
        if (Objects.equals(membership.getScopeType(), ProductEnterpriseAdminMemberDO.SCOPE_ENTERPRISE)) {
            return shop;
        }
        ProductShopAdminAuthorizationDO grant = shopAuthorizationMapper.selectOne(
                new LambdaQueryWrapperX<ProductShopAdminAuthorizationDO>()
                        .eq(ProductShopAdminAuthorizationDO::getAdminUserId, adminUserId)
                        .eq(ProductShopAdminAuthorizationDO::getEnterpriseId, enterpriseId)
                        .eq(ProductShopAdminAuthorizationDO::getShopId, shopId)
                        .eq(ProductShopAdminAuthorizationDO::getStatus, 0)
                        .last("LIMIT 1"));
        if (grant == null || !Objects.equals(grant.getTenantId(), tenantId)) {
            throw forbidden("未获得该历史报价所属店铺的经营权限");
        }
        return shop;
    }

    /** 判断后台账号是否与指定企业存在有效经营成员关系，供审核职责分离校验。 */
    public boolean isEnterpriseBusinessMember(Long adminUserId, Long enterpriseId) {
        if (enterpriseId == null) return false;
        return scope(adminUserId).enterpriseIds().contains(enterpriseId);
    }

    /** 平台店铺档案管理使用；租户管理员可查看和恢复本租户停用店铺，经营账号仍只允许有效授权店铺。 */
    public ProductShopDO requireShopResource(Long adminUserId, Long shopId) {
        if (shopId == null) throw forbidden("请选择店铺");
        Scope scope = scope(adminUserId);
        ProductShopDO shop = shopMapper.selectById(shopId);
        if (shop == null || !Objects.equals(shop.getTenantId(), scope.tenantId())
                || (!scope.unrestricted() && !scope.shopIds().contains(shopId))) {
            throw forbidden("未获得该店铺管理权限");
        }
        return shop;
    }

    public ProductShopDO requireCurrentShopResource(Long shopId) {
        return requireShopResource(currentAdminUserId(), shopId);
    }

    public ProductShopDO requireCurrentShop(Long shopId) {
        return requireShop(currentAdminUserId(), shopId);
    }

    public void requireEnterprise(Long adminUserId, Long enterpriseId) {
        Scope scope = scope(adminUserId);
        ProductEnterpriseDO enterprise = enterpriseId == null ? null : enterpriseMapper.selectById(enterpriseId);
        if (enterprise == null || !Objects.equals(enterprise.getTenantId(), scope.tenantId())
                || !Objects.equals(enterprise.getAuditStatus(), 1)
                || (!scope.unrestricted() && !scope.enterpriseIds().contains(enterpriseId))) {
            throw forbidden("未获得该企业经营权限");
        }
    }

    public void requireTenantManager(Long adminUserId) {
        if (!scope(adminUserId).unrestricted()) throw forbidden("该操作仅限租户审核人员");
    }

    public void requireEnterpriseManager(Long adminUserId, Long enterpriseId) {
        Scope scope = scope(adminUserId);
        requireEnterprise(adminUserId, enterpriseId);
        if (scope.unrestricted()) return;
        ProductEnterpriseAdminMemberDO membership = enterpriseMemberMapper.selectOne(
                new LambdaQueryWrapperX<ProductEnterpriseAdminMemberDO>()
                        .eq(ProductEnterpriseAdminMemberDO::getAdminUserId, adminUserId)
                        .eq(ProductEnterpriseAdminMemberDO::getEnterpriseId, enterpriseId)
                        .eq(ProductEnterpriseAdminMemberDO::getScopeType,
                                ProductEnterpriseAdminMemberDO.SCOPE_ENTERPRISE)
                        .eq(ProductEnterpriseAdminMemberDO::getStatus, 0));
        if (membership == null) throw forbidden("仅本企业管理员或租户管理员可以维护企业资料");
    }

    public List<ProductShopDO> authorizedShops(Long adminUserId) {
        Scope scope = scope(adminUserId);
        if (scope.unrestricted()) {
            return shopMapper.selectList(new LambdaQueryWrapperX<ProductShopDO>()
                    .eq(ProductShopDO::getStatus, 0).orderByAsc(ProductShopDO::getId));
        }
        if (scope.shopIds().isEmpty()) return List.of();
        return shopMapper.selectList(new LambdaQueryWrapperX<ProductShopDO>()
                .in(ProductShopDO::getId, scope.shopIds())
                .eq(ProductShopDO::getStatus, 0).orderByAsc(ProductShopDO::getId));
    }

    public List<ProductAdminAuthorizedShopRespDTO> authorizedShopOptions(Long adminUserId) {
        return authorizedShops(adminUserId).stream().map(shop -> {
            ProductAdminAuthorizedShopRespDTO dto = new ProductAdminAuthorizedShopRespDTO();
            dto.setId(shop.getId());
            dto.setEnterpriseId(shop.getEnterpriseId());
            dto.setName(shop.getName());
            dto.setLogoUrl(shop.getLogoUrl());
            dto.setStatus(shop.getStatus());
            return dto;
        }).toList();
    }

    public ProductAdminShopScopeRespDTO toDto(Scope scope) {
        ProductAdminShopScopeRespDTO dto = new ProductAdminShopScopeRespDTO();
        dto.setTenantId(scope.tenantId()); dto.setAdminUserId(scope.adminUserId());
        dto.setUnrestricted(scope.unrestricted()); dto.setEnterpriseIds(scope.enterpriseIds());
        dto.setShopIds(scope.shopIds()); return dto;
    }

    private static ServiceException forbidden(String message) {
        return new ServiceException(403, message);
    }

    public record Scope(Long tenantId, Long adminUserId, boolean unrestricted,
                        Set<Long> enterpriseIds, Set<Long> shopIds) {
        public boolean allowsShop(Long shopId) {
            return unrestricted || shopIds.contains(shopId);
        }
    }
}

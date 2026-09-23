package cn.iocoder.txgy.module.product.service.enterprise;

import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.product.controller.admin.enterprise.vo.ProductAdminBusinessMemberRespVO;
import cn.iocoder.txgy.module.product.controller.admin.enterprise.vo.ProductAdminBusinessMemberSaveReqVO;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.ProductEnterpriseAdminMemberDO;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ProductShopAdminAuthorizationDO;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ProductShopDO;
import cn.iocoder.txgy.module.product.dal.mysql.enterprise.ProductEnterpriseAdminMemberMapper;
import cn.iocoder.txgy.module.product.dal.mysql.shop.ProductShopAdminAuthorizationMapper;
import cn.iocoder.txgy.module.product.dal.mysql.shop.ProductShopMapper;
import cn.iocoder.txgy.module.product.service.audit.ProductBusinessOperationAuditService;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService.invalid;

@Service
public class ProductAdminBusinessAuthorizationService {

    @Resource private ProductAdminBusinessScopeService scopeService;
    @Resource private ProductEnterpriseAdminMemberMapper memberMapper;
    @Resource private ProductShopAdminAuthorizationMapper authorizationMapper;
    @Resource private ProductShopMapper shopMapper;
    @Resource private AdminUserApi adminUserApi;
    @Resource private ProductBusinessOperationAuditService operationAuditService;

    public List<ProductAdminBusinessMemberRespVO> list(Long callerAdminUserId, Long enterpriseId) {
        requireGrantManager(callerAdminUserId, enterpriseId);
        List<ProductEnterpriseAdminMemberDO> members = memberMapper.selectList(
                new LambdaQueryWrapperX<ProductEnterpriseAdminMemberDO>()
                        .eq(ProductEnterpriseAdminMemberDO::getEnterpriseId, enterpriseId)
                        .orderByAsc(ProductEnterpriseAdminMemberDO::getAdminUserId));
        if (members.isEmpty()) return List.of();
        Set<Long> adminIds = members.stream().map(ProductEnterpriseAdminMemberDO::getAdminUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, AdminUserRespDTO> admins = adminUserApi.getUserList(adminIds).getCheckedData().stream()
                .collect(Collectors.toMap(AdminUserRespDTO::getId, Function.identity()));
        Map<Long, Set<Long>> shopIds = authorizationMapper.selectList(
                        new LambdaQueryWrapperX<ProductShopAdminAuthorizationDO>()
                                .eq(ProductShopAdminAuthorizationDO::getEnterpriseId, enterpriseId)
                                .eq(ProductShopAdminAuthorizationDO::getStatus, 0)).stream()
                .collect(Collectors.groupingBy(ProductShopAdminAuthorizationDO::getAdminUserId,
                        Collectors.mapping(ProductShopAdminAuthorizationDO::getShopId,
                                Collectors.toCollection(LinkedHashSet::new))));
        return members.stream().map(member -> {
            ProductAdminBusinessMemberRespVO result = new ProductAdminBusinessMemberRespVO();
            result.setId(member.getId()); result.setAdminUserId(member.getAdminUserId());
            result.setEnterpriseId(member.getEnterpriseId()); result.setScopeType(member.getScopeType());
            result.setStatus(member.getStatus()); result.setShopIds(shopIds.getOrDefault(member.getAdminUserId(), Set.of()));
            AdminUserRespDTO admin = admins.get(member.getAdminUserId());
            if (admin != null) { result.setNickname(admin.getNickname()); result.setMobile(admin.getMobile()); }
            return result;
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(Long callerAdminUserId, ProductAdminBusinessMemberSaveReqVO request) {
        requireGrantManager(callerAdminUserId, request.getEnterpriseId());
        if (!Set.of(ProductEnterpriseAdminMemberDO.SCOPE_ENTERPRISE, ProductEnterpriseAdminMemberDO.SCOPE_SHOP)
                .contains(request.getScopeType()) || !Set.of(0, 1).contains(request.getStatus())) {
            throw invalid("企业成员范围或状态不正确");
        }
        boolean tenantManager = scopeService.scope(callerAdminUserId).unrestricted();
        if (!tenantManager && Objects.equals(callerAdminUserId, request.getAdminUserId())) {
            throw invalid("企业管理员不能修改自己的经营范围，请联系租户管理员");
        }
        ProductEnterpriseAdminMemberDO membership = memberMapper.selectOne(
                new LambdaQueryWrapperX<ProductEnterpriseAdminMemberDO>()
                        .eq(ProductEnterpriseAdminMemberDO::getAdminUserId, request.getAdminUserId())
                        .eq(ProductEnterpriseAdminMemberDO::getEnterpriseId, request.getEnterpriseId()));
        if (request.getStatus() == 0) {
            validateTargetAdmin(request.getAdminUserId());
        } else if (membership == null) {
            // 停用只能作用于当前租户内已经存在的授权，避免写入任意 adminUserId 的幽灵关系。
            throw invalid("企业成员授权不存在，无需停用");
        }
        ProductEnterpriseAdminMemberDO other = memberMapper.selectOne(
                new LambdaQueryWrapperX<ProductEnterpriseAdminMemberDO>()
                        .eq(ProductEnterpriseAdminMemberDO::getAdminUserId, request.getAdminUserId())
                        .eq(ProductEnterpriseAdminMemberDO::getStatus, 0)
                        .ne(ProductEnterpriseAdminMemberDO::getEnterpriseId, request.getEnterpriseId())
                        .last("LIMIT 1"));
        if (other != null) throw invalid("该后台账号已关联其他企业，不能跨企业授权");

        Set<Long> requestedShopIds = new LinkedHashSet<>(Objects.requireNonNullElse(request.getShopIds(), Set.of()));
        if (request.getStatus() == 0 && request.getScopeType() == ProductEnterpriseAdminMemberDO.SCOPE_SHOP) {
            validateShops(request.getEnterpriseId(), requestedShopIds);
        } else if (!requestedShopIds.isEmpty()) {
            throw invalid("企业全范围或停用成员不能同时提交店铺清单");
        }

        if (membership == null) {
            membership = new ProductEnterpriseAdminMemberDO();
            membership.setAdminUserId(request.getAdminUserId()); membership.setEnterpriseId(request.getEnterpriseId());
            membership.setScopeType(request.getScopeType()); membership.setStatus(request.getStatus());
            memberMapper.insert(membership);
        } else {
            membership.setScopeType(request.getScopeType()); membership.setStatus(request.getStatus());
            memberMapper.updateById(membership);
        }
        replaceShopGrants(request.getAdminUserId(), request.getEnterpriseId(), requestedShopIds);
        operationAuditService.success(request.getEnterpriseId(), null, "ADMIN", callerAdminUserId,
                "BUSINESS_MEMBER_GRANT", "ADMIN_USER", request.getAdminUserId(),
                "scopeType=" + request.getScopeType() + ",status=" + request.getStatus()
                        + ",shopIds=" + requestedShopIds.stream().sorted().toList());
    }

    private void requireGrantManager(Long callerAdminUserId, Long enterpriseId) {
        ProductAdminBusinessScopeService.Scope scope = scopeService.scope(callerAdminUserId);
        scopeService.requireEnterprise(callerAdminUserId, enterpriseId);
        if (scope.unrestricted()) return;
        ProductEnterpriseAdminMemberDO manager = memberMapper.selectOne(
                new LambdaQueryWrapperX<ProductEnterpriseAdminMemberDO>()
                        .eq(ProductEnterpriseAdminMemberDO::getAdminUserId, callerAdminUserId)
                        .eq(ProductEnterpriseAdminMemberDO::getEnterpriseId, enterpriseId)
                        .eq(ProductEnterpriseAdminMemberDO::getScopeType, ProductEnterpriseAdminMemberDO.SCOPE_ENTERPRISE)
                        .eq(ProductEnterpriseAdminMemberDO::getStatus, 0));
        if (manager == null) throw invalid("仅本企业管理员或租户管理员可以维护员工经营范围");
    }

    private void validateTargetAdmin(Long adminUserId) {
        AdminUserRespDTO admin = adminUserApi.getUser(adminUserId).getCheckedData();
        if (admin == null || !Integer.valueOf(0).equals(admin.getStatus())) {
            throw invalid("所选后台账号不存在、已停用或不属于当前租户");
        }
    }

    private void validateShops(Long enterpriseId, Set<Long> shopIds) {
        if (shopIds.isEmpty()) throw invalid("店铺范围成员至少选择一家店铺");
        List<ProductShopDO> shops = shopMapper.selectBatchIds(shopIds);
        if (shops.size() != shopIds.size() || shops.stream().anyMatch(shop ->
                !Objects.equals(shop.getEnterpriseId(), enterpriseId) || !Integer.valueOf(0).equals(shop.getStatus()))) {
            throw invalid("只能授权本企业的启用店铺");
        }
    }

    private void replaceShopGrants(Long adminUserId, Long enterpriseId, Set<Long> shopIds) {
        List<ProductShopAdminAuthorizationDO> existing = authorizationMapper.selectList(
                new LambdaQueryWrapperX<ProductShopAdminAuthorizationDO>()
                        .eq(ProductShopAdminAuthorizationDO::getAdminUserId, adminUserId)
                        .eq(ProductShopAdminAuthorizationDO::getEnterpriseId, enterpriseId));
        Map<Long, ProductShopAdminAuthorizationDO> byShop = existing.stream()
                .collect(Collectors.toMap(ProductShopAdminAuthorizationDO::getShopId, Function.identity()));
        for (ProductShopAdminAuthorizationDO grant : existing) {
            grant.setStatus(shopIds.contains(grant.getShopId()) ? 0 : 1);
            authorizationMapper.updateById(grant);
        }
        for (Long shopId : shopIds) {
            if (byShop.containsKey(shopId)) continue;
            ProductShopAdminAuthorizationDO grant = new ProductShopAdminAuthorizationDO();
            grant.setAdminUserId(adminUserId); grant.setEnterpriseId(enterpriseId);
            grant.setShopId(shopId); grant.setStatus(0); authorizationMapper.insert(grant);
        }
    }
}

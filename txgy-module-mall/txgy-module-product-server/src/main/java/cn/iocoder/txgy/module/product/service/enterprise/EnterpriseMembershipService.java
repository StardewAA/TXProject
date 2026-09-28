package cn.iocoder.txgy.module.product.service.enterprise;

import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.*;
import cn.iocoder.txgy.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.member.api.user.MemberUserApi;
import cn.iocoder.txgy.module.product.controller.app.enterprise.EnterpriseApplyReqVO;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.api.tenant.MallTenantWriteGuard;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.*;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ProductShopDO;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ProductShopAdminAuthorizationDO;
import cn.iocoder.txgy.module.product.dal.mysql.enterprise.*;
import cn.iocoder.txgy.module.product.dal.mysql.shop.ProductShopAdminAuthorizationMapper;
import cn.iocoder.txgy.module.product.service.audit.ProductBusinessOperationAuditService;
import cn.iocoder.txgy.module.product.service.shop.ProductShopService;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

@Service
public class EnterpriseMembershipService {
    @Resource private EnterpriseApplicationMapper mapper;
    @Resource private ProductEnterpriseMapper enterpriseMapper;
    @Resource private MemberUserApi memberApi;
    @Resource private ProductShopService shopService;
    @Resource private AdminUserApi adminUserApi;
    @Resource private PermissionCommonApi permissionApi;
    @Resource private ProductEnterpriseAdminMemberMapper enterpriseAdminMemberMapper;
    @Resource private ProductShopAdminAuthorizationMapper shopAdminAuthorizationMapper;
    @Resource private ProductBusinessOperationAuditService operationAuditService;

    public static ServiceException invalid(String message) { return new ServiceException(400, message); }
    public static Long memberId() {
        var user = SecurityFrameworkUtils.getLoginUser();
        if (user == null || !Objects.equals(user.getUserType(), UserTypeEnum.MEMBER.getValue()))
            throw new ServiceException(403, "请使用商城会员账号");
        return user.getId();
    }
    public EnterpriseApplicationDO mine(Long memberId) {
        return mapper.selectOne(new LambdaQueryWrapperX<EnterpriseApplicationDO>()
                .eq(EnterpriseApplicationDO::getMemberId, memberId));
    }
    private EnterpriseApplicationDO locked(Long id) {
        var row = mapper.selectOne(new LambdaQueryWrapperX<EnterpriseApplicationDO>()
                .eq(EnterpriseApplicationDO::getId, id).last("FOR UPDATE"));
        if (row == null) throw invalid("申请不存在");
        return row;
    }
    private EnterpriseApplicationDO requireApprovedMember(Long memberId) {
        var row = requireApprovedMemberHistory(memberId);
        var company = enterpriseMapper.selectById(row.getEnterpriseId());
        if (company == null || !Integer.valueOf(1).equals(company.getAuditStatus()))
            throw new ServiceException(403, "企业认证已失效，请联系平台");
        return row;
    }

    /**
     * 历史业务读取只验证账号及已审核成员关系，不要求企业仍处于可经营状态。
     * 企业停用会阻断后续写操作，但不能让原成员已经参与的报价历史消失。
     */
    private EnterpriseApplicationDO requireApprovedMemberHistory(Long memberId) {
        var member = memberApi.getUser(memberId).getCheckedData();
        if (member == null || !Integer.valueOf(0).equals(member.getStatus())) {
            throw new ServiceException(403, "商城会员账号不存在或已停用");
        }
        var row = mine(memberId);
        if (row == null || !Integer.valueOf(1).equals(row.getStatus()) || row.getEnterpriseId() == null)
            throw new ServiceException(403, "请先完善企业资料并等待后台审核通过");
        if (!Objects.equals(member.getMemberIdentity(), row.getIdentity())) {
            throw new ServiceException(403, "会员身份与企业成员关系不一致，请联系平台核验");
        }
        return row;
    }
    public EnterpriseApplicationDO requireBuyer(Long memberId) {
        var row = requireApprovedMember(memberId);
        if (!"buyer".equals(row.getIdentity())) {
            throw new ServiceException(403, "当前账号不是企业采购成员");
        }
        return row;
    }
    /** 旧用户端销售入口迁移期兼容使用；新供应经营统一使用后台 system_user。 */
    public EnterpriseApplicationDO requireSupplier(Long memberId) {
        var row = requireApprovedMember(memberId);
        if (!"supplier".equals(row.getIdentity())) {
            throw new ServiceException(403, "当前账号不是供应企业经营成员");
        }
        return row;
    }

    public EnterpriseApplicationDO requireBuyerHistory(Long memberId) {
        var row = requireApprovedMemberHistory(memberId);
        if (!"buyer".equals(row.getIdentity())) {
            throw new ServiceException(403, "当前账号不是企业采购成员");
        }
        return row;
    }

    public EnterpriseApplicationDO requireSupplierHistory(Long memberId) {
        var row = requireApprovedMemberHistory(memberId);
        if (!"supplier".equals(row.getIdentity())) {
            throw new ServiceException(403, "当前账号不是供应企业经营成员");
        }
        return row;
    }

    /** 历史读取按审核时固化的企业、店铺关系授权，不要求店铺当前仍启用。 */
    public EnterpriseApplicationDO requireSupplierShopHistory(Long memberId, Long enterpriseId, Long shopId) {
        var row = requireSupplierHistory(memberId);
        if (enterpriseId == null || shopId == null
                || !Objects.equals(row.getEnterpriseId(), enterpriseId)
                || !Objects.equals(row.getShopId(), shopId)) {
            throw new ServiceException(403, "报价不属于当前供应企业或店铺");
        }
        return row;
    }
    public List<ProductShopDO> salesShops(Long memberId) {
        var row = requireSupplier(memberId);
        if (row.getShopId() == null) return List.of();
        return shopService.getShopList().stream()
                .filter(s -> Objects.equals(s.getId(), row.getShopId())
                        && Objects.equals(s.getEnterpriseId(), row.getEnterpriseId())
                        && Integer.valueOf(0).equals(s.getStatus())).toList();
    }
    public void requireShop(Long memberId, Long shopId) {
        if (shopId == null || salesShops(memberId).stream().noneMatch(s -> s.getId().equals(shopId)))
            throw new ServiceException(403, "仅审核通过并关联店铺的供应商可管理该店铺");
    }
    @Transactional(rollbackFor=Exception.class)
    public void bindShop(Long applicationId, Long shopId) {
        requireWritableTenant();
        var row = locked(applicationId);
        requireSupplier(row.getMemberId());
        var shop = shopService.getShop(shopId);
        if (shop == null || !Objects.equals(shop.getEnterpriseId(), row.getEnterpriseId())
                || !Integer.valueOf(0).equals(shop.getStatus()))
            throw invalid("仅可关联该企业下的启用店铺");
        row.setShopId(shopId);
        mapper.updateById(row);
    }
    @Transactional(rollbackFor=Exception.class)
    public Long apply(Long memberId, EnterpriseApplyReqVO req) {
        requireWritableTenant();
        var user = memberApi.getUser(memberId).getCheckedData();
        if (user == null || !Set.of("buyer", "supplier").contains(
                Objects.toString(user.getMemberIdentity(), "")))
            throw invalid("当前账号尚未申请企业身份，请联系平台办理，原有购物功能不受影响");
        validateLicense(req.getLicenseData());
        var current = mine(memberId);
        if (current != null) {
            current = locked(current.getId());
            if (!Integer.valueOf(2).equals(current.getStatus()))
                throw invalid("仅驳回申请可修改重提");
        }
        var row = BeanUtils.toBean(req, EnterpriseApplicationDO.class);
        row.setMemberId(memberId);
        row.setIdentity(user.getMemberIdentity());
        row.setStatus(0);
        row.setAuditRemark("");
        if (current == null) mapper.insert(row);
        else { row.setId(current.getId()); mapper.updateById(row); }
        return row.getId();
    }
    private void validateLicense(String data) {
        if (data == null || data.length() > 1500000 ||
                !(data.startsWith("data:image/png;base64,") || data.startsWith("data:image/jpeg;base64,")))
            throw invalid("营业执照仅支持1MB以内的PNG或JPEG图片");
        try {
            byte[] bytes = Base64.getDecoder().decode(data.substring(data.indexOf(',') + 1));
            if (bytes.length > 1048576) throw invalid("营业执照不能超过1MB");
            try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw invalid("营业执照图片无效");
                var reader = readers.next();
                try {
                    reader.setInput(stream);
                    if ((long)reader.getWidth(0) * reader.getHeight(0) > 25000000L)
                        throw invalid("营业执照图片像素过大");
                } finally { reader.dispose(); }
            }
        } catch (ServiceException e) { throw e; }
        catch (Exception e) { throw invalid("营业执照图片无法读取"); }
    }
    public PageResult<EnterpriseApplicationDO> page(PageParam page, Integer status) {
        var result = mapper.selectPage(page, new LambdaQueryWrapperX<EnterpriseApplicationDO>()
                .eqIfPresent(EnterpriseApplicationDO::getStatus, status).orderByDesc(EnterpriseApplicationDO::getId));
        result.getList().forEach(r -> r.setLicenseData(null));
        return result;
    }
    public EnterpriseApplicationDO get(Long id) { return mapper.selectById(id); }

    @Transactional(rollbackFor=Exception.class)
    public void audit(Long id, Integer status, String remark, Long existingEnterpriseId,
                      Long adminUserId, Long auditor) {
        requireWritableTenant();
        if (!Set.of(1, 2).contains(status)) throw invalid("审核结果不正确");
        if (remark == null || remark.isBlank() || remark.length() > 500) throw invalid("请填写500字以内审核意见");
        var row = locked(id);
        if (!Integer.valueOf(0).equals(row.getStatus())) throw invalid("申请已经审核，请刷新");
        if (status == 1) {
            ProductEnterpriseDO company;
            if (existingEnterpriseId != null) {
                company = enterpriseMapper.selectById(existingEnterpriseId);
                if (company == null || !row.getCreditCode().equals(company.getCreditCode())
                        || !Integer.valueOf(1).equals(company.getAuditStatus()))
                    throw invalid("仅可关联信用代码一致且审核通过的企业");
            } else {
                if (enterpriseMapper.selectOne(new LambdaQueryWrapperX<ProductEnterpriseDO>()
                        .eq(ProductEnterpriseDO::getCreditCode, row.getCreditCode()).last("FOR UPDATE")) != null)
                    throw invalid("企业已存在，请核实申请人身份后填写已有企业编号关联，不会自动认领企业");
                company = new ProductEnterpriseDO();
                company.setName(row.getName()); company.setCreditCode(row.getCreditCode());
                company.setContactName(row.getContactName()); company.setContactMobile(row.getContactMobile());
                company.setAddress(row.getAddress()); company.setIntroduction(row.getIntroduction());
                // 私有资质保留在申请记录中，不能复制进公开店铺资料。
                company.setAuditStatus(1); company.setAuditRemark(remark);
                enterpriseMapper.insert(company);
            }
            row.setEnterpriseId(company.getId());
            if ("supplier".equals(row.getIdentity())) {
                if (adminUserId == null) {
                    throw invalid("供应企业审核通过时必须选择当前租户内的后台企业管理员账号");
                }
                validateAdminUser(adminUserId);
                ProductShopDO shop = shopService.getShopByEnterpriseId(company.getId());
                if (shop == null) {
                    shop = new ProductShopDO();
                    shop.setEnterpriseId(company.getId());
                    shop.setName(defaultShopName(company.getName()));
                    shop.setContactName(row.getContactName());
                    shop.setContactMobile(row.getContactMobile());
                    shop.setIntroduction(row.getIntroduction());
                    shop.setStatus(0);
                    row.setShopId(shopService.createShop(shop));
                } else {
                    if (!Integer.valueOf(0).equals(shop.getStatus())) {
                        throw invalid("该企业店铺已停用，请先在企业店铺中启用后再通过申请");
                    }
                    row.setShopId(shop.getId());
                }
                bindEnterpriseAdmin(adminUserId, company.getId(), row.getShopId());
                row.setAdminUserId(adminUserId);
            }
        }
        row.setStatus(status); row.setAuditRemark(remark); row.setAuditorId(auditor);
        row.setAuditTime(LocalDateTime.now()); mapper.updateById(row);
        operationAuditService.success(row.getEnterpriseId(), row.getShopId(), "ADMIN", auditor,
                status == 1 ? "ENTERPRISE_APPLICATION_APPROVE" : "ENTERPRISE_APPLICATION_REJECT",
                "ENTERPRISE_APPLICATION", row.getId(),
                "identity=" + Objects.toString(row.getIdentity(), "")
                        + ",adminUserId=" + Objects.toString(row.getAdminUserId(), ""));
    }

    private void validateAdminUser(Long adminUserId) {
        // AdminUserApi.getUser 受当前租户拦截器约束，跨租户编号不会返回可用账号。
        var admin = adminUserApi.getUser(adminUserId).getCheckedData();
        if (admin == null || !Integer.valueOf(0).equals(admin.getStatus())) {
            throw invalid("所选后台账号不存在、已停用或不属于当前租户");
        }
        if (!Boolean.TRUE.equals(permissionApi.hasAnyRoles(adminUserId, "mall_enterprise_admin")
                .getCheckedData())) {
            throw invalid("所选后台账号尚未分配商城企业管理员角色");
        }
    }

    private void bindEnterpriseAdmin(Long adminUserId, Long enterpriseId, Long shopId) {
        ProductEnterpriseAdminMemberDO other = enterpriseAdminMemberMapper.selectOne(
                new LambdaQueryWrapperX<ProductEnterpriseAdminMemberDO>()
                        .eq(ProductEnterpriseAdminMemberDO::getAdminUserId, adminUserId)
                        .eq(ProductEnterpriseAdminMemberDO::getStatus, 0)
                        .ne(ProductEnterpriseAdminMemberDO::getEnterpriseId, enterpriseId)
                        .last("LIMIT 1"));
        if (other != null) throw invalid("该后台账号已关联其他企业，不能跨企业复用");

        ProductEnterpriseAdminMemberDO membership = enterpriseAdminMemberMapper.selectOne(
                new LambdaQueryWrapperX<ProductEnterpriseAdminMemberDO>()
                        .eq(ProductEnterpriseAdminMemberDO::getAdminUserId, adminUserId)
                        .eq(ProductEnterpriseAdminMemberDO::getEnterpriseId, enterpriseId));
        if (membership == null) {
            membership = new ProductEnterpriseAdminMemberDO();
            membership.setAdminUserId(adminUserId); membership.setEnterpriseId(enterpriseId);
            membership.setScopeType(ProductEnterpriseAdminMemberDO.SCOPE_ENTERPRISE); membership.setStatus(0);
            enterpriseAdminMemberMapper.insert(membership);
        } else {
            membership.setScopeType(ProductEnterpriseAdminMemberDO.SCOPE_ENTERPRISE); membership.setStatus(0);
            enterpriseAdminMemberMapper.updateById(membership);
        }

        ProductShopAdminAuthorizationDO grant = shopAdminAuthorizationMapper.selectOne(
                new LambdaQueryWrapperX<ProductShopAdminAuthorizationDO>()
                        .eq(ProductShopAdminAuthorizationDO::getAdminUserId, adminUserId)
                        .eq(ProductShopAdminAuthorizationDO::getShopId, shopId));
        if (grant == null) {
            grant = new ProductShopAdminAuthorizationDO();
            grant.setAdminUserId(adminUserId); grant.setEnterpriseId(enterpriseId);
            grant.setShopId(shopId); grant.setStatus(0); shopAdminAuthorizationMapper.insert(grant);
        } else {
            grant.setEnterpriseId(enterpriseId); grant.setStatus(0); shopAdminAuthorizationMapper.updateById(grant);
        }
    }

    private String defaultShopName(String enterpriseName) {
        String name = Objects.toString(enterpriseName, "企业").trim() + "官方店";
        return name.length() <= 64 ? name : name.substring(0, 64);
    }

    private static void requireWritableTenant() {
        MallTenantWriteGuard.requireWritable(TenantContextHolder.getRequiredTenantId());
    }
}

package cn.iocoder.txgy.module.product.service.review;

import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.dal.dataobject.review.ProductSpuReviewRecordDO;
import cn.iocoder.txgy.module.product.api.tenant.MallTenantWriteGuard;
import cn.iocoder.txgy.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.txgy.module.product.dal.mysql.review.ProductSpuReviewRecordMapper;
import cn.iocoder.txgy.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.iocoder.txgy.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import cn.iocoder.txgy.module.product.service.enterprise.ProductAdminBusinessScopeService;
import cn.iocoder.txgy.module.product.service.audit.ProductBusinessOperationAuditService;
import cn.iocoder.txgy.module.product.service.shop.ProductShopService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService.invalid;

@Service
public class ProductSpuReviewService {
    public static final int DRAFT = 0, PENDING = 10, APPROVED = 20, REJECTED = 30;
    @Resource private ProductSpuMapper spuMapper;
    @Resource private ProductSkuMapper skuMapper;
    @Resource private ProductSpuReviewRecordMapper recordMapper;
    @Resource private ProductShopService shopService;
    @Resource private EnterpriseMembershipService membership;
    @Resource private ProductAdminBusinessScopeService businessScopeService;
    @Resource private ProductReviewProperties properties;
    @Resource private cn.iocoder.txgy.module.product.dal.mysql.enterprise.ProductEnterpriseMapper enterpriseMapper;
    @Resource private cn.iocoder.txgy.module.product.service.category.ProductCategoryService categoryService;
    @Resource private cn.iocoder.txgy.module.product.service.brand.ProductBrandService brandService;
    @Resource private cn.iocoder.txgy.module.trade.api.delivery.DeliveryTemplateApi deliveryApi;
    @Resource private ProductBusinessOperationAuditService operationAuditService;
    @Resource private ProductWmsListingValidator wmsListingValidator;

    public record Policy(boolean adminWriteEnabled, String legacyStrategy, boolean reviewEnabled, List<Long> adminWriteLockedShopIds) {}
    public Policy policy() { return new Policy(properties.isAdminWriteEnabled(), properties.getLegacyStrategy(), properties.isReviewEnabled(),
            properties.getAdminWriteLockedShopIds()); }
    public void requireAdminWrite(Long shopId) {
        if (!properties.isAdminWriteEnabled() || properties.getAdminWriteLockedShopIds().contains(shopId))
            throw invalid("该店商品经营权限尚未切换到后台，请联系租户管理员完成账号与店铺授权");
    }
    public boolean keepLegacy() { return properties.isReviewEnabled() && "KEEP_EXISTING_UNMODIFIED".equals(properties.getLegacyStrategy()); }
    public void applySaleableQuery(LambdaQueryWrapperX<ProductSpuDO> query) {
        query.eq(ProductSpuDO::getStatus, 1)
                .apply("((review_status = 20 AND approved_version IS NOT NULL AND approved_version = content_version)"
                        + (keepLegacy() ? " OR review_status IS NULL)" : ")"));
        query.apply("(EXISTS (SELECT 1 FROM product_shop ps WHERE ps.id = product_spu.shop_id"
                + " AND ps.tenant_id = product_spu.tenant_id AND ps.deleted = 0 AND ps.status = 0"
                + " AND (product_spu.review_status IS NULL OR EXISTS (SELECT 1 FROM product_enterprise pe"
                + " WHERE pe.id = ps.enterprise_id AND pe.tenant_id = product_spu.tenant_id AND pe.deleted = 0 AND pe.audit_status = 1)))"
                + (keepLegacy() ? " OR (review_status IS NULL AND (shop_id IS NULL OR shop_id = 0)))" : ")"));
    }
    public static long version(ProductSpuDO p) { return Objects.requireNonNullElse(p.getContentVersion(), 0L); }
    public static void expect(ProductSpuDO p, Long expected) {
        if (expected == null || expected < 0 || expected != version(p)) throw invalid("商品版本已变化，请刷新后重试");
    }
    public ProductSpuDO lock(Long id) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("商品锁必须在事务内使用");
        if (TenantContextHolder.getTenantId() == null) throw invalid("缺少租户上下文");
        var p = spuMapper.selectOne(new LambdaQueryWrapperX<ProductSpuDO>().eq(ProductSpuDO::getId, id).last("FOR UPDATE"));
        if (p == null) throw invalid("商品不存在或无权操作");
        return p;
    }
    public String snapshot(ProductSpuDO p) { return ProductReviewSnapshot.of(p, skuMapper.selectListBySpuId(p.getId())); }
    public String beforeEdit(ProductSpuDO p, Long expected) {
        expect(p, expected);
        if (Objects.equals(p.getReviewStatus(), PENDING)) throw invalid("商品正在审核，请先撤回再修改");
        return snapshot(p);
    }
    /** 调用者已持有 SPU 锁，所有经营写入和失效批准在同一事务。 */
    public void finishEdit(ProductSpuDO before, String oldSnapshot) {
        var current = spuMapper.selectById(before.getId());
        if (Objects.equals(oldSnapshot, snapshot(current))) return;
        long next = Math.addExact(version(before), 1L);
        spuMapper.update(null, new LambdaUpdateWrapper<ProductSpuDO>().eq(ProductSpuDO::getId, before.getId())
                .set(ProductSpuDO::getStatus, 0).set(ProductSpuDO::getReviewStatus, DRAFT)
                .set(ProductSpuDO::getContentVersion, next).set(ProductSpuDO::getApprovedVersion, null)
                .set(ProductSpuDO::getSubmissionId, null).set(ProductSpuDO::getReviewRemark, null));
        current.setContentVersion(next).setStatus(0).setReviewStatus(DRAFT);
        append(current, "EDIT", "version:" + next, "经营资料修改，须重新审核", snapshot(current));
    }
    public void created(Long id) {
        var p = spuMapper.selectById(id);
        append(p, "CREATE", "version:1", "新建草稿", snapshot(p));
    }
    public void restoreDraft(ProductSpuDO p) {
        long next = Math.addExact(version(p), 1L);
        spuMapper.update(null, new LambdaUpdateWrapper<ProductSpuDO>().eq(ProductSpuDO::getId, p.getId())
                .set(ProductSpuDO::getStatus, 0).set(ProductSpuDO::getReviewStatus, DRAFT)
                .set(ProductSpuDO::getContentVersion, next).set(ProductSpuDO::getApprovedVersion, null)
                .set(ProductSpuDO::getSubmissionId, null).set(ProductSpuDO::getReviewRemark, null));
        p.setContentVersion(next);
        append(p, "RESTORE", "version:" + next, "恢复为草稿，须重新审核", snapshot(p));
    }
    public void requireApprovedForListing(ProductSpuDO p) {
        if (!Objects.equals(p.getReviewStatus(), APPROVED) || p.getApprovedVersion() == null
                || !Objects.equals(p.getContentVersion(), p.getApprovedVersion()))
            throw invalid("当前商品版本尚未审核通过，请先提交审核");
        requireOperatingShop(p);
        validateListingMetadata(p);
    }
    private void validateListingMetadata(ProductSpuDO p) {
        categoryService.validateCategory(p.getCategoryId()); brandService.validateProductBrand(p.getBrandId());
        if (p.getName() == null || p.getName().isBlank() || p.getDescription() == null || p.getDescription().isBlank()
                || p.getDeliveryTypes() == null || !p.getDeliveryTypes().contains(1)) throw invalid("请完善商品名称、详情和快递配送方式");
        cn.iocoder.txgy.module.product.service.supplier.SupplierProductService.validateImageUrl(p.getPicUrl());
        var template = deliveryApi.list().getCheckedData().stream().filter(t -> Objects.equals(t.id(), p.getDeliveryTemplateId()))
                .findFirst().orElseThrow(() -> invalid("请配置有效快递运费模板"));
        var skus = skuMapper.selectListBySpuId(p.getId());
        if (skus.isEmpty()) throw invalid("商品缺少有效规格");
        wmsListingValidator.validate(skus);
        for (var sku : skus) {
            if (sku.getPrice() == null || sku.getPrice() <= 0) throw invalid("商品SKU价格必须大于0");
            if (template.chargeMode() == 2 && (sku.getWeight() == null || !Double.isFinite(sku.getWeight()) || sku.getWeight() <= 0))
                throw invalid("按重量计费须配置正数重量");
            if (template.chargeMode() == 3 && (sku.getVolume() == null || !Double.isFinite(sku.getVolume()) || sku.getVolume() <= 0))
                throw invalid("按体积计费须配置正数体积");
        }
    }
    private void requireOperatingShop(ProductSpuDO p) {
        var shop = p.getShopId() == null ? null : shopService.getShop(p.getShopId());
        var company = shop == null || shop.getEnterpriseId() == null ? null : enterpriseMapper.selectById(shop.getEnterpriseId());
        if (shop == null || !Objects.equals(shop.getStatus(), 0) || company == null || !Objects.equals(company.getAuditStatus(), 1))
            throw invalid("店铺或企业资质已失效，请先完成企业及店铺认证");
    }
    public boolean saleable(ProductSpuDO p) {
        if (p == null || !Objects.equals(p.getStatus(), 1)) return false;
        boolean reviewPassed = Objects.equals(p.getReviewStatus(), APPROVED) && p.getApprovedVersion() != null
                && Objects.equals(p.getContentVersion(), p.getApprovedVersion());
        if (!reviewPassed && !(p.getReviewStatus() == null && keepLegacy())) return false;
        if (p.getShopId() == null || p.getShopId() == 0) return p.getReviewStatus() == null && keepLegacy();
        var shop = shopService.getShop(p.getShopId());
        if (shop == null || !Objects.equals(shop.getStatus(), 0)) return false;
        if (p.getReviewStatus() == null && keepLegacy()) return true;
        var company = shop.getEnterpriseId() == null ? null : enterpriseMapper.selectById(shop.getEnterpriseId());
        return company != null && Objects.equals(company.getAuditStatus(), 1);
    }
    public List<ProductSpuReviewRecordDO> history(Long id) {
        return recordMapper.selectList(new LambdaQueryWrapperX<ProductSpuReviewRecordDO>()
                .eq(ProductSpuReviewRecordDO::getSpuId, id).orderByDesc(ProductSpuReviewRecordDO::getId));
    }
    private ProductSpuDO owned(Long member, Long shopId, Long id) {
        membership.requireShop(member, shopId);
        var p = lock(id);
        if (!Objects.equals(shopId, p.getShopId()) || !Set.of(0, 1).contains(p.getStatus())) throw invalid("商品不存在或无权管理");
        return p;
    }
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long member, Long shop, Long id, Long expected) {
        requireWritableTenant();
        var p = owned(member, shop, id);
        submitLocked(p, expected);
    }
    private void submitLocked(ProductSpuDO p, Long expected) {
        expect(p, expected);
        requireOperatingShop(p);
        validateListingMetadata(p);
        if (Objects.equals(p.getReviewStatus(), PENDING)) return;
        if (Objects.equals(p.getReviewStatus(), APPROVED)) throw invalid("当前版本已审核通过，无须重复送审");
        String submission = UUID.randomUUID().toString();
        long version = Math.max(1L, version(p));
        spuMapper.update(null, new LambdaUpdateWrapper<ProductSpuDO>().eq(ProductSpuDO::getId, p.getId())
                .set(ProductSpuDO::getStatus, 0).set(ProductSpuDO::getReviewStatus, PENDING)
                .set(ProductSpuDO::getContentVersion, version).set(ProductSpuDO::getApprovedVersion, null)
                .set(ProductSpuDO::getSubmissionId, submission).set(ProductSpuDO::getReviewRemark, null));
        p.setStatus(0).setReviewStatus(PENDING).setContentVersion(version).setSubmissionId(submission);
        append(p, "SUBMIT", submission, "提交商品审核", snapshot(p));
    }
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long member, Long shop, Long id, Long expected, String submission) {
        requireWritableTenant();
        var p = owned(member, shop, id);
        withdrawLocked(p, expected, submission);
    }
    private void withdrawLocked(ProductSpuDO p, Long expected, String submission) {
        expect(p, expected);
        if (submission == null || !Objects.equals(submission, p.getSubmissionId())) throw invalid("审核批次已变化，请刷新");
        if (Objects.equals(p.getReviewStatus(), DRAFT)) return;
        if (!Objects.equals(p.getReviewStatus(), PENDING)) throw invalid("仅待审核商品可撤回");
        spuMapper.updateById(new ProductSpuDO().setId(p.getId()).setReviewStatus(DRAFT).setStatus(0));
        append(p, "WITHDRAW", submission, "商家撤回审核", snapshot(p));
    }
    @Transactional(rollbackFor = Exception.class)
    public void adminSubmit(Long id, Long expected) {
        requireWritableTenant();
        var p = adminOwned(id); requireAdminWrite(p.getShopId());
        submitLocked(p, expected);
    }
    @Transactional(rollbackFor = Exception.class)
    public void adminWithdraw(Long id, Long expected, String submission) {
        requireWritableTenant();
        var p = adminOwned(id); requireAdminWrite(p.getShopId());
        withdrawLocked(p, expected, submission);
    }
    private ProductSpuDO adminOwned(Long id) {
        var p = lock(id);
        var user = SecurityFrameworkUtils.getLoginUser();
        if (user == null || !Objects.equals(user.getUserType(), UserTypeEnum.ADMIN.getValue())) throw invalid("请使用后台账号");
        if (p.getShopId() == null || !Set.of(0, 1).contains(p.getStatus())) throw invalid("请先配置有效店铺，回收商品须先恢复为草稿");
        businessScopeService.requireMerchantShop(user.getId(), p.getShopId());
        return p;
    }
    @Transactional(rollbackFor = Exception.class)
    public void decide(Long id, Long expected, String submission, boolean approved, String reason) {
        requireWritableTenant();
        var p = lock(id); expect(p, expected);
        if (submission == null || !Objects.equals(submission, p.getSubmissionId())) throw invalid("审核批次已变化，请刷新");
        var user = SecurityFrameworkUtils.getLoginUser();
        if (user == null || !Objects.equals(user.getUserType(), UserTypeEnum.ADMIN.getValue())) throw invalid("请使用后台审核账号");
        // 商品经营账号即使误配了 review 权限，也不能借此跨越“经营与审核分离”边界。
        businessScopeService.requireTenantManager(user.getId());
        var shop = p.getShopId() == null ? null : shopService.getShop(p.getShopId());
        if (shop == null || Objects.equals(shop.getManagerUserId(), user.getId())) throw invalid("经营关联账号不能审核本店商品，请由独立审核人员处理");
        if (businessScopeService.isEnterpriseBusinessMember(user.getId(), shop.getEnterpriseId())) {
            throw invalid("企业经营成员不能审核本企业商品，请由独立审核人员处理");
        }
        var submitted = history(id).stream().filter(r -> "SUBMIT".equals(r.getAction()) && submission.equals(r.getSubmissionId())).findFirst()
                .orElseThrow(() -> invalid("审核提交快照不存在"));
        if (Objects.equals(submitted.getActorType(), user.getUserType()) && Objects.equals(submitted.getActorId(), user.getId()))
            throw invalid("提交人员不能审核自己的商品");
        if (history(id).stream().anyMatch(r -> Objects.equals(r.getContentVersion(), p.getContentVersion())
                && Set.of("CREATE", "EDIT", "RESTORE").contains(r.getAction())
                && Objects.equals(r.getActorType(), user.getUserType()) && Objects.equals(r.getActorId(), user.getId())))
            throw invalid("当前版本的经营维护人员不能审核自己的商品");
        requireOperatingShop(p);
        if (!Objects.equals(submitted.getSnapshot(), snapshot(p))) throw invalid("商品内容与提交快照不一致，请撤回后重新送审");
        String remark = reason == null ? "" : reason.trim();
        if ((!approved && remark.isBlank()) || remark.length() > 1000) throw invalid("驳回必须填写原因，审核意见不得超过1000字");
        int target = approved ? APPROVED : REJECTED;
        if (Objects.equals(p.getReviewStatus(), target) && Objects.equals(p.getReviewRemark(), remark)) return;
        if (!Objects.equals(p.getReviewStatus(), PENDING)) throw invalid("该批次已处理或撤回，请刷新");
        spuMapper.update(null, new LambdaUpdateWrapper<ProductSpuDO>().eq(ProductSpuDO::getId, id)
                .set(ProductSpuDO::getStatus, 0).set(ProductSpuDO::getReviewStatus, target)
                .set(ProductSpuDO::getApprovedVersion, approved ? version(p) : null).set(ProductSpuDO::getReviewRemark, remark));
        append(p, approved ? "APPROVE" : "REJECT", submission, remark, submitted.getSnapshot());
    }
    private void append(ProductSpuDO p, String action, String submission, String remark, String snapshot) {
        var user = SecurityFrameworkUtils.getLoginUser();
        var r = new ProductSpuReviewRecordDO();
        r.setTenantId(TenantContextHolder.getRequiredTenantId()); r.setSpuId(p.getId()); r.setShopId(p.getShopId());
        r.setContentVersion(version(p)); r.setSubmissionId(submission); r.setAction(action);
        r.setActorType(user == null ? 0 : user.getUserType()); r.setActorId(user == null ? 0L : user.getId());
        r.setRemark(remark); r.setSnapshot(snapshot); r.setCreateTime(LocalDateTime.now());
        if (recordMapper.insert(r) != 1) throw invalid("审核记录保存失败");
        var shop = p.getShopId() == null ? null : shopService.getShop(p.getShopId());
        String actorType = user == null ? "SYSTEM"
                : Objects.equals(user.getUserType(), UserTypeEnum.ADMIN.getValue()) ? "ADMIN" : "MEMBER";
        operationAuditService.success(shop == null ? null : shop.getEnterpriseId(), p.getShopId(), actorType,
                user == null ? null : user.getId(), "PRODUCT_" + action, "PRODUCT_SPU", p.getId(),
                "contentVersion=" + version(p) + ",submissionId=" + Objects.toString(submission, ""));
    }

    private static void requireWritableTenant() {
        MallTenantWriteGuard.requireWritable(TenantContextHolder.getRequiredTenantId());
    }
}

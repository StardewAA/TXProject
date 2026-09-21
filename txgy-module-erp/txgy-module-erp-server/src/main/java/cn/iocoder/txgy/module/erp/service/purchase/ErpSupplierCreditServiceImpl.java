package cn.iocoder.txgy.module.erp.service.purchase;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.supplier.credit.*;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.*;
import cn.iocoder.txgy.module.erp.dal.mysql.purchase.*;
import cn.iocoder.txgy.module.erp.enums.ErpSupplierCreditConstants;
import cn.iocoder.txgy.module.erp.service.product.ErpProductService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.*;

@Service
@Validated
public class ErpSupplierCreditServiceImpl implements ErpSupplierCreditService {

    @Resource
    private ErpSupplierCreditStatusMapper creditStatusMapper;
    @Resource
    private ErpSupplierAdmissionCategoryMapper admissionCategoryMapper;
    @Resource
    private ErpSupplierQualificationMapper qualificationMapper;
    @Resource
    private ErpSupplierRiskEventMapper riskEventMapper;
    @Resource
    private ErpProductService productService;

    @Override
    public ErpSupplierCreditRespVO getCredit(Long supplierId) {
        ErpSupplierCreditStatusDO status = ensureStatus(supplierId);
        ErpSupplierCreditRespVO result = BeanUtils.toBean(status, ErpSupplierCreditRespVO.class);
        result.setQualifications(BeanUtils.toBean(qualificationMapper.selectListBySupplierId(supplierId),
                ErpSupplierCreditRespVO.Qualification.class));
        result.setAdmissionCategories(BeanUtils.toBean(admissionCategoryMapper.selectListBySupplierId(supplierId),
                ErpSupplierCreditRespVO.AdmissionCategory.class));
        result.setRiskEvents(BeanUtils.toBean(riskEventMapper.selectListBySupplierId(supplierId),
                ErpSupplierCreditRespVO.RiskEvent.class));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ErpSupplierCreditStatusDO ensureStatus(Long supplierId) {
        ErpSupplierCreditStatusDO status = creditStatusMapper.selectBySupplierId(supplierId);
        if (status != null) {
            return status;
        }
        status = new ErpSupplierCreditStatusDO()
                .setSupplierId(supplierId)
                .setAuthStatus(ErpSupplierCreditConstants.AUTH_NORMAL)
                .setQualificationStatus(ErpSupplierCreditConstants.QUAL_VALID)
                .setRiskLevel("LOW")
                .setPurchaseLimitStatus(ErpSupplierCreditConstants.LIMIT_NONE)
                .setSnapshotVersion(1L)
                .setLastSyncTime(LocalDateTime.now());
        creditStatusMapper.insert(status);
        return status;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<Long, ErpSupplierCreditStatusDO> getStatusMap(Collection<Long> supplierIds) {
        if (CollUtil.isEmpty(supplierIds)) {
            return Collections.emptyMap();
        }
        Map<Long, ErpSupplierCreditStatusDO> statusMap = convertMap(
                creditStatusMapper.selectListBySupplierIds(supplierIds), ErpSupplierCreditStatusDO::getSupplierId);
        supplierIds.stream()
                .filter(Objects::nonNull)
                .filter(supplierId -> !statusMap.containsKey(supplierId))
                .forEach(supplierId -> statusMap.put(supplierId, ensureStatus(supplierId)));
        return statusMap;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receiveCreditStatus(ErpSupplierCreditStatusSaveReqVO reqVO) {
        ErpSupplierCreditStatusDO current = ensureStatus(reqVO.getSupplierId());
        if (reqVO.getSnapshotVersion() < current.getSnapshotVersion()) {
            throw exception(SUPPLIER_CREDIT_VERSION_CONFLICT, current.getSnapshotVersion());
        }
        if (!isAuthStatus(reqVO.getAuthStatus()) || !isLimitStatus(reqVO.getPurchaseLimitStatus())
                || !isQualificationStatus(reqVO.getQualificationStatus())
                || !isRiskLevel(reqVO.getRiskLevel())) {
            throw exception(SUPPLIER_CREDIT_STATUS_INVALID, "认证、采购限制、资质状态或风险等级");
        }
        if (Objects.equals(reqVO.getSnapshotVersion(), current.getSnapshotVersion())
                && Objects.equals(reqVO.getAuthStatus(), current.getAuthStatus())
                && Objects.equals(reqVO.getQualificationStatus(), current.getQualificationStatus())
                && Objects.equals(reqVO.getRiskLevel(), current.getRiskLevel())
                && Objects.equals(reqVO.getPurchaseLimitStatus(), current.getPurchaseLimitStatus())
                && Objects.equals(reqVO.getFreezeReason(), current.getFreezeReason())) {
            return;
        }
        if (Objects.equals(reqVO.getSnapshotVersion(), current.getSnapshotVersion())) {
            throw exception(SUPPLIER_CREDIT_VERSION_CONFLICT, current.getSnapshotVersion());
        }
        ErpSupplierCreditStatusDO update = BeanUtils.toBean(reqVO, ErpSupplierCreditStatusDO.class);
        update.setId(current.getId()).setLastSyncTime(LocalDateTime.now());
        creditStatusMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceAdmissionCategories(ErpSupplierAdmissionCategorySaveReqVO reqVO) {
        if (CollUtil.isEmpty(reqVO.getItems())) {
            admissionCategoryMapper.delete(AdmissionCategoryMapperWrappers.bySupplierId(reqVO.getSupplierId()));
            return;
        }
        Set<String> categoryKeys = new HashSet<>();
        for (ErpSupplierAdmissionCategorySaveReqVO.Item item : reqVO.getItems()) {
            if (item.getCategoryId() == null && (item.getCategoryName() == null
                    || item.getCategoryName().isBlank())) {
                throw exception(SUPPLIER_CREDIT_STATUS_INVALID, "准入品类名称不能为空");
            }
            if (item.getValidFrom() != null && item.getValidTo() != null
                    && item.getValidFrom().isAfter(item.getValidTo())) {
                throw exception(SUPPLIER_CREDIT_STATUS_INVALID, "准入品类生效时间不能晚于失效时间");
            }
            String categoryKey = item.getCategoryId() == null
                    ? "name:" + item.getCategoryName().trim()
                    : "id:" + item.getCategoryId();
            if (!categoryKeys.add(categoryKey)) {
                throw exception(SUPPLIER_CREDIT_STATUS_INVALID, "准入品类不能重复");
            }
            String status = item.getStatus() == null ? ErpSupplierCreditConstants.QUAL_VALID : item.getStatus();
            if (!ErpSupplierCreditConstants.QUAL_VALID.equals(status)) {
                throw exception(SUPPLIER_CREDIT_STATUS_INVALID, "准入品类必须为有效状态");
            }
        }
        admissionCategoryMapper.delete(AdmissionCategoryMapperWrappers.bySupplierId(reqVO.getSupplierId()));
        for (ErpSupplierAdmissionCategorySaveReqVO.Item item : reqVO.getItems()) {
            admissionCategoryMapper.insert(BeanUtils.toBean(item, ErpSupplierAdmissionCategoryDO.class)
                    .setSupplierId(reqVO.getSupplierId())
                    .setStatus(item.getStatus() == null ? ErpSupplierCreditConstants.QUAL_VALID : item.getStatus()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceQualifications(ErpSupplierQualificationSaveReqVO reqVO) {
        qualificationMapper.delete(QualificationMapperWrappers.bySupplierId(reqVO.getSupplierId()));
        if (CollUtil.isEmpty(reqVO.getItems())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Set<String> qualificationKeys = new HashSet<>();
        for (ErpSupplierQualificationSaveReqVO.Item item : reqVO.getItems()) {
            if (item.getQualificationName() == null || item.getQualificationName().isBlank()) {
                throw exception(SUPPLIER_CREDIT_STATUS_INVALID, "资质名称不能为空");
            }
            if (item.getValidFrom() != null && item.getValidTo() != null
                    && item.getValidFrom().isAfter(item.getValidTo())) {
                throw exception(SUPPLIER_CREDIT_STATUS_INVALID, "资质生效时间不能晚于失效时间");
            }
            String qualificationKey = item.getQualificationNo() == null || item.getQualificationNo().isBlank()
                    ? item.getQualificationName().trim()
                    : item.getQualificationNo().trim();
            if (!qualificationKeys.add(qualificationKey)) {
                throw exception(SUPPLIER_CREDIT_STATUS_INVALID, "资质编号或名称不能重复");
            }
            String status = item.getStatus();
            if (status == null) {
                status = item.getValidTo() != null && item.getValidTo().isBefore(now)
                        ? ErpSupplierCreditConstants.QUAL_EXPIRED : ErpSupplierCreditConstants.QUAL_VALID;
            }
            if (!isQualificationStatus(status)) {
                throw exception(SUPPLIER_CREDIT_STATUS_INVALID, status);
            }
            qualificationMapper.insert(BeanUtils.toBean(item, ErpSupplierQualificationDO.class)
                    .setId(null).setSupplierId(reqVO.getSupplierId()).setStatus(status));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRiskEvent(ErpSupplierRiskEventSaveReqVO reqVO) {
        if (!Set.of(ErpSupplierCreditConstants.EVENT_QUALIFICATION_EXPIRED,
                ErpSupplierCreditConstants.EVENT_DELIVERY_DELAY,
                ErpSupplierCreditConstants.EVENT_QUALITY_FAILURE,
                ErpSupplierCreditConstants.EVENT_MAJOR_RETURN,
                ErpSupplierCreditConstants.EVENT_PRICE_EXCEPTION,
                ErpSupplierCreditConstants.EVENT_SETTLEMENT_DISPUTE,
                ErpSupplierCreditConstants.EVENT_CONTRACT_BREACH,
                ErpSupplierCreditConstants.EVENT_MANUAL_WARNING,
                ErpSupplierCreditConstants.EVENT_OTHER).contains(reqVO.getEventType())) {
            throw exception(SUPPLIER_CREDIT_STATUS_INVALID, reqVO.getEventType());
        }
        if (reqVO.getSeverity() != null && (reqVO.getSeverity() < 1 || reqVO.getSeverity() > 5)) {
            throw exception(SUPPLIER_CREDIT_STATUS_INVALID, "风险等级必须在1到5之间");
        }
        if (reqVO.getDescription() == null || reqVO.getDescription().isBlank()) {
            throw exception(SUPPLIER_CREDIT_STATUS_INVALID, "风险事件说明不能为空");
        }
        ErpSupplierRiskEventDO event = BeanUtils.toBean(reqVO, ErpSupplierRiskEventDO.class)
                .setStatus(ErpSupplierCreditConstants.RISK_PENDING)
                .setOccurredAt(reqVO.getOccurredAt() == null ? LocalDateTime.now() : reqVO.getOccurredAt())
                .setBlocksPurchase(Boolean.TRUE.equals(reqVO.getBlocksPurchase()));
        riskEventMapper.insert(event);
        return event.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRiskEventStatus(Long id, String status, String resolution) {
        ErpSupplierRiskEventDO event = riskEventMapper.selectById(id);
        if (event == null) {
            throw exception(SUPPLIER_CREDIT_NOT_EXISTS);
        }
        if (!Set.of(ErpSupplierCreditConstants.RISK_DRAFT, ErpSupplierCreditConstants.RISK_PENDING,
                ErpSupplierCreditConstants.RISK_CONFIRMED, ErpSupplierCreditConstants.RISK_REJECTED,
                ErpSupplierCreditConstants.RISK_PROCESSING, ErpSupplierCreditConstants.RISK_CLOSED).contains(status)) {
            throw exception(SUPPLIER_CREDIT_STATUS_INVALID, status);
        }
        ErpSupplierRiskEventDO update = new ErpSupplierRiskEventDO().setId(id).setStatus(status).setResolution(resolution);
        if (ErpSupplierCreditConstants.RISK_CLOSED.equals(status)) {
            update.setResolvedAt(LocalDateTime.now());
        }
        riskEventMapper.updateById(update);
    }

    @Override
    public ErpSupplierQualificationCheckRespVO checkQualification(ErpSupplierQualificationCheckReqVO reqVO) {
        ErpSupplierCreditStatusDO status = ensureStatus(reqVO.getSupplierId());
        ErpSupplierQualificationCheckRespVO result = new ErpSupplierQualificationCheckRespVO()
                .setAuthStatus(status.getAuthStatus())
                .setPurchaseLimitStatus(status.getPurchaseLimitStatus())
                .setAllowed(true);
        if (Set.of(ErpSupplierCreditConstants.AUTH_DRAFT, ErpSupplierCreditConstants.AUTH_PENDING,
                        ErpSupplierCreditConstants.AUTH_REJECTED, ErpSupplierCreditConstants.AUTH_FROZEN,
                        ErpSupplierCreditConstants.AUTH_CANCELLED).contains(status.getAuthStatus())) {
            result.addBlockingReason("认证状态为" + status.getAuthStatus());
        }
        if (Set.of(ErpSupplierCreditConstants.LIMIT_FROZEN, ErpSupplierCreditConstants.LIMIT_BLACKLISTED)
                .contains(status.getPurchaseLimitStatus())) {
            result.addBlockingReason("采购限制状态为" + status.getPurchaseLimitStatus());
        } else if (ErpSupplierCreditConstants.LIMIT_LIMITED.equals(status.getPurchaseLimitStatus())) {
            result.addWarning("供应商处于受限采购状态");
        }
        if (Set.of(ErpSupplierCreditConstants.QUAL_EXPIRED, ErpSupplierCreditConstants.QUAL_REVOKED,
                        ErpSupplierCreditConstants.QUAL_PENDING_REVIEW).contains(status.getQualificationStatus())) {
            result.addBlockingReason("资质状态为" + status.getQualificationStatus());
        } else if (ErpSupplierCreditConstants.QUAL_EXPIRING_SOON.equals(status.getQualificationStatus())) {
            result.addWarning("供应商资质即将到期");
        }

        List<ErpSupplierQualificationDO> qualifications = qualificationMapper.selectListBySupplierId(reqVO.getSupplierId());
        LocalDateTime now = LocalDateTime.now();
        qualifications.stream()
                .filter(q -> Boolean.TRUE.equals(q.getRequiredFlag()))
                .forEach(q -> {
                    String qualificationName = q.getQualificationName();
                    if (Set.of(ErpSupplierCreditConstants.QUAL_EXPIRED,
                            ErpSupplierCreditConstants.QUAL_REVOKED,
                            ErpSupplierCreditConstants.QUAL_PENDING_REVIEW).contains(q.getStatus())) {
                        result.addBlockingReason("必要资质状态不可用：" + qualificationName);
                    }
                    if (q.getValidFrom() != null && q.getValidFrom().isAfter(now)) {
                        result.addBlockingReason("必要资质尚未生效：" + qualificationName);
                    }
                    if (q.getValidTo() != null && !q.getValidTo().isAfter(now)) {
                        result.addBlockingReason("必要资质已过期：" + qualificationName);
                    }
                });

        List<ErpSupplierRiskEventDO> riskEvents = riskEventMapper.selectListBySupplierId(reqVO.getSupplierId());
        riskEvents.stream()
                .filter(e -> Boolean.TRUE.equals(e.getBlocksPurchase()))
                .filter(e -> Set.of(ErpSupplierCreditConstants.RISK_CONFIRMED,
                        ErpSupplierCreditConstants.RISK_PROCESSING).contains(e.getStatus()))
                .forEach(e -> result.addBlockingReason("存在禁止交易风险事件：" + e.getEventType()));

        List<ErpSupplierAdmissionCategoryDO> categories =
                admissionCategoryMapper.selectListBySupplierId(reqVO.getSupplierId());
        if (CollUtil.isNotEmpty(categories)) {
            Map<Long, ErpSupplierAdmissionCategoryDO> categoryMap = categories.stream()
                    .filter(c -> c.getCategoryId() != null)
                    .collect(Collectors.toMap(ErpSupplierAdmissionCategoryDO::getCategoryId, Function.identity(),
                            (left, right) -> left));
            for (Long productId : reqVO.getProductIds()) {
                ErpProductDO product = productService.getProduct(productId);
                if (product == null || product.getCategoryId() == null || !categoryMap.containsKey(product.getCategoryId())) {
                    result.addBlockingReason("产品未纳入供应商准入品类：" + productId);
                    continue;
                }
                ErpSupplierAdmissionCategoryDO category = categoryMap.get(product.getCategoryId());
                if (!ErpSupplierCreditConstants.QUAL_VALID.equals(category.getStatus())) {
                    result.addBlockingReason("供应商准入品类状态不可用：" + category.getCategoryName());
                }
                if (category.getValidFrom() != null && category.getValidFrom().isAfter(now)) {
                    result.addBlockingReason("供应商准入品类尚未生效：" + category.getCategoryName());
                }
                if (category.getValidTo() != null && category.getValidTo().isBefore(now)) {
                    result.addBlockingReason("供应商准入品类已过期：" + category.getCategoryName());
                }
            }
        }
        result.setAllowed(result.getBlockingReasons().isEmpty());
        return result;
    }

    @Override
    public void validateQualification(Long supplierId, Collection<Long> productIds) {
        ErpSupplierQualificationCheckReqVO reqVO = new ErpSupplierQualificationCheckReqVO()
                .setSupplierId(supplierId).setProductIds(new ArrayList<>(productIds));
        ErpSupplierQualificationCheckRespVO result = checkQualification(reqVO);
        if (!result.isAllowed()) {
            throw exception(SUPPLIER_CREDIT_BLOCKED, supplierId,
                    String.join("；", result.getBlockingReasons()));
        }
    }

    private boolean isAuthStatus(String status) {
        return status != null && Set.of(ErpSupplierCreditConstants.AUTH_DRAFT,
                ErpSupplierCreditConstants.AUTH_PENDING, ErpSupplierCreditConstants.AUTH_APPROVED,
                ErpSupplierCreditConstants.AUTH_REJECTED, ErpSupplierCreditConstants.AUTH_NORMAL,
                ErpSupplierCreditConstants.AUTH_FROZEN, ErpSupplierCreditConstants.AUTH_CANCELLED).contains(status);
    }

    private boolean isLimitStatus(String status) {
        return status != null && Set.of(ErpSupplierCreditConstants.LIMIT_NONE,
                ErpSupplierCreditConstants.LIMIT_WARNING, ErpSupplierCreditConstants.LIMIT_LIMITED,
                ErpSupplierCreditConstants.LIMIT_FROZEN, ErpSupplierCreditConstants.LIMIT_BLACKLISTED).contains(status);
    }

    private boolean isQualificationStatus(String status) {
        return status != null && Set.of(ErpSupplierCreditConstants.QUAL_VALID,
                ErpSupplierCreditConstants.QUAL_EXPIRING_SOON, ErpSupplierCreditConstants.QUAL_EXPIRED,
                ErpSupplierCreditConstants.QUAL_REVOKED, ErpSupplierCreditConstants.QUAL_PENDING_REVIEW).contains(status);
    }

    private boolean isRiskLevel(String riskLevel) {
        return riskLevel != null && Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(riskLevel);
    }

    /**
     * 仅用于避免在服务层暴露 MyBatis Wrapper 细节到业务代码。
     */
    private static final class AdmissionCategoryMapperWrappers {
        private static com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ErpSupplierAdmissionCategoryDO> bySupplierId(Long supplierId) {
            return new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ErpSupplierAdmissionCategoryDO>()
                    .eq(ErpSupplierAdmissionCategoryDO::getSupplierId, supplierId);
        }
    }

    private static final class QualificationMapperWrappers {
        private static com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ErpSupplierQualificationDO> bySupplierId(Long supplierId) {
            return new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ErpSupplierQualificationDO>()
                    .eq(ErpSupplierQualificationDO::getSupplierId, supplierId);
        }
    }
}

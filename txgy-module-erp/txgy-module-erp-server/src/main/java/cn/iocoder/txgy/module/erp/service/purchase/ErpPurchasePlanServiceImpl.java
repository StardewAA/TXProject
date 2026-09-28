package cn.iocoder.txgy.module.erp.service.purchase;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.txgy.framework.common.exception.ErrorCode;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.common.util.number.MoneyUtils;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.plan.ErpPurchasePlanPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.plan.ErpPurchasePlanSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchasePlanDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchasePlanItemDO;
import cn.iocoder.txgy.module.erp.dal.mysql.purchase.ErpPurchasePlanItemMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.purchase.ErpPurchasePlanMapper;
import cn.iocoder.txgy.module.erp.dal.redis.no.ErpNoRedisDAO;
import cn.iocoder.txgy.module.erp.enums.ErpAuditStatus;
import cn.iocoder.txgy.module.erp.service.product.ErpProductService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.*;

@Service
@Validated
public class ErpPurchasePlanServiceImpl implements ErpPurchasePlanService {

    @Resource private ErpPurchasePlanMapper planMapper;
    @Resource private ErpPurchasePlanItemMapper itemMapper;
    @Resource private ErpNoRedisDAO noRedisDAO;
    @Resource private ErpProductService productService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPurchasePlan(ErpPurchasePlanSaveReqVO reqVO) {
        List<ErpPurchasePlanItemDO> items = validateItems(reqVO.getItems());
        String no = noRedisDAO.generate(ErpNoRedisDAO.PURCHASE_PLAN_NO_PREFIX);
        if (planMapper.selectByNo(no) != null) throw exception(PURCHASE_PLAN_NO_EXISTS);
        ErpPurchasePlanDO plan = BeanUtils.toBean(reqVO, ErpPurchasePlanDO.class)
                .setNo(no).setStatus(ErpAuditStatus.DRAFT.getStatus())
                .setTotalCount(getSumValue(items, ErpPurchasePlanItemDO::getCount, BigDecimal::add, BigDecimal.ZERO));
        planMapper.insert(plan);
        items.forEach(item -> item.setPlanId(plan.getId()));
        itemMapper.insertBatch(items);
        return plan.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePurchasePlan(ErpPurchasePlanSaveReqVO reqVO) {
        ErpPurchasePlanDO plan = validateEditable(reqVO.getId(), PURCHASE_PLAN_UPDATE_FAIL_STATUS);
        List<ErpPurchasePlanItemDO> items = validateItems(reqVO.getItems());
        planMapper.updateById(BeanUtils.toBean(reqVO, ErpPurchasePlanDO.class)
                .setTotalCount(getSumValue(items, ErpPurchasePlanItemDO::getCount, BigDecimal::add, BigDecimal.ZERO)));
        itemMapper.deleteByPlanId(plan.getId());
        items.forEach(item -> item.setId(null).setPlanId(plan.getId()));
        itemMapper.insertBatch(items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePurchasePlan(List<Long> ids) {
        for (ErpPurchasePlanDO plan : planMapper.selectByIds(ids)) {
            validateEditable(plan.getId(), PURCHASE_PLAN_DELETE_FAIL_STATUS);
            planMapper.deleteById(plan.getId());
            itemMapper.deleteByPlanId(plan.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitPurchasePlan(Long id, Long userId) {
        validateEditable(id, PURCHASE_PLAN_SUBMIT_FAIL_STATUS);
        planMapper.updateById(new ErpPurchasePlanDO().setId(id).setStatus(ErpAuditStatus.PROCESS.getStatus()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approvePurchasePlan(Long id, Long userId) {
        ErpPurchasePlanDO plan = validateApproving(id);
        planMapper.updateById(new ErpPurchasePlanDO().setId(plan.getId()).setStatus(ErpAuditStatus.APPROVE.getStatus()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectPurchasePlan(Long id, Long userId) {
        ErpPurchasePlanDO plan = validateApproving(id);
        planMapper.updateById(new ErpPurchasePlanDO().setId(plan.getId()).setStatus(ErpAuditStatus.REJECT.getStatus()));
    }

    /** 校验单据处于「审批中」，只有该状态才允许审批通过 / 驳回 */
    private ErpPurchasePlanDO validateApproving(Long id) {
        ErpPurchasePlanDO plan = validatePurchasePlan(id);
        if (!ObjectUtil.equal(plan.getStatus(), ErpAuditStatus.PROCESS.getStatus())) {
            throw exception(PURCHASE_PLAN_APPROVE_FAIL_STATUS, plan.getNo());
        }
        return plan;
    }

    /** 校验单据处于「草稿 / 审批驳回 / 已撤销」，只有这些状态才允许修改、删除、提交审批 */
    private ErpPurchasePlanDO validateEditable(Long id, ErrorCode code) {
        ErpPurchasePlanDO plan = validatePurchasePlan(id);
        if (!ObjectUtil.equal(plan.getStatus(), ErpAuditStatus.DRAFT.getStatus())
                && !ObjectUtil.equal(plan.getStatus(), ErpAuditStatus.REJECT.getStatus())
                && !ObjectUtil.equal(plan.getStatus(), ErpAuditStatus.CANCEL.getStatus())) {
            throw exception(code, plan.getNo());
        }
        return plan;
    }

    private List<ErpPurchasePlanItemDO> validateItems(List<ErpPurchasePlanSaveReqVO.Item> reqItems) {
        if (CollUtil.isEmpty(reqItems) || reqItems.stream().anyMatch(i -> i.getCount() == null || i.getCount().signum() <= 0)) {
            throw exception(PURCHASE_PLAN_ITEM_COUNT_INVALID);
        }
        List<ErpProductDO> products = productService.validProductList(convertSet(reqItems, ErpPurchasePlanSaveReqVO.Item::getProductId));
        Map<Long, ErpProductDO> productMap = convertMap(products, ErpProductDO::getId);
        return convertList(reqItems, req -> {
            ErpProductDO product = productMap.get(req.getProductId());
            BigDecimal purchasePrice = product.getPurchasePrice();
            if (purchasePrice == null || purchasePrice.signum() <= 0) {
                throw exception(PURCHASE_PLAN_ITEM_PRICE_INVALID, product.getName());
            }
            // 单位、单价和金额全部以服务端主数据重算，拒绝客户端篡改金额。
            return BeanUtils.toBean(req, ErpPurchasePlanItemDO.class)
                    .setProductUnitId(product.getUnitId())
                    .setTargetPrice(purchasePrice)
                    .setAmount(MoneyUtils.priceMultiply(purchasePrice, req.getCount()));
        });
    }

    @Override public ErpPurchasePlanDO getPurchasePlan(Long id) { return planMapper.selectById(id); }
    @Override public ErpPurchasePlanDO validatePurchasePlan(Long id) {
        ErpPurchasePlanDO plan = planMapper.selectById(id);
        if (plan == null) throw exception(PURCHASE_PLAN_NOT_EXISTS);
        return plan;
    }
    @Override public PageResult<ErpPurchasePlanDO> getPurchasePlanPage(ErpPurchasePlanPageReqVO reqVO) { return planMapper.selectPage(reqVO); }
    @Override public List<ErpPurchasePlanItemDO> getItemListByPlanId(Long planId) { return itemMapper.selectListByPlanId(planId); }
    @Override public List<ErpPurchasePlanItemDO> getItemListByPlanIds(Collection<Long> planIds) {
        return CollUtil.isEmpty(planIds) ? Collections.emptyList() : itemMapper.selectListByPlanIds(planIds);
    }
}

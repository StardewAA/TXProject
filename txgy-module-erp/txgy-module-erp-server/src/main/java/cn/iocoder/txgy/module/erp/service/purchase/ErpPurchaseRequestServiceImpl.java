package cn.iocoder.txgy.module.erp.service.purchase;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.txgy.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.request.ErpPurchaseRequestPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.request.ErpPurchaseRequestSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseRequestDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseRequestItemDO;
import cn.iocoder.txgy.module.erp.dal.mysql.purchase.ErpPurchaseRequestItemMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.purchase.ErpPurchaseRequestMapper;
import cn.iocoder.txgy.module.erp.dal.redis.no.ErpNoRedisDAO;
import cn.iocoder.txgy.module.erp.enums.ErpAuditStatus;
import cn.iocoder.txgy.module.erp.service.approval.ErpApprovalNotificationService;
import cn.iocoder.txgy.module.erp.service.product.ErpProductService;
import cn.iocoder.txgy.module.erp.util.ErpAuditStatusUtils;
import cn.iocoder.txgy.module.erp.util.ErpBpmProcessUtils;
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
public class ErpPurchaseRequestServiceImpl implements ErpPurchaseRequestService {
    public static final String BPM_PROCESS_DEFINITION_KEY = "erp-purchase-request-approval";

    @Resource private ErpPurchaseRequestMapper requestMapper;
    @Resource private ErpPurchaseRequestItemMapper itemMapper;
    @Resource private ErpNoRedisDAO noRedisDAO;
    @Resource private ErpProductService productService;
    @Resource private BpmProcessInstanceApi bpmProcessInstanceApi;
    @Resource private ErpApprovalNotificationService notificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPurchaseRequest(ErpPurchaseRequestSaveReqVO reqVO) {
        List<ErpPurchaseRequestItemDO> items = validateItems(reqVO.getItems());
        String no = noRedisDAO.generate(ErpNoRedisDAO.PURCHASE_REQUEST_NO_PREFIX);
        if (requestMapper.selectByNo(no) != null) throw exception(PURCHASE_REQUEST_NO_EXISTS);
        ErpPurchaseRequestDO request = BeanUtils.toBean(reqVO, ErpPurchaseRequestDO.class)
                .setNo(no).setStatus(ErpAuditStatus.DRAFT.getStatus())
                .setTotalCount(getSumValue(items, ErpPurchaseRequestItemDO::getCount, BigDecimal::add, BigDecimal.ZERO));
        requestMapper.insert(request);
        items.forEach(item -> item.setRequestId(request.getId()));
        itemMapper.insertBatch(items);
        return request.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePurchaseRequest(ErpPurchaseRequestSaveReqVO reqVO) {
        ErpPurchaseRequestDO request = validateEditable(reqVO.getId(), PURCHASE_REQUEST_UPDATE_FAIL_STATUS);
        List<ErpPurchaseRequestItemDO> items = validateItems(reqVO.getItems());
        requestMapper.updateById(BeanUtils.toBean(reqVO, ErpPurchaseRequestDO.class)
                .setTotalCount(getSumValue(items, ErpPurchaseRequestItemDO::getCount, BigDecimal::add, BigDecimal.ZERO)));
        itemMapper.deleteByRequestId(request.getId());
        items.forEach(item -> item.setId(null).setRequestId(request.getId()));
        itemMapper.insertBatch(items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePurchaseRequest(List<Long> ids) {
        for (ErpPurchaseRequestDO request : requestMapper.selectByIds(ids)) {
            validateEditable(request.getId(), PURCHASE_REQUEST_DELETE_FAIL_STATUS);
            requestMapper.deleteById(request.getId());
            itemMapper.deleteByRequestId(request.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitPurchaseRequest(Long id, Long userId) {
        ErpPurchaseRequestDO request = validateEditable(id, PURCHASE_REQUEST_SUBMIT_FAIL_STATUS);
        String processId = bpmProcessInstanceApi.createProcessInstance(userId,
                new BpmProcessInstanceCreateReqDTO().setProcessDefinitionKey(BPM_PROCESS_DEFINITION_KEY)
                        .setBusinessKey(String.valueOf(id)).setVariables(Map.of("requestNo", request.getNo(),
                                "budgetAmount", ObjectUtil.defaultIfNull(request.getBudgetAmount(), BigDecimal.ZERO))))
                .getCheckedData();
        requestMapper.updateById(new ErpPurchaseRequestDO().setId(id).setStatus(ErpAuditStatus.PROCESS.getStatus())
                .setProcessInstanceId(processId));
        notificationService.sendBusinessSubmittedAfterCommit(userId, "采购申请", request.getNo(), id,
                "/erp/purchase/request");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reverseApprovePurchaseRequest(Long id, Long userId, String reason) {
        ErpPurchaseRequestDO request = validatePurchaseRequest(id);
        Integer status = request.getStatus();
        // 只有审批中(10)、已审批(20) 才能反审批
        if (!ObjectUtil.equal(status, ErpAuditStatus.PROCESS.getStatus())
                && !ObjectUtil.equal(status, ErpAuditStatus.APPROVE.getStatus())) {
            throw exception(PURCHASE_REQUEST_REVERSE_APPROVE_FAIL_STATUS);
        }
        String processInstanceId = request.getProcessInstanceId();
        int updateCount = requestMapper.reverseApproveByIdAndStatus(id, status, reason);
        if (updateCount == 0) {
            throw exception(PURCHASE_REQUEST_REVERSE_APPROVE_FAIL_STATUS);
        }
        ErpBpmProcessUtils.cancelProcessInstanceAfterCommit(bpmProcessInstanceApi, userId,
                processInstanceId, "采购申请(" + request.getNo() + ")反审批：" + reason);
    }

    @Override
    public void updatePurchaseRequestAuditStatus(Long id, Integer bpmStatus) {
        ErpPurchaseRequestDO request = validatePurchaseRequest(id);
        if (ObjectUtil.equal(request.getStatus(), ErpAuditStatus.PROCESS.getStatus())) {
            requestMapper.updateById(new ErpPurchaseRequestDO().setId(id)
                    .setStatus(ErpAuditStatusUtils.convertBpmResultToAuditStatus(bpmStatus)));
        }
    }

    private ErpPurchaseRequestDO validateEditable(Long id, cn.iocoder.txgy.framework.common.exception.ErrorCode code) {
        ErpPurchaseRequestDO request = validatePurchaseRequest(id);
        if (!ObjectUtil.equal(request.getStatus(), ErpAuditStatus.DRAFT.getStatus())
                && !ObjectUtil.equal(request.getStatus(), ErpAuditStatus.REJECT.getStatus())
                && !ObjectUtil.equal(request.getStatus(), ErpAuditStatus.CANCEL.getStatus())) {
            throw exception(code, request.getNo());
        }
        return request;
    }

    private List<ErpPurchaseRequestItemDO> validateItems(List<ErpPurchaseRequestSaveReqVO.Item> reqItems) {
        if (CollUtil.isEmpty(reqItems) || reqItems.stream().anyMatch(i -> i.getCount() == null || i.getCount().signum() <= 0)) {
            throw exception(PURCHASE_REQUEST_ITEM_COUNT_INVALID);
        }
        List<ErpProductDO> products = productService.validProductList(convertSet(reqItems, ErpPurchaseRequestSaveReqVO.Item::getProductId));
        Map<Long, ErpProductDO> productMap = convertMap(products, ErpProductDO::getId);
        return convertList(reqItems, req -> BeanUtils.toBean(req, ErpPurchaseRequestItemDO.class)
                .setProductUnitId(productMap.get(req.getProductId()).getUnitId())
                .setConvertedCount(BigDecimal.ZERO));
    }

    @Override public ErpPurchaseRequestDO getPurchaseRequest(Long id) { return requestMapper.selectById(id); }
    @Override public ErpPurchaseRequestDO validatePurchaseRequest(Long id) {
        ErpPurchaseRequestDO request = requestMapper.selectById(id);
        if (request == null) throw exception(PURCHASE_REQUEST_NOT_EXISTS);
        return request;
    }
    @Override public PageResult<ErpPurchaseRequestDO> getPurchaseRequestPage(ErpPurchaseRequestPageReqVO reqVO) { return requestMapper.selectPage(reqVO); }
    @Override public List<ErpPurchaseRequestItemDO> getItemListByRequestId(Long requestId) { return itemMapper.selectListByRequestId(requestId); }
    @Override public List<ErpPurchaseRequestItemDO> getItemListByRequestIds(Collection<Long> requestIds) {
        return CollUtil.isEmpty(requestIds) ? Collections.emptyList() : itemMapper.selectListByRequestIds(requestIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int accumulateConvertedCount(Long requestId) {
        List<ErpPurchaseRequestItemDO> items = itemMapper.selectListByRequestId(requestId);
        for (ErpPurchaseRequestItemDO item : items) {
            BigDecimal converted = item.getConvertedCount() == null ? BigDecimal.ZERO : item.getConvertedCount();
            itemMapper.updateById(new ErpPurchaseRequestItemDO().setId(item.getId())
                    .setConvertedCount(converted.add(item.getCount())));
        }
        return items.size();
    }
}

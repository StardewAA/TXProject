package cn.iocoder.txgy.module.erp.service.purchase;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.number.MoneyUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.txgy.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.inquiry.*;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.order.ErpPurchaseOrderSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.*;
import cn.iocoder.txgy.module.erp.dal.mysql.purchase.*;
import cn.iocoder.txgy.module.erp.dal.redis.no.ErpNoRedisDAO;
import cn.iocoder.txgy.module.erp.enums.ErpAuditStatus;
import cn.iocoder.txgy.module.erp.service.approval.ErpApprovalNotificationService;
import cn.iocoder.txgy.module.erp.service.product.ErpProductService;
import cn.iocoder.txgy.module.erp.util.ErpAuditStatusUtils;
import cn.iocoder.txgy.module.erp.util.ErpBpmProcessUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.*;

@Service
@Slf4j
public class ErpInquiryServiceImpl implements ErpInquiryService {
    public static final String INQUIRY_BPM_KEY = "erp-inquiry-approval";
    public static final String AWARD_BPM_KEY = "erp-inquiry-award-approval";
    public static final int BUSINESS_DRAFT = 0, BUSINESS_WAIT_PUBLISH = 10, BUSINESS_QUOTING = 20,
            BUSINESS_COMPARING = 40, BUSINESS_AWARD_APPROVED = 50, BUSINESS_ORDER_CREATED = 60;

    @Resource private ErpInquiryMapper inquiryMapper;
    @Resource private ErpInquiryItemMapper itemMapper;
    @Resource private ErpInquiryQuoteMapper quoteMapper;
    @Resource private ErpInquiryQuoteItemMapper quoteItemMapper;
    /** 询价单受邀供应商关系：供应商数据隔离的判定依据 */
    @Resource private ErpInquirySupplierMapper inquirySupplierMapper;
    @Resource private ErpPurchaseRequestService requestService;
    @Resource private ErpPurchaseOrderService purchaseOrderService;
    @Resource private ErpProductService productService;
    @Resource private ErpSupplierService supplierService;
    @Resource private ErpNoRedisDAO noRedisDAO;
    @Resource private BpmProcessInstanceApi bpmProcessInstanceApi;
    @Resource private ErpApprovalNotificationService notificationService;

    @Override @Transactional(rollbackFor = Exception.class)
    public Long createInquiry(ErpInquirySaveReqVO reqVO) {
        validateDeadline(reqVO.getDeadline());
        List<ErpInquiryItemDO> items = validateItems(reqVO.getItems());
        String no = noRedisDAO.generate(ErpNoRedisDAO.INQUIRY_NO_PREFIX);
        if (inquiryMapper.selectByNo(no) != null) throw exception(INQUIRY_NO_EXISTS);
        ErpInquiryDO inquiry = BeanUtils.toBean(reqVO, ErpInquiryDO.class).setNo(no)
                .setStatus(ErpAuditStatus.DRAFT.getStatus()).setAwardStatus(ErpAuditStatus.DRAFT.getStatus())
                .setBusinessStatus(BUSINESS_DRAFT).setQuoteCount(0)
                .setCurrency(ObjectUtil.defaultIfNull(reqVO.getCurrency(), "CNY"))
                .setTotalCount(getSumValue(items, ErpInquiryItemDO::getCount, BigDecimal::add, BigDecimal.ZERO));
        inquiryMapper.insert(inquiry);
        items.forEach(i -> i.setInquiryId(inquiry.getId())); itemMapper.insertBatch(items);
        saveInvitedSuppliers(inquiry.getId(), reqVO.getSupplierIds());
        return inquiry.getId();
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public Long createInquiryFromRequest(Long requestId) {
        ErpPurchaseRequestDO request = requestService.validatePurchaseRequest(requestId);
        if (!ObjectUtil.equal(request.getStatus(), ErpAuditStatus.APPROVE.getStatus()))
            throw exception(PURCHASE_REQUEST_CONVERT_FAIL_STATUS, request.getNo());
        ErpInquirySaveReqVO reqVO = new ErpInquirySaveReqVO().setPurchaseRequestId(requestId)
                .setTitle("采购申请 " + request.getNo() + " 询价").setInquiryMode(1)
                .setDeadline(LocalDateTime.now().plusDays(7)).setCurrency("CNY").setRemark(request.getRemark())
                .setItems(convertList(requestService.getItemListByRequestId(requestId), item ->
                        new ErpInquirySaveReqVO.Item().setPurchaseRequestItemId(item.getId()).setProductId(item.getProductId())
                                .setProductUnitId(item.getProductUnitId()).setCount(item.getCount()).setRemark(item.getRemark())));
        Long inquiryId = createInquiry(reqVO);
        // 累加「已转询价数量」，使申请侧能显示已转情况（原实现该字段从不累加）
        requestService.accumulateConvertedCount(requestId);
        return inquiryId;
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void updateInquiry(ErpInquirySaveReqVO reqVO) {
        ErpInquiryDO inquiry = validateEditable(reqVO.getId()); validateDeadline(reqVO.getDeadline());
        List<ErpInquiryItemDO> items = validateItems(reqVO.getItems());
        inquiryMapper.updateById(BeanUtils.toBean(reqVO, ErpInquiryDO.class)
                .setTotalCount(getSumValue(items, ErpInquiryItemDO::getCount, BigDecimal::add, BigDecimal.ZERO)));
        itemMapper.deleteByInquiryId(inquiry.getId());
        items.forEach(i -> i.setId(null).setInquiryId(inquiry.getId())); itemMapper.insertBatch(items);
        saveInvitedSuppliers(inquiry.getId(), reqVO.getSupplierIds());
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void deleteInquiry(List<Long> ids) {
        for (Long id : ids) { ErpInquiryDO inquiry = validateEditable(id); inquiryMapper.deleteById(id); itemMapper.deleteByInquiryId(inquiry.getId()); }
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void submitInquiry(Long id, Long userId) {
        ErpInquiryDO inquiry = validateEditable(id);
        String processId = createProcess(userId, INQUIRY_BPM_KEY, id, Map.of("inquiryNo", inquiry.getNo(), "title", inquiry.getTitle()));
        inquiryMapper.updateById(new ErpInquiryDO().setId(id).setStatus(ErpAuditStatus.PROCESS.getStatus()).setProcessInstanceId(processId));
        notificationService.sendBusinessSubmittedAfterCommit(userId, "询价单", inquiry.getNo(), id, "/erp/purchase/inquiry");
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void reverseApproveInquiry(Long id, Long userId, String reason) {
        ErpInquiryDO inquiry = validateExists(id);
        Integer status = inquiry.getStatus();
        // 只有审批中(10)、已审批(20) 才能反审批；已发布进入报价（业务阶段>=20）后禁止
        if ((!ObjectUtil.equal(status, ErpAuditStatus.PROCESS.getStatus())
                && !ObjectUtil.equal(status, ErpAuditStatus.APPROVE.getStatus()))
                || inquiry.getBusinessStatus() >= BUSINESS_QUOTING) {
            throw exception(INQUIRY_REVERSE_APPROVE_FAIL_STATUS);
        }
        String processInstanceId = inquiry.getProcessInstanceId();
        int updateCount = inquiryMapper.reverseApproveInquiryByIdAndStatus(id, status, reason);
        if (updateCount == 0) {
            throw exception(INQUIRY_REVERSE_APPROVE_FAIL_STATUS);
        }
        ErpBpmProcessUtils.cancelProcessInstanceAfterCommit(bpmProcessInstanceApi, userId,
                processInstanceId, "询价单(" + inquiry.getNo() + ")反审批：" + reason);
    }

    @Override public void updateInquiryAuditStatus(Long id, Integer bpmStatus) {
        ErpInquiryDO inquiry = validateExists(id);
        if (!ObjectUtil.equal(inquiry.getStatus(), ErpAuditStatus.PROCESS.getStatus())) return;
        Integer status = ErpAuditStatusUtils.convertBpmResultToAuditStatus(bpmStatus);
        ErpInquiryDO update = new ErpInquiryDO().setId(id).setStatus(status);
        if (ObjectUtil.equal(status, ErpAuditStatus.APPROVE.getStatus())) update.setBusinessStatus(BUSINESS_WAIT_PUBLISH);
        inquiryMapper.updateById(update);
    }

    @Override public void publishInquiry(Long id) {
        ErpInquiryDO inquiry = validateExists(id);
        if (!ObjectUtil.equal(inquiry.getStatus(), ErpAuditStatus.APPROVE.getStatus()) || inquiry.getDeadline().isBefore(LocalDateTime.now()))
            throw exception(INQUIRY_PUBLISH_FAIL_STATUS, inquiry.getNo());
        inquiryMapper.updateById(new ErpInquiryDO().setId(id).setBusinessStatus(BUSINESS_QUOTING));
    }

    @Override public void closeInquiry(Long id) {
        ErpInquiryDO inquiry = validateExists(id);
        if (!ObjectUtil.equal(inquiry.getBusinessStatus(), BUSINESS_QUOTING)) throw exception(INQUIRY_CLOSE_FAIL_STATUS, inquiry.getNo());
        inquiryMapper.updateById(new ErpInquiryDO().setId(id).setBusinessStatus(BUSINESS_COMPARING));
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public Long saveQuote(ErpInquiryQuoteSaveReqVO reqVO) {
        ErpInquiryDO inquiry = validateExists(reqVO.getInquiryId());
        // 供应商身份以登录态为准，不信任前端传入的 supplierId：
        // 供应商门户账号只能为自己报价，内部账号才允许指定供应商代录。
        Long loginSupplierId = getLoginSupplierId();
        if (loginSupplierId != null) {
            if (reqVO.getSupplierId() != null && !ObjectUtil.equal(loginSupplierId, reqVO.getSupplierId())) {
                log.warn("[saveQuote][账号所属供应商({}) 与请求供应商({}) 不一致，按登录态强制覆盖]",
                        loginSupplierId, reqVO.getSupplierId());
            }
            reqVO.setSupplierId(loginSupplierId);
            // 供应商门户账号：必须真的收到过这张询价单，否则不允许报价
            validateSupplierVisible(inquiry.getId(), loginSupplierId);
        } else {
            // 内部代录：询价单已指定受邀范围时，只能为名单内的供应商代录
            validateSupplierInvited(inquiry.getId(), reqVO.getSupplierId());
        }
        if (!ObjectUtil.equal(inquiry.getBusinessStatus(), BUSINESS_QUOTING)) throw exception(INQUIRY_QUOTE_STATUS_INVALID);
        if (!inquiry.getDeadline().isAfter(LocalDateTime.now())) throw exception(INQUIRY_QUOTE_DEADLINE_PASSED, inquiry.getNo());
        supplierService.validateSupplier(reqVO.getSupplierId());
        List<ErpInquiryItemDO> inquiryItems = itemMapper.selectListByInquiryId(inquiry.getId());
        Set<Long> validIds = convertSet(inquiryItems, ErpInquiryItemDO::getId);
        Map<Long, ErpInquiryItemDO> inquiryItemMap = convertMap(inquiryItems, ErpInquiryItemDO::getId);
        if (reqVO.getItems().size() != inquiryItems.size()
                || reqVO.getItems().stream().map(ErpInquiryQuoteSaveReqVO.Item::getInquiryItemId).distinct().count() != inquiryItems.size()
                || reqVO.getItems().stream().anyMatch(i -> !validIds.contains(i.getInquiryItemId())
                || i.getCount().signum() <= 0
                || i.getCount().compareTo(inquiryItemMap.get(i.getInquiryItemId()).getCount()) > 0))
            throw exception(INQUIRY_QUOTE_ITEMS_INCOMPLETE);
        List<ErpInquiryQuoteItemDO> items = convertList(reqVO.getItems(), item -> {
            ErpInquiryQuoteItemDO result = BeanUtils.toBean(item, ErpInquiryQuoteItemDO.class);
            BigDecimal base = MoneyUtils.priceMultiply(item.getProductPrice(), item.getCount());
            BigDecimal tax = MoneyUtils.priceMultiplyPercent(base, ObjectUtil.defaultIfNull(item.getTaxPercent(), BigDecimal.ZERO));
            return result.setTaxPrice(tax).setFreightPrice(ObjectUtil.defaultIfNull(item.getFreightPrice(), BigDecimal.ZERO))
                    .setTotalPrice(base.add(tax).add(ObjectUtil.defaultIfNull(item.getFreightPrice(), BigDecimal.ZERO)))
                    .setAwarded(false).setAwardCount(BigDecimal.ZERO);
        });
        ErpInquiryQuoteDO quote;
        if (reqVO.getId() == null) {
            String no = noRedisDAO.generate(ErpNoRedisDAO.INQUIRY_QUOTE_NO_PREFIX);
            quote = BeanUtils.toBean(reqVO, ErpInquiryQuoteDO.class).setNo(no).setStatus(20)
                    .setVersion(ObjectUtil.defaultIfNull(reqVO.getVersion(), 1)).setSubmittedAt(LocalDateTime.now())
                    .setTotalPrice(getSumValue(items, ErpInquiryQuoteItemDO::getTotalPrice, BigDecimal::add, BigDecimal.ZERO));
            quoteMapper.insert(quote);
            inquiryMapper.updateById(new ErpInquiryDO().setId(inquiry.getId()).setQuoteCount(inquiry.getQuoteCount() + 1));
        } else {
            quote = quoteMapper.selectById(reqVO.getId());
            if (quote == null || !quote.getInquiryId().equals(inquiry.getId())) throw exception(INQUIRY_QUOTE_NOT_EXISTS);
            // 供应商门户账号不能改别家的报价
            if (loginSupplierId != null && !ObjectUtil.equal(quote.getSupplierId(), loginSupplierId)) {
                throw exception(INQUIRY_QUOTE_SUPPLIER_MISMATCH, loginSupplierId);
            }
            quoteMapper.updateById(BeanUtils.toBean(reqVO, ErpInquiryQuoteDO.class)
                    .setTotalPrice(getSumValue(items, ErpInquiryQuoteItemDO::getTotalPrice, BigDecimal::add, BigDecimal.ZERO)));
            quoteItemMapper.deleteByQuoteId(quote.getId());
        }
        items.forEach(i -> i.setId(null).setQuoteId(quote.getId())); quoteItemMapper.insertBatch(items);
        markInvitationQuoted(inquiry.getId(), reqVO.getSupplierId());
        return quote.getId();
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void saveAwardSelection(ErpAwardSelectionReqVO reqVO) {
        ErpInquiryDO inquiry = validateExists(reqVO.getInquiryId());
        if (!ObjectUtil.equal(inquiry.getBusinessStatus(), BUSINESS_COMPARING)) throw exception(AWARD_SELECTION_INVALID);
        List<ErpInquiryItemDO> inquiryItems = itemMapper.selectListByInquiryId(inquiry.getId());
        Map<Long, ErpInquiryItemDO> inquiryItemMap = convertMap(inquiryItems, ErpInquiryItemDO::getId);
        if (reqVO.getItems().size() != inquiryItems.size() || reqVO.getItems().stream().map(ErpAwardSelectionReqVO.Item::getInquiryItemId).distinct().count() != inquiryItems.size())
            throw exception(AWARD_SELECTION_INVALID);
        List<ErpInquiryQuoteDO> quotes = quoteMapper.selectListByInquiryId(inquiry.getId());
        List<ErpInquiryQuoteItemDO> allQuoteItems = quoteItemMapper.selectListByQuoteIds(convertSet(quotes, ErpInquiryQuoteDO::getId));
        Map<Long, ErpInquiryQuoteItemDO> quoteItemMap = convertMap(allQuoteItems, ErpInquiryQuoteItemDO::getId);
        allQuoteItems.stream().filter(i -> Boolean.TRUE.equals(i.getAwarded())).forEach(i -> quoteItemMapper.updateById(new ErpInquiryQuoteItemDO().setId(i.getId()).setAwarded(false).setAwardCount(BigDecimal.ZERO).setAwardReason(null)));
        for (ErpAwardSelectionReqVO.Item selected : reqVO.getItems()) {
            ErpInquiryQuoteItemDO quoteItem = quoteItemMap.get(selected.getQuoteItemId());
            if (quoteItem == null || !quoteItem.getInquiryItemId().equals(selected.getInquiryItemId()) || selected.getAwardCount().signum() <= 0 || selected.getAwardCount().compareTo(quoteItem.getCount()) > 0)
                throw exception(AWARD_SELECTION_INVALID);
            if (selected.getAwardCount().compareTo(inquiryItemMap.get(selected.getInquiryItemId()).getCount()) != 0)
                throw exception(AWARD_COUNT_MISMATCH);
            quoteItemMapper.updateById(new ErpInquiryQuoteItemDO().setId(quoteItem.getId()).setAwarded(true)
                    .setAwardCount(selected.getAwardCount()).setAwardReason(selected.getAwardReason()));
        }
        inquiryMapper.updateById(new ErpInquiryDO().setId(inquiry.getId()).setAwardStatus(ErpAuditStatus.DRAFT.getStatus()));
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void submitAward(Long id, Long userId) {
        ErpInquiryDO inquiry = validateExists(id);
        // 只有进入评标对比阶段（业务状态=40）且定标为草稿状态才能提交定标审批
        if (!ObjectUtil.equal(inquiry.getBusinessStatus(), BUSINESS_COMPARING)
                || !ObjectUtil.equal(inquiry.getAwardStatus(), ErpAuditStatus.DRAFT.getStatus())) {
            throw exception(AWARD_SUBMIT_FAIL_STATUS, inquiry.getNo());
        }
        String processId = createProcess(userId, AWARD_BPM_KEY, id,
                Map.of("inquiryNo", inquiry.getNo(), "title", inquiry.getTitle()));
        inquiryMapper.updateById(new ErpInquiryDO().setId(id)
                .setAwardStatus(ErpAuditStatus.PROCESS.getStatus())
                .setAwardProcessInstanceId(processId));
        notificationService.sendBusinessSubmittedAfterCommit(userId, "询价定标", inquiry.getNo(), id, "/erp/purchase/inquiry");
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void reverseApproveAward(Long id, Long userId, String reason) {
        ErpInquiryDO inquiry = validateExists(id);
        Integer awardStatus = inquiry.getAwardStatus();
        // 只有定标审批中(10)、已定标(20) 才能反审批；已确认生成采购订单（业务阶段=60）后禁止
        if ((!ObjectUtil.equal(awardStatus, ErpAuditStatus.PROCESS.getStatus())
                && !ObjectUtil.equal(awardStatus, ErpAuditStatus.APPROVE.getStatus()))
                || inquiry.getBusinessStatus() >= BUSINESS_ORDER_CREATED) {
            throw exception(AWARD_REVERSE_APPROVE_FAIL_STATUS);
        }
        String processInstanceId = inquiry.getAwardProcessInstanceId();
        int updateCount = inquiryMapper.reverseApproveAwardByIdAndStatus(id, awardStatus, reason);
        if (updateCount == 0) {
            throw exception(AWARD_REVERSE_APPROVE_FAIL_STATUS);
        }
        ErpBpmProcessUtils.cancelProcessInstanceAfterCommit(bpmProcessInstanceApi, userId,
                processInstanceId, "询价定标(" + inquiry.getNo() + ")反审批：" + reason);
    }

    @Override public void updateAwardAuditStatus(Long id, Integer bpmStatus) {
        ErpInquiryDO inquiry = validateExists(id);
        if (!ObjectUtil.equal(inquiry.getAwardStatus(), ErpAuditStatus.PROCESS.getStatus())) return;
        Integer status = ErpAuditStatusUtils.convertBpmResultToAuditStatus(bpmStatus);
        ErpInquiryDO update = new ErpInquiryDO().setId(id).setAwardStatus(status);
        if (ObjectUtil.equal(status, ErpAuditStatus.APPROVE.getStatus())) update.setBusinessStatus(BUSINESS_AWARD_APPROVED);
        inquiryMapper.updateById(update);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public List<Long> confirmAward(Long id) {
        ErpInquiryDO inquiry = validateExists(id);
        if (!ObjectUtil.equal(inquiry.getAwardStatus(), ErpAuditStatus.APPROVE.getStatus())) throw exception(AWARD_CONFIRM_FAIL_STATUS, inquiry.getNo());
        if (CollUtil.isNotEmpty(parseIds(inquiry.getPurchaseOrderIds()))) throw exception(AWARD_ALREADY_CONFIRMED, inquiry.getNo());
        List<ErpInquiryQuoteDO> quotes = quoteMapper.selectListByInquiryId(id);
        Map<Long, ErpInquiryQuoteDO> quoteMap = convertMap(quotes, ErpInquiryQuoteDO::getId);
        List<ErpInquiryQuoteItemDO> awarded = quoteItemMapper.selectListByQuoteIds(quoteMap.keySet()).stream().filter(i -> Boolean.TRUE.equals(i.getAwarded())).toList();
        Map<Long, List<ErpInquiryQuoteItemDO>> bySupplier = awarded.stream().collect(Collectors.groupingBy(i -> quoteMap.get(i.getQuoteId()).getSupplierId()));
        List<Long> orderIds = new ArrayList<>();
        bySupplier.forEach((supplierId, items) -> {
            ErpPurchaseOrderSaveReqVO order = new ErpPurchaseOrderSaveReqVO().setSupplierId(supplierId).setOrderTime(LocalDateTime.now())
                    .setDiscountPercent(BigDecimal.ZERO).setDepositPrice(BigDecimal.ZERO).setSourceType("AWARD_RESULT")
                    .setSourceId(id).setSourceNo(inquiry.getNo()).setBusinessChainNo("INQUIRY-" + inquiry.getNo())
                    .setRemark("由询价定标 " + inquiry.getNo() + " 自动生成").setItems(convertList(items, item ->
                            new ErpPurchaseOrderSaveReqVO.Item().setProductId(item.getProductId()).setProductUnitId(item.getProductUnitId())
                                    .setCount(item.getAwardCount()).setProductPrice(item.getProductPrice()).setTaxPercent(item.getTaxPercent()).setRemark(item.getRemark())));
            orderIds.add(purchaseOrderService.createPurchaseOrder(order));
        });
        inquiryMapper.updateById(new ErpInquiryDO().setId(id).setPurchaseOrderIds(CollUtil.join(orderIds, ",")).setBusinessStatus(BUSINESS_ORDER_CREATED));
        return orderIds;
    }

    private String createProcess(Long userId, String key, Long id, Map<String, Object> variables) {
        return bpmProcessInstanceApi.createProcessInstance(userId, new BpmProcessInstanceCreateReqDTO()
                .setProcessDefinitionKey(key).setBusinessKey(String.valueOf(id)).setVariables(variables)).getCheckedData();
    }
    private ErpInquiryDO validateExists(Long id) { ErpInquiryDO v = inquiryMapper.selectById(id); if (v == null) throw exception(INQUIRY_NOT_EXISTS); return v; }
    private ErpInquiryDO validateEditable(Long id) { ErpInquiryDO v = validateExists(id); if (!ObjectUtil.equal(v.getStatus(), ErpAuditStatus.DRAFT.getStatus()) && !ObjectUtil.equal(v.getStatus(), ErpAuditStatus.REJECT.getStatus())) throw exception(INQUIRY_UPDATE_FAIL_STATUS, v.getNo()); return v; }
    private void validateDeadline(LocalDateTime deadline) { if (deadline == null || !deadline.isAfter(LocalDateTime.now())) throw exception(INQUIRY_DEADLINE_INVALID); }
    private List<ErpInquiryItemDO> validateItems(List<ErpInquirySaveReqVO.Item> items) {
        if (CollUtil.isEmpty(items) || items.stream().anyMatch(i -> i.getCount() == null || i.getCount().signum() <= 0)) throw exception(AWARD_SELECTION_INVALID);
        List<ErpProductDO> products = productService.validProductList(convertSet(items, ErpInquirySaveReqVO.Item::getProductId));
        Map<Long, ErpProductDO> productMap = convertMap(products, ErpProductDO::getId);
        return convertList(items, i -> BeanUtils.toBean(i, ErpInquiryItemDO.class).setProductUnitId(productMap.get(i.getProductId()).getUnitId()));
    }
    private List<Long> parseIds(String ids) { if (ids == null || ids.isBlank()) return Collections.emptyList(); return Arrays.stream(ids.split(",")).map(Long::valueOf).toList(); }
    @Override public ErpInquiryDO getInquiry(Long id) { return inquiryMapper.selectById(id); }

    /**
     * 供应商身份的分页只返回受邀的询价单；内部账号不受限。
     * 放在 Service 层而不是 Controller，保证任何调用方都绕不过这道过滤。
     */
    @Override public PageResult<ErpInquiryDO> getInquiryPage(ErpInquiryPageReqVO reqVO) {
        Long supplierId = getLoginSupplierId();
        if (supplierId == null) {
            return inquiryMapper.selectPage(reqVO);
        }
        List<Long> invitedInquiryIds = convertList(inquirySupplierMapper.selectListBySupplierId(supplierId),
                ErpInquirySupplierDO::getInquiryId);
        return inquiryMapper.selectPageByInquiryIds(reqVO, invitedInquiryIds);
    }

    @Override public List<ErpInquiryItemDO> getItemList(Collection<Long> ids) { return CollUtil.isEmpty(ids) ? Collections.emptyList() : itemMapper.selectListByInquiryIds(ids); }
    @Override public List<ErpInquiryQuoteDO> getQuoteList(Long id) { return quoteMapper.selectListByInquiryId(id); }
    @Override public List<ErpInquiryQuoteItemDO> getQuoteItemList(Collection<Long> ids) { return CollUtil.isEmpty(ids) ? Collections.emptyList() : quoteItemMapper.selectListByQuoteIds(ids); }

    @Override
    public Long getLoginSupplierId() {
        ErpSupplierDO supplier = supplierService.getSupplierByUserId(SecurityFrameworkUtils.getLoginUserId());
        return supplier == null ? null : supplier.getId();
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void saveInvitedSuppliers(Long inquiryId, List<Long> supplierIds) {
        // 全量覆盖：先逻辑删除旧名单，再写入新名单
        inquirySupplierMapper.deleteByInquiryId(inquiryId);
        if (CollUtil.isEmpty(supplierIds)) {
            return;
        }
        List<Long> distinctIds = supplierIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return;
        }
        // 邀请前校验供应商存在且启用，避免把非法主体写进名单
        distinctIds.forEach(supplierService::validateSupplier);
        LocalDateTime invitedAt = LocalDateTime.now();
        inquirySupplierMapper.insertBatch(convertList(distinctIds, supplierId -> ErpInquirySupplierDO.builder()
                .inquiryId(inquiryId).supplierId(supplierId)
                .quoteStatus(ErpInquirySupplierDO.QUOTE_STATUS_NONE).invitedAt(invitedAt).build()));
    }

    @Override
    public List<ErpInquirySupplierDO> getInvitedSupplierList(Long inquiryId) {
        return inquirySupplierMapper.selectListByInquiryId(inquiryId);
    }

    @Override
    public void validateSupplierInvited(Long inquiryId, Long supplierId) {
        if (supplierId == null) {
            return;
        }
        List<ErpInquirySupplierDO> invited = inquirySupplierMapper.selectListByInquiryId(inquiryId);
        // 未配置受邀名单的询价单视为不限受邀范围，兼容改造前的历史数据
        if (CollUtil.isEmpty(invited)) {
            return;
        }
        boolean matched = invited.stream().anyMatch(i -> ObjectUtil.equal(i.getSupplierId(), supplierId));
        if (!matched) {
            throw exception(INQUIRY_SUPPLIER_NOT_INVITED, supplierId);
        }
    }

    @Override
    public void validateSupplierVisible(Long inquiryId, Long supplierId) {
        if (supplierId == null) {
            return;
        }
        if (inquirySupplierMapper.selectByInquiryIdAndSupplierId(inquiryId, supplierId) == null) {
            throw exception(INQUIRY_NOT_VISIBLE_FOR_SUPPLIER);
        }
    }

    /** 报价落库后回写受邀记录，便于采购方查看「谁还没报价」 */
    private void markInvitationQuoted(Long inquiryId, Long supplierId) {
        ErpInquirySupplierDO invited = inquirySupplierMapper.selectByInquiryIdAndSupplierId(inquiryId, supplierId);
        if (invited == null || ObjectUtil.equal(invited.getQuoteStatus(), ErpInquirySupplierDO.QUOTE_STATUS_QUOTED)) {
            return;
        }
        inquirySupplierMapper.updateById(new ErpInquirySupplierDO().setId(invited.getId())
                .setQuoteStatus(ErpInquirySupplierDO.QUOTE_STATUS_QUOTED));
    }
}

package cn.iocoder.txgy.module.erp.service.purchase;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.number.MoneyUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
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
import jakarta.annotation.Resource;
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
public class ErpInquiryServiceImpl implements ErpInquiryService {
    public static final String INQUIRY_BPM_KEY = "erp-inquiry-approval";
    public static final String AWARD_BPM_KEY = "erp-inquiry-award-approval";
    public static final int BUSINESS_DRAFT = 0, BUSINESS_WAIT_PUBLISH = 10, BUSINESS_QUOTING = 20,
            BUSINESS_COMPARING = 40, BUSINESS_AWARD_APPROVED = 50, BUSINESS_ORDER_CREATED = 60;

    @Resource private ErpInquiryMapper inquiryMapper;
    @Resource private ErpInquiryItemMapper itemMapper;
    @Resource private ErpInquiryQuoteMapper quoteMapper;
    @Resource private ErpInquiryQuoteItemMapper quoteItemMapper;
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
        return createInquiry(reqVO);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void updateInquiry(ErpInquirySaveReqVO reqVO) {
        ErpInquiryDO inquiry = validateEditable(reqVO.getId()); validateDeadline(reqVO.getDeadline());
        List<ErpInquiryItemDO> items = validateItems(reqVO.getItems());
        inquiryMapper.updateById(BeanUtils.toBean(reqVO, ErpInquiryDO.class)
                .setTotalCount(getSumValue(items, ErpInquiryItemDO::getCount, BigDecimal::add, BigDecimal.ZERO)));
        itemMapper.deleteByInquiryId(inquiry.getId());
        items.forEach(i -> i.setId(null).setInquiryId(inquiry.getId())); itemMapper.insertBatch(items);
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
        if (!ObjectUtil.equal(inquiry.getBusinessStatus(), BUSINESS_QUOTING)) throw exception(INQUIRY_QUOTE_STATUS_INVALID);
        supplierService.validateSupplier(reqVO.getSupplierId());
        List<ErpInquiryItemDO> inquiryItems = itemMapper.selectListByInquiryId(inquiry.getId());
        Set<Long> validIds = convertSet(inquiryItems, ErpInquiryItemDO::getId);
        if (reqVO.getItems().stream().anyMatch(i -> !validIds.contains(i.getInquiryItemId()) || i.getCount().signum() <= 0))
            throw exception(INQUIRY_QUOTE_STATUS_INVALID);
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
            quoteMapper.updateById(BeanUtils.toBean(reqVO, ErpInquiryQuoteDO.class)
                    .setTotalPrice(getSumValue(items, ErpInquiryQuoteItemDO::getTotalPrice, BigDecimal::add, BigDecimal.ZERO)));
            quoteItemMapper.deleteByQuoteId(quote.getId());
        }
        items.forEach(i -> i.setId(null).setQuoteId(quote.getId())); quoteItemMapper.insertBatch(items);
        return quote.getId();
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void saveAwardSelection(ErpAwardSelectionReqVO reqVO) {
        ErpInquiryDO inquiry = validateExists(reqVO.getInquiryId());
        if (!ObjectUtil.equal(inquiry.getBusinessStatus(), BUSINESS_COMPARING)) throw exception(AWARD_SELECTION_INVALID);
        List<ErpInquiryItemDO> inquiryItems = itemMapper.selectListByInquiryId(inquiry.getId());
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
            quoteItemMapper.updateById(new ErpInquiryQuoteItemDO().setId(quoteItem.getId()).setAwarded(true)
                    .setAwardCount(selected.getAwardCount()).setAwardReason(selected.getAwardReason()));
        }
        inquiryMapper.updateById(new ErpInquiryDO().setId(inquiry.getId()).setAwardStatus(ErpAuditStatus.DRAFT.getStatus()));
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void submitAward(Long id, Long userId) {
        ErpInquiryDO inquiry = validateExists(id);
        if (!ObjectUtil.equal(inquiry.getBusinessStatus(), BUSINESS_COMPARING) || !ObjectUtil.equal(inquiry.getAwardStatus(), ErpAuditStatus.DRAFT.getStatus()))
            throw exception(AWARD_SUBMIT_FAIL_STATUS, inquiry.getNo());
        String processId = createProcess(userId, AWARD_BPM_KEY, id, Map.of("inquiryNo", inquiry.getNo(), "title", inquiry.getTitle()));
        inquiryMapper.updateById(new ErpInquiryDO().setId(id).setAwardStatus(ErpAuditStatus.PROCESS.getStatus()).setAwardProcessInstanceId(processId));
        notificationService.sendBusinessSubmittedAfterCommit(userId, "询价定标", inquiry.getNo(), id, "/erp/purchase/award");
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
    @Override public PageResult<ErpInquiryDO> getInquiryPage(ErpInquiryPageReqVO reqVO) { return inquiryMapper.selectPage(reqVO); }
    @Override public List<ErpInquiryItemDO> getItemList(Collection<Long> ids) { return CollUtil.isEmpty(ids) ? Collections.emptyList() : itemMapper.selectListByInquiryIds(ids); }
    @Override public List<ErpInquiryQuoteDO> getQuoteList(Long id) { return quoteMapper.selectListByInquiryId(id); }
    @Override public List<ErpInquiryQuoteItemDO> getQuoteItemList(Collection<Long> ids) { return CollUtil.isEmpty(ids) ? Collections.emptyList() : quoteItemMapper.selectListByQuoteIds(ids); }
}

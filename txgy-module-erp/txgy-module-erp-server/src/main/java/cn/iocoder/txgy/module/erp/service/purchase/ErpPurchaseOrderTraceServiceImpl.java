package cn.iocoder.txgy.module.erp.service.purchase;

import cn.iocoder.txgy.module.erp.controller.admin.product.vo.product.ErpProductRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.order.ErpPurchaseOrderTraceRespVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.*;
import cn.iocoder.txgy.module.erp.dal.mysql.purchase.ErpInquiryMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.purchase.ErpPurchaseInMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.purchase.ErpPurchaseOrderMapper;
import cn.iocoder.txgy.module.erp.service.product.ErpProductService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ERP 采购订单溯源 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class ErpPurchaseOrderTraceServiceImpl implements ErpPurchaseOrderTraceService {

    /** 来源类型：定标结果 */
    private static final String SOURCE_TYPE_AWARD_RESULT = "AWARD_RESULT";

    @Resource
    private ErpPurchaseOrderMapper purchaseOrderMapper;
    @Resource
    private ErpPurchaseInMapper purchaseInMapper;
    @Resource
    private ErpInquiryMapper inquiryMapper;
    @Resource
    private ErpInquiryService inquiryService;
    @Resource
    private ErpSupplierService supplierService;
    @Resource
    private ErpProductService productService;

    @Override
    public ErpPurchaseOrderTraceRespVO getTrace(Long orderId) {
        ErpPurchaseOrderDO order = purchaseOrderMapper.selectById(orderId);
        if (order == null) {
            return null;
        }
        ErpPurchaseOrderTraceRespVO result = new ErpPurchaseOrderTraceRespVO();
        // 1. 订单信息（含溯源字段）
        Map<Long, ErpSupplierDO> supplierMap = order.getSupplierId() == null ? Collections.emptyMap()
                : supplierService.getSupplierMap(Collections.singleton(order.getSupplierId()));
        ErpSupplierDO supplier = supplierMap.get(order.getSupplierId());
        result.setOrder(new ErpPurchaseOrderTraceRespVO.Order()
                .setId(order.getId()).setNo(order.getNo())
                .setBusinessChainNo(order.getBusinessChainNo())
                .setSourceType(order.getSourceType()).setSourceNo(order.getSourceNo())
                .setSupplierName(supplier != null ? supplier.getName() : null)
                .setOrderTime(order.getOrderTime())
                .setTotalProductPrice(order.getTotalProductPrice()).setTotalPrice(order.getTotalPrice())
                .setStatus(order.getStatus())
                .setInCount(order.getInCount()).setReturnCount(order.getReturnCount()));
        // 2. 溯源可用性判断
        boolean traceable = order.getSourceNo() != null && !order.getSourceNo().isBlank();
        result.setTraceable(traceable);
        if (!traceable) {
            result.setTraceHint(SOURCE_TYPE_AWARD_RESULT.equals(order.getSourceType())
                    ? "订单标记了定标来源但来源单号为空，数据不完整"
                    : "该订单为手工创建，未关联询价/定标数据，因此没有上游溯源");
        }
        // 3. 来源询价单（优先按 sourceId，其次按 sourceNo）
        ErpInquiryDO inquiry = null;
        if (order.getSourceId() != null) {
            inquiry = inquiryService.getInquiry(order.getSourceId());
        }
        if (inquiry == null && traceable) {
            inquiry = inquiryMapper.selectByNo(order.getSourceNo());
        }
        if (inquiry == null) {
            result.setAwardItems(Collections.emptyList());
        } else {
            result.setInquiry(new ErpPurchaseOrderTraceRespVO.Inquiry()
                    .setId(inquiry.getId()).setNo(inquiry.getNo()).setTitle(inquiry.getTitle())
                    .setStatus(inquiry.getStatus()).setAwardStatus(inquiry.getAwardStatus())
                    .setQuoteCount(inquiry.getQuoteCount())
                    .setPurchaseOrderIds(inquiry.getPurchaseOrderIds()));
            result.setAwardItems(buildAwardItems(inquiry.getId(), supplierMap));
        }
        // 4. 该订单下的入库单
        result.setInDocs(buildInDocs(orderId));
        return result;
    }

    /** 中标明细：取自询价报价明细中 awarded = true 的行 */
    private List<ErpPurchaseOrderTraceRespVO.AwardItem> buildAwardItems(Long inquiryId,
                                                                        Map<Long, ErpSupplierDO> supplierMap) {
        List<ErpInquiryQuoteDO> quotes = inquiryService.getQuoteList(inquiryId);
        if (quotes.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, ErpInquiryQuoteDO> quoteMap = quotes.stream()
                .collect(Collectors.toMap(ErpInquiryQuoteDO::getId, q -> q, (a, b) -> a));
        List<ErpInquiryQuoteItemDO> awarded = inquiryService.getQuoteItemList(quoteMap.keySet()).stream()
                .filter(item -> Boolean.TRUE.equals(item.getAwarded()))
                .toList();
        if (awarded.isEmpty()) {
            return Collections.emptyList();
        }
        // 中标供应商可能在订单供应商之外，需要补查
        Set<Long> allSupplierIds = awarded.stream()
                .map(item -> quoteMap.get(item.getQuoteId()))
                .filter(Objects::nonNull)
                .map(ErpInquiryQuoteDO::getSupplierId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        allSupplierIds.removeAll(supplierMap.keySet());
        Map<Long, ErpSupplierDO> merged = new HashMap<>(supplierMap);
        if (!allSupplierIds.isEmpty()) {
            merged.putAll(supplierService.getSupplierMap(allSupplierIds));
        }
        Map<Long, ErpProductRespVO> productMap = productService.getProductVOMap(
                awarded.stream().map(ErpInquiryQuoteItemDO::getProductId).filter(Objects::nonNull)
                        .collect(Collectors.toSet()));
        return awarded.stream().map(item -> {
            ErpInquiryQuoteDO quote = quoteMap.get(item.getQuoteId());
            ErpSupplierDO supplier = quote != null ? merged.get(quote.getSupplierId()) : null;
            ErpProductRespVO product = productMap.get(item.getProductId());
            return new ErpPurchaseOrderTraceRespVO.AwardItem()
                    .setProductCode(product != null ? product.getMaterialCode() : null)
                    .setProductName(product != null ? product.getName() : null)
                    .setSupplierName(supplier != null ? supplier.getName() : null)
                    .setAwardCount(item.getAwardCount()).setProductPrice(item.getProductPrice())
                    .setAwardReason(item.getAwardReason());
        }).toList();
    }

    private List<ErpPurchaseOrderTraceRespVO.InDoc> buildInDocs(Long orderId) {
        return purchaseInMapper.selectListByOrderId(orderId).stream()
                .map(in -> new ErpPurchaseOrderTraceRespVO.InDoc()
                        .setId(in.getId()).setNo(in.getNo()).setInTime(in.getInTime())
                        .setTotalPrice(in.getTotalPrice()).setStatus(in.getStatus())
                        .setBusinessChainNo(in.getBusinessChainNo()).setWmsStatus(in.getWmsStatus()))
                .toList();
    }

}

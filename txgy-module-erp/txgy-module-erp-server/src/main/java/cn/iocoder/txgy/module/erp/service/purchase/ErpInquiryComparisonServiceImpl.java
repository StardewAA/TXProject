package cn.iocoder.txgy.module.erp.service.purchase;

import cn.iocoder.txgy.module.erp.controller.admin.product.vo.product.ErpProductRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.inquiry.ErpInquiryComparisonRespVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.*;
import cn.iocoder.txgy.module.erp.service.product.ErpProductService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ERP 询价横向比价 Service 实现类
 *
 * <p><b>加权评分规则</b>（权重按可用项归一化，某项无数据时自动剔除并重新归一）：</p>
 * <ul>
 *     <li>价格 40 —— 最低未税单价得 100 分，其余按 {@code 最低价 / 本家价 × 100}</li>
 *     <li>交期 25 —— 最早交期得 100 分，其余按相对跨度线性递减（只有一家时全给 100）</li>
 *     <li>账期 20 —— 从账期文本中解析天数，最长者得 100 分</li>
 * </ul>
 * <p>「历史履约」权重预留给后续接入供应商履约数据，当前不计入。</p>
 *
 * <p><b>价格异常检测</b>：同一物料报价数 ≥ 4 时用 IQR 法
 * （低于 Q1−1.5·IQR 记 LOW，高于 Q3+1.5·IQR 记 HIGH）；样本太少时不做判断，避免误报。</p>
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class ErpInquiryComparisonServiceImpl implements ErpInquiryComparisonService {

    /** 权重：价格 */
    private static final BigDecimal W_PRICE = BigDecimal.valueOf(40);
    /** 权重：交期 */
    private static final BigDecimal W_DELIVERY = BigDecimal.valueOf(25);
    /** 权重：账期 */
    private static final BigDecimal W_PAYMENT = BigDecimal.valueOf(20);
    /** IQR 异常判定系数 */
    private static final BigDecimal IQR_FACTOR = BigDecimal.valueOf(1.5);
    /** IQR 检测所需的最小样本数 */
    private static final int IQR_MIN_SAMPLES = 4;
    /** 从账期文本中提取天数 */
    private static final Pattern DAYS_PATTERN = Pattern.compile("(\\d+)");

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Resource
    private ErpInquiryService inquiryService;
    @Resource
    private ErpProductService productService;
    @Resource
    private ErpSupplierService supplierService;

    @Override
    public ErpInquiryComparisonRespVO getComparison(Long inquiryId) {
        ErpInquiryDO inquiry = inquiryService.getInquiry(inquiryId);
        if (inquiry == null) {
            return null;
        }
        List<ErpInquiryItemDO> items = inquiryService.getItemList(List.of(inquiryId));
        List<ErpInquiryQuoteDO> quotes = inquiryService.getQuoteList(inquiryId);
        ErpInquiryComparisonRespVO result = new ErpInquiryComparisonRespVO()
                .setInquiryId(inquiryId).setInquiryNo(inquiry.getNo()).setTitle(inquiry.getTitle())
                .setSupplierCount((int) quotes.stream().map(ErpInquiryQuoteDO::getSupplierId)
                        .filter(Objects::nonNull).distinct().count());
        if (items.isEmpty()) {
            return result.setMaterials(Collections.emptyList());
        }
        // 1. 报价明细按「询价明细」分组
        Map<Long, ErpInquiryQuoteDO> quoteMap = quotes.stream()
                .collect(Collectors.toMap(ErpInquiryQuoteDO::getId, q -> q, (a, b) -> a));
        List<ErpInquiryQuoteItemDO> quoteItems = quotes.isEmpty() ? Collections.emptyList()
                : inquiryService.getQuoteItemList(quoteMap.keySet());
        Map<Long, List<ErpInquiryQuoteItemDO>> groupByItem = quoteItems.stream()
                .filter(qi -> qi.getInquiryItemId() != null)
                .collect(Collectors.groupingBy(ErpInquiryQuoteItemDO::getInquiryItemId));
        // 2. 名称字典
        Map<Long, ErpProductRespVO> productMap = productService.getProductVOMap(
                items.stream().map(ErpInquiryItemDO::getProductId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, ErpSupplierDO> supplierMap = supplierService.getSupplierMap(
                quotes.stream().map(ErpInquiryQuoteDO::getSupplierId).filter(Objects::nonNull).collect(Collectors.toSet()));
        // 3. 逐物料比价
        List<ErpInquiryComparisonRespVO.Material> materials = new ArrayList<>(items.size());
        for (ErpInquiryItemDO item : items) {
            materials.add(buildMaterial(item, groupByItem.getOrDefault(item.getId(), Collections.emptyList()),
                    quoteMap, supplierMap, productMap));
        }
        return result.setMaterials(materials);
    }

    private ErpInquiryComparisonRespVO.Material buildMaterial(ErpInquiryItemDO item,
                                                              List<ErpInquiryQuoteItemDO> rows,
                                                              Map<Long, ErpInquiryQuoteDO> quoteMap,
                                                              Map<Long, ErpSupplierDO> supplierMap,
                                                              Map<Long, ErpProductRespVO> productMap) {
        ErpInquiryComparisonRespVO.Material material = new ErpInquiryComparisonRespVO.Material()
                .setInquiryItemId(item.getId()).setProductId(item.getProductId()).setCount(item.getCount());
        ErpProductRespVO product = productMap.get(item.getProductId());
        if (product != null) {
            material.setProductCode(product.getMaterialCode()).setProductName(product.getName())
                    .setProductUnitName(product.getUnitName());
        }
        material.setAwarded(rows.stream().anyMatch(r -> Boolean.TRUE.equals(r.getAwarded())));
        if (rows.isEmpty()) {
            return material.setQuotes(Collections.emptyList());
        }
        // 1. 构造报价行
        List<ErpInquiryComparisonRespVO.Quote> quoteVOs = rows.stream()
                .map(r -> buildQuote(r, quoteMap.get(r.getQuoteId()), supplierMap))
                .collect(Collectors.toList());
        // 2. 价格统计与异常标记
        fillPriceStats(material, quoteVOs);
        // 3. 评分与排名
        scoreAndRank(quoteVOs);
        // 4. 最优推荐
        quoteVOs.stream().max(Comparator.comparing(ErpInquiryComparisonRespVO.Quote::getTotalScore,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .ifPresent(best -> {
                    best.setRecommended(true);
                    material.setRecommendedQuoteItemId(best.getQuoteItemId())
                            .setRecommendedSupplierId(best.getSupplierId())
                            .setRecommendedSupplierName(best.getSupplierName())
                            .setRecommendedScore(best.getTotalScore());
                });
        // 5. 按总分降序输出
        quoteVOs.sort(Comparator.comparing(ErpInquiryComparisonRespVO.Quote::getTotalScore,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return material.setQuotes(quoteVOs);
    }

    private ErpInquiryComparisonRespVO.Quote buildQuote(ErpInquiryQuoteItemDO row, ErpInquiryQuoteDO quote,
                                                        Map<Long, ErpSupplierDO> supplierMap) {
        ErpInquiryComparisonRespVO.Quote vo = new ErpInquiryComparisonRespVO.Quote()
                .setQuoteItemId(row.getId()).setQuoteId(row.getQuoteId())
                .setCount(row.getCount()).setProductPrice(row.getProductPrice())
                .setTaxPercent(row.getTaxPercent()).setFreightPrice(row.getFreightPrice())
                .setTotalPrice(row.getTotalPrice()).setAwarded(Boolean.TRUE.equals(row.getAwarded()));
        if (quote != null) {
            vo.setQuoteNo(quote.getNo()).setSupplierId(quote.getSupplierId())
                    .setDeliveryDate(quote.getDeliveryDate()).setPaymentTerm(quote.getPaymentTerm());
            ErpSupplierDO supplier = supplierMap.get(quote.getSupplierId());
            if (supplier != null) {
                vo.setSupplierName(supplier.getName());
            }
        }
        return vo;
    }

    /** 价格统计 + IQR 异常标记 */
    private void fillPriceStats(ErpInquiryComparisonRespVO.Material material,
                                List<ErpInquiryComparisonRespVO.Quote> quotes) {
        List<BigDecimal> prices = quotes.stream()
                .map(ErpInquiryComparisonRespVO.Quote::getProductPrice)
                .filter(p -> p != null && p.compareTo(BigDecimal.ZERO) > 0)
                .sorted()
                .collect(Collectors.toList());
        if (prices.isEmpty()) {
            return;
        }
        BigDecimal lowest = prices.get(0);
        BigDecimal highest = prices.get(prices.size() - 1);
        BigDecimal sum = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        material.setLowestPrice(lowest).setHighestPrice(highest)
                .setAveragePrice(sum.divide(BigDecimal.valueOf(prices.size()), 4, RoundingMode.HALF_UP))
                .setMedianPrice(percentile(prices, 0.5));
        // IQR 异常检测（样本足够时才判定）
        if (prices.size() < IQR_MIN_SAMPLES) {
            return;
        }
        BigDecimal q1 = percentile(prices, 0.25);
        BigDecimal q3 = percentile(prices, 0.75);
        BigDecimal iqr = q3.subtract(q1);
        BigDecimal upper = q3.add(IQR_FACTOR.multiply(iqr));
        BigDecimal lower = q1.subtract(IQR_FACTOR.multiply(iqr));
        quotes.forEach(q -> {
            BigDecimal price = q.getProductPrice();
            if (price == null) {
                return;
            }
            if (price.compareTo(upper) > 0) {
                q.setPriceAnomaly("HIGH");
            } else if (price.compareTo(lower) < 0) {
                q.setPriceAnomaly("LOW");
            }
        });
    }

    /** 加权评分与排名 */
    private void scoreAndRank(List<ErpInquiryComparisonRespVO.Quote> quotes) {
        // 价格分：最低价得 100
        BigDecimal lowest = quotes.stream()
                .map(ErpInquiryComparisonRespVO.Quote::getProductPrice)
                .filter(p -> p != null && p.compareTo(BigDecimal.ZERO) > 0)
                .min(Comparator.naturalOrder()).orElse(null);
        // 交期分：最早得 100
        List<LocalDateTime> dates = quotes.stream()
                .map(ErpInquiryComparisonRespVO.Quote::getDeliveryDate)
                .filter(Objects::nonNull).sorted().collect(Collectors.toList());
        LocalDateTime earliest = dates.isEmpty() ? null : dates.get(0);
        LocalDateTime latest = dates.isEmpty() ? null : dates.get(dates.size() - 1);
        long span = (earliest != null && latest != null)
                ? ChronoUnit.DAYS.between(earliest.toLocalDate(), latest.toLocalDate()) : 0L;
        // 账期分：天数最长得 100
        Map<Long, Long> paymentDays = new HashMap<>();
        long maxDays = 0L;
        for (ErpInquiryComparisonRespVO.Quote q : quotes) {
            Long days = parsePaymentDays(q.getPaymentTerm());
            if (days != null) {
                paymentDays.put(q.getQuoteItemId(), days);
                maxDays = Math.max(maxDays, days);
            }
        }
        // 逐行评分
        for (ErpInquiryComparisonRespVO.Quote q : quotes) {
            BigDecimal wSum = BigDecimal.ZERO;
            BigDecimal weighted = BigDecimal.ZERO;
            StringBuilder detail = new StringBuilder();
            // 价格
            if (lowest != null && q.getProductPrice() != null && q.getProductPrice().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal s = lowest.multiply(HUNDRED)
                        .divide(q.getProductPrice(), 2, RoundingMode.HALF_UP);
                q.setPriceScore(s);
                q.setLowestPrice(s.compareTo(HUNDRED) == 0);
                weighted = weighted.add(W_PRICE.multiply(s));
                wSum = wSum.add(W_PRICE);
                detail.append("价格 ").append(W_PRICE.stripTrailingZeros().toPlainString()).append("%(")
                        .append(s.toPlainString()).append(") ");
            }
            // 交期
            if (q.getDeliveryDate() != null && earliest != null) {
                BigDecimal s;
                if (span <= 0) {
                    s = HUNDRED;
                } else {
                    long delay = ChronoUnit.DAYS.between(earliest.toLocalDate(), q.getDeliveryDate().toLocalDate());
                    s = HUNDRED.subtract(HUNDRED.multiply(BigDecimal.valueOf(delay))
                            .divide(BigDecimal.valueOf(span), 2, RoundingMode.HALF_UP));
                }
                q.setDeliveryScore(s);
                weighted = weighted.add(W_DELIVERY.multiply(s));
                wSum = wSum.add(W_DELIVERY);
                detail.append("交期 ").append(W_DELIVERY.stripTrailingZeros().toPlainString()).append("%(")
                        .append(s.toPlainString()).append(") ");
            }
            // 账期
            Long days = paymentDays.get(q.getQuoteItemId());
            if (days != null && maxDays > 0) {
                BigDecimal s = HUNDRED.multiply(BigDecimal.valueOf(days))
                        .divide(BigDecimal.valueOf(maxDays), 2, RoundingMode.HALF_UP);
                q.setPaymentScore(s);
                weighted = weighted.add(W_PAYMENT.multiply(s));
                wSum = wSum.add(W_PAYMENT);
                detail.append("账期 ").append(W_PAYMENT.stripTrailingZeros().toPlainString()).append("%(")
                        .append(s.toPlainString()).append(") ");
            }
            // 加权总分（按可用项归一化）
            if (wSum.compareTo(BigDecimal.ZERO) > 0) {
                q.setTotalScore(weighted.divide(wSum, 2, RoundingMode.HALF_UP));
            }
            q.setScoreDetail(detail.toString().trim());
        }
        // 排名（按总分降序，同分同名次）
        List<ErpInquiryComparisonRespVO.Quote> sorted = quotes.stream()
                .filter(q -> q.getTotalScore() != null)
                .sorted(Comparator.comparing(ErpInquiryComparisonRespVO.Quote::getTotalScore).reversed())
                .collect(Collectors.toList());
        int rank = 0;
        BigDecimal lastScore = null;
        for (int i = 0; i < sorted.size(); i++) {
            ErpInquiryComparisonRespVO.Quote q = sorted.get(i);
            if (lastScore == null || q.getTotalScore().compareTo(lastScore) != 0) {
                rank = i + 1;
                lastScore = q.getTotalScore();
            }
            q.setRank(rank);
        }
    }

    /** 从账期文本解析天数，例如「月结30天」→ 30、「60」→ 60；解析不出返回 null */
    private Long parsePaymentDays(String paymentTerm) {
        if (paymentTerm == null || paymentTerm.isBlank()) {
            return null;
        }
        Matcher matcher = DAYS_PATTERN.matcher(paymentTerm);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    /** 线性插值分位数，入参需已升序排列 */
    private BigDecimal percentile(List<BigDecimal> sorted, double p) {
        int n = sorted.size();
        if (n == 1) {
            return sorted.get(0);
        }
        double idx = p * (n - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        if (lo == hi) {
            return sorted.get(lo);
        }
        BigDecimal frac = BigDecimal.valueOf(idx - lo);
        return sorted.get(lo).add(sorted.get(hi).subtract(sorted.get(lo)).multiply(frac))
                .setScale(4, RoundingMode.HALF_UP);
    }

}

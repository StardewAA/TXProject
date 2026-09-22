package cn.iocoder.txgy.module.product.service.comparison;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.controller.app.comparison.*;
import cn.iocoder.txgy.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.txgy.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.txgy.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.iocoder.txgy.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.txgy.module.product.service.review.ProductSpuReviewService;
import cn.iocoder.txgy.module.product.service.shop.ProductShopService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ProductComparisonService {
    @Resource private ProductSkuMapper skuMapper;
    @Resource private ProductSpuMapper spuMapper;
    @Resource private ProductSpuReviewService reviewService;
    @Resource private ProductShopService shopService;
    @Resource private ComparisonListRepository repository;

    public ComparisonListRepository.Selection getList(Long memberId) {
        return repository.get(tenant(), member(memberId));
    }

    public ComparisonListRepository.Selection saveList(Long memberId, ComparisonRequest request) {
        long tenant = tenant(); long member = member(memberId);
        validate(request, true);
        if (request.getVersion() == null || request.getVersion() < 0 || request.getVersion() >= 9_007_199_254_740_991L)
            throw invalid("请携带当前清单版本");
        var current = repository.get(tenant, member);
        if (request.getVersion() != current.version())
            throw new ServiceException(409, "对比清单已在其他页面更新，请刷新后确认再保存");
        // Existing hidden IDs may be retained/removed one by one, but a guest merge/new ID must be visible.
        // The repository still performs atomic CAS after this read to protect concurrent clients.
        for (Long id : request.getSkuIds()) if (!current.skuIds().contains(id) && !detail(tenant, id).isAvailable())
            throw invalid("部分商品暂不可比较，请移除不可用商品后重试");
        return repository.save(tenant, member, new ComparisonListRepository.Selection(request.getVersion(),
                request.getSkuIds(), request.getQuantity(), request.getPreference()));
    }

    public ComparisonResponse calculate(ComparisonRequest request) {
        long tenant = tenant(); validate(request, false);
        List<ComparisonResponse.Item> items = request.getSkuIds().stream().map(id -> detail(tenant, id)).toList();
        var response = new ComparisonResponse();
        response.setQuantity(request.getQuantity()); response.setPreference(request.getPreference());
        response.setCalculatedAt(System.currentTimeMillis()); response.setItems(items);
        response.setWarnings(List.of("商品标价是当前SKU销售份数的单价，不是每平方米、每千克或含运费到手价",
                "采购数量按各SKU销售份数填写；包装、起订量及单位待确认，不把不同包装视为相同采购量",
                "运费、优惠及到手总额以交易计价和结算结果为准；加购及下单会重新校验价格、商品与库存"));
        var live = items.stream().filter(ComparisonResponse.Item::isAvailable).toList();
        describeRawDifferences(live);
        if (items.size() < 2) {
            response.setRecommendationStatus("NEED_MORE_ITEMS"); response.setRecommendationReason("请至少选择两个SKU进行比较");
        } else if (live.size() < 2) {
            response.setRecommendationStatus("NO_ELIGIBLE_ITEMS"); response.setRecommendationReason("至少需要两个当前可售SKU，请移除不可用商品");
        } else if (live.stream().map(ComparisonResponse.Item::getShopId).distinct().count() < 2) {
            response.setRecommendationStatus("NEED_DIFFERENT_SHOPS"); response.setRecommendationReason("请选择不同店铺的SKU进行跨店比较");
        } else {
            response.setRecommendationStatus("INSUFFICIENT_STANDARD_DATA");
            response.setRecommendationReason("已展示真实SKU资料；缺少核验过的同款、规格与包装资料，暂不按价格推荐或认定可替代");
        }
        return response;
    }

    /** Descriptive only: identical raw display text is never a hard-requirement match or proof of equivalence. */
    private static void describeRawDifferences(List<ComparisonResponse.Item> items) {
        if (items.size() < 2) return;
        Set<String> names = new LinkedHashSet<>();
        items.forEach(item -> item.getProperties().forEach(p -> names.add(p.name())));
        Set<String> differentNames = new LinkedHashSet<>();
        for (String name : names) if (items.stream().map(item -> rawValue(item, name)).distinct().count() > 1) differentNames.add(name);
        boolean differentPrice = items.stream().map(ComparisonResponse.Item::getPrice).distinct().count() > 1;
        for (var item : items) {
            List<String> differences = new ArrayList<>();
            for (String name : differentNames) differences.add("原始“" + name + "”字段不同：" + rawValue(item, name) + "（未标准化）");
            if (differentPrice) differences.add("SKU标价不同，销售单位与包装尚未归一，不能据此判定更划算");
            item.setDifferences(List.copyOf(differences));
        }
    }

    private static String rawValue(ComparisonResponse.Item item, String name) {
        return item.getProperties().stream().filter(p -> Objects.equals(name, p.name())).map(ComparisonResponse.Property::value)
                .collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.joining(" / "), value -> value.isBlank() ? "待补充" : value));
    }

    private ComparisonResponse.Item detail(long tenant, Long id) {
        var item = new ComparisonResponse.Item(); item.setSkuId(id);
        item.setUnavailableReason("商品暂不可比较");
        // ProductSkuDO/ProductSpuDO predate TenantBaseDO: the explicit SQL column is intentional.
        ProductSkuDO sku = skuMapper.selectOne(new QueryWrapper<ProductSkuDO>().eq("id", id).eq("tenant_id", tenant));
        if (sku == null) return item;
        ProductSpuDO spu = spuMapper.selectOne(new QueryWrapper<ProductSpuDO>().eq("id", sku.getSpuId()).eq("tenant_id", tenant));
        if (spu == null || !reviewService.saleable(spu) || sku.getPrice() == null || sku.getPrice() < 0) return item;
        Long shopId = Objects.requireNonNullElse(spu.getShopId(), 0L);
        String shopName = "平台自营";
        if (shopId != 0L) {
            var shop = shopService.getShop(shopId);
            if (shop == null || !Objects.equals(shop.getTenantId(), tenant) || !Objects.equals(shop.getStatus(), 0)) return item;
            shopName = shop.getName();
        }
        item.setAvailable(true); item.setUnavailableReason(null); item.setSpuId(spu.getId());
        item.setSpuName(spu.getName()); item.setPicUrl(sku.getPicUrl() == null || sku.getPicUrl().isBlank() ? spu.getPicUrl() : sku.getPicUrl());
        item.setShopId(shopId); item.setShopName(shopName); item.setPrice(sku.getPrice());
        if (sku.getProperties() != null) item.setProperties(sku.getProperties().stream().filter(Objects::nonNull)
                .map(p -> new ComparisonResponse.Property(Objects.toString(p.getPropertyName(), "待补充"),
                        Objects.toString(p.getValueName(), "待补充"))).toList());
        item.setUnknowns(List.of("核验过的标准商品及硬性规格", "计量单位、包装换算与起订量", "配送费用及优惠", "WMS实时库存与交期"));
        return item;
    }

    static void validate(ComparisonRequest request, boolean allowEmpty) {
        if (request == null || request.getSkuIds() == null || request.getSkuIds().size() > 4
                || (!allowEmpty && request.getSkuIds().isEmpty()) || request.getSkuIds().stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(request.getSkuIds()).size() != request.getSkuIds().size()) throw invalid("请选择不重复的1至4个有效SKU");
        if (request.getQuantity() == null || request.getQuantity() < 1 || request.getQuantity() > 999)
            throw invalid("采购销售份数应为1至999的整数");
        if (request.getPreference() == null || !Set.of("PRICE", "DELIVERY", "BALANCED").contains(request.getPreference()))
            throw invalid("比较偏好不正确");
    }

    private static long tenant() {
        Long id = TenantContextHolder.getTenantId();
        if (id == null || id <= 0 || Boolean.TRUE.equals(TenantContextHolder.isIgnore()))
            throw new ServiceException(403, "请先选择有效商城");
        return id;
    }
    private static long member(Long id) { if (id == null || id <= 0) throw new ServiceException(403, "请先登录商城账号"); return id; }
    private static ServiceException invalid(String message) { return new ServiceException(400, message); }
}

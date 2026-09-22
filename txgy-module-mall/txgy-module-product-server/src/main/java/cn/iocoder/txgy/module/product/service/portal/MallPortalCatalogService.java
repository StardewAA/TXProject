package cn.iocoder.txgy.module.product.service.portal;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.product.controller.app.portal.vo.AppPortalHotTenantRespVO;
import cn.iocoder.txgy.module.product.controller.app.portal.vo.AppPortalProductPageReqVO;
import cn.iocoder.txgy.module.product.controller.app.portal.vo.AppPortalProductRespVO;
import cn.iocoder.txgy.module.product.controller.app.portal.vo.AppPortalTenantRespVO;
import cn.iocoder.txgy.module.product.dal.mysql.portal.MallPortalCatalogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MallPortalCatalogService {

    private static final Pattern TENANT_SLUG = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");
    private static final int MAX_HOT_TENANTS = 100;

    private final MallPortalCatalogMapper mapper;
    private final Clock clock = Clock.systemDefaultZone();

    @TenantIgnore
    public PageResult<AppPortalProductRespVO> getProducts(AppPortalProductPageReqVO request) {
        return getProducts(null, request);
    }

    @TenantIgnore
    public PageResult<AppPortalProductRespVO> getTenantProducts(String tenantSlug,
                                                                 AppPortalProductPageReqVO request) {
        String normalizedSlug = normalizeSlug(tenantSlug);
        requireTenant(normalizedSlug);
        return getProducts(normalizedSlug, request);
    }

    @TenantIgnore
    public AppPortalProductRespVO getProduct(Long spuId) {
        return getProduct(null, spuId);
    }

    @TenantIgnore
    public AppPortalProductRespVO getTenantProduct(String tenantSlug, Long spuId) {
        String normalizedSlug = normalizeSlug(tenantSlug);
        requireTenant(normalizedSlug);
        return getProduct(normalizedSlug, spuId);
    }

    @TenantIgnore
    public AppPortalTenantRespVO getTenant(String tenantSlug) {
        return toTenant(requireTenant(normalizeSlug(tenantSlug)));
    }

    @TenantIgnore
    public List<AppPortalHotTenantRespVO> getHotTenants(String window, Integer requestedLimit) {
        int windowDays = parseWindow(window);
        int limit = requestedLimit == null ? 20 : requestedLimit;
        if (limit < 1 || limit > MAX_HOT_TENANTS) {
            throw new ServiceException(400, "热门租户数量须在 1 到 100 之间");
        }
        LocalDate toDate = LocalDate.now(clock);
        LocalDate fromDate = toDate.minusDays(windowDays - 1L);
        List<MallPortalCatalogMapper.PortalHotTenantRow> rows = mapper.selectHotTenants(fromDate, toDate, limit);
        List<AppPortalHotTenantRespVO> result = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            MallPortalCatalogMapper.PortalHotTenantRow row = rows.get(index);
            result.add(new AppPortalHotTenantRespVO()
                    .setRank(index + 1)
                    .setTenantId(row.getTenantId())
                    .setTenantSlug(row.getTenantSlug())
                    .setTenantName(row.getTenantName())
                    .setTenantLogo(row.getTenantLogo())
                    .setMainCategories(row.getMainCategories())
                    .setValidQuantity(row.getValidQuantity()));
        }
        return result;
    }

    private PageResult<AppPortalProductRespVO> getProducts(String tenantSlug,
                                                            AppPortalProductPageReqVO request) {
        String keyword = normalizeKeyword(request.getKeyword());
        long total = mapper.countProducts(tenantSlug, keyword, request.getCategoryId());
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = Math.multiplyExact((long) request.getPageNo() - 1L, request.getPageSize().longValue());
        List<MallPortalCatalogMapper.PortalProductRow> rows = mapper.selectProducts(tenantSlug, keyword,
                request.getCategoryId(), offset, request.getPageSize());
        if (rows.isEmpty()) {
            return PageResult.empty(total);
        }

        List<Long> spuIds = rows.stream().map(MallPortalCatalogMapper.PortalProductRow::getSpuId).toList();
        Map<Long, List<AppPortalProductRespVO.Sku>> skusBySpu = new LinkedHashMap<>();
        for (MallPortalCatalogMapper.PortalSkuRow sku : mapper.selectPublicSkus(spuIds)) {
            skusBySpu.computeIfAbsent(sku.getSpuId(), ignored -> new ArrayList<>()).add(
                    new AppPortalProductRespVO.Sku()
                            .setSkuId(sku.getSkuId())
                            .setSkuName(publicSkuName(sku))
                            .setProperties(sku.getProperties() == null ? List.of() : List.copyOf(sku.getProperties()))
                            .setPicUrl(sku.getPicUrl())
                            .setPrice(sku.getPrice()));
        }

        List<AppPortalProductRespVO> products = rows.stream()
                .map(row -> toProduct(row, skusBySpu.getOrDefault(row.getSpuId(), List.of())))
                .toList();
        return new PageResult<>(products, total);
    }

    private AppPortalProductRespVO getProduct(String tenantSlug, Long spuId) {
        if (spuId == null || spuId <= 0) {
            throw new ServiceException(404, "商品不存在或未公开");
        }
        MallPortalCatalogMapper.PortalProductRow row = mapper.selectProductById(tenantSlug, spuId);
        if (row == null) {
            throw new ServiceException(404, "商品不存在或未公开");
        }
        List<AppPortalProductRespVO.Sku> skus = toPublicSkus(mapper.selectPublicSkus(List.of(spuId)));
        if (skus.isEmpty()) {
            throw new ServiceException(404, "商品不存在或暂不可售");
        }
        return toProduct(row, skus);
    }

    private static List<AppPortalProductRespVO.Sku> toPublicSkus(
            List<MallPortalCatalogMapper.PortalSkuRow> rows) {
        return rows.stream().map(sku -> new AppPortalProductRespVO.Sku()
                .setSkuId(sku.getSkuId())
                .setSkuName(publicSkuName(sku))
                .setProperties(sku.getProperties() == null ? List.of() : List.copyOf(sku.getProperties()))
                .setPicUrl(sku.getPicUrl())
                .setPrice(sku.getPrice())).toList();
    }

    private static AppPortalProductRespVO toProduct(MallPortalCatalogMapper.PortalProductRow row,
                                                     List<AppPortalProductRespVO.Sku> skuRows) {
        List<AppPortalProductRespVO.Sku> skus = List.copyOf(skuRows);
        return new AppPortalProductRespVO()
                .setTenantId(row.getTenantId())
                .setTenantSlug(row.getTenantSlug())
                .setTenantName(row.getTenantName())
                .setTenantLogo(row.getTenantLogo())
                .setEnterpriseId(row.getEnterpriseId())
                .setEnterpriseName(row.getEnterpriseName())
                .setShopId(row.getShopId())
                .setShopName(row.getShopName())
                .setSpuId(row.getSpuId())
                .setSpuName(row.getSpuName())
                .setIntroduction(row.getIntroduction())
                .setDescription(row.getDescription())
                .setPicUrl(row.getPicUrl())
                .setCategoryId(row.getCategoryId())
                .setPrice(row.getPrice())
                .setSalesCount(row.getSalesCount())
                .setSaleable(!skus.isEmpty())
                .setSkus(skus);
    }

    private MallPortalCatalogMapper.PortalTenantRow requireTenant(String tenantSlug) {
        MallPortalCatalogMapper.PortalTenantRow tenant = mapper.selectTenantBySlug(tenantSlug);
        if (tenant == null) {
            throw new ServiceException(404, "租户商城不存在或未开放");
        }
        return tenant;
    }

    static int parseWindow(String window) {
        String normalized = window == null || window.isBlank() ? "7d" : window.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "7d" -> 7;
            case "30d" -> 30;
            default -> throw new ServiceException(400, "热门租户窗口仅支持 7d 或 30d");
        };
    }

    private static String normalizeSlug(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!TENANT_SLUG.matcher(normalized).matches()) {
            throw new ServiceException(404, "租户商城不存在或未开放");
        }
        return normalized;
    }

    private static String normalizeKeyword(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String publicSkuName(MallPortalCatalogMapper.PortalSkuRow sku) {
        String spuName = Objects.toString(sku.getSpuName(), "").trim();
        if (sku.getProperties() == null || sku.getProperties().isEmpty()) {
            return spuName;
        }
        String specification = sku.getProperties().stream()
                .map(property -> Objects.toString(property.getValueName(), "").trim())
                .filter(value -> !value.isEmpty())
                .collect(Collectors.joining(" "));
        return specification.isEmpty() ? spuName : spuName + " " + specification;
    }

    private static AppPortalTenantRespVO toTenant(MallPortalCatalogMapper.PortalTenantRow row) {
        return new AppPortalTenantRespVO()
                .setTenantId(row.getTenantId())
                .setTenantSlug(row.getTenantSlug())
                .setTenantName(row.getTenantName())
                .setTenantLogo(row.getTenantLogo())
                .setMainCategories(row.getMainCategories());
    }
}

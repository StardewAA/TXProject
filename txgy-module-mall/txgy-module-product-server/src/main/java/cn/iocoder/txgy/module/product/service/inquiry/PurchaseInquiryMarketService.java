package cn.iocoder.txgy.module.product.service.inquiry;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.ip.core.enums.AreaTypeEnum;
import cn.iocoder.txgy.framework.ip.core.utils.AreaUtils;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.api.inquiry.dto.InquiryAcceptedQuoteRespDTO;
import cn.iocoder.txgy.module.product.api.tenant.MallTenantWriteGuard;
import cn.iocoder.txgy.module.product.controller.app.inquiry.*;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.EnterpriseApplicationDO;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.ProductEnterpriseDO;
import cn.iocoder.txgy.module.product.dal.dataobject.inquiry.*;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ProductShopDO;
import cn.iocoder.txgy.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.txgy.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.txgy.module.product.dal.mysql.enterprise.ProductEnterpriseMapper;
import cn.iocoder.txgy.module.product.dal.mysql.inquiry.*;
import cn.iocoder.txgy.module.product.dal.mysql.shop.ProductShopMapper;
import cn.iocoder.txgy.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.iocoder.txgy.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.txgy.module.product.framework.web.LegacyMemberSalesProperties;
import cn.iocoder.txgy.module.product.service.audit.ProductBusinessOperationAuditService;
import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import cn.iocoder.txgy.module.product.service.enterprise.ProductAdminBusinessScopeService;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService.invalid;

@Service
public class PurchaseInquiryMarketService {

    public static final int QUOTE_DRAFT = 0;
    public static final int QUOTE_SUBMITTED = 1;
    public static final int QUOTE_ACCEPTED = 2;
    public static final int QUOTE_REJECTED = 3;
    public static final int QUOTE_WITHDRAWN = 4;

    private static final Set<Integer> PUBLIC_QUOTE_STATUSES = Set.of(
            QUOTE_SUBMITTED, QUOTE_ACCEPTED, QUOTE_REJECTED);
    /** 采购方只可看到已经提交过的报价；供应商草稿始终仅本店铺可见。 */
    private static final Set<Integer> BUYER_VISIBLE_QUOTE_STATUSES = Set.of(
            QUOTE_SUBMITTED, QUOTE_ACCEPTED, QUOTE_REJECTED, QUOTE_WITHDRAWN);
    private static final List<String> PROVINCE_LEVEL_AREAS = List.of(
            "北京市", "天津市", "上海市", "重庆市",
            "河北省", "山西省", "辽宁省", "吉林省", "黑龙江省", "江苏省", "浙江省", "安徽省",
            "福建省", "江西省", "山东省", "河南省", "湖北省", "湖南省", "广东省", "海南省",
            "四川省", "贵州省", "云南省", "陕西省", "甘肃省", "青海省", "台湾省",
            "内蒙古自治区", "广西壮族自治区", "西藏自治区", "宁夏回族自治区", "新疆维吾尔自治区",
            "香港特别行政区", "澳门特别行政区");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?i)(?<![\\w.+-])[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}(?![\\w.-])");
    private static final Pattern OBFUSCATED_EMAIL_PATTERN = Pattern.compile(
            "(?i)(?<![\\w.+-])[\\w.+-]+\\s*[@＠]\\s*[a-z0-9-]+"
                    + "(?:\\s*[.．。]\\s*[a-z0-9-]+)+(?![\\w.-])");
    private static final Pattern ZERO_WIDTH_PATTERN = Pattern.compile("[\\u200B-\\u200D\\u2060\\uFEFF]");
    private static final String PUBLIC_DIGIT = "[0-9０-９]";
    private static final String PUBLIC_DIGIT_SEPARATOR = "[\\s\\-－—.．·]*";
    private static final Pattern MOBILE_PATTERN = Pattern.compile(
            "(?<!" + PUBLIC_DIGIT + ")(?:[+＋]?[8８]" + PUBLIC_DIGIT_SEPARATOR + "[6６]"
                    + PUBLIC_DIGIT_SEPARATOR + ")?[1１]" + PUBLIC_DIGIT_SEPARATOR + "[3-9３-９]"
                    + PUBLIC_DIGIT_SEPARATOR + "(?:" + PUBLIC_DIGIT + PUBLIC_DIGIT_SEPARATOR + "){8}"
                    + PUBLIC_DIGIT + "(?!" + PUBLIC_DIGIT + ")");
    private static final Pattern CHINESE_MOBILE_PATTERN = Pattern.compile(
            "(?<![〇零一二三四五六七八九壹贰叁肆伍陆柒捌玖])[一壹]" + PUBLIC_DIGIT_SEPARATOR
                    + "[三四五六七八九叁肆伍陆柒捌玖]" + PUBLIC_DIGIT_SEPARATOR
                    + "(?:[〇零一二三四五六七八九壹贰叁肆伍陆柒捌玖]" + PUBLIC_DIGIT_SEPARATOR + "){8}"
                    + "[〇零一二三四五六七八九壹贰叁肆伍陆柒捌玖]"
                    + "(?![〇零一二三四五六七八九壹贰叁肆伍陆柒捌玖])");
    private static final Pattern LANDLINE_PATTERN = Pattern.compile(
            "(?<!" + PUBLIC_DIGIT + ")[0０]" + PUBLIC_DIGIT_SEPARATOR
                    + "(?:" + PUBLIC_DIGIT + PUBLIC_DIGIT_SEPARATOR + "){2,3}"
                    + "(?:" + PUBLIC_DIGIT + PUBLIC_DIGIT_SEPARATOR + "){6,7}" + PUBLIC_DIGIT
                    + "(?:[\\s\\-－—转]*(?:" + PUBLIC_DIGIT + PUBLIC_DIGIT_SEPARATOR + "){1,6})?"
                    + "(?!" + PUBLIC_DIGIT + ")");
    private static final Pattern IM_PATTERN = Pattern.compile(
            "(?i)(?:(?:微\\s*信|v\\s*信|wechat|weixin|vx)\\s*[:：号]?\\s*[a-z][a-z0-9_-]{5,19}"
                    + "|(?:q\\s*q|扣\\s*扣)\\s*[:：号]?\\s*(?:" + PUBLIC_DIGIT
                    + PUBLIC_DIGIT_SEPARATOR + "){4,11}" + PUBLIC_DIGIT + ")");
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)(?<![\\w@])(?:(?:https?://|www\\.)[a-z0-9.-]+(?:/[\\w\\-./?%&=+#]*)?"
                    + "|[a-z0-9][a-z0-9.-]*\\.(?:com|cn|net|org|io|co|top|xyz)"
                    + "(?:/[\\w\\-./?%&=+#]*)?)(?![\\w.-])");
    private static final String CONTACT_MASK = "[联系方式已隐藏]";
    /**
     * 租户公开询价的经营类目匹配：店铺至少有一个已审核且当前版本未变更的商品，
     * 其分类与询价中所选 SKU 的商品分类一致，或与手工填写的分类名称匹配。
     */
    private static final String SHOP_CATEGORY_MATCH_FOR_MARKET = "EXISTS ("
            + "SELECT 1 FROM product_spu seller_spu "
            + "LEFT JOIN product_category seller_category ON seller_category.id=seller_spu.category_id "
            + " AND seller_category.deleted=b'0' "
            + "WHERE seller_spu.tenant_id=product_purchase_inquiry.tenant_id "
            + " AND seller_spu.deleted=b'0' AND seller_spu.shop_id={0} "
            + " AND seller_spu.status=1 AND seller_spu.review_status=20 "
            + " AND seller_spu.approved_version=seller_spu.content_version "
            + " AND EXISTS (SELECT 1 FROM product_purchase_inquiry_item requested_item "
            + " LEFT JOIN product_sku requested_sku ON requested_sku.id=requested_item.sku_id "
            + "  AND requested_sku.tenant_id=requested_item.tenant_id AND requested_sku.deleted=b'0' "
            + " LEFT JOIN product_spu requested_spu ON requested_spu.id=requested_sku.spu_id "
            + "  AND requested_spu.tenant_id=requested_item.tenant_id AND requested_spu.deleted=b'0' "
            + " WHERE requested_item.tenant_id=product_purchase_inquiry.tenant_id "
            + "  AND requested_item.deleted=b'0' AND requested_item.inquiry_id=product_purchase_inquiry.id "
            + "  AND ((requested_spu.category_id IS NOT NULL AND requested_spu.category_id=seller_spu.category_id) "
            + "   OR (requested_spu.category_id IS NULL "
            + "    AND NULLIF(TRIM(requested_item.category),'') IS NOT NULL AND seller_category.id IS NOT NULL "
            + "    AND LOWER(TRIM(requested_item.category))=LOWER(TRIM(seller_category.name)))))"
            + ")";
    private static final String SHOP_CATEGORY_MATCH_FOR_INQUIRY = SHOP_CATEGORY_MATCH_FOR_MARKET
            .replace("product_purchase_inquiry.tenant_id", "product_spu.tenant_id")
            .replace("product_purchase_inquiry.id", "{1}");

    @Resource private InquiryMapper inquiryMapper;
    @Resource private InquiryItemMapper inquiryItemMapper;
    @Resource private InquiryQuoteMapper quoteMapper;
    @Resource private InquiryQuoteItemMapper quoteItemMapper;
    @Resource private InquiryQuoteMessageMapper messageMapper;
    @Resource private InquiryInvitedShopMapper invitedShopMapper;
    @Resource private InquiryCrossTenantMapper crossTenantMapper;
    @Resource private ProductEnterpriseMapper enterpriseMapper;
    @Resource private ProductShopMapper shopMapper;
    @Resource private ProductSkuMapper skuMapper;
    @Resource private ProductSpuMapper spuMapper;
    @Resource private EnterpriseMembershipService membership;
    @Resource private ProductAdminBusinessScopeService adminScope;
    @Resource private ProductBusinessOperationAuditService operationAuditService;
    @Resource private LegacyMemberSalesProperties legacyMemberSalesProperties;

    public PageResult<InquiryPublicSummaryRespVO> publicPage(PageParam page, String state, String keyword) {
        String normalizedState = normalizeMarketState(state);
        String publicKeyword = normalizePublicKeyword(keyword);
        long now = System.currentTimeMillis();
        long offset = Math.multiplyExact((long) page.getPageNo() - 1L, page.getPageSize());
        List<InquiryCrossTenantMapper.OpportunityRef> refs = crossTenantMapper.selectPlatformPublicInquiries(
                normalizedState, now, publicKeyword, offset, page.getPageSize());
        if (refs == null || refs.isEmpty()) {
            return PageResult.empty(crossTenantMapper.countPlatformPublicInquiries(
                    normalizedState, now, publicKeyword));
        }
        List<InquiryPublicSummaryRespVO> summaries = crossTenantSummaries(refs);
        long total = crossTenantMapper.countPlatformPublicInquiries(normalizedState, now, publicKeyword);
        return new PageResult<>(summaries, total);
    }

    /** 后台供应商询价机会；当前店铺不能看到本企业发布的询价。 */
    public PageResult<InquiryPublicSummaryRespVO> adminOpportunityPage(Long adminUserId, Long shopId,
                                                                       PageParam page, String state, String keyword) {
        ProductShopDO shop = adminScope.requireMerchantShop(adminUserId, shopId);
        Long viewerTenantId = TenantContextHolder.getRequiredTenantId();
        return crossTenantMarketPage(page, state, keyword, viewerTenantId, shop.getEnterpriseId(), shop.getId());
    }

    private PageResult<InquiryPublicSummaryRespVO> crossTenantMarketPage(
            PageParam page, String state, String keyword, Long viewerTenantId,
            Long viewerEnterpriseId, Long viewerShopId) {
        String normalizedState = normalizeMarketState(state);
        String publicKeyword = normalizePublicKeyword(keyword);
        long now = System.currentTimeMillis();
        long offset = Math.multiplyExact((long) page.getPageNo() - 1L, page.getPageSize());
        List<InquiryCrossTenantMapper.OpportunityRef> refs = crossTenantMapper.selectVisibleOpportunities(
                viewerTenantId, viewerEnterpriseId, viewerShopId, normalizedState, now,
                publicKeyword, offset, page.getPageSize());
        if (refs == null || refs.isEmpty()) return PageResult.empty(crossTenantMapper.countVisibleOpportunities(
                viewerTenantId, viewerEnterpriseId, viewerShopId, normalizedState, now, publicKeyword));
        List<InquiryPublicSummaryRespVO> summaries = crossTenantSummaries(refs);
        long total = crossTenantMapper.countVisibleOpportunities(viewerTenantId, viewerEnterpriseId,
                viewerShopId, normalizedState, now, publicKeyword);
        return new PageResult<>(summaries, total);
    }

    private List<InquiryPublicSummaryRespVO> crossTenantSummaries(
            List<InquiryCrossTenantMapper.OpportunityRef> refs) {
        Map<Long, List<InquiryCrossTenantMapper.OpportunityRef>> byTenant = refs.stream()
                .collect(Collectors.groupingBy(InquiryCrossTenantMapper.OpportunityRef::getInquiryTenantId,
                        LinkedHashMap::new, Collectors.toList()));
        Map<Long, InquiryPublicSummaryRespVO> summaries = new HashMap<>();
        for (var entry : byTenant.entrySet()) {
            Long tenantId = requireBusinessTenant(entry.getKey());
            Map<Long, InquiryCrossTenantMapper.OpportunityRef> expected = entry.getValue().stream()
                    .collect(Collectors.toMap(InquiryCrossTenantMapper.OpportunityRef::getInquiryId,
                            Function.identity(), (left, right) -> left));
            List<Long> ids = entry.getValue().stream().map(InquiryCrossTenantMapper.OpportunityRef::getInquiryId)
                    .filter(Objects::nonNull).toList();
            if (ids.isEmpty()) continue;
            List<InquiryPublicSummaryRespVO> tenantRows = inTenant(tenantId, () -> {
                List<InquiryDO> rows = inquiryMapper.selectList(new LambdaQueryWrapperX<InquiryDO>()
                        .in(InquiryDO::getId, ids));
                if (rows.stream().anyMatch(row -> !Objects.equals(row.getTenantId(), tenantId)
                        || expected.get(row.getId()) == null
                        || !Objects.equals(currentVisibility(row), expected.get(row.getId()).getVisibility()))) {
                    throw invalid("询价真实租户校验失败");
                }
                return publicSummaries(rows);
            });
            tenantRows.forEach(row -> summaries.put(row.getId(), row));
        }
        return refs.stream().map(ref -> summaries.get(ref.getInquiryId())).filter(Objects::nonNull).toList();
    }

    private static String normalizeMarketState(String state) {
        if (state == null || state.isBlank() || "open".equals(state)) return "open";
        if ("ended".equals(state) || "all".equals(state)) return state;
        throw invalid("询价市场状态仅支持 open、ended 或 all");
    }

    private PageResult<InquiryPublicSummaryRespVO> marketPage(PageParam page, String state, String keyword,
                                                               Long excludedEnterpriseId, Long viewerShopId) {
        long now = System.currentTimeMillis();
        String publicKeyword = normalizePublicKeyword(keyword);
        var query = new LambdaQueryWrapperX<InquiryDO>().in(InquiryDO::getStatus, List.of(1, 3, 4))
                .isNotNull(InquiryDO::getNo).ne(InquiryDO::getNo, "")
                .apply("EXISTS (SELECT 1 FROM product_enterprise pe"
                        + " WHERE pe.id = product_purchase_inquiry.enterprise_id"
                        + " AND pe.tenant_id = product_purchase_inquiry.tenant_id"
                        + " AND pe.deleted = 0 AND pe.audit_status = 1)");
        if (viewerShopId == null) {
            query.apply("COALESCE(visibility, 'TENANT_PUBLIC') = 'TENANT_PUBLIC'");
        } else {
            query.apply("((COALESCE(visibility, 'TENANT_PUBLIC') = 'TENANT_PUBLIC' AND "
                    + SHOP_CATEGORY_MATCH_FOR_MARKET + ") OR (COALESCE(visibility, 'TENANT_PUBLIC') = 'INVITED_SHOPS' AND EXISTS ("
                    + "SELECT 1 FROM product_purchase_inquiry_invited_shop pis"
                    + " WHERE pis.inquiry_id = product_purchase_inquiry.id"
                    + " AND pis.tenant_id = product_purchase_inquiry.tenant_id"
                    + " AND pis.deleted = b'0' AND pis.shop_id = {0})))", viewerShopId);
        }
        if (excludedEnterpriseId != null) query.ne(InquiryDO::getEnterpriseId, excludedEnterpriseId);
        if (state == null || state.isBlank() || "open".equals(state)) {
            query.eq(InquiryDO::getStatus, 1).gt(InquiryDO::getDeadline, now);
        } else if ("ended".equals(state)) {
            query.and(w -> w.in(InquiryDO::getStatus, List.of(3, 4))
                    .or(n -> n.eq(InquiryDO::getStatus, 1).le(InquiryDO::getDeadline, now)));
        } else {
            throw invalid("询价市场状态仅支持 open 或 ended");
        }
        if (publicKeyword != null) {
            // Bound parameters only; search excludes contact and detailed delivery address fields.
            query.and(term -> term.like(InquiryDO::getNo, publicKeyword)
                    .or().like(InquiryDO::getTitle, publicKeyword)
                    .or().apply("EXISTS (SELECT 1 FROM product_enterprise keyword_enterprise"
                                    + " WHERE keyword_enterprise.id=product_purchase_inquiry.enterprise_id"
                                    + " AND keyword_enterprise.tenant_id=product_purchase_inquiry.tenant_id"
                                    + " AND keyword_enterprise.deleted=b'0' AND keyword_enterprise.audit_status=1"
                                    + " AND LOWER(keyword_enterprise.name) LIKE CONCAT('%',{0},'%'))",
                            publicKeyword)
                    .or().apply("EXISTS (SELECT 1 FROM product_purchase_inquiry_item keyword_item"
                                    + " WHERE keyword_item.inquiry_id=product_purchase_inquiry.id"
                                    + " AND keyword_item.tenant_id=product_purchase_inquiry.tenant_id"
                                    + " AND keyword_item.deleted=b'0' AND (LOWER(keyword_item.name)"
                                    + " LIKE CONCAT('%',{0},'%') OR LOWER(keyword_item.category)"
                                    + " LIKE CONCAT('%',{0},'%')))", publicKeyword));
        }
        query.orderByDesc(InquiryDO::getId);
        PageResult<InquiryDO> rows = inquiryMapper.selectPage(page, query);
        if (rows.getList().isEmpty()) {
            return PageResult.empty(rows.getTotal());
        }
        List<InquiryPublicSummaryRespVO> summaries = publicSummaries(rows.getList());
        long safeTotal = summaries.size() == rows.getList().size() ? rows.getTotal() : summaries.size();
        return new PageResult<>(summaries, safeTotal);
    }

    public InquiryPublicDetailRespVO publicGet(Long id) {
        if (id == null || id <= 0) {
            throw invalid("公开询价单不存在");
        }
        InquiryCrossTenantMapper.OpportunityRef ref = crossTenantMapper.selectPlatformPublicInquiry(id);
        if (ref == null || ref.getInquiryTenantId() == null) {
            throw invalid("公开询价单不存在");
        }
        Long inquiryTenantId = requireBusinessTenant(ref.getInquiryTenantId());
        return inTenant(inquiryTenantId, () -> {
            InquiryDO inquiry = inquiryMapper.selectById(id);
            if (!isPublishedInquiry(inquiry) || !isPlatformPublic(inquiry)
                    || !Objects.equals(inquiry.getTenantId(), inquiryTenantId)) {
                throw invalid("公开询价单不存在");
            }
            return marketDetail(inquiry, false);
        });
    }

    private InquiryPublicDetailRespVO marketDetail(InquiryDO inquiry, boolean includeParticipantRemarks) {
        List<InquiryItemDO> items = inquiryItems(inquiry.getId());
        InquiryPublicDetailRespVO result = new InquiryPublicDetailRespVO();
        result.setOrder(publicSummary(inquiry, items, publicQuoteCount(inquiry.getId()),
                enterpriseName(inquiry.getEnterpriseId())));
        result.setRemark(includeParticipantRemarks ? sanitizePublicText(inquiry.getRemark()) : null);
        result.setItems(items.stream().map(row -> publicItem(row, includeParticipantRemarks)).toList());
        return result;
    }

    public InquiryPublicDetailRespVO adminOpportunityGet(Long adminUserId, Long shopId, Long id) {
        ProductShopDO shop = adminScope.requireMerchantShop(adminUserId, shopId);
        Long viewerTenantId = TenantContextHolder.getRequiredTenantId();
        InquiryCrossTenantMapper.OpportunityRef ref = crossTenantMapper.selectVisibleOpportunity(
                viewerTenantId, shop.getEnterpriseId(), shop.getId(), id);
        if (ref == null) {
            throw invalid("询价机会不存在或当前店铺不能参与");
        }
        Long inquiryTenantId = requireBusinessTenant(ref.getInquiryTenantId());
        return inTenant(inquiryTenantId, () -> {
            InquiryDO inquiry = inquiryMapper.selectById(id);
            if (!isPublishedInquiry(inquiry) || !Objects.equals(inquiry.getTenantId(), inquiryTenantId)
                    || !Objects.equals(currentVisibility(inquiry), ref.getVisibility())
                    || Objects.equals(shop.getEnterpriseId(), inquiry.getEnterpriseId())) {
                throw invalid("询价机会不存在或当前店铺不能参与");
            }
            return marketDetail(inquiry, true);
        });
    }

    private List<InquiryPublicSummaryRespVO> publicSummaries(List<InquiryDO> rows) {
        rows = rows.stream().filter(PurchaseInquiryMarketService::isPublishedInquiry).toList();
        if (rows.isEmpty()) return List.of();
        Set<Long> inquiryIds = rows.stream().map(InquiryDO::getId).collect(Collectors.toSet());
        Set<Long> enterpriseIds = rows.stream().map(InquiryDO::getEnterpriseId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, List<InquiryItemDO>> items = inquiryItemMapper.selectList(
                        new LambdaQueryWrapperX<InquiryItemDO>().in(InquiryItemDO::getInquiryId, inquiryIds)
                                .orderByAsc(InquiryItemDO::getId)).stream()
                .collect(Collectors.groupingBy(InquiryItemDO::getInquiryId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, Long> quoteCounts = quoteMapper.selectList(new LambdaQueryWrapperX<InquiryQuoteDO>()
                        .in(InquiryQuoteDO::getInquiryId, inquiryIds)
                        .in(InquiryQuoteDO::getStatus, PUBLIC_QUOTE_STATUSES)).stream()
                .collect(Collectors.groupingBy(InquiryQuoteDO::getInquiryId, Collectors.counting()));
        Map<Long, String> enterpriseNames = enterpriseIds.isEmpty() ? Map.of() : enterpriseMapper.selectBatchIds(enterpriseIds)
                .stream().filter(e -> Objects.equals(e.getAuditStatus(), 1))
                .collect(Collectors.toMap(ProductEnterpriseDO::getId, e -> Objects.toString(e.getName(), "")));
        return rows.stream().filter(row -> enterpriseNames.containsKey(row.getEnterpriseId())).map(row -> publicSummary(row,
                items.getOrDefault(row.getId(), List.of()), quoteCounts.getOrDefault(row.getId(), 0L),
                enterpriseNames.getOrDefault(row.getEnterpriseId(), "企业用户"))).toList();
    }

    private InquiryPublicSummaryRespVO publicSummary(InquiryDO row, List<InquiryItemDO> items,
                                                       long quoteCount, String enterpriseName) {
        InquiryPublicSummaryRespVO result = new InquiryPublicSummaryRespVO();
        result.setId(row.getId());
        result.setInquiryTenantId(row.getTenantId());
        result.setNo(row.getNo());
        result.setTitle(sanitizePublicText(row.getTitle()));
        result.setEnterpriseName(sanitizePublicText(enterpriseName));
        result.setDeliveryArea(publicDeliveryArea(row));
        result.setItemSummary(itemSummary(items));
        result.setVisibility(PurchaseInquiryService.normalizeVisibility(row.getVisibility()));
        result.setDeadline(row.getDeadline());
        result.setExpectedDate(row.getExpectedDate());
        result.setStatus(PurchaseInquiryService.effectiveStatus(row));
        result.setQuoteCount(quoteCount);
        result.setItemCount(items.size());
        result.setCanQuote(Objects.equals(result.getStatus(), 1));
        return result;
    }

    private static String publicDeliveryArea(InquiryDO row) {
        if (row.getDeliveryAreaId() != null) {
            Integer cityId = row.getDeliveryCityId() != null ? row.getDeliveryCityId()
                    : AreaUtils.getParentIdByType(row.getDeliveryAreaId(), AreaTypeEnum.CITY);
            Integer provinceId = row.getDeliveryProvinceId() != null ? row.getDeliveryProvinceId()
                    : AreaUtils.getParentIdByType(row.getDeliveryAreaId(), AreaTypeEnum.PROVINCE);
            String formatted = AreaUtils.format(cityId != null ? cityId : provinceId);
            if (formatted != null && !formatted.isBlank()) return formatted;
        }
        return maskDeliveryArea(row.getAddress());
    }

    private InquiryPublicDetailRespVO.Item publicItem(InquiryItemDO row, boolean includeParticipantRemarks) {
        InquiryPublicDetailRespVO.Item item = new InquiryPublicDetailRespVO.Item();
        item.setId(row.getId()); item.setName(sanitizePublicText(row.getName()));
        item.setCode(sanitizePublicText(row.getCode()));
        item.setCategory(sanitizePublicText(row.getCategory()));
        item.setSpecification(sanitizePublicText(row.getSpecification()));
        item.setQuantity(row.getQuantity()); item.setUnit(sanitizePublicText(row.getUnit()));
        item.setRemark(includeParticipantRemarks ? sanitizePublicText(row.getRemark()) : null);
        // 当前商品模块只校验 URL 语法，无法证明 URL 确由平台文件服务签发；公开接口先按失败关闭处理。
        // 保留字段用于后续接入文件元数据校验，现阶段不把任意用户输入的 HTTP(S) 地址透传给匿名访客。
        item.setPicUrl(null);
        return item;
    }

    private String enterpriseName(Long enterpriseId) {
        var enterprise = enterpriseId == null ? null : enterpriseMapper.selectById(enterpriseId);
        if (enterprise == null || !Objects.equals(enterprise.getAuditStatus(), 1)) {
            throw invalid("公开询价单不存在");
        }
        return Objects.toString(enterprise.getName(), "企业用户");
    }

    private long publicQuoteCount(Long inquiryId) {
        return quoteMapper.selectCount(new LambdaQueryWrapperX<InquiryQuoteDO>()
                .eq(InquiryQuoteDO::getInquiryId, inquiryId)
                .in(InquiryQuoteDO::getStatus, PUBLIC_QUOTE_STATUSES));
    }

    private static String itemSummary(List<InquiryItemDO> items) {
        String summary = items.stream().map(InquiryItemDO::getName).map(PurchaseInquiryMarketService::sanitizePublicText)
                .filter(Objects::nonNull)
                .filter(name -> !name.isBlank()).limit(3).collect(Collectors.joining("、"));
        if (summary.isBlank()) return "未命名物品";
        return items.size() > 3 ? summary + "等" + items.size() + "项" : summary;
    }

    public static String maskDeliveryArea(String address) {
        if (address == null || address.isBlank()) return "成交后可见";
        String normalized = address.trim();
        return PROVINCE_LEVEL_AREAS.stream().filter(normalized::startsWith).findFirst().orElse("成交后可见");
    }

    /** 公开询价内容为用户输入，服务端兜底移除常见电话、座机和邮箱。 */
    public static String sanitizePublicText(String value) {
        if (value == null || value.isBlank()) return value;
        String sanitized = ZERO_WIDTH_PATTERN.matcher(value).replaceAll("");
        sanitized = OBFUSCATED_EMAIL_PATTERN.matcher(sanitized).replaceAll(CONTACT_MASK);
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll(CONTACT_MASK);
        sanitized = URL_PATTERN.matcher(sanitized).replaceAll(CONTACT_MASK);
        sanitized = IM_PATTERN.matcher(sanitized).replaceAll(CONTACT_MASK);
        sanitized = MOBILE_PATTERN.matcher(sanitized).replaceAll(CONTACT_MASK);
        sanitized = CHINESE_MOBILE_PATTERN.matcher(sanitized).replaceAll(CONTACT_MASK);
        return LANDLINE_PATTERN.matcher(sanitized).replaceAll(CONTACT_MASK);
    }

    private static boolean isPublishedInquiry(InquiryDO inquiry) {
        return inquiry != null && Set.of(1, 3, 4).contains(inquiry.getStatus())
                && inquiry.getNo() != null && !inquiry.getNo().isBlank();
    }

    private static boolean isTenantPublic(InquiryDO inquiry) {
        return PurchaseInquiryService.VISIBILITY_TENANT_PUBLIC.equals(
                PurchaseInquiryService.normalizeVisibility(inquiry.getVisibility()));
    }

    private static boolean isPlatformPublic(InquiryDO inquiry) {
        return PurchaseInquiryService.VISIBILITY_PLATFORM_PUBLIC.equals(
                PurchaseInquiryService.normalizeVisibility(inquiry.getVisibility()));
    }

    private static String currentVisibility(InquiryDO inquiry) {
        return PurchaseInquiryService.normalizeVisibility(inquiry.getVisibility());
    }

    private boolean canShopView(InquiryDO inquiry, Long shopId) {
        if (isTenantPublic(inquiry)) {
            return spuMapper.selectCount(new LambdaQueryWrapperX<ProductSpuDO>()
                    .eq(ProductSpuDO::getShopId, shopId)
                    .apply(SHOP_CATEGORY_MATCH_FOR_INQUIRY, shopId, inquiry.getId())) > 0;
        }
        return invitedShopMapper.selectCount(new LambdaQueryWrapperX<InquiryInvitedShopDO>()
                .eq(InquiryInvitedShopDO::getInquiryId, inquiry.getId())
                .eq(InquiryInvitedShopDO::getShopId, shopId)) > 0;
    }

    public static String normalizePublicKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String normalized = keyword.trim();
        if (normalized.length() > 50) throw invalid("公开询价搜索词不能超过 50 个字符");
        if (normalized.indexOf('%') >= 0 || normalized.indexOf('_') >= 0) {
            throw invalid("公开询价搜索词不能包含通配符");
        }
        if (!Objects.equals(normalized, sanitizePublicText(normalized))) {
            throw invalid("不能使用联系方式搜索公开询价");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveQuote(Long memberId, InquiryQuoteSaveReqVO request) {
        requireWritableTenant();
        EnterpriseApplicationDO application = membership.requireSupplier(memberId);
        membership.requireShop(memberId, request.getShopId());
        Long supplierTenantId = TenantContextHolder.getRequiredTenantId();
        return saveSupplierQuote(application.getEnterpriseId(), supplierTenantId, null, request, null, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long adminSaveQuote(Long adminUserId, InquiryQuoteSaveReqVO request) {
        requireWritableTenant();
        ProductShopDO shop = adminScope.requireMerchantShop(adminUserId, request.getShopId());
        Long supplierTenantId = TenantContextHolder.getRequiredTenantId();
        InquiryCrossTenantMapper.OpportunityRef ref = crossTenantMapper.selectVisibleOpportunity(
                supplierTenantId, shop.getEnterpriseId(), shop.getId(), request.getInquiryId());
        if (ref == null) throw invalid("询价机会不存在或当前店铺不能参与");
        Long inquiryTenantId = requireBusinessTenant(ref.getInquiryTenantId());
        List<InquiryItemDO> requested = inTenant(inquiryTenantId,
                () -> inquiryItems(request.getInquiryId()));
        ValidatedQuote validated = validateQuoteItems(supplierTenantId, inquiryTenantId, request.getInquiryId(),
                shop.getId(), requested, request.getItems());
        return inTenant(inquiryTenantId, () -> saveSupplierQuote(shop.getEnterpriseId(), supplierTenantId,
                adminUserId, request, validated, true));
    }

    private Long saveSupplierQuote(Long supplierEnterpriseId, Long supplierTenantId, Long adminUserId,
                                   InquiryQuoteSaveReqVO request, ValidatedQuote prevalidated,
                                   boolean crossTenantAuthorized) {
        InquiryDO inquiry = lockInquiry(request.getInquiryId());
        Long inquiryTenantId = TenantContextHolder.getRequiredTenantId();
        if (!Objects.equals(inquiry.getTenantId(), inquiryTenantId) || Objects.equals(inquiryTenantId, 3000L)) {
            throw invalid("询价真实租户校验失败");
        }
        requireOpenForQuote(inquiry);
        requireApprovedInquiryEnterprise(inquiry);
        if (Objects.equals(supplierTenantId, inquiryTenantId)
                && Objects.equals(supplierEnterpriseId, inquiry.getEnterpriseId())) {
            throw invalid("不能对本企业发布的询价单报价");
        }
        if (crossTenantAuthorized) {
            InquiryCrossTenantMapper.OpportunityRef current = crossTenantMapper.selectVisibleOpportunity(
                    supplierTenantId, supplierEnterpriseId, request.getShopId(), inquiry.getId());
            if (current == null || !Objects.equals(current.getInquiryTenantId(), inquiryTenantId)) {
                throw invalid("询价机会已变化，当前店铺不能参与");
            }
        } else {
            lockAndValidateSupplierShop(supplierEnterpriseId, request.getShopId());
            if (!canShopView(inquiry, request.getShopId())) {
                throw invalid("当前店铺未获邀请，不能参与该询价");
            }
        }
        Long foreignHistory = quoteMapper.selectCount(new LambdaQueryWrapperX<InquiryQuoteDO>()
                .apply("COALESCE(supplier_tenant_id, tenant_id) = {0}", supplierTenantId)
                .eq(InquiryQuoteDO::getShopId, request.getShopId())
                .ne(InquiryQuoteDO::getEnterpriseId, supplierEnterpriseId));
        if (foreignHistory != null && foreignHistory > 0) {
            throw invalid("店铺存在原企业询价报价历史，不能变更企业后继续报价");
        }

        InquiryQuoteDO previousByKey = quoteMapper.selectOne(new LambdaQueryWrapperX<InquiryQuoteDO>()
                .apply("COALESCE(supplier_tenant_id, tenant_id) = {0}", supplierTenantId)
                .eq(InquiryQuoteDO::getShopId, request.getShopId())
                .eq(InquiryQuoteDO::getRequestKey, request.getRequestKey()));
        if (previousByKey != null) {
            requireSupplierQuoteEnterprise(supplierTenantId, supplierEnterpriseId, previousByKey, false);
            if (!Objects.equals(previousByKey.getInquiryId(), request.getInquiryId())) {
                throw invalid("该报价幂等键已用于其他询价单");
            }
            return previousByKey.getId();
        }

        InquiryQuoteDO quote = quoteMapper.selectOne(new LambdaQueryWrapperX<InquiryQuoteDO>()
                .apply("COALESCE(supplier_tenant_id, tenant_id) = {0}", supplierTenantId)
                .eq(InquiryQuoteDO::getInquiryId, request.getInquiryId())
                .eq(InquiryQuoteDO::getShopId, request.getShopId()).last("FOR UPDATE"));
        boolean creating = quote == null;
        if (creating) {
            if (request.getId() != null || !Long.valueOf(0L).equals(request.getVersion())) {
                throw invalid("新报价版本必须为 0");
            }
            quote = new InquiryQuoteDO();
            quote.setTenantId(inquiryTenantId);
            quote.setInquiryId(request.getInquiryId());
            quote.setShopId(request.getShopId());
            quote.setEnterpriseId(supplierEnterpriseId);
            quote.setSupplierTenantId(supplierTenantId);
            quote.setAdminUserId(adminUserId);
            quote.setVersion(1L);
        } else {
            requireSupplierQuoteEnterprise(supplierTenantId, supplierEnterpriseId, quote, true);
            if (request.getId() != null && !Objects.equals(request.getId(), quote.getId())) {
                throw invalid("报价编号与询价单不匹配");
            }
            if (!Set.of(QUOTE_DRAFT, QUOTE_REJECTED, QUOTE_WITHDRAWN).contains(quote.getStatus())) {
                throw invalid("仅草稿、已终结或已撤回报价可以重新保存");
            }
            expectVersion(quote, request.getVersion());
            quote.setVersion(Math.addExact(quote.getVersion(), 1L));
            if (adminUserId != null && quote.getAdminUserId() == null) quote.setAdminUserId(adminUserId);
        }

        if (request.getFreightPrice() == null || request.getFreightPrice() < 0) {
            throw invalid("预计运费不能小于 0");
        }
        validateQuoteTerms(request.getDeliveryDate(), request.getValidUntil());
        ValidatedQuote validated = prevalidated == null
                ? validateQuoteItems(supplierTenantId, inquiryTenantId, inquiry.getId(), request.getShopId(),
                inquiryItems(inquiry.getId()), request.getItems())
                : prevalidated;
        long totalPrice = Math.addExact(validated.totalPrice(), request.getFreightPrice().longValue());
        if (totalPrice > Integer.MAX_VALUE) throw invalid("报价总额超过商城订单金额上限");
        quote.setRequestKey(request.getRequestKey());
        quote.setStatus(QUOTE_DRAFT);
        quote.setGoodsPrice(validated.totalPrice());
        quote.setFreightPrice(request.getFreightPrice());
        quote.setTotalPrice(totalPrice);
        quote.setDeliveryDate(request.getDeliveryDate());
        quote.setValidUntil(request.getValidUntil());
        quote.setRemark(trimToNull(request.getRemark()));
        quote.setSubmittedAt(null); quote.setWithdrawnAt(null); quote.setAcceptedAt(null);
        if (creating) quoteMapper.insert(quote); else quoteMapper.updateById(quote);
        quoteItemMapper.deleteByQuoteIdPhysically(quote.getId());
        for (ValidatedQuoteItem validatedItem : validated.items()) {
            InquiryQuoteItemDO item = new InquiryQuoteItemDO();
            item.setQuoteId(quote.getId()); item.setInquiryItemId(validatedItem.inquiryItemId());
            item.setSkuId(validatedItem.skuId()); item.setProductName(validatedItem.productName());
            item.setSkuName(validatedItem.skuName()); item.setSkuSpecification(validatedItem.skuSpecification());
            item.setSpuContentVersion(validatedItem.spuContentVersion()); item.setCount(validatedItem.count());
            item.setUnitPrice(validatedItem.unitPrice()); item.setTotalPrice(validatedItem.totalPrice());
            quoteItemMapper.insert(item);
        }
        if (adminUserId != null) {
            auditSupplier(supplierTenantId, supplierEnterpriseId, quote.getShopId(), adminUserId,
                    creating ? "INQUIRY_QUOTE_CREATE" : "INQUIRY_QUOTE_UPDATE", "INQUIRY_QUOTE",
                    quote.getId(), "inquiryId=" + inquiry.getId() + ",version=" + quote.getVersion());
        }
        return quote.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void submitQuote(Long memberId, Long quoteId, Long version) {
        requireWritableTenant();
        InquiryQuoteDO initial = requireQuote(quoteId);
        EnterpriseApplicationDO supplier = membership.requireSupplier(memberId);
        membership.requireShop(memberId, initial.getShopId());
        submitSupplierQuote(TenantContextHolder.getRequiredTenantId(), supplier.getEnterpriseId(), null, quoteId, version);
    }

    @Transactional(rollbackFor = Exception.class)
    public void adminSubmitQuote(Long adminUserId, Long quoteId, Long version) {
        requireWritableTenant();
        AdminQuoteRef access = requireAdminQuoteRef(adminUserId, quoteId);
        inTenant(access.inquiryTenantId(), () -> submitSupplierQuote(access.supplierTenantId(),
                access.shop().getEnterpriseId(), adminUserId, quoteId, version));
    }

    private void submitSupplierQuote(Long supplierTenantId, Long supplierEnterpriseId,
                                     Long adminUserId, Long quoteId, Long version) {
        InquiryQuoteDO initial = requireQuote(quoteId);
        InquiryDO inquiry = lockInquiry(initial.getInquiryId());
        InquiryQuoteDO quote = lockQuote(quoteId);
        requireSupplierQuoteEnterprise(supplierTenantId, supplierEnterpriseId, quote, true);
        if (Objects.equals(quote.getStatus(), QUOTE_SUBMITTED)) return;
        if (!Objects.equals(quote.getStatus(), QUOTE_DRAFT)) throw invalid("仅报价草稿可以提交");
        expectVersion(quote, version);
        requireOpenForQuote(inquiry);
        List<InquiryQuoteItemDO> items = quoteItems(quoteId);
        if (items.isEmpty()) throw invalid("报价明细不能为空");
        validateQuoteTerms(quote.getDeliveryDate(), quote.getValidUntil());
        validateStoredQuoteItems(quote, inquiry, items, true);
        quote.setStatus(QUOTE_SUBMITTED);
        quote.setSubmittedAt(LocalDateTime.now());
        quote.setVersion(Math.addExact(quote.getVersion(), 1L));
        if (adminUserId != null && quote.getAdminUserId() == null) quote.setAdminUserId(adminUserId);
        quoteMapper.updateById(quote);
        if (adminUserId != null) {
            auditSupplier(supplierTenantId, supplierEnterpriseId, quote.getShopId(), adminUserId,
                    "INQUIRY_QUOTE_SUBMIT", "INQUIRY_QUOTE", quote.getId(), "version=" + quote.getVersion());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void withdrawQuote(Long memberId, Long quoteId, Long version) {
        requireWritableTenant();
        InquiryQuoteDO initial = requireQuote(quoteId);
        EnterpriseApplicationDO supplier = membership.requireSupplier(memberId);
        membership.requireShop(memberId, initial.getShopId());
        withdrawSupplierQuote(TenantContextHolder.getRequiredTenantId(), supplier.getEnterpriseId(), null,
                quoteId, version);
    }

    @Transactional(rollbackFor = Exception.class)
    public void adminWithdrawQuote(Long adminUserId, Long quoteId, Long version) {
        requireWritableTenant();
        AdminQuoteRef access = requireAdminQuoteRef(adminUserId, quoteId);
        inTenant(access.inquiryTenantId(), () -> withdrawSupplierQuote(access.supplierTenantId(),
                access.shop().getEnterpriseId(), adminUserId, quoteId, version));
    }

    private void withdrawSupplierQuote(Long supplierTenantId, Long supplierEnterpriseId,
                                       Long adminUserId, Long quoteId, Long version) {
        InquiryQuoteDO initial = requireQuote(quoteId);
        lockInquiry(initial.getInquiryId());
        InquiryQuoteDO quote = lockQuote(quoteId);
        requireSupplierQuoteEnterprise(supplierTenantId, supplierEnterpriseId, quote, true);
        if (Objects.equals(quote.getStatus(), QUOTE_WITHDRAWN)) return;
        if (!Objects.equals(quote.getStatus(), QUOTE_SUBMITTED)) throw invalid("仅已提交且未采纳的报价可以撤回");
        expectVersion(quote, version);
        quote.setStatus(QUOTE_WITHDRAWN);
        quote.setWithdrawnAt(LocalDateTime.now());
        quote.setVersion(Math.addExact(quote.getVersion(), 1L));
        if (adminUserId != null && quote.getAdminUserId() == null) quote.setAdminUserId(adminUserId);
        quoteMapper.updateById(quote);
        if (adminUserId != null) {
            auditSupplier(supplierTenantId, supplierEnterpriseId, quote.getShopId(), adminUserId,
                    "INQUIRY_QUOTE_WITHDRAW", "INQUIRY_QUOTE", quote.getId(), "version=" + quote.getVersion());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void acceptQuote(Long memberId, Long quoteId, Long version) {
        requireWritableTenant();
        EnterpriseApplicationDO buyer = membership.requireBuyer(memberId);
        InquiryQuoteDO initial = requireQuote(quoteId);
        InquiryDO inquiry = lockInquiry(initial.getInquiryId());
        requireBuyerEnterprise(buyer, inquiry);
        InquiryQuoteDO quote = lockQuote(quoteId);
        if (isCrossTenantQuote(quote)) {
            throw invalid("跨租户报价已可查看和沟通，成交拆单尚未开放，当前不能采纳");
        }
        requireActiveSupplier(quote, true);
        if (Objects.equals(inquiry.getStatus(), 4) && Objects.equals(inquiry.getSelectedQuoteId(), quoteId)
                && Objects.equals(quote.getStatus(), QUOTE_ACCEPTED)) return;
        if (Objects.equals(inquiry.getStatus(), 4)) throw invalid("该询价单已有采纳报价");
        if (!Objects.equals(inquiry.getStatus(), 1)) throw invalid("当前询价单不能采纳报价");
        if (!Objects.equals(quote.getStatus(), QUOTE_SUBMITTED)) throw invalid("仅已提交报价可以采纳");
        if (quote.getValidUntil() == null || quote.getValidUntil() <= System.currentTimeMillis()) {
            throw invalid("报价已过有效期");
        }
        if (quote.getDeliveryDate() == null || quote.getDeliveryDate() <= System.currentTimeMillis()) {
            throw invalid("预计送达日期已失效");
        }
        expectVersion(quote, version);
        List<InquiryQuoteItemDO> items = quoteItems(quoteId);
        if (items.isEmpty()) throw invalid("报价明细不能为空");
        validateStoredQuoteItems(quote, inquiry, items, false);

        LocalDateTime now = LocalDateTime.now();
        quote.setStatus(QUOTE_ACCEPTED); quote.setAcceptedAt(now);
        quote.setVersion(Math.addExact(quote.getVersion(), 1L));
        quoteMapper.updateById(quote);
        quoteMapper.update(null, new UpdateWrapper<InquiryQuoteDO>()
                .eq("inquiry_id", inquiry.getId())
                .ne("id", quoteId)
                .eq("status", QUOTE_SUBMITTED)
                .set("status", QUOTE_REJECTED)
                .setSql("version = version + 1"));
        inquiry.setStatus(4); inquiry.setSelectedQuoteId(quoteId);
        inquiryMapper.updateById(inquiry);
        operationAuditService.success(inquiry.getEnterpriseId(), Objects.requireNonNullElse(quote.getShopId(), 0L),
                "MEMBER", memberId, "INQUIRY_QUOTE_ACCEPT", "INQUIRY_QUOTE", quote.getId(),
                "inquiryId=" + inquiry.getId() + ",version=" + quote.getVersion());
    }

    public InquiryQuoteRespVO mine(Long memberId, Long inquiryId, Long shopId) {
        EnterpriseApplicationDO supplier = membership.requireSupplierHistory(memberId);
        membership.requireSupplierShopHistory(memberId, supplier.getEnterpriseId(), shopId);
        InquiryQuoteDO quote = quoteMapper.selectOne(new LambdaQueryWrapperX<InquiryQuoteDO>()
                .eq(InquiryQuoteDO::getInquiryId, inquiryId).eq(InquiryQuoteDO::getShopId, shopId)
                .eq(InquiryQuoteDO::getEnterpriseId, supplier.getEnterpriseId()));
        if (quote != null) requireSupplierQuoteHistory(TenantContextHolder.getTenantId(),
                supplier.getEnterpriseId(), shopId, quote);
        return quote == null ? null : quoteResponse(memberId, quote);
    }

    public PageResult<InquiryQuoteRespVO> page(Long memberId, Long inquiryId, PageParam page) {
        EnterpriseApplicationDO buyer = membership.requireBuyerHistory(memberId);
        Collection<Long> inquiryIds;
        if (inquiryId != null) {
            InquiryDO inquiry = inquiryMapper.selectById(inquiryId);
            if (inquiry == null || !Objects.equals(inquiry.getEnterpriseId(), buyer.getEnterpriseId())) {
                throw invalid("询价单不存在或无权访问");
            }
            requireBuyerEnterprise(buyer, inquiry);
            inquiryIds = List.of(inquiryId);
        } else {
            inquiryIds = inquiryMapper.selectList(new LambdaQueryWrapperX<InquiryDO>()
                            .eq(InquiryDO::getEnterpriseId, buyer.getEnterpriseId())).stream()
                    .map(InquiryDO::getId).toList();
            if (inquiryIds.isEmpty()) return PageResult.empty();
        }
        PageResult<InquiryQuoteDO> rows = quoteMapper.selectPage(page, new LambdaQueryWrapperX<InquiryQuoteDO>()
                .in(InquiryQuoteDO::getInquiryId, inquiryIds)
                .in(InquiryQuoteDO::getStatus, BUYER_VISIBLE_QUOTE_STATUSES)
                .orderByDesc(InquiryQuoteDO::getId));
        return new PageResult<>(rows.getList().stream().map(row -> quoteResponse(row, true, false)).toList(),
                rows.getTotal());
    }

    /** 销售中心按已授权店铺查看自己的报价，绝不接受企业编号或会员编号作为查询范围。 */
    public PageResult<InquiryQuoteRespVO> supplierPage(Long memberId, Long shopId, Integer status, PageParam page) {
        EnterpriseApplicationDO supplier = membership.requireSupplierHistory(memberId);
        membership.requireSupplierShopHistory(memberId, supplier.getEnterpriseId(), shopId);
        if (status != null && !Set.of(QUOTE_DRAFT, QUOTE_SUBMITTED, QUOTE_ACCEPTED,
                QUOTE_REJECTED, QUOTE_WITHDRAWN).contains(status)) {
            throw invalid("报价状态不正确");
        }
        PageResult<InquiryQuoteDO> rows = quoteMapper.selectPage(page, new LambdaQueryWrapperX<InquiryQuoteDO>()
                .eq(InquiryQuoteDO::getShopId, shopId)
                .eq(InquiryQuoteDO::getEnterpriseId, supplier.getEnterpriseId())
                .eqIfPresent(InquiryQuoteDO::getStatus, status)
                .orderByDesc(InquiryQuoteDO::getId));
        if (rows.getList().stream().anyMatch(row -> !Objects.equals(row.getShopId(), shopId)
                || !Objects.equals(row.getEnterpriseId(), supplier.getEnterpriseId()))) {
            throw invalid("报价企业归属已变化，不能访问或操作该报价");
        }
        return new PageResult<>(rows.getList().stream().map(row -> quoteResponse(memberId, row)).toList(),
                rows.getTotal());
    }

    public InquiryQuoteRespVO adminMine(Long adminUserId, Long inquiryId, Long shopId) {
        ProductShopDO shop = adminScope.requireMerchantShopHistory(adminUserId, shopId);
        Long supplierTenantId = TenantContextHolder.getRequiredTenantId();
        InquiryCrossTenantMapper.QuoteRef ref = crossTenantMapper.selectHistoricalSupplierQuoteByInquiry(
                supplierTenantId, shop.getEnterpriseId(), shopId, inquiryId);
        if (ref == null) return null;
        return inTenant(requireBusinessTenant(ref.getInquiryTenantId()), () -> {
            InquiryQuoteDO quote = requireQuote(ref.getQuoteId());
            requireSupplierQuoteHistory(supplierTenantId, shop.getEnterpriseId(), shopId, quote);
            return quoteResponse(quote, false, true);
        });
    }

    public PageResult<InquiryQuoteRespVO> adminSupplierPage(Long adminUserId, Long shopId,
                                                            Integer status, PageParam page) {
        ProductShopDO shop = adminScope.requireMerchantShopHistory(adminUserId, shopId);
        if (status != null && !Set.of(QUOTE_DRAFT, QUOTE_SUBMITTED, QUOTE_ACCEPTED,
                QUOTE_REJECTED, QUOTE_WITHDRAWN).contains(status)) {
            throw invalid("报价状态不正确");
        }
        Long supplierTenantId = TenantContextHolder.getRequiredTenantId();
        long offset = Math.multiplyExact((long) page.getPageNo() - 1L, page.getPageSize());
        List<InquiryCrossTenantMapper.QuoteRef> refs = crossTenantMapper.selectHistoricalSupplierQuotes(
                supplierTenantId, shop.getEnterpriseId(), shopId, status, offset, page.getPageSize());
        List<InquiryQuoteRespVO> rows = refs == null ? List.of() : refs.stream().map(ref ->
                inTenant(requireBusinessTenant(ref.getInquiryTenantId()), () -> {
                    InquiryQuoteDO quote = requireQuote(ref.getQuoteId());
                    requireSupplierQuoteHistory(supplierTenantId, shop.getEnterpriseId(), shopId, quote);
                    return quoteResponse(quote, false, true);
                })).toList();
        long total = crossTenantMapper.countHistoricalSupplierQuotes(
                supplierTenantId, shop.getEnterpriseId(), shopId, status);
        return new PageResult<>(rows, total);
    }

    public InquiryQuoteRespVO adminGet(Long adminUserId, Long quoteId) {
        AdminQuoteRef ref = requireAdminHistoricalQuoteRef(adminUserId, quoteId);
        return inTenant(ref.inquiryTenantId(), () -> {
            InquiryQuoteDO quote = requireQuote(quoteId);
            requireSupplierQuoteHistory(ref.supplierTenantId(), ref.shop().getEnterpriseId(),
                    ref.shop().getId(), quote);
            return quoteResponse(quote, false, true);
        });
    }

    public InquiryQuoteRespVO get(Long memberId, Long quoteId) {
        QuoteAccess access = requireAccess(memberId, quoteId, false);
        if ("SUPPLIER".equals(access.role())) return legacySupplierQuoteResponse(access.quote());
        return quoteResponse(access.quote(), true, false);
    }

    public List<InquiryQuoteMessageRespVO> messages(Long memberId, Long quoteId, Long afterId, Integer pageSize) {
        requireAccess(memberId, quoteId, false);
        int size = pageSize == null ? 50 : pageSize;
        if (size < 1 || size > 200) throw invalid("消息条数须为 1 至 200");
        List<InquiryQuoteMessageDO> rows = messageMapper.selectList(new LambdaQueryWrapperX<InquiryQuoteMessageDO>()
                .eq(InquiryQuoteMessageDO::getQuoteId, quoteId)
                .gt(InquiryQuoteMessageDO::getId, Objects.requireNonNullElse(afterId, 0L))
                .orderByAsc(InquiryQuoteMessageDO::getId).last("LIMIT " + size));
        return rows.stream().map(this::messageResponse).toList();
    }

    public List<InquiryQuoteMessageRespVO> adminMessages(Long adminUserId, Long quoteId,
                                                         Long afterId, Integer pageSize) {
        AdminQuoteRef ref = requireAdminHistoricalQuoteRef(adminUserId, quoteId);
        return inTenant(ref.inquiryTenantId(), () -> {
            InquiryQuoteDO quote = requireQuote(quoteId);
            requireSupplierQuoteHistory(ref.supplierTenantId(), ref.shop().getEnterpriseId(),
                    ref.shop().getId(), quote);
            return quoteMessages(quoteId, afterId, pageSize);
        });
    }

    private List<InquiryQuoteMessageRespVO> quoteMessages(Long quoteId, Long afterId, Integer pageSize) {
        int size = pageSize == null ? 50 : pageSize;
        if (size < 1 || size > 200) throw invalid("消息条数须为 1 至 200");
        List<InquiryQuoteMessageDO> rows = messageMapper.selectList(new LambdaQueryWrapperX<InquiryQuoteMessageDO>()
                .eq(InquiryQuoteMessageDO::getQuoteId, quoteId)
                .gt(InquiryQuoteMessageDO::getId, Objects.requireNonNullElse(afterId, 0L))
                .orderByAsc(InquiryQuoteMessageDO::getId).last("LIMIT " + size));
        return rows.stream().map(this::messageResponse).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long sendMessage(Long memberId, InquiryQuoteMessageSendReqVO request) {
        requireWritableTenant();
        InquiryQuoteDO initial = requireQuote(request.getQuoteId());
        lockInquiry(initial.getInquiryId());
        InquiryQuoteDO quote = lockQuote(request.getQuoteId());
        QuoteAccess access = requireAccess(memberId, quote, false);
        if ("SUPPLIER".equals(access.role())) {
            EnterpriseApplicationDO supplier = membership.requireSupplier(memberId);
            membership.requireShop(memberId, quote.getShopId());
            requireSupplierQuoteEnterprise(TenantContextHolder.getTenantId(), supplier.getEnterpriseId(),
                    quote, false);
            if (!legacyMemberSalesProperties.isEnabled()) {
                throw invalid("旧会员端供应经营写入口已关闭，请使用后台销售中心处理报价和沟通");
            }
        } else {
            EnterpriseApplicationDO buyer = membership.requireBuyer(memberId);
            requireBuyerEnterprise(buyer, access.inquiry());
            requireActiveSupplier(quote, false);
        }
        if (!inquiryAllowsMessages(access.inquiry())
                || !Set.of(QUOTE_SUBMITTED, QUOTE_ACCEPTED).contains(quote.getStatus())) {
            throw invalid("仅已提交或已采纳报价可以发送消息");
        }
        String content = request.getContent().trim();
        InquiryQuoteMessageDO previous = messageMapper.selectOne(new LambdaQueryWrapperX<InquiryQuoteMessageDO>()
                .eq(InquiryQuoteMessageDO::getQuoteId, quote.getId())
                .eq(InquiryQuoteMessageDO::getSenderMemberId, memberId)
                .eq(InquiryQuoteMessageDO::getClientMessageId, request.getClientMessageId()));
        if (previous != null) {
            if (!Objects.equals(previous.getContent(), content)) throw invalid("消息幂等键已用于其他内容");
            return previous.getId();
        }
        InquiryQuoteMessageDO message = new InquiryQuoteMessageDO();
        message.setQuoteId(quote.getId()); message.setSenderMemberId(memberId);
        message.setSenderRole(access.role()); message.setClientMessageId(request.getClientMessageId());
        message.setContent(content); messageMapper.insert(message);
        return message.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long adminSendMessage(Long adminUserId, InquiryQuoteMessageSendReqVO request) {
        requireWritableTenant();
        AdminQuoteRef ref = requireAdminQuoteRef(adminUserId, request.getQuoteId());
        return inTenant(ref.inquiryTenantId(), () -> {
            InquiryQuoteDO initial = requireQuote(request.getQuoteId());
            lockInquiry(initial.getInquiryId());
            InquiryQuoteDO quote = lockQuote(request.getQuoteId());
            AdminQuoteAccess access = requireAdminAccess(adminUserId, quote, false, ref.shop(),
                    ref.supplierTenantId());
            if (!inquiryAllowsMessages(access.inquiry())
                    || !Set.of(QUOTE_SUBMITTED, QUOTE_ACCEPTED).contains(quote.getStatus())) {
                throw invalid("仅已提交或已采纳报价可以发送消息");
            }
            String content = request.getContent().trim();
            InquiryQuoteMessageDO previous = messageMapper.selectOne(new LambdaQueryWrapperX<InquiryQuoteMessageDO>()
                    .eq(InquiryQuoteMessageDO::getQuoteId, quote.getId())
                    .eq(InquiryQuoteMessageDO::getSenderAdminUserId, adminUserId)
                    .eq(InquiryQuoteMessageDO::getClientMessageId, request.getClientMessageId()));
            if (previous != null) {
                if (!Objects.equals(previous.getContent(), content)) throw invalid("消息幂等键已用于其他内容");
                return previous.getId();
            }
            InquiryQuoteMessageDO message = new InquiryQuoteMessageDO();
            message.setQuoteId(quote.getId()); message.setSenderAdminUserId(adminUserId);
            message.setSenderRole("SUPPLIER"); message.setClientMessageId(request.getClientMessageId());
            message.setContent(content); messageMapper.insert(message);
            auditSupplier(ref.supplierTenantId(), access.shop().getEnterpriseId(), access.shop().getId(),
                    adminUserId, "INQUIRY_QUOTE_MESSAGE_SEND", "INQUIRY_QUOTE_MESSAGE", message.getId(),
                    "quoteId=" + quote.getId());
            return message.getId();
        });
    }

    @Transactional(readOnly = true)
    public InquiryAcceptedQuoteRespDTO getAcceptedQuote(Long memberId, Long quoteId) {
        EnterpriseApplicationDO buyer = membership.requireBuyer(memberId);
        InquiryQuoteDO quote = requireQuote(quoteId);
        InquiryDO inquiry = inquiryMapper.selectById(quote.getInquiryId());
        if (inquiry == null || !Objects.equals(inquiry.getEnterpriseId(), buyer.getEnterpriseId())) {
            throw invalid("成交报价不存在或不属于当前采购方");
        }
        requireBuyerEnterprise(buyer, inquiry);
        if (!Objects.equals(inquiry.getStatus(), 4) || !Objects.equals(inquiry.getSelectedQuoteId(), quoteId)
                || !Objects.equals(quote.getStatus(), QUOTE_ACCEPTED)) {
            throw invalid("报价尚未采纳");
        }
        // 当前订单服务仍按采购方租户创建单租户订单，无法安全承载供应方真实租户。
        // 即使脏数据或人工 SQL 已把跨租户报价标成采纳，也绝不能把它放行到订单链路。
        if (isCrossTenantQuote(quote)) {
            throw invalid("跨租户报价成交拆单尚未开放，当前不能生成订单");
        }
        requireActiveSupplier(quote, false);
        List<InquiryQuoteItemDO> items = quoteItems(quoteId);
        if (items.isEmpty()) throw invalid("成交报价明细已失效");
        validateStoredQuoteItems(quote, inquiry, items, false);
        InquiryAcceptedQuoteRespDTO result = new InquiryAcceptedQuoteRespDTO();
        result.setQuoteId(quoteId); result.setInquiryId(inquiry.getId());
        result.setBuyerEnterpriseId(inquiry.getEnterpriseId());
        result.setBuyerMemberId(memberId); result.setShopId(quote.getShopId());
        result.setVersion(quote.getVersion()); result.setTitle(inquiry.getTitle());
        result.setItems(items.stream().map(row -> {
            InquiryAcceptedQuoteRespDTO.Item item = new InquiryAcceptedQuoteRespDTO.Item();
            item.setInquiryItemId(row.getInquiryItemId()); item.setSkuId(row.getSkuId());
            item.setCount(row.getCount()); item.setUnitPrice(row.getUnitPrice()); return item;
        }).toList());
        return result;
    }

    private InquiryQuoteRespVO quoteResponse(Long memberId, InquiryQuoteDO quote) {
        InquiryDO inquiry = inquiryMapper.selectById(quote.getInquiryId());
        boolean buyer = inquiry != null && Objects.equals(inquiry.getMemberId(), memberId);
        return buyer ? quoteResponse(quote, true, false) : legacySupplierQuoteResponse(quote);
    }

    /**
     * 旧 member 供应侧只保留历史读取。兼容开关关闭时，响应能力位也必须失败关闭，
     * 避免旧页面继续向用户展示实际不可执行的经营按钮；后台销售中心不经过此方法。
     */
    private InquiryQuoteRespVO legacySupplierQuoteResponse(InquiryQuoteDO quote) {
        InquiryQuoteRespVO result = quoteResponse(quote, false, true);
        if (!legacyMemberSalesProperties.isEnabled()) {
            result.setCanQuote(false);
            result.setCanEdit(false);
            result.setCanSubmit(false);
            result.setCanWithdraw(false);
            result.setCanMessage(false);
            result.setLegacySalesReadOnly(true);
            result.setSalesWriteNotice("旧会员端仅保留历史报价读取，请使用后台管理的销售中心处理报价和沟通");
        } else {
            result.setLegacySalesReadOnly(false);
            result.setSalesWriteNotice("旧会员端销售经营兼容窗口已临时开启，请尽快迁移至后台销售中心");
        }
        return result;
    }

    private InquiryQuoteRespVO quoteResponse(InquiryQuoteDO quote, boolean buyer, boolean supplier) {
        InquiryQuoteRespVO result = new InquiryQuoteRespVO();
        result.setLegacySalesReadOnly(false);
        result.setId(quote.getId()); result.setInquiryId(quote.getInquiryId()); result.setShopId(quote.getShopId());
        Long supplierTenantId = supplierTenantId(quote);
        result.setSupplierTenantId(supplierTenantId);
        ProductShopDO shop = supplierTenantId == null ? shopMapper.selectById(quote.getShopId())
                : inTenant(supplierTenantId, () -> shopMapper.selectById(quote.getShopId()));
        result.setShopName(shop == null ? "" : shop.getName());
        result.setStatus(quote.getStatus()); result.setVersion(quote.getVersion());
        result.setGoodsPrice(quote.getGoodsPrice()); result.setFreightPrice(quote.getFreightPrice());
        result.setTotalPrice(quote.getTotalPrice()); result.setDeliveryDate(quote.getDeliveryDate());
        result.setValidUntil(quote.getValidUntil()); result.setRemark(quote.getRemark());
        result.setCreateTime(quote.getCreateTime()); result.setUpdateTime(quote.getUpdateTime());
        result.setSubmittedAt(quote.getSubmittedAt()); result.setAcceptedAt(quote.getAcceptedAt());
        InquiryDO inquiry = inquiryMapper.selectById(quote.getInquiryId());
        if (inquiry != null) {
            result.setInquiryNo(inquiry.getNo());
            result.setInquiryTitle(inquiry.getTitle());
        }
        boolean supplierActive = isActiveSupplier(quote, shop);
        boolean active = inquiry != null && Objects.equals(inquiry.getStatus(), 1)
                && inquiry.getDeadline() != null && inquiry.getDeadline() > System.currentTimeMillis();
        boolean editable = supplier && supplierActive && active
                && Set.of(QUOTE_DRAFT, QUOTE_REJECTED, QUOTE_WITHDRAWN).contains(quote.getStatus());
        boolean termsActive = quote.getDeliveryDate() != null && quote.getDeliveryDate() > System.currentTimeMillis()
                && quote.getValidUntil() != null && quote.getValidUntil() > System.currentTimeMillis();
        result.setCanQuote(editable);
        result.setCanEdit(editable);
        result.setCanSubmit(supplier && supplierActive && active
                && Objects.equals(quote.getStatus(), QUOTE_DRAFT) && termsActive);
        result.setCanWithdraw(supplier && supplierActive && Objects.equals(quote.getStatus(), QUOTE_SUBMITTED));
        boolean messageWritable = Set.of(QUOTE_SUBMITTED, QUOTE_ACCEPTED).contains(quote.getStatus());
        result.setCanMessage(messageWritable && inquiryAllowsMessages(inquiry)
                && supplierActive && (buyer || supplier));
        result.setCanAccept(buyer && !isCrossTenantQuote(quote) && supplierActive
                && Objects.equals(inquiry.getStatus(), 1)
                && Objects.equals(quote.getStatus(), QUOTE_SUBMITTED)
                && quote.getDeliveryDate() != null && quote.getDeliveryDate() > System.currentTimeMillis()
                && quote.getValidUntil() != null && quote.getValidUntil() > System.currentTimeMillis());
        result.setCanConvert(buyer && !isCrossTenantQuote(quote) && supplierActive
                && Objects.equals(inquiry.getStatus(), 4)
                && Objects.equals(inquiry.getSelectedQuoteId(), quote.getId())
                && Objects.equals(quote.getStatus(), QUOTE_ACCEPTED));
        Map<Long, InquiryItemDO> inquiryItems = inquiryItems(quote.getInquiryId()).stream()
                .collect(Collectors.toMap(InquiryItemDO::getId, Function.identity()));
        List<InquiryQuoteItemDO> quoteItems = quoteItems(quote.getId());
        result.setItems(quoteItems.stream().map(row -> {
            InquiryQuoteRespVO.Item item = new InquiryQuoteRespVO.Item();
            item.setInquiryItemId(row.getInquiryItemId()); item.setSkuId(row.getSkuId());
            InquiryItemDO inquiryItem = inquiryItems.get(row.getInquiryItemId());
            String productName = Objects.toString(row.getProductName(), "商品快照缺失");
            String specification = Objects.toString(row.getSkuSpecification(), "");
            item.setProductName(productName);
            item.setSkuSpecification(specification);
            item.setSkuName(Objects.toString(row.getSkuName(), productName));
            item.setName(productName);
            item.setSpecification(specification);
            if (inquiryItem != null) {
                item.setQuantity(inquiryItem.getQuantity()); item.setUnit(inquiryItem.getUnit());
            }
            item.setCount(row.getCount()); item.setUnitPrice(row.getUnitPrice()); item.setTotalPrice(row.getTotalPrice());
            return item;
        }).toList());
        return result;
    }

    private static String skuSpecification(ProductSkuDO sku) {
        if (sku == null || sku.getProperties() == null || sku.getProperties().isEmpty()) return "默认规格";
        String value = sku.getProperties().stream().map(property -> {
            String name = Objects.toString(property.getPropertyName(), "").trim();
            String option = Objects.toString(property.getValueName(), "").trim();
            if (name.isEmpty()) return option;
            if (option.isEmpty()) return name;
            return name + "：" + option;
        }).filter(text -> !text.isBlank()).collect(Collectors.joining("；"));
        return value.isBlank() ? "默认规格" : value;
    }

    private InquiryQuoteMessageRespVO messageResponse(InquiryQuoteMessageDO row) {
        InquiryQuoteMessageRespVO result = new InquiryQuoteMessageRespVO();
        result.setId(row.getId()); result.setQuoteId(row.getQuoteId()); result.setSenderRole(row.getSenderRole());
        result.setClientMessageId(row.getClientMessageId()); result.setContent(row.getContent());
        result.setCreateTime(row.getCreateTime()); return result;
    }

    private QuoteAccess requireAccess(Long memberId, Long quoteId, boolean lock) {
        InquiryQuoteDO quote = lock ? lockQuote(quoteId) : requireQuote(quoteId);
        return requireAccess(memberId, quote, lock);
    }

    private QuoteAccess requireAccess(Long memberId, InquiryQuoteDO quote, boolean ignored) {
        InquiryDO inquiry = inquiryMapper.selectById(quote.getInquiryId());
        if (inquiry == null) throw invalid("报价不存在或无权访问");
        EnterpriseApplicationDO application = membership.mine(memberId);
        if (application != null && "buyer".equals(application.getIdentity())) {
            EnterpriseApplicationDO buyer = membership.requireBuyerHistory(memberId);
            requireBuyerEnterprise(buyer, inquiry);
            if (!BUYER_VISIBLE_QUOTE_STATUSES.contains(quote.getStatus())) {
                throw invalid("报价不存在或无权访问");
            }
            return new QuoteAccess(quote, inquiry, "BUYER");
        }
        EnterpriseApplicationDO supplier = membership.requireSupplierShopHistory(memberId,
                quote.getEnterpriseId(), quote.getShopId());
        requireSupplierQuoteHistory(TenantContextHolder.getTenantId(), supplier.getEnterpriseId(),
                quote.getShopId(), quote);
        return new QuoteAccess(quote, inquiry, "SUPPLIER");
    }

    private AdminQuoteAccess requireAdminAccess(Long adminUserId, Long quoteId, boolean lock,
                                                ProductShopDO authorizedShop, Long supplierTenantId) {
        InquiryQuoteDO quote = lock ? lockQuote(quoteId) : requireQuote(quoteId);
        return requireAdminAccess(adminUserId, quote, lock, authorizedShop, supplierTenantId);
    }

    private AdminQuoteAccess requireAdminAccess(Long adminUserId, InquiryQuoteDO quote, boolean lock,
                                                ProductShopDO authorizedShop, Long supplierTenantId) {
        if (authorizedShop == null || !Objects.equals(authorizedShop.getId(), quote.getShopId())) {
            throw invalid("未获得该店铺经营权限");
        }
        requireSupplierQuoteEnterprise(supplierTenantId, authorizedShop.getEnterpriseId(), quote, lock);
        InquiryDO inquiry = inquiryMapper.selectById(quote.getInquiryId());
        if (inquiry == null) throw invalid("报价不存在或无权访问");
        return new AdminQuoteAccess(quote, inquiry, authorizedShop);
    }

    private AdminQuoteRef requireAdminQuoteRef(Long adminUserId, Long quoteId) {
        Long supplierTenantId = requireBusinessTenant(TenantContextHolder.getRequiredTenantId());
        InquiryCrossTenantMapper.QuoteRef ref = crossTenantMapper.selectSupplierQuoteForTenant(
                supplierTenantId, quoteId);
        if (ref == null || !Objects.equals(ref.getSupplierTenantId(), supplierTenantId)) {
            throw invalid("报价不存在或无权访问");
        }
        ProductShopDO shop = adminScope.requireMerchantShop(adminUserId, ref.getShopId());
        if (!Objects.equals(shop.getEnterpriseId(), ref.getEnterpriseId())) {
            throw invalid("报价企业归属已变化，不能访问或操作该报价");
        }
        return new AdminQuoteRef(requireBusinessTenant(ref.getInquiryTenantId()), supplierTenantId, shop);
    }

    /** 历史读取不复用当前经营资格查询；仅校验报价固化归属与调用者仍持有的主体授权。 */
    private AdminQuoteRef requireAdminHistoricalQuoteRef(Long adminUserId, Long quoteId) {
        Long supplierTenantId = requireBusinessTenant(TenantContextHolder.getRequiredTenantId());
        InquiryCrossTenantMapper.QuoteRef ref = crossTenantMapper.selectHistoricalSupplierQuoteForTenant(
                supplierTenantId, quoteId);
        if (ref == null || !Objects.equals(ref.getSupplierTenantId(), supplierTenantId)) {
            throw invalid("报价不存在或无权访问");
        }
        ProductShopDO shop = adminScope.requireMerchantShopHistory(adminUserId, ref.getShopId());
        if (!Objects.equals(shop.getEnterpriseId(), ref.getEnterpriseId())) {
            throw invalid("报价企业归属已变化，不能访问该报价");
        }
        return new AdminQuoteRef(requireBusinessTenant(ref.getInquiryTenantId()), supplierTenantId, shop);
    }

    private static void requireBuyerEnterprise(EnterpriseApplicationDO buyer, InquiryDO inquiry) {
        if (buyer == null || buyer.getEnterpriseId() == null
                || !Objects.equals(buyer.getEnterpriseId(), inquiry.getEnterpriseId())) {
            throw invalid("采购企业归属已变化，不能继续使用该询价成交");
        }
    }

    /** 成交边界持续复核报价企业、店铺及两者关联，不能只信任保存报价时的授权。 */
    private void requireActiveSupplier(InquiryQuoteDO quote, boolean lock) {
        if (isCrossTenantQuote(quote) && !isActiveCrossTenantQuoteScope(quote)) {
            throw invalid("跨租户报价范围已关闭或参与方资质已失效");
        }
        Long supplierTenantId = supplierTenantId(quote);
        Long currentTenantId = TenantContextHolder.getTenantId();
        if (supplierTenantId != null && !Objects.equals(supplierTenantId, currentTenantId)) {
            inTenant(supplierTenantId, () -> requireActiveSupplierInCurrentTenant(quote, lock));
            return;
        }
        requireActiveSupplierInCurrentTenant(quote, lock);
    }

    private void requireActiveSupplierInCurrentTenant(InquiryQuoteDO quote, boolean lock) {
        if (quote.getEnterpriseId() == null || quote.getShopId() == null) {
            throw invalid("报价企业或店铺资质已失效");
        }
        ProductEnterpriseDO enterprise = lock
                ? enterpriseMapper.selectOne(new LambdaQueryWrapperX<ProductEnterpriseDO>()
                        .eq(ProductEnterpriseDO::getId, quote.getEnterpriseId()).last("FOR UPDATE"))
                : enterpriseMapper.selectById(quote.getEnterpriseId());
        ProductShopDO shop = lock
                ? shopMapper.selectOne(new LambdaQueryWrapperX<ProductShopDO>()
                        .eq(ProductShopDO::getId, quote.getShopId()).last("FOR UPDATE"))
                : shopMapper.selectById(quote.getShopId());
        if (enterprise == null || !Objects.equals(enterprise.getAuditStatus(), 1)
                || shop == null || !Objects.equals(shop.getStatus(), 0)
                || !Objects.equals(shop.getEnterpriseId(), quote.getEnterpriseId())) {
            throw invalid("报价企业或店铺资质已失效");
        }
    }

    private boolean isActiveSupplier(InquiryQuoteDO quote, ProductShopDO shop) {
        if (quote.getEnterpriseId() == null || shop == null || !Objects.equals(shop.getStatus(), 0)
                || !Objects.equals(shop.getEnterpriseId(), quote.getEnterpriseId())) {
            return false;
        }
        if (isCrossTenantQuote(quote) && !isActiveCrossTenantQuoteScope(quote)) {
            return false;
        }
        Long supplierTenantId = supplierTenantId(quote);
        Long currentTenantId = TenantContextHolder.getTenantId();
        ProductEnterpriseDO enterprise = supplierTenantId != null
                && !Objects.equals(supplierTenantId, currentTenantId)
                ? inTenant(supplierTenantId, () -> enterpriseMapper.selectById(quote.getEnterpriseId()))
                : enterpriseMapper.selectById(quote.getEnterpriseId());
        return enterprise != null && Objects.equals(enterprise.getAuditStatus(), 1);
    }

    private boolean isActiveCrossTenantQuoteScope(InquiryQuoteDO quote) {
        Long supplierTenantId = supplierTenantId(quote);
        if (supplierTenantId == null || quote.getEnterpriseId() == null || quote.getShopId() == null
                || quote.getId() == null) {
            return false;
        }
        InquiryCrossTenantMapper.QuoteRef ref = crossTenantMapper.selectSupplierQuote(
                supplierTenantId, quote.getEnterpriseId(), quote.getShopId(), quote.getId());
        return ref != null
                && Objects.equals(ref.getQuoteId(), quote.getId())
                && Objects.equals(ref.getInquiryTenantId(), quote.getTenantId())
                && Objects.equals(ref.getSupplierTenantId(), supplierTenantId)
                && Objects.equals(ref.getInquiryId(), quote.getInquiryId())
                && Objects.equals(ref.getEnterpriseId(), quote.getEnterpriseId())
                && Objects.equals(ref.getShopId(), quote.getShopId());
    }

    private void requireSupplierQuoteEnterprise(Long supplierEnterpriseId, InquiryQuoteDO quote, boolean lock) {
        requireSupplierQuoteEnterprise(TenantContextHolder.getTenantId(), supplierEnterpriseId, quote, lock);
    }

    private void requireSupplierQuoteEnterprise(Long supplierTenantId, Long supplierEnterpriseId,
                                                 InquiryQuoteDO quote, boolean lock) {
        requireSupplierQuoteHistory(supplierTenantId, supplierEnterpriseId, quote.getShopId(), quote);
        requireActiveSupplier(quote, lock);
    }

    private void requireSupplierQuoteHistory(Long supplierTenantId, Long supplierEnterpriseId,
                                             Long supplierShopId, InquiryQuoteDO quote) {
        if (supplierEnterpriseId == null || !Objects.equals(supplierEnterpriseId, quote.getEnterpriseId())) {
            throw invalid("报价企业归属已变化，不能访问该报价");
        }
        if (supplierShopId == null || !Objects.equals(supplierShopId, quote.getShopId())) {
            throw invalid("报价店铺归属已变化，不能访问该报价");
        }
        Long actualSupplierTenantId = supplierTenantId(quote);
        if (supplierTenantId != null && actualSupplierTenantId != null
                && !Objects.equals(supplierTenantId, actualSupplierTenantId)) {
            throw invalid("报价供应租户归属已变化，不能访问该报价");
        }
    }

    private ProductShopDO lockAndValidateSupplierShop(Long supplierEnterpriseId, Long shopId) {
        ProductShopDO shop = shopMapper.selectOne(new LambdaQueryWrapperX<ProductShopDO>()
                .eq(ProductShopDO::getId, shopId).last("FOR UPDATE"));
        if (supplierEnterpriseId == null || shop == null
                || !Objects.equals(shop.getStatus(), 0)
                || !Objects.equals(shop.getEnterpriseId(), supplierEnterpriseId)) {
            throw invalid("报价店铺归属已变化或已停用，请刷新后重试");
        }
        return shop;
    }

    private ProductEnterpriseDO requireApprovedInquiryEnterprise(InquiryDO inquiry) {
        ProductEnterpriseDO enterprise = inquiry.getEnterpriseId() == null
                ? null : enterpriseMapper.selectById(inquiry.getEnterpriseId());
        if (enterprise == null || !Objects.equals(enterprise.getAuditStatus(), 1)) {
            throw invalid("采购企业资质已失效，当前询价不能报价");
        }
        return enterprise;
    }

    private InquiryDO lockInquiry(Long inquiryId) {
        InquiryDO inquiry = inquiryMapper.selectOne(new LambdaQueryWrapperX<InquiryDO>()
                .eq(InquiryDO::getId, inquiryId).last("FOR UPDATE"));
        if (inquiry == null) throw invalid("询价单不存在");
        return inquiry;
    }

    private InquiryQuoteDO lockQuote(Long quoteId) {
        InquiryQuoteDO quote = quoteMapper.selectOne(new LambdaQueryWrapperX<InquiryQuoteDO>()
                .eq(InquiryQuoteDO::getId, quoteId).last("FOR UPDATE"));
        if (quote == null) throw invalid("报价不存在");
        return quote;
    }

    private InquiryQuoteDO requireQuote(Long quoteId) {
        InquiryQuoteDO quote = quoteMapper.selectById(quoteId);
        if (quote == null) throw invalid("报价不存在");
        return quote;
    }

    private static void requireOpenForQuote(InquiryDO inquiry) {
        if (!Objects.equals(inquiry.getStatus(), 1) || inquiry.getDeadline() == null
                || inquiry.getDeadline() <= System.currentTimeMillis()) {
            throw invalid("询价单未开放报价或已截止");
        }
    }

    private static boolean inquiryAllowsMessages(InquiryDO inquiry) {
        if (inquiry == null) return false;
        if (Objects.equals(inquiry.getStatus(), 4)) return true;
        return Objects.equals(inquiry.getStatus(), 1) && inquiry.getDeadline() != null
                && inquiry.getDeadline() > System.currentTimeMillis();
    }

    private static void expectVersion(InquiryQuoteDO quote, Long version) {
        if (version == null || !Objects.equals(quote.getVersion(), version)) {
            throw invalid("报价版本已变化，请刷新后重试");
        }
    }

    private ValidatedQuote validateQuoteItems(Long supplierTenantId, Long inquiryTenantId, Long inquiryId,
                                              Long shopId, List<InquiryItemDO> requested,
                                              List<InquiryQuoteSaveReqVO.Item> inputs) {
        if (requested.isEmpty() || inputs == null || inputs.size() != requested.size()) {
            throw invalid("报价必须逐项覆盖询价明细");
        }
        Map<Long, InquiryItemDO> requestedById = requested.stream()
                .collect(Collectors.toMap(InquiryItemDO::getId, Function.identity()));
        if (inputs.stream().map(InquiryQuoteSaveReqVO.Item::getInquiryItemId).distinct().count() != inputs.size()
                || !requestedById.keySet().equals(inputs.stream().map(InquiryQuoteSaveReqVO.Item::getInquiryItemId)
                .collect(Collectors.toSet()))) {
            throw invalid("报价必须逐项且不重复覆盖询价明细");
        }
        if (inputs.stream().map(InquiryQuoteSaveReqVO.Item::getSkuId).distinct().count() != inputs.size()) {
            throw invalid("每项询价明细必须映射不同的商品 SKU");
        }
        long total = 0L;
        List<ValidatedQuoteItem> result = new ArrayList<>(inputs.size());
        for (InquiryQuoteSaveReqVO.Item input : inputs) {
            // 询价数量可以是吨、米等三位小数需求；count 是供应商 SKU 的独立正整数包装件数。
            if (input.getCount() == null || input.getCount() <= 0) throw invalid("SKU 包装件数必须为正整数");
            if (input.getUnitPrice() == null || input.getUnitPrice() <= 0) throw invalid("SKU 单价必须大于 0 分");
            ProductSkuDO sku = skuMapper.selectById(input.getSkuId());
            ProductSpuDO spu = sku == null ? null : spuMapper.selectById(sku.getSpuId());
            if (spu == null || !Objects.equals(spu.getShopId(), shopId) || !Objects.equals(spu.getStatus(), 1)
                    || !Objects.equals(spu.getReviewStatus(), 20) || spu.getApprovedVersion() == null
                    || !Objects.equals(spu.getApprovedVersion(), spu.getContentVersion())) {
                throw invalid("报价 SKU 必须属于当前店铺且为审核通过的在售商品");
            }
            boolean requireDeliveryScope = !Objects.equals(supplierTenantId, inquiryTenantId);
            long eligible = crossTenantMapper.countEligibleQuoteSkuForItem(supplierTenantId, inquiryTenantId,
                    inquiryId, input.getInquiryItemId(), shopId, input.getSkuId(), requireDeliveryScope);
            if (eligible != 1L) {
                throw invalid(requireDeliveryScope
                        ? "报价 SKU 与询价品类不匹配，或当前运费模板不支持该交付地区"
                        : "报价 SKU 与对应询价明细的品类不匹配");
            }
            long subtotal = Math.multiplyExact((long) input.getCount(), (long) input.getUnitPrice());
            total = Math.addExact(total, subtotal);
            if (total > Integer.MAX_VALUE) throw invalid("报价总额超过商城订单金额上限");
            String productName = spu.getName();
            String specification = skuSpecification(sku);
            String skuName = "默认规格".equals(specification) ? productName : productName + " " + specification;
            result.add(new ValidatedQuoteItem(input.getInquiryItemId(), input.getSkuId(), productName, skuName,
                    specification, spu.getContentVersion(), input.getCount(), input.getUnitPrice(), subtotal));
        }
        return new ValidatedQuote(result, total);
    }

    private void validateStoredQuoteItems(InquiryQuoteDO quote, InquiryDO inquiry, List<InquiryQuoteItemDO> stored,
                                          boolean populateMissingSnapshot) {
        List<InquiryQuoteSaveReqVO.Item> inputs = stored.stream().map(row -> {
            InquiryQuoteSaveReqVO.Item item = new InquiryQuoteSaveReqVO.Item();
            item.setInquiryItemId(row.getInquiryItemId()); item.setSkuId(row.getSkuId());
            item.setCount(row.getCount()); item.setUnitPrice(row.getUnitPrice()); return item;
        }).toList();
        List<InquiryItemDO> requested = inquiryItems(inquiry.getId());
        Long storedSupplierTenantId = supplierTenantId(quote);
        if (storedSupplierTenantId == null) {
            // 仅兼容迁移前未回填 supplier_tenant_id/tenant_id 的同租户历史报价；
            // 新数据的真实供应租户仍由报价记录持久化，不从请求参数猜测。
            storedSupplierTenantId = TenantContextHolder.getRequiredTenantId();
        }
        final Long supplierTenantId = storedSupplierTenantId;
        Long inquiryTenantId = quote.getTenantId() != null ? quote.getTenantId()
                : TenantContextHolder.getRequiredTenantId();
        Long currentTenantId = TenantContextHolder.getTenantId();
        ValidatedQuote recalculated = supplierTenantId != null
                && !Objects.equals(supplierTenantId, currentTenantId)
                ? inTenant(supplierTenantId, () -> validateQuoteItems(supplierTenantId, inquiryTenantId,
                inquiry.getId(), quote.getShopId(), requested, inputs))
                : validateQuoteItems(supplierTenantId, inquiryTenantId, inquiry.getId(),
                quote.getShopId(), requested, inputs);
        Map<Long, ValidatedQuoteItem> currentByInquiryItem = recalculated.items().stream()
                .collect(Collectors.toMap(ValidatedQuoteItem::inquiryItemId, Function.identity()));
        for (InquiryQuoteItemDO row : stored) {
            ValidatedQuoteItem current = currentByInquiryItem.get(row.getInquiryItemId());
            boolean snapshotMissing = row.getProductName() == null || row.getSkuName() == null
                    || row.getSkuSpecification() == null || row.getSpuContentVersion() == null;
            if (snapshotMissing && !populateMissingSnapshot) {
                throw invalid("报价商品快照缺失，请供应商重新保存并提交");
            }
            if (!snapshotMissing && (!Objects.equals(row.getProductName(), current.productName())
                    || !Objects.equals(row.getSkuName(), current.skuName())
                    || !Objects.equals(row.getSkuSpecification(), current.skuSpecification())
                    || !Objects.equals(row.getSpuContentVersion(), current.spuContentVersion()))) {
                throw invalid("报价商品版本已变化，请供应商重新保存并提交");
            }
            if (populateMissingSnapshot) {
                row.setProductName(current.productName()); row.setSkuName(current.skuName());
                row.setSkuSpecification(current.skuSpecification());
                row.setSpuContentVersion(current.spuContentVersion());
                quoteItemMapper.updateById(row);
            }
        }
        long storedTotal = stored.stream().mapToLong(InquiryQuoteItemDO::getTotalPrice).sum();
        if (quote.getFreightPrice() == null || quote.getFreightPrice() < 0) throw invalid("预计运费校验失败，请重新保存");
        long expectedTotal = Math.addExact(recalculated.totalPrice(), quote.getFreightPrice().longValue());
        if (recalculated.totalPrice() != storedTotal
                || !Objects.equals(quote.getGoodsPrice(), recalculated.totalPrice())
                || !Objects.equals(quote.getTotalPrice(), expectedTotal)) {
            throw invalid("报价金额校验失败，请重新保存");
        }
    }

    private List<InquiryItemDO> inquiryItems(Long inquiryId) {
        return inquiryItemMapper.selectList(new LambdaQueryWrapperX<InquiryItemDO>()
                .eq(InquiryItemDO::getInquiryId, inquiryId).orderByAsc(InquiryItemDO::getId));
    }

    private List<InquiryQuoteItemDO> quoteItems(Long quoteId) {
        return quoteItemMapper.selectList(new LambdaQueryWrapperX<InquiryQuoteItemDO>()
                .eq(InquiryQuoteItemDO::getQuoteId, quoteId).orderByAsc(InquiryQuoteItemDO::getInquiryItemId));
    }

    private static void validateQuoteTerms(Long deliveryDate, Long validUntil) {
        long now = System.currentTimeMillis();
        if (deliveryDate == null || deliveryDate <= now) throw invalid("预计送达日期须晚于当前时间");
        if (validUntil == null || validUntil <= now) throw invalid("报价有效期须晚于当前时间");
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private void auditSupplier(Long supplierTenantId, Long enterpriseId, Long shopId, Long adminUserId,
                               String action, String resourceType, Long resourceId, String detail) {
        inTenant(requireBusinessTenant(supplierTenantId), () -> operationAuditService.success(
                enterpriseId, shopId, "ADMIN", adminUserId, action, resourceType, resourceId, detail));
    }

    private static Long supplierTenantId(InquiryQuoteDO quote) {
        if (quote == null) return null;
        return quote.getSupplierTenantId() != null ? quote.getSupplierTenantId() : quote.getTenantId();
    }

    private static boolean isCrossTenantQuote(InquiryQuoteDO quote) {
        Long supplierTenantId = supplierTenantId(quote);
        return quote != null && quote.getTenantId() != null && supplierTenantId != null
                && !Objects.equals(quote.getTenantId(), supplierTenantId);
    }

    private static Long requireBusinessTenant(Long tenantId) {
        if (tenantId == null || tenantId <= 0 || Objects.equals(tenantId, 3000L)) {
            throw invalid("询价租户无效或已停止经营写入");
        }
        return tenantId;
    }

    private static <T> T inTenant(Long tenantId, Supplier<T> supplier) {
        Long target = requireBusinessTenant(tenantId);
        Long previousTenant = TenantContextHolder.getTenantId();
        boolean previousIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setTenantId(target);
            TenantContextHolder.setIgnore(false);
            return supplier.get();
        } finally {
            TenantContextHolder.setTenantId(previousTenant);
            TenantContextHolder.setIgnore(previousIgnore);
        }
    }

    private static void inTenant(Long tenantId, Runnable runnable) {
        inTenant(tenantId, () -> {
            runnable.run();
            return null;
        });
    }

    private static void requireWritableTenant() {
        MallTenantWriteGuard.requireWritable(TenantContextHolder.getRequiredTenantId());
    }

    private record QuoteAccess(InquiryQuoteDO quote, InquiryDO inquiry, String role) {}
    private record AdminQuoteAccess(InquiryQuoteDO quote, InquiryDO inquiry, ProductShopDO shop) {}
    private record AdminQuoteRef(Long inquiryTenantId, Long supplierTenantId, ProductShopDO shop) {}
    private record ValidatedQuoteItem(Long inquiryItemId, Long skuId, String productName, String skuName,
                                      String skuSpecification, Long spuContentVersion, Integer count,
                                      Integer unitPrice, Long totalPrice) {}
    private record ValidatedQuote(List<ValidatedQuoteItem> items, Long totalPrice) {}
}

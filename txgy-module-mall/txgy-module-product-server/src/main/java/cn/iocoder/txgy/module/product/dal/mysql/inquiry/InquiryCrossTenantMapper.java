package cn.iocoder.txgy.module.product.dal.mysql.inquiry;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 跨租户询价的最小白名单查询。
 *
 * <p>这里故意不返回询价正文、联系方式、报价金额或商品数据，只解析已经通过
 * 租户、企业、店铺、品类和可见范围校验的资源编号及其真实数据租户。业务服务
 * 随后必须切换到 {@code inquiryTenantId}，继续使用普通租户 Mapper 读取和写入。</p>
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface InquiryCrossTenantMapper {

    String ACTIVE_SYSTEM_TENANT = " EXISTS (SELECT 1 FROM system_tenant st"
            + " WHERE st.id=i.tenant_id AND st.deleted=b'0' AND st.status=0"
            + " AND (st.expire_time IS NULL OR st.expire_time&gt;NOW()))";

    String ACTIVE_BUYER_ENTERPRISE = " EXISTS (SELECT 1 FROM product_enterprise buyer_enterprise"
            + " WHERE buyer_enterprise.id=i.enterprise_id"
            + " AND buyer_enterprise.tenant_id=i.tenant_id"
            + " AND buyer_enterprise.deleted=b'0' AND buyer_enterprise.audit_status=1)";

    String OPEN_BUYER_PORTAL = " EXISTS (SELECT 1 FROM mall_portal_tenant buyer_portal"
            + " WHERE buyer_portal.tenant_id=i.tenant_id"
            + " AND buyer_portal.deleted=b'0' AND buyer_portal.open_status=0)";

    /**
     * 已产生报价后的跨租户读取仍须持续满足原平台公开边界。不能因为报价编号已知，
     * 就绕过租户停用、门户关闭或询价可见范围变更后的隔离。
     */
    String ACTIVE_QUOTE_PORTAL_PAIR = " EXISTS (SELECT 1 FROM mall_portal_tenant buyer_portal"
            + " WHERE buyer_portal.tenant_id=q.tenant_id"
            + " AND buyer_portal.deleted=b'0' AND buyer_portal.open_status=0)"
            + " AND EXISTS (SELECT 1 FROM mall_portal_tenant supplier_portal"
            + " WHERE supplier_portal.tenant_id=COALESCE(q.supplier_tenant_id,q.tenant_id)"
            + " AND supplier_portal.deleted=b'0' AND supplier_portal.open_status=0)";

    String QUOTE_DIRECTED_SCOPE = " EXISTS (SELECT 1 FROM product_purchase_inquiry_invited_scope quote_scope"
            + " WHERE quote_scope.inquiry_id=i.id AND quote_scope.deleted=b'0'"
            + " AND quote_scope.target_tenant_id=COALESCE(q.supplier_tenant_id,q.tenant_id)"
            + " AND ((quote_scope.target_type='TENANT'"
            + " AND quote_scope.target_enterprise_id IS NULL AND quote_scope.target_shop_id IS NULL)"
            + " OR (quote_scope.target_type='ENTERPRISE'"
            + " AND quote_scope.target_enterprise_id=q.enterprise_id AND quote_scope.target_shop_id IS NULL)"
            + " OR (quote_scope.target_type='SHOP'"
            + " AND quote_scope.target_enterprise_id=q.enterprise_id AND quote_scope.target_shop_id=q.shop_id)))";

    String ACTIVE_QUOTE_SCOPE = " AND COALESCE(q.supplier_tenant_id,q.tenant_id)&lt;&gt;3000"
            + " AND " + ACTIVE_SYSTEM_TENANT
            + " AND EXISTS (SELECT 1 FROM system_tenant supplier_tenant"
            + " WHERE supplier_tenant.id=COALESCE(q.supplier_tenant_id,q.tenant_id)"
            + " AND supplier_tenant.deleted=b'0' AND supplier_tenant.status=0"
            + " AND (supplier_tenant.expire_time IS NULL OR supplier_tenant.expire_time&gt;NOW()))"
            + " AND " + ACTIVE_BUYER_ENTERPRISE
            + " AND (COALESCE(q.supplier_tenant_id,q.tenant_id)=q.tenant_id"
            + " OR (" + ACTIVE_QUOTE_PORTAL_PAIR
            + " AND (COALESCE(i.visibility,'TENANT_PUBLIC')='PLATFORM_PUBLIC'"
            + " OR (COALESCE(i.visibility,'TENANT_PUBLIC')='INVITED_SHOPS'"
            + " AND " + QUOTE_DIRECTED_SCOPE + "))))";

    String ACTIVE_VIEWER_SHOP = " EXISTS (SELECT 1 FROM product_shop viewer_shop"
            + " JOIN product_enterprise viewer_enterprise"
            + " ON viewer_enterprise.id=viewer_shop.enterprise_id"
            + " AND viewer_enterprise.tenant_id=viewer_shop.tenant_id"
            + " WHERE viewer_shop.id=#{shopId} AND viewer_shop.tenant_id=#{viewerTenantId}"
            + " AND viewer_shop.enterprise_id=#{viewerEnterpriseId}"
            + " AND viewer_shop.deleted=b'0' AND viewer_shop.status=0"
            + " AND viewer_enterprise.deleted=b'0' AND viewer_enterprise.audit_status=1)";

    String OPEN_PLATFORM_PAIR = " EXISTS (SELECT 1 FROM mall_portal_tenant buyer_portal"
            + " WHERE buyer_portal.tenant_id=i.tenant_id"
            + " AND buyer_portal.deleted=b'0' AND buyer_portal.open_status=0)"
            + " AND EXISTS (SELECT 1 FROM mall_portal_tenant supplier_portal"
            + " WHERE supplier_portal.tenant_id=#{viewerTenantId}"
            + " AND supplier_portal.deleted=b'0' AND supplier_portal.open_status=0)";

    /**
     * 未绑定 SKU 的询价明细允许按物料名称匹配供应商商品。询价人常把“防腐”写进
     * 物料名称，而商品标题会把它放在分类中，因此同时使用原名称和去掉“防腐”后的
     * 核心名称；核心名称至少两个字符，避免过宽匹配。
     */
    String FREE_TEXT_PRODUCT_NAME_MATCH = " (NULLIF(TRIM(requested_item.name),'') IS NOT NULL"
            + " AND (LOCATE(BINARY LOWER(TRIM(requested_item.name)),BINARY LOWER(seller_spu.name))&gt;0"
            + " OR (CHAR_LENGTH(TRIM(REPLACE(requested_item.name,'防腐','')))&gt;=2"
            + " AND LOCATE(BINARY LOWER(TRIM(REPLACE(requested_item.name,'防腐',''))),"
            + " BINARY LOWER(seller_spu.name))&gt;0)))";

    String CATEGORY_MATCH = " EXISTS (SELECT 1 FROM product_spu seller_spu"
            + " LEFT JOIN product_category seller_category"
            + " ON seller_category.id=seller_spu.category_id"
            + " AND seller_category.tenant_id=seller_spu.tenant_id"
            + " AND seller_category.deleted=b'0'"
            + " WHERE seller_spu.tenant_id=#{viewerTenantId}"
            + " AND seller_spu.shop_id=#{shopId} AND seller_spu.deleted=b'0'"
            + " AND seller_spu.status=1 AND seller_spu.review_status=20"
            + " AND seller_spu.approved_version IS NOT NULL"
            + " AND seller_spu.approved_version=seller_spu.content_version"
            + " AND EXISTS (SELECT 1 FROM product_purchase_inquiry_item requested_item"
            + " LEFT JOIN product_sku requested_sku"
            + " ON requested_sku.id=requested_item.sku_id"
            + " AND requested_sku.tenant_id=requested_item.tenant_id"
            + " AND requested_sku.deleted=b'0'"
            + " LEFT JOIN product_spu requested_spu"
            + " ON requested_spu.id=requested_sku.spu_id"
            + " AND requested_spu.tenant_id=requested_item.tenant_id"
            + " AND requested_spu.deleted=b'0'"
            + " WHERE requested_item.inquiry_id=i.id"
            + " AND requested_item.tenant_id=i.tenant_id AND requested_item.deleted=b'0'"
            + " AND ((requested_spu.category_id IS NOT NULL"
            + " AND requested_spu.category_id=seller_spu.category_id)"
            + " OR (requested_spu.category_id IS NULL"
            + " AND ((NULLIF(TRIM(requested_item.category),'') IS NOT NULL"
            + " AND seller_category.id IS NOT NULL"
            + " AND BINARY LOWER(TRIM(requested_item.category))"
            + "=BINARY LOWER(TRIM(seller_category.name)))"
            + " OR " + FREE_TEXT_PRODUCT_NAME_MATCH + ")))))";

    /** 平台公开机会复用商品运费模板作为真实可配送范围，不从自由文本地址猜测。 */
    String DELIVERY_SCOPE_MATCH = " i.delivery_area_id IS NOT NULL"
            + " AND seller_spu.delivery_template_id IS NOT NULL"
            + " AND EXISTS (SELECT 1 FROM trade_delivery_express_template delivery_template"
            + " WHERE delivery_template.id=seller_spu.delivery_template_id"
            + " AND delivery_template.tenant_id=#{viewerTenantId}"
            + " AND delivery_template.deleted=b'0'"
            + " AND (EXISTS (SELECT 1 FROM trade_delivery_express_template_charge delivery_charge"
            + " WHERE delivery_charge.template_id=delivery_template.id"
            + " AND delivery_charge.tenant_id=#{viewerTenantId}"
            + " AND delivery_charge.deleted=b'0'"
            + " AND (FIND_IN_SET(CAST(i.delivery_area_id AS CHAR) COLLATE utf8mb4_unicode_ci,delivery_charge.area_ids)&gt;0"
            + " OR FIND_IN_SET(CAST(i.delivery_city_id AS CHAR) COLLATE utf8mb4_unicode_ci,delivery_charge.area_ids)&gt;0"
            + " OR FIND_IN_SET(CAST(i.delivery_province_id AS CHAR) COLLATE utf8mb4_unicode_ci,delivery_charge.area_ids)&gt;0"
            + " OR FIND_IN_SET('1',delivery_charge.area_ids)&gt;0))"
            + " OR EXISTS (SELECT 1 FROM trade_delivery_express_template_free delivery_free"
            + " WHERE delivery_free.template_id=delivery_template.id"
            + " AND delivery_free.tenant_id=#{viewerTenantId}"
            + " AND delivery_free.deleted=b'0'"
            + " AND (FIND_IN_SET(CAST(i.delivery_area_id AS CHAR) COLLATE utf8mb4_unicode_ci,delivery_free.area_ids)&gt;0"
            + " OR FIND_IN_SET(CAST(i.delivery_city_id AS CHAR) COLLATE utf8mb4_unicode_ci,delivery_free.area_ids)&gt;0"
            + " OR FIND_IN_SET(CAST(i.delivery_province_id AS CHAR) COLLATE utf8mb4_unicode_ci,delivery_free.area_ids)&gt;0"
            + " OR FIND_IN_SET('1',delivery_free.area_ids)&gt;0))))";

    String CATEGORY_AND_DELIVERY_MATCH = " EXISTS (SELECT 1"
            + " FROM product_purchase_inquiry_item platform_item"
            + " WHERE platform_item.inquiry_id=i.id"
            + " AND platform_item.tenant_id=i.tenant_id AND platform_item.deleted=b'0')"
            + " AND NOT EXISTS (SELECT 1 FROM product_purchase_inquiry_item requested_item"
            + " LEFT JOIN product_sku requested_sku"
            + " ON requested_sku.id=requested_item.sku_id"
            + " AND requested_sku.tenant_id=requested_item.tenant_id"
            + " AND requested_sku.deleted=b'0'"
            + " LEFT JOIN product_spu requested_spu"
            + " ON requested_spu.id=requested_sku.spu_id"
            + " AND requested_spu.tenant_id=requested_item.tenant_id"
            + " AND requested_spu.deleted=b'0'"
            + " LEFT JOIN product_category requested_category"
            + " ON requested_category.id=requested_spu.category_id"
            + " AND requested_category.tenant_id=requested_item.tenant_id"
            + " AND requested_category.deleted=b'0'"
            + " WHERE requested_item.inquiry_id=i.id"
            + " AND requested_item.tenant_id=i.tenant_id AND requested_item.deleted=b'0'"
            + " AND NOT EXISTS (SELECT 1 FROM product_spu seller_spu"
            + " LEFT JOIN product_category seller_category"
            + " ON seller_category.id=seller_spu.category_id"
            + " AND seller_category.tenant_id=seller_spu.tenant_id"
            + " AND seller_category.deleted=b'0'"
            + " WHERE seller_spu.tenant_id=#{viewerTenantId}"
            + " AND seller_spu.shop_id=#{shopId} AND seller_spu.deleted=b'0'"
            + " AND seller_spu.status=1 AND seller_spu.review_status=20"
            + " AND seller_spu.approved_version IS NOT NULL"
            + " AND seller_spu.approved_version=seller_spu.content_version"
            + " AND " + DELIVERY_SCOPE_MATCH
            + " AND ((requested_spu.category_id IS NOT NULL AND ("
            + " (i.tenant_id=#{viewerTenantId} AND requested_spu.category_id=seller_spu.category_id)"
            + " OR (requested_category.id IS NOT NULL AND seller_category.id IS NOT NULL"
            + " AND BINARY LOWER(TRIM(requested_category.name))"
            + "=BINARY LOWER(TRIM(seller_category.name)))))"
            + " OR (requested_spu.category_id IS NULL"
            + " AND ((NULLIF(TRIM(requested_item.category),'') IS NOT NULL"
            + " AND seller_category.id IS NOT NULL"
            + " AND BINARY LOWER(TRIM(requested_item.category))"
            + "=BINARY LOWER(TRIM(seller_category.name)))"
            + " OR " + FREE_TEXT_PRODUCT_NAME_MATCH + ")))))";

    /**
     * 报价行必须精确对应一条询价明细。查询显式带上买卖双方租户，避免普通租户
     * Mapper 在切换上下文时把另一个租户的 SKU 或配送模板误当成当前资源。
     */
    String QUOTE_ITEM_ELIGIBILITY_FROM = " FROM product_purchase_inquiry i"
            + " JOIN product_purchase_inquiry_item requested_item"
            + " ON requested_item.id=#{inquiryItemId}"
            + " AND requested_item.inquiry_id=i.id"
            + " AND requested_item.tenant_id=i.tenant_id"
            + " AND requested_item.deleted=b'0'"
            + " JOIN product_sku seller_sku ON seller_sku.id=#{skuId}"
            + " AND seller_sku.tenant_id=#{supplierTenantId} AND seller_sku.deleted=b'0'"
            + " JOIN product_spu seller_spu ON seller_spu.id=seller_sku.spu_id"
            + " AND seller_spu.tenant_id=seller_sku.tenant_id AND seller_spu.deleted=b'0'"
            + " LEFT JOIN product_category seller_category"
            + " ON seller_category.id=seller_spu.category_id"
            + " AND seller_category.tenant_id=seller_spu.tenant_id"
            + " AND seller_category.deleted=b'0'"
            + " LEFT JOIN product_sku requested_sku ON requested_sku.id=requested_item.sku_id"
            + " AND requested_sku.tenant_id=requested_item.tenant_id AND requested_sku.deleted=b'0'"
            + " LEFT JOIN product_spu requested_spu ON requested_spu.id=requested_sku.spu_id"
            + " AND requested_spu.tenant_id=requested_item.tenant_id AND requested_spu.deleted=b'0'"
            + " LEFT JOIN product_category requested_category"
            + " ON requested_category.id=requested_spu.category_id"
            + " AND requested_category.tenant_id=requested_spu.tenant_id"
            + " AND requested_category.deleted=b'0'"
            + " WHERE i.id=#{inquiryId} AND i.tenant_id=#{inquiryTenantId}"
            + " AND i.deleted=b'0' AND i.tenant_id&lt;&gt;3000 AND #{supplierTenantId}&lt;&gt;3000"
            + " AND seller_spu.shop_id=#{shopId}"
            + " AND seller_spu.status=1 AND seller_spu.review_status=20"
            + " AND seller_spu.approved_version IS NOT NULL"
            + " AND seller_spu.approved_version=seller_spu.content_version"
            + " AND ((requested_spu.category_id IS NOT NULL AND ("
            + " (#{supplierTenantId}=#{inquiryTenantId}"
            + " AND requested_spu.category_id=seller_spu.category_id)"
            + " OR (requested_category.id IS NOT NULL AND seller_category.id IS NOT NULL"
            + " AND BINARY LOWER(TRIM(requested_category.name))"
            + "=BINARY LOWER(TRIM(seller_category.name)))))"
            + " OR (requested_spu.category_id IS NULL"
            + " AND ((NULLIF(TRIM(requested_item.category),'') IS NOT NULL"
            + " AND seller_category.id IS NOT NULL"
            + " AND BINARY LOWER(TRIM(requested_item.category))"
            + "=BINARY LOWER(TRIM(seller_category.name)))"
            + " OR " + FREE_TEXT_PRODUCT_NAME_MATCH + ")))";

    String QUOTE_ITEM_DELIVERY_SCOPE = " i.delivery_area_id IS NOT NULL"
            + " AND seller_spu.delivery_template_id IS NOT NULL"
            + " AND EXISTS (SELECT 1 FROM trade_delivery_express_template delivery_template"
            + " WHERE delivery_template.id=seller_spu.delivery_template_id"
            + " AND delivery_template.tenant_id=#{supplierTenantId}"
            + " AND delivery_template.deleted=b'0'"
            + " AND (EXISTS (SELECT 1 FROM trade_delivery_express_template_charge delivery_charge"
            + " WHERE delivery_charge.template_id=delivery_template.id"
            + " AND delivery_charge.tenant_id=#{supplierTenantId}"
            + " AND delivery_charge.deleted=b'0'"
            + " AND (FIND_IN_SET(CAST(i.delivery_area_id AS CHAR) COLLATE utf8mb4_unicode_ci,delivery_charge.area_ids)&gt;0"
            + " OR FIND_IN_SET(CAST(i.delivery_city_id AS CHAR) COLLATE utf8mb4_unicode_ci,delivery_charge.area_ids)&gt;0"
            + " OR FIND_IN_SET(CAST(i.delivery_province_id AS CHAR) COLLATE utf8mb4_unicode_ci,delivery_charge.area_ids)&gt;0"
            + " OR FIND_IN_SET('1',delivery_charge.area_ids)&gt;0))"
            + " OR EXISTS (SELECT 1 FROM trade_delivery_express_template_free delivery_free"
            + " WHERE delivery_free.template_id=delivery_template.id"
            + " AND delivery_free.tenant_id=#{supplierTenantId}"
            + " AND delivery_free.deleted=b'0'"
            + " AND (FIND_IN_SET(CAST(i.delivery_area_id AS CHAR) COLLATE utf8mb4_unicode_ci,delivery_free.area_ids)&gt;0"
            + " OR FIND_IN_SET(CAST(i.delivery_city_id AS CHAR) COLLATE utf8mb4_unicode_ci,delivery_free.area_ids)&gt;0"
            + " OR FIND_IN_SET(CAST(i.delivery_province_id AS CHAR) COLLATE utf8mb4_unicode_ci,delivery_free.area_ids)&gt;0"
            + " OR FIND_IN_SET('1',delivery_free.area_ids)&gt;0))))";

    String LEGACY_SAME_TENANT_INVITATION = " EXISTS (SELECT 1"
            + " FROM product_purchase_inquiry_invited_shop legacy_invite"
            + " WHERE legacy_invite.inquiry_id=i.id"
            + " AND legacy_invite.tenant_id=i.tenant_id"
            + " AND legacy_invite.shop_id=#{shopId} AND legacy_invite.deleted=b'0')";

    String CROSS_TENANT_DIRECTED_INVITATION = " EXISTS (SELECT 1"
            + " FROM product_purchase_inquiry_invited_scope directed_scope"
            + " WHERE directed_scope.inquiry_id=i.id AND directed_scope.deleted=b'0'"
            + " AND directed_scope.target_tenant_id=#{viewerTenantId}"
            + " AND ((directed_scope.target_type='TENANT'"
            + " AND directed_scope.target_enterprise_id IS NULL AND directed_scope.target_shop_id IS NULL)"
            + " OR (directed_scope.target_type='ENTERPRISE'"
            + " AND directed_scope.target_enterprise_id=#{viewerEnterpriseId}"
            + " AND directed_scope.target_shop_id IS NULL)"
            + " OR (directed_scope.target_type='SHOP'"
            + " AND directed_scope.target_enterprise_id=#{viewerEnterpriseId}"
            + " AND directed_scope.target_shop_id=#{shopId})))";

    String VISIBILITY_WHITELIST = " ((COALESCE(i.visibility,'TENANT_PUBLIC')='PLATFORM_PUBLIC'"
            + " AND " + OPEN_PLATFORM_PAIR + " AND " + CATEGORY_AND_DELIVERY_MATCH + ")"
            + " OR (i.tenant_id=#{viewerTenantId}"
            + " AND COALESCE(i.visibility,'TENANT_PUBLIC')='TENANT_PUBLIC'"
            + " AND " + CATEGORY_MATCH + ")"
            + " OR (i.tenant_id=#{viewerTenantId}"
            + " AND COALESCE(i.visibility,'TENANT_PUBLIC')='INVITED_SHOPS'"
            + " AND " + LEGACY_SAME_TENANT_INVITATION + ")"
            + " OR (i.tenant_id&lt;&gt;#{viewerTenantId}"
            + " AND COALESCE(i.visibility,'TENANT_PUBLIC')='INVITED_SHOPS'"
            + " AND " + OPEN_PLATFORM_PAIR
            + " AND " + CROSS_TENANT_DIRECTED_INVITATION + "))";

    String OPPORTUNITY_FROM = " FROM product_purchase_inquiry i"
            + " WHERE i.deleted=b'0' AND i.tenant_id&lt;&gt;3000"
            + " AND #{viewerTenantId}&lt;&gt;3000"
            + " AND i.status IN (1,3,4) AND i.no IS NOT NULL AND i.no&lt;&gt;''"
            + " AND " + ACTIVE_SYSTEM_TENANT
            + " AND EXISTS (SELECT 1 FROM system_tenant viewer_tenant"
            + " WHERE viewer_tenant.id=#{viewerTenantId}"
            + " AND viewer_tenant.deleted=b'0' AND viewer_tenant.status=0"
            + " AND (viewer_tenant.expire_time IS NULL OR viewer_tenant.expire_time&gt;NOW()))"
            + " AND " + ACTIVE_BUYER_ENTERPRISE
            + " AND " + ACTIVE_VIEWER_SHOP
            + " AND i.enterprise_id&lt;&gt;#{viewerEnterpriseId}"
            + " AND " + VISIBILITY_WHITELIST;

    /**
     * 平台询价专栏只聚合明确发布为 PLATFORM_PUBLIC 的公开摘要。这里仍只返回编号和
     * 真实租户，正文由业务层切回资源租户后通过普通 Mapper 读取，避免开放任意跨租户查询。
     */
    String PLATFORM_PUBLIC_FROM = " FROM product_purchase_inquiry i"
            + " WHERE i.deleted=b'0' AND i.tenant_id&lt;&gt;3000"
            + " AND i.status IN (1,3,4) AND i.no IS NOT NULL AND i.no&lt;&gt;''"
            + " AND COALESCE(i.visibility,'TENANT_PUBLIC')='PLATFORM_PUBLIC'"
            + " AND " + ACTIVE_SYSTEM_TENANT
            + " AND " + ACTIVE_BUYER_ENTERPRISE
            + " AND " + OPEN_BUYER_PORTAL;

    /** Search only fields that are already visible in the public summary; never search contact/address detail. */
    String PUBLIC_KEYWORD_FILTER = " AND (LOWER(i.no) LIKE CONCAT('%',#{keyword},'%')"
            + " OR LOWER(i.title) LIKE CONCAT('%',#{keyword},'%')"
            + " OR EXISTS (SELECT 1 FROM product_enterprise keyword_enterprise"
            + " WHERE keyword_enterprise.id=i.enterprise_id"
            + " AND keyword_enterprise.tenant_id=i.tenant_id"
            + " AND keyword_enterprise.deleted=b'0' AND keyword_enterprise.audit_status=1"
            + " AND LOWER(keyword_enterprise.name) LIKE CONCAT('%',#{keyword},'%'))"
            + " OR EXISTS (SELECT 1 FROM product_purchase_inquiry_item keyword_item"
            + " WHERE keyword_item.inquiry_id=i.id AND keyword_item.tenant_id=i.tenant_id"
            + " AND keyword_item.deleted=b'0' AND (LOWER(keyword_item.name) LIKE CONCAT('%',#{keyword},'%')"
            + " OR LOWER(keyword_item.category) LIKE CONCAT('%',#{keyword},'%'))))";

    @Select({"<script>",
            "SELECT i.id AS inquiryId,i.tenant_id AS inquiryTenantId,i.visibility AS visibility",
            PLATFORM_PUBLIC_FROM,
            "<if test=\"state == 'open'\"> AND i.status=1 AND i.deadline&gt;#{now}</if>",
            "<if test=\"state == 'ended'\"> AND (i.status IN (3,4) OR (i.status=1 AND i.deadline&lt;=#{now}))</if>",
            "<if test=\"keyword != null\">" + PUBLIC_KEYWORD_FILTER + "</if>",
            " ORDER BY i.id DESC LIMIT #{limit} OFFSET #{offset}",
            "</script>"})
    List<OpportunityRef> selectPlatformPublicInquiries(@Param("state") String state,
                                                        @Param("now") Long now,
                                                        @Param("keyword") String keyword,
                                                        @Param("offset") long offset,
                                                        @Param("limit") int limit);

    @Select({"<script>", "SELECT COUNT(1)", PLATFORM_PUBLIC_FROM,
            "<if test=\"state == 'open'\"> AND i.status=1 AND i.deadline&gt;#{now}</if>",
            "<if test=\"state == 'ended'\"> AND (i.status IN (3,4) OR (i.status=1 AND i.deadline&lt;=#{now}))</if>",
            "<if test=\"keyword != null\">" + PUBLIC_KEYWORD_FILTER + "</if>",
            "</script>"})
    long countPlatformPublicInquiries(@Param("state") String state, @Param("now") Long now,
                                      @Param("keyword") String keyword);

    @Select({"<script>", "SELECT i.id AS inquiryId,i.tenant_id AS inquiryTenantId,i.visibility AS visibility",
            PLATFORM_PUBLIC_FROM, " AND i.id=#{inquiryId} LIMIT 1", "</script>"})
    OpportunityRef selectPlatformPublicInquiry(@Param("inquiryId") Long inquiryId);

    @Select({"<script>",
            "SELECT i.id AS inquiryId,i.tenant_id AS inquiryTenantId,i.visibility AS visibility",
            OPPORTUNITY_FROM,
            "<if test=\"state == 'open'\"> AND i.status=1 AND i.deadline&gt;#{now}</if>",
            "<if test=\"state == 'ended'\"> AND (i.status IN (3,4) OR (i.status=1 AND i.deadline&lt;=#{now}))</if>",
            "<if test=\"keyword != null\">" + PUBLIC_KEYWORD_FILTER + "</if>",
            " ORDER BY i.id DESC LIMIT #{limit} OFFSET #{offset}",
            "</script>"})
    List<OpportunityRef> selectVisibleOpportunities(@Param("viewerTenantId") Long viewerTenantId,
                                                     @Param("viewerEnterpriseId") Long viewerEnterpriseId,
                                                     @Param("shopId") Long shopId,
                                                     @Param("state") String state,
                                                     @Param("now") Long now,
                                                     @Param("keyword") String keyword,
                                                     @Param("offset") long offset,
                                                     @Param("limit") int limit);

    @Select({"<script>", "SELECT COUNT(1)", OPPORTUNITY_FROM,
            "<if test=\"state == 'open'\"> AND i.status=1 AND i.deadline&gt;#{now}</if>",
            "<if test=\"state == 'ended'\"> AND (i.status IN (3,4) OR (i.status=1 AND i.deadline&lt;=#{now}))</if>",
            "<if test=\"keyword != null\">" + PUBLIC_KEYWORD_FILTER + "</if>",
            "</script>"})
    long countVisibleOpportunities(@Param("viewerTenantId") Long viewerTenantId,
                                   @Param("viewerEnterpriseId") Long viewerEnterpriseId,
                                   @Param("shopId") Long shopId,
                                   @Param("state") String state,
                                   @Param("now") Long now,
                                   @Param("keyword") String keyword);

    @Select({"<script>", "SELECT i.id AS inquiryId,i.tenant_id AS inquiryTenantId,i.visibility AS visibility",
            OPPORTUNITY_FROM, " AND i.id=#{inquiryId} LIMIT 1", "</script>"})
    OpportunityRef selectVisibleOpportunity(@Param("viewerTenantId") Long viewerTenantId,
                                             @Param("viewerEnterpriseId") Long viewerEnterpriseId,
                                             @Param("shopId") Long shopId,
                                             @Param("inquiryId") Long inquiryId);

    @Select({"<script>", "SELECT COUNT(1)", QUOTE_ITEM_ELIGIBILITY_FROM,
            "<if test='requireDeliveryScope'> AND " + QUOTE_ITEM_DELIVERY_SCOPE + "</if>",
            "</script>"})
    long countEligibleQuoteSkuForItem(@Param("supplierTenantId") Long supplierTenantId,
                                      @Param("inquiryTenantId") Long inquiryTenantId,
                                      @Param("inquiryId") Long inquiryId,
                                      @Param("inquiryItemId") Long inquiryItemId,
                                      @Param("shopId") Long shopId,
                                      @Param("skuId") Long skuId,
                                      @Param("requireDeliveryScope") boolean requireDeliveryScope);

    String SUPPLIER_QUOTE_FROM = " FROM product_purchase_inquiry_quote q"
            + " JOIN product_purchase_inquiry i ON i.id=q.inquiry_id"
            + " AND i.tenant_id=q.tenant_id AND i.deleted=b'0'"
            + " JOIN product_shop supplier_shop ON supplier_shop.id=q.shop_id"
            + " AND supplier_shop.tenant_id=COALESCE(q.supplier_tenant_id,q.tenant_id)"
            + " JOIN product_enterprise supplier_enterprise"
            + " ON supplier_enterprise.id=q.enterprise_id"
            + " AND supplier_enterprise.tenant_id=COALESCE(q.supplier_tenant_id,q.tenant_id)"
            + " WHERE q.deleted=b'0' AND q.tenant_id&lt;&gt;3000"
            + " AND COALESCE(q.supplier_tenant_id,q.tenant_id)=#{supplierTenantId}"
            + " AND q.shop_id=#{shopId} AND q.enterprise_id=#{enterpriseId}"
            + ACTIVE_QUOTE_SCOPE
            + " AND supplier_shop.enterprise_id=q.enterprise_id"
            + " AND supplier_shop.deleted=b'0' AND supplier_shop.status=0"
            + " AND supplier_enterprise.deleted=b'0' AND supplier_enterprise.audit_status=1";

    String SUPPLIER_TENANT_QUOTE_FROM = " FROM product_purchase_inquiry_quote q"
            + " JOIN product_purchase_inquiry i ON i.id=q.inquiry_id"
            + " AND i.tenant_id=q.tenant_id AND i.deleted=b'0'"
            + " JOIN product_shop supplier_shop ON supplier_shop.id=q.shop_id"
            + " AND supplier_shop.tenant_id=COALESCE(q.supplier_tenant_id,q.tenant_id)"
            + " JOIN product_enterprise supplier_enterprise"
            + " ON supplier_enterprise.id=q.enterprise_id"
            + " AND supplier_enterprise.tenant_id=COALESCE(q.supplier_tenant_id,q.tenant_id)"
            + " WHERE q.deleted=b'0' AND q.tenant_id&lt;&gt;3000"
            + " AND COALESCE(q.supplier_tenant_id,q.tenant_id)=#{supplierTenantId}"
            + ACTIVE_QUOTE_SCOPE
            + " AND supplier_shop.enterprise_id=q.enterprise_id"
            + " AND supplier_shop.deleted=b'0' AND supplier_shop.status=0"
            + " AND supplier_enterprise.deleted=b'0' AND supplier_enterprise.audit_status=1";

    /**
     * 历史报价读取只按报价落库时固化的供应租户、企业和店铺归属授权。
     *
     * <p>这里刻意不关联当前门户、租户、企业或店铺状态：这些状态控制后续写操作，
     * 不能抹掉交易双方已经形成的报价历史。查询只返回不含业务正文的引用，服务层还
     * 必须校验当前调用者仍持有对应企业/店铺主体授权。</p>
     */
    String HISTORICAL_SUPPLIER_QUOTE_BASE = " FROM product_purchase_inquiry_quote q"
            + " WHERE q.deleted=b'0' AND q.tenant_id&lt;&gt;3000"
            + " AND COALESCE(q.supplier_tenant_id,q.tenant_id)&lt;&gt;3000"
            + " AND COALESCE(q.supplier_tenant_id,q.tenant_id)=#{supplierTenantId}";

    String HISTORICAL_SUPPLIER_QUOTE_FROM = HISTORICAL_SUPPLIER_QUOTE_BASE
            + " AND q.shop_id=#{shopId} AND q.enterprise_id=#{enterpriseId}";

    @Select({"<script>", "SELECT q.id AS quoteId,q.tenant_id AS inquiryTenantId,",
            " COALESCE(q.supplier_tenant_id,q.tenant_id) AS supplierTenantId,",
            " q.inquiry_id AS inquiryId,q.enterprise_id AS enterpriseId,q.shop_id AS shopId",
            HISTORICAL_SUPPLIER_QUOTE_BASE, " AND q.id=#{quoteId} LIMIT 1", "</script>"})
    QuoteRef selectHistoricalSupplierQuoteForTenant(@Param("supplierTenantId") Long supplierTenantId,
                                                    @Param("quoteId") Long quoteId);

    @Select({"<script>", "SELECT q.id AS quoteId,q.tenant_id AS inquiryTenantId,",
            " COALESCE(q.supplier_tenant_id,q.tenant_id) AS supplierTenantId,",
            " q.inquiry_id AS inquiryId,q.enterprise_id AS enterpriseId,q.shop_id AS shopId",
            HISTORICAL_SUPPLIER_QUOTE_FROM, " AND q.id=#{quoteId} LIMIT 1", "</script>"})
    QuoteRef selectHistoricalSupplierQuote(@Param("supplierTenantId") Long supplierTenantId,
                                           @Param("enterpriseId") Long enterpriseId,
                                           @Param("shopId") Long shopId,
                                           @Param("quoteId") Long quoteId);

    @Select({"<script>", "SELECT q.id AS quoteId,q.tenant_id AS inquiryTenantId,",
            " COALESCE(q.supplier_tenant_id,q.tenant_id) AS supplierTenantId,",
            " q.inquiry_id AS inquiryId,q.enterprise_id AS enterpriseId,q.shop_id AS shopId",
            HISTORICAL_SUPPLIER_QUOTE_FROM, " AND q.inquiry_id=#{inquiryId} LIMIT 1", "</script>"})
    QuoteRef selectHistoricalSupplierQuoteByInquiry(@Param("supplierTenantId") Long supplierTenantId,
                                                    @Param("enterpriseId") Long enterpriseId,
                                                    @Param("shopId") Long shopId,
                                                    @Param("inquiryId") Long inquiryId);

    @Select({"<script>",
            "SELECT q.id AS quoteId,q.tenant_id AS inquiryTenantId,",
            " COALESCE(q.supplier_tenant_id,q.tenant_id) AS supplierTenantId,",
            " q.inquiry_id AS inquiryId,q.enterprise_id AS enterpriseId,q.shop_id AS shopId",
            HISTORICAL_SUPPLIER_QUOTE_FROM,
            "<if test='status != null'> AND q.status=#{status}</if>",
            " ORDER BY q.id DESC LIMIT #{limit} OFFSET #{offset}",
            "</script>"})
    List<QuoteRef> selectHistoricalSupplierQuotes(@Param("supplierTenantId") Long supplierTenantId,
                                                  @Param("enterpriseId") Long enterpriseId,
                                                  @Param("shopId") Long shopId,
                                                  @Param("status") Integer status,
                                                  @Param("offset") long offset,
                                                  @Param("limit") int limit);

    @Select({"<script>", "SELECT COUNT(1)", HISTORICAL_SUPPLIER_QUOTE_FROM,
            "<if test='status != null'> AND q.status=#{status}</if>", "</script>"})
    long countHistoricalSupplierQuotes(@Param("supplierTenantId") Long supplierTenantId,
                                       @Param("enterpriseId") Long enterpriseId,
                                       @Param("shopId") Long shopId,
                                       @Param("status") Integer status);

    @Select({"<script>", "SELECT q.id AS quoteId,q.tenant_id AS inquiryTenantId,",
            " COALESCE(q.supplier_tenant_id,q.tenant_id) AS supplierTenantId,",
            " q.inquiry_id AS inquiryId,q.enterprise_id AS enterpriseId,q.shop_id AS shopId",
            SUPPLIER_TENANT_QUOTE_FROM, " AND q.id=#{quoteId} LIMIT 1", "</script>"})
    QuoteRef selectSupplierQuoteForTenant(@Param("supplierTenantId") Long supplierTenantId,
                                          @Param("quoteId") Long quoteId);

    @Select({"<script>", "SELECT q.id AS quoteId,q.tenant_id AS inquiryTenantId,",
            " COALESCE(q.supplier_tenant_id,q.tenant_id) AS supplierTenantId,",
            " q.inquiry_id AS inquiryId,q.enterprise_id AS enterpriseId,q.shop_id AS shopId",
            SUPPLIER_QUOTE_FROM, " AND q.id=#{quoteId} LIMIT 1", "</script>"})
    QuoteRef selectSupplierQuote(@Param("supplierTenantId") Long supplierTenantId,
                                 @Param("enterpriseId") Long enterpriseId,
                                 @Param("shopId") Long shopId,
                                 @Param("quoteId") Long quoteId);

    @Select({"<script>", "SELECT q.id AS quoteId,q.tenant_id AS inquiryTenantId,",
            " COALESCE(q.supplier_tenant_id,q.tenant_id) AS supplierTenantId,",
            " q.inquiry_id AS inquiryId,q.enterprise_id AS enterpriseId,q.shop_id AS shopId",
            SUPPLIER_QUOTE_FROM, " AND q.inquiry_id=#{inquiryId} LIMIT 1", "</script>"})
    QuoteRef selectSupplierQuoteByInquiry(@Param("supplierTenantId") Long supplierTenantId,
                                          @Param("enterpriseId") Long enterpriseId,
                                          @Param("shopId") Long shopId,
                                          @Param("inquiryId") Long inquiryId);

    @Select({"<script>",
            "SELECT q.id AS quoteId,q.tenant_id AS inquiryTenantId,",
            " COALESCE(q.supplier_tenant_id,q.tenant_id) AS supplierTenantId,",
            " q.inquiry_id AS inquiryId,q.enterprise_id AS enterpriseId,q.shop_id AS shopId",
            SUPPLIER_QUOTE_FROM,
            "<if test='status != null'> AND q.status=#{status}</if>",
            " ORDER BY q.id DESC LIMIT #{limit} OFFSET #{offset}",
            "</script>"})
    List<QuoteRef> selectSupplierQuotes(@Param("supplierTenantId") Long supplierTenantId,
                                        @Param("enterpriseId") Long enterpriseId,
                                        @Param("shopId") Long shopId,
                                        @Param("status") Integer status,
                                        @Param("offset") long offset,
                                        @Param("limit") int limit);

    @Select({"<script>", "SELECT COUNT(1)", SUPPLIER_QUOTE_FROM,
            "<if test='status != null'> AND q.status=#{status}</if>", "</script>"})
    long countSupplierQuotes(@Param("supplierTenantId") Long supplierTenantId,
                             @Param("enterpriseId") Long enterpriseId,
                             @Param("shopId") Long shopId,
                             @Param("status") Integer status);

    @Data
    class OpportunityRef {
        private Long inquiryId;
        private Long inquiryTenantId;
        private String visibility;
    }

    @Data
    class QuoteRef {
        private Long quoteId;
        private Long inquiryTenantId;
        private Long supplierTenantId;
        private Long inquiryId;
        private Long enterpriseId;
        private Long shopId;
    }
}

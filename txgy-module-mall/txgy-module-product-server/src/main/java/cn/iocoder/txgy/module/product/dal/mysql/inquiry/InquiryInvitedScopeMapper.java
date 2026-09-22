package cn.iocoder.txgy.module.product.dal.mysql.inquiry;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.product.dal.dataobject.inquiry.InquiryInvitedScopeDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 跨租户定向邀请专用 Mapper。
 *
 * <p>该表没有 {@code tenant_id}，因此只允许这里绕过租户插件。所有目标解析 SQL 都同时校验
 * 租户、平台目录、企业及店铺状态，业务层还必须先校验询价归属后才能调用写方法。</p>
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface InquiryInvitedScopeMapper extends BaseMapperX<InquiryInvitedScopeDO> {

    @Select("SELECT id,inquiry_id,target_type,target_tenant_id,target_enterprise_id,target_shop_id"
            + " FROM product_purchase_inquiry_invited_scope"
            + " WHERE inquiry_id=#{inquiryId} AND deleted=b'0'"
            + " ORDER BY target_tenant_id,target_type,target_enterprise_id,target_shop_id,id")
    List<InquiryInvitedScopeDO> selectActiveByInquiryId(@Param("inquiryId") Long inquiryId);

    @Select("SELECT id FROM product_purchase_inquiry_invited_scope"
            + " WHERE inquiry_id=#{inquiryId} AND target_type=#{targetType}"
            + " AND target_tenant_id=#{targetTenantId}"
            + " AND target_enterprise_id &lt;=&gt; #{targetEnterpriseId}"
            + " AND target_shop_id &lt;=&gt; #{targetShopId}"
            + " ORDER BY deleted ASC,id ASC LIMIT 1")
    Long selectExistingId(@Param("inquiryId") Long inquiryId,
                          @Param("targetType") String targetType,
                          @Param("targetTenantId") Long targetTenantId,
                          @Param("targetEnterpriseId") Long targetEnterpriseId,
                          @Param("targetShopId") Long targetShopId);

    @Update("UPDATE product_purchase_inquiry_invited_scope SET deleted=b'1',update_time=NOW()"
            + " WHERE inquiry_id=#{inquiryId} AND deleted=b'0'")
    int deactivateByInquiryId(@Param("inquiryId") Long inquiryId);

    @Update("UPDATE product_purchase_inquiry_invited_scope SET deleted=b'0',update_time=NOW()"
            + " WHERE id=#{id} AND inquiry_id=#{inquiryId}")
    int activateById(@Param("id") Long id, @Param("inquiryId") Long inquiryId);

    @Select({"<script>",
            "SELECT pt.tenant_id AS tenantId,pt.tenant_name AS tenantName,",
            " pe.id AS enterpriseId,pe.name AS enterpriseName,",
            " ps.id AS shopId,ps.name AS shopName",
            " FROM mall_portal_tenant pt",
            " JOIN system_tenant st ON st.id=pt.tenant_id",
            " LEFT JOIN product_enterprise pe ON pe.tenant_id=pt.tenant_id",
            "  AND pe.id=#{targetEnterpriseId} AND pe.deleted=b'0' AND pe.audit_status=1",
            " LEFT JOIN product_shop ps ON ps.tenant_id=pt.tenant_id",
            "  AND ps.id=#{targetShopId} AND ps.enterprise_id=pe.id",
            "  AND ps.deleted=b'0' AND ps.status=0",
            " WHERE pt.tenant_id=#{targetTenantId} AND pt.tenant_id&lt;&gt;3000",
            " AND pt.deleted=b'0' AND pt.open_status=0",
            " AND st.deleted=b'0' AND st.status=0",
            " AND (st.expire_time IS NULL OR st.expire_time&gt;NOW())",
            "<choose>",
            " <when test=\"targetType == 'TENANT'\">",
            "  AND #{targetEnterpriseId} IS NULL AND #{targetShopId} IS NULL",
            " </when>",
            " <when test=\"targetType == 'ENTERPRISE'\">",
            "  AND pe.id IS NOT NULL AND #{targetShopId} IS NULL",
            " </when>",
            " <when test=\"targetType == 'SHOP'\">",
            "  AND pe.id IS NOT NULL AND ps.id IS NOT NULL",
            " </when>",
            " <otherwise>AND 1=0</otherwise>",
            "</choose>",
            " LIMIT 1",
            "</script>"})
    InvitationTargetRow selectEligibleTarget(@Param("targetType") String targetType,
                                              @Param("targetTenantId") Long targetTenantId,
                                              @Param("targetEnterpriseId") Long targetEnterpriseId,
                                              @Param("targetShopId") Long targetShopId);

    /**
     * 查询当前采购方可定向邀请的跨租户店铺。返回列必须保持为公开编号和名称；禁止在此目录中
     * 追加联系人、资质附件、库存或其他经营数据。
     */
    @Select({"<script>",
            "SELECT pt.tenant_id AS tenantId,pt.tenant_name AS tenantName,",
            " pe.id AS enterpriseId,pe.name AS enterpriseName,",
            " ps.id AS shopId,ps.name AS shopName",
            " FROM mall_portal_tenant pt",
            " JOIN system_tenant st ON st.id=pt.tenant_id",
            " JOIN product_enterprise pe ON pe.tenant_id=pt.tenant_id",
            "  AND pe.deleted=b'0' AND pe.audit_status=1",
            " JOIN product_shop ps ON ps.tenant_id=pt.tenant_id",
            "  AND ps.enterprise_id=pe.id AND ps.deleted=b'0' AND ps.status=0",
            " WHERE pt.tenant_id&lt;&gt;#{sourceTenantId} AND pt.tenant_id&lt;&gt;3000",
            " AND pt.deleted=b'0' AND pt.open_status=0",
            " AND st.deleted=b'0' AND st.status=0",
            " AND (st.expire_time IS NULL OR st.expire_time&gt;NOW())",
            "<if test=\"keyword != null and keyword != ''\">",
            " AND (pt.tenant_name LIKE CONCAT('%',#{keyword},'%')",
            "  OR pe.name LIKE CONCAT('%',#{keyword},'%')",
            "  OR ps.name LIKE CONCAT('%',#{keyword},'%'))",
            "</if>",
            " ORDER BY pt.sort,pt.tenant_id,pe.name,ps.name,ps.id",
            " LIMIT #{limit}",
            "</script>"})
    List<InvitationTargetRow> selectEligibleShopOptions(@Param("sourceTenantId") Long sourceTenantId,
                                                         @Param("keyword") String keyword,
                                                         @Param("limit") int limit);

    @Data
    class InvitationTargetRow {
        private Long tenantId;
        private String tenantName;
        private Long enterpriseId;
        private String enterpriseName;
        private Long shopId;
        private String shopName;
    }
}

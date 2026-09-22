package cn.iocoder.txgy.module.member.dal.mysql.platform;

import cn.iocoder.txgy.module.member.api.platform.dto.MemberPlatformAddressRespDTO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 平台账号地址兼容查询。旧地址仍按经营租户存放，查询先由服务层用令牌中的旧会员和源租户
 * 解析平台账号，再只允许读取该平台账号任一 READY 旧会员映射下的地址。
 */
@Mapper
public interface MemberPlatformAddressMapper {

    @InterceptorIgnore(tenantLine = "true")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT owner_link.platform_account_id AS platformAccountId,
               a.user_id AS legacyMemberId,
               a.tenant_id AS sourceTenantId,
               a.id AS addressId,
               a.name,
               a.mobile,
               a.area_id AS areaId,
               a.detail_address AS detailAddress,
               a.longitude,
               a.latitude,
               a.coord_system AS coordSystem
        FROM member_address a
        JOIN member_platform_account_link owner_link
          ON owner_link.legacy_member_id=a.user_id AND owner_link.source_tenant_id=a.tenant_id
          AND owner_link.link_status='READY' AND owner_link.deleted=FALSE
        JOIN member_platform_account pa ON pa.id=owner_link.platform_account_id
          AND pa.status=0 AND pa.deleted=FALSE
        JOIN member_platform_account_link requester_link
          ON requester_link.platform_account_id=pa.id
          AND requester_link.legacy_member_id=#{legacyMemberId}
          AND requester_link.source_tenant_id=#{sourceTenantId}
          AND requester_link.link_status='READY' AND requester_link.deleted=FALSE
        JOIN member_user requester_user ON requester_user.id=requester_link.legacy_member_id
          AND requester_user.tenant_id=requester_link.source_tenant_id
          AND requester_user.status=0 AND requester_user.deleted=FALSE
        JOIN member_user owner_user ON owner_user.id=owner_link.legacy_member_id
          AND owner_user.tenant_id=owner_link.source_tenant_id
          AND owner_user.status=0 AND owner_user.deleted=FALSE
        WHERE owner_link.platform_account_id=#{platformAccountId}
          AND a.id=#{addressId} AND a.deleted=FALSE
          AND requester_link.source_tenant_id<>3000 AND owner_link.source_tenant_id<>3000
          AND (owner_link.id=requester_link.id OR (
            owner_link.link_origin='MANUAL_IDENTITY_REVIEW'
            AND requester_link.link_origin='MANUAL_IDENTITY_REVIEW'
            AND NULLIF(TRIM(owner_link.identity_review_no),'') IS NOT NULL
            AND owner_link.identity_review_no=requester_link.identity_review_no
            AND NULLIF(TRIM(owner_link.identity_verified_by),'') IS NOT NULL
            AND NULLIF(TRIM(requester_link.identity_verified_by),'') IS NOT NULL
            AND owner_link.identity_verified_at IS NOT NULL
            AND requester_link.identity_verified_at IS NOT NULL
          ))
        LIMIT 1
        """)
    MemberPlatformAddressRespDTO selectLinkedAddress(
            @Param("platformAccountId") Long platformAccountId,
            @Param("legacyMemberId") Long legacyMemberId,
            @Param("sourceTenantId") Long sourceTenantId,
            @Param("addressId") Long addressId);
}

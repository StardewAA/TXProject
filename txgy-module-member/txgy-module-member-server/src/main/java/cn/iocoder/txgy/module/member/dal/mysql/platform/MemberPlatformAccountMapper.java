package cn.iocoder.txgy.module.member.dal.mysql.platform;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.member.dal.dataobject.platform.MemberPlatformAccountDO;
import cn.iocoder.txgy.module.member.service.platform.MemberPlatformLoginCandidate;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface MemberPlatformAccountMapper extends BaseMapperX<MemberPlatformAccountDO> {

    /**
     * 平台表不属于任何经营租户；查询必须同时绑定令牌中的会员和源租户，并验证会员仍有效。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT pa.*
        FROM member_platform_account_link l
        JOIN member_platform_account pa ON pa.id=l.platform_account_id
          AND pa.status=0 AND pa.deleted=FALSE
        JOIN member_user u ON u.id=l.legacy_member_id AND u.tenant_id=l.source_tenant_id
          AND u.status=0 AND u.deleted=FALSE
        WHERE l.legacy_member_id=#{legacyMemberId} AND l.source_tenant_id=#{sourceTenantId}
          AND l.link_status='READY' AND l.deleted=FALSE
        LIMIT 1
        """)
    MemberPlatformAccountDO selectReadyByLegacySession(@Param("legacyMemberId") Long legacyMemberId,
                                                        @Param("sourceTenantId") Long sourceTenantId);

    @InterceptorIgnore(tenantLine = "true")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT COUNT(*)
        FROM member_user u
        WHERE u.deleted=FALSE AND u.tenant_id<>3000
          AND ((#{credentialType}='LOGIN_NAME' AND LOWER(TRIM(u.login_name))=#{identifier})
            OR (#{credentialType}='MOBILE' AND TRIM(u.mobile)=#{identifier}))
        """)
    long countMembersByCredential(@Param("credentialType") String credentialType,
                                  @Param("identifier") String identifier);

    @InterceptorIgnore(tenantLine = "true")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT COUNT(*)
        FROM member_user u
        JOIN member_platform_account_link l ON l.legacy_member_id=u.id
          AND l.source_tenant_id=u.tenant_id AND l.deleted=FALSE AND l.link_status='READY'
        WHERE l.platform_account_id=#{platformAccountId} AND u.deleted=FALSE AND u.tenant_id<>3000
          AND ((#{credentialType}='LOGIN_NAME' AND LOWER(TRIM(u.login_name))=#{identifier})
            OR (#{credentialType}='MOBILE' AND TRIM(u.mobile)=#{identifier}))
        """)
    long countReadyMembersByCredential(@Param("platformAccountId") Long platformAccountId,
                                       @Param("credentialType") String credentialType,
                                       @Param("identifier") String identifier);

    @InterceptorIgnore(tenantLine = "true")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT COUNT(DISTINCT u.password)
        FROM member_user u
        JOIN member_platform_account_link l ON l.legacy_member_id=u.id
          AND l.source_tenant_id=u.tenant_id AND l.deleted=FALSE AND l.link_status='READY'
        WHERE l.platform_account_id=#{platformAccountId} AND u.deleted=FALSE AND u.tenant_id<>3000
          AND ((#{credentialType}='LOGIN_NAME' AND LOWER(TRIM(u.login_name))=#{identifier})
            OR (#{credentialType}='MOBILE' AND TRIM(u.mobile)=#{identifier}))
        """)
    long countReadyCredentialHashes(@Param("platformAccountId") Long platformAccountId,
                                    @Param("credentialType") String credentialType,
                                    @Param("identifier") String identifier);

    @InterceptorIgnore(tenantLine = "true")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT pa.id AS platform_account_id,pa.platform_no,#{credentialType} AS credential_type,
               #{identifier} AS credential_identifier,pa.canonical_member_id,l.source_tenant_id
        FROM member_platform_account pa
        JOIN member_platform_account_link l ON l.platform_account_id=pa.id
          AND l.legacy_member_id=pa.canonical_member_id
          AND l.link_status='READY' AND l.deleted=FALSE
        JOIN member_user u ON u.id=l.legacy_member_id AND u.tenant_id=l.source_tenant_id
          AND u.deleted=FALSE
        WHERE ((#{credentialType}='LOGIN_NAME' AND pa.login_name=#{identifier})
            OR (#{credentialType}='MOBILE' AND pa.mobile=#{identifier}))
          AND pa.status=0 AND pa.deleted=FALSE AND l.source_tenant_id<>3000
        ORDER BY pa.id
        LIMIT 2
        """)
    List<MemberPlatformLoginCandidate> selectLoginCandidates(@Param("credentialType") String credentialType,
                                                              @Param("identifier") String identifier);

    @InterceptorIgnore(tenantLine = "true")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT pa.*
        FROM member_platform_account_link l
        JOIN member_platform_account pa ON pa.id=l.platform_account_id
          AND pa.status=0 AND pa.deleted=FALSE
        WHERE l.legacy_member_id=#{legacyMemberId} AND l.source_tenant_id=#{sourceTenantId}
          AND l.link_status='READY' AND l.deleted=FALSE
        LIMIT 1
        """)
    MemberPlatformAccountDO selectReadyRegistrationLink(@Param("legacyMemberId") Long legacyMemberId,
                                                         @Param("sourceTenantId") Long sourceTenantId);

    @InterceptorIgnore(tenantLine = "true")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT COUNT(*)
        FROM member_platform_account_link
        WHERE legacy_member_id=#{legacyMemberId} AND source_tenant_id=#{sourceTenantId}
          AND deleted=FALSE
        """)
    long countActiveLinksByLegacyMember(@Param("legacyMemberId") Long legacyMemberId,
                                        @Param("sourceTenantId") Long sourceTenantId);

    @InterceptorIgnore(tenantLine = "true")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT COUNT(*)
        FROM member_platform_account_link
        WHERE platform_account_id=#{platformAccountId} AND link_status='READY' AND deleted=FALSE
        """)
    long countReadyLinksByPlatformAccount(@Param("platformAccountId") Long platformAccountId);

    /**
     * Returns {@code 0} only when the READY identity graph is safe to resolve.
     *
     * <p>A single legacy member never constitutes an identity merge. Multiple legacy members,
     * however, must all carry the same explicit manual-review number. Matching credentials or
     * password hashes are deliberately not treated as proof that two tenant members are the same
     * natural person.</p>
     */
    @InterceptorIgnore(tenantLine = "true")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT CASE
          WHEN COUNT(*)=1 THEN 0
          WHEN COUNT(*)>1
            AND SUM(CASE WHEN link_origin='MANUAL_IDENTITY_REVIEW'
                           AND NULLIF(TRIM(identity_review_no),'') IS NOT NULL
                           AND NULLIF(TRIM(identity_verified_by),'') IS NOT NULL
                           AND identity_verified_at IS NOT NULL
                         THEN 0 ELSE 1 END)=0
            AND COUNT(DISTINCT identity_review_no)=1 THEN 0
          ELSE 1
        END
        FROM member_platform_account_link
        WHERE platform_account_id=#{platformAccountId} AND link_status='READY'
          AND source_tenant_id<>3000 AND deleted=FALSE
        """)
    int countUnsafeReadyIdentityGraph(@Param("platformAccountId") Long platformAccountId);

    @InterceptorIgnore(tenantLine = "true")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT COUNT(*)
        FROM member_user u
        WHERE u.deleted=FALSE AND u.tenant_id<>3000 AND TRIM(u.mobile)=#{mobile}
          AND NOT (u.id=#{legacyMemberId} AND u.tenant_id=#{sourceTenantId})
        """)
    long countOtherMembersByMobile(@Param("mobile") String mobile,
                                   @Param("legacyMemberId") Long legacyMemberId,
                                   @Param("sourceTenantId") Long sourceTenantId);

    @InterceptorIgnore(tenantLine = "true")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT COUNT(*)
        FROM member_platform_account
        WHERE id<>#{platformAccountId} AND mobile=#{mobile}
        """)
    long countOtherPlatformAccountsByMobile(@Param("platformAccountId") Long platformAccountId,
                                             @Param("mobile") String mobile);

    @InterceptorIgnore(tenantLine = "true")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Update("""
        UPDATE member_platform_account
        SET mobile=#{newMobile},
            credential_identifier=CASE WHEN credential_type='MOBILE' THEN #{newMobile}
                                       ELSE credential_identifier END,
            updater='member-profile',update_time=NOW()
        WHERE id=#{platformAccountId} AND status=0 AND deleted=FALSE
          AND ((mobile IS NULL AND #{oldMobile} IS NULL) OR mobile=#{oldMobile})
          AND (credential_type<>'MOBILE' OR credential_identifier=#{oldMobile})
        """)
    int updateMobileAlias(@Param("platformAccountId") Long platformAccountId,
                          @Param("oldMobile") String oldMobile,
                          @Param("newMobile") String newMobile);

    @InterceptorIgnore(tenantLine = "true")
    @Insert("""
        INSERT INTO member_platform_account
          (platform_no,credential_type,credential_identifier,login_name,mobile,canonical_member_id,status,
           creator,create_time,updater,update_time,deleted)
        VALUES
          (#{platformNo},#{credentialType},#{credentialIdentifier},#{loginName},#{mobile},
           #{canonicalMemberId},#{status},
           'mall-registration',NOW(),'mall-registration',NOW(),FALSE)
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id", flushCache = Options.FlushCachePolicy.TRUE)
    int insertRegistrationAccount(MemberPlatformAccountDO account);

    @InterceptorIgnore(tenantLine = "true")
    @Insert("""
        INSERT INTO member_platform_account_link
          (platform_account_id,legacy_member_id,source_tenant_id,link_status,conflict_reason,
           link_origin,identity_review_no,identity_verified_by,identity_verified_at,
           creator,create_time,updater,update_time,deleted)
        VALUES
          (#{platformAccountId},#{legacyMemberId},#{sourceTenantId},'READY',NULL,
           'SELF_REGISTRATION',NULL,'mall-registration',NOW(),
           'mall-registration',NOW(),'mall-registration',NOW(),FALSE)
        """)
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    int insertRegistrationLink(@Param("platformAccountId") Long platformAccountId,
                               @Param("legacyMemberId") Long legacyMemberId,
                               @Param("sourceTenantId") Long sourceTenantId);
}

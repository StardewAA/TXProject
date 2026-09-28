package cn.iocoder.txgy.module.member.dal.mysql.platform;

import cn.iocoder.txgy.module.member.dal.dataobject.platform.MemberPlatformAccountDO;
import cn.iocoder.txgy.module.member.dal.dataobject.platform.MemberPlatformAccountLinkDO;
import cn.iocoder.txgy.module.member.dal.dataobject.platform.MemberPlatformAccountReviewLinkRow;
import cn.iocoder.txgy.module.member.dal.dataobject.platform.MemberPlatformIdentityReviewDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface MemberPlatformIdentityReviewMapper {

    @InterceptorIgnore(tenantLine = "true")
    @Select({
            "<script>",
            "SELECT COUNT(*) FROM member_platform_account_link l",
            "WHERE l.deleted=FALSE",
            "<if test='linkStatus != null and linkStatus != &quot;&quot;'> AND l.link_status=#{linkStatus}</if>",
            "<if test='platformAccountId != null'> AND l.platform_account_id=#{platformAccountId}</if>",
            "<if test='sourceTenantId != null'> AND l.source_tenant_id=#{sourceTenantId}</if>",
            "<if test='legacyMemberId != null'> AND l.legacy_member_id=#{legacyMemberId}</if>",
            "<if test='reviewNo != null and reviewNo != &quot;&quot;'> AND l.identity_review_no=#{reviewNo}</if>",
            "</script>"
    })
    long countReviewLinks(@Param("linkStatus") String linkStatus,
                          @Param("platformAccountId") Long platformAccountId,
                          @Param("sourceTenantId") Long sourceTenantId,
                          @Param("legacyMemberId") Long legacyMemberId,
                          @Param("reviewNo") String reviewNo);

    /**
     * 密码列有意不出现在 SELECT 中；登录名与手机号也只返回脱敏值。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select({
            "<script>",
            "SELECT l.id AS link_id,l.platform_account_id,pa.platform_no,l.legacy_member_id,l.source_tenant_id,",
            " l.link_status,l.conflict_reason,l.link_origin,l.identity_review_no,l.identity_verified_by,",
            " l.identity_verified_at,u.status AS member_status,l.create_time,l.update_time,",
            " CASE WHEN NULLIF(TRIM(u.login_name),'') IS NULL THEN NULL",
            "      WHEN CHAR_LENGTH(TRIM(u.login_name))=1 THEN '*'",
            "      ELSE CONCAT(LEFT(TRIM(u.login_name),1),'***',RIGHT(TRIM(u.login_name),1)) END AS masked_login_name,",
            " CASE WHEN NULLIF(TRIM(u.mobile),'') IS NULL THEN NULL",
            "      WHEN CHAR_LENGTH(TRIM(u.mobile))&gt;=7 THEN CONCAT(LEFT(TRIM(u.mobile),3),'****',RIGHT(TRIM(u.mobile),4))",
            "      ELSE '***' END AS masked_mobile",
            "FROM member_platform_account_link l",
            "LEFT JOIN member_platform_account pa ON pa.id=l.platform_account_id AND pa.deleted=FALSE",
            "LEFT JOIN member_user u ON u.id=l.legacy_member_id AND u.tenant_id=l.source_tenant_id AND u.deleted=FALSE",
            "WHERE l.deleted=FALSE",
            "<if test='linkStatus != null and linkStatus != &quot;&quot;'> AND l.link_status=#{linkStatus}</if>",
            "<if test='platformAccountId != null'> AND l.platform_account_id=#{platformAccountId}</if>",
            "<if test='sourceTenantId != null'> AND l.source_tenant_id=#{sourceTenantId}</if>",
            "<if test='legacyMemberId != null'> AND l.legacy_member_id=#{legacyMemberId}</if>",
            "<if test='reviewNo != null and reviewNo != &quot;&quot;'> AND l.identity_review_no=#{reviewNo}</if>",
            "ORDER BY l.update_time DESC,l.id DESC LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    List<MemberPlatformAccountReviewLinkRow> selectReviewLinks(
            @Param("linkStatus") String linkStatus,
            @Param("platformAccountId") Long platformAccountId,
            @Param("sourceTenantId") Long sourceTenantId,
            @Param("legacyMemberId") Long legacyMemberId,
            @Param("reviewNo") String reviewNo,
            @Param("offset") long offset,
            @Param("limit") int limit);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT * FROM member_platform_account
            WHERE id=#{platformAccountId} AND status=0 AND deleted=FALSE
            FOR UPDATE
            """)
    MemberPlatformAccountDO selectActiveAccountForUpdate(@Param("platformAccountId") Long platformAccountId);

    @InterceptorIgnore(tenantLine = "true")
    @Select({
            "<script>",
            "SELECT * FROM member_platform_account_link",
            "WHERE deleted=FALSE AND id IN",
            "<foreach collection='linkIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "ORDER BY id FOR UPDATE",
            "</script>"
    })
    List<MemberPlatformAccountLinkDO> selectLinksForUpdate(@Param("linkIds") Collection<Long> linkIds);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT * FROM member_platform_account_link
            WHERE platform_account_id=#{platformAccountId} AND link_status='READY' AND deleted=FALSE
            ORDER BY id FOR UPDATE
            """)
    List<MemberPlatformAccountLinkDO> selectReadyLinksForUpdate(
            @Param("platformAccountId") Long platformAccountId);

    @InterceptorIgnore(tenantLine = "true")
    @Select({
            "<script>",
            "SELECT COUNT(*) FROM member_platform_account_link l",
            "JOIN member_user u ON u.id=l.legacy_member_id AND u.tenant_id=l.source_tenant_id",
            "WHERE l.deleted=FALSE AND u.deleted=FALSE AND u.status=0 AND u.tenant_id&lt;&gt;3000 AND l.id IN",
            "<foreach collection='linkIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    long countActiveMembersForLinks(@Param("linkIds") Collection<Long> linkIds);

    /**
     * Locks every active member that will participate in an identity graph. The merge transaction
     * must hold these rows until commit so a concurrent disable/delete cannot invalidate the
     * human-review decision between validation and link update.
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select({
            "<script>",
            "SELECT u.id FROM member_user u",
            "JOIN member_platform_account_link l ON l.legacy_member_id=u.id AND l.source_tenant_id=u.tenant_id",
            "WHERE l.deleted=FALSE AND u.deleted=FALSE AND u.status=0 AND u.tenant_id&lt;&gt;3000 AND l.id IN",
            "<foreach collection='linkIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "ORDER BY u.id FOR UPDATE",
            "</script>"
    })
    List<Long> lockActiveMembersForLinks(@Param("linkIds") Collection<Long> linkIds);

    @InterceptorIgnore(tenantLine = "true")
    @Update({
            "<script>",
            "UPDATE member_platform_account_link",
            "SET platform_account_id=#{platformAccountId},link_status='READY',conflict_reason=NULL,",
            " link_origin='MANUAL_IDENTITY_REVIEW',identity_review_no=#{reviewNo},",
            " identity_verified_by=#{verifiedBy},identity_verified_at=#{verifiedAt},",
            " updater=#{verifiedBy},update_time=NOW()",
            "WHERE deleted=FALSE AND source_tenant_id&lt;&gt;3000 AND platform_account_id IS NULL",
            " AND link_status IN ('IDENTITY_CONFLICT','CREDENTIAL_CONFLICT') AND id IN",
            "<foreach collection='linkIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    int attachConflictLinks(@Param("platformAccountId") Long platformAccountId,
                            @Param("linkIds") Collection<Long> linkIds,
                            @Param("reviewNo") String reviewNo,
                            @Param("verifiedBy") String verifiedBy,
                            @Param("verifiedAt") LocalDateTime verifiedAt);

    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE member_platform_account_link
            SET link_origin='MANUAL_IDENTITY_REVIEW',identity_review_no=#{reviewNo},
                identity_verified_by=#{verifiedBy},identity_verified_at=#{verifiedAt},
                updater=#{verifiedBy},update_time=NOW()
            WHERE platform_account_id=#{platformAccountId} AND link_status='READY'
              AND source_tenant_id<>3000 AND deleted=FALSE
            """)
    int markReadyGraphReviewed(@Param("platformAccountId") Long platformAccountId,
                               @Param("reviewNo") String reviewNo,
                               @Param("verifiedBy") String verifiedBy,
                               @Param("verifiedAt") LocalDateTime verifiedAt);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT id,review_no,target_platform_account_id,selected_link_ids,link_snapshot_json,
                   review_note,operator_user_id,operator_tenant_id,status,applied_at,create_time
            FROM member_platform_account_identity_review
            WHERE review_no=#{reviewNo}
            LIMIT 1
            """)
    MemberPlatformIdentityReviewDO selectReviewByNo(@Param("reviewNo") String reviewNo);

    @InterceptorIgnore(tenantLine = "true")
    @Insert("""
            INSERT INTO member_platform_account_identity_review
              (review_no,target_platform_account_id,selected_link_ids,link_snapshot_json,review_note,
               operator_user_id,operator_tenant_id,status,applied_at,creator,create_time,updater,update_time,deleted)
            VALUES
              (#{reviewNo},#{targetPlatformAccountId},#{selectedLinkIds},#{linkSnapshotJson},#{reviewNote},
               #{operatorUserId},#{operatorTenantId},#{status},#{appliedAt},
               CONCAT('admin:',#{operatorUserId}),NOW(),CONCAT('admin:',#{operatorUserId}),NOW(),FALSE)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", flushCache = Options.FlushCachePolicy.TRUE)
    int insertReview(MemberPlatformIdentityReviewDO review);
}

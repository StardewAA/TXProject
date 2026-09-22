package cn.iocoder.txgy.module.member.service.platform;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.controller.admin.platform.vo.MemberPlatformAccountLinkPageReqVO;
import cn.iocoder.txgy.module.member.controller.admin.platform.vo.MemberPlatformAccountMergeReqVO;
import cn.iocoder.txgy.module.member.dal.dataobject.platform.MemberPlatformAccountDO;
import cn.iocoder.txgy.module.member.dal.dataobject.platform.MemberPlatformAccountLinkDO;
import cn.iocoder.txgy.module.member.dal.dataobject.platform.MemberPlatformAccountReviewLinkRow;
import cn.iocoder.txgy.module.member.dal.dataobject.platform.MemberPlatformIdentityReviewDO;
import cn.iocoder.txgy.module.member.dal.mysql.platform.MemberPlatformAccountMapper;
import cn.iocoder.txgy.module.member.dal.mysql.platform.MemberPlatformIdentityReviewMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.PLATFORM_ACCOUNT_REVIEW_CONFLICT;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.PLATFORM_ACCOUNT_REVIEW_FORBIDDEN;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.PLATFORM_ACCOUNT_REVIEW_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MemberPlatformIdentityReviewServiceTest {

    private final MemberPlatformIdentityReviewMapper reviewMapper =
            mock(MemberPlatformIdentityReviewMapper.class);
    private final MemberPlatformAccountMapper accountMapper = mock(MemberPlatformAccountMapper.class);
    private final MemberPlatformIdentityReviewServiceImpl service = new MemberPlatformIdentityReviewServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "reviewMapper", reviewMapper);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void platformTenantIsMandatoryForReadAndMerge() {
        TenantContextHolder.setTenantId(2009L);

        ServiceException pageError = assertThrows(ServiceException.class,
                () -> service.getLinkPage(new MemberPlatformAccountLinkPageReqVO()));
        ServiceException mergeError = assertThrows(ServiceException.class,
                () -> service.merge(request(8L, List.of(12L), "REV-20260921-001"), 1L));

        assertEquals(PLATFORM_ACCOUNT_REVIEW_FORBIDDEN.getCode(), pageError.getCode());
        assertEquals(PLATFORM_ACCOUNT_REVIEW_FORBIDDEN.getCode(), mergeError.getCode());
        verifyNoInteractions(reviewMapper, accountMapper);
    }

    @Test
    void pageReturnsOnlyMapperProjectionAndNeverReadsPassword() {
        MemberPlatformAccountLinkPageReqVO request = new MemberPlatformAccountLinkPageReqVO();
        request.setPageNo(1);
        request.setPageSize(20);
        MemberPlatformAccountReviewLinkRow row = new MemberPlatformAccountReviewLinkRow();
        row.setLinkId(12L);
        row.setLegacyMemberId(82L);
        row.setSourceTenantId(2009L);
        row.setLinkStatus("IDENTITY_CONFLICT");
        row.setMaskedLoginName("b***1");
        row.setMaskedMobile("139****9991");
        when(reviewMapper.countReviewLinks("IDENTITY_CONFLICT", null, null, null, null)).thenReturn(1L);
        when(reviewMapper.selectReviewLinks("IDENTITY_CONFLICT", null, null, null, null, 0L, 20))
                .thenReturn(List.of(row));

        var result = service.getLinkPage(request);

        assertEquals(1L, result.getTotal());
        assertEquals("139****9991", result.getList().get(0).getMaskedMobile());
        assertEquals("b***1", result.getList().get(0).getMaskedLoginName());
    }

    @Test
    void mergeAttachesConflictAndMarksWholeGraphWithOneManualReview() {
        stubSuccessfulMerge("REV-20260921-001");

        var result = service.merge(request(8L, List.of(12L), "REV-20260921-001"), 99L);

        assertEquals(8L, result.getTargetPlatformAccountId());
        assertEquals(2, result.getReadyLinkCount());
        assertFalse(result.getIdempotentReplay());
        verify(reviewMapper).attachConflictLinks(eq(8L), eq(List.of(12L)), eq("REV-20260921-001"),
                eq("admin:99"), any(LocalDateTime.class));
        verify(reviewMapper).markReadyGraphReviewed(eq(8L), eq("REV-20260921-001"),
                eq("admin:99"), any(LocalDateTime.class));
        verify(reviewMapper).insertReview(org.mockito.ArgumentMatchers.argThat(audit ->
                audit.getLinkSnapshotJson().contains("IDENTITY_CONFLICT")
                        && !audit.getLinkSnapshotJson().toLowerCase().contains("password")
                        && "12".equals(audit.getSelectedLinkIds())
                        && Long.valueOf(1L).equals(audit.getOperatorTenantId())));
    }

    @Test
    void sameReviewAndPayloadIsIdempotentOnlyWhileReviewedGraphStillMatches() {
        MemberPlatformIdentityReviewDO audit = new MemberPlatformIdentityReviewDO();
        audit.setReviewNo("REV-20260921-001");
        audit.setTargetPlatformAccountId(8L);
        audit.setSelectedLinkIds("12");
        audit.setReviewNote("已通过线下证件与企业授权材料进行双人复核");
        audit.setStatus("APPLIED");
        audit.setAppliedAt(LocalDateTime.of(2026, 9, 21, 12, 0));
        when(reviewMapper.selectActiveAccountForUpdate(8L)).thenReturn(account());
        when(reviewMapper.selectReviewByNo("REV-20260921-001")).thenReturn(audit);
        when(reviewMapper.selectReadyLinksForUpdate(8L)).thenReturn(List.of(
                reviewedReady(11L, 81L, 1L, "REV-20260921-001", "admin:99"),
                reviewedReady(12L, 82L, 2009L, "REV-20260921-001", "admin:99")));
        when(reviewMapper.lockActiveMembersForLinks(List.of(11L, 12L))).thenReturn(List.of(81L, 82L));

        var result = service.merge(request(8L, List.of(12L), "REV-20260921-001"), 100L);

        assertTrue(result.getIdempotentReplay());
        assertEquals(2, result.getReadyLinkCount());
        verify(reviewMapper, never()).attachConflictLinks(any(), anyList(), any(), any(), any());
        verify(reviewMapper, never()).insertReview(any());
    }

    @Test
    void reusedReviewNumberForDifferentTargetOrLinksFailsClosed() {
        MemberPlatformIdentityReviewDO audit = new MemberPlatformIdentityReviewDO();
        audit.setReviewNo("REV-20260921-001");
        audit.setTargetPlatformAccountId(9L);
        audit.setSelectedLinkIds("12");
        audit.setReviewNote("已通过线下证件与企业授权材料进行双人复核");
        audit.setStatus("APPLIED");
        when(reviewMapper.selectActiveAccountForUpdate(8L)).thenReturn(account());
        when(reviewMapper.selectReviewByNo("REV-20260921-001")).thenReturn(audit);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.merge(request(8L, List.of(12L), "REV-20260921-001"), 99L));

        assertEquals(PLATFORM_ACCOUNT_REVIEW_CONFLICT.getCode(), error.getCode());
        verify(reviewMapper, never()).attachConflictLinks(any(), anyList(), any(), any(), any());
    }

    @Test
    void reusedReviewNumberWithDifferentEvidenceFailsClosed() {
        MemberPlatformIdentityReviewDO audit = new MemberPlatformIdentityReviewDO();
        audit.setReviewNo("REV-20260921-001");
        audit.setTargetPlatformAccountId(8L);
        audit.setSelectedLinkIds("12");
        audit.setReviewNote("原始人工审核依据摘要，不允许在幂等重放时被替换");
        audit.setStatus("APPLIED");
        when(reviewMapper.selectActiveAccountForUpdate(8L)).thenReturn(account());
        when(reviewMapper.selectReviewByNo("REV-20260921-001")).thenReturn(audit);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.merge(request(8L, List.of(12L), "REV-20260921-001"), 99L));

        assertEquals(PLATFORM_ACCOUNT_REVIEW_CONFLICT.getCode(), error.getCode());
        verify(reviewMapper, never()).selectReadyLinksForUpdate(any());
        verify(reviewMapper, never()).attachConflictLinks(any(), anyList(), any(), any(), any());
    }

    @Test
    void readyLinkOwnedByAnotherPlatformAccountCannotBeMoved() {
        when(reviewMapper.selectActiveAccountForUpdate(8L)).thenReturn(account());
        when(reviewMapper.selectLinksForUpdate(List.of(12L))).thenReturn(List.of(
                ready(12L, 82L, 2009L, 9L)));
        when(reviewMapper.countActiveMembersForLinks(List.of(12L))).thenReturn(1L);
        when(reviewMapper.selectReadyLinksForUpdate(8L)).thenReturn(List.of(
                ready(11L, 81L, 1L, 8L)));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.merge(request(8L, List.of(12L), "REV-20260921-001"), 99L));

        assertEquals(PLATFORM_ACCOUNT_REVIEW_CONFLICT.getCode(), error.getCode());
        verify(reviewMapper, never()).attachConflictLinks(any(), anyList(), any(), any(), any());
    }

    @Test
    void unsafeExistingTargetGraphCannotBeLegitimizedAsSideEffect() {
        when(reviewMapper.selectActiveAccountForUpdate(8L)).thenReturn(account());
        when(reviewMapper.selectLinksForUpdate(List.of(12L))).thenReturn(List.of(
                conflict(12L, 82L, 2009L)));
        when(reviewMapper.countActiveMembersForLinks(List.of(12L))).thenReturn(1L);
        when(reviewMapper.selectReadyLinksForUpdate(8L)).thenReturn(List.of(
                ready(10L, 80L, 2008L, 8L),
                ready(11L, 81L, 1L, 8L)));
        when(accountMapper.countUnsafeReadyIdentityGraph(8L)).thenReturn(1);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.merge(request(8L, List.of(12L), "REV-20260921-001"), 99L));

        assertEquals(PLATFORM_ACCOUNT_REVIEW_CONFLICT.getCode(), error.getCode());
        verify(reviewMapper, never()).markReadyGraphReviewed(any(), any(), any(), any());
        verify(reviewMapper, never()).insertReview(any());
    }

    @Test
    void tenant3000LinkCanNeverBecomeReady() {
        when(reviewMapper.selectActiveAccountForUpdate(8L)).thenReturn(account());
        when(reviewMapper.selectLinksForUpdate(List.of(12L))).thenReturn(List.of(
                conflict(12L, 82L, 3000L)));
        when(reviewMapper.countActiveMembersForLinks(List.of(12L))).thenReturn(1L);
        when(reviewMapper.selectReadyLinksForUpdate(8L)).thenReturn(List.of(
                ready(11L, 81L, 1L, 8L)));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.merge(request(8L, List.of(12L), "REV-20260921-001"), 99L));

        assertEquals(PLATFORM_ACCOUNT_REVIEW_CONFLICT.getCode(), error.getCode());
        verify(reviewMapper, never()).attachConflictLinks(any(), anyList(), any(), any(), any());
    }

    @Test
    void duplicateLinkSelectionAndMissingActiveMemberAreRejected() {
        ServiceException duplicate = assertThrows(ServiceException.class,
                () -> service.merge(request(8L, List.of(12L, 12L), "REV-20260921-001"), 99L));
        assertEquals(PLATFORM_ACCOUNT_REVIEW_INVALID.getCode(), duplicate.getCode());

        when(reviewMapper.selectActiveAccountForUpdate(8L)).thenReturn(account());
        when(reviewMapper.selectLinksForUpdate(List.of(12L))).thenReturn(List.of(conflict(12L, 82L, 2009L)));
        when(reviewMapper.countActiveMembersForLinks(List.of(12L))).thenReturn(0L);
        ServiceException missing = assertThrows(ServiceException.class,
                () -> service.merge(request(8L, List.of(12L), "REV-20260921-002"), 99L));
        assertEquals(PLATFORM_ACCOUNT_REVIEW_INVALID.getCode(), missing.getCode());
    }

    @Test
    void postUpdateUnsafeGraphCausesTransactionToFailClosed() {
        when(reviewMapper.selectActiveAccountForUpdate(8L)).thenReturn(account());
        MemberPlatformAccountLinkDO target = ready(11L, 81L, 1L, 8L);
        MemberPlatformAccountLinkDO conflict = conflict(12L, 82L, 2009L);
        when(reviewMapper.selectLinksForUpdate(List.of(12L))).thenReturn(List.of(conflict));
        when(reviewMapper.countActiveMembersForLinks(List.of(12L))).thenReturn(1L);
        when(reviewMapper.lockActiveMembersForLinks(org.mockito.ArgumentMatchers.argThat(ids -> ids.size() == 2)))
                .thenReturn(List.of(81L, 82L));
        when(reviewMapper.selectReadyLinksForUpdate(8L)).thenReturn(List.of(target), List.of(
                reviewedReady(11L, 81L, 1L, "REV-20260921-001", "admin:99"),
                reviewedReady(12L, 82L, 2009L, "REV-20260921-001", "admin:99")));
        when(reviewMapper.attachConflictLinks(eq(8L), eq(List.of(12L)), any(), any(), any())).thenReturn(1);
        when(accountMapper.countUnsafeReadyIdentityGraph(8L)).thenReturn(1);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.merge(request(8L, List.of(12L), "REV-20260921-001"), 99L));

        assertEquals(PLATFORM_ACCOUNT_REVIEW_CONFLICT.getCode(), error.getCode());
        verify(reviewMapper, never()).insertReview(any());
    }

    @Test
    void newReviewCannotRewriteGraphWithoutAddingAConflictLink() {
        when(reviewMapper.selectActiveAccountForUpdate(8L)).thenReturn(account());
        when(reviewMapper.selectLinksForUpdate(List.of(11L))).thenReturn(List.of(
                ready(11L, 81L, 1L, 8L)));
        when(reviewMapper.countActiveMembersForLinks(List.of(11L))).thenReturn(1L);
        when(reviewMapper.selectReadyLinksForUpdate(8L)).thenReturn(List.of(
                ready(11L, 81L, 1L, 8L)));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.merge(request(8L, List.of(11L), "REV-20260921-999"), 99L));

        assertEquals(PLATFORM_ACCOUNT_REVIEW_CONFLICT.getCode(), error.getCode());
        verify(reviewMapper, never()).markReadyGraphReviewed(any(), any(), any(), any());
    }

    private void stubSuccessfulMerge(String reviewNo) {
        when(reviewMapper.selectActiveAccountForUpdate(8L)).thenReturn(account());
        MemberPlatformAccountLinkDO target = ready(11L, 81L, 1L, 8L);
        MemberPlatformAccountLinkDO conflict = conflict(12L, 82L, 2009L);
        when(reviewMapper.selectLinksForUpdate(List.of(12L))).thenReturn(List.of(conflict));
        when(reviewMapper.countActiveMembersForLinks(List.of(12L))).thenReturn(1L);
        when(reviewMapper.lockActiveMembersForLinks(org.mockito.ArgumentMatchers.argThat(ids -> ids.size() == 2)))
                .thenReturn(List.of(81L, 82L));
        when(reviewMapper.selectReadyLinksForUpdate(8L)).thenReturn(List.of(target), List.of(
                reviewedReady(11L, 81L, 1L, reviewNo, "admin:99"),
                reviewedReady(12L, 82L, 2009L, reviewNo, "admin:99")));
        when(reviewMapper.attachConflictLinks(eq(8L), eq(List.of(12L)), eq(reviewNo),
                eq("admin:99"), any(LocalDateTime.class))).thenReturn(1);
        when(reviewMapper.insertReview(any())).thenReturn(1);
    }

    private static MemberPlatformAccountMergeReqVO request(Long accountId, List<Long> linkIds, String reviewNo) {
        MemberPlatformAccountMergeReqVO request = new MemberPlatformAccountMergeReqVO();
        request.setTargetPlatformAccountId(accountId);
        request.setLinkIds(linkIds);
        request.setReviewNo(reviewNo);
        request.setReviewNote("已通过线下证件与企业授权材料进行双人复核");
        return request;
    }

    private static MemberPlatformAccountDO account() {
        return MemberPlatformAccountDO.builder().id(8L).platformNo("PM0008")
                .canonicalMemberId(81L).status(0).build();
    }

    private static MemberPlatformAccountLinkDO conflict(long id, long memberId, long tenantId) {
        return MemberPlatformAccountLinkDO.builder().id(id).legacyMemberId(memberId)
                .sourceTenantId(tenantId).linkStatus("IDENTITY_CONFLICT")
                .conflictReason("duplicate credential; requires human identity proof").build();
    }

    private static MemberPlatformAccountLinkDO ready(long id, long memberId, long tenantId, long accountId) {
        return MemberPlatformAccountLinkDO.builder().id(id).legacyMemberId(memberId)
                .sourceTenantId(tenantId).platformAccountId(accountId).linkStatus("READY")
                .linkOrigin("SELF_REGISTRATION").build();
    }

    private static MemberPlatformAccountLinkDO reviewedReady(long id, long memberId, long tenantId,
                                                              String reviewNo, String verifiedBy) {
        return MemberPlatformAccountLinkDO.builder().id(id).legacyMemberId(memberId)
                .sourceTenantId(tenantId).platformAccountId(8L).linkStatus("READY")
                .linkOrigin("MANUAL_IDENTITY_REVIEW").identityReviewNo(reviewNo)
                .identityVerifiedBy(verifiedBy).identityVerifiedAt(LocalDateTime.now()).build();
    }
}

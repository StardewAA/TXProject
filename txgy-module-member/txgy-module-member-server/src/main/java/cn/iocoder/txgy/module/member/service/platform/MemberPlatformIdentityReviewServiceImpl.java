package cn.iocoder.txgy.module.member.service.platform;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.controller.admin.platform.vo.MemberPlatformAccountLinkPageReqVO;
import cn.iocoder.txgy.module.member.controller.admin.platform.vo.MemberPlatformAccountLinkRespVO;
import cn.iocoder.txgy.module.member.controller.admin.platform.vo.MemberPlatformAccountMergeReqVO;
import cn.iocoder.txgy.module.member.controller.admin.platform.vo.MemberPlatformAccountMergeRespVO;
import cn.iocoder.txgy.module.member.dal.dataobject.platform.MemberPlatformAccountDO;
import cn.iocoder.txgy.module.member.dal.dataobject.platform.MemberPlatformAccountLinkDO;
import cn.iocoder.txgy.module.member.dal.dataobject.platform.MemberPlatformAccountReviewLinkRow;
import cn.iocoder.txgy.module.member.dal.dataobject.platform.MemberPlatformIdentityReviewDO;
import cn.iocoder.txgy.module.member.dal.mysql.platform.MemberPlatformAccountMapper;
import cn.iocoder.txgy.module.member.dal.mysql.platform.MemberPlatformIdentityReviewMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.PLATFORM_ACCOUNT_REVIEW_CONFLICT;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.PLATFORM_ACCOUNT_REVIEW_FORBIDDEN;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.PLATFORM_ACCOUNT_REVIEW_INVALID;

@Service
@Validated
public class MemberPlatformIdentityReviewServiceImpl implements MemberPlatformIdentityReviewService {

    private static final long PLATFORM_TENANT_ID = 1L;
    private static final long QUARANTINED_TENANT_ID = 3000L;
    private static final Set<String> LINK_STATUSES = Set.of(
            "READY", "PENDING", "CREDENTIAL_CONFLICT", "IDENTITY_CONFLICT");
    private static final Set<String> REVIEWABLE_STATUSES = Set.of(
            "CREDENTIAL_CONFLICT", "IDENTITY_CONFLICT");

    @Resource
    private MemberPlatformIdentityReviewMapper reviewMapper;
    @Resource
    private MemberPlatformAccountMapper accountMapper;

    @Override
    public PageResult<MemberPlatformAccountLinkRespVO> getLinkPage(MemberPlatformAccountLinkPageReqVO request) {
        requirePlatformTenant();
        String status = blankToNull(request.getLinkStatus());
        if (status != null && !LINK_STATUSES.contains(status)) {
            throw exception(PLATFORM_ACCOUNT_REVIEW_INVALID);
        }
        String reviewNo = blankToNull(request.getReviewNo());
        long offset = Math.multiplyExact((long) request.getPageNo() - 1L, request.getPageSize().longValue());
        long total = reviewMapper.countReviewLinks(status, positiveOrNull(request.getPlatformAccountId()),
                positiveOrNull(request.getSourceTenantId()), positiveOrNull(request.getLegacyMemberId()), reviewNo);
        if (total == 0L) {
            return PageResult.empty();
        }
        List<MemberPlatformAccountLinkRespVO> list = reviewMapper.selectReviewLinks(status,
                        positiveOrNull(request.getPlatformAccountId()), positiveOrNull(request.getSourceTenantId()),
                        positiveOrNull(request.getLegacyMemberId()), reviewNo, offset, request.getPageSize())
                .stream().map(MemberPlatformIdentityReviewServiceImpl::convert).toList();
        return new PageResult<>(list, total);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberPlatformAccountMergeRespVO merge(MemberPlatformAccountMergeReqVO request, Long operatorUserId) {
        requirePlatformTenant();
        if (operatorUserId == null || operatorUserId <= 0 || request.getTargetPlatformAccountId() == null
                || request.getTargetPlatformAccountId() <= 0) {
            throw exception(PLATFORM_ACCOUNT_REVIEW_INVALID);
        }
        String reviewNo = StrUtil.trim(request.getReviewNo());
        String reviewNote = StrUtil.trim(request.getReviewNote());
        if (StrUtil.isBlank(reviewNo) || !reviewNo.matches("[A-Za-z0-9][A-Za-z0-9._-]{5,63}")
                || StrUtil.length(reviewNote) < 10 || StrUtil.length(reviewNote) > 500) {
            throw exception(PLATFORM_ACCOUNT_REVIEW_INVALID);
        }
        List<Long> requestedIds = normalizeLinkIds(request.getLinkIds());
        String selectedLinkIds = requestedIds.stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));

        // Account row is the serialization point for all merges into the same identity graph.
        MemberPlatformAccountDO account = reviewMapper.selectActiveAccountForUpdate(
                request.getTargetPlatformAccountId());
        if (account == null || account.getCanonicalMemberId() == null || account.getCanonicalMemberId() <= 0) {
            throw exception(PLATFORM_ACCOUNT_REVIEW_INVALID);
        }

        MemberPlatformIdentityReviewDO existingReview = reviewMapper.selectReviewByNo(reviewNo);
        if (existingReview != null) {
            return resolveIdempotentReplay(existingReview, account.getId(), selectedLinkIds, reviewNo, reviewNote);
        }

        List<MemberPlatformAccountLinkDO> selected = reviewMapper.selectLinksForUpdate(requestedIds);
        if (selected.size() != requestedIds.size()
                || reviewMapper.countActiveMembersForLinks(requestedIds) != requestedIds.size()) {
            throw exception(PLATFORM_ACCOUNT_REVIEW_INVALID);
        }
        List<MemberPlatformAccountLinkDO> existingReady = reviewMapper.selectReadyLinksForUpdate(account.getId());
        if (existingReady.isEmpty() || existingReady.stream().noneMatch(link ->
                Objects.equals(link.getLegacyMemberId(), account.getCanonicalMemberId()))) {
            throw exception(PLATFORM_ACCOUNT_REVIEW_INVALID);
        }
        if (accountMapper.countUnsafeReadyIdentityGraph(account.getId()) != 0) {
            // A new review may extend a previously safe graph, but must never legitimize an
            // already-unsafe automatic merge as a side effect.
            throw exception(PLATFORM_ACCOUNT_REVIEW_CONFLICT);
        }

        List<MemberPlatformAccountLinkDO> conflictLinks = new ArrayList<>();
        for (MemberPlatformAccountLinkDO link : selected) {
            requireSafeTenant(link);
            if (REVIEWABLE_STATUSES.contains(link.getLinkStatus()) && link.getPlatformAccountId() == null) {
                conflictLinks.add(link);
            } else if (!Objects.equals(link.getLinkStatus(), "READY")
                    || !Objects.equals(link.getPlatformAccountId(), account.getId())) {
                // A READY link owned by another platform account can never be moved by this workflow.
                throw exception(PLATFORM_ACCOUNT_REVIEW_CONFLICT);
            }
        }
        if (conflictLinks.isEmpty()) {
            // New review numbers may not silently rewrite an already reviewed graph.
            throw exception(PLATFORM_ACCOUNT_REVIEW_CONFLICT);
        }
        existingReady.forEach(MemberPlatformIdentityReviewServiceImpl::requireSafeTenant);

        LinkedHashMap<Long, MemberPlatformAccountLinkDO> graph = new LinkedHashMap<>();
        existingReady.forEach(link -> graph.put(link.getId(), link));
        selected.forEach(link -> graph.put(link.getId(), link));
        if (graph.size() < 2 || reviewMapper.lockActiveMembersForLinks(graph.keySet()).size() != graph.size()) {
            throw exception(PLATFORM_ACCOUNT_REVIEW_INVALID);
        }

        LocalDateTime verifiedAt = LocalDateTime.now();
        String verifiedBy = "admin:" + operatorUserId;
        List<Long> conflictIds = conflictLinks.stream().map(MemberPlatformAccountLinkDO::getId).toList();
        if (reviewMapper.attachConflictLinks(account.getId(), conflictIds, reviewNo, verifiedBy, verifiedAt)
                != conflictIds.size()) {
            throw exception(PLATFORM_ACCOUNT_REVIEW_CONFLICT);
        }
        reviewMapper.markReadyGraphReviewed(account.getId(), reviewNo, verifiedBy, verifiedAt);

        List<MemberPlatformAccountLinkDO> reviewedGraph = reviewMapper.selectReadyLinksForUpdate(account.getId());
        if (reviewedGraph.size() != graph.size() || !isReviewedGraph(reviewedGraph, reviewNo, verifiedBy)
                || accountMapper.countUnsafeReadyIdentityGraph(account.getId()) != 0) {
            throw exception(PLATFORM_ACCOUNT_REVIEW_CONFLICT);
        }

        MemberPlatformIdentityReviewDO audit = new MemberPlatformIdentityReviewDO();
        audit.setReviewNo(reviewNo);
        audit.setTargetPlatformAccountId(account.getId());
        audit.setSelectedLinkIds(selectedLinkIds);
        audit.setLinkSnapshotJson(snapshot(graph.values()));
        audit.setReviewNote(reviewNote);
        audit.setOperatorUserId(operatorUserId);
        audit.setOperatorTenantId(PLATFORM_TENANT_ID);
        audit.setStatus("APPLIED");
        audit.setAppliedAt(verifiedAt);
        try {
            if (reviewMapper.insertReview(audit) != 1) {
                throw exception(PLATFORM_ACCOUNT_REVIEW_CONFLICT);
            }
        } catch (DataIntegrityViolationException ex) {
            // Unique review_no is the final guard for concurrent reuse across target accounts.
            throw exception(PLATFORM_ACCOUNT_REVIEW_CONFLICT);
        }
        return new MemberPlatformAccountMergeRespVO(reviewNo, account.getId(), reviewedGraph.size(),
                verifiedAt, false);
    }

    private MemberPlatformAccountMergeRespVO resolveIdempotentReplay(MemberPlatformIdentityReviewDO audit,
                                                                      Long accountId,
                                                                      String selectedLinkIds,
                                                                      String reviewNo,
                                                                      String reviewNote) {
        if (!Objects.equals(audit.getStatus(), "APPLIED")
                || !Objects.equals(audit.getTargetPlatformAccountId(), accountId)
                || !Objects.equals(audit.getSelectedLinkIds(), selectedLinkIds)
                || !Objects.equals(audit.getReviewNote(), reviewNote)) {
            throw exception(PLATFORM_ACCOUNT_REVIEW_CONFLICT);
        }
        List<MemberPlatformAccountLinkDO> current = reviewMapper.selectReadyLinksForUpdate(accountId);
        if (current.size() < 2 || !isReviewedGraph(current, reviewNo, null)
                || current.stream().anyMatch(link -> Objects.equals(link.getSourceTenantId(), QUARANTINED_TENANT_ID))
                || reviewMapper.lockActiveMembersForLinks(current.stream()
                        .map(MemberPlatformAccountLinkDO::getId).toList()).size() != current.size()
                || accountMapper.countUnsafeReadyIdentityGraph(accountId) != 0) {
            throw exception(PLATFORM_ACCOUNT_REVIEW_CONFLICT);
        }
        return new MemberPlatformAccountMergeRespVO(reviewNo, accountId, current.size(), audit.getAppliedAt(), true);
    }

    private static boolean isReviewedGraph(List<MemberPlatformAccountLinkDO> graph, String reviewNo,
                                           String verifiedBy) {
        return graph.stream().allMatch(link -> Objects.equals(link.getLinkStatus(), "READY")
                && Objects.equals(link.getLinkOrigin(), "MANUAL_IDENTITY_REVIEW")
                && Objects.equals(link.getIdentityReviewNo(), reviewNo)
                && StrUtil.isNotBlank(link.getIdentityVerifiedBy())
                && (verifiedBy == null || Objects.equals(link.getIdentityVerifiedBy(), verifiedBy))
                && link.getIdentityVerifiedAt() != null);
    }

    private static List<Long> normalizeLinkIds(List<Long> values) {
        if (values == null || values.isEmpty() || values.size() > 50
                || values.stream().anyMatch(value -> value == null || value <= 0)) {
            throw exception(PLATFORM_ACCOUNT_REVIEW_INVALID);
        }
        List<Long> sorted = values.stream().sorted().toList();
        if (new LinkedHashSet<>(sorted).size() != sorted.size()) {
            throw exception(PLATFORM_ACCOUNT_REVIEW_INVALID);
        }
        return sorted;
    }

    private static void requireSafeTenant(MemberPlatformAccountLinkDO link) {
        if (link.getSourceTenantId() == null || link.getSourceTenantId() <= 0
                || Objects.equals(link.getSourceTenantId(), QUARANTINED_TENANT_ID)) {
            throw exception(PLATFORM_ACCOUNT_REVIEW_CONFLICT);
        }
    }

    private static String snapshot(java.util.Collection<MemberPlatformAccountLinkDO> links) {
        List<Map<String, Object>> rows = links.stream()
                .sorted(Comparator.comparing(MemberPlatformAccountLinkDO::getId))
                .map(link -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("linkId", link.getId());
                    row.put("legacyMemberId", link.getLegacyMemberId());
                    row.put("sourceTenantId", link.getSourceTenantId());
                    row.put("platformAccountId", link.getPlatformAccountId());
                    row.put("linkStatus", link.getLinkStatus());
                    row.put("conflictReason", StrUtil.maxLength(link.getConflictReason(), 200));
                    return row;
                }).toList();
        return JsonUtils.toJsonString(rows);
    }

    private static MemberPlatformAccountLinkRespVO convert(MemberPlatformAccountReviewLinkRow row) {
        MemberPlatformAccountLinkRespVO response = new MemberPlatformAccountLinkRespVO();
        response.setLinkId(row.getLinkId());
        response.setPlatformAccountId(row.getPlatformAccountId());
        response.setPlatformNo(row.getPlatformNo());
        response.setLegacyMemberId(row.getLegacyMemberId());
        response.setSourceTenantId(row.getSourceTenantId());
        response.setLinkStatus(row.getLinkStatus());
        response.setConflictReason(row.getConflictReason());
        response.setLinkOrigin(row.getLinkOrigin());
        response.setIdentityReviewNo(row.getIdentityReviewNo());
        response.setIdentityVerifiedBy(row.getIdentityVerifiedBy());
        response.setIdentityVerifiedAt(row.getIdentityVerifiedAt());
        response.setMaskedLoginName(row.getMaskedLoginName());
        response.setMaskedMobile(row.getMaskedMobile());
        response.setMemberStatus(row.getMemberStatus());
        response.setCreateTime(row.getCreateTime());
        response.setUpdateTime(row.getUpdateTime());
        return response;
    }

    private static Long positiveOrNull(Long value) {
        if (value == null) {
            return null;
        }
        if (value <= 0) {
            throw exception(PLATFORM_ACCOUNT_REVIEW_INVALID);
        }
        return value;
    }

    private static String blankToNull(String value) {
        String trimmed = StrUtil.trim(value);
        return StrUtil.isBlank(trimmed) ? null : trimmed;
    }

    private static void requirePlatformTenant() {
        if (!Objects.equals(TenantContextHolder.getTenantId(), PLATFORM_TENANT_ID)) {
            throw exception(PLATFORM_ACCOUNT_REVIEW_FORBIDDEN);
        }
    }
}

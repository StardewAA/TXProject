package cn.iocoder.txgy.module.cms.service.validity;

import cn.iocoder.txgy.framework.common.exception.ErrorCode;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.cms.config.CmsLawValidityCheckProperties;
import cn.iocoder.txgy.module.cms.service.knowledge.CmsKnowledgeService;
import cn.iocoder.txgy.module.cms.service.review.ReviewEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;

/** 法律效力核验待办：程序发现风险，人工决定归档或忽略。 */
@Service
@Slf4j
public class CmsLawValidityCheckService {

    private static final ErrorCode TASK_NOT_FOUND =
            new ErrorCode(1_041_004_000, "法律效力核验待办不存在或已处理");

    private final JdbcTemplate jdbc;
    private final CmsLawValidityVerifier verifier;
    private final CmsLawValidityCheckProperties properties;
    private final CmsKnowledgeService knowledgeService;

    public CmsLawValidityCheckService(JdbcTemplate jdbc, CmsLawValidityVerifier verifier,
                                      CmsLawValidityCheckProperties properties,
                                      CmsKnowledgeService knowledgeService) {
        this.jdbc = jdbc;
        this.verifier = verifier;
        this.properties = properties;
        this.knowledgeService = knowledgeService;
    }

    public CheckSummary checkCurrentTenant() {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        List<Candidate> candidates = jdbc.query("""
                        SELECT review_id,title,validity,source_url
                        FROM cms_knowledge_document
                        WHERE tenant_id=? AND published=1 AND deleted_at IS NULL
                          AND source_url LIKE 'https://%.gov.cn/%'
                        ORDER BY created_at ASC
                        LIMIT ?
                        """,
                (rs, row) -> new Candidate(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)),
                tenantId, properties.getBatchSize());
        int issues = 0;
        int recovered = 0;
        for (Candidate candidate : candidates) {
            CmsLawValidityVerifier.Verification verification = verifier.verify(candidate.sourceUrl());
            if (verification.healthy()) {
                recovered += recoverPending(tenantId, candidate.reviewId(), "SOURCE_UNREACHABLE");
                if ("3".equals(verification.remoteStatus())) {
                    recovered += recoverPending(tenantId, candidate.reviewId(), "REMOTE_STATUS_REVIEW");
                    if (!"EFFECTIVE".equals(candidate.validity())) {
                        upsertTask(tenantId, candidate,
                                CmsLawValidityVerifier.IssueType.STATUS_MISMATCH,
                                verification.remoteStatus(), verification.remoteTitle(),
                                "国家法律法规数据库标记为现行有效，但本地效力为 "
                                        + candidate.validity() + "，请人工核对版本");
                        issues++;
                    }
                }
                continue;
            }
            upsertTask(tenantId, candidate, verification.issueType(), verification.remoteStatus(),
                    verification.remoteTitle(), verification.message());
            issues++;
        }
        log.info("[checkCurrentTenant][tenantId={} checked={} issues={} recovered={}]",
                tenantId, candidates.size(), issues, recovered);
        return new CheckSummary(candidates.size(), issues, recovered, LocalDateTime.now());
    }

    public List<ValidityTask> list(String status) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        String resolvedStatus = status == null || status.isBlank() ? "PENDING" : status.strip().toUpperCase();
        return jdbc.query("""
                        SELECT id,review_id,title,source_url,issue_type,current_validity,remote_status,
                               remote_title,message,task_status,occurrence_count,first_detected_at,
                               last_checked_at,resolved_at,resolution_note
                        FROM cms_law_validity_task
                        WHERE tenant_id=? AND task_status=?
                        ORDER BY last_checked_at DESC,id DESC
                        LIMIT 300
                        """, (rs, row) -> new ValidityTask(
                        rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                        rs.getString(9), rs.getString(10), rs.getInt(11),
                        rs.getTimestamp(12).toLocalDateTime(), rs.getTimestamp(13).toLocalDateTime(),
                        rs.getTimestamp(14) == null ? null : rs.getTimestamp(14).toLocalDateTime(),
                        rs.getString(15)), tenantId, resolvedStatus);
    }

    @Transactional(rollbackFor = Exception.class)
    public void dismiss(long taskId, String note) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        int updated = jdbc.update("""
                        UPDATE cms_law_validity_task
                        SET task_status='DISMISSED',resolved_at=CURRENT_TIMESTAMP,resolution_note=?
                        WHERE tenant_id=? AND id=? AND task_status='PENDING'
                        """, normalizeNote(note, "管理员确认暂不归档"), tenantId, taskId);
        if (updated == 0) throw exception(TASK_NOT_FOUND);
    }

    @Transactional(rollbackFor = Exception.class)
    public void archive(long taskId, String note) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        List<String> reviewIds = jdbc.query("""
                        SELECT review_id FROM cms_law_validity_task
                        WHERE tenant_id=? AND id=? AND task_status='PENDING'
                        FOR UPDATE
                        """, (rs, row) -> rs.getString(1), tenantId, taskId);
        if (reviewIds.isEmpty()) throw exception(TASK_NOT_FOUND);
        knowledgeService.archive(reviewIds.getFirst());
        jdbc.update("""
                        UPDATE cms_law_validity_task
                        SET task_status='ARCHIVED',resolved_at=CURRENT_TIMESTAMP,resolution_note=?
                        WHERE tenant_id=? AND id=?
                        """, normalizeNote(note, "管理员确认效力风险并归档"), tenantId, taskId);
    }

    private void upsertTask(long tenantId, Candidate candidate,
                            CmsLawValidityVerifier.IssueType issueType, String remoteStatus,
                            String remoteTitle, String message) {
        String type = issueType == null ? "SOURCE_UNREACHABLE" : issueType.name();
        String eventKey = ReviewEngine.hash(candidate.reviewId() + "|" + type + "|"
                + Objects.toString(remoteStatus, ""));
        jdbc.update("""
                        INSERT INTO cms_law_validity_task
                          (tenant_id,review_id,event_key,title,source_url,issue_type,current_validity,
                           remote_status,remote_title,message,task_status,occurrence_count,
                           first_detected_at,last_checked_at)
                        VALUES (?,?,?,?,?,?,?,?,?,?,'PENDING',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                        ON DUPLICATE KEY UPDATE
                          title=VALUES(title),source_url=VALUES(source_url),current_validity=VALUES(current_validity),
                          remote_title=VALUES(remote_title),message=VALUES(message),
                          occurrence_count=occurrence_count+1,last_checked_at=CURRENT_TIMESTAMP
                        """, tenantId, candidate.reviewId(), eventKey, candidate.title(), candidate.sourceUrl(),
                type, candidate.validity(), remoteStatus, remoteTitle, message);
    }

    private int recoverPending(long tenantId, String reviewId, String issueType) {
        return jdbc.update("""
                        UPDATE cms_law_validity_task
                        SET task_status='RECOVERED',resolved_at=CURRENT_TIMESTAMP,
                            resolution_note='后续定时核验已恢复正常'
                        WHERE tenant_id=? AND review_id=? AND issue_type=? AND task_status='PENDING'
                        """, tenantId, reviewId, issueType);
    }

    private static String normalizeNote(String value, String fallback) {
        String note = value == null ? "" : value.strip();
        if (note.isBlank()) return fallback;
        return note.length() <= 500 ? note : note.substring(0, 500);
    }

    private record Candidate(String reviewId, String title, String validity, String sourceUrl) { }

    public record CheckSummary(int checked, int issues, int recovered, LocalDateTime completedAt) { }

    public record ValidityTask(long id, String reviewId, String title, String sourceUrl,
                               String issueType, String currentValidity, String remoteStatus,
                               String remoteTitle, String message, String taskStatus,
                               int occurrenceCount, LocalDateTime firstDetectedAt,
                               LocalDateTime lastCheckedAt, LocalDateTime resolvedAt,
                               String resolutionNote) { }
}

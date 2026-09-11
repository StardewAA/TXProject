package cn.iocoder.txgy.module.cms.service.review;

import cn.iocoder.txgy.framework.common.exception.ErrorCode;
import cn.iocoder.txgy.module.cms.service.rag.CmsComplianceReviewer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.REVIEW_DUPLICATE;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.REVIEW_NOT_READY;
import static cn.iocoder.txgy.module.cms.service.review.ReviewModels.*;

@Service
public class ReviewService {
    private static final ErrorCode NOT_FOUND = new ErrorCode(1_041_001_000, "审查记录不存在或无权访问");
    private static final ErrorCode INVALID_EVIDENCE = new ErrorCode(1_041_001_002, "请提供官方来源、效力依据、实施日期和核验意见");
    private final ReviewRepository repository;
    private final ReviewEngine engine;
    private ApplicationEventPublisher eventPublisher = event -> { };
    private CmsComplianceReviewer complianceReviewer;

    public ReviewService(ReviewRepository repository, ReviewEngine engine) {
        this.repository = repository;
        this.engine = engine;
    }

    @Autowired
    void setEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Autowired(required=false)
    void setComplianceReviewer(CmsComplianceReviewer complianceReviewer) {
        this.complianceReviewer=complianceReviewer;
    }

    @Transactional(rollbackFor = Exception.class)
    public Report submit(long tenant, Long user, Metadata metadata, Parsed parsed, String filename, byte[] original) {
        DuplicateCheckResult duplicate = checkDuplicate(tenant, metadata, parsed.text(), null);
        if (duplicate.duplicate()) throw exception(REVIEW_DUPLICATE);
        String id = UUID.randomUUID().toString();
        Report report = pendingReport(id, metadata, parsed.text(), parsed.issues());
        repository.insert(tenant, new Stored(report, parsed.text(), filename, parsed.issues(), user, List.of(), List.of()), original);
        eventPublisher.publishEvent(new ReviewSubmittedEvent(tenant, id));
        return report;
    }

    public Stored get(long tenant, String id) {
        Stored stored = repository.get(tenant, id, false);
        if (stored == null) throw exception(NOT_FOUND);
        return normalize(stored);
    }

    public List<Report> list(long tenant, int limit) {
        return repository.list(tenant, Math.max(1, Math.min(limit, 100))).stream().map(this::normalize).toList();
    }

    public List<SubmissionStatus> listBySubmitter(long tenant, Long user, int limit) {
        return repository.listBySubmitter(tenant, user, Math.max(1, Math.min(limit, 100))).stream()
                .map(item -> new SubmissionStatus(normalize(item.report()), item.published())).toList();
    }

    public byte[] original(long tenant, String id) {
        get(tenant, id);
        return repository.original(tenant, id);
    }

    public DuplicateCheckResult checkDuplicate(long tenant, Metadata metadata, String text) {
        return checkDuplicate(tenant, metadata, text, null);
    }

    private DuplicateCheckResult checkDuplicate(long tenant, Metadata metadata, String text, String excludedId) {
        String hash = ReviewEngine.hash(text);
        String documentNo = normalized(metadata.documentNo());
        String sourceUrl = normalized(metadata.sourceUrl());
        for (Stored stored : repository.listStored(tenant, 1000)) {
            Report report = stored.report();
            if (Objects.equals(excludedId, report.id())) continue;
            if (hash.equals(report.textHash())) return new DuplicateCheckResult(true, report.id(), "正文内容完全相同");
            Metadata existing = report.metadata();
            if (!documentNo.isBlank() && documentNo.equals(normalized(existing.documentNo())))
                return new DuplicateCheckResult(true, report.id(), "文号或标准编号已存在");
            if (!sourceUrl.isBlank() && sourceUrl.equals(normalized(existing.sourceUrl())))
                return new DuplicateCheckResult(true, report.id(), "来源链接已提交");
        }
        return new DuplicateCheckResult(false, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Report completeAiReview(long tenant, String id) {
        Stored raw = repository.get(tenant, id, true);
        if (raw == null) throw exception(NOT_FOUND);
        Stored stored = normalize(raw);
        if (stored.report().workflowStatus() != WorkflowStatus.AI_REVIEWING) return stored.report();
        Report reviewed;
        try {
            reviewed = engine.review(id, stored.report().createdAt(), stored.report().metadata(), stored.text(), stored.parsingIssues(), null);
            if(complianceReviewer!=null) {
                CmsComplianceReviewer.Assessment ai=complianceReviewer.review(reviewed.metadata(),stored.text(),reviewed.issues());
                List<Issue> issues=new ArrayList<>(reviewed.issues());
                issues.addAll(ai.issues());
                Decision decision=issues.stream().anyMatch(issue->issue.severity()==Severity.ERROR)
                        ?Decision.FAIL:ai.decision();
                reviewed=new Report(reviewed.id(),reviewed.createdAt(),reviewed.metadata(),reviewed.textHash(),
                        decision,Math.min(reviewed.score(),ai.score()),reviewed.scores(),List.copyOf(issues),null,false,0,
                        WorkflowStatus.PENDING_MANUAL,ai.model(),Instant.now().toString(),null,null,null);
            }
        } catch (RuntimeException ex) {
            Issue issue = new Issue(Dimension.QUALITY, Severity.WARNING, "AI_REVIEW_UNAVAILABLE", "AI初审",
                    "合规初审执行失败，已转人工复核", "人工查看原文与元数据后作出最终决定");
            reviewed = new Report(id, stored.report().createdAt(), stored.report().metadata(), stored.report().textHash(),
                    Decision.WARNING, 0, Map.of(), List.of(issue), null, false, 0,
                    WorkflowStatus.PENDING_MANUAL, "fallback", Instant.now().toString(), null, null, null);
        }
        Report result = new Report(reviewed.id(), reviewed.createdAt(), reviewed.metadata(), reviewed.textHash(),
                reviewed.decision(), reviewed.score(), reviewed.scores(), reviewed.issues(), reviewed.evidence(), false, 0,
                WorkflowStatus.PENDING_MANUAL, reviewed.aiModel(),
                reviewed.aiCompletedAt() == null ? Instant.now().toString() : reviewed.aiCompletedAt(), null, null, null);
        repository.update(tenant, new Stored(result, stored.text(), stored.filename(), stored.parsingIssues(),
                stored.submittedBy(), stored.evidenceHistory(), List.of()));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Report manualReview(long tenant, Long reviewer, String id, ManualReviewRequest request) {
        Stored raw = repository.get(tenant, id, true);
        if (raw == null) throw exception(NOT_FOUND);
        Stored stored = normalize(raw);
        Report current = stored.report();
        if (current.workflowStatus() == WorkflowStatus.AI_REVIEWING) throw exception(REVIEW_NOT_READY);
        if (current.workflowStatus() == WorkflowStatus.APPROVED || current.workflowStatus() == WorkflowStatus.REJECTED) return current;
        Evidence evidence = toEvidence(reviewer, request.evidence());
        boolean approved = request.decision() == ManualDecision.APPROVE;
        List<Chunk> chunks = approved ? ReviewEngine.chunk(stored.text()) : List.of();
        Report result = new Report(current.id(), current.createdAt(), current.metadata(), current.textHash(),
                current.decision(), current.score(), current.scores(), current.issues(), evidence, approved, chunks.size(),
                approved ? WorkflowStatus.APPROVED : WorkflowStatus.REJECTED, current.aiModel(), current.aiCompletedAt(),
                reviewer, Instant.now().toString(), request.note().strip());
        List<Evidence> history = new ArrayList<>(stored.evidenceHistory());
        if (evidence != null) history.add(evidence);
        Stored next = new Stored(result, stored.text(), stored.filename(), stored.parsingIssues(), stored.submittedBy(), List.copyOf(history), chunks);
        if (approved && !repository.knowledgeExists(tenant, id)) repository.admit(tenant, next);
        repository.update(tenant, next);
        return result;
    }

    private Evidence toEvidence(Long reviewer, EvidenceRequest request) {
        if (request == null) return null;
        Metadata official = request.officialMetadata();
        if (reviewer == null || !ReviewEngine.officialUrl(official.sourceUrl()) || !ReviewEngine.officialUrl(request.validitySourceUrl())
                || official.effectiveDate() == null || request.note().isBlank()) throw exception(INVALID_EVIDENCE);
        return new Evidence(official, ReviewEngine.hash(request.officialText()), request.validitySourceUrl(), request.note(), reviewer, engine.today());
    }

    @Transactional(rollbackFor = Exception.class)
    public BatchResult submitBatch(long tenant, Long user, List<BatchDocument> documents) {
        String batchId = UUID.randomUUID().toString();
        List<BatchItemResult> items = new ArrayList<>(documents.size());
        for (BatchDocument document : documents) {
            Report report = submit(tenant, user, document.metadata(), document.parsed(), document.fileName(), document.original());
            items.add(new BatchItemResult(document.fileName(), report));
        }
        return new BatchResult(batchId, items.size(), List.copyOf(items));
    }

    @Transactional(rollbackFor = Exception.class)
    public Report verify(long tenant, Long reviewer, String id, EvidenceRequest request) {
        Stored stored = get(tenant, id);
        if (stored.report().workflowStatus() == WorkflowStatus.AI_REVIEWING) completeAiReview(tenant, id);
        return manualReview(tenant, reviewer, id, new ManualReviewRequest(ManualDecision.APPROVE, request.note(), request));
    }

    @Transactional(rollbackFor = Exception.class)
    public Report resubmit(long tenant, Long user, String previousId, Submission input) {
        Stored previous = get(tenant, previousId);
        DuplicateCheckResult duplicate = checkDuplicate(tenant, input.metadata(), input.text(), previousId);
        if (duplicate.duplicate()) throw exception(REVIEW_DUPLICATE);
        String id = UUID.randomUUID().toString();
        Report report = pendingReport(id, input.metadata(), input.text(), List.of());
        repository.insert(tenant, new Stored(report, input.text(), "修订自 " + previous.report().id() + ".txt",
                List.of(), user, List.of(), List.of()), input.text().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        eventPublisher.publishEvent(new ReviewSubmittedEvent(tenant, id));
        return report;
    }

    private Report pendingReport(String id, Metadata metadata, String text, List<Issue> issues) {
        return new Report(id, Instant.now().toString(), metadata, ReviewEngine.hash(text), Decision.PENDING, 0,
                Map.of(), List.copyOf(issues), null, false, 0, WorkflowStatus.AI_REVIEWING,
                null, null, null, null, null);
    }

    private Stored normalize(Stored stored) {
        Report report = normalize(stored.report());
        return report == stored.report() ? stored : new Stored(report, stored.text(), stored.filename(), stored.parsingIssues(),
                stored.submittedBy(), stored.evidenceHistory(), stored.chunks());
    }

    private Report normalize(Report report) {
        if (report.workflowStatus() != null) return report;
        WorkflowStatus status = report.admitted() ? WorkflowStatus.APPROVED : WorkflowStatus.PENDING_MANUAL;
        return new Report(report.id(), report.createdAt(), report.metadata(), report.textHash(), report.decision(),
                report.score(), report.scores(), report.issues(), report.evidence(), report.admitted(), report.chunkCount(),
                status, "legacy-review", report.createdAt(), null, null, null);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT).replaceAll("[\\s·•—–－_\\-]+", "");
    }
}

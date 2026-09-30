package cn.iocoder.txgy.module.cms.service.review;

import cn.iocoder.txgy.framework.common.exception.ErrorCode;
import cn.iocoder.txgy.module.cms.service.rag.CmsComplianceReviewer;
import cn.iocoder.txgy.module.cms.service.storage.CmsDocumentStorageService;
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
    private static final ErrorCode RESUBMIT_NOT_ALLOWED = new ErrorCode(1_041_001_001, "只有已被人工驳回的内容才能重新提交");
    private static final ErrorCode INVALID_EVIDENCE = new ErrorCode(1_041_001_002, "请提供官方来源、效力依据、实施日期和核验意见");
    private static final ErrorCode CHUNK_EDIT_NOT_ALLOWED = new ErrorCode(1_041_001_004, "只有待人工复核的切片可以直接修改");
    private static final ErrorCode CHUNK_NOT_FOUND = new ErrorCode(1_041_001_005, "审查切片不存在");
    private static final ErrorCode CHUNK_VERSION_CONFLICT = new ErrorCode(1_041_001_006, "切片已被他人修改，请刷新后重试");
    private final ReviewRepository repository;
    private final ReviewEngine engine;
    private final ChunkingService chunkingService;
    private final CmsDocumentStorageService storageService;
    private ApplicationEventPublisher eventPublisher = event -> { };
    private CmsComplianceReviewer complianceReviewer;

    public ReviewService(ReviewRepository repository, ReviewEngine engine) {
        this(repository, engine, new ChunkingService(), null);
    }

    @Autowired
    public ReviewService(ReviewRepository repository, ReviewEngine engine, ChunkingService chunkingService,
                         CmsDocumentStorageService storageService) {
        this.repository = repository;
        this.engine = engine;
        this.chunkingService = chunkingService;
        this.storageService = storageService;
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
        return submit(tenant, user, metadata, parsed, filename, original, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Report submit(long tenant, Long user, Metadata metadata, Parsed parsed, String filename, byte[] original,
                         ChunkingOptions chunking) {
        DuplicateCheckResult duplicate = checkDuplicate(tenant, metadata, parsed.text(), null, false);
        if (duplicate.duplicate()) throw exception(REVIEW_DUPLICATE);
        String id = UUID.randomUUID().toString();
        Report report = pendingReport(id, metadata, parsed.text(), parsed.issues());
        ChunkingOptions resolved = chunkingService.resolve(chunking, metadata.kind());
        String originalPath = storageService == null ? null : storageService.store(tenant, id, filename, original);
        repository.insert(tenant, new Stored(report, parsed.text(), filename, parsed.issues(), user,
                List.of(), List.of(), resolved, parsed.pages()), storageService == null ? original : null, originalPath);
        eventPublisher.publishEvent(new ReviewSubmittedEvent(tenant, id));
        return report;
    }

    public ChunkPreview preview(ChunkPreviewRequest request) {
        return chunkingService.preview(request.text(), request.chunking(), request.kind());
    }

    public ChunkPreview preview(Parsed parsed, ChunkingOptions options, Kind kind) {
        return chunkingService.preview(parsed.text(), options, kind, parsed.pages());
    }

    public Stored get(long tenant, String id) {
        Stored stored = repository.get(tenant, id, false);
        if (stored == null) throw exception(NOT_FOUND);
        return normalize(stored);
    }

    public Stored getBySubmitter(long tenant, Long user, String id) {
        Stored stored = get(tenant, id);
        if (!Objects.equals(stored.submittedBy(), user)) throw exception(NOT_FOUND);
        return stored;
    }

    /** 人工复核阶段直接修正切片；版本号防止多个审查员相互覆盖。 */
    @Transactional(rollbackFor = Exception.class)
    public Stored updateReviewChunk(long tenant, String id, ChunkUpdateRequest request) {
        Stored raw = repository.get(tenant, id, true);
        if (raw == null) throw exception(NOT_FOUND);
        Stored stored = normalize(raw);
        if (stored.report().workflowStatus() != WorkflowStatus.PENDING_MANUAL_REVIEW
                && stored.report().workflowStatus() != WorkflowStatus.PENDING_MANUAL) {
            throw exception(CHUNK_EDIT_NOT_ALLOWED);
        }
        List<Chunk> chunks = new ArrayList<>(stored.chunks());
        int position = -1;
        for (int i = 0; i < chunks.size(); i++) {
            if (chunks.get(i).ordinal() == request.ordinal()) {
                position = i;
                break;
            }
        }
        if (position < 0) throw exception(CHUNK_NOT_FOUND);
        Chunk current = chunks.get(position);
        if (current.version() != request.version()) throw exception(CHUNK_VERSION_CONFLICT);
        String text = request.text().strip();
        chunks.set(position, new Chunk(current.ordinal(), current.startOffset(),
                current.startOffset() + text.length(), text, current.pageNumber(), ChunkReviewStatus.PASS,
                "人工复核已修正", true, current.version() + 1));
        Stored updated = new Stored(stored.report(), stored.text(), stored.filename(), stored.parsingIssues(),
                stored.submittedBy(), stored.evidenceHistory(), List.copyOf(chunks), stored.chunking(), stored.pages());
        repository.update(tenant, updated);
        return updated;
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
        String path = repository.originalPath(tenant, id);
        if (path != null && storageService != null) return storageService.read(path);
        return repository.original(tenant, id);
    }

    public OriginalFile originalFile(long tenant, String id) {
        Stored stored = get(tenant, id);
        String path = repository.originalPath(tenant, id);
        if (path != null && storageService != null) {
            String url = storageService.accessUrl(path);
            if (url != null) return new OriginalFile(stored.filename(), null, url);
            return new OriginalFile(stored.filename(), storageService.read(path), null);
        }
        return new OriginalFile(stored.filename(), repository.original(tenant, id), null);
    }

    /** 企业端只允许读取已经正式发布、且尚未归档的知识库原件。 */
    public OriginalFile publishedOriginalFile(long tenant, String id) {
        if (!repository.isPublished(tenant, id)) throw exception(NOT_FOUND);
        return originalFile(tenant, id);
    }

    public DuplicateCheckResult checkDuplicate(long tenant, Metadata metadata, String text) {
        return checkDuplicate(tenant, metadata, text, null, false);
    }

    private DuplicateCheckResult checkDuplicate(long tenant, Metadata metadata, String text,
                                                String excludedId, boolean ignoreRejected) {
        String hash = ReviewEngine.hash(text);
        String documentNo = normalized(metadata.documentNo());
        String sourceUrl = normalized(metadata.sourceUrl());
        for (Stored stored : repository.listStored(tenant, 1000)) {
            Report report = stored.report();
            if (Objects.equals(excludedId, report.id())) continue;
            if (ignoreRejected && normalize(report).workflowStatus() == WorkflowStatus.REJECTED) continue;
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
        WorkflowStatus workflowStatus = stored.report().workflowStatus();
        if (workflowStatus != WorkflowStatus.CHUNKING && workflowStatus != WorkflowStatus.PENDING_AI_REVIEW
                && workflowStatus != WorkflowStatus.AI_REVIEWING) return stored.report();
        List<Chunk> chunks = stored.chunks();
        if (workflowStatus == WorkflowStatus.CHUNKING || chunks == null || chunks.isEmpty()) {
            chunks = chunkingService.split(stored.text(), stored.chunking(), stored.report().metadata().kind(), stored.pages());
            Report chunked = withWorkflow(stored.report(), WorkflowStatus.PENDING_AI_REVIEW, chunks.size());
            stored = new Stored(chunked, stored.text(), stored.filename(), stored.parsingIssues(), stored.submittedBy(),
                    stored.evidenceHistory(), chunks, chunkingService.resolve(stored.chunking(), stored.report().metadata().kind()),
                    stored.pages());
            repository.update(tenant, stored);
        }
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
                        WorkflowStatus.PENDING_MANUAL_REVIEW,ai.model(),Instant.now().toString(),null,null,null);
            }
        } catch (RuntimeException ex) {
            Issue issue = new Issue(Dimension.QUALITY, Severity.WARNING, "AI_REVIEW_UNAVAILABLE", "AI初审",
                    "合规初审执行失败，已转人工复核", "人工查看原文与元数据后作出最终决定");
            reviewed = new Report(id, stored.report().createdAt(), stored.report().metadata(), stored.report().textHash(),
                    Decision.WARNING, 0, Map.of(), List.of(issue), null, false, 0,
                    WorkflowStatus.PENDING_MANUAL_REVIEW, "fallback", Instant.now().toString(), null, null, null);
        }
        Report result = new Report(reviewed.id(), reviewed.createdAt(), reviewed.metadata(), reviewed.textHash(),
                reviewed.decision(), reviewed.score(), reviewed.scores(), reviewed.issues(), reviewed.evidence(), false, chunks.size(),
                WorkflowStatus.PENDING_MANUAL_REVIEW, reviewed.aiModel(),
                reviewed.aiCompletedAt() == null ? Instant.now().toString() : reviewed.aiCompletedAt(), null, null, null);
        repository.update(tenant, new Stored(result, stored.text(), stored.filename(), stored.parsingIssues(),
                stored.submittedBy(), stored.evidenceHistory(), chunks, stored.chunking(), stored.pages()));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Report manualReview(long tenant, Long reviewer, String id, ManualReviewRequest request) {
        Stored raw = repository.get(tenant, id, true);
        if (raw == null) throw exception(NOT_FOUND);
        Stored stored = normalize(raw);
        Report current = stored.report();
        if (current.workflowStatus() == WorkflowStatus.AI_REVIEWING
                || current.workflowStatus() == WorkflowStatus.CHUNKING
                || current.workflowStatus() == WorkflowStatus.PENDING_AI_REVIEW) throw exception(REVIEW_NOT_READY);
        if (current.workflowStatus() == WorkflowStatus.APPROVED || current.workflowStatus() == WorkflowStatus.REJECTED) return current;
        Evidence evidence = toEvidence(reviewer, request.evidence());
        boolean approved = request.decision() == ManualDecision.APPROVE;
        List<Chunk> chunks = stored.chunks() == null ? List.of() : stored.chunks();
        if (approved && chunks.isEmpty()) {
            chunks = chunkingService.split(stored.text(), stored.chunking(), stored.report().metadata().kind(), stored.pages());
        }
        Report result = new Report(current.id(), current.createdAt(), current.metadata(), current.textHash(),
                current.decision(), current.score(), current.scores(), current.issues(), evidence, approved, chunks.size(),
                approved ? WorkflowStatus.APPROVED : WorkflowStatus.REJECTED, current.aiModel(), current.aiCompletedAt(),
                reviewer, Instant.now().toString(), request.note().strip());
        List<Evidence> history = new ArrayList<>(stored.evidenceHistory());
        if (evidence != null) history.add(evidence);
        Stored next = new Stored(result, stored.text(), stored.filename(), stored.parsingIssues(), stored.submittedBy(),
                List.copyOf(history), chunks, stored.chunking(), stored.pages());
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
            Report report = submit(tenant, user, document.metadata(), document.parsed(), document.fileName(),
                    document.original(), document.chunking());
            items.add(new BatchItemResult(document.fileName(), report));
        }
        return new BatchResult(batchId, items.size(), List.copyOf(items));
    }

    @Transactional(rollbackFor = Exception.class)
    public Report verify(long tenant, Long reviewer, String id, EvidenceRequest request) {
        Stored stored = get(tenant, id);
        if (stored.report().workflowStatus() == WorkflowStatus.AI_REVIEWING
                || stored.report().workflowStatus() == WorkflowStatus.CHUNKING
                || stored.report().workflowStatus() == WorkflowStatus.PENDING_AI_REVIEW) completeAiReview(tenant, id);
        return manualReview(tenant, reviewer, id, new ManualReviewRequest(ManualDecision.APPROVE, request.note(), request));
    }

    @Transactional(rollbackFor = Exception.class)
    public Report resubmit(long tenant, Long user, String previousId, Submission input) {
        return resubmit(tenant, user, previousId, input, true);
    }

    /** 管理员复核入口：允许修改任意已驳回申请，但保留原提交人归属。 */
    @Transactional(rollbackFor = Exception.class)
    public Report resubmitAsReviewer(long tenant, Long reviewer, String previousId, Submission input) {
        return resubmit(tenant, reviewer, previousId, input, false);
    }

    private Report resubmit(long tenant, Long user, String previousId, Submission input, boolean requireOwner) {
        Stored previous = requireOwner ? getBySubmitter(tenant, user, previousId) : get(tenant, previousId);
        if (previous.report().workflowStatus() != WorkflowStatus.REJECTED) {
            throw exception(RESUBMIT_NOT_ALLOWED);
        }
        // 已驳回版本从未进入知识库，不应阻止修订稿再次送审；仍阻止与待审或已发布内容重复。
        DuplicateCheckResult duplicate = checkDuplicate(tenant, input.metadata(), input.text(), previousId, true);
        if (duplicate.duplicate()) throw exception(REVIEW_DUPLICATE);
        String id = UUID.randomUUID().toString();
        Report report = pendingReport(id, input.metadata(), input.text(), List.of());
        Long submitter = requireOwner || previous.submittedBy() == null ? user : previous.submittedBy();
        ChunkingOptions resolved = chunkingService.resolve(input.chunking() == null ? previous.chunking() : input.chunking(),
                input.metadata().kind());
        repository.insert(tenant, new Stored(report, input.text(), "修订自 " + previous.report().id() + ".txt",
                List.of(), submitter, List.of(), List.of(), resolved),
                input.text().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        eventPublisher.publishEvent(new ReviewSubmittedEvent(tenant, id));
        return report;
    }

    private Report pendingReport(String id, Metadata metadata, String text, List<Issue> issues) {
        return new Report(id, Instant.now().toString(), metadata, ReviewEngine.hash(text), Decision.PENDING, 0,
                Map.of(), List.copyOf(issues), null, false, 0, WorkflowStatus.CHUNKING,
                null, null, null, null, null);
    }

    private Stored normalize(Stored stored) {
        Report report = normalize(stored.report());
        List<Issue> parsingIssues = stored.parsingIssues() == null ? List.of() : stored.parsingIssues();
        List<Evidence> evidenceHistory = stored.evidenceHistory() == null ? List.of() : stored.evidenceHistory();
        List<Chunk> chunks = stored.chunks() == null ? List.of() : stored.chunks();
        List<PageSpan> pages = stored.pages() == null ? List.of() : stored.pages();
        if (report == stored.report() && parsingIssues == stored.parsingIssues()
                && evidenceHistory == stored.evidenceHistory() && chunks == stored.chunks()
                && pages == stored.pages()) return stored;
        return new Stored(report, stored.text(), stored.filename(), parsingIssues,
                stored.submittedBy(), evidenceHistory, chunks, stored.chunking(), pages);
    }

    private Report normalize(Report report) {
        if (report.workflowStatus() != null) return report;
        WorkflowStatus status = report.admitted() ? WorkflowStatus.APPROVED : WorkflowStatus.PENDING_MANUAL;
        return new Report(report.id(), report.createdAt(), report.metadata(), report.textHash(), report.decision(),
                report.score(), report.scores(), report.issues(), report.evidence(), report.admitted(), report.chunkCount(),
                status, "legacy-review", report.createdAt(), null, null, null);
    }

    private static Report withWorkflow(Report report, WorkflowStatus status, int chunkCount) {
        return new Report(report.id(), report.createdAt(), report.metadata(), report.textHash(), report.decision(),
                report.score(), report.scores(), report.issues(), report.evidence(), report.admitted(), chunkCount,
                status, report.aiModel(), report.aiCompletedAt(), report.manualReviewerId(),
                report.manualReviewedAt(), report.manualNote());
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT).replaceAll("[\\s·•—–－_\\-]+", "");
    }

    public record OriginalFile(String filename, byte[] content, String redirectUrl) { }
}

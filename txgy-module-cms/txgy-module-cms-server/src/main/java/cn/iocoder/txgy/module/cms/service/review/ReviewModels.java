package cn.iocoder.txgy.module.cms.service.review;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 审查输入不是可信依据，Evidence 只能由独立核验权限的人员提交。 */
public final class ReviewModels {
    private ReviewModels() { }
    public enum Decision { PENDING, PASS, WARNING, FAIL }
    public enum WorkflowStatus {
        DRAFT, CHUNKING, PENDING_AI_REVIEW, PENDING_MANUAL_REVIEW,
        /** 兼容历史快照。 */ AI_REVIEWING, PENDING_MANUAL,
        APPROVED, REJECTED, MODIFYING, ARCHIVED
    }
    public enum ManualDecision { APPROVE, REJECT }
    public enum Dimension { ENCODING, AUTHENTICITY, FORMAT, QUALITY }
    public enum Severity { WARNING, ERROR }
    public enum Validity { EFFECTIVE, NOT_YET_EFFECTIVE, REPEALED, EXPIRED, UNKNOWN }
    public enum Kind { LAW, STANDARD, POLICY }
    public enum KnowledgeCategory { QA, LAW, FINANCE_POLICY }
    public enum ChunkStrategy { SEMANTIC, HEADING, REGEX, FIXED }
    public enum ChunkReviewStatus { PASS, WARN, FAIL }
    public record ChunkingOptions(KnowledgeCategory category,
                                  ChunkStrategy strategy,
                                  @Min(200) @Max(4000) Integer chunkSize,
                                  @Min(0) @Max(1000) Integer overlap,
                                  @Size(max=200) String delimiterRegex) { }
    public record Metadata(@NotBlank @Size(max=300) String title,
                           @NotNull @Size(max=100) String documentNo,
                           @NotBlank @Size(max=200) String issuer,
                           @NotNull @Size(max=2000) String sourceUrl,
                           @NotNull Kind kind, @NotNull Validity validity,
                           LocalDate effectiveDate, LocalDate expiryDate, boolean historical) { }
    public record Submission(@NotNull @jakarta.validation.Valid Metadata metadata,
                             @NotBlank @Size(max=1000000) String text,
                             @Valid ChunkingOptions chunking,
                             @Size(max=64) String sourceSnapshotToken) {
        public Submission(Metadata metadata, String text) { this(metadata, text, null, null); }
        public Submission(Metadata metadata, String text, ChunkingOptions chunking) {
            this(metadata, text, chunking, null);
        }
    }
    public record BatchItem(@NotBlank @Size(max=255) String fileName,
                            @NotNull @Valid Metadata metadata,
                            @Valid ChunkingOptions chunking) {
        public BatchItem(String fileName, Metadata metadata) { this(fileName, metadata, null); }
    }
    public record BatchManifest(@NotEmpty @Size(max=20) List<@Valid BatchItem> items) { }
    public record BatchDocument(String fileName, Metadata metadata, Parsed parsed, byte[] original,
                                ChunkingOptions chunking) {
        public BatchDocument(String fileName, Metadata metadata, Parsed parsed, byte[] original) {
            this(fileName, metadata, parsed, original, null);
        }
    }
    public record BatchItemResult(String fileName, Report report) { }
    public record BatchResult(String batchId, int total, List<BatchItemResult> items) { }
    public record SubmissionStatus(Report report, boolean published) { }
    public record EvidenceRequest(@NotNull @jakarta.validation.Valid Metadata officialMetadata,
                                  @NotBlank @Size(max=1000000) String officialText,
                                  @NotBlank @Size(max=2000) String validitySourceUrl,
                                  @NotBlank @Size(max=4000) String note) { }
    public record Evidence(Metadata officialMetadata, String officialTextHash, String validitySourceUrl,
                           String note, Long reviewerId, LocalDate checkedOn) { }
    public record Issue(Dimension dimension, Severity severity, String code, String location,
                        String message, String suggestion) { }
    public record Report(String id, String createdAt, Metadata metadata, String textHash,
                         Decision decision, int score, Map<Dimension,Integer> scores, List<Issue> issues,
                         Evidence evidence, boolean admitted, int chunkCount,
                         WorkflowStatus workflowStatus, String aiModel, String aiCompletedAt,
                         Long manualReviewerId, String manualReviewedAt, String manualNote) { }
    public record ManualReviewRequest(@NotNull ManualDecision decision,
                                      @NotBlank @Size(max=4000) String note,
                                      @Valid EvidenceRequest evidence) { }
    public record DuplicateCheckRequest(@NotNull @Valid Metadata metadata,
                                        @NotBlank @Size(max=1000000) String text) { }
    public record DuplicateCheckResult(boolean duplicate, String matchedReviewId, String reason) { }
    public record ReviewSubmittedEvent(long tenantId, String reviewId) { }
    public record Chunk(int ordinal, int startOffset, int endOffset, String text,
                        Integer pageNumber, ChunkReviewStatus aiReviewStatus,
                        String aiReviewMessage, Boolean manualEdited, Long version) {
        /**
         * 历史审查快照只包含 ordinal/startOffset/endOffset/text。使用包装类型接收
         * 缺失字段，并在构造时补默认值，避免旧 JSON 因 null 无法写入基本类型而
         * 导致整个审查列表反序列化失败。
         */
        public Chunk {
            if (aiReviewStatus == null) {
                aiReviewStatus = ChunkReviewStatus.PASS;
            }
            if (manualEdited == null) {
                manualEdited = false;
            }
            if (version == null) {
                version = 0L;
            }
        }

        public Chunk(int ordinal, int startOffset, int endOffset, String text) {
            this(ordinal, startOffset, endOffset, text, null, ChunkReviewStatus.PASS, null, false, 0L);
        }
    }
    public record PageSpan(int pageNumber, int startOffset, int endOffset) { }
    public record Stored(Report report, String text, String filename, List<Issue> parsingIssues,
                         Long submittedBy, List<Evidence> evidenceHistory, List<Chunk> chunks,
                         ChunkingOptions chunking, List<PageSpan> pages) {
        public Stored(Report report, String text, String filename, List<Issue> parsingIssues,
                      Long submittedBy, List<Evidence> evidenceHistory, List<Chunk> chunks,
                      ChunkingOptions chunking) {
            this(report, text, filename, parsingIssues, submittedBy, evidenceHistory, chunks, chunking, List.of());
        }
        public Stored(Report report, String text, String filename, List<Issue> parsingIssues,
                      Long submittedBy, List<Evidence> evidenceHistory, List<Chunk> chunks) {
            this(report, text, filename, parsingIssues, submittedBy, evidenceHistory, chunks, null, List.of());
        }
    }
    public record ChunkPreviewRequest(@NotBlank @Size(max=1000000) String text,
                                      @Valid ChunkingOptions chunking,
                                      Kind kind) { }
    public record ChunkPreview(int total, ChunkingOptions resolvedOptions, List<Chunk> samples) { }
    public record ChunkUpdateRequest(@Min(1) int ordinal,
                                     @NotBlank @Size(max=100000) String text,
                                     @Min(0) long version) { }
    public record Parsed(String text, List<Issue> issues, List<PageSpan> pages) {
        public Parsed(String text, List<Issue> issues) { this(text, issues, List.of()); }
    }
}

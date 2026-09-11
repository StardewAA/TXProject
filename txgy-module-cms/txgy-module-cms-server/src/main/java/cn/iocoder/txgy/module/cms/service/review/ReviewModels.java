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
    public enum WorkflowStatus { AI_REVIEWING, PENDING_MANUAL, APPROVED, REJECTED }
    public enum ManualDecision { APPROVE, REJECT }
    public enum Dimension { ENCODING, AUTHENTICITY, FORMAT, QUALITY }
    public enum Severity { WARNING, ERROR }
    public enum Validity { EFFECTIVE, NOT_YET_EFFECTIVE, REPEALED, EXPIRED, UNKNOWN }
    public enum Kind { LAW, STANDARD, POLICY }
    public record Metadata(@NotBlank @Size(max=300) String title,
                           @NotNull @Size(max=100) String documentNo,
                           @NotBlank @Size(max=200) String issuer,
                           @NotBlank @Size(max=2000) String sourceUrl,
                           @NotNull Kind kind, @NotNull Validity validity,
                           LocalDate effectiveDate, LocalDate expiryDate, boolean historical) { }
    public record Submission(@NotNull @jakarta.validation.Valid Metadata metadata,
                             @NotBlank @Size(max=1000000) String text) { }
    public record BatchItem(@NotBlank @Size(max=255) String fileName,
                            @NotNull @Valid Metadata metadata) { }
    public record BatchManifest(@NotEmpty @Size(max=20) List<@Valid BatchItem> items) { }
    public record BatchDocument(String fileName, Metadata metadata, Parsed parsed, byte[] original) { }
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
    public record Chunk(int ordinal, int startOffset, int endOffset, String text) { }
    public record Stored(Report report, String text, String filename, List<Issue> parsingIssues,
                         Long submittedBy, List<Evidence> evidenceHistory, List<Chunk> chunks) { }
    public record Parsed(String text, List<Issue> issues) { }
}

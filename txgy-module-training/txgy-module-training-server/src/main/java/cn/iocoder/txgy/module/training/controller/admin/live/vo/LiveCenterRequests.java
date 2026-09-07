package cn.iocoder.txgy.module.training.controller.admin.live.vo;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
public final class LiveCenterRequests {
 private LiveCenterRequests() {}
 public static final class Room {
  private Long id;
  @NotBlank @Size(max=200) private String title;
  @Size(max=2000) private String description;
  @Size(max=2000) private String coverUrl;
  @NotNull private LocalDateTime planStartAt;
  @NotNull private LocalDateTime planEndAt;
  @NotBlank @Pattern(regexp="public|enterprise") private String visibility;
  @Size(max=2000) private String streamUrl;
  @NotNull @DecimalMin("0.01") @DecimalMax("24") private BigDecimal creditHours;
  public Long id(){return id;} public String title(){return title;} public String description(){return description;}
  public String coverUrl(){return coverUrl;} public LocalDateTime planStartAt(){return planStartAt;}
  public LocalDateTime planEndAt(){return planEndAt;} public String visibility(){return visibility;}
  public String streamUrl(){return streamUrl;} public BigDecimal creditHours(){return creditHours;}
  public Long getId(){return id;} public void setId(Long v){id=v;} public String getTitle(){return title;} public void setTitle(String v){title=v;}
  public String getDescription(){return description;} public void setDescription(String v){description=v;} public String getCoverUrl(){return coverUrl;} public void setCoverUrl(String v){coverUrl=v;}
  public LocalDateTime getPlanStartAt(){return planStartAt;} public void setPlanStartAt(LocalDateTime v){planStartAt=v;} public LocalDateTime getPlanEndAt(){return planEndAt;} public void setPlanEndAt(LocalDateTime v){planEndAt=v;}
  public String getVisibility(){return visibility;} public void setVisibility(String v){visibility=v;} public String getStreamUrl(){return streamUrl;} public void setStreamUrl(String v){streamUrl=v;}
  public BigDecimal getCreditHours(){return creditHours;} public void setCreditHours(BigDecimal v){creditHours=v;}
 }
 public record Profile(@NotNull Long userId,@NotBlank @Size(max=100) String name,
   @NotBlank @Size(max=200) String organization,@NotBlank @Size(max=500) String expertise,@Size(max=5000) String bio) {}
 public record Review(@NotNull Long id,@NotBlank @Pattern(regexp="approved|rejected") String status,@Size(max=1000) String comment) {}
 public record Id(@NotNull Long id) {}
 public record Toggle(@NotNull Long id,@NotNull Boolean enabled) {}
 public record Replay(@NotNull Long roomId,@Size(max=2000) String url,@Size(max=1000) String issue) {}
 public record Users(@NotNull Long roomId,@NotNull @Size(max=200) List<@NotNull Long> userIds) {}
 public static final class Assignment {
  @NotNull private Long roomId; @NotBlank @Size(max=200) private String title;
  @NotBlank @Size(max=10000) private String content; @NotNull private LocalDateTime dueAt;
  public Long roomId(){return roomId;} public String title(){return title;} public String content(){return content;} public LocalDateTime dueAt(){return dueAt;}
  public Long getRoomId(){return roomId;} public void setRoomId(Long v){roomId=v;} public String getTitle(){return title;} public void setTitle(String v){title=v;}
  public String getContent(){return content;} public void setContent(String v){content=v;} public LocalDateTime getDueAt(){return dueAt;} public void setDueAt(LocalDateTime v){dueAt=v;}
 }
 public record Submission(@NotNull Long assignmentId,@NotBlank @Size(max=10000) String content) {}
 public record Grade(@NotNull Long id,@NotNull @Min(0) @Max(100) Integer score,@NotBlank @Size(max=2000) String feedback) {}
 public record Moderation(@NotNull Long roomId,@NotNull Long tenantId,@NotNull Long userId,
   @Pattern(regexp="mute|unmute|kick|restore") @NotBlank String action,@NotBlank @Size(max=500) String reason) {}
 public record Quiz(@NotNull Long roomId,@NotBlank @Size(max=1000) String question,
   @Size(min=2,max=6) @NotNull List<@NotBlank @Size(max=300) String> options,@NotNull @Min(0) @Max(5) Integer correctAnswer) {}
 public record Answer(@NotNull Long quizId,@NotNull @Min(0) @Max(5) Integer answer) {}
 public record Config(@NotNull Boolean danmakuEnabled,@NotNull Boolean micEnabled) {}
}

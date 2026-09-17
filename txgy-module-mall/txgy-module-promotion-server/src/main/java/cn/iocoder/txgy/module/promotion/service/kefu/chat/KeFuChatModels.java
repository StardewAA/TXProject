package cn.iocoder.txgy.module.promotion.service.kefu.chat;

import cn.iocoder.txgy.module.promotion.service.kefu.scope.KeFuScopeModels.Session;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class KeFuChatModels {
    private KeFuChatModels() {}
    public record Capability(boolean enabled, String version, String mode, boolean attachmentsEnabled, String message) {}
    public record Question(String id, String question) {}
    public record Thread(Session session, String mode, boolean humanRequested, long onlineAgents,
                         String availabilityMessage, long lastMessageId, long readMessageId, long unreadCount,
                         List<Question> faq, boolean attachmentsEnabled) {}
    public record Message(long id, String sessionId, String senderRole, String text, long createdAt,
                          String clientMessageId, String faqId, Long faqVersion, KeFuChatCardService.Card card) {}
    public record MessagePage(List<Message> items, long nextCursor, boolean hasMore) {}
    public record SendResult(List<Message> messages, Thread thread) {}
    public record SendRequest(@NotBlank @Size(max=36) String sessionId, @NotNull @Min(0) Long version,
                              @NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{8,64}") String clientMessageId,
                              @Size(max=2000) String text, String resourceType, Long resourceId) {}
    public record ReadRequest(@NotBlank @Size(max=36) String sessionId, @NotNull @Min(0) Long messageId) {}
    public record HeartbeatRequest(@NotNull @Pattern(regexp="PLATFORM|SHOP") String scope,
                                   @NotNull @Min(0) Long shopId, @NotNull Boolean online) {}
    public record Heartbeat(boolean online, long expiresAt) {}
    public record FaqEntry(@NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{1,40}") String id,
                           @NotBlank @Size(max=100) String question,
                           @NotNull @Size(max=8) List<@NotBlank @Size(min=2,max=30) String> keywords,
                           @NotBlank @Size(max=1000) String answer, boolean enabled) {}
    public record FaqConfig(String scope, long shopId, long version, long publishedVersion,
                            List<FaqEntry> entries, List<FaqEntry> publishedEntries) {}
    public record FaqSaveRequest(@NotNull @Pattern(regexp="PLATFORM|SHOP") String scope,
                                 @NotNull @Min(0) Long shopId, @NotNull @Min(0) Long version,
                                 @NotNull @Size(max=30) List<@Valid FaqEntry> entries) {}
    public record FaqPublishRequest(@NotNull @Pattern(regexp="PLATFORM|SHOP") String scope,
                                    @NotNull @Min(0) Long shopId, @NotNull @Min(0) Long version,
                                    @NotBlank @Size(max=500) String reason) {}
}

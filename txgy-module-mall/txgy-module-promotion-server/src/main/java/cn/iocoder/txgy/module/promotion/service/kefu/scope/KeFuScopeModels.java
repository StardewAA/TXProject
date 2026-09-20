package cn.iocoder.txgy.module.promotion.service.kefu.scope;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

/** Versioned contracts. IDs are not compatible with the legacy numeric conversation API. */
public final class KeFuScopeModels {
    private KeFuScopeModels() {}

    public record Actor(long id, int type) {}

    public record OpenRequest(@NotNull @Pattern(regexp = "PLATFORM|SHOP") String scope,
                              @NotNull @Min(0) Long shopId) {}

    public record ChangeRequest(@NotBlank @Size(max = 36) String sessionId,
                                @NotNull @Min(0) Long version) {}

    public record GrantRequest(@NotNull @Pattern(regexp = "PLATFORM|SHOP") String scope,
                               @NotNull @Min(0) Long shopId,
                               @NotNull @Min(1) @Max(2) Integer actorType,
                               @NotNull @Positive Long actorId,
                               @NotNull Boolean enabled,
                               @NotNull @Min(0) Long version,
                               @NotBlank @Size(max = 500) String reason) {}

    public record Session(String id, long tenantId, String scope, long shopId, long memberId,
                          String state, Integer agentType, Long agentId, long version,
                          LocalDateTime createTime, LocalDateTime updateTime) {}

    public record Agent(String scope, long shopId, int actorType, long actorId,
                        boolean enabled, long version) {}
}

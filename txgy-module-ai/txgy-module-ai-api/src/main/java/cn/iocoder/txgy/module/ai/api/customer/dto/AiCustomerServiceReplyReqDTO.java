package cn.iocoder.txgy.module.ai.api.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Desensitized mall customer-service prompt. */
@Data
public class AiCustomerServiceReplyReqDTO {

    @NotBlank
    @Pattern(regexp = "PLATFORM|SHOP")
    private String scope;

    @NotNull
    @Min(0)
    private Long shopId;

    /** Internal member identity used only for provider quota isolation and audit correlation. */
    @NotNull
    @Min(1)
    private Long requesterId;

    /** Server-issued customer-service conversation identifier; never accept a free-form prompt value here. */
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{8,64}")
    private String conversationId;

    @NotBlank
    @Size(max = 1000)
    private String question;

    /** Published FAQ and a server-owned resource-card summary only; never member PII. */
    @Size(max = 4000)
    private String context;
}

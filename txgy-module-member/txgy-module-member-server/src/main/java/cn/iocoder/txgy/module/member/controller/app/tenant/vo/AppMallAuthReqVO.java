package cn.iocoder.txgy.module.member.controller.app.tenant.vo;

import cn.iocoder.txgy.framework.common.validation.Mobile;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** Distinct request types ensure no client-supplied enterprise/role/social binding can grant permissions. */
public final class AppMallAuthReqVO {
    private AppMallAuthReqVO() { }
    public enum Purpose { REGISTER, SMS_LOGIN, RESET_PASSWORD }

    @Data
    public static class Target {
        @Positive private Long tenantId;
        @NotBlank @Mobile private String mobile;
    }
    @Data @EqualsAndHashCode(callSuper = true)
    public static class Send extends Target {
        @NotNull private Purpose purpose;
    }
    @Data @EqualsAndHashCode(callSuper = true)
    public static class Challenge extends Target {
        @NotBlank @Pattern(regexp = "[a-f0-9]{32}") private String challengeId;
        @ToString.Exclude @NotBlank @Pattern(regexp = "[0-9]{6}") private String code;
    }
    @Data @EqualsAndHashCode(callSuper = true)
    public static class Register extends Challenge {
        @ToString.Exclude @NotBlank @Size(min = 6, max = 16) private String password;
        @NotBlank @Size(max = 30) private String nickname;
        @NotNull @AssertTrue private Boolean agree;
        @NotBlank @Pattern(regexp = "supplier|buyer") private String memberIdentity;
    }
    @Data @EqualsAndHashCode(callSuper = true)
    public static class ResetPassword extends Challenge {
        @ToString.Exclude @NotBlank @Size(min = 6, max = 16) private String password;
    }
    @Data @EqualsAndHashCode(callSuper = true)
    public static class PasswordLogin extends Target {
        @ToString.Exclude @NotBlank @Size(min = 6, max = 16) private String password;
    }
}

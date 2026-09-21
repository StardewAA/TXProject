package cn.iocoder.txgy.module.member.controller.app.tenant.vo;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

public final class AppMallImageAuthReqVO {
    private AppMallImageAuthReqVO() { }
    @Data
    public static class Target {
        @Positive private Long tenantId;
    }
    @Data @EqualsAndHashCode(callSuper = true)
    public static class Image extends Target {
        @NotBlank @Size(max = 40) private String loginName;
        @NotNull @Pattern(regexp = "[0-9a-f]{32}") private String captchaSession;
        @Pattern(regexp = "[0-9a-f]{32}") private String previousChallengeId;
        @NotNull @Pattern(regexp = "REGISTER") private String purpose;
    }
    @Data @EqualsAndHashCode(callSuper = true)
    public static class Register extends Target {
        @NotBlank @Size(max = 40) private String loginName;
        @NotNull @Size(min = 6, max = 16) @ToString.Exclude private String password;
        @NotNull @Size(min = 6, max = 16) @ToString.Exclude private String confirmPassword;
        @NotNull @Pattern(regexp = "[0-9a-f]{32}") private String captchaSession;
        @NotNull @Pattern(regexp = "[0-9a-f]{32}") private String imageChallengeId;
        @NotNull @Pattern(regexp = "[A-Za-z0-9]{5}") @ToString.Exclude private String imageCode;
        @Pattern(regexp = "(?:1[3-9][0-9]{9})?") private String contactMobile;
        @Size(max = 30) private String nickname;
        @NotNull @Pattern(regexp = "buyer|supplier") private String memberIdentity;
        @NotNull @AssertTrue private Boolean agree;
    }
    @Data @EqualsAndHashCode(callSuper = true)
    public static class Login extends Target {
        @NotBlank @Size(max = 40) private String account;
        @NotNull @Size(min = 4, max = 16) @ToString.Exclude private String password;
    }
}

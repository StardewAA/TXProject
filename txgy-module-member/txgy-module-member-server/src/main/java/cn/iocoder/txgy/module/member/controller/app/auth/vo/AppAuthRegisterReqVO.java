package cn.iocoder.txgy.module.member.controller.app.auth.vo;

import cn.iocoder.txgy.framework.common.validation.Mobile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Schema(description = "用户 APP - 手机号注册 Request VO")
@Data
public class AppAuthRegisterReqVO {

    @Schema(description = "手机号", requiredMode = Schema.RequiredMode.REQUIRED, example = "15601691300")
    @NotEmpty(message = "手机号不能为空")
    @Mobile
    private String mobile;

    @Schema(description = "手机验证码", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotEmpty(message = "手机验证码不能为空")
    @Length(min = 4, max = 6, message = "手机验证码长度为 4-6 位")
    @Pattern(regexp = "^[0-9]+$", message = "手机验证码必须都是数字")
    private String code;

    @Schema(description = "登录密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "Abc123456")
    @NotEmpty(message = "密码不能为空")
    @Length(min = 6, max = 16, message = "密码长度为 6-16 位")
    private String password;

    @Schema(description = "用户昵称", requiredMode = Schema.RequiredMode.REQUIRED, example = "商城用户")
    @NotEmpty(message = "昵称不能为空")
    @Length(max = 30, message = "昵称长度不能超过 30 个字符")
    private String nickname;

    @Schema(description = "是否同意用户协议", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @AssertTrue(message = "请先同意用户协议")
    private Boolean agree;

}

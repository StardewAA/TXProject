package cn.iocoder.txgy.module.system.controller.admin.mail.vo.account;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 邮箱账号 Response VO")
@Data
public class MailAccountRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "邮箱", requiredMode = Schema.RequiredMode.REQUIRED, example = "txgyyuanma@123.com")
    private String mail;

    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED, example = "txgy")
    private String username;

    /**
     * 注意：刻意不返回密码（授权码）。
     * 历史实现直接把明文密码放进响应体，等于任何能查到账号的人都能拿到发件授权码，
     * 这里只回传"是否已配置"，前端编辑时密码留空即表示不修改。
     */
    @Schema(description = "是否已配置密码（授权码）", example = "true")
    private Boolean passwordConfigured;

    @Schema(description = "SMTP 服务器域名", requiredMode = Schema.RequiredMode.REQUIRED, example = "www.iocoder.cn")
    private String host;

    @Schema(description = "SMTP 服务器端口", requiredMode = Schema.RequiredMode.REQUIRED, example = "80")
    private Integer port;

    @Schema(description = "是否开启 ssl", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean sslEnable;

    @Schema(description = "是否开启 starttls", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean starttlsEnable;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}

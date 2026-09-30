package cn.iocoder.txgy.module.bidding.controller.admin.opening.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "开标房间 - 成员进房 Request（进房身份认证对接外部 CA SDK）")
@Data
public class OpeningEnterReqVO {

    @Schema(description = "开标记录ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "开标记录ID不能为空")
    private Long openingId;

    @Schema(description = "成员用户ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "成员用户ID不能为空")
    private Long userId;

    @Schema(description = "成员姓名", example = "张三")
    private String userName;

    @Schema(description = "所属企业名称（投标人/招标人）", example = "XX建设集团有限公司")
    private String subjectName;

    @Schema(description = "统一社会信用代码", example = "91610000XXXXXXXXXX")
    private String subjectCode;

    @Schema(description = "身份：TENDERER/PLATFORM/BIDDER/SUPERVISOR", requiredMode = Schema.RequiredMode.REQUIRED, example = "BIDDER")
    @NotBlank(message = "进房身份不能为空")
    private String role;

    @Schema(description = "身份认证方式：CA_CERT/ACCOUNT", example = "CA_CERT")
    private String identityType;

    @Schema(description = "CA 证书序列号（CA_CERT 时必填）", example = "SN-9E8D7C")
    private String caCertNo;
}

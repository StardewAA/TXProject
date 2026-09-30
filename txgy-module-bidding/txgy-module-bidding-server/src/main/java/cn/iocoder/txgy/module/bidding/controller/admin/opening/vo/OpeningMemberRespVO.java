package cn.iocoder.txgy.module.bidding.controller.admin.opening.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "开标房间成员 Response（显示房间里有谁）")
@Data
public class OpeningMemberRespVO {

    @Schema(description = "成员记录ID")
    private Long id;
    @Schema(description = "成员用户ID")
    private Long userId;
    @Schema(description = "成员姓名")
    private String userName;
    @Schema(description = "所属企业名称")
    private String subjectName;
    @Schema(description = "统一社会信用代码")
    private String subjectCode;
    @Schema(description = "身份：TENDERER/PLATFORM/BIDDER/SUPERVISOR")
    private String role;
    @Schema(description = "身份认证方式：CA_CERT/ACCOUNT")
    private String identityType;
    @Schema(description = "CA 证书序列号")
    private String caCertNo;
    @Schema(description = "进房认证结果：PENDING/AUTHED/FAILED")
    private String authResult;
    @Schema(description = "认证结果说明")
    private String authMessage;
    @Schema(description = "认证通过时间")
    private LocalDateTime authTime;
    @Schema(description = "进房时间")
    private LocalDateTime enterTime;
    @Schema(description = "离房时间")
    private LocalDateTime leaveTime;
    @Schema(description = "是否在房间内")
    private Boolean online;
}

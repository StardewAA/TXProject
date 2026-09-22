package cn.iocoder.txgy.module.member.controller.admin.platform.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 平台账号人工身份归并 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberPlatformAccountMergeRespVO {

    private String reviewNo;
    private Long targetPlatformAccountId;
    private Integer readyLinkCount;
    private LocalDateTime verifiedAt;
    private Boolean idempotentReplay;
}

package cn.iocoder.txgy.module.bidding.controller.admin.safety.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 多岗兼任合规校验结论。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§1.1(f)/§1.3。
 * <p>为什么返回"要求什么证"而不只是 true/false：不通过时前端要能直接告诉用户"你缺 B 类安全生产考核合格证"，
 * 只回 false 会让用户无从下手（这是合规类校验最常见的体验缺陷）。
 * <p>业务目标：不通过即可行动。
 */
@Schema(description = "管理后台 - 多岗兼任合规校验结论")
@Data
public class MultiPostCheckRespVO {

    @Schema(description = "是否通过", example = "false")
    private Boolean pass;

    @Schema(description = "兼任岗位要求的证书类型 code", example = "SAFETY_ASSESS_CERT")
    private String requiredCertType;

    @Schema(description = "要求的证书细分（如 A/B/C）", example = "C")
    private String requiredCertSubType;

    @Schema(description = "命中的员工证书主键（通过时）", example = "2001")
    private Long matchedCertId;

    @Schema(description = "不通过原因（可直接展示给用户）", example = "缺少兼任岗位要求的证书：安全生产考核合格证（C类）")
    private String failReason;

    @Schema(description = "校验时刻")
    private LocalDateTime checkedAt;
}

package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 达标核查记录行（页签 3 · 核查历史）。
 *
 * <p>规范出处：契约 §4 G6；表 15-6 qualification_compliance_review。
 * <p>为什么这样写：① {@code stdVersion} 必须返回——核查结论只有配上"依据哪一版标准"才可追溯，
 * 否则换版后无法解释历史结论；② {@code elementSnapshot} 原样返回（核查当时的要素快照），
 * 使历史结论可复算，不受后续要素修改影响；③ 只追加不改写，故不返回 version。
 * <p>业务目标：核查结论可作为投标资格审核证据（可追溯、可复算）。
 */
@Data
public class QualificationReviewRespVO {

    @Schema(description = "核查记录 id", example = "8001")
    private Long id;

    @Schema(description = "企业主体 id", example = "1")
    private Long entId;

    @Schema(description = "资质主档 id", example = "9001")
    private Long qualId;

    @Schema(description = "依据的标准版本号", example = "v2026.1")
    private String stdVersion;

    @Schema(description = "核查类型", example = "QUAL_RENEW")
    private String reviewType;

    @Schema(description = "核查结论 code（字典 review_result）", example = "PASS")
    private String reviewResult;

    @Schema(description = "要素快照（核查当时的人员/业绩/资产 JSON）", example = "{\"staff\":3}")
    private String elementSnapshot;

    @Schema(description = "差异项（缺失/不足明细 JSON）", example = "{\"staff\":\"缺 A 类 1 人\"}")
    private String diffItems;

    @Schema(description = "核查人用户 id", example = "1024")
    private Long reviewer;

    @Schema(description = "核查时间", example = "1788668723000")
    private LocalDateTime reviewedAt;

    @Schema(description = "关联申报单 id（由申报触发的核查）", example = "88001")
    private Long linkedApplyId;
}

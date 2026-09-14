package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 企业资质详情出参（列表行 + 绑定证照 + 最近一次达标核查）。
 *
 * <p>规范出处：契约《企业资质页-页面级契约-v1.0.md》§4 G2；§四-3（时刻用 LocalDateTime）。
 *
 * <p>为什么这样写：① 详情页要展示"这份资质背后的影像件"与"最近一次达标核查结论+依据标准版本"，
 * 若不给，前端得再发两次请求且可能拿到与详情不一致的快照；② 绑定证照允许为空
 * （资质可以只登记文字信息、不挂影像），故三个字段都可空，前端按空态处理而不是报错。
 *
 * <p>业务目标：资质详情一屏拿全"主档 + 影像来源 + 核查依据"，作为投标资格审核的证据视图。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QualificationDetailRespVO extends QualificationRespVO {

    @Schema(description = "绑定证照的名称（未绑定则为空）", example = "建筑业企业资质证书")
    private String bindedCertName;

    @Schema(description = "绑定证照的编号", example = "D361123456")
    private String bindedCertNo;

    @Schema(description = "绑定证照的状态 code（字典 cert_status）", example = "VALID")
    private String bindedCertStatus;

    @Schema(description = "最近一次达标核查")
    private LatestReview latestReview;

    /**
     * 最近一次达标核查摘要。
     *
     * <p>为什么单独成类：核查记录是只追加的历史表，详情只取最近一条；把"依据的标准版本号"
     * 一并带出，核查结论才能作为审核证据（只给结论无法追溯依据）。
     */
    @Data
    public static class LatestReview {

        @Schema(description = "依据的标准版本号", example = "v2026.1")
        private String stdVersion;

        @Schema(description = "核查结论 code", example = "PASS")
        private String reviewResult;

        @Schema(description = "核查时刻", example = "1788668723000")
        private LocalDateTime reviewedAt;

        @Schema(description = "差异项 JSON（缺失/不足的要素明细）", example = "{\"staff\":\"缺 A 类 1 人\"}")
        private String diffItems;
    }
}

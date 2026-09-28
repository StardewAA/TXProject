package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 工程业绩行（要素台账 · 业绩）。
 *
 * <p>规范出处：契约 §4 G5；表 15-4 qualification_performance。
 * <p>为什么这样写：把"规模指标"与"是否平台可核验"一并返回——业绩是资质达标与投标加分的核心证据，
 * 审核人要能一眼看出该业绩是否已在四库一平台可查（isPlatformVerified），而不是只看到一串自填数字；
 * {@code evidenceFileIds} 以 JSON 字符串原样返回（前端按文件 id 数组解析，形态由契约统一）。
 * <p>业务目标：业绩可查、可核验性显式可见。
 */
@Data
public class QualificationPerformanceRespVO {

    @Schema(description = "记录 id", example = "7101")
    private Long id;

    @Schema(description = "企业主体 id", example = "1")
    private Long entId;

    @Schema(description = "资质主档 id", example = "9001")
    private Long qualId;

    @Schema(description = "项目名称", example = "XX 市政道路工程")
    private String projectName;

    @Schema(description = "工程类型（对口资质类别）", example = "市政公用工程")
    private String projectType;

    @Schema(description = "规模指标（JSON：面积/跨度/长度/层数等）", example = "{\"length\":3200}")
    private String scaleMetrics;

    @Schema(description = "合同额（元）", example = "12000000.00")
    private BigDecimal contractAmount;

    @Schema(description = "竣工验收日期（业绩年限窗口判定用）", example = "2023-11-30")
    private LocalDate completionDate;

    @Schema(description = "本企业承担角色", example = "总承包")
    private String role;

    @Schema(description = "质量评定结果", example = "合格")
    private String qualityResult;

    @Schema(description = "建设单位名称", example = "XX 城投")
    private String ownerName;

    @Schema(description = "是否四库一平台可核验", example = "true")
    private Boolean isPlatformVerified;

    @Schema(description = "佐证材料文件 id（JSON 数组字符串）", example = "[101,102]")
    private String evidenceFileIds;
}

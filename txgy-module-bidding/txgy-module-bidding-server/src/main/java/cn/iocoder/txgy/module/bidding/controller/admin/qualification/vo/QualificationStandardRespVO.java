package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 资质标准行（分页列表出参）。
 *
 * <p>规范出处：契约 §4 G4；表 15-2（req_assets / req_staff / req_performance / req_equipment /
 * effective_from / effective_to / source_ref）。
 *
 * <p>为什么这样写：① 把四类要素要求（资产/人员/业绩/设备）与**依据出处 source_ref** 一并返回——
 * 达标核查结论要能回答"按哪一版标准、依据哪个文件"；② 出参带 isEffective 由后端按生效区间统一判定，
 * 避免各页面各判一套；③ 要求字段用 JSON 字符串原样返回，保持与核查引擎同源，前端只做展示不做解析。
 *
 * <p>业务目标：标准可查、依据可溯、换版留痕。
 */
@Data
public class QualificationStandardRespVO {

    @Schema(description = "标准 id", example = "3001")
    private Long id;

    @Schema(description = "标准版本号", example = "v2026.1")
    private String stdVersion;

    @Schema(description = "资质序列", example = "施工")
    private String qualSerial;

    @Schema(description = "资质类别", example = "市政公用工程施工总承包")
    private String qualCategory;

    @Schema(description = "资质等级", example = "二级")
    private String qualGrade;

    @Schema(description = "资产要求（JSON）", example = "{\"netAssets\":8000000}")
    private String reqAssets;

    @Schema(description = "人员要求（JSON：按岗位/细分等级计数）", example = "{\"A\":3,\"B\":5}")
    private String reqStaff;

    @Schema(description = "业绩要求（JSON）", example = "{\"count\":2,\"minAmount\":10000000}")
    private String reqPerformance;

    @Schema(description = "设备要求（JSON）", example = "{}")
    private String reqEquipment;

    @Schema(description = "生效起始日", example = "2026-01-01")
    private LocalDate effectiveFrom;

    @Schema(description = "生效截止日（null=长期有效）", example = "2028-12-31")
    private LocalDate effectiveTo;

    @Schema(description = "依据出处（法规/文号/公告）", example = "建市〔2014〕159号")
    private String sourceRef;

    @Schema(description = "当前是否生效（按基准日与生效区间判定）", example = "true")
    private Boolean isEffective;
}

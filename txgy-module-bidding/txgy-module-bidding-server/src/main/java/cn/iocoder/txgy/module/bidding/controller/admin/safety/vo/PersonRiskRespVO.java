package cn.iocoder.txgy.module.bidding.controller.admin.safety.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 人员级风险状态（与企业级风险严格分离）。
 *
 * <p>规范出处：用户业务口径"企业级 ent_risk_flag 与人员级 person_risk_flag 分离"；
 * 《电子资质七大模块技术方案.md》§1.3（GET /bidding/personnel/{id}/risk）。
 * <p>业务目标：回答"这名人员能不能上岗/配证"，与"企业能不能投标"互不覆盖。
 */
@Schema(description = "管理后台 - 人员级风险状态")
@Data
public class PersonRiskRespVO {

    @Schema(description = "员工档案主键", example = "100")
    private Long personId;

    @Schema(description = "人员级标签 code：NORMAL/ABNORMAL/FREQUENT", example = "ABNORMAL")
    private String personRiskFlag;

    @Schema(description = "12 个月内更换受聘企业次数", example = "5")
    private Integer jobChangeCount12m;

    @Schema(description = "统计窗口起点")
    private LocalDate jobChangeWindowStart;

    @Schema(description = "是否异常（前端据此禁用上岗/配证按钮）", example = "true")
    private Boolean abnormal;

    @Schema(description = "最近评估时刻")
    private LocalDateTime personRiskEvaluatedAt;
}

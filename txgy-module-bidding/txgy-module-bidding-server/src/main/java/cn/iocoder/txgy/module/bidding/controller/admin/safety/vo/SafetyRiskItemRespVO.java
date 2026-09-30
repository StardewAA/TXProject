package cn.iocoder.txgy.module.bidding.controller.admin.safety.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 安许风险条目工单出参（五页改造缺接口契约 Q18，2026-09-28）。
 *
 * <p>规范出处：Q18 契约——出参 riskLevel/riskDesc/certRef/entName/assigneeId/assigneeName/status/foundAt 等。
 * <p>为什么这样写：assigneeName 与 entName 为落库/解析快照（展示免联查、改名不影响历史工单）；
 * version 供处理（handle）乐观锁 CAS 回传。
 */
@Schema(description = "管理后台 - 安许风险条目工单出参")
@Data
public class SafetyRiskItemRespVO {

    @Schema(description = "工单主键", example = "9001")
    private Long id;

    @Schema(description = "影响企业主体 id", example = "1")
    private Long entId;

    @Schema(description = "影响企业名称（主体档案快照解析）", example = "西安某某建设有限公司")
    private String entName;

    @Schema(description = "关联安许证 id（空=非安许来源）", example = "88001")
    private Long permitId;

    @Schema(description = "风险等级 code：LOW/MEDIUM/HIGH/MAJOR", example = "HIGH")
    private String riskLevel;

    @Schema(description = "风险描述", example = "B 类安全管理人员配备不足")
    private String riskDesc;

    @Schema(description = "关联证照/证书名称快照", example = "安许证 D361123456")
    private String certRef;

    @Schema(description = "责任人 id（未指派=null）", example = "1024")
    private Long assigneeId;

    @Schema(description = "责任人姓名快照", example = "李四")
    private String assigneeName;

    @Schema(description = "工单状态 code：PENDING 待处理/ASSIGNED 已指派/HANDLING 处理中/HANDLED 已处理/IGNORED 已忽略"
            + "（字典 risk_item_status，与前端 RiskStatus 一致）", example = "PENDING")
    private String status;

    @Schema(description = "发现时间", example = "1788668723000")
    private LocalDateTime foundAt;

    @Schema(description = "处理完成时间（未处理=null）")
    private LocalDateTime handledAt;

    @Schema(description = "处理/指派备注")
    private String handleRemark;

    // [契约 e2e P1 方案A 2026-10-02] 前端 api/bidding/safetyRisk 的 RiskItem.remark 是逐字对齐字段名；
    // 后端历史字段名为 handleRemark，此处补别名（同值），避免页面"备注列"读不到。
    @Schema(description = "处理/指派备注（前端字段名别名，与 handleRemark 同值）")
    private String remark;

    // [契约 e2e P1 方案A 2026-10-02] 前端 RiskItem.riskType（风险类型/来源 code）：
    // 当前工单来源仅安许 A/B/C 评估一种（permitId 非空即 SAFETY_PERMIT），其余来源留待扩展。
    @Schema(description = "风险类型/来源 code（当前：SAFETY_PERMIT；非安许来源为 null）", example = "SAFETY_PERMIT")
    private String riskType;

    @Schema(description = "乐观锁版本（handle 回传）", example = "1")
    private Integer version;
}

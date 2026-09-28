package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工预警清单 Response VO。
 *
 * <p>规范出处：《电子证照模块-前端接口契约》§8 员工证书 + 资质域既有形状（三域同形，页面组件可复用）。
 *
 * <p>为什么这样写（设计遵循）：
 * <ul>
 *   <li>带 {@code version}：处理走乐观锁 CAS（同资质域），避免并发覆盖他人的处理备注。</li>
 *   <li>{@code refName}：员工类预警写入的是**持证人/证书显示名（已脱敏）**，前端可直接展示；
 *       要跳转到员工档案请用 {@code refId}（= 员工证书 id）反查，不要在列表接口里做联表。</li>
 *   <li>不外发 {@code idempotentKey} 与租户/审计列，保持契约稳定。</li>
 * </ul>
 *
 * <p>业务目标：支撑"员工证件到期看板"的列表、待处理筛选与后续处理入口。
 *
 * @author txgy
 */
@Schema(description = "员工预警清单 Response VO")
@Data
public class PersonnelAlertRespVO {

    @Schema(description = "预警记录 id", requiredMode = Schema.RequiredMode.REQUIRED, example = "5001")
    private Long id;

    @Schema(description = "版本号（处理时原样回传，用于并发控制）", example = "1")
    private Integer version;

    @Schema(description = "泛化类型：PERSON_CERT / PERSON_CERT_REVIEW / PERSON_ID_CARD", example = "PERSON_CERT")
    private String refType;

    @Schema(description = "员工证书 id（跳转用）", example = "7001")
    private Long refId;

    @Schema(description = "显示名（持证人/证书，已脱敏）", example = "张*（安全生产考核合格证 C 类）")
    private String refName;

    @Schema(description = "预警类型 code：EXPIRY / REVIEW / RENEWAL", example = "EXPIRY")
    private String alertType;

    @Schema(description = "档位 code：INFO / WARNING / CRITICAL", example = "WARNING")
    private String alertLevel;

    @Schema(description = "预警内容", example = "【员工证书到期】张* 的安全生产考核合格证 C 类 将于 15 天后到期")
    private String alertMessage;

    @Schema(description = "触发时间")
    private LocalDateTime triggeredAt;

    @Schema(description = "是否已处理", example = "false")
    private Boolean handled;

    @Schema(description = "处理人（用户 id）", example = "1")
    private Long handledBy;

    @Schema(description = "处理时间")
    private LocalDateTime handledAt;

    @Schema(description = "处理备注", example = "已通知本人办理复审")
    private String handleNote;

}

package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 证照到期预警清单 Response VO。
 *
 * <p>规范出处：《电子证照模块-前端接口契约》§2 台账 + 资质域既有形状（{@code QualificationAlertRespVO}）。
 *
 * <p>为什么这样写（设计遵循）：
 * <ul>
 *   <li><b>带 {@code version}</b>：处理预警（标记已处理）走乐观锁 CAS，必须把版本号原样回传，
 *       否则并发下会覆盖别人的处理备注（资质域已有的 {@code /alert/handle} 就是这么用的）。</li>
 *   <li><b>{@code refName} 可能为空</b>：证照类预警在预警表里以 {@code certId} 定位，
 *       {@code refName} 只有资质/人员类预警才写入。前端展示证照名请用 {@code certId} 关联已加载的证照列表，
 *       或直接展示 {@code alertMessage}（文案里已含证照名）——**不要**为每条预警再查一次证照接口。</li>
 *   <li>不返回 {@code idempotentKey}（内部幂等键）与租户/审计列：属于内部字段，外发会让契约不稳定。</li>
 * </ul>
 *
 * <p>业务目标：支撑"到期预警看板"的列表与"待处理/已处理"筛选，并与资质域清单保持同形，页面组件可复用。
 *
 * @author txgy
 */
@Schema(description = "证照到期预警清单 Response VO")
@Data
public class CertificateAlertRespVO {

    @Schema(description = "预警记录 id", requiredMode = Schema.RequiredMode.REQUIRED, example = "4001")
    private Long id;

    @Schema(description = "版本号（处理时原样回传，用于并发控制）", example = "1")
    private Integer version;

    @Schema(description = "证照 id（可空：资质/人员类预警不填）", example = "1024")
    private Long certId;

    @Schema(description = "业务对象大类 code（证照类为空；资质类 QUAL/SAFETY_PERMIT/STAFF）", example = "QUAL")
    private String refType;

    @Schema(description = "业务对象主键（证照类为空）", example = "9001")
    private Long refId;

    @Schema(description = "业务对象显示名（证照类可能为空，见类注释）", example = "市政公用工程施工总承包二级")
    private String refName;

    @Schema(description = "预警类型 code：EXPIRY / REVIEW / RENEWAL", example = "EXPIRY")
    private String alertType;

    @Schema(description = "档位 code：INFO / WARNING / CRITICAL", example = "WARNING")
    private String alertLevel;

    @Schema(description = "预警内容（文案里含证照名与剩余天数）", example = "【证照到期】安全生产许可证 将于 30 天后到期，请及时办理延续/换证")
    private String alertMessage;

    @Schema(description = "触发时间")
    private LocalDateTime triggeredAt;

    @Schema(description = "是否已处理", example = "false")
    private Boolean handled;

    @Schema(description = "处理人（用户 id）", example = "1")
    private Long handledBy;

    @Schema(description = "处理时间")
    private LocalDateTime handledAt;

    @Schema(description = "处理备注", example = "已联系经办人办理延续")
    private String handleNote;

}

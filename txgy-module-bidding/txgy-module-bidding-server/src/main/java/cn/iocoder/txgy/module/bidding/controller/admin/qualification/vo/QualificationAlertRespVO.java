package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资质到期预警行（页签 4 · 预警清单，来源 cert_alert_records 中 alertType=QUAL_EXPIRY）。
 *
 * <p>规范出处：契约 §4 G8；cert_alert_records（09-10 ALTER 后的口径）+ QUAL_EXPIRY 三源扫描实现。
 * <p>为什么这样写：① {@code refType}/{@code refId} 是预警与业务对象的唯一纽带——同一张预警表承载
 * 资质主档（QUAL）/ 安许证（SAFETY_PERMIT）/ 人员配置（STAFF）三源，前端靠它跳转到对应台账；
 * ② {@code idempotentKey} 一并返回，便于排查"为什么今天没有重复预警"（同日幂等键 = refType:refId:yyyyMMdd）；
 * ③ 返回 {@code handled}/{@code handledBy}/{@code handledAt}/{@code handleNote} 形成处理闭环，
 * 而不是只给一条提示；
 * ④ **必须回传 {@code version}**：处理端点 {@code POST /alert/handle} 用版本号做 CAS
 * （防止"我看到的备注"被覆盖、或我覆盖别人的），请求 VO 里 {@code version} 是 {@code @NotNull} ——
 * 读侧不回传，前端就只能发一个假版本（要么恒冲突、要么等于绕过 CAS），处理闭环根本接不上。
 * 这是"写出口建好了、读侧却没给钥匙"的典型，故与处理端点成对维护。
 * <p>业务目标：预警可看、可跳转、可闭环处理。
 */
@Data
public class QualificationAlertRespVO {

    @Schema(description = "预警记录 id", example = "4001")
    private Long id;

    @Schema(description = "版本号（处理时原样回传，用于并发控制；与库中不一致 = 已被他人处理）", example = "3")
    private Integer version;

    @Schema(description = "来源类型：QUAL / SAFETY_PERMIT / STAFF", example = "QUAL")
    private String refType;

    @Schema(description = "来源对象 id（资质主档 / 安许证 / 人员配置记录）", example = "9001")
    private Long refId;

    @Schema(description = "来源对象名称", example = "市政公用工程施工总承包二级")
    private String refName;

    @Schema(description = "关联证照 id（可空）", example = "501")
    private Long certId;

    @Schema(description = "预警类型", example = "QUAL_EXPIRY")
    private String alertType;

    @Schema(description = "预警级别 code（字典 risk_level）", example = "WARNING")
    private String alertLevel;

    @Schema(description = "预警内容", example = "距到期 30 天，请及时发起延续申报")
    private String alertMessage;

    @Schema(description = "触发时间", example = "1788668723000")
    private LocalDateTime triggeredAt;

    @Schema(description = "同日幂等键（refType:refId:yyyyMMdd）", example = "QUAL:9001:20260913")
    private String idempotentKey;

    @Schema(description = "通知渠道", example = "站内信")
    private String notifyChannel;

    @Schema(description = "通知对象", example = "1024")
    private String notifyTarget;

    @Schema(description = "通知状态", example = "SENT")
    private String notifyStatus;

    @Schema(description = "是否已处理", example = "false")
    private Boolean handled;

    @Schema(description = "处理人用户 id", example = "1024")
    private Long handledBy;

    @Schema(description = "处理时间", example = "1788668723000")
    private LocalDateTime handledAt;

    @Schema(description = "处理说明", example = "已发起延续申报")
    private String handleNote;
}

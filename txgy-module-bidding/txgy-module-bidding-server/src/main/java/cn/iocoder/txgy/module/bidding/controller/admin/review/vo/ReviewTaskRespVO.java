package cn.iocoder.txgy.module.bidding.controller.admin.review.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台审查任务 Response VO（**与前端 api/bidding/review-task/index.ts 字段逐字对齐**）。
 *
 * <p>规范出处：《后端变更请求-平台审查任务中心-2026-09-20》§2 数据模型 + §5 接口出参。
 *
 * <p>为什么这样写（设计遵循）：
 * <ul>
 *   <li>字段名与前端 TS 接口**逐字一致**（bizType/bizId/entId/entName/entUscc/refNo/submitTime/status/reviewer/reviewTime/reason/remark/version）——
 *       任何一处改名都会让前端取到 undefined，而这类错误编译期发现不了（本仓已有 sha256/fileHash 的先例）。</li>
 *   <li>带 `version`：approve/reject 必须原样回传做乐观锁（契约 §4）。</li>
 *   <li>不外发租户/审计列，保持契约稳定。</li>
 * </ul>
 *
 * <p>业务目标：支撑"平台审查中心"列表、详情与审查操作；租户侧同一 VO 只读展示本企业任务与驳回原因。
 *
 * @author txgy
 */
@Schema(description = "平台审查任务 Response VO")
@Data
public class ReviewTaskRespVO {

    @Schema(description = "任务主键", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "业务类型：QUALIFICATION 企业资质 / CERTIFICATE 证照 / PERSON_CERT 员工证书", example = "CERTIFICATE")
    private String bizType;

    @Schema(description = "关联业务主键（资质 id / 证照 id）", example = "1024")
    private Long bizId;

    @Schema(description = "提交企业 id", example = "182143816674785")
    private Long entId;

    @Schema(description = "提交企业名称（快照）", example = "示例建设有限公司")
    private String entName;

    @Schema(description = "提交企业统一社会信用代码（快照）", example = "91610100MAM94DYHJ0")
    private String entUscc;

    @Schema(description = "关联证书编号/资质证书编号（快照）", example = "D361123456")
    private String refNo;

    @Schema(description = "任务产生（企业录入）时间")
    private LocalDateTime submitTime;

    @Schema(description = "PENDING 待审查 / APPROVED 已通过 / REJECTED 已驳回", example = "PENDING")
    private String status;

    @Schema(description = "审查人", example = "admin")
    private String reviewer;

    @Schema(description = "审查时间")
    private LocalDateTime reviewTime;

    @Schema(description = "驳回原因（REJECTED 必填，租户可见）")
    private String reason;

    @Schema(description = "提交时的备注")
    private String remark;

    @Schema(description = "乐观锁版本（审查时必须原样回传）", example = "0")
    private Integer version;

}

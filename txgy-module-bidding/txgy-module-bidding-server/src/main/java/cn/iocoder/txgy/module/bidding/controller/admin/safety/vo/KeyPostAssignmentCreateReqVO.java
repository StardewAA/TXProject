package cn.iocoder.txgy.module.bidding.controller.admin.safety.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 关键岗位任职/变更登记请求。
 *
 * <p>规范出处：用户业务口径"增加对同一项目关键岗位人员频繁变更的监控（防挂靠/管理失控）"；
 * 《电子资质七大模块技术方案.md》§1.1(e)/§1.3（关键岗位变更统计与阈值）。
 *
 * <p>为什么**没有** endAt（任期结束日）：频繁变更统计依赖"一段接一段"的任职流水，
 * 允许手工填 end_at 会造成任期断裂或重叠，统计口径随之失真。因此任期结束只有一种来源——
 * 登记同岗位的下一位任职者时，由服务端把上任的 end_at 闭合为本次 start_at。
 * <p>已知边界（未实现，非本次范围）：若某岗位人员离任且**无人接任**，本接口无法留出空档；
 * 需要时应新增"离任登记"接口，而不是放开 endAt 让调用方自己拼。
 *
 * <p>业务目标：换人动作一次登记即完成"上任闭合 + 新人上任 + 变更次数重算"，
 * 避免调用方分两步写导致中间态（两人同时在任）被统计读到。
 */
@Schema(description = "管理后台 - 关键岗位任职/变更登记 Request VO")
@Data
public class KeyPostAssignmentCreateReqVO {

    @Schema(description = "项目主键", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "项目不能为空")
    private Long projectId;

    @Schema(description = "关键岗位 code（KeyPostEnum）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "PROJECT_MANAGER")
    @NotBlank(message = "关键岗位不能为空")
    @Size(max = 32, message = "关键岗位 code 长度不能超过 32")
    private String postCode;

    @Schema(description = "任职人员（员工档案主键）", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "任职人员不能为空")
    private Long personId;

    @Schema(description = "任职开始日（留空=今天）", example = "2026-09-13")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startAt;

    @Schema(description = "变更原因（留痕，便于申诉时说明）", example = "原项目经理离职，由注册建造师接任")
    @Size(max = 256, message = "变更原因长度不能超过 256")
    private String changeReason;

}

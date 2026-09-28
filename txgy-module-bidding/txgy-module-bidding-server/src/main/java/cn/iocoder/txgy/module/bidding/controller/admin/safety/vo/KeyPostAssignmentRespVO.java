package cn.iocoder.txgy.module.bidding.controller.admin.safety.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 关键岗位任职/变更登记结果。
 *
 * <p>规范出处：用户业务口径"频繁变更监控"；《电子资质七大模块技术方案.md》§1.3（阈值来自配置中心）。
 *
 * <p>为什么把"窗口内变更次数 / 阈值 / 是否超阈值"一起返回：登记是关键岗位变更的**唯一入口**，
 * 前端需要在保存成功的同一屏立刻提示"该岗位 90 天内已变更 2 次，达阈值（挂靠嫌疑）"；
 * 若让前端保存后再调一次统计接口，会出现"保存成功但提示闪烁/失败"的中间态。
 * 注意：本接口只**提示**，不产出待办——待办由 Job `projectKeyPostChangeJob` 统一产出，
 * 两处都产出会导致同一嫌疑重复推送。
 *
 * <p>业务目标：换人当场可见风险，防挂靠从"事后统计"变成"操作时提示"。
 */
@Schema(description = "管理后台 - 关键岗位任职/变更登记 Response VO")
@Data
public class KeyPostAssignmentRespVO {

    @Schema(description = "任职流水主键", example = "4096")
    private Long id;

    @Schema(description = "项目主键", example = "1024")
    private Long projectId;

    @Schema(description = "关键岗位 code", example = "PROJECT_MANAGER")
    private String postCode;

    @Schema(description = "关键岗位中文名", example = "项目经理")
    private String postLabel;

    @Schema(description = "任职人员", example = "2048")
    private Long personId;

    @Schema(description = "任职开始日", example = "2026-09-13")
    private LocalDate startAt;

    @Schema(description = "该岗位第几次变更（首次任职为 1）", example = "2")
    private Integer changeSeq;

    @Schema(description = "被本次变更结束任期的上一位人员（首次任职为空）", example = "1024")
    private Long previousPersonId;

    @Schema(description = "上一位人员的任期结束日（=本次任职开始日；首次任职为空）", example = "2026-09-13")
    private LocalDate previousEndAt;

    @Schema(description = "统计窗口天数（配置中心 project.key.post.change.window.days）", example = "90")
    private Integer windowDays;

    @Schema(description = "统计窗口起点", example = "2026-06-15")
    private LocalDate windowStart;

    @Schema(description = "窗口内该岗位变更次数（含本次；首次任职算 0）", example = "2")
    private Integer windowChangeCount;

    @Schema(description = "变更次数阈值（配置中心 project.key.post.change.threshold）", example = "2")
    private Integer threshold;

    @Schema(description = "是否已达阈值（true=前端应提示挂靠嫌疑）", example = "true")
    private Boolean exceeded;

}

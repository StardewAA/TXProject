package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 员工证书批量续期回执（五页改造契约 Q11，2026-09-28）。
 *
 * <p>规范出处：Q11 契约——"返回逐条结果（单个失败不影响其余，前端据此展示部分成功）"。
 * <p>为什么改成包装对象：2026-09-28 对账发现前端已发布类型（personnel/index.ts 的 {@code BatchResult}）读的是
 * {@code total/successCount/failCount/failures[{id,name,message}]}；后端原先返回**裸数组**，
 * 前端读 {@code successCount} 恒为 undefined → "部分成功"提示出不来。故按前端形态包装，
 * 同时保留逐条 {@code items}（含成功行，便于排查）。
 * <p>业务目标：一次批量续期后，前端能直接说出"共 N 条，成功 X，失败 Y，失败项及原因"（不丢任何一条）。
 */
@Schema(description = "管理后台 - 员工证书批量续期回执")
@Data
public class PersonnelCertBatchRenewRespVO {

    @Schema(description = "提交条数（不含表头等无关概念，纯入参条目数）", example = "5")
    private Integer total;

    @Schema(description = "成功条数", example = "4")
    private Integer successCount;

    @Schema(description = "失败条数", example = "1")
    private Integer failureCount;

    @Schema(description = "失败条数（= failureCount 的别名；前端契约字段名）", example = "1")
    private Integer failCount;

    @Schema(description = "逐条结果（含成功行；每行 id/success/message）")
    private List<PersonnelCertBatchRenewItemRespVO> items;

    @Schema(description = "失败明细（前端契约字段名；元素 id/name/message）")
    private List<PersonnelCertBatchRenewFailVO> failures;
}

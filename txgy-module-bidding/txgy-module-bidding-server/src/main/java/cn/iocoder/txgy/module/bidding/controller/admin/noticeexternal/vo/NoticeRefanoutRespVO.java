package cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 关注扇出重放结果（契约《关注扇出重放补偿与维度回填-2026-09-29》§一）。
 *
 * <p>[为什么需要重放] 状态变更此前只有"应用审核接口"一条触发路径：
 * 批量导入、DBA 改库、数据迁移、刷数脚本都不会补扇出；且 {@code approveExternal} 里
 * 扇出失败**只记日志**（一次失败 = 该提醒永久丢失）。本接口是人工/脚本补偿的唯一入口。
 *
 * <p>[为什么重放是安全的] 事件键含 contentHash（用户-关注项-来源-公告id-contentHash），
 * 重复调用不会产生重复提醒，仅"内容确实变过"才新建事件 —— 幂等是它能当补偿手段的前提。
 */
@Schema(description = "管理后台 - 关注扇出重放结果")
@Data
public class NoticeRefanoutRespVO {

    @Schema(description = "命中订阅数（该公告维度键 + 公告级能匹配到的 FOLLOWING 订阅）", example = "3")
    private Integer matched;

    @Schema(description = "新建事件数（真正产生新动态的订阅数）", example = "3")
    private Integer created;

    @Schema(description = "去重跳过数（事件键已存在，重放幂等的正常表现）", example = "0")
    private Integer skipped;

    @Schema(description = "是否仅预演（dryRun=true 时不写库）", example = "false")
    private Boolean dryRun;
}

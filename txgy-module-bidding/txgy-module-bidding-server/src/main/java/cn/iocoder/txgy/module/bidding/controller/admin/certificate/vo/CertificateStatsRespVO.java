package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 证照看板统计出参（五页改造缺接口契约 Q3，2026-09-28：一次请求返回全量口径计数）。
 *
 * <p>规范出处：五页改造契约 Q3——"出参按状态全量计数（含暂扣/吊销/注销），并支持 certStatus in (...) 多选筛选"。
 * <p>为什么这样写：状态键按 {@code CertificateStatusEnum} 全量铺开、缺失补 0，
 * 前端卡片无需判空/兜底；值一律为 Long，避免前端 number 断言分叉。
 * <p>业务目标：看板 5 张卡片 + 状态分布图由 1 次请求取齐，且口径与列表筛选一致。
 *
 * <p>2026-09-28 对齐**前端已发布类型**（apps/web-antd/src/api/bidding/certificate/index.ts 的 CertificateStats）：
 * 前端读的是 {@code total/active/expiring/abnormal/cancelled/unverified/byStatus} 七个字段，
 * 原先只发 statusCounts/verificationCounts/typeCounts 会让卡片全读成 undefined。
 * 故这里**两个口径都发**：映射（statusCounts/…）给新前端，扁平桶（active/…）与 byStatus 兼容已发布前端；
 * 分桶规则**取自字典 color_type**（success/warning/error/info），不硬编码状态名——新增状态码只改字典即可。
 */
@Schema(description = "管理后台 - 证照看板统计")
@Data
public class CertificateStatsRespVO {

    @Schema(description = "证照总数（当前筛选条件下全量，不受分页影响）", example = "12")
    private Long total;

    @Schema(description = "台账状态计数（键=状态 code；六个状态全量返回，未出现补 0）")
    private Map<String, Long> statusCounts;

    @Schema(description = "验真状态计数（键=验真 code；四个状态全量返回，未出现补 0）")
    private Map<String, Long> verificationCounts;

    @Schema(description = "证照大类计数（键=大类 code；仅返回当前数据中出现过的大类）")
    private Map<String, Long> typeCounts;

    // ===== 以下为前端已发布契约字段（与上面映射同源；两个口径都发，避免前端改代码） =====

    @Schema(description = "按 certStatus 逐码计数（= statusCounts 的别名；前端契约字段名）")
    private Map<String, Long> byStatus;

    @Schema(description = "生效可用（字典 color_type=success 的状态计数之和）", example = "8")
    private Long active;

    @Schema(description = "临期（字典 color_type=warning 的状态计数之和，含即将到期/暂扣）", example = "2")
    private Long expiring;

    @Schema(description = "异常/过期（字典 color_type=error 的状态计数之和，含已过期/吊销）", example = "1")
    private Long abnormal;

    @Schema(description = "已注销（字典 color_type=info 的状态计数之和）", example = "0")
    private Long cancelled;

    @Schema(description = "未验真（verification_status=UNVERIFIED 计数）", example = "3")
    private Long unverified;
}

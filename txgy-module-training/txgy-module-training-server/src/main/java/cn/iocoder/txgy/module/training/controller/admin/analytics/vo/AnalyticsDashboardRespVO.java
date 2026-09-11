package cn.iocoder.txgy.module.training.controller.admin.analytics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数据统计与档案归档 - 统计看板 Response VO
 *
 * 首期（框架版）：全部指标基于现有培训表聚合，不引入新业务表。
 * 档案归档相关字段将在后续迭代中扩展。
 *
 * @author 云培训学院课题小组
 */
@Schema(description = "管理后台 - 数据统计看板 Response VO")
@Data
public class AnalyticsDashboardRespVO {

    @Schema(description = "课程规模统计")
    private CourseStats course;

    @Schema(description = "资源规模统计")
    private ResourceStats resource;

    @Schema(description = "互动与日志统计")
    private ActivityStats activity;

    @Schema(description = "近 7 天操作趋势（date -> count）")
    private List<Map<String, Object>> operationTrend7d;

    @Schema(description = "文档资源分类分布（categoryName -> count）")
    private List<Map<String, Object>> documentCategoryDistribution;

    @Schema(description = "最近操作日志（最多 10 条）")
    private List<RecentLogItem> recentLogs;

    @Schema(description = "课程规模统计")
    @Data
    public static class CourseStats {

        @Schema(description = "课程总数", example = "12")
        private Long total;

        @Schema(description = "已上架课程数", example = "5")
        private Long onShelf;

        @Schema(description = "草稿课程数", example = "3")
        private Long draft;

        @Schema(description = "待审核课程数", example = "1")
        private Long pendingReview;

        @Schema(description = "待上架课程数", example = "2")
        private Long approved;

        @Schema(description = "已下架课程数", example = "1")
        private Long offShelf;

        @Schema(description = "课程媒资绑定总数", example = "30")
        private Long mediaTotal;
    }

    @Schema(description = "资源规模统计")
    @Data
    public static class ResourceStats {

        @Schema(description = "文档总数", example = "20")
        private Long documentTotal;

        @Schema(description = "在线文档数", example = "15")
        private Long documentOnline;

        @Schema(description = "视频总数", example = "10")
        private Long videoTotal;

        @Schema(description = "在线视频数", example = "8")
        private Long videoOnline;

        @Schema(description = "试题总数", example = "100")
        private Long questionTotal;
    }

    @Schema(description = "互动与日志统计")
    @Data
    public static class ActivityStats {

        @Schema(description = "课程评论总数", example = "45")
        private Long commentTotal;

        @Schema(description = "课程审计日志总数", example = "60")
        private Long auditLogTotal;

        @Schema(description = "资源操作日志总数", example = "120")
        private Long operationLogTotal;

        @Schema(description = "近 7 天资源操作日志数", example = "30")
        private Long operationLogLast7d;
    }

    @Schema(description = "最近操作日志条目")
    @Data
    public static class RecentLogItem {

        @Schema(description = "日志编号", example = "1")
        private Long id;

        @Schema(description = "操作人", example = "admin")
        private String userName;

        @Schema(description = "操作动作", example = "CREATE")
        private String action;

        @Schema(description = "目标类型", example = "DOCUMENT")
        private String targetType;

        @Schema(description = "操作时间")
        private LocalDateTime createTime;
    }
}

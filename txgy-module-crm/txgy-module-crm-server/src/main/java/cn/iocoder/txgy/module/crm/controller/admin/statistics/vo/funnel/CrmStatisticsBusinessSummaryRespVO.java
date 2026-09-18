package cn.iocoder.txgy.module.crm.controller.admin.statistics.vo.funnel;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理后台 - CRM 商机统计通用结果 VO
 *
 * <p>用于「按商机阶段 / 按商机状态组 / 按丢单原因」三种汇总维度：
 * {@link #key} 为维度值，{@link #label} 为维度名称，前端可直接渲染漏斗、柱状图与饼图。
 *
 * @author 防腐保温项目组
 */
@Schema(description = "管理后台 - CRM 商机统计通用结果 VO")
@Data
public class CrmStatisticsBusinessSummaryRespVO {

    @Schema(description = "维度值：商机阶段 / 商机状态组 / 丢单原因", example = "3")
    private Integer key;

    @Schema(description = "维度名称", example = "需求确认")
    private String label;

    @Schema(description = "商机数量", example = "12")
    private Long count;

    @Schema(description = "商机总金额（元）", example = "120000.00")
    private BigDecimal totalPrice;

}

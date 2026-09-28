package cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch;
// 规范依据：设计文档 v0.6 §15.1（G 轨：平台级聚合无租户语义）；《招投标模块开发规范》五章

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/**
 * 采集批次（调度/人工跑批汇总）（G 轨）。表：bidding_fetch_job。
 */
@TenantIgnore // G 轨：表无 tenant_id 列，忽略租户拦截器（平台级数据，见 DO 注释）
@TableName("bidding_fetch_job")
@Data
@EqualsAndHashCode(callSuper = true)
public class CrawlJobDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID) // 雪花主键：表 id 无自增（见 DDL），显式注解压过全局 AUTO 策略

    private Long id;                      // 主键（雪花 ID）
    private String sourceCode;            // 源 code（空=全源）
    private String triggerType;           // SCHEDULE/MANUAL/REPAIR
    private String scope;                 // 范围说明（如按时间窗口）
    private String status;                // RUNNING/SUCCESS/PARTIAL/FAILED
    /** 生产端是否已投递完成（async：runNow 投递完置 true；tryFinalizeJob 要求 produced 且无未终态 task 才收尾，防提前收尾竞态） */
    private Boolean produced;
    private String summaryJson;           // 汇总：总数/成功/失败/转人工数（JSON）
    private LocalDateTime finishedAt;     // 结束时间
}

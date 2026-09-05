package cn.iocoder.txgy.module.bidding.dal.dataobject.crawlexternal;
// 规范依据：设计文档 v0.6 §15.1（G 轨：平台级聚合无租户语义）；《招投标模块开发规范》五章

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/**
 * 采集批次（调度/人工跑批汇总）（G 轨）。表：bidding_crawl_job。
 */
@TableName("bidding_crawl_job")
@Data
@EqualsAndHashCode(callSuper = true)
public class CrawlJobDO extends BaseDO {

    private Long id;                      // 主键（雪花 ID）
    private String sourceCode;            // 源 code（空=全源）
    private String triggerType;           // SCHEDULE/MANUAL/REPAIR
    private String scope;                 // 范围说明（如按时间窗口）
    private String status;                // RUNNING/SUCCESS/PARTIAL/FAILED
    private String summaryJson;           // 汇总：总数/成功/失败/转人工数（JSON）
    private LocalDateTime finishedAt;     // 结束时间
}

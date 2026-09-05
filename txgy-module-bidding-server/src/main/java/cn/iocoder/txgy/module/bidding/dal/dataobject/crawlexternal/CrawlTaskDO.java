package cn.iocoder.txgy.module.bidding.dal.dataobject.crawlexternal;
// 规范依据：设计文档 v0.6 §15.1（G 轨：平台级聚合无租户语义）；《招投标模块开发规范》五章

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/**
 * 采集任务（分布式：PENDING→RUNNING→SUCCESS/FAILED/SKIPPED，断点/重试≤3/worker 归属）（G 轨）。表：bidding_crawl_task。
 */
@TableName("bidding_crawl_task")
@Data
@EqualsAndHashCode(callSuper = true)
public class CrawlTaskDO extends BaseDO {

    private Long id;                      // 主键（雪花 ID）
    private Long jobId;                   // 所属批次（bidding_crawl_job.id）
    private String sourceCode;            // 源 code
    private String url;                   // 待抓 URL
    private String urlHash;               // URL SHA-256（去重）
    private String channelCode;           // 通道适配器 code
    private String parserCode;            // 解析/策略 code（URL 审批绑定或源默认）
    private Integer priority;             // 优先级（小=高）
    private String status;                // PENDING/RUNNING/SUCCESS/FAILED/SKIPPED
    private Integer attempt;              // 已尝试次数（MQ 侧 ≤3）
    private LocalDateTime nextRetryTime;  // 下次重试时间（指数退避）
    private String workerId;              // 执行节点标识（分布式抢占）
    private LocalDateTime startedAt;      // 开始时间
    private LocalDateTime finishedAt;     // 结束时间
    private String message;               // 结果/错误说明
}

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
 * 采集任务（分布式：PENDING→RUNNING→SUCCESS/FAILED/SKIPPED，断点/重试≤3/worker 归属）（G 轨）。表：bidding_fetch_task。
 */
@TenantIgnore // G 轨：表无 tenant_id 列，忽略租户拦截器（平台级数据，见 DO 注释）
@TableName("bidding_fetch_task")
@Data
@EqualsAndHashCode(callSuper = true)
public class CrawlTaskDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID) // 雪花主键：表 id 无自增（见 DDL），显式注解压过全局 AUTO 策略

    private Long id;                      // 主键（雪花 ID）
    private Long jobId;                   // 所属批次（bidding_fetch_job.id）
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
    /** 列表阶段条目快照 JSON（QYZB 全文 HTML / GX 标题日期；持久队列载体，Redis 消息仅带 taskId） */
    private String payloadJson;           // 载荷快照（DB 侧持久，Redis 不放大）
}

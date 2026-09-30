package cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 智慧标书审查 AI 调用流水（表 bidding_bid_review_ai_log）。
 *
 * <p>方案依据：§6.4（审计与计量、自检与告警）。
 * <p>设计遵循/针对：prompt 只落摘要哈希、不落全文——标书含报价/业绩等敏感内容（规范 §4.8），
 * 流水表本身只是"调用过没、多久、多大、成功没"，用于成本统计与失败率告警。
 */
@TableName("bidding_bid_review_ai_log")
@Data
@EqualsAndHashCode(callSuper = true)
public class BidReviewAiLogDO extends TenantBaseDO {

    private Long id;
    /** 审查任务 id（自检 probe 时为空） */
    private Long taskId;
    /** 步骤：RULE/GENERATE/REPORT/PROBE */
    private String step;
    /** 模型名 */
    private String model;
    /** 提示词摘要哈希 */
    private String promptDigest;
    /** 请求字符数 */
    private Integer requestChars;
    /** 响应字符数 */
    private Integer responseChars;
    /** 耗时（毫秒） */
    private Long costMs;
    /** 状态：SUCCESS/FAILED/DEGRADED/REUSE */
    private String status;
    /** 失败原因（脱敏） */
    private String errorMsg;
}

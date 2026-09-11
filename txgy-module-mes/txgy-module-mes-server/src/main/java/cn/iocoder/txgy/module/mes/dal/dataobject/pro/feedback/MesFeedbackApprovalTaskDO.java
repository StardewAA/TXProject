package cn.iocoder.txgy.module.mes.dal.dataobject.pro.feedback;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * MES 报工审批结果任务 DO
 *
 * 记录"报工审批 BPM 事件 → 回写报工单状态"的异步消费任务。
 * BPM 事件转发与 MES 事务提交存在时序差，因此收到事件先落任务表，由定时任务异步重试消费。
 *
 * @author OPENLAB BS
 */
@TableName("mes_feedback_approval_task")
@KeySequence("mes_feedback_approval_task_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesFeedbackApprovalTaskDO extends TenantBaseDO {

    /** 待处理：尚未成功消费，等待定时任务重试 */
    public static final Integer STATUS_PENDING = 0;
    /** 成功：审批结果已回写报工单 */
    public static final Integer STATUS_SUCCESS = 1;
    /** 失败：超过最大重试次数仍未成功，需人工处理 */
    public static final Integer STATUS_FAILED = 2;
    /** 跳过：报工单状态已不满足消费条件（如被人工处理） */
    public static final Integer STATUS_SKIPPED = 3;

    /** 最大重试次数 */
    public static final int MAX_RETRY_COUNT = 10;

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 报工单编号
     */
    private Long feedbackId;
    /**
     * BPM 流程实例编号（幂等键）
     */
    private String processInstanceId;
    /**
     * BPM 流程实例状态
     *
     * 2 审批通过、3 驳回
     */
    private Integer bpmStatus;
    /**
     * 审批原因/驳回意见
     */
    private String reason;
    /**
     * 任务状态
     *
     * 0 待处理、1 成功、2 失败、3 跳过
     */
    private Integer status;
    /**
     * 重试次数
     */
    private Integer retryCount;
    /**
     * 下次重试时间
     */
    private LocalDateTime nextRetryTime;
    /**
     * 失败原因
     */
    private String errorMessage;

}

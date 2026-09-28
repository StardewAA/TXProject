package cn.iocoder.txgy.module.mes.dal.dataobject.wm.productsales;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * MES 销售出库审批结果任务 DO
 *
 * 记录"销售出库审批 BPM 事件 → 回写出库单状态"的异步消费任务。
 * 与采购入库审批的 MesWmReceiptApprovalTaskDO 同构。
 *
 * @author OPENLAB BS
 */
@TableName("mes_wm_product_sales_approval_task")
@KeySequence("mes_wm_product_sales_approval_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesWmProductSalesApprovalTaskDO extends TenantBaseDO {

    /** 待处理：尚未成功消费，等待定时任务重试 */
    public static final Integer STATUS_PENDING = 0;
    /** 成功：审批结果已回写出库单 */
    public static final Integer STATUS_SUCCESS = 1;
    /** 失败：超过最大重试次数仍未成功，需人工处理 */
    public static final Integer STATUS_FAILED = 2;
    /** 跳过：出库单状态已不满足消费条件（如被人工处理） */
    public static final Integer STATUS_SKIPPED = 3;

    /** 最大重试次数 */
    public static final int MAX_RETRY_COUNT = 10;

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 销售出库单编号
     */
    private Long salesId;
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

package cn.iocoder.txgy.module.mes.dal.dataobject.pro.project;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * MES 项目同步任务 DO
 *
 * 记录"销售订单审批通过 → 自动生成生产项目"的同步任务。
 * BPM 事件与 ERP 事务提交存在时序差，因此收到事件先落任务表，由定时任务异步重试消费。
 *
 * @author OPENLAB BS
 */
@TableName("mes_pro_project_sync_task")
@KeySequence("mes_pro_project_sync_task_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesProProjectSyncTaskDO extends TenantBaseDO {

    /** 待处理：尚未成功消费，等待定时任务重试 */
    public static final Integer STATUS_PENDING = 0;
    /** 成功：项目已创建（或已存在） */
    public static final Integer STATUS_SUCCESS = 1;
    /** 失败：超过最大重试次数仍未成功，需人工处理 */
    public static final Integer STATUS_FAILED = 2;
    /** 跳过：订单被驳回/撤销，无需建项目 */
    public static final Integer STATUS_SKIPPED = 3;

    /** 最大重试次数 */
    public static final int MAX_RETRY_COUNT = 10;

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * ERP 销售订单编号（幂等键）
     */
    private Long saleOrderId;
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
    /**
     * 生成的项目编号（成功后回填）
     */
    private Long projectId;

}

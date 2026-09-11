package cn.iocoder.txgy.module.crm.dal.dataobject.aftersale;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CRM 售后回访任务 DO（执行实例）
 *
 * @author txgycloud
 */
@TableName("crm_after_sale_visit_task")
@KeySequence("crm_after_sale_visit_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmAfterSaleVisitTaskDO extends BaseDO {

    /**
     * 编号，主键自增
     */
    @TableId
    private Long id;
    /**
     * 来源计划编号，0=手动创建任务
     */
    private Long planId;
    /**
     * 质保档案编号
     */
    private Long warrantyId;
    /**
     * 客户编号（冗余）
     */
    private Long customerId;
    /**
     * 合同编号（冗余）
     */
    private Long contractId;
    /**
     * 处理人（售后人员或业务员）
     */
    private Long assigneeUserId;
    /**
     * 计划执行日期
     */
    private LocalDate taskDate;
    /**
     * 任务状态：1=待处理 2=已完成 3=已逾期
     */
    private Integer status;
    /**
     * 实际完成时间
     */
    private LocalDateTime finishTime;
    /**
     * 临期提醒发送日期（防重复）
     */
    private LocalDate overdueSentDate;
    /**
     * 任务备注/回访提示
     */
    private String remark;

}

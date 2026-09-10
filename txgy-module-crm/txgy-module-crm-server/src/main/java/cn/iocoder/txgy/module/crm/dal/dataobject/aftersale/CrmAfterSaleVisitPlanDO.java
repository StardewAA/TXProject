package cn.iocoder.txgy.module.crm.dal.dataobject.aftersale;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDate;

/**
 * CRM 售后回访计划 DO
 *
 * @author txgycloud
 */
@TableName("crm_after_sale_visit_plan")
@KeySequence("crm_after_sale_visit_plan_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmAfterSaleVisitPlanDO extends BaseDO {

    /**
     * 编号，主键自增
     */
    @TableId
    private Long id;
    /**
     * 计划名称
     */
    private String name;
    /**
     * 关联质保档案编号
     */
    private Long warrantyId;
    /**
     * 计划类型：1=手动 2=自动
     */
    private Integer planType;
    /**
     * 周期类型：1=每季度 2=每半年 3=每年 4=质保到期前重点回访
     */
    private Integer cycleType;
    /**
     * 周期倍数 或 到期前天数（cycleType=4 时）
     */
    private Integer cycleValue;
    /**
     * 计划生效开始日期
     */
    private LocalDate startDate;
    /**
     * 计划生效结束日期
     */
    private LocalDate endDate;
    /**
     * 任务处理人类型：1=售后人员 2=业务员
     */
    private Integer assigneeType;
    /**
     * 指定处理人（为空则按类型取售后责任人/业务负责人）
     */
    private Long assigneeUserId;
    /**
     * 默认回访方式：1=电话 2=上门 3=微信 4=邮件
     */
    private Integer visitType;
    /**
     * 计划状态：1=启用 2=停用 3=已完成
     */
    private Integer status;
    /**
     * 最近一次生成任务的日期（自动计划断点续排）
     */
    private LocalDate lastTaskDate;
    /**
     * 备注
     */
    private String remark;

}

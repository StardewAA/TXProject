package cn.iocoder.txgy.module.crm.dal.dataobject.aftersale;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * CRM 售后设置 DO（单行配置）
 *
 * @author txgycloud
 */
@TableName("crm_after_sale_config")
@KeySequence("crm_after_sale_config_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmAfterSaleConfigDO extends BaseDO {

    /**
     * 编号，主键自增
     */
    @TableId
    private Long id;
    /**
     * 质保到期提前提醒天数，范围 7-180
     */
    private Integer warrantyWarnDays;
    /**
     * 是否开启自动生成回访计划
     */
    private Boolean autoVisitEnabled;
    /**
     * 自动回访周期类型：1=每季度 2=每半年 3=每年 4=质保到期前重点回访
     */
    private Integer autoVisitCycleType;
    /**
     * 自动回访周期倍数/到期前天数
     */
    private Integer autoVisitCycleValue;
    /**
     * 回访任务临期提醒提前天数
     */
    private Integer taskRemindDays;
    /**
     * 回访任务逾期判定天数
     */
    private Integer taskOverdueDays;
    /**
     * 工单超时未处理预警天数
     */
    private Integer ticketOverdueDays;

}

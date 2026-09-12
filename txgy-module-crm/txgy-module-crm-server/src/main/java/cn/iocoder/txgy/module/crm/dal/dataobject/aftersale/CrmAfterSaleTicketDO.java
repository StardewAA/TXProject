package cn.iocoder.txgy.module.crm.dal.dataobject.aftersale;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * CRM 售后问题工单 DO
 *
 * @author txgycloud
 */
@TableName("crm_after_sale_ticket")
@KeySequence("crm_after_sale_ticket_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmAfterSaleTicketDO extends BaseDO {

    /**
     * 编号，主键自增
     */
    @TableId
    private Long id;
    /**
     * 工单编号，AT+yyyyMMddHHmmss+4位随机
     */
    private String no;
    /**
     * 问题标题
     */
    private String title;
    /**
     * 关联质保档案编号（可空）
     */
    private Long warrantyId;
    /**
     * 客户编号
     */
    private Long customerId;
    /**
     * 合同编号（可空）
     */
    private Long contractId;
    /**
     * 来源回访记录编号（客户报障为空）
     */
    private Long visitRecordId;
    /**
     * 工单来源：1=回访发现 2=客户报障
     */
    private Integer source;
    /**
     * 优先级：1=高 2=中 3=低
     */
    private Integer priority;
    /**
     * 问题类型（字典）
     */
    private String problemType;
    /**
     * 问题描述
     */
    private String description;
    /**
     * 报障联系人姓名（快照）
     */
    private String contactName;
    /**
     * 报障联系人电话（快照）
     */
    private String contactMobile;
    /**
     * 处理人（售后工程师/外协）
     */
    private Long assigneeUserId;
    /**
     * 工单状态：1=待处理 2=处理中 3=已完成 4=已关闭
     */
    private Integer status;
    /**
     * 处理结果说明
     */
    private String handleResult;
    /**
     * 开始处理时间
     */
    private LocalDateTime startTime;
    /**
     * 完成时间
     */
    private LocalDateTime finishTime;
    /**
     * 关闭归档时间
     */
    private LocalDateTime closeTime;
    /**
     * 关闭人编号
     */
    private Long closeUserId;
    /**
     * 是否转有偿服务（超质保范围）
     */
    private Boolean isChargeable;
    /**
     * 备注
     */
    private String remark;

}

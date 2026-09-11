package cn.iocoder.txgy.module.crm.dal.dataobject.aftersale;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * CRM 售后工单流转记录 DO（留痕）
 *
 * @author txgycloud
 */
@TableName("crm_after_sale_ticket_note")
@KeySequence("crm_after_sale_ticket_note_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmAfterSaleTicketNoteDO extends BaseDO {

    /**
     * 编号，主键自增
     */
    @TableId
    private Long id;
    /**
     * 工单编号
     */
    private Long ticketId;
    /**
     * 操作类型：1=指派 2=状态变更 3=处理记录 4=转有偿 5=关闭
     */
    private Integer operateType;
    /**
     * 处理内容/变更说明
     */
    private String content;
    /**
     * 操作人
     */
    private Long operatorUserId;

}

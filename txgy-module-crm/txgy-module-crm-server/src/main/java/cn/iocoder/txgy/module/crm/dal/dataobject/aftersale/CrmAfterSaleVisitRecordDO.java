package cn.iocoder.txgy.module.crm.dal.dataobject.aftersale;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.framework.mybatis.core.type.StringListTypeHandler;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CRM 售后回访记录 DO（含满意度，永久留存）
 *
 * @author txgycloud
 */
@TableName(value = "crm_after_sale_visit_record", autoResultMap = true)
@KeySequence("crm_after_sale_visit_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmAfterSaleVisitRecordDO extends BaseDO {

    /**
     * 编号，主键自增
     */
    @TableId
    private Long id;
    /**
     * 关联回访任务编号，手动登记可为空
     */
    private Long taskId;
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
     * 被访联系人编号，可空
     */
    private Long contactId;
    /**
     * 回访方式：1=电话 2=上门 3=微信 4=邮件
     */
    private Integer visitType;
    /**
     * 回访时间
     */
    private LocalDateTime visitTime;
    /**
     * 回访内容
     */
    private String content;
    /**
     * 满意度：5=非常满意 4=满意 3=一般 2=不满意 1=非常不满意
     */
    private Integer satisfactionLevel;
    /**
     * 满意度文字备注
     */
    private String satisfactionRemark;
    /**
     * 建议下次回访日期
     */
    private LocalDate nextVisitDate;
    /**
     * 现场照片 URL 数组(JSON)
     */
    @TableField(typeHandler = StringListTypeHandler.class)
    private List<String> picUrls;
    /**
     * 附件 URL 数组(JSON)
     */
    @TableField(typeHandler = StringListTypeHandler.class)
    private List<String> fileUrls;
    /**
     * 本次回访转出的问题工单编号（可空）
     */
    private Long ticketId;
    /**
     * 本次回访转出的新商机编号（可空）
     */
    private Long businessId;

}

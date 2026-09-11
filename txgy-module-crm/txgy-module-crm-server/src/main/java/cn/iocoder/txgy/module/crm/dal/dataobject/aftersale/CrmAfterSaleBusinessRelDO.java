package cn.iocoder.txgy.module.crm.dal.dataobject.aftersale;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * CRM 售后转商机关联 DO
 *
 * @author txgycloud
 */
@TableName("crm_after_sale_business_rel")
@KeySequence("crm_after_sale_business_rel_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmAfterSaleBusinessRelDO extends BaseDO {

    /**
     * 编号，主键自增
     */
    @TableId
    private Long id;
    /**
     * 新商机编号
     */
    private Long businessId;
    /**
     * 来源质保档案编号
     */
    private Long warrantyId;
    /**
     * 来源回访记录编号（可空）
     */
    private Long visitRecordId;
    /**
     * 客户编号
     */
    private Long customerId;
    /**
     * 商机内容方向：1=复购 2=续签 3=扩项/新工程
     */
    private Integer sourceType;
    /**
     * 转化说明
     */
    private String remark;

}

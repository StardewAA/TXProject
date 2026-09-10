package cn.iocoder.txgy.module.crm.dal.dataobject.aftersale;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CRM 售后质保档案 DO
 *
 * @author txgycloud
 */
@TableName("crm_after_sale_warranty")
@KeySequence("crm_after_sale_warranty_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmAfterSaleWarrantyDO extends BaseDO {

    /**
     * 编号，主键自增
     */
    @TableId
    private Long id;
    /**
     * 质保编号，WB+yyyyMMdd+3位流水
     */
    private String no;
    /**
     * 来源合同编号
     */
    private Long contractId;
    /**
     * 客户编号（冗余）
     */
    private Long customerId;
    /**
     * 质保标的-合同产品行编号，可空
     */
    private Long contractProductId;
    /**
     * 工程项目名称
     */
    private String projectName;
    /**
     * 项目完工验收日期（质保起算日，可手动修改）
     */
    private LocalDate projectAcceptDate;
    /**
     * 质保年限，1-5 年
     */
    private Integer warrantyYears;
    /**
     * 质保到期日期，默认=验收日期+年限-1天，可人工修改
     */
    private LocalDate warrantyEndDate;
    /**
     * 质保范围
     */
    private String warrantyScope;
    /**
     * 质保状态：1=质保中 2=即将到期 3=已到期终止
     */
    private Integer status;
    /**
     * 业务负责人（原合同业务员）
     */
    private Long ownerUserId;
    /**
     * 售后责任人
     */
    private Long serviceUserId;
    /**
     * 最近一次回访时间（冗余）
     */
    private LocalDateTime lastVisitTime;
    /**
     * 计划下次回访时间（冗余）
     */
    private LocalDateTime nextVisitTime;
    /**
     * 最近一次到期提醒发送日期（防重复推送）
     */
    private LocalDate remindSentDate;
    /**
     * 备注
     */
    private String remark;

}

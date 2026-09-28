package cn.iocoder.txgy.module.erp.dal.dataobject.purchase;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** ERP 采购计划 DO。 */
@TableName("erp_purchase_plan")
@KeySequence("erp_purchase_plan_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpPurchasePlanDO extends BaseDO {
    @TableId
    private Long id;
    private String no;
    /** 计划名称 */
    private String planName;
    /** 计划期间（如 2026-Q4 / 2026-09） */
    private String planPeriod;
    /** 需求部门编号 */
    private Long departmentId;
    /** 计划负责人编号 */
    private Long requesterId;
    /** 编制时间 */
    private LocalDateTime planTime;
    /** 计划开始日期 */
    private LocalDateTime startDate;
    /** 计划结束日期 */
    private LocalDateTime endDate;
    /** 审批状态 */
    private Integer status;
    /** 反审批原因 */
    private String reverseApproveReason;
    /** 计划总数量 */
    private BigDecimal totalCount;
    /** 预算金额 */
    private BigDecimal budgetAmount;
    /** 计划说明 */
    private String purpose;
    /** 附件地址 */
    private String fileUrl;
    private String remark;
}

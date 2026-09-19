package cn.iocoder.txgy.module.crm.dal.dataobject.business;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.txgy.module.crm.enums.business.CrmBusinessEndStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CRM 商机 DO
 *
 * @author ljlleo
 */
@TableName("crm_business")
@KeySequence("crm_business_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmBusinessDO extends BaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 商机名称
     */
    private String name;
    /**
     * 客户编号
     *
     * 关联 {@link CrmCustomerDO#getId()}
     */
    private Long customerId;

    /**
     * 跟进状态
     */
    private Boolean followUpStatus;
    /**
     * 累计跟进次数（每写一次跟进 +1，删除一条跟进 -1）
     */
    private Integer followUpCount;
    /**
     * 最后跟进时间
     */
    private LocalDateTime contactLastTime;
    /**
     * 下次联系时间
     */
    private LocalDateTime contactNextTime;

    /**
     * 负责人的用户编号
     *
     * 关联 AdminUserDO 的 id 字段
     */
    private Long ownerUserId;

    /**
     * 商机状态组编号
     *
     *  关联 {@link CrmBusinessStatusTypeDO#getId()}
     */
    private Long statusTypeId;
    /**
     * 商机状态编号
     *
     * 关联 {@link CrmBusinessStatusDO#getId()}
     */
    private Long statusId;

    /**
     * 商机阶段（商机阶段只表达推进过程，赢单/输单由按钮驱动）
     *
     * 枚举 {@link cn.iocoder.txgy.module.crm.enums.business.CrmBusinessStageEnum}
     */
    private Integer stage;
    /**
     * 商机状态组（只读派生：未开始/进行中/已赢单/已丢单）
     *
     * 枚举 {@link cn.iocoder.txgy.module.crm.enums.business.CrmBusinessStateEnum}
     */
    private Integer businessState;
    /**
     * 预估赢单率（%）：随阶段自动计算（5/10/40/60/70），赢单=100、输单=0
     */
    private Integer winRate;
    /**
     * 丢单原因（仅输单时填写）
     *
     * 枚举 {@link cn.iocoder.txgy.module.crm.enums.business.CrmBusinessLostReasonEnum}
     */
    private Integer lostReason;
    /**
     * 丢单备注（仅输单时填写）
     */
    private String lostRemark;
    /**
     * 签约日期（赢单时填写）
     */
    private LocalDateTime signDate;
    /**
     * 签约金额（赢单时填写）
     */
    private BigDecimal signAmount;
    /**
     * 关闭时间（赢单/丢单时间）
     */
    private LocalDateTime closedAt;
    /**
     * 关闭操作人
     */
    private Long closedBy;

    /**
     * 结束状态
     *
     * 枚举 {@link CrmBusinessEndStatusEnum}
     */
    private Integer endStatus;
    /**
     * 结束时的备注
     */
    private String endRemark;

    /**
     * 预计成交日期
     */
    private LocalDateTime dealTime;
    /**
     * 产品总金额，单位：元
     *
     * productPrice = ∑({@link CrmBusinessProductDO#getTotalPrice()})
     */
    private BigDecimal totalProductPrice;
    /**
     * 整单折扣，百分比
     */
    private BigDecimal discountPercent;
    /**
     * 商机总金额，单位：元
     */
    private BigDecimal totalPrice;
    /**
     * 备注
     */
    private String remark;

}

package cn.iocoder.txgy.module.crm.dal.dataobject.quotation;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.business.CrmBusinessDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.contract.CrmContractDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.txgy.module.crm.enums.common.CrmAuditStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CRM 报价单 DO
 *
 * 关联链路：客户(Customer) -> 商机(Business) -> 报价(Quotation) -> 合同(Contract)
 *
 * @author 防腐保温项目组
 */
@TableName("crm_quotation")
@KeySequence("crm_quotation_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmQuotationDO extends BaseDO {

    /**
     * 报价编号
     */
    @TableId
    private Long id;
    /**
     * 报价名称
     */
    private String name;
    /**
     * 报价单号
     */
    private String no;
    /**
     * 客户编号
     *
     * 关联 {@link CrmCustomerDO#getId()}
     */
    private Long customerId;
    /**
     * 商机编号，非必须
     *
     * 关联 {@link CrmBusinessDO#getId()}
     */
    private Long businessId;
    /**
     * 负责人的用户编号
     *
     * 关联 AdminUserDO 的 id 字段
     */
    private Long ownerUserId;
    /**
     * 报价日期
     */
    private LocalDateTime orderDate;
    /**
     * 有效期至，即报价截止日期
     */
    private LocalDateTime validUntil;
    /**
     * 工作流编号
     *
     * 关联 ProcessInstance 的 id 属性
     */
    private String processInstanceId;
    /**
     * 审批状态
     *
     * 枚举 {@link CrmAuditStatusEnum}
     */
    private Integer auditStatus;
    /**
     * 生成的合同编号，非必须。为空表示尚未转为合同
     *
     * 关联 {@link CrmContractDO#getId()}
     */
    private Long contractId;
    /**
     * 产品总金额，单位：元
     */
    private BigDecimal totalProductPrice;
    /**
     * 整单折扣
     */
    private BigDecimal discountPercent;
    /**
     * 报价总金额，单位：元
     */
    private BigDecimal totalPrice;
    /**
     * 备注
     */
    private String remark;

}

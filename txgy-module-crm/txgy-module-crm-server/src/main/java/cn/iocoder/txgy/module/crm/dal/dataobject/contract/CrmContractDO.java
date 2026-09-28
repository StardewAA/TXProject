package cn.iocoder.txgy.module.crm.dal.dataobject.contract;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.business.CrmBusinessDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.contact.CrmContactDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.txgy.module.crm.enums.common.CrmAuditStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CRM 合同 DO
 *
 * @author dhb52
 */
@TableName("crm_contract")
@KeySequence("crm_contract_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmContractDO extends BaseDO {

    /**
     * 合同编号
     */
    @TableId
    private Long id;
    /**
     * 合同名称
     */
    private String name;
    /**
     * 合同编号
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
     * 最后跟进时间
     */
    private LocalDateTime contactLastTime;

    /**
     * 负责人的用户编号
     *
     * 关联 AdminUserDO 的 id 字段
     */
    private Long ownerUserId;

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
     * 下单日期
     */
    private LocalDateTime orderDate;
    /**
     * 开始时间
     */
    private LocalDateTime startTime;
    /**
     * 结束时间
     */
    private LocalDateTime endTime;
    /**
     * 产品总金额，单位：元
     */
    private BigDecimal totalProductPrice;
    /**
     * 整单折扣
     */
    private BigDecimal discountPercent;
    /**
     * 合同总金额，单位：分
     */
    private BigDecimal totalPrice;
    /**
     * 客户签约人，非必须
     *
     * 关联 {@link CrmContactDO#getId()}
     */
    private Long signContactId;
    /**
     * 公司签约人，非必须
     *
     * 关联 AdminUserDO 的 id 字段
     */
    private Long signUserId;
    /**
     * 备注
     */
    private String remark;

    // ==================== 合同模板相关字段 ====================
    // 对应《采购合同-模板》《购销合同-模板》中所有需填写的空白项

    /**
     * 合同模板类型
     *
     * 枚举 {@link cn.iocoder.txgy.module.crm.enums.contract.CrmContractTemplateTypeEnum}
     */
    private Integer templateType;
    /**
     * 签订地点
     */
    private String signPlace;
    /**
     * 签订日期
     */
    private LocalDateTime signDate;
    /**
     * 项目编号
     */
    private String projectNo;
    /**
     * 采购项目名称 / 建设项目名称
     */
    private String projectName;
    /**
     * 地名，仅购销合同使用
     */
    private String projectArea;
    /**
     * 附件份数，仅购销合同使用
     */
    private Integer attachmentCopies;
    /**
     * 附件页数，仅购销合同使用
     */
    private Integer attachmentPages;
    /**
     * 合同份数
     */
    private Integer contractCopies;
    /**
     * 政府采购中心名称
     */
    private String gpcName;

    // ---------- 质量、交货与验收 ----------
    /**
     * 国家（行业）标准
     */
    private String qualityStandard;
    /**
     * 送样天数
     */
    private Integer sampleDays;
    /**
     * 交货期限天数
     */
    private Integer deliveryDays;
    /**
     * 交货到甲方指定地点天数
     */
    private Integer deliveryArriveDays;
    /**
     * 安装调试验收天数
     */
    private Integer installDays;
    /**
     * 最迟完成安装调试验收日期
     */
    private LocalDateTime deliveryDeadline;
    /**
     * 初步验收天数
     */
    private Integer firstAcceptDays;
    /**
     * 试用期天数
     */
    private Integer trialDays;
    /**
     * 最终验收天数
     */
    private Integer finalAcceptDays;
    /**
     * 甲方不组织验收、视同验收合格的天数
     */
    private Integer autoAcceptDays;
    /**
     * 维修次数上限，达到则甲方有权退货
     */
    private Integer maxRepairTimes;

    // ---------- 付款方式 ----------
    /**
     * 首付款金额，单位：元
     */
    private BigDecimal prepayAmount;
    /**
     * 首付支付天数
     */
    private Integer prepayDays;
    /**
     * 首付款比例，单位：%
     */
    private BigDecimal prepayRatio;
    /**
     * 验收合格后付款天数（购销合同为工作日）
     */
    private Integer acceptPayDays;
    /**
     * 验收款比例，单位：%
     */
    private BigDecimal acceptPayRatio;
    /**
     * 验收款金额，单位：元
     */
    private BigDecimal acceptPayAmount;
    /**
     * 履约保证金月数（购销合同为质保年数）
     */
    private Integer depositMonths;
    /**
     * 保证金退还天数
     */
    private Integer depositDays;
    /**
     * 保证金金额，单位：元
     */
    private BigDecimal depositAmount;
    /**
     * 保证金比例，单位：%
     */
    private BigDecimal depositRatio;

    // ---------- 售后服务 ----------
    /**
     * 质保年数
     */
    private Integer warrantyYears;
    /**
     * 响应到场小时数
     */
    private Integer responseHours;
    /**
     * 完成维修小时数
     */
    private Integer repairHours;
    /**
     * 质保期内维修次数上限
     */
    private Integer repairTimes;
    /**
     * 报修无法解决、提供代用品的天数
     */
    private Integer reportRepairDays;

    // ---------- 甲方（采购人）主体信息 ----------
    private String partyAName;
    private String partyALegalPerson;
    private String partyAAgent;
    private String partyAAddress;
    private String partyABankName;
    private String partyABankAccount;
    private String partyATel;
    private String partyAFax;

    // ---------- 乙方（供应商）主体信息 ----------
    private String partyBName;
    private String partyBLegalPerson;
    private String partyBAgent;
    private String partyBAddress;
    private String partyBBankName;
    private String partyBBankAccount;
    private String partyBTel;
    private String partyBFax;

    /**
     * 违约责任条款，JSON 格式
     *
     * 默认取合同配置 {@link CrmContractConfigDO} 的默认值，允许单份合同覆盖
     */
    private String penaltyTerms;

}

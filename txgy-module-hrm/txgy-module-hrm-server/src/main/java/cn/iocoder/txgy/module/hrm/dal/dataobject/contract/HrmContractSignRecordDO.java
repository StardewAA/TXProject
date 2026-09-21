package cn.iocoder.txgy.module.hrm.dal.dataobject.contract;

import cn.iocoder.txgy.framework.mybatis.core.type.StringListTypeHandler;
import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

/**
 * HRM 合同签署记录 DO
 *
 * <p>记录合同从"待签"到"已签/已作废"的签署过程，支撑线下签署件归档与签署催办。</p>
 *
 * @author OPENLAB BS
 */
@TableName(value = "hrm_contract_sign_record", autoResultMap = true)
@KeySequence("hrm_contract_sign_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrmContractSignRecordDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 合同编号（hrm_employee_contract.id）
     */
    private Long contractId;
    /**
     * 员工编号
     */
    private Long employeeId;
    /**
     * 合同编号（冗余，便于台账展示）
     */
    private String contractNo;
    /**
     * 签署用工主体编号（hrm_company_profile.id）
     */
    private Long signEmployerId;
    /**
     * 签署用工主体名称（冗余）
     */
    private String signEmployerName;
    /**
     * 签署人（乙方签署人姓名）
     */
    private String signPerson;
    /**
     * 签署方式：1 线下签署，2 电子签章
     *
     * @see cn.iocoder.txgy.module.hrm.enums.contract.HrmContractSignWayEnum
     */
    private Integer signWay;
    /**
     * 签署时间
     */
    private LocalDateTime signTime;
    /**
     * 签署件附件地址列表
     */
    @TableField(typeHandler = StringListTypeHandler.class)
    private List<String> fileUrls;
    /**
     * 状态：0 待签，1 已签，2 已作废
     *
     * @see cn.iocoder.txgy.module.hrm.enums.contract.HrmContractSignStatusEnum
     */
    private Integer status;
    /**
     * 催办次数
     */
    private Integer urgeCount;
    /**
     * 最后催办时间
     */
    private LocalDateTime lastUrgeTime;
    /**
     * 备注
     */
    private String remark;

}

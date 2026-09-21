package cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment;

import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.txgy.framework.mybatis.core.type.StringListTypeHandler;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeContractStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeContractTypeEnum;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * HRM 员工合同 DO
 *
 * @author OPENLAB BS
 */
@TableName(value = "hrm_employee_contract", autoResultMap = true)
@KeySequence("hrm_employee_contract_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrmEmployeeContractDO extends TenantBaseDO {

    /**
     * 合同编号
     */
    @TableId
    private Long id;
    /**
     * 员工编号
     *
     * 关联 {@link HrmEmployeeDO#getId()}
     */
    private Long employeeId;
    /**
     * 合同编号
     */
    private String no;
    /**
     * 合同模板编号
     *
     * 关联 {@link cn.iocoder.txgy.module.hrm.dal.dataobject.contract.HrmContractTemplateDO#getId()}
     */
    private Long templateId;
    /**
     * 来源 Offer 编号
     *
     * 关联 {@link cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitOfferDO#getId()}
     */
    private Long sourceOfferId;
    /**
     * 工作内容（劳务内容）
     */
    private String workContent;
    /**
     * 工作地点
     */
    private String workLocation;
    /**
     * 工作任务（以完成一定工作任务为期限时）
     */
    private String workTask;
    /**
     * 计薪方式：1 月薪，2 日薪，3 计件
     */
    private Integer payMode;
    /**
     * 计薪金额/单价
     */
    private BigDecimal payAmount;
    /**
     * 计薪单位，如 元/月、元/日、元/件
     */
    private String payUnit;
    /**
     * 每月发薪日
     */
    private Integer payDay;
    /**
     * 商业保险说明
     */
    private String commercialInsurance;
    /**
     * 考勤方式
     */
    private String attendanceMode;
    /**
     * 合同类型
     *
     * 枚举 {@link HrmEmployeeContractTypeEnum}
     */
    private Integer type;
    /**
     * 合同开始日期
     */
    private LocalDateTime startTime;
    /**
     * 合同结束日期
     */
    private LocalDateTime endTime;
    /**
     * 合同期限，单位：年
     */
    private Integer term;
    /**
     * 合同状态
     *
     * 枚举 {@link HrmEmployeeContractStatusEnum}
     */
    private Integer status;
    /**
     * 签约公司
     */
    private String signCompany;
    /**
     * 合同签订日期
     */
    private LocalDateTime signTime;
    /**
     * 备注
     */
    private String remark;
    /**
     * 是否到期提醒
     *
     */
    private Boolean expireRemind;
    /**
     * 显示顺序
     */
    private Integer sort;
    /**
     * 附件地址数组
     */
    @TableField(typeHandler = StringListTypeHandler.class)
    private List<String> fileUrls;

}

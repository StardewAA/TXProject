package cn.iocoder.txgy.module.hrm.dal.dataobject.employment;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.txgy.module.hrm.enums.employment.HrmSalaryModeEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * HRM 用工形式 DO
 *
 * <p>行业背景：防腐保温属于工业施工，人随项目走、队伍以班组承包为主。
 * 同一家公司同时存在正式员工、临时工（项目制短工）、劳务用工、劳务派遣、兼职、实习、返聘、日结工、计件工等多种用工形式，
 * 每种用工形式对应的合同模板、计薪方式、出勤口径、是否参与转正都不一样，必须先定"用工形式"，后面才谈得上签对合同、算对钱。</p>
 *
 * @author OPENLAB BS
 */
@TableName("hrm_employment_type")
@KeySequence("hrm_employment_type_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrmEmploymentTypeDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 用工形式名称
     */
    private String name;
    /**
     * 用工形式编码
     */
    private String code;
    /**
     * 默认合同模板编号（hrm_contract_template.id）
     */
    private Long contractTemplateId;
    /**
     * 默认计薪方式
     *
     * 枚举 {@link HrmSalaryModeEnum}
     */
    private Integer salaryMode;
    /**
     * 是否按工日统计出勤
     */
    private Boolean needWorkDay;
    /**
     * 是否要求人脸打卡
     */
    private Boolean needFaceClock;
    /**
     * 是否参与自动转正
     */
    private Boolean joinRegular;
    /**
     * 显示顺序
     */
    private Integer sort;
    /**
     * 状态：0 启用，1 停用
     *
     * 枚举 {@link cn.iocoder.txgy.framework.common.enums.CommonStatusEnum}
     */
    private Integer status;
    /**
     * 备注
     */
    private String remark;

}

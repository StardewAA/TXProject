package cn.iocoder.txgy.module.hrm.dal.dataobject.company;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
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
 * HRM 公司信息（用工主体）DO
 *
 * <p>作为合同甲方信息的来源，例如劳务合同中的公司名称、统一社会信用代码、住所、法定代表人等。</p>
 *
 * @author OPENLAB BS
 */
@TableName("hrm_company_profile")
@KeySequence("hrm_company_profile_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrmCompanyProfileDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 公司名称（用工主体）
     */
    private String name;
    /**
     * 统一社会信用代码
     */
    private String creditCode;
    /**
     * 住所
     */
    private String address;
    /**
     * 法定代表人
     */
    private String legalPerson;
    /**
     * 成立日期
     */
    private java.time.LocalDate establishDate;
    /**
     * 注册资本
     */
    private String registeredCapital;
    /**
     * 企业类型
     */
    private String companyType;
    /**
     * 经营范围
     */
    private String businessScope;
    /**
     * 参保人数
     */
    private Integer insuredCount;
    /**
     * 联系电话
     */
    private String phone;
    /**
     * 开户银行
     */
    private String bankName;
    /**
     * 银行账号
     */
    private String bankAccount;
    /**
     * 商业保险说明（雇主责任险/团体意外险）
     */
    private String commercialInsurance;
    /**
     * 是否默认主体
     */
    private Boolean defaultStatus;
    /**
     * 显示顺序
     */
    private Integer sort;
    /**
     * 备注
     */
    private String remark;

}

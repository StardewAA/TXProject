package cn.iocoder.txgy.module.hrm.dal.dataobject.employee.headcount;

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
 * HRM 部门编制 DO
 *
 * <p>按"部门"维度设定编制人数（项目部/工区/职能部门统一口径），
 * 与在册人数对比得出缺口，供组织管理页与 HR 工作台使用。
 * 部门本身仍由 system 的部门树维护，本表只登记编制数。</p>
 *
 * @author OPENLAB BS
 */
@TableName("hrm_dept_headcount")
@KeySequence("hrm_dept_headcount_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrmDeptHeadcountDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 部门编号
     *
     * 关联 system 的部门编号
     */
    private Long deptId;
    /**
     * 编制人数
     */
    private Integer headcount;
    /**
     * 备注
     */
    private String remark;

}

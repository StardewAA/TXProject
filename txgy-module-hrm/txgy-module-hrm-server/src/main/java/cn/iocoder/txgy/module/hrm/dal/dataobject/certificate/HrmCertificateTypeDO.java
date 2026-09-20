package cn.iocoder.txgy.module.hrm.dal.dataobject.certificate;

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
 * HRM 证照类型字典 DO
 *
 * <p>防腐保温是强证照行业：高处作业、焊接与热切割、架子工、喷砂、探伤都是特种作业，
 * 证照类型上要标记「是否特种作业 / 是否必持 / 预警提前天数」，上岗卡控才有依据。</p>
 *
 * @author OPENLAB BS
 */
@TableName("hrm_certificate_type")
@KeySequence("hrm_certificate_type_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrmCertificateTypeDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 证照类型名称
     */
    private String name;
    /**
     * 编码
     */
    private String code;
    /**
     * 是否特种作业
     */
    private Boolean specialOperation;
    /**
     * 是否必持（缺证不得上岗）
     */
    private Boolean mandatory;
    /**
     * 是否需要复审
     */
    private Boolean needReview;
    /**
     * 到期预警提前天数
     */
    private Integer warnDays;
    /**
     * 发证机关
     */
    private String issuingAuthority;
    /**
     * 显示顺序
     */
    private Integer sort;
    /**
     * 状态：0 启用，1 停用
     */
    private Integer status;
    /**
     * 备注
     */
    private String remark;

}

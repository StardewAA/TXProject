package cn.iocoder.txgy.module.hrm.dal.dataobject.certificate;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeCertificateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.enums.certificate.HrmCertificateRenewalTypeEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * HRM 证件续证/复审记录 DO
 *
 * 续证/复审只追加历史记录，不覆盖原证件：本表保存办理前的原有效期快照与办理后的新有效期，
 * 台账主表由 Service 在事务内更新为新值，附件同样只追加不删除。
 *
 * @author OPENLAB BS
 */
@TableName("hrm_certificate_renewal")
@KeySequence("hrm_certificate_renewal_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrmCertificateRenewalDO extends BaseDO {

    /**
     * 续证/复审记录编号
     */
    @TableId
    private Long id;
    /**
     * 证件台账编号
     *
     * 关联 {@link HrmEmployeeCertificateDO#getId()}
     */
    private Long certificateId;
    /**
     * 员工编号
     *
     * 关联 {@link HrmEmployeeDO#getId()}
     */
    private Long employeeId;
    /**
     * 续证/复审类型
     *
     * 枚举 {@link HrmCertificateRenewalTypeEnum}
     */
    private Integer type;
    /**
     * 办理前证件编号
     */
    private String oldNo;
    /**
     * 办理后证件编号
     */
    private String newNo;
    /**
     * 办理前有效开始日期
     */
    private LocalDateTime oldStartTime;
    /**
     * 办理前有效结束日期
     */
    private LocalDateTime oldEndTime;
    /**
     * 办理后有效开始日期
     */
    private LocalDateTime newStartTime;
    /**
     * 办理后有效结束日期
     */
    private LocalDateTime newEndTime;
    /**
     * 复审日期
     *
     * 仅复审类型有值
     */
    private LocalDateTime reviewTime;
    /**
     * 发证机关
     */
    private String issuingAuthority;
    /**
     * 发证日期
     */
    private LocalDateTime issuingTime;
    /**
     * 新证件附件地址
     *
     * 关联统一文件服务的文件地址，替换只追加新版本
     */
    private String attachmentUrl;
    /**
     * 经办人用户编号
     */
    private Long operatorUserId;
    /**
     * 办理时间
     */
    private LocalDateTime renewTime;
    /**
     * 备注
     */
    private String remark;

}

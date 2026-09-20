package cn.iocoder.txgy.module.hrm.dal.dataobject.certificate;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
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
 * HRM 证件类型 DO
 *
 * 证件类型是资格证件域的配置源，维护是否特种作业、是否需要复审、复审周期与默认提前预警天数，
 * 证件台账与每日预警任务均读取本表配置。
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
public class HrmCertificateTypeDO extends BaseDO {

    /**
     * 证件类型编号
     */
    @TableId
    private Long id;
    /**
     * 证件类型名称
     */
    private String name;
    /**
     * 证件类型编码
     */
    private String code;
    /**
     * 是否特种作业
     */
    private Boolean specialOperation;
    /**
     * 是否需要复审
     */
    private Boolean needReview;
    /**
     * 复审周期（月）
     *
     * 仅当 {@link #needReview} 为 true 时生效
     */
    private Integer reviewCycleMonths;
    /**
     * 默认提前预警天数
     *
     * 为 null 时按 {@link cn.iocoder.txgy.module.hrm.service.certificate.HrmCertificateStatusCalculator#DEFAULT_ADVANCE_DAYS} 兜底
     */
    private Integer advanceDays;
    /**
     * 是否必须上传证件附件
     */
    private Boolean requireAttachment;
    /**
     * 状态
     *
     * 枚举 {@link CommonStatusEnum}
     */
    private Integer status;
    /**
     * 显示顺序
     */
    private Integer sort;
    /**
     * 备注
     */
    private String remark;

}

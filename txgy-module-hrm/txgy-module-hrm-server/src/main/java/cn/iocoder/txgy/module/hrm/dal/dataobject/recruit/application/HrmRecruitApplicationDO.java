package cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** HRM 招聘应聘记录 DO。 */
@TableName("hrm_recruit_application")
@KeySequence("hrm_recruit_application_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class HrmRecruitApplicationDO extends TenantBaseDO {
    @TableId
    private Long id;
    /** 创建该应聘记录的登录用户编号；管理端历史数据允许为空。 */
    private Long applicantUserId;
    private Long candidateId;
    private Long postId;
    private Long resumeId;
    private Long channelId;
    private String status;
    private LocalDateTime statusUpdateTime;
    private String eliminateReason;
    private String withdrawReason;
    private String sourceRemark;
    private Long employeeId;
    private LocalDateTime onboardTime;
    private Integer version;
}

package cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("hrm_recruit_offer")
@KeySequence("hrm_recruit_offer_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class HrmRecruitOfferDO extends BaseDO {
    @TableId private Long id;
    private Long applicationId;
    private String offerNo;
    private String approvalStatus;
    private BigDecimal salaryAmount;
    private Integer salaryUnit;
    private Long postId;
    private Long entryPostId;
    private LocalDate plannedOnboardDate;
    private LocalDate validUntil;
    private String attachmentIds;
    private String processInstanceId;
    private Integer approvalVersion;
    private Long employeeId;
    private LocalDateTime sentTime;
    private LocalDateTime confirmTime;
    private String remark;
}

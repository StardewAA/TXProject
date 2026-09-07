package cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("hrm_recruit_application_log")
@KeySequence("hrm_recruit_application_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class HrmRecruitApplicationLogDO extends BaseDO {
    @TableId
    private Long id;
    private Long applicationId;
    private String fromStatus;
    private String toStatus;
    private String action;
    private String reason;
    private Long operatorId;
    private String processInstanceId;
    private String requestId;
}

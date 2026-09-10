package cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.resume;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("hrm_recruit_resume")
@KeySequence("hrm_recruit_resume_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class HrmRecruitResumeDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long candidateId;
    private String fileName;
    private String fileUrl;
    private Integer versionNo;
    /** 0: current, 1: replaced */
    private Integer status;
    private String remark;

    /**
     * 旧候选人 resume_urls 中的附件下标，仅用于兼容展示，不落库。
     */
    @TableField(exist = false)
    private Integer legacyIndex;
}

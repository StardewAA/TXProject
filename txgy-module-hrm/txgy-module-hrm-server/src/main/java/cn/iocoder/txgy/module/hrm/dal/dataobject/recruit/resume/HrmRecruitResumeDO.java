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
     * 在线结构化简历的内容快照。
     *
     * <p>投递时把求职者选定的个人简历版本内容原样固化到企业租户内，
     * 此后求职者修改或删除自己的简历都不会改变这份历史快照。</p>
     */
    private String content;
    /** 内容指纹，用于判断快照内容是否发生变化 */
    private String contentHash;
    /** 来源个人简历版本编号，便于追溯投递当时使用的版本 */
    private Long sourcePersonalVersionId;
    /** 投递职位编号 */
    private Long postId;
    /** 投递时的职位内容指纹，职位后续变更时可判断原分析是否过期 */
    private String postFingerprint;

    /**
     * 旧候选人 resume_urls 中的附件下标，仅用于兼容展示，不落库。
     */
    @TableField(exist = false)
    private Integer legacyIndex;
}

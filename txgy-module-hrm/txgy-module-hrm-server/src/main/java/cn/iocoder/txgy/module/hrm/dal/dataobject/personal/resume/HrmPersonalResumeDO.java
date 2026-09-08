package cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 个人用户简历 DO。
 *
 * <p>与企业招聘流程中的候选人简历分开存储，使用登录用户编号作为归属。</p>
 */
@TableName("hrm_personal_resume")
@KeySequence("hrm_personal_resume_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class HrmPersonalResumeDO extends TenantBaseDO {

    @TableId
    private Long id;
    /** 登录用户编号 */
    private Long userId;
    /** 简历标题 */
    private String title;
    /** 1 附件简历，2 在线简历 */
    private Integer resumeType;
    /** 附件名称 */
    private String fileName;
    /** 附件地址 */
    private String fileUrl;
    /** 在线填写内容，当前作为占位文本保存 */
    private String content;
    /** 备注 */
    private String remark;
}

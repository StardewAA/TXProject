package cn.iocoder.txgy.module.hrm.dal.dataobject.agent;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * HRM 招聘 Agent 分析结果 DO。
 *
 * <p>只有任务成功时才会写入；失败任务不得留下半成品结论。</p>
 */
@TableName("hrm_recruit_agent_analysis")
@KeySequence("hrm_recruit_agent_analysis_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class HrmRecruitAgentAnalysisDO extends TenantBaseDO {

    @TableId
    private Long id;
    /** 所属任务编号 */
    private Long taskId;
    /** 场景，见 HrmRecruitAgentSceneEnum */
    private String scene;
    /** 输入的个人简历版本编号 */
    private Long resumeVersionId;
    /** 关联职位编号 */
    private Long postId;
    /** 分析时的职位内容指纹，职位变化后可判定结论过期 */
    private String postFingerprint;
    /** 综合得分，无评分场景为空 */
    private Integer overallScore;
    /** 结构化分析结果（分项、依据、优势、缺口、待确认项） */
    private String resultJson;
    /** 结论摘要 */
    private String summary;
    /** 提示词版本 */
    private String promptVersion;
    /** 生成该结论的模型 */
    private String model;
    /** 结构化结果的 SHA-256 指纹，用于审计与结果完整性校验 */
    private String resultHash;
}

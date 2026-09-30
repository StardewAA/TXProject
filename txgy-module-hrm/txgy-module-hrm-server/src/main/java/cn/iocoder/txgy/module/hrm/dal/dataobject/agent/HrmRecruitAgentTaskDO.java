package cn.iocoder.txgy.module.hrm.dal.dataobject.agent;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * HRM 招聘 Agent 任务 DO。
 *
 * <p>记录场景、业务对象、幂等键、状态、模型与提示词版本，
 * 用于幂等去重、失败追溯以及「模型/提示词版本/调用状态」的审计要求。</p>
 *
 * <p>本表不保存简历原文与模型原始响应，避免在通用任务表中堆积敏感内容。</p>
 */
@TableName("hrm_recruit_agent_task")
@KeySequence("hrm_recruit_agent_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class HrmRecruitAgentTaskDO extends TenantBaseDO {

    @TableId
    private Long id;
    /** 发起用户编号 */
    private Long userId;
    /** 场景，见 HrmRecruitAgentSceneEnum */
    private String scene;
    /** 业务对象类型 */
    private String bizType;
    /** 业务对象编号 */
    private Long bizId;
    /**
     * 输入的简历版本编号。
     *
     * <p>个人端为本人固化的个人简历版本编号；
     * 管理端为应聘记录关联的候选人简历快照编号。</p>
     */
    private Long resumeVersionId;
    /** 输入或关联的职位编号 */
    private Long postId;
    /** 任务发起时的职位内容指纹 */
    private String postFingerprint;
    /** 幂等键 */
    private String idempotencyKey;
    /** 状态，见 HrmRecruitAgentTaskStatusEnum */
    private String status;
    /** 实际使用的模型标识 */
    private String model;
    /** 提示词版本 */
    private String promptVersion;
    private LocalDateTime startTime;
    private LocalDateTime finishTime;
    /** 耗时（毫秒） */
    private Long durationMs;
    /** 提示词 token 数，调用量审计用；提供方未返回用量时为空 */
    private Integer promptTokens;
    /** 输出 token 数 */
    private Integer completionTokens;
    /** 合计 token 数 */
    private Integer totalTokens;
    /** 失败错误码 */
    private String errorCode;
    /** 失败原因，不得包含简历原文 */
    private String errorMessage;
}

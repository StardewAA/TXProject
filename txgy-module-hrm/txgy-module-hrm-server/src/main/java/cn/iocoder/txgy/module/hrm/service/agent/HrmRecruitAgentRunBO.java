package cn.iocoder.txgy.module.hrm.service.agent;

import cn.hutool.json.JSONObject;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostDO;
import cn.iocoder.txgy.module.hrm.enums.agent.HrmRecruitAgentSceneEnum;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.function.BiFunction;

/**
 * 一次 Agent 场景执行所需的全部输入。
 *
 * <p>个人端与管理端在「归属校验」这一步上完全不同，因此调用方先完成校验，
 * 再把已经确认可用的简历内容与职位交给执行器；执行器只负责
 * 脱敏白名单、幂等任务、模型调用、结构化校验与落库这些**两边必须一致**的环节。</p>
 */
@Data
@Accessors(chain = true)
public class HrmRecruitAgentRunBO {

    /** 发起用户编号，个人端为本人，管理端为 HR */
    private Long userId;
    /** 场景 */
    private HrmRecruitAgentSceneEnum scene;
    /** 提示词版本 */
    private String promptVersion;
    /** 系统提示词 */
    private String systemPrompt;
    /** 简历内容（个人端为个人简历版本内容，管理端为候选人简历快照内容） */
    private String resumeContent;
    /**
     * 写入任务与结果的简历版本编号。
     *
     * <p>个人端为个人简历版本编号，管理端为候选人简历快照编号。</p>
     */
    private Long resumeVersionId;
    /** 业务对象类型，见 HrmRecruitAgentTaskCreateBO */
    private String bizType;
    /** 业务对象编号 */
    private Long bizId;
    /** 目标职位，已由调用方校验存在且可分析 */
    private HrmRecruitPostDO post;
    /** 幂等键 */
    private String idempotencyKey;
    /** 结果结构化校验器，第二入参为简历原文，供需要比对原文的场景使用 */
    private BiFunction<String, String, JSONObject> resultValidator;
}

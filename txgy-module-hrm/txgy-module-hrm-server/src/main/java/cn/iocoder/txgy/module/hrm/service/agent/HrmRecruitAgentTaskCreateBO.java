package cn.iocoder.txgy.module.hrm.service.agent;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 创建 Agent 任务的服务层入参。
 */
@Data
@Accessors(chain = true)
public class HrmRecruitAgentTaskCreateBO {

    /** 场景，见 HrmRecruitAgentSceneEnum */
    private String scene;
    /** 业务对象类型 */
    private String bizType;
    /** 业务对象编号 */
    private Long bizId;
    /** 输入的个人简历版本编号 */
    private Long resumeVersionId;
    /** 输入或关联的职位编号 */
    private Long postId;
    /** 任务发起时的职位内容指纹 */
    private String postFingerprint;
    /** 幂等键 */
    private String idempotencyKey;
}

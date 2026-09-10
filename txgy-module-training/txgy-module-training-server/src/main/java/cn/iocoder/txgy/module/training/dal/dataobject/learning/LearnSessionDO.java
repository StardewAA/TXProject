package cn.iocoder.txgy.module.training.dal.dataobject.learning;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.txgy.module.training.enums.learner.LearnMediaTypeEnum;
import cn.iocoder.txgy.module.training.enums.learner.LearnerSessionStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 学习会话 DO
 *
 * <p>对应表 training_learner_session：一次「开始→心跳→结束」的完整学习过程。
 * 心跳计费规则：delta ∈ [1, max_delta] 秒计入有效学时；超过心跳超时则服务端自动结束；
 * 有效学时不足 / 进度条异常拖动等由服务端心跳校验兜底，防挂机。</p>
 *
 * @author 云培训学院课题小组
 */
@TableName("training_learner_session")
@KeySequence("training_learner_session_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LearnSessionDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 学员用户 ID
     */
    private Long userId;
    /**
     * 课程 ID（直播/回放等独立学习时为 NULL）
     */
    private Long courseId;
    /**
     * 学习任务 ID（直播/回放独立学习时为 NULL）
     */
    private Long taskId;
    /**
     * 可学习媒资类型：见 {@link LearnMediaTypeEnum}：video/document/live/replay
     */
    private String mediaType;
    /**
     * 媒资 ID：课程媒资 course_media.id 或直播房间 live_room.id
     */
    private Long mediaId;
    /**
     * 会话开始时间
     */
    private LocalDateTime startAt;
    /**
     * 会话结束时间
     */
    private LocalDateTime endAt;
    /**
     * 有效学习秒数（服务端心跳累计，已排除异常心跳）
     */
    private Integer validSeconds;
    /**
     * 状态：见 {@link LearnerSessionStatusEnum}：active/ended/invalid
     */
    private String status;
    /**
     * 结束原因（normal 正常结束 / timeout 心跳超时 / cheat 验证失败 / clock 心跳过快等）
     */
    private String endReason;
    /**
     * 逻辑删除标记
     */
    @TableLogic
    private Boolean deleted;

}

package cn.iocoder.txgy.module.training.dal.dataobject.learning;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 学习打卡记录 DO
 *
 * <p>对应表 training_checkin_record：会话有效学时满 15 秒后可打卡一次，
 * 打卡记录快照该会话已累计的有效秒数。</p>
 *
 * @author 云培训学院课题小组
 */
@TableName("training_checkin_record")
@KeySequence("training_checkin_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CheckinRecordDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 学习会话 ID
     */
    private Long sessionId;
    /**
     * 学员用户 ID
     */
    private Long userId;
    /**
     * 课程 ID
     */
    private Long courseId;
    /**
     * 课程媒资 ID
     */
    private Long mediaId;
    /**
     * 媒资类型：video/document/live/replay
     */
    private String mediaType;
    /**
     * 打卡前本会话已累计有效秒数
     */
    private Integer validSecondsBefore;
    /**
     * 打卡时间
     */
    private LocalDateTime checkinAt;
    /**
     * 逻辑删除标记
     */
    @TableLogic
    private Boolean deleted;

}

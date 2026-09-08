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
 * 直播弹幕 DO
 *
 * <p>对应表 training_live_danmaku：直播互动消息（弹幕），由前端轮询拉取。</p>
 *
 * @author 云培训学院课题小组
 */
@TableName("training_live_danmaku")
@KeySequence("training_live_danmaku_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LiveDanmakuDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 直播房间 ID
     */
    private Long roomId;
    /**
     * 发送人用户 ID
     */
    private Long userId;
    /**
     * 发送人昵称快照
     */
    private String userName;
    /**
     * 弹幕内容
     */
    private String content;
    /**
     * 发送时间
     */
    private LocalDateTime createAt;
    /**
     * 逻辑删除标记
     */
    @TableLogic
    private Boolean deleted;

}

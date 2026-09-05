package cn.iocoder.txgy.module.training.dal.dataobject.learning;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 直播房间 DO
 *
 * <p>对应表 training_live_room：讲师授课直播间；结束后的直播可挂载回放视频地址
 * （回放观看走 replay 学习会话，计入个人学习轨迹的直播/回放学时）。</p>
 *
 * @author 云培训学院课题小组
 */
@TableName("training_live_room")
@KeySequence("training_live_room_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class LiveRoomDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 直播标题
     */
    private String title;
    /**
     * 封面图 URL
     */
    private String coverUrl;
    /**
     * 讲师姓名
     */
    private String lecturerName;
    /**
     * 直播流地址（演示环境为平台内视频地址）
     */
    private String streamUrl;
    /**
     * 状态：0 未开始 1 直播中 2 已结束
     */
    private Integer status;
    /**
     * 计划开始时间
     */
    private LocalDateTime planStartAt;
    /**
     * 计划结束时间
     */
    private LocalDateTime planEndAt;
    /**
     * 回放视频地址（结束后提供，观看走 replay 会话）
     */
    private String replayVideoUrl;
    /**
     * 讲师课时（小时，结算用）
     */
    private BigDecimal creditHours;
    /**
     * 排序
     */
    private Integer sort;
    /**
     * 备注
     */
    private String remark;
    /**
     * 逻辑删除标记
     */
    @TableLogic
    private Boolean deleted;

}

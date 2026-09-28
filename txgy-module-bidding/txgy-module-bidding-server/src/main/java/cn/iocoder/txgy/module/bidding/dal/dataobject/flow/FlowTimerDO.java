package cn.iocoder.txgy.module.bidding.dal.dataobject.flow;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 招标全流程时间驱动调度记录（G 轨：后台调度无租户上下文）。表：bidding_flow_timer。
 *
 * <p>回应用户关切：不做「定时任务轮询全表」，而是保存时注册 trigger_time（用户配置、精确到秒），
 * 调度器只扫「到期且 WAITING」的轻量索引行，执行后 DONE/SKIPPED（幂等）。
 */
@TableName("bidding_flow_timer")
@Data
@EqualsAndHashCode(callSuper = true)
public class FlowTimerDO extends BaseDO {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 实体类型：PROJECT/TENDER_PROJECT/BID_SECTION */
    private String bizType;
    /** 业务编号 */
    private String bizCode;
    /** 时间轴事件：BIDDING_STARTED/BIDDING_ENDED/EXPIRE */
    private String event;
    /** 触发时间（用户配置，精确到秒） */
    private LocalDateTime triggerTime;
    /** 状态：WAITING/DONE/SKIPPED */
    private String status;
    /** 实际执行时间 */
    private LocalDateTime executedTime;
    /** 来源说明 */
    private String remark;
}

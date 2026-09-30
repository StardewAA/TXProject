package cn.iocoder.txgy.module.bidding.dal.dataobject.processtrack;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 投标流程事件记录（审计留痕，只追加）。表：bidding_process_event。
 */
@TableName("bidding_process_event")
@Data
@EqualsAndHashCode(callSuper = true)
public class BiddingProcessEventDO extends BaseDO {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 流程跟踪ID */
    private Long trackId;
    /** 用户ID */
    private Long userId;
    /** 事件类型（BiddingProcessEventEnum code） */
    private String eventType;
    /** 源状态 */
    private String fromState;
    /** 目标状态 */
    private String toState;
    /** 事件发生时间 */
    private LocalDateTime eventTime;
    /** 操作人ID（平台方审核员等） */
    private Long operatorId;
    /** 操作人名称 */
    private String operatorName;
    /** 操作描述 */
    private String operationDesc;
    /** 附加数据（JSON，投标文件信息/报价等） */
    private String extraData;
}

package cn.iocoder.txgy.module.bidding.dal.dataobject.noticefollow;
// 规范依据：设计文档 v0.6 §12.2/§15.1（T 轨；提醒发送留痕，真实渠道 API 占位）

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 提醒发送留痕（T 轨）。表：bidding_notice_subscription_event。
 * channel=IN_APP(必发)/EMAIL(按用户设置)；channelStatus=PENDING/SENT/FAILED；
 * 真实通知渠道为跨模块能力（设计文档 §14.3 API 占位），本表先落站内事件并留重试计数。
 */
@TableName("bidding_notice_subscription_event")
@Data
@EqualsAndHashCode(callSuper = true)
public class SubscriptionEventDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    private Long id;
    /** 收件用户 ID */
    private Long userId;
    /** 对应关注动态（bidding_follow_item_event.id，可空） */
    private Long followEventId;
    /** 关联公告 ID（可空） */
    private Long noticeId;
    /** 渠道：IN_APP/EMAIL */
    private String channel;
    /** 渠道状态：PENDING/SENT/FAILED */
    private String channelStatus;
    /** 发送时间 */
    private LocalDateTime sentTime;
    /** 发送重试次数（≤3） */
    private Integer retryCount;
    /** 结果/错误说明 */
    private String message;

}

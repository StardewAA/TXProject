package cn.iocoder.txgy.module.bidding.dal.dataobject.noticefollow;
// 规范依据：设计文档 v0.6 §13.2/§15.1（T 轨；动态事件=关注流数据源+未读依据，同事件幂等）

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 关注项动态/事件（T 轨）。表：bidding_follow_item_event。
 *
 * <p>“有动态才提醒”的数据源：新公告/公告更新/外部 ACTIVE/阶段变化写一条；
 * event_key 唯一（user+item+source+notice_id/版本）保证同一用户同一事件只提醒一次；
 * readFlag=未读 → 触发通知（跨模块通知 API 占位，见 §14.3）。
 */
@TableName("bidding_follow_item_event")
@Data
@EqualsAndHashCode(callSuper = true)
public class FollowItemEventDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    private Long id;
    /** 用户 ID */
    private Long userId;
    /** 所属关注行（bidding_follow_item.id） */
    private Long itemId;
    /** 来源：INTERNAL/EXTERNAL */
    private String source;
    /** 事件类型：NOTICE_PUBLISHED/NOTICE_UPDATED/EXTERNAL_ACTIVE/STAGE_CHANGE */
    private String eventType;
    /** 关联公告 ID（内部或外部表主档 id，可空） */
    private Long noticeId;
    /** 唯一键：user+item+source+notice_id/版本（防重复提醒） */
    private String eventKey;
    /** 标题快照 */
    private String titleSnapshot;
    /** 原网址（外部） */
    private String origUrl;
    /** 动态发生时间 */
    private LocalDateTime occurredAt;
    /** 是否已读（未读=提醒，读后置 1） */
    private Boolean readFlag;

}

package cn.iocoder.txgy.module.bidding.dal.dataobject.noticefollow;
// 规范依据：设计文档 v0.6 §13/§15.1（T 轨；关注项目列表行：阶段/基础信息/未读）

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 关注项目列表主档（T 轨，“关注的项目列表”界面行）。表：bidding_follow_item。
 *
 * <p>每行=一个被关注维度（PROJECT/TENDER_PROJECT/SECTION）的当前进展（stage 快照）、
 * 基础信息（base_json 快照）、最近动态时间与未读数；公告归属键命中时刷新行并写事件。
 */
@TableName("bidding_follow_item")
@Data
@EqualsAndHashCode(callSuper = true)
public class FollowItemDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    private Long id;
    /** 用户 ID（投标人） */
    private Long userId;
    /** 维度类型：PROJECT/TENDER_PROJECT/SECTION */
    private String followType;
    /** 维度代码（project_code/tender_project_code/bid_section_code） */
    private String followKey;
    /** 项目/标段名快照 */
    private String displayName;
    /** 发布单位快照 */
    private String issuerName;
    /** 进展阶段：PENDING/TENDER/OPEN_BID/EVAL/RESULT/CONTRACT/DONE/UNKNOWN */
    private String stageCode;
    /** 阶段名称快照 */
    private String stageName;
    /** 基础信息快照（JSON：建设单位/监管/招标方式/地点等关键项） */
    private String baseJson;
    /** 最近动态时间 */
    private LocalDateTime latestDynamicTime;
    /** 未读动态数（读后清零） */
    private Integer unreadCount;
    /** 关注状态：FOLLOWING/UNFOLLOWED */
    private String followStatus;
    /** 关注时间 */
    private LocalDateTime followTime;
    /** 取消关注时间 */
    private LocalDateTime unfollowTime;

}

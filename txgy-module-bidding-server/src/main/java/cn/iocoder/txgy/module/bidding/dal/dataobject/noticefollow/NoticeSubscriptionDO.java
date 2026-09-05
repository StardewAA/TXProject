package cn.iocoder.txgy.module.bidding.dal.dataobject.noticefollow;
// 规范依据：设计文档 v0.6 §12/§15.1（T 轨：用户隔离数据，租户过滤交拦截器，业务不手写）；
// 《招投标模块开发规范》五章（审计列）

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 关注订阅（T 轨）。表：bidding_notice_subscription。
 *
 * <p>投标人（=system AdminUser 登录用户，设计文档 §15.5）关注 公告/项目/招标项目/标段；
 * 唯一键 (tenant_id, user_id, target_type, target_key, deleted)，重复关注幂等。
 */
@TableName("bidding_notice_subscription")
@Data
@EqualsAndHashCode(callSuper = true)
public class NoticeSubscriptionDO extends TenantBaseDO {

    /** 主键（雪花 ID） */
    private Long id;
    /** 用户 ID（投标人，system AdminUser） */
    private Long userId;
    /** 关注目标类型：ANNOUNCEMENT/PROJECT/TENDER_PROJECT/SECTION */
    private String targetType;
    /** 目标键：公告 id 或 维度代码（project_code/tender_project_code/bid_section_code） */
    private String targetKey;
    /** 关注状态：FOLLOWING/UNFOLLOWED */
    private String followStatus;
    /** 关注时间 */
    private LocalDateTime followTime;
    /** 取消关注时间 */
    private LocalDateTime unfollowTime;

}

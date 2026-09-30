package cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 智慧标书审查任务分页入参（管理端可按归属人查，个人端由后端强制本人）。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BidReviewTaskPageReqVO extends PageParam {

    /** 任务名称（模糊） */
    private String taskName;
    /** 任务编号（精确） */
    private String taskNo;
    /** 归属人（管理端筛选用；个人端忽略此参数） */
    private Long ownerUserId;
    /** 归属人名称（模糊，管理端按提交人筛选；owner_user_id 是精确匹配，二者互补） */
    private String ownerName;
    /** 来源场景：ADMIN_TASK/PORTAL_SELF */
    private String sourceScene;
    /** 状态 */
    private String status;
    /** 结论 */
    private String verdict;
    /** 创建时间起 */
    private LocalDateTime beginTime;
    /** 创建时间止 */
    private LocalDateTime endTime;
}

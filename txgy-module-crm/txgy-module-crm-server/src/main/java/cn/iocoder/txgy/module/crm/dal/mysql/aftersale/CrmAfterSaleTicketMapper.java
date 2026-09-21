package cn.iocoder.txgy.module.crm.dal.mysql.aftersale;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.ticket.CrmAfterSaleTicketPageReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleTicketDO;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleTicketStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 售后问题工单 Mapper
 *
 * @author txgycloud
 */
@Mapper
public interface CrmAfterSaleTicketMapper extends BaseMapperX<CrmAfterSaleTicketDO> {

    default CrmAfterSaleTicketDO selectByNo(String no) {
        return selectOne(CrmAfterSaleTicketDO::getNo, no);
    }

    default PageResult<CrmAfterSaleTicketDO> selectPage(CrmAfterSaleTicketPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CrmAfterSaleTicketDO>()
                .likeIfPresent(CrmAfterSaleTicketDO::getNo, reqVO.getNo())
                .likeIfPresent(CrmAfterSaleTicketDO::getTitle, reqVO.getTitle())
                .eqIfPresent(CrmAfterSaleTicketDO::getCustomerId, reqVO.getCustomerId())
                .eqIfPresent(CrmAfterSaleTicketDO::getWarrantyId, reqVO.getWarrantyId())
                .eqIfPresent(CrmAfterSaleTicketDO::getSource, reqVO.getSource())
                .eqIfPresent(CrmAfterSaleTicketDO::getPriority, reqVO.getPriority())
                .eqIfPresent(CrmAfterSaleTicketDO::getStatus, reqVO.getStatus())
                .eqIfPresent(CrmAfterSaleTicketDO::getAssigneeUserId, reqVO.getAssigneeUserId())
                .betweenIfPresent(CrmAfterSaleTicketDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(CrmAfterSaleTicketDO::getId));
    }

    // ========== 待办：工单超时预警（实时查询，不落库） ==========

    /**
     * 超时判定的起算时间（SQL 表达式）：开始处理时间，尚未开始处理时以创建时间兜底
     *
     * <p>语义：「处理时长」从工单实际开始处理那一刻起算；
     * 待处理（尚未开始）的工单若长期无人认领，同样需要预警，因此回退到创建时间，
     * 避免「一直没人开始处理」的工单永远不进待办。
     */
    String OVERDUE_START_TIME_SQL = "COALESCE(start_time, create_time)";

    /**
     * 扫描「未关闭且已超时」的工单（超时检查任务的标记来源）
     *
     * <p>判定条件：状态 ∈ {待处理, 处理中, 已完成}（已关闭除外），且起算时间
     * （COALESCE(start_time, create_time)，见 {@link #OVERDUE_START_TIME_SQL}）
     * 早于等于超时截止时间。
     *
     * <p>已被标记为「已超时(5)」的工单不在候选状态内，天然不会重复扫描。
     *
     * @param deadline 超时截止时间（含）
     * @return 已超时但尚未标记的工单列表
     */
    default List<CrmAfterSaleTicketDO> selectListByUnclosedOverdue(LocalDateTime deadline) {
        return selectList(new QueryWrapper<CrmAfterSaleTicketDO>()
                .in("status", unclosedStatuses())
                .apply(OVERDUE_START_TIME_SQL + " <= {0}", deadline)
                .orderByAsc(OVERDUE_START_TIME_SQL));
    }

    /**
     * 待办「工单超时预警」分页查询
     *
     * <p>实时判定条件：
     * <ul>
     *   <li>工单状态 = 已超时（由超时检查任务/懒同步自动标记，标记后即出现在待办中，
     *       恢复处理中或完成后自动消失）；</li>
     *   <li>处理人 ∈ 归属范围（当前登录人本人 + 其下属）。</li>
     * </ul>
     * 按起算时间升序，超时最久的排在最前。
     *
     * <p>注意：排序依赖 COALESCE 表达式，Lambda 写法不支持表达式列，
     * 因此这里使用字符串列名的 QueryWrapper（列名均为常量，无注入风险）。
     *
     * @param reqVO           分页查询
     * @param assigneeUserIds 归属范围（本人 + 下属负责的工单处理人）
     * @param deadline        未使用（保留参数以兼容既有调用；超时判定已前置到状态标记环节）
     */
    default PageResult<CrmAfterSaleTicketDO> selectPageByOverdue(CrmAfterSaleTicketPageReqVO reqVO,
                                                                Collection<Long> assigneeUserIds, LocalDateTime deadline) {
        if (CollUtil.isEmpty(assigneeUserIds)) {
            return PageResult.empty();
        }
        return selectPage(reqVO, new QueryWrapper<CrmAfterSaleTicketDO>()
                .in("status", List.of(CrmAfterSaleTicketStatusEnum.OVERDUE.getStatus()))
                .in("assignee_user_id", assigneeUserIds)
                .orderByAsc(OVERDUE_START_TIME_SQL));
    }

    /**
     * 待办「工单超时预警」数量（与 {@link #selectPageByOverdue} 同一口径）
     */
    default Long selectCountByOverdue(Collection<Long> assigneeUserIds, LocalDateTime deadline) {
        if (CollUtil.isEmpty(assigneeUserIds)) {
            return 0L;
        }
        return selectCount(new QueryWrapper<CrmAfterSaleTicketDO>()
                .in("status", List.of(CrmAfterSaleTicketStatusEnum.OVERDUE.getStatus()))
                .in("assignee_user_id", assigneeUserIds));
    }

    /**
     * 超时标记的候选状态：未闭环的工单状态（待处理 / 处理中）
     *
     * <p>「已关闭(4)」与「已完成(3)」均视为闭环、不再标记 —— 已完成的工单
     * 处理已结束，超时预警的目的（提醒跟进）已经达成；
     * 从「已超时」完成的工单也因此自然退出预警，不会出现反复标记的循环。
     * 「已超时(5)」本身不在候选内，标记后天然不会被重复扫描。
     */
    private List<Integer> unclosedStatuses() {
        return List.of(CrmAfterSaleTicketStatusEnum.PENDING.getStatus(),
                CrmAfterSaleTicketStatusEnum.PROCESSING.getStatus());
    }

}

package cn.iocoder.txgy.module.crm.dal.mysql.aftersale;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.ticket.CrmAfterSaleTicketPageReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleTicketDO;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleTicketStatusEnum;
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
     * 待办「工单超时预警」分页查询
     *
     * <p>实时判定条件：
     * <ul>
     *   <li>工单未闭环（待处理 / 处理中；完成后状态变为已完成/已关闭，条件不再命中，刷新即消失）；</li>
     *   <li>处理人 ∈ 归属范围（当前登录人本人 + 其下属）；</li>
     *   <li>创建时间 ≤ 截止时间（今天 - 售后设置「工单超时预警天数」的日终），
     *       即「当前日期 - 工单创建日期 ≥ 超时预警天数」。</li>
     * </ul>
     * 按创建时间升序，积压最久的排在最前。
     *
     * @param reqVO           分页查询
     * @param assigneeUserIds 归属范围（本人 + 下属负责的工单处理人）
     * @param deadline        超时截止时间（含）
     */
    default PageResult<CrmAfterSaleTicketDO> selectPageByOverdue(CrmAfterSaleTicketPageReqVO reqVO,
                                                                Collection<Long> assigneeUserIds, LocalDateTime deadline) {
        if (CollUtil.isEmpty(assigneeUserIds)) {
            return PageResult.empty();
        }
        return selectPage(reqVO, new LambdaQueryWrapperX<CrmAfterSaleTicketDO>()
                .in(CrmAfterSaleTicketDO::getStatus, unclosedStatuses())
                .in(CrmAfterSaleTicketDO::getAssigneeUserId, assigneeUserIds)
                .le(CrmAfterSaleTicketDO::getCreateTime, deadline)
                .orderByAsc(CrmAfterSaleTicketDO::getCreateTime));
    }

    /**
     * 待办「工单超时预警」数量（与 {@link #selectPageByOverdue} 同一口径）
     */
    default Long selectCountByOverdue(Collection<Long> assigneeUserIds, LocalDateTime deadline) {
        if (CollUtil.isEmpty(assigneeUserIds)) {
            return 0L;
        }
        return selectCount(new LambdaQueryWrapperX<CrmAfterSaleTicketDO>()
                .in(CrmAfterSaleTicketDO::getStatus, unclosedStatuses())
                .in(CrmAfterSaleTicketDO::getAssigneeUserId, assigneeUserIds)
                .le(CrmAfterSaleTicketDO::getCreateTime, deadline));
    }

    /**
     * 未闭环的工单状态：待处理 + 处理中（已完成 / 已关闭视为闭环，不再进入待办）
     */
    private List<Integer> unclosedStatuses() {
        return List.of(CrmAfterSaleTicketStatusEnum.PENDING.getStatus(),
                CrmAfterSaleTicketStatusEnum.PROCESSING.getStatus());
    }

}

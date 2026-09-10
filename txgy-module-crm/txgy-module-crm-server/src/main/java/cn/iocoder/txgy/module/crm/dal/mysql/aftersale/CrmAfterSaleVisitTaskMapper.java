package cn.iocoder.txgy.module.crm.dal.mysql.aftersale;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visittask.CrmAfterSaleVisitTaskPageReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleVisitTaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 售后回访任务 Mapper
 *
 * @author txgycloud
 */
@Mapper
public interface CrmAfterSaleVisitTaskMapper extends BaseMapperX<CrmAfterSaleVisitTaskDO> {

    default PageResult<CrmAfterSaleVisitTaskDO> selectPage(CrmAfterSaleVisitTaskPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CrmAfterSaleVisitTaskDO>()
                .eqIfPresent(CrmAfterSaleVisitTaskDO::getPlanId, reqVO.getPlanId())
                .eqIfPresent(CrmAfterSaleVisitTaskDO::getWarrantyId, reqVO.getWarrantyId())
                .eqIfPresent(CrmAfterSaleVisitTaskDO::getCustomerId, reqVO.getCustomerId())
                .eqIfPresent(CrmAfterSaleVisitTaskDO::getAssigneeUserId, reqVO.getAssigneeUserId())
                .eqIfPresent(CrmAfterSaleVisitTaskDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(CrmAfterSaleVisitTaskDO::getTaskDate, reqVO.getTaskDate())
                .orderByAsc(CrmAfterSaleVisitTaskDO::getTaskDate));
    }

    default List<CrmAfterSaleVisitTaskDO> selectListByStatusAndDateLe(Integer status, LocalDate date) {
        return selectList(new LambdaQueryWrapperX<CrmAfterSaleVisitTaskDO>()
                .eq(CrmAfterSaleVisitTaskDO::getStatus, status)
                .le(CrmAfterSaleVisitTaskDO::getTaskDate, date));
    }

    /**
     * 查询指定状态、未发送过临期提醒、且任务日期不晚于指定日期的任务
     */
    default List<CrmAfterSaleVisitTaskDO> selectListByStatusAndRemindNotSent(Integer status, LocalDate date) {
        return selectList(new LambdaQueryWrapperX<CrmAfterSaleVisitTaskDO>()
                .eq(CrmAfterSaleVisitTaskDO::getStatus, status)
                .isNull(CrmAfterSaleVisitTaskDO::getOverdueSentDate)
                .le(CrmAfterSaleVisitTaskDO::getTaskDate, date));
    }

    default List<CrmAfterSaleVisitTaskDO> selectListByPlanId(Long planId) {
        return selectList(new LambdaQueryWrapperX<CrmAfterSaleVisitTaskDO>()
                .eq(CrmAfterSaleVisitTaskDO::getPlanId, planId));
    }

    default long selectCountByPlanIdAndStatus(Long planId, Integer status) {
        return selectCount(new LambdaQueryWrapperX<CrmAfterSaleVisitTaskDO>()
                .eq(CrmAfterSaleVisitTaskDO::getPlanId, planId)
                .eq(CrmAfterSaleVisitTaskDO::getStatus, status));
    }

    default List<CrmAfterSaleVisitTaskDO> selectListByWarrantyIds(Collection<Long> warrantyIds) {
        return selectList(new LambdaQueryWrapperX<CrmAfterSaleVisitTaskDO>()
                .in(CrmAfterSaleVisitTaskDO::getWarrantyId, warrantyIds));
    }

}

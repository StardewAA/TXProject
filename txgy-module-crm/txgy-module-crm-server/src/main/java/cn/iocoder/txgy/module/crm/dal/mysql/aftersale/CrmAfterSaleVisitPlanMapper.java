package cn.iocoder.txgy.module.crm.dal.mysql.aftersale;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visitplan.CrmAfterSaleVisitPlanPageReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleVisitPlanDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 售后回访计划 Mapper
 *
 * @author txgycloud
 */
@Mapper
public interface CrmAfterSaleVisitPlanMapper extends BaseMapperX<CrmAfterSaleVisitPlanDO> {

    default PageResult<CrmAfterSaleVisitPlanDO> selectPage(CrmAfterSaleVisitPlanPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CrmAfterSaleVisitPlanDO>()
                .likeIfPresent(CrmAfterSaleVisitPlanDO::getName, reqVO.getName())
                .eqIfPresent(CrmAfterSaleVisitPlanDO::getWarrantyId, reqVO.getWarrantyId())
                .eqIfPresent(CrmAfterSaleVisitPlanDO::getPlanType, reqVO.getPlanType())
                .eqIfPresent(CrmAfterSaleVisitPlanDO::getStatus, reqVO.getStatus())
                .eqIfPresent(CrmAfterSaleVisitPlanDO::getAssigneeType, reqVO.getAssigneeType())
                .orderByDesc(CrmAfterSaleVisitPlanDO::getId));
    }

    default List<CrmAfterSaleVisitPlanDO> selectListByWarrantyIds(Collection<Long> warrantyIds) {
        return selectList(new LambdaQueryWrapperX<CrmAfterSaleVisitPlanDO>()
                .in(CrmAfterSaleVisitPlanDO::getWarrantyId, warrantyIds));
    }

    /**
     * 查询启用中、且仍处于有效期内的回访计划（end_date 为空或 >= 今天）
     */
    default List<CrmAfterSaleVisitPlanDO> selectListByStatusEnabled(Integer status, LocalDate today) {
        return selectList(new LambdaQueryWrapperX<CrmAfterSaleVisitPlanDO>()
                .eq(CrmAfterSaleVisitPlanDO::getStatus, status)
                .and(w -> w.isNull(CrmAfterSaleVisitPlanDO::getEndDate).or()
                        .ge(CrmAfterSaleVisitPlanDO::getEndDate, today)));
    }

}

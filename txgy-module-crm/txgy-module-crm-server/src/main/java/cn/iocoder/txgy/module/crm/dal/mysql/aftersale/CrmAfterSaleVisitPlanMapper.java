package cn.iocoder.txgy.module.crm.dal.mysql.aftersale;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visitplan.CrmAfterSaleVisitPlanPageReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleVisitPlanDO;
import cn.iocoder.txgy.module.crm.enums.aftersale.CrmAfterSaleVisitPlanStatusEnum;
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
     * 查询质保档案对应的回访计划（一份质保档案至多绑定一条有效计划）
     *
     * @param warrantyId 质保档案编号
     * @return 回访计划（不存在时返回 null）
     */
    default CrmAfterSaleVisitPlanDO selectByWarrantyId(Long warrantyId) {
        return selectOne(new LambdaQueryWrapperX<CrmAfterSaleVisitPlanDO>()
                .eq(CrmAfterSaleVisitPlanDO::getWarrantyId, warrantyId)
                .last("LIMIT 1"));
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

    /**
     * 停用指定质保档案关联的回访计划（质保自动流转为「已到期终止」时调用）
     *
     * <p>质保到期后不再需要回访，停用计划可避免继续生成新的回访任务；
     * 「已完成」的计划保持原状（历史事实不回溯）。
     *
     * @param warrantyId 质保档案编号
     * @return 更新的条数
     */
    default int disableByWarrantyId(Long warrantyId) {
        CrmAfterSaleVisitPlanDO updateObj = new CrmAfterSaleVisitPlanDO();
        updateObj.setStatus(CrmAfterSaleVisitPlanStatusEnum.DISABLE.getStatus());
        return update(updateObj, new LambdaQueryWrapperX<CrmAfterSaleVisitPlanDO>()
                .eq(CrmAfterSaleVisitPlanDO::getWarrantyId, warrantyId)
                .eq(CrmAfterSaleVisitPlanDO::getStatus, CrmAfterSaleVisitPlanStatusEnum.ENABLE.getStatus()));
    }

}

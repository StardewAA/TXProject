package cn.iocoder.txgy.module.crm.dal.mysql.aftersale;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visitrecord.CrmAfterSaleVisitRecordPageReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleVisitRecordDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 售后回访记录 Mapper
 *
 * @author txgycloud
 */
@Mapper
public interface CrmAfterSaleVisitRecordMapper extends BaseMapperX<CrmAfterSaleVisitRecordDO> {

    default PageResult<CrmAfterSaleVisitRecordDO> selectPage(CrmAfterSaleVisitRecordPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CrmAfterSaleVisitRecordDO>()
                .eqIfPresent(CrmAfterSaleVisitRecordDO::getTaskId, reqVO.getTaskId())
                .eqIfPresent(CrmAfterSaleVisitRecordDO::getStatus, reqVO.getStatus())
                .eqIfPresent(CrmAfterSaleVisitRecordDO::getWarrantyId, reqVO.getWarrantyId())
                .eqIfPresent(CrmAfterSaleVisitRecordDO::getCustomerId, reqVO.getCustomerId())
                .eqIfPresent(CrmAfterSaleVisitRecordDO::getSatisfactionLevel, reqVO.getSatisfactionLevel())
                .betweenIfPresent(CrmAfterSaleVisitRecordDO::getVisitTime, reqVO.getVisitTime())
                .orderByDesc(CrmAfterSaleVisitRecordDO::getId));
    }

    default long selectCountByTaskId(Long taskId) {
        return selectCount(new LambdaQueryWrapperX<CrmAfterSaleVisitRecordDO>()
                .eq(CrmAfterSaleVisitRecordDO::getTaskId, taskId));
    }

    /**
     * 查询任务关联的回访记录（一个任务最多产生一条自动记录）
     */
    default CrmAfterSaleVisitRecordDO selectOneByTaskId(Long taskId) {
        List<CrmAfterSaleVisitRecordDO> list = selectListByTaskId(taskId);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 删除任务关联的回访记录（随任务删除，保持任务与记录联动一致）
     */
    default int deleteByTaskId(Long taskId) {
        return delete(new LambdaQueryWrapperX<CrmAfterSaleVisitRecordDO>()
                .eq(CrmAfterSaleVisitRecordDO::getTaskId, taskId));
    }

    default List<CrmAfterSaleVisitRecordDO> selectListByTaskId(Long taskId) {
        return selectList(new LambdaQueryWrapperX<CrmAfterSaleVisitRecordDO>()
                .eq(CrmAfterSaleVisitRecordDO::getTaskId, taskId));
    }

    default List<CrmAfterSaleVisitRecordDO> selectListByWarrantyIdOrderByVisitTimeDesc(Long warrantyId) {
        return selectList(new LambdaQueryWrapperX<CrmAfterSaleVisitRecordDO>()
                .eq(CrmAfterSaleVisitRecordDO::getWarrantyId, warrantyId)
                .orderByDesc(CrmAfterSaleVisitRecordDO::getVisitTime));
    }

}

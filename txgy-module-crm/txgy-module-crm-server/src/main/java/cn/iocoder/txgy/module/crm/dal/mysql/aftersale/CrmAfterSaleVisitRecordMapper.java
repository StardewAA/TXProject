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

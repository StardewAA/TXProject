package cn.iocoder.txgy.module.erp.dal.mysql.logistics;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.erp.controller.admin.logistics.vo.ErpLogisticsPageReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.logistics.ErpLogisticsRelationDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ErpLogisticsRelationMapper extends BaseMapperX<ErpLogisticsRelationDO> {

    default ErpLogisticsRelationDO selectByIdForUpdate(Long id) {
        return selectOneForUpdate(ErpLogisticsRelationDO::getId, id);
    }

    default ErpLogisticsRelationDO selectByTaskNo(String logisticsTaskNo) {
        return selectOne(ErpLogisticsRelationDO::getLogisticsTaskNo, logisticsTaskNo);
    }

    default ErpLogisticsRelationDO selectByTaskNoForUpdate(String logisticsTaskNo) {
        return selectOneForUpdate(ErpLogisticsRelationDO::getLogisticsTaskNo, logisticsTaskNo);
    }

    default ErpLogisticsRelationDO selectBySourceOrder(String sourceOrderType, String sourceOrderNo) {
        return selectOne(new LambdaQueryWrapperX<ErpLogisticsRelationDO>()
                .eq(ErpLogisticsRelationDO::getSourceOrderType, sourceOrderType)
                .eq(ErpLogisticsRelationDO::getSourceOrderNo, sourceOrderNo));
    }

    default ErpLogisticsRelationDO selectBySourceOrderForUpdate(String sourceOrderType, String sourceOrderNo) {
        return selectOneForUpdate(new LambdaQueryWrapperX<ErpLogisticsRelationDO>()
                .eq(ErpLogisticsRelationDO::getSourceOrderType, sourceOrderType)
                .eq(ErpLogisticsRelationDO::getSourceOrderNo, sourceOrderNo));
    }

    default PageResult<ErpLogisticsRelationDO> selectPage(ErpLogisticsPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ErpLogisticsRelationDO>()
                .likeIfPresent(ErpLogisticsRelationDO::getLogisticsTaskNo, reqVO.getLogisticsTaskNo())
                .likeIfPresent(ErpLogisticsRelationDO::getWaybillNo, reqVO.getWaybillNo())
                .eqIfPresent(ErpLogisticsRelationDO::getSourceOrderType, reqVO.getSourceOrderType())
                .likeIfPresent(ErpLogisticsRelationDO::getSourceOrderNo, reqVO.getSourceOrderNo())
                .eqIfPresent(ErpLogisticsRelationDO::getLogisticsStatus, reqVO.getLogisticsStatus())
                .eqIfPresent(ErpLogisticsRelationDO::getHasException, reqVO.getHasException())
                .orderByDesc(ErpLogisticsRelationDO::getId));
    }

}

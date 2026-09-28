package cn.iocoder.txgy.module.erp.dal.mysql.purchase;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.inspection.ErpArrivalInspectionPageReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpArrivalInspectionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ErpArrivalInspectionMapper extends BaseMapperX<ErpArrivalInspectionDO> {

    default PageResult<ErpArrivalInspectionDO> selectPage(ErpArrivalInspectionPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ErpArrivalInspectionDO>()
                .likeIfPresent(ErpArrivalInspectionDO::getNo, reqVO.getNo())
                .likeIfPresent(ErpArrivalInspectionDO::getPurchaseOrderNo, reqVO.getPurchaseOrderNo())
                .eqIfPresent(ErpArrivalInspectionDO::getSupplierId, reqVO.getSupplierId())
                .eqIfPresent(ErpArrivalInspectionDO::getResult, reqVO.getResult())
                .eqIfPresent(ErpArrivalInspectionDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(ErpArrivalInspectionDO::getInspectTime, reqVO.getInspectTime())
                .orderByDesc(ErpArrivalInspectionDO::getId));
    }

    default ErpArrivalInspectionDO selectByNo(String no) { return selectOne(ErpArrivalInspectionDO::getNo, no); }
}

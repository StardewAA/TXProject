package cn.iocoder.txgy.module.erp.dal.mysql.purchase;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.request.ErpPurchaseRequestPageReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseRequestDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ErpPurchaseRequestMapper extends BaseMapperX<ErpPurchaseRequestDO> {
    default PageResult<ErpPurchaseRequestDO> selectPage(ErpPurchaseRequestPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ErpPurchaseRequestDO>()
                .likeIfPresent(ErpPurchaseRequestDO::getNo, reqVO.getNo())
                .eqIfPresent(ErpPurchaseRequestDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ErpPurchaseRequestDO::getDepartmentId, reqVO.getDepartmentId())
                .betweenIfPresent(ErpPurchaseRequestDO::getRequestTime, reqVO.getRequestTime())
                .likeIfPresent(ErpPurchaseRequestDO::getPurpose, reqVO.getPurpose())
                .orderByDesc(ErpPurchaseRequestDO::getId));
    }
    default ErpPurchaseRequestDO selectByNo(String no) { return selectOne(ErpPurchaseRequestDO::getNo, no); }
}

package cn.iocoder.txgy.module.erp.dal.mysql.purchase;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.plan.ErpPurchasePlanPageReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchasePlanDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ErpPurchasePlanMapper extends BaseMapperX<ErpPurchasePlanDO> {

    default PageResult<ErpPurchasePlanDO> selectPage(ErpPurchasePlanPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ErpPurchasePlanDO>()
                .likeIfPresent(ErpPurchasePlanDO::getNo, reqVO.getNo())
                .likeIfPresent(ErpPurchasePlanDO::getPlanName, reqVO.getPlanName())
                .eqIfPresent(ErpPurchasePlanDO::getPlanPeriod, reqVO.getPlanPeriod())
                .eqIfPresent(ErpPurchasePlanDO::getDepartmentId, reqVO.getDepartmentId())
                .eqIfPresent(ErpPurchasePlanDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(ErpPurchasePlanDO::getPlanTime, reqVO.getPlanTime())
                .orderByDesc(ErpPurchasePlanDO::getId));
    }

    default ErpPurchasePlanDO selectByNo(String no) { return selectOne(ErpPurchasePlanDO::getNo, no); }
}

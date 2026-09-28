package cn.iocoder.txgy.module.erp.dal.mysql.purchase;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.contract.ErpPurchaseContractPageReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseContractDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ErpPurchaseContractMapper extends BaseMapperX<ErpPurchaseContractDO> {

    default PageResult<ErpPurchaseContractDO> selectPage(ErpPurchaseContractPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ErpPurchaseContractDO>()
                .likeIfPresent(ErpPurchaseContractDO::getNo, reqVO.getNo())
                .likeIfPresent(ErpPurchaseContractDO::getContractName, reqVO.getContractName())
                .eqIfPresent(ErpPurchaseContractDO::getSupplierId, reqVO.getSupplierId())
                .eqIfPresent(ErpPurchaseContractDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(ErpPurchaseContractDO::getSignDate, reqVO.getSignDate())
                .orderByDesc(ErpPurchaseContractDO::getId));
    }

    default ErpPurchaseContractDO selectByNo(String no) { return selectOne(ErpPurchaseContractDO::getNo, no); }
}

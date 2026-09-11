package cn.iocoder.txgy.module.crm.dal.mysql.aftersale;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.businessrel.CrmAfterSaleBusinessRelPageReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleBusinessRelDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 售后转商机关联 Mapper
 *
 * @author txgycloud
 */
@Mapper
public interface CrmAfterSaleBusinessRelMapper extends BaseMapperX<CrmAfterSaleBusinessRelDO> {

    default PageResult<CrmAfterSaleBusinessRelDO> selectPage(CrmAfterSaleBusinessRelPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CrmAfterSaleBusinessRelDO>()
                .eqIfPresent(CrmAfterSaleBusinessRelDO::getBusinessId, reqVO.getBusinessId())
                .eqIfPresent(CrmAfterSaleBusinessRelDO::getWarrantyId, reqVO.getWarrantyId())
                .eqIfPresent(CrmAfterSaleBusinessRelDO::getCustomerId, reqVO.getCustomerId())
                .eqIfPresent(CrmAfterSaleBusinessRelDO::getSourceType, reqVO.getSourceType())
                .orderByDesc(CrmAfterSaleBusinessRelDO::getId));
    }

    default long selectCountByWarrantyId(Long warrantyId) {
        return selectCount(new LambdaQueryWrapperX<CrmAfterSaleBusinessRelDO>()
                .eq(CrmAfterSaleBusinessRelDO::getWarrantyId, warrantyId));
    }

}

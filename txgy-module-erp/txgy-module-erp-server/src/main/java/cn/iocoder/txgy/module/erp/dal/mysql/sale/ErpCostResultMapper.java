package cn.iocoder.txgy.module.erp.dal.mysql.sale;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.erp.controller.admin.sale.vo.cost.ErpCostResultPageReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpCostResultDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ErpCostResultMapper extends BaseMapperX<ErpCostResultDO> {
    default PageResult<ErpCostResultDO> selectPage(ErpCostResultPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ErpCostResultDO>()
                .likeIfPresent(ErpCostResultDO::getNo, reqVO.getNo())
                .likeIfPresent(ErpCostResultDO::getName, reqVO.getName())
                .likeIfPresent(ErpCostResultDO::getProjectCode, reqVO.getProjectCode())
                .eqIfPresent(ErpCostResultDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ErpCostResultDO::getBusinessStatus, reqVO.getBusinessStatus())
                .orderByDesc(ErpCostResultDO::getId));
    }
    default ErpCostResultDO selectByNo(String no) { return selectOne(ErpCostResultDO::getNo, no); }
    default ErpCostResultDO selectByProjectAndVersion(String projectCode, Integer version) {
        return selectOne(ErpCostResultDO::getProjectCode, projectCode, ErpCostResultDO::getCostVersion, version);
    }
}

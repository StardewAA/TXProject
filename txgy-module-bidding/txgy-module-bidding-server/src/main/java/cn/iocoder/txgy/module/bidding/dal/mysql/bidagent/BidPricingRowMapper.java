package cn.iocoder.txgy.module.bidding.dal.mysql.bidagent;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidPricingRowDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 报价数据行 Mapper（COPY_PRICING 通道数据源）
 */
@Mapper
public interface BidPricingRowMapper extends BaseMapperX<BidPricingRowDO> {

    default List<BidPricingRowDO> selectListByProject(Long projectId) {
        return selectList(new LambdaQueryWrapperX<BidPricingRowDO>()
                .eqIfPresent(BidPricingRowDO::getProjectId, projectId)
                .orderByAsc(BidPricingRowDO::getTableName)
                .orderByAsc(BidPricingRowDO::getRowNo));
    }

    default List<BidPricingRowDO> selectListByMaterial(Long materialId) {
        return selectList(new LambdaQueryWrapperX<BidPricingRowDO>()
                .eqIfPresent(BidPricingRowDO::getMaterialId, materialId));
    }
}

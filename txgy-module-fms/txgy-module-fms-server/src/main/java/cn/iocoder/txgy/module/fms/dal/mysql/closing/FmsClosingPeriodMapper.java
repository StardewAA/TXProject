package cn.iocoder.txgy.module.fms.dal.mysql.closing;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.fms.dal.dataobject.closing.FmsClosingPeriodDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * FMS 结账期间 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface FmsClosingPeriodMapper extends BaseMapperX<FmsClosingPeriodDO> {

    default FmsClosingPeriodDO selectByPeriod(Long accountSetId,
            LocalDateTime beginTime, LocalDateTime endTime) {
        return selectOne(new LambdaQueryWrapperX<FmsClosingPeriodDO>()
                .eq(FmsClosingPeriodDO::getAccountSetId, accountSetId)
                .between(FmsClosingPeriodDO::getClosingTime, beginTime, endTime));
    }

    default FmsClosingPeriodDO selectLatestByAccountSetId(Long accountSetId) {
        return selectLastOne(new LambdaQueryWrapperX<FmsClosingPeriodDO>()
                .eq(FmsClosingPeriodDO::getAccountSetId, accountSetId)
                .orderByAsc(FmsClosingPeriodDO::getClosingTime)
                .orderByAsc(FmsClosingPeriodDO::getId));
    }

}

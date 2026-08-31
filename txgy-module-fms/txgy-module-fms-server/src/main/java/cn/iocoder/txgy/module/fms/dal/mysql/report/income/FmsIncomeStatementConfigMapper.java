package cn.iocoder.txgy.module.fms.dal.mysql.report.income;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.fms.dal.dataobject.report.income.FmsIncomeStatementConfigDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * FMS 利润表配置 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface FmsIncomeStatementConfigMapper extends BaseMapperX<FmsIncomeStatementConfigDO> {

    default List<FmsIncomeStatementConfigDO> selectListByAccountSetId(Long accountSetId) {
        return selectList(new LambdaQueryWrapperX<FmsIncomeStatementConfigDO>()
                .eq(FmsIncomeStatementConfigDO::getAccountSetId, accountSetId)
                .orderByAsc(FmsIncomeStatementConfigDO::getSort)
                .orderByAsc(FmsIncomeStatementConfigDO::getId));
    }

}

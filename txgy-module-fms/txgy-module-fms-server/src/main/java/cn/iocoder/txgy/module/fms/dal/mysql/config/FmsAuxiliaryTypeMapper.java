package cn.iocoder.txgy.module.fms.dal.mysql.config;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.fms.dal.dataobject.config.FmsAuxiliaryTypeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * FMS 辅助核算类别 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface FmsAuxiliaryTypeMapper extends BaseMapperX<FmsAuxiliaryTypeDO> {

    default List<FmsAuxiliaryTypeDO> selectListByAccountSetId(Long accountSetId) {
        return selectList(new LambdaQueryWrapperX<FmsAuxiliaryTypeDO>()
                .eq(FmsAuxiliaryTypeDO::getAccountSetId, accountSetId)
                .orderByAsc(FmsAuxiliaryTypeDO::getType)
                .orderByAsc(FmsAuxiliaryTypeDO::getId));
    }

    default List<FmsAuxiliaryTypeDO> selectListByIdsAndAccountSetId(
            Collection<Long> ids, Long accountSetId) {
        return selectList(new LambdaQueryWrapperX<FmsAuxiliaryTypeDO>()
                .in(FmsAuxiliaryTypeDO::getId, ids)
                .eq(FmsAuxiliaryTypeDO::getAccountSetId, accountSetId));
    }

    default FmsAuxiliaryTypeDO selectByAccountSetIdAndName(Long accountSetId, String name) {
        return selectOne(new LambdaQueryWrapperX<FmsAuxiliaryTypeDO>()
                .eq(FmsAuxiliaryTypeDO::getAccountSetId, accountSetId)
                .eq(FmsAuxiliaryTypeDO::getName, name));
    }

}

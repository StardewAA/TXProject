package cn.iocoder.txgy.module.erp.dal.mysql.sale;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpCostResultItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ErpCostResultItemMapper extends BaseMapperX<ErpCostResultItemDO> {
    default List<ErpCostResultItemDO> selectListByCostResultId(Long id) { return selectList(ErpCostResultItemDO::getCostResultId, id); }
    default List<ErpCostResultItemDO> selectListByCostResultIds(Collection<Long> ids) { return selectList(ErpCostResultItemDO::getCostResultId, ids); }
    default int deleteByCostResultId(Long id) { return delete(ErpCostResultItemDO::getCostResultId, id); }
}

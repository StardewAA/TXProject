package cn.iocoder.txgy.module.erp.dal.mysql.purchase;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpArrivalInspectionItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ErpArrivalInspectionItemMapper extends BaseMapperX<ErpArrivalInspectionItemDO> {
    default List<ErpArrivalInspectionItemDO> selectListByInspectionId(Long inspectionId) { return selectList(ErpArrivalInspectionItemDO::getInspectionId, inspectionId); }
    default List<ErpArrivalInspectionItemDO> selectListByInspectionIds(Collection<Long> inspectionIds) { return selectList(ErpArrivalInspectionItemDO::getInspectionId, inspectionIds); }
    default int deleteByInspectionId(Long inspectionId) { return delete(ErpArrivalInspectionItemDO::getInspectionId, inspectionId); }
}

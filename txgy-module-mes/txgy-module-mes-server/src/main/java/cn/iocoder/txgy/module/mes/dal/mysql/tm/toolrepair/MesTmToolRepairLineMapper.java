package cn.iocoder.txgy.module.mes.dal.mysql.tm.toolrepair;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolrepair.MesTmToolRepairLineDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MesTmToolRepairLineMapper extends BaseMapperX<MesTmToolRepairLineDO> {

    default List<MesTmToolRepairLineDO> selectListByRepairId(Long repairId) {
        return selectList(MesTmToolRepairLineDO::getRepairId, repairId);
    }

    default void deleteByRepairId(Long repairId) {
        delete(MesTmToolRepairLineDO::getRepairId, repairId);
    }
}

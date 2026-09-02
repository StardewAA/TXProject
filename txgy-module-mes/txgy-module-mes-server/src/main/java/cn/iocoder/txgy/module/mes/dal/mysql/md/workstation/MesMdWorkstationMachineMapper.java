package cn.iocoder.txgy.module.mes.dal.mysql.md.workstation;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.workstation.MesMdWorkstationMachineDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * MES 设备资源 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesMdWorkstationMachineMapper extends BaseMapperX<MesMdWorkstationMachineDO> {

    default List<MesMdWorkstationMachineDO> selectListByWorkstationId(Long workstationId) {
        return selectList(MesMdWorkstationMachineDO::getWorkstationId, workstationId);
    }

    default MesMdWorkstationMachineDO selectByMachineryId(Long machineryId) {
        return selectOne(MesMdWorkstationMachineDO::getMachineryId, machineryId);
    }

    default void deleteByWorkstationId(Long workstationId) {
        delete(MesMdWorkstationMachineDO::getWorkstationId, workstationId);
    }

}

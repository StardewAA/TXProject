package cn.iocoder.txgy.module.mes.dal.mysql.dv.repair;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.controller.admin.dv.repair.vo.MesDvRepairPageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.dv.repair.MesDvRepairDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * MES 维修工单 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesDvRepairMapper extends BaseMapperX<MesDvRepairDO> {

    default PageResult<MesDvRepairDO> selectPage(MesDvRepairPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MesDvRepairDO>()
                .likeIfPresent(MesDvRepairDO::getCode, reqVO.getCode())
                .likeIfPresent(MesDvRepairDO::getName, reqVO.getName())
                .eqIfPresent(MesDvRepairDO::getMachineryId, reqVO.getMachineryId())
                .eqIfPresent(MesDvRepairDO::getResult, reqVO.getResult())
                .eqIfPresent(MesDvRepairDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(MesDvRepairDO::getRequireDate, reqVO.getRequireDate())
                .orderByDesc(MesDvRepairDO::getId));
    }

    default MesDvRepairDO selectByCode(String code) {
        return selectOne(MesDvRepairDO::getCode, code);
    }

    default Long selectCountByMachineryId(Long machineryId) {
        return selectCount(MesDvRepairDO::getMachineryId, machineryId);
    }

}

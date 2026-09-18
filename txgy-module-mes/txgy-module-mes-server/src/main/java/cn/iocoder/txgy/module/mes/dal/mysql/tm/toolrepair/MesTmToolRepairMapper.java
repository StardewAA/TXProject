package cn.iocoder.txgy.module.mes.dal.mysql.tm.toolrepair;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolrepair.vo.MesTmToolRepairPageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolrepair.MesTmToolRepairDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MesTmToolRepairMapper extends BaseMapperX<MesTmToolRepairDO> {

    default PageResult<MesTmToolRepairDO> selectPage(MesTmToolRepairPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MesTmToolRepairDO>()
                .likeIfPresent(MesTmToolRepairDO::getCode, reqVO.getCode())
                .likeIfPresent(MesTmToolRepairDO::getName, reqVO.getName())
                .eqIfPresent(MesTmToolRepairDO::getStatus, reqVO.getStatus())
                .orderByDesc(MesTmToolRepairDO::getId));
    }

    default MesTmToolRepairDO selectByCode(String code) {
        return selectOne(MesTmToolRepairDO::getCode, code);
    }
}

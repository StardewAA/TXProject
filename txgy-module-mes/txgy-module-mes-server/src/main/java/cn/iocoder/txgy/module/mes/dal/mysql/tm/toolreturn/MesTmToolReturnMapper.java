package cn.iocoder.txgy.module.mes.dal.mysql.tm.toolreturn;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolreturn.vo.MesTmToolReturnPageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolreturn.MesTmToolReturnDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MesTmToolReturnMapper extends BaseMapperX<MesTmToolReturnDO> {

    default PageResult<MesTmToolReturnDO> selectPage(MesTmToolReturnPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MesTmToolReturnDO>()
                .likeIfPresent(MesTmToolReturnDO::getCode, reqVO.getCode())
                .likeIfPresent(MesTmToolReturnDO::getName, reqVO.getName())
                .eqIfPresent(MesTmToolReturnDO::getWorkstationId, reqVO.getWorkstationId())
                .eqIfPresent(MesTmToolReturnDO::getStatus, reqVO.getStatus())
                .orderByDesc(MesTmToolReturnDO::getId));
    }

    default MesTmToolReturnDO selectByCode(String code) {
        return selectOne(MesTmToolReturnDO::getCode, code);
    }
}

package cn.iocoder.txgy.module.mes.dal.mysql.tm.toolscrap;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolscrap.vo.MesTmToolScrapPageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolscrap.MesTmToolScrapDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MesTmToolScrapMapper extends BaseMapperX<MesTmToolScrapDO> {

    default PageResult<MesTmToolScrapDO> selectPage(MesTmToolScrapPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MesTmToolScrapDO>()
                .likeIfPresent(MesTmToolScrapDO::getCode, reqVO.getCode())
                .likeIfPresent(MesTmToolScrapDO::getName, reqVO.getName())
                .eqIfPresent(MesTmToolScrapDO::getStatus, reqVO.getStatus())
                .orderByDesc(MesTmToolScrapDO::getId));
    }

    default MesTmToolScrapDO selectByCode(String code) {
        return selectOne(MesTmToolScrapDO::getCode, code);
    }
}

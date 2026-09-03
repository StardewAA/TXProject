package cn.iocoder.txgy.module.mes.dal.mysql.pro.project;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectPageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.project.MesProProjectDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * MES 项目 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesProProjectMapper extends BaseMapperX<MesProProjectDO> {

    default PageResult<MesProProjectDO> selectPage(MesProProjectPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MesProProjectDO>()
                .likeIfPresent(MesProProjectDO::getCode, reqVO.getCode())
                .likeIfPresent(MesProProjectDO::getName, reqVO.getName())
                .eqIfPresent(MesProProjectDO::getStatus, reqVO.getStatus())
                .eqIfPresent(MesProProjectDO::getSourceType, reqVO.getSourceType())
                .orderByDesc(MesProProjectDO::getId));
    }

    default MesProProjectDO selectByCode(String code) {
        return selectOne(MesProProjectDO::getCode, code);
    }

}

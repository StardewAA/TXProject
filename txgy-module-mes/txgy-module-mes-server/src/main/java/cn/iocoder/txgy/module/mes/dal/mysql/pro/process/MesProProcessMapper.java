package cn.iocoder.txgy.module.mes.dal.mysql.pro.process;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.controller.admin.pro.process.vo.MesProProcessPageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.process.MesProProcessDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * MES 生产工序 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesProProcessMapper extends BaseMapperX<MesProProcessDO> {

    default PageResult<MesProProcessDO> selectPage(MesProProcessPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MesProProcessDO>()
                .likeIfPresent(MesProProcessDO::getCode, reqVO.getCode())
                .likeIfPresent(MesProProcessDO::getName, reqVO.getName())
                .eqIfPresent(MesProProcessDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(MesProProcessDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(MesProProcessDO::getId));
    }

    default List<MesProProcessDO> selectList(MesProProcessPageReqVO reqVO) {
        return selectList(new LambdaQueryWrapperX<MesProProcessDO>()
                .likeIfPresent(MesProProcessDO::getCode, reqVO.getCode())
                .likeIfPresent(MesProProcessDO::getName, reqVO.getName())
                .eqIfPresent(MesProProcessDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(MesProProcessDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(MesProProcessDO::getId));
    }

    default MesProProcessDO selectByCode(String code) {
        return selectOne(MesProProcessDO::getCode, code);
    }

    default MesProProcessDO selectByName(String name) {
        return selectOne(MesProProcessDO::getName, name);
    }

    default List<MesProProcessDO> selectListByStatus(Integer status) {
        return selectList(MesProProcessDO::getStatus, status);
    }

}

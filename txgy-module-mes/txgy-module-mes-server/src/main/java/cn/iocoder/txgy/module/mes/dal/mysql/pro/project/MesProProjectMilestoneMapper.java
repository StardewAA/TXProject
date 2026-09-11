package cn.iocoder.txgy.module.mes.dal.mysql.pro.project;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.project.MesProProjectMilestoneDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * MES 项目里程碑 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesProProjectMilestoneMapper extends BaseMapperX<MesProProjectMilestoneDO> {

    default List<MesProProjectMilestoneDO> selectListByProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<MesProProjectMilestoneDO>()
                .eq(MesProProjectMilestoneDO::getProjectId, projectId)
                .orderByAsc(MesProProjectMilestoneDO::getSort)
                .orderByAsc(MesProProjectMilestoneDO::getId));
    }

    default MesProProjectMilestoneDO selectByProjectIdAndName(Long projectId, String name) {
        return selectOne(new LambdaQueryWrapperX<MesProProjectMilestoneDO>()
                .eq(MesProProjectMilestoneDO::getProjectId, projectId)
                .eq(MesProProjectMilestoneDO::getName, name));
    }

}

package cn.iocoder.txgy.module.mes.dal.mysql.pro.project;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.project.MesProProjectMemberDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * MES 项目成员 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesProProjectMemberMapper extends BaseMapperX<MesProProjectMemberDO> {

    default List<MesProProjectMemberDO> selectListByProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<MesProProjectMemberDO>()
                .eq(MesProProjectMemberDO::getProjectId, projectId)
                .orderByAsc(MesProProjectMemberDO::getId));
    }

    default MesProProjectMemberDO selectByProjectIdAndName(Long projectId, String userName) {
        return selectOne(new LambdaQueryWrapperX<MesProProjectMemberDO>()
                .eq(MesProProjectMemberDO::getProjectId, projectId)
                .eq(MesProProjectMemberDO::getUserName, userName));
    }

}

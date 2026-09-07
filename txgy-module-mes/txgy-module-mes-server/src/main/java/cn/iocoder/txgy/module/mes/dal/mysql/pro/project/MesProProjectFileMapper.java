package cn.iocoder.txgy.module.mes.dal.mysql.pro.project;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.project.MesProProjectFileDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * MES 项目附件 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesProProjectFileMapper extends BaseMapperX<MesProProjectFileDO> {

    default List<MesProProjectFileDO> selectListByProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<MesProProjectFileDO>()
                .eq(MesProProjectFileDO::getProjectId, projectId)
                .orderByDesc(MesProProjectFileDO::getId));
    }

}

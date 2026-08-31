package cn.iocoder.txgy.module.mes.dal.mysql.cal.team;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.controller.admin.cal.team.vo.member.MesCalTeamMemberPageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.cal.team.MesCalTeamMemberDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * MES 班组成员 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesCalTeamMemberMapper extends BaseMapperX<MesCalTeamMemberDO> {

    default PageResult<MesCalTeamMemberDO> selectPage(MesCalTeamMemberPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MesCalTeamMemberDO>()
                .eqIfPresent(MesCalTeamMemberDO::getTeamId, reqVO.getTeamId())
                .orderByDesc(MesCalTeamMemberDO::getId));
    }

    default List<MesCalTeamMemberDO> selectListByTeamId(Long teamId) {
        return selectList(MesCalTeamMemberDO::getTeamId, teamId);
    }

    default List<MesCalTeamMemberDO> selectListByTeamIds(Collection<Long> teamIds) {
        return selectList(MesCalTeamMemberDO::getTeamId, teamIds);
    }

    default MesCalTeamMemberDO selectByUserId(Long userId) {
        return selectOne(MesCalTeamMemberDO::getUserId, userId);
    }

    default void deleteByTeamId(Long teamId) {
        delete(MesCalTeamMemberDO::getTeamId, teamId);
    }

}

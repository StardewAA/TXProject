package cn.iocoder.txgy.module.bidding.dal.mysql.personnel;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.ProjectKeyPostAssignmentDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

/**
 * 项目关键岗位任职流水 Mapper。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§1.1(e)/§1.3（同一项目同一关键岗位变更次数统计）。
 * <p>设计遵循/针对：按 (project_id, post_code, start_at) 取窗口内记录（命中 idx_post_project），
 * 统计与明细共用同一查询，避免"统计一个口径、明细另一个口径"。
 * <p>业务目标：关键岗位频繁变更可统计、可下钻到人。
 */
@Mapper
public interface ProjectKeyPostAssignmentMapper extends BaseMapperX<ProjectKeyPostAssignmentDO> {

    /** 取某项目窗口内的任职记录（按岗位、开始时间升序）。 */
    default List<ProjectKeyPostAssignmentDO> selectByProjectInWindow(Long projectId, LocalDate windowStart) {
        return selectList(new LambdaQueryWrapper<ProjectKeyPostAssignmentDO>()
                .eq(ProjectKeyPostAssignmentDO::getProjectId, projectId)
                .ge(ProjectKeyPostAssignmentDO::getStartAt, windowStart)
                .orderByAsc(ProjectKeyPostAssignmentDO::getPostCode)
                .orderByAsc(ProjectKeyPostAssignmentDO::getStartAt));
    }

    /** 取某项目某岗位当前在任记录（end_at 为空）。 */
    default ProjectKeyPostAssignmentDO selectCurrent(Long projectId, String postCode) {
        return selectOne(new LambdaQueryWrapper<ProjectKeyPostAssignmentDO>()
                .eq(ProjectKeyPostAssignmentDO::getProjectId, projectId)
                .eq(ProjectKeyPostAssignmentDO::getPostCode, postCode)
                .isNull(ProjectKeyPostAssignmentDO::getEndAt)
                .orderByDesc(ProjectKeyPostAssignmentDO::getChangeSeq)
                .last("LIMIT 1"));
    }

    /** 取某项目某岗位已出现的最大变更序号（登记下一条时 +1）。 */
    default Integer selectMaxSeq(Long projectId, String postCode) {
        ProjectKeyPostAssignmentDO last = selectOne(new LambdaQueryWrapper<ProjectKeyPostAssignmentDO>()
                .eq(ProjectKeyPostAssignmentDO::getProjectId, projectId)
                .eq(ProjectKeyPostAssignmentDO::getPostCode, postCode)
                .orderByDesc(ProjectKeyPostAssignmentDO::getChangeSeq)
                .last("LIMIT 1"));
        return last == null ? 0 : last.getChangeSeq();
    }
}

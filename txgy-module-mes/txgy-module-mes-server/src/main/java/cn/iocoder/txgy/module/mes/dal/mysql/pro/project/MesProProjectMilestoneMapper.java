package cn.iocoder.txgy.module.mes.dal.mysql.pro.project;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectMilestoneAlertRespVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.project.MesProProjectMilestoneDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MES 项目里程碑 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesProProjectMilestoneMapper extends BaseMapperX<MesProProjectMilestoneDO> {

    String ALERT_SELECT = "SELECT m.id AS milestoneId, m.project_id AS projectId, " +
            "p.name AS projectName, m.name AS name, m.plan_date AS planDate, " +
            "DATEDIFF(m.plan_date, NOW()) AS daysLeft " +
            "FROM mes_pro_project_milestone m " +
            "INNER JOIN mes_pro_project p ON p.id = m.project_id AND p.deleted = 0 " +
            "WHERE m.deleted = 0 AND m.actual_date IS NULL " +
            "AND m.plan_date IS NOT NULL AND m.plan_date <= DATE_ADD(NOW(), INTERVAL 3 DAY)";

    /**
     * 查询未完成且 3 天内到期（含已超期）的里程碑预警列表
     */
    @Select(ALERT_SELECT + " ORDER BY m.plan_date")
    List<MesProProjectMilestoneAlertRespVO> selectAlertList();

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

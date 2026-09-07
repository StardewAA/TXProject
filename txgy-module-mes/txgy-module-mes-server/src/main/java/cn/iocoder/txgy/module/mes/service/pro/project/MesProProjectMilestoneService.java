package cn.iocoder.txgy.module.mes.service.pro.project;

import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectMilestoneSaveReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.project.MesProProjectMilestoneDO;

import java.util.List;

/**
 * MES 项目里程碑 Service 接口
 *
 * @author OPENLAB BS
 */
public interface MesProProjectMilestoneService {

    /**
     * 创建项目里程碑
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createMilestone(MesProProjectMilestoneSaveReqVO createReqVO);

    /**
     * 更新项目里程碑
     *
     * @param updateReqVO 更新信息
     */
    void updateMilestone(MesProProjectMilestoneSaveReqVO updateReqVO);

    /**
     * 删除项目里程碑
     *
     * @param id 编号
     */
    void deleteMilestone(Long id);

    /**
     * 获得项目里程碑
     *
     * @param id 编号
     * @return 里程碑
     */
    MesProProjectMilestoneDO getMilestone(Long id);

    /**
     * 获得指定项目的全部里程碑（按排序升序）
     *
     * @param projectId 项目编号
     * @return 里程碑列表
     */
    List<MesProProjectMilestoneDO> getMilestoneList(Long projectId);

}

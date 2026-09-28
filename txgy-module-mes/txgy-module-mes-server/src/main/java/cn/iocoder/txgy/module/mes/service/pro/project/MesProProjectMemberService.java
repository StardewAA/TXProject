package cn.iocoder.txgy.module.mes.service.pro.project;

import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectMemberSaveReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.project.MesProProjectMemberDO;

import java.util.List;

/**
 * MES 项目成员 Service 接口
 *
 * @author OPENLAB BS
 */
public interface MesProProjectMemberService {

    /**
     * 创建项目成员
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createProjectMember(MesProProjectMemberSaveReqVO createReqVO);

    /**
     * 更新项目成员
     *
     * @param updateReqVO 更新信息
     */
    void updateProjectMember(MesProProjectMemberSaveReqVO updateReqVO);

    /**
     * 删除项目成员
     *
     * @param id 编号
     */
    void deleteProjectMember(Long id);

    /**
     * 获得项目成员
     *
     * @param id 编号
     * @return 项目成员
     */
    MesProProjectMemberDO getProjectMember(Long id);

    /**
     * 获得指定项目的全部成员
     *
     * @param projectId 项目编号
     * @return 成员列表
     */
    List<MesProProjectMemberDO> getProjectMemberList(Long projectId);

}

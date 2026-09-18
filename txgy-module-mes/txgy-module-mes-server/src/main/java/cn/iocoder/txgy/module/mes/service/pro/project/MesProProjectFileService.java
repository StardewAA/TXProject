package cn.iocoder.txgy.module.mes.service.pro.project;

import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectFileSaveReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.project.MesProProjectFileDO;

import java.util.List;

/**
 * MES 项目附件 Service 接口
 *
 * @author OPENLAB BS
 */
public interface MesProProjectFileService {

    /**
     * 新增项目附件关联
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createProjectFile(MesProProjectFileSaveReqVO createReqVO);

    /**
     * 删除项目附件关联
     *
     * @param id 编号
     */
    void deleteProjectFile(Long id);

    /**
     * 获得指定项目的全部附件
     *
     * @param projectId 项目编号
     * @return 附件列表
     */
    List<MesProProjectFileDO> getProjectFileList(Long projectId);

}

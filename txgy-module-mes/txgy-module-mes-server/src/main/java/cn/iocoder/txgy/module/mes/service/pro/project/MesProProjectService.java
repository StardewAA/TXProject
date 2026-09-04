package cn.iocoder.txgy.module.mes.service.pro.project;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectPageReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectSaveReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectStatisticsRespVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.project.MesProProjectDO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;

/**
 * MES 项目 Service 接口
 *
 * @author OPENLAB BS
 */
public interface MesProProjectService {

    /**
     * 创建项目
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createProject(MesProProjectSaveReqVO createReqVO);

    /**
     * 更新项目
     *
     * @param updateReqVO 更新信息
     */
    void updateProject(MesProProjectSaveReqVO updateReqVO);

    /**
     * 删除项目
     *
     * @param id 编号
     */
    void deleteProject(Long id);

    /**
     * 获得项目
     *
     * @param id 编号
     * @return 项目
     */
    MesProProjectDO getProject(Long id);

    /**
     * 校验项目是否存在，不存在则抛出异常
     *
     * @param id 编号
     * @return 项目
     */
    MesProProjectDO validateProjectExists(Long id);

    /**
     * 获得项目分页
     *
     * @param pageReqVO 分页查询
     * @return 项目分页
     */
    PageResult<MesProProjectDO> getProjectPage(MesProProjectPageReqVO pageReqVO);

    /**
     * 获得项目列表
     *
     * @param ids 编号数组
     * @return 项目列表
     */
    List<MesProProjectDO> getProjectList(Collection<Long> ids);

    /**
     * 获得项目 Map
     *
     * @param ids 编号数组
     * @return 项目 Map
     */
    default Map<Long, MesProProjectDO> getProjectMap(Collection<Long> ids) {
        return convertMap(getProjectList(ids), MesProProjectDO::getId);
    }

    /**
     * 批量将生产工单挂接到项目下
     *
     * @param projectId    项目编号
     * @param workOrderIds 生产工单编号数组
     */
    void bindWorkOrders(Long projectId, List<Long> workOrderIds);

    /**
     * 批量解除生产工单的项目关联
     *
     * @param workOrderIds 生产工单编号数组
     */
    void unbindWorkOrders(List<Long> workOrderIds);

    /**
     * 获得项目的工单进度统计
     *
     * @param projectId 项目编号
     * @return 进度统计
     */
    MesProProjectStatisticsRespVO getProjectStatistics(Long projectId);

    /**
     * 获得项目的工单统计 Map（key 为项目编号），供列表批量拼接使用
     *
     * @param projectIds 项目编号数组
     * @return 项目编号 -> 工单统计
     */
    Map<Long, MesProProjectStatisticsRespVO> getProjectStatisticsMap(Collection<Long> projectIds);

    /**
     * 按工单进度自动流转项目状态
     *
     * 规则：
     * 1. 已暂停 / 已取消（人工状态）与已完成（终态）不参与自动流转
     * 2. 挂接工单后：未开始 -> 进行中
     * 3. 工单全部完工或取消：-> 已完成
     * 4. 工单全部解绑：进行中 -> 未开始
     *
     * @param projectId 项目编号
     */
    void refreshProjectStatus(Long projectId);

}

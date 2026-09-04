package cn.iocoder.txgy.module.mes.service.pro.project;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectPageReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectSaveReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectStatisticsRespVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.project.MesProProjectDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.workorder.MesProWorkOrderDO;
import cn.iocoder.txgy.module.mes.dal.mysql.pro.project.MesProProjectMapper;
import cn.iocoder.txgy.module.mes.dal.mysql.pro.workorder.MesProWorkOrderMapper;
import cn.iocoder.txgy.module.mes.enums.pro.MesProProjectStatusEnum;
import cn.iocoder.txgy.module.mes.enums.pro.MesProWorkOrderStatusEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.*;

/**
 * MES 项目 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class MesProProjectServiceImpl implements MesProProjectService {

    @Resource
    private MesProProjectMapper projectMapper;
    @Resource
    private MesProWorkOrderMapper workOrderMapper;

    @Override
    public Long createProject(MesProProjectSaveReqVO createReqVO) {
        // 1. 校验数据
        validateProjectSaveData(null, createReqVO);
        // 2. 插入项目
        MesProProjectDO project = BeanUtils.toBean(createReqVO, MesProProjectDO.class);
        projectMapper.insert(project);
        return project.getId();
    }

    @Override
    public void updateProject(MesProProjectSaveReqVO updateReqVO) {
        // 1.1 校验存在
        MesProProjectDO dbProject = validateProjectExists(updateReqVO.getId());
        // 1.2 校验状态流转：已完成 / 已取消的项目不允许回退为未开始
        if (MesProProjectStatusEnum.isClosed(dbProject.getStatus())
                && MesProProjectStatusEnum.NOT_STARTED.getStatus().equals(updateReqVO.getStatus())) {
            throw exception(PRO_PROJECT_STATUS_FORBIDDEN);
        }
        // 1.3 校验数据
        validateProjectSaveData(updateReqVO.getId(), updateReqVO);
        // 2. 更新
        MesProProjectDO updateObj = BeanUtils.toBean(updateReqVO, MesProProjectDO.class);
        projectMapper.updateById(updateObj);
    }

    @Override
    public void deleteProject(Long id) {
        // 1. 校验存在
        validateProjectExists(id);
        // 2. 校验项目下没有生产工单
        Long workOrderCount = workOrderMapper.selectCount(MesProWorkOrderDO::getProjectId, id);
        if (workOrderCount > 0) {
            throw exception(PRO_PROJECT_HAS_WORK_ORDERS);
        }
        // 3. 删除
        projectMapper.deleteById(id);
    }

    @Override
    public MesProProjectDO getProject(Long id) {
        return projectMapper.selectById(id);
    }

    @Override
    public MesProProjectDO validateProjectExists(Long id) {
        MesProProjectDO project = projectMapper.selectById(id);
        if (project == null) {
            throw exception(PRO_PROJECT_NOT_EXISTS);
        }
        return project;
    }

    @Override
    public PageResult<MesProProjectDO> getProjectPage(MesProProjectPageReqVO pageReqVO) {
        return projectMapper.selectPage(pageReqVO);
    }

    @Override
    public List<MesProProjectDO> getProjectList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return projectMapper.selectByIds(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindWorkOrders(Long projectId, List<Long> workOrderIds) {
        // 1. 校验项目存在
        MesProProjectDO project = validateProjectExists(projectId);
        // 1.1 校验项目状态：已完成 / 已取消的项目不允许再挂接工单
        if (MesProProjectStatusEnum.isClosed(project.getStatus())) {
            throw exception(PRO_PROJECT_STATUS_CLOSED);
        }
        if (CollUtil.isEmpty(workOrderIds)) {
            return;
        }
        // 2. 校验生产工单存在、且未被取消
        Map<Long, MesProWorkOrderDO> workOrderMap = convertMap(
                workOrderMapper.selectByIds(workOrderIds), MesProWorkOrderDO::getId);
        for (Long workOrderId : workOrderIds) {
            MesProWorkOrderDO workOrder = workOrderMap.get(workOrderId);
            if (workOrder == null) {
                throw exception(PRO_PROJECT_WORK_ORDER_NOT_EXISTS);
            }
            if (ObjUtil.equal(workOrder.getStatus(), MesProWorkOrderStatusEnum.CANCELED.getStatus())) {
                throw exception(PRO_PROJECT_WORK_ORDER_CANCELED);
            }
        }
        // 3. 批量挂接
        workOrderMapper.update(null, new LambdaUpdateWrapper<MesProWorkOrderDO>()
                .in(MesProWorkOrderDO::getId, workOrderIds)
                .set(MesProWorkOrderDO::getProjectId, projectId));
        // 4. 挂接后自动流转项目状态（未开始 -> 进行中 / 工单全部完工 -> 已完成）
        refreshProjectStatus(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindWorkOrders(List<Long> workOrderIds) {
        if (CollUtil.isEmpty(workOrderIds)) {
            return;
        }
        // 1. 记录解绑前所属的项目，解绑后需要重算项目状态
        Set<Long> projectIds = convertSet(workOrderMapper.selectByIds(workOrderIds),
                MesProWorkOrderDO::getProjectId);
        // 2. 解除关联
        workOrderMapper.update(null, new LambdaUpdateWrapper<MesProWorkOrderDO>()
                .in(MesProWorkOrderDO::getId, workOrderIds)
                .set(MesProWorkOrderDO::getProjectId, null));
        // 3. 重算受影响项目的状态
        projectIds.stream().filter(Objects::nonNull).forEach(this::refreshProjectStatus);
    }

    @Override
    public MesProProjectStatisticsRespVO getProjectStatistics(Long projectId) {
        // 1. 校验项目存在
        validateProjectExists(projectId);
        // 2. 查询统计
        List<MesProProjectStatisticsRespVO> statisticsList =
                workOrderMapper.selectProjectStatistics(Collections.singleton(projectId));
        MesProProjectStatisticsRespVO statistics = CollUtil.isEmpty(statisticsList)
                ? new MesProProjectStatisticsRespVO() : statisticsList.get(0);
        statistics.setProjectId(projectId);
        return statistics;
    }

    /**
     * 获得项目的工单统计 Map（key 为项目编号），供列表批量拼接使用
     *
     * @param projectIds 项目编号数组
     * @return 项目编号 -> 工单统计
     */
    @Override
    public Map<Long, MesProProjectStatisticsRespVO> getProjectStatisticsMap(Collection<Long> projectIds) {
        if (CollUtil.isEmpty(projectIds)) {
            return Collections.emptyMap();
        }
        List<MesProProjectStatisticsRespVO> statisticsList =
                workOrderMapper.selectProjectStatistics(projectIds);
        return convertMap(statisticsList, MesProProjectStatisticsRespVO::getProjectId);
    }

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
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshProjectStatus(Long projectId) {
        MesProProjectDO project = projectMapper.selectById(projectId);
        if (project == null) {
            return;
        }
        // 1. 人工状态与终态不参与自动流转
        if (MesProProjectStatusEnum.isClosed(project.getStatus())
                || MesProProjectStatusEnum.isManual(project.getStatus())) {
            return;
        }
        // 2. 统计项目下的工单
        List<MesProProjectStatisticsRespVO> statisticsList =
                workOrderMapper.selectProjectStatistics(Collections.singleton(projectId));
        MesProProjectStatisticsRespVO statistics = CollUtil.isEmpty(statisticsList)
                ? null : statisticsList.get(0);
        Integer newStatus = null;
        if (statistics == null) {
            // 3. 无关联工单：进行中回退为未开始
            if (MesProProjectStatusEnum.PROCESSING.getStatus().equals(project.getStatus())) {
                newStatus = MesProProjectStatusEnum.NOT_STARTED.getStatus();
            }
        } else {
            int total = statistics.getWorkOrderTotal() == null ? 0 : statistics.getWorkOrderTotal();
            if (total == 0) {
                if (MesProProjectStatusEnum.PROCESSING.getStatus().equals(project.getStatus())) {
                    newStatus = MesProProjectStatusEnum.NOT_STARTED.getStatus();
                }
            } else {
                int finished = statistics.getFinishedCount() == null
                        ? 0 : statistics.getFinishedCount();
                int canceled = statistics.getCanceledCount() == null
                        ? 0 : statistics.getCanceledCount();
                if (finished + canceled >= total) {
                    // 4. 工单全部完工 / 取消：项目已完成
                    newStatus = MesProProjectStatusEnum.FINISHED.getStatus();
                } else if (MesProProjectStatusEnum.NOT_STARTED.getStatus()
                        .equals(project.getStatus())) {
                    // 5. 已挂接工单：未开始 -> 进行中
                    newStatus = MesProProjectStatusEnum.PROCESSING.getStatus();
                }
            }
        }
        if (newStatus != null && ObjUtil.notEqual(newStatus, project.getStatus())) {
            projectMapper.update(null, new LambdaUpdateWrapper<MesProProjectDO>()
                    .eq(MesProProjectDO::getId, projectId)
                    .set(MesProProjectDO::getStatus, newStatus));
        }
    }

    // ==================== 校验方法 ====================

    private void validateProjectSaveData(Long id, MesProProjectSaveReqVO reqVO) {
        validateProjectCodeUnique(id, reqVO.getCode());
    }

    private void validateProjectCodeUnique(Long id, String code) {
        if (code == null) {
            return;
        }
        MesProProjectDO project = projectMapper.selectByCode(code);
        if (project == null) {
            return;
        }
        if (ObjUtil.notEqual(project.getId(), id)) {
            throw exception(PRO_PROJECT_CODE_DUPLICATE);
        }
    }

}

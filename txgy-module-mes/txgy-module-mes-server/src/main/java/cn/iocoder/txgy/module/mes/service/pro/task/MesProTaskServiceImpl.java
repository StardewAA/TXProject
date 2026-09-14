package cn.iocoder.txgy.module.mes.service.pro.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.mes.controller.admin.pro.task.vo.MesProTaskPageReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.pro.task.vo.MesProTaskSaveReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.item.MesMdItemDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.unitmeasure.MesMdUnitMeasureDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.workstation.MesMdWorkstationDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.process.MesProProcessDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.route.MesProRouteProcessDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.route.MesProRouteProductDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.task.MesProTaskDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.workorder.MesProWorkOrderDO;
import cn.iocoder.txgy.module.mes.dal.mysql.pro.task.MesProTaskMapper;
import cn.iocoder.txgy.module.mes.enums.md.autocode.MesMdAutoCodeRuleCodeEnum;
import cn.iocoder.txgy.module.mes.enums.pro.MesProTaskStatusEnum;
import cn.iocoder.txgy.module.mes.service.md.autocode.MesMdAutoCodeRecordService;
import cn.iocoder.txgy.module.mes.service.md.item.MesMdItemService;
import cn.iocoder.txgy.module.mes.service.md.unitmeasure.MesMdUnitMeasureService;
import cn.iocoder.txgy.module.mes.service.md.workstation.MesMdWorkstationService;
import cn.iocoder.txgy.module.mes.service.pro.process.MesProProcessService;
import cn.iocoder.txgy.module.mes.service.pro.route.MesProRouteProcessService;
import cn.iocoder.txgy.module.mes.service.pro.route.MesProRouteProductService;
import cn.iocoder.txgy.module.mes.service.pro.route.MesProRouteService;
import cn.iocoder.txgy.module.mes.service.pro.workorder.MesProWorkOrderService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.PRO_TASK_ALREADY_FINISHED;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.PRO_TASK_NOT_EXISTS;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.PRO_WORK_ORDER_ROUTE_REQUIRED;

/**
 * MES 生产任务 Service 实现类
 *
 * @author OPENLAB BS
 */
@Slf4j
@Service
@Validated
public class MesProTaskServiceImpl implements MesProTaskService {

    @Resource
    private MesProTaskMapper taskMapper;

    @Resource
    @Lazy
    private MesProWorkOrderService workOrderService;
    @Resource
    private MesMdWorkstationService workstationService;
    @Resource
    private MesProProcessService processService;
    @Resource
    private MesMdItemService itemService;
    @Resource
    private MesMdUnitMeasureService unitMeasureService;
    @Resource
    private MesProRouteService routeService;
    @Resource
    private MesMdAutoCodeRecordService autoCodeRecordService;
    @Resource
    private MesProRouteProcessService routeProcessService;
    @Resource
    private MesProRouteProductService routeProductService;

    @Override
    public Long createTask(MesProTaskSaveReqVO createReqVO) {
        // 1. 校验关联数据存在
        MesProWorkOrderDO workOrder = workOrderService.validateWorkOrderExists(createReqVO.getWorkOrderId());
        workstationService.validateWorkstationExists(createReqVO.getWorkstationId());
        routeService.validateRouteExists(createReqVO.getRouteId());
        processService.validateProcessExistsAndEnable(createReqVO.getProcessId());
        MesMdItemDO item = itemService.validateItemExists(createReqVO.getItemId());

        // 2.1 构建任务 DO：自动填充客户信息
        MesProTaskDO task = BeanUtils.toBean(createReqVO, MesProTaskDO.class)
                .setName(buildTaskName(item, createReqVO.getQuantity()))
                .setCode(autoCodeRecordService.generateAutoCode(MesMdAutoCodeRuleCodeEnum.PRO_TASK_CODE.getCode()))
                .setStatus(MesProTaskStatusEnum.PREPARE.getStatus());
        if (task.getClientId() == null && workOrder.getClientId() != null) {
            task.setClientId(workOrder.getClientId());
        }
        // 2.2 插入
        taskMapper.insert(task);
        return task.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer autoGenerateTasksByRoute(Long workOrderId) {
        // 1.1 校验工单存在
        MesProWorkOrderDO workOrder = workOrderService.validateWorkOrderExists(workOrderId);
        // 1.2 校验产品已绑定工艺路线
        MesProRouteProductDO routeProduct = routeProductService.getRouteProductByItemId(workOrder.getProductId());
        if (routeProduct == null) {
            throw exception(PRO_WORK_ORDER_ROUTE_REQUIRED);
        }
        // 2. 查询工艺路线的工序序列并按顺序排布
        List<MesProRouteProcessDO> routeProcesses =
                routeProcessService.getRouteProcessListByProductId(workOrder.getProductId());
        if (CollUtil.isEmpty(routeProcesses)) {
            throw exception(PRO_WORK_ORDER_ROUTE_REQUIRED);
        }
        routeProcesses.sort(Comparator.comparing(
                rp -> rp.getSort() == null ? Integer.MAX_VALUE : rp.getSort()));
        // 2.1 解析默认工位：任务表 workstation_id 非空约束，取第一个启用工位兜底
        List<MesMdWorkstationDO> workstations =
                workstationService.getWorkstationListByStatus(CommonStatusEnum.ENABLE.getStatus());
        MesMdWorkstationDO defaultWorkstation = CollUtil.getFirst(workstations);
        if (defaultWorkstation == null) {
            log.warn("[autoGenerateTasksByRoute][工单({}) 无启用工位，跳过任务拆分]", workOrderId);
            return 0;
        }
        // 3. 幂等处理：已有任务的工序自动跳过，重复调用只补缺失工序
        List<MesProTaskDO> existingTasks = taskMapper.selectList(MesProTaskDO::getWorkOrderId, workOrderId);
        Set<Long> existingProcessIds = convertSet(existingTasks, MesProTaskDO::getProcessId);
        // 4. 链式生成：每道工序计划 1 个工作日（8 小时），前一道结束即后一道开始
        MesMdItemDO item = itemService.validateItemExists(workOrder.getProductId());
        LocalDateTime cursor = LocalDateTime.now();
        int created = 0;
        for (MesProRouteProcessDO routeProcess : routeProcesses) {
            if (routeProcess.getProcessId() == null
                    || existingProcessIds.contains(routeProcess.getProcessId())) {
                continue;
            }
            MesProProcessDO process = processService.getProcess(routeProcess.getProcessId());
            String processName = process != null ? process.getName() : "工序";
            MesProTaskDO task = new MesProTaskDO()
                    .setCode(autoCodeRecordService.generateAutoCode(
                            MesMdAutoCodeRuleCodeEnum.PRO_TASK_CODE.getCode()))
                    .setName(item.getName() + " " + processName + "任务")
                    .setWorkOrderId(workOrderId)
                    .setRouteId(routeProduct.getRouteId())
                    .setProcessId(routeProcess.getProcessId())
                    .setItemId(workOrder.getProductId())
                    .setQuantity(workOrder.getQuantity())
                    .setWorkstationId(defaultWorkstation.getId())
                    .setStartTime(cursor)
                    .setDuration(1)
                    .setEndTime(cursor.plusHours(8))
                    .setClientId(workOrder.getClientId())
                    .setStatus(MesProTaskStatusEnum.CONFIRMED.getStatus());
            taskMapper.insert(task);
            cursor = cursor.plusHours(8);
            created++;
        }
        return created;
    }

    @Override
    public void updateTask(MesProTaskSaveReqVO updateReqVO) {
        // 1.1 校验存在
        MesProTaskDO task = validateTaskExists(updateReqVO.getId());
        // 1.2 校验关联数据存在
        if (updateReqVO.getWorkOrderId() != null) {
            workOrderService.validateWorkOrderExists(updateReqVO.getWorkOrderId());
        }
        if (updateReqVO.getWorkstationId() != null) {
            workstationService.validateWorkstationExists(updateReqVO.getWorkstationId());
        }
        if (updateReqVO.getRouteId() != null) {
            routeService.validateRouteExists(updateReqVO.getRouteId());
        }
        if (updateReqVO.getProcessId() != null) {
            processService.validateProcessExistsAndEnable(updateReqVO.getProcessId());
        }
        MesMdItemDO item;
        if (updateReqVO.getItemId() != null) {
            item = itemService.validateItemExists(updateReqVO.getItemId());
        } else {
            item = itemService.validateItemExists(task.getItemId());
        }
        BigDecimal quantity = updateReqVO.getQuantity() != null ? updateReqVO.getQuantity() : task.getQuantity();

        // 2. 更新
        MesProTaskDO updateObj = BeanUtils.toBean(updateReqVO, MesProTaskDO.class)
                .setName(buildTaskName(item, quantity));
        taskMapper.updateById(updateObj);
    }

    /**
     * 构建任务名称，格式："产品名【数量】单位"
     */
    private String buildTaskName(MesMdItemDO item, BigDecimal quantity) {
        String unitName = "";
        if (item.getUnitMeasureId() != null) {
            MesMdUnitMeasureDO unit = unitMeasureService.getUnitMeasure(item.getUnitMeasureId());
            if (unit != null) {
                unitName = unit.getName();
            }
        }
        return item.getName() + "【" + quantity + "】" + unitName;
    }

    @Override
    public void deleteTask(Long id) {
        // 1. 校验存在且非终态
        validateTaskNotFinished(id);

        // 2. 删除
        taskMapper.deleteById(id);
    }

    @Override
    public MesProTaskDO getTask(Long id) {
        return taskMapper.selectById(id);
    }

    @Override
    public PageResult<MesProTaskDO> getTaskPage(MesProTaskPageReqVO pageReqVO) {
        return taskMapper.selectPage(pageReqVO);
    }

    @Override
    public List<MesProTaskDO> getTaskListByWorkOrderIds(Collection<Long> workOrderIds) {
        if (CollUtil.isEmpty(workOrderIds)) {
            return Collections.emptyList();
        }
        return taskMapper.selectListByWorkOrderIds(workOrderIds);
    }

    @Override
    public MesProTaskDO validateTaskExists(Long id) {
        MesProTaskDO task = taskMapper.selectById(id);
        if (task == null) {
            throw exception(PRO_TASK_NOT_EXISTS);
        }
        return task;
    }

    @Override
    public MesProTaskDO validateTaskNotFinished(Long id) {
        MesProTaskDO task = validateTaskExists(id);
        if (MesProTaskStatusEnum.isEndStatus(task.getStatus())) {
            throw exception(PRO_TASK_ALREADY_FINISHED);
        }
        return task;
    }

    @Override
    public List<MesProTaskDO> getTaskList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return taskMapper.selectByIds(ids);
    }

    @Override
    public void finishTaskByOrderId(Long workOrderId) {
        // 1. 读取工单下所有任务，过滤掉已完成/已取消的
        List<MesProTaskDO> tasks = taskMapper.selectListByWorkOrderId(workOrderId);
        LocalDateTime now = LocalDateTime.now();
        // 2. 构建待更新列表，批量更新
        List<MesProTaskDO> updateList = convertList(tasks,
                task -> new MesProTaskDO().setId(task.getId())
                        .setStatus(MesProTaskStatusEnum.FINISHED.getStatus()).setFinishDate(now),
                task -> !MesProTaskStatusEnum.isEndStatus(task.getStatus()));
        if (CollUtil.isNotEmpty(updateList)) {
            taskMapper.updateBatch(updateList);
        }
    }

    @Override
    public void cancelTaskByOrderId(Long workOrderId) {
        // 1. 读取工单下所有任务，过滤掉已完成/已取消的
        List<MesProTaskDO> tasks = taskMapper.selectListByWorkOrderId(workOrderId);
        LocalDateTime now = LocalDateTime.now();
        // 2. 构建待更新列表，批量更新
        List<MesProTaskDO> updateList = convertList(tasks,
                task -> new MesProTaskDO().setId(task.getId())
                        .setStatus(MesProTaskStatusEnum.CANCELED.getStatus()).setCancelDate(now),
                task -> !MesProTaskStatusEnum.isEndStatus(task.getStatus()));
        if (CollUtil.isNotEmpty(updateList)) {
            taskMapper.updateBatch(updateList);
        }
    }

    @Override
    public void updateProducedQuantity(Long id,
                                       BigDecimal incrProducedQuantity,
                                       BigDecimal incrQualifyQuantity,
                                       BigDecimal incrUnqualifyQuantity) {
        // 校验任务存在
        validateTaskExists(id);
        // 更新数量
        taskMapper.updateProducedQuantity(id, incrProducedQuantity, incrQualifyQuantity, incrUnqualifyQuantity);
    }

}

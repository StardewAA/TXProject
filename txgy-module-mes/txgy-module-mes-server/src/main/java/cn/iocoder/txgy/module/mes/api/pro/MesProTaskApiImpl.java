package cn.iocoder.txgy.module.mes.api.pro;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.mes.api.pro.dto.MesProTaskRespDTO;
import cn.iocoder.txgy.module.mes.controller.admin.pro.task.vo.MesProTaskPageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.item.MesMdItemDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.workstation.MesMdWorkstationDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.process.MesProProcessDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.task.MesProTaskDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.workorder.MesProWorkOrderDO;
import cn.iocoder.txgy.module.mes.service.md.item.MesMdItemService;
import cn.iocoder.txgy.module.mes.service.md.workstation.MesMdWorkstationService;
import cn.iocoder.txgy.module.mes.service.pro.process.MesProProcessService;
import cn.iocoder.txgy.module.mes.service.pro.task.MesProTaskService;
import cn.iocoder.txgy.module.mes.service.pro.workorder.MesProWorkOrderService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;

/**
 * 生产任务 RPC API 实现类
 *
 * @author OPENLAB BS
 */
@RestController
@Validated
public class MesProTaskApiImpl implements MesProTaskApi {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private MesProTaskService taskService;
    @Resource
    private MesProWorkOrderService workOrderService;
    @Resource
    private MesMdItemService itemService;
    @Resource
    private MesMdWorkstationService workstationService;
    @Resource
    private MesProProcessService processService;

    @Override
    public CommonResult<MesProTaskRespDTO> getTask(Long id) {
        MesProTaskDO task = taskService.getTask(id);
        if (task == null) {
            return success(null);
        }
        return success(buildRespDTO(task));
    }

    @Override
    public CommonResult<PageResult<MesProTaskRespDTO>> getTaskPage(String code, String name, Long workOrderId,
                                                                   Integer status, String createTimeBegin, String createTimeEnd,
                                                                   Integer pageNo, Integer pageSize) {
        MesProTaskPageReqVO pageReqVO = new MesProTaskPageReqVO();
        pageReqVO.setCode(code);
        pageReqVO.setName(name);
        pageReqVO.setWorkOrderId(workOrderId);
        pageReqVO.setStatus(status);
        if (createTimeBegin != null || createTimeEnd != null) {
            pageReqVO.setCreateTime(new LocalDateTime[]{
                    parseTime(createTimeBegin, true),
                    parseTime(createTimeEnd, false)});
        }
        pageReqVO.setPageNo(pageNo);
        pageReqVO.setPageSize(pageSize);
        PageResult<MesProTaskDO> pageResult = taskService.getTaskPage(pageReqVO);
        return success(new PageResult<>(buildRespDTOList(pageResult.getList()), pageResult.getTotal()));
    }

    private MesProTaskRespDTO buildRespDTO(MesProTaskDO task) {
        MesProTaskRespDTO dto = BeanUtils.toBean(task, MesProTaskRespDTO.class);
        if (task.getWorkOrderId() != null) {
            MesProWorkOrderDO workOrder = workOrderService.getWorkOrder(task.getWorkOrderId());
            if (workOrder != null) {
                dto.setWorkOrderCode(workOrder.getCode());
            }
        }
        if (task.getItemId() != null) {
            MesMdItemDO item = itemService.getItem(task.getItemId());
            if (item != null) {
                dto.setItemCode(item.getCode());
                dto.setItemName(item.getName());
            }
        }
        if (task.getWorkstationId() != null) {
            MesMdWorkstationDO workstation = workstationService.getWorkstation(task.getWorkstationId());
            if (workstation != null) {
                dto.setWorkstationName(workstation.getName());
            }
        }
        if (task.getProcessId() != null) {
            MesProProcessDO process = processService.getProcess(task.getProcessId());
            if (process != null) {
                dto.setProcessName(process.getName());
            }
        }
        return dto;
    }

    private List<MesProTaskRespDTO> buildRespDTOList(List<MesProTaskDO> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        // 批量组装工单编码、产品、工作站、工序
        Map<Long, MesProWorkOrderDO> workOrderMap = workOrderService.getWorkOrderMap(
                convertSet(list, MesProTaskDO::getWorkOrderId));
        Map<Long, MesMdItemDO> itemMap = itemService.getItemMap(convertSet(list, MesProTaskDO::getItemId));
        Map<Long, MesMdWorkstationDO> workstationMap = workstationService.getWorkstationMap(
                convertSet(list, MesProTaskDO::getWorkstationId));
        Map<Long, MesProProcessDO> processMap = processService.getProcessMap(
                convertSet(list, MesProTaskDO::getProcessId));
        return list.stream().map(task -> {
            MesProTaskRespDTO dto = BeanUtils.toBean(task, MesProTaskRespDTO.class);
            MesProWorkOrderDO workOrder = workOrderMap.get(task.getWorkOrderId());
            if (workOrder != null) {
                dto.setWorkOrderCode(workOrder.getCode());
            }
            MesMdItemDO item = itemMap.get(task.getItemId());
            if (item != null) {
                dto.setItemCode(item.getCode());
                dto.setItemName(item.getName());
            }
            MesMdWorkstationDO workstation = workstationMap.get(task.getWorkstationId());
            if (workstation != null) {
                dto.setWorkstationName(workstation.getName());
            }
            MesProProcessDO process = processMap.get(task.getProcessId());
            if (process != null) {
                dto.setProcessName(process.getName());
            }
            return dto;
        }).toList();
    }

    private LocalDateTime parseTime(String time, boolean isBegin) {
        if (time == null || time.isBlank()) {
            return null;
        }
        // 兼容只传日期的情况
        if (time.length() <= 10) {
            time = time + (isBegin ? " 00:00:00" : " 23:59:59");
        }
        return LocalDateTime.parse(time, DATE_TIME_FORMATTER);
    }

}

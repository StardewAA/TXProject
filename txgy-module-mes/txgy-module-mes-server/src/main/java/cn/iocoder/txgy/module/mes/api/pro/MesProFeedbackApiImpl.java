package cn.iocoder.txgy.module.mes.api.pro;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.mes.api.pro.dto.MesProFeedbackRespDTO;
import cn.iocoder.txgy.module.mes.controller.admin.pro.feedback.vo.MesProFeedbackPageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.item.MesMdItemDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.workstation.MesMdWorkstationDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.feedback.MesProFeedbackDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.process.MesProProcessDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.task.MesProTaskDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.workorder.MesProWorkOrderDO;
import cn.iocoder.txgy.module.mes.service.md.item.MesMdItemService;
import cn.iocoder.txgy.module.mes.service.md.workstation.MesMdWorkstationService;
import cn.iocoder.txgy.module.mes.service.pro.feedback.MesProFeedbackService;
import cn.iocoder.txgy.module.mes.service.pro.process.MesProProcessService;
import cn.iocoder.txgy.module.mes.service.pro.task.MesProTaskService;
import cn.iocoder.txgy.module.mes.service.pro.workorder.MesProWorkOrderService;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;

/**
 * 生产报工 RPC API 实现类
 *
 * @author OPENLAB BS
 */
@RestController
@Validated
public class MesProFeedbackApiImpl implements MesProFeedbackApi {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private MesProFeedbackService feedbackService;
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
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public CommonResult<MesProFeedbackRespDTO> getFeedback(Long id) {
        MesProFeedbackDO feedback = feedbackService.getFeedback(id);
        if (feedback == null) {
            return success(null);
        }
        return success(buildRespDTO(feedback));
    }

    @Override
    public CommonResult<PageResult<MesProFeedbackRespDTO>> getFeedbackPage(String code, Long workOrderId,
                                                                           Integer status, Long itemId,
                                                                           String feedbackTimeBegin, String feedbackTimeEnd,
                                                                           Integer pageNo, Integer pageSize) {
        MesProFeedbackPageReqVO pageReqVO = new MesProFeedbackPageReqVO();
        pageReqVO.setCode(code);
        pageReqVO.setWorkOrderId(workOrderId);
        pageReqVO.setStatus(status);
        pageReqVO.setItemId(itemId);
        if (feedbackTimeBegin != null || feedbackTimeEnd != null) {
            pageReqVO.setFeedbackTime(new LocalDateTime[]{
                    parseTime(feedbackTimeBegin, true),
                    parseTime(feedbackTimeEnd, false)});
        }
        pageReqVO.setPageNo(pageNo);
        pageReqVO.setPageSize(pageSize);
        PageResult<MesProFeedbackDO> pageResult = feedbackService.getFeedbackPage(pageReqVO);
        return success(new PageResult<>(buildRespDTOList(pageResult.getList()), pageResult.getTotal()));
    }

    private MesProFeedbackRespDTO buildRespDTO(MesProFeedbackDO feedback) {
        MesProFeedbackRespDTO dto = BeanUtils.toBean(feedback, MesProFeedbackRespDTO.class);
        if (feedback.getTaskId() != null) {
            MesProTaskDO task = taskService.getTask(feedback.getTaskId());
            if (task != null) {
                dto.setTaskCode(task.getCode());
            }
        }
        if (feedback.getWorkOrderId() != null) {
            MesProWorkOrderDO workOrder = workOrderService.getWorkOrder(feedback.getWorkOrderId());
            if (workOrder != null) {
                dto.setWorkOrderCode(workOrder.getCode());
                dto.setWorkOrderName(workOrder.getName());
            }
        }
        if (feedback.getItemId() != null) {
            MesMdItemDO item = itemService.getItem(feedback.getItemId());
            if (item != null) {
                dto.setItemCode(item.getCode());
                dto.setItemName(item.getName());
            }
        }
        if (feedback.getWorkstationId() != null) {
            MesMdWorkstationDO workstation = workstationService.getWorkstation(feedback.getWorkstationId());
            if (workstation != null) {
                dto.setWorkstationName(workstation.getName());
            }
        }
        if (feedback.getProcessId() != null) {
            MesProProcessDO process = processService.getProcess(feedback.getProcessId());
            if (process != null) {
                dto.setProcessName(process.getName());
            }
        }
        fillUserNickname(dto, feedback.getApproveUserId());
        return dto;
    }

    private List<MesProFeedbackRespDTO> buildRespDTOList(List<MesProFeedbackDO> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        // 批量组装任务、工单、产品、工作站、工序、人员
        Map<Long, MesProTaskDO> taskMap = taskService.getTaskMap(convertSet(list, MesProFeedbackDO::getTaskId));
        Map<Long, MesProWorkOrderDO> workOrderMap = workOrderService.getWorkOrderMap(
                convertSet(list, MesProFeedbackDO::getWorkOrderId));
        Map<Long, MesMdItemDO> itemMap = itemService.getItemMap(convertSet(list, MesProFeedbackDO::getItemId));
        Map<Long, MesMdWorkstationDO> workstationMap = workstationService.getWorkstationMap(
                convertSet(list, MesProFeedbackDO::getWorkstationId));
        Map<Long, MesProProcessDO> processMap = processService.getProcessMap(
                convertSet(list, MesProFeedbackDO::getProcessId));
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(
                convertSet(list, MesProFeedbackDO::getFeedbackUserId));
        return list.stream().map(feedback -> {
            MesProFeedbackRespDTO dto = BeanUtils.toBean(feedback, MesProFeedbackRespDTO.class);
            MesProTaskDO task = taskMap.get(feedback.getTaskId());
            if (task != null) {
                dto.setTaskCode(task.getCode());
            }
            MesProWorkOrderDO workOrder = workOrderMap.get(feedback.getWorkOrderId());
            if (workOrder != null) {
                dto.setWorkOrderCode(workOrder.getCode());
                dto.setWorkOrderName(workOrder.getName());
            }
            MesMdItemDO item = itemMap.get(feedback.getItemId());
            if (item != null) {
                dto.setItemCode(item.getCode());
                dto.setItemName(item.getName());
            }
            MesMdWorkstationDO workstation = workstationMap.get(feedback.getWorkstationId());
            if (workstation != null) {
                dto.setWorkstationName(workstation.getName());
            }
            MesProProcessDO process = processMap.get(feedback.getProcessId());
            if (process != null) {
                dto.setProcessName(process.getName());
            }
            AdminUserRespDTO user = userMap.get(feedback.getFeedbackUserId());
            if (user != null) {
                dto.setFeedbackUserNickname(user.getNickname());
            }
            if (feedback.getApproveUserId() != null) {
                AdminUserRespDTO approveUser = adminUserApi.getUser(feedback.getApproveUserId()).getCheckedData();
                if (approveUser != null) {
                    dto.setApproveUserNickname(approveUser.getNickname());
                }
            }
            return dto;
        }).toList();
    }

    private void fillUserNickname(MesProFeedbackRespDTO dto, Long approveUserId) {
        if (dto.getFeedbackUserId() != null) {
            AdminUserRespDTO user = adminUserApi.getUser(dto.getFeedbackUserId()).getCheckedData();
            if (user != null) {
                dto.setFeedbackUserNickname(user.getNickname());
            }
        }
        if (approveUserId == null) {
            return;
        }
        AdminUserRespDTO approveUser = adminUserApi.getUser(approveUserId).getCheckedData();
        if (Objects.nonNull(approveUser)) {
            dto.setApproveUserNickname(approveUser.getNickname());
        }
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

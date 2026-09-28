package cn.iocoder.txgy.module.bpm.api.task;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.txgy.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceCancelReqVO;
import cn.iocoder.txgy.module.bpm.service.task.BpmProcessInstanceService;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * Flowable 流程实例 Api 实现类
 *
 * @author OPENLAB BS
 * @author jason
 */
@RestController
@Validated
public class BpmProcessInstanceApiImpl implements BpmProcessInstanceApi {

    @Resource
    private BpmProcessInstanceService processInstanceService;

    @Override
    public CommonResult<String> createProcessInstance(Long userId, @Valid BpmProcessInstanceCreateReqDTO reqDTO) {
        return success(processInstanceService.createProcessInstance(userId, reqDTO));
    }

    @Override
    public CommonResult<Boolean> cancelProcessInstanceByStartUser(
            Long userId, String processInstanceId, String reason) {
        BpmProcessInstanceCancelReqVO cancelReqVO = new BpmProcessInstanceCancelReqVO();
        cancelReqVO.setId(processInstanceId);
        cancelReqVO.setReason(reason);
        processInstanceService.cancelProcessInstanceByStartUser(userId, cancelReqVO);
        return success(true);
    }

    @Override
    public CommonResult<Boolean> cancelProcessInstanceByAdminIfRunning(
            Long userId, String processInstanceId, String reason) {
        // 已结束（审批通过/驳回/取消）的流程实例运行时数据已清除，查询返回 null，无需也无法取消
        ProcessInstance instance = processInstanceService.getProcessInstance(processInstanceId);
        if (instance == null) {
            return success(false);
        }
        BpmProcessInstanceCancelReqVO cancelReqVO = new BpmProcessInstanceCancelReqVO();
        cancelReqVO.setId(processInstanceId);
        cancelReqVO.setReason(reason);
        // 管理员身份取消：不校验发起人、不校验流程定义的“允许撤销”配置
        processInstanceService.cancelProcessInstanceByAdmin(userId, cancelReqVO);
        return success(true);
    }

}

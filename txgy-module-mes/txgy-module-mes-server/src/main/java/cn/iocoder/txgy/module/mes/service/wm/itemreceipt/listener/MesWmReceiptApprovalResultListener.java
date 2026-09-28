package cn.iocoder.txgy.module.mes.service.wm.itemreceipt.listener;

import cn.iocoder.txgy.module.mes.service.wm.itemreceipt.MesWmReceiptApprovalTaskService;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 接收 BPM 服务转发的采购入库审批结果，落审批结果任务表异步消费
 *
 * 与报工审批的 MesFeedbackApprovalResultListener 同构：
 * bpm-server 的 WmItemReceiptStatusListener 按流程 key "mes-wm-item-receipt-approval"
 * 过滤后，将审批事件 HTTP POST 给本端点；先落任务表规避与提交方事务提交的时序差。
 *
 * @author OPENLAB BS
 */
@Slf4j
@RestController
@Validated
public class MesWmReceiptApprovalResultListener {

    @Resource
    private MesWmReceiptApprovalTaskService approvalTaskService;

    /**
     * 入库单审批结果事件（BPM 服务转发）
     */
    @Data
    public static class WmReceiptApprovalEvent {

        /** 流程实例编号 */
        private String id;
        /** 流程定义 Key */
        private String processDefinitionKey;
        /** 业务编号（采购入库单编号） */
        private String businessKey;
        /** BPM 流程实例状态：2 审批通过、3 驳回 */
        private Integer status;
        /** 审批原因 */
        private String reason;

    }

    @PostMapping("/rpc-api/mes/wm-receipt/on-approval-result")
    public void onApprovalResult(@RequestBody WmReceiptApprovalEvent event) {
        Long receiptId = parseReceiptId(event.getBusinessKey());
        if (receiptId == null) {
            log.warn("[onApprovalResult][businessKey 非法：{}，忽略]", event.getBusinessKey());
            return;
        }
        Long taskId = approvalTaskService.createTask(receiptId, event.getId(), event.getStatus(), event.getReason());
        log.info("[onApprovalResult][入库单({}) 流程实例({}) 已创建审批结果任务({})]", receiptId, event.getId(), taskId);
    }

    private Long parseReceiptId(String businessKey) {
        if (businessKey == null || businessKey.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(businessKey);
        } catch (NumberFormatException e) {
            return null;
        }
    }

}

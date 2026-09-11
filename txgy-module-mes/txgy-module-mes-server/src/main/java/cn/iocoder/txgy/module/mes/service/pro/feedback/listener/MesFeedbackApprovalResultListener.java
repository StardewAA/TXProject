package cn.iocoder.txgy.module.mes.service.pro.feedback.listener;

import cn.iocoder.txgy.module.mes.service.pro.feedback.MesFeedbackApprovalTaskService;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 接收 BPM 服务转发的报工审批结果，落审批结果任务表异步消费
 *
 * 与 MES 项目同步的 MesSaleOrderApprovedListener 同构：
 * bpm-server 的 MesFeedbackStatusListener 按流程 key "mes-feedback-approval" 过滤后，
 * 将审批事件 HTTP POST 给本端点；先落任务表规避与提交方事务提交的时序差。
 *
 * @author OPENLAB BS
 */
@Slf4j
@RestController
@Validated
public class MesFeedbackApprovalResultListener {

    @Resource
    private MesFeedbackApprovalTaskService approvalTaskService;

    /**
     * 报工审批结果事件（BPM 服务转发）
     */
    @Data
    public static class FeedbackApprovalEvent {

        /** 流程实例编号 */
        private String id;
        /** 流程定义 Key */
        private String processDefinitionKey;
        /** 业务编号（报工单编号） */
        private String businessKey;
        /** BPM 流程实例状态：2 审批通过、3 驳回 */
        private Integer status;
        /** 审批原因 */
        private String reason;

    }

    @PostMapping("/rpc-api/mes/feedback/on-approval-result")
    public void onApprovalResult(@RequestBody FeedbackApprovalEvent event) {
        Long feedbackId = parseFeedbackId(event.getBusinessKey());
        if (feedbackId == null) {
            log.warn("[onApprovalResult][businessKey 非法：{}，忽略]", event.getBusinessKey());
            return;
        }
        Long taskId = approvalTaskService.createTask(feedbackId, event.getId(), event.getStatus(), event.getReason());
        log.info("[onApprovalResult][报工单({}) 流程实例({}) 已创建审批结果任务({})]", feedbackId, event.getId(), taskId);
    }

    private Long parseFeedbackId(String businessKey) {
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

package cn.iocoder.txgy.module.mes.service.wm.productsales.listener;

import cn.iocoder.txgy.module.mes.service.wm.productsales.MesWmProductSalesApprovalTaskService;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 接收 BPM 服务转发的销售出库审批结果，落审批结果任务表异步消费
 *
 * 与采购入库审批的 MesWmReceiptApprovalResultListener 同构：
 * bpm-server 的 ProductSalesStatusListener 按流程 key "mes-wm-product-sales-approval"
 * 过滤后，将审批事件 HTTP POST 给本端点；先落任务表规避与提交方事务提交的时序差。
 *
 * @author OPENLAB BS
 */
@Slf4j
@RestController
@Validated
public class MesWmProductSalesApprovalResultListener {

    @Resource
    private MesWmProductSalesApprovalTaskService approvalTaskService;

    /**
     * 销售出库审批结果事件（BPM 服务转发）
     */
    @Data
    public static class ProductSalesApprovalEvent {

        /** 流程实例编号 */
        private String id;
        /** 流程定义 Key */
        private String processDefinitionKey;
        /** 业务编号（销售出库单编号） */
        private String businessKey;
        /** BPM 流程实例状态：2 审批通过、3 驳回 */
        private Integer status;
        /** 审批原因 */
        private String reason;

    }

    @PostMapping("/rpc-api/mes/wm-product-sales/on-approval-result")
    public void onApprovalResult(@RequestBody ProductSalesApprovalEvent event) {
        Long salesId = parseSalesId(event.getBusinessKey());
        if (salesId == null) {
            log.warn("[onApprovalResult][businessKey 非法：{}，忽略]", event.getBusinessKey());
            return;
        }
        Long taskId = approvalTaskService.createTask(salesId, event.getId(), event.getStatus(), event.getReason());
        log.info("[onApprovalResult][销售出库单({}) 流程实例({}) 已创建审批结果任务({})]", salesId, event.getId(), taskId);
    }

    private Long parseSalesId(String businessKey) {
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

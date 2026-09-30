package cn.iocoder.txgy.module.mes.service.pro.project.listener;

import cn.iocoder.txgy.module.mes.service.pro.project.MesProProjectSyncService;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 接收 BPM 服务转发的销售订单审批结果，创建项目同步任务
 *
 * 与 ERP 侧 ErpSaleOrderStatusListener（erp-server 接收端）同源：
 * bpm-server 的转发 Listener 按流程 key "erp-sale-order-approval" 过滤后，将审批事件
 * HTTP POST 给订阅方。MES 收到后先落任务表，由 MesProProjectSyncService 定时任务异步消费，
 * 规避与 ERP 事务提交的时序差。
 *
 * @author OPENLAB BS
 */
@Slf4j
@RestController
@Validated
public class MesSaleOrderApprovedListener {

    @Resource
    private MesProProjectSyncService projectSyncService;

    /**
     * 销售订单审批结果事件（BPM 服务转发）
     */
    @Data
    public static class SaleOrderApprovalEvent {

        /** 流程实例编号 */
        private String id;
        /** 流程定义 Key */
        private String processDefinitionKey;
        /** 业务编号（销售订单编号） */
        private String businessKey;
        /** BPM 流程实例状态：2 审批通过 */
        private Integer status;
        /** 审批原因 */
        private String reason;

    }

    @PostMapping("/rpc-api/mes/pro-project/on-sale-order-approved")
    public void onSaleOrderApproved(@RequestBody SaleOrderApprovalEvent event) {
        Long saleOrderId = parseSaleOrderId(event.getBusinessKey());
        if (saleOrderId == null) {
            log.warn("[onSaleOrderApproved][businessKey 非法：{}，忽略]", event.getBusinessKey());
            return;
        }
        // 仅审批通过时生成项目；驳回/撤销无需处理
        if (event.getStatus() == null || event.getStatus() != 2) {
            log.info("[onSaleOrderApproved][订单({}) 审批状态({}) 非通过，忽略]", saleOrderId, event.getStatus());
            return;
        }
        Long taskId = projectSyncService.createSyncTask(saleOrderId);
        log.info("[onSaleOrderApproved][订单({}) 已创建项目同步任务({})]", saleOrderId, taskId);
    }

    private Long parseSaleOrderId(String businessKey) {
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

package cn.iocoder.txgy.module.bpm.api.event;

import cn.iocoder.txgy.module.bpm.framework.flowable.core.util.BpmHttpRequestUtils;
import jakarta.annotation.Resource;
import org.springframework.web.client.RestTemplate;

/** 将生产工单审批结果转发到 MES 服务，回写工单状态。 */
public class MesWorkOrderStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource
    private RestTemplate loadBalancedRestTemplate;

    @Override
    protected String getProcessDefinitionKey() {
        // 与 MesProWorkOrderServiceImpl.BPM_PROCESS_DEFINITION_KEY 保持一致：生产工单审批流程
        return "mes-work-order-approval";
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        BpmHttpRequestUtils.executeBpmHttpRequest(event,
                "http://mes-server/rpc-api/mes/work-order/on-approval-result",
                loadBalancedRestTemplate);
    }
}

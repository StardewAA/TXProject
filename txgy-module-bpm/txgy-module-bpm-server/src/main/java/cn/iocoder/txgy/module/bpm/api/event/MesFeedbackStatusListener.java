package cn.iocoder.txgy.module.bpm.api.event;

import cn.iocoder.txgy.module.bpm.framework.flowable.core.util.BpmHttpRequestUtils;
import jakarta.annotation.Resource;
import org.springframework.web.client.RestTemplate;

/** 将报工审批结果转发到 MES 服务，回写报工单状态。 */
public class MesFeedbackStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource
    private RestTemplate loadBalancedRestTemplate;

    @Override
    protected String getProcessDefinitionKey() {
        // 与 MesProFeedbackServiceImpl.BPM_PROCESS_DEFINITION_KEY 保持一致：报工审批流程
        return "mes-feedback-approval";
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        BpmHttpRequestUtils.executeBpmHttpRequest(event,
                "http://mes-server/rpc-api/mes/feedback/on-approval-result",
                loadBalancedRestTemplate);
    }

}

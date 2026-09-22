package cn.iocoder.txgy.module.bpm.api.event;

import cn.iocoder.txgy.module.bpm.framework.flowable.core.util.BpmHttpRequestUtils;
import jakarta.annotation.Resource;
import org.springframework.web.client.RestTemplate;

/** 将委外发料审批结果转发到 MES 服务，回写发料单状态。 */
public class OutsourceIssueStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource
    private RestTemplate loadBalancedRestTemplate;

    @Override
    protected String getProcessDefinitionKey() {
        // 与 MesWmOutsourceIssueServiceImpl.BPM_PROCESS_DEFINITION_KEY 保持一致：委外发料单审批流程
        return "mes-wm-outsource-issue-approval";
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        BpmHttpRequestUtils.executeBpmHttpRequest(event,
                "http://mes-server/rpc-api/mes/wm-outsource-issue/on-approval-result",
                loadBalancedRestTemplate);
    }

}

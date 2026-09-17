package cn.iocoder.txgy.module.bpm.api.event;

import cn.iocoder.txgy.module.bpm.framework.flowable.core.util.BpmHttpRequestUtils;
import jakarta.annotation.Resource;
import org.springframework.web.client.RestTemplate;

/** 将采购入库审批结果转发到 MES 服务，回写入库单状态。 */
public class WmItemReceiptStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource
    private RestTemplate loadBalancedRestTemplate;

    @Override
    protected String getProcessDefinitionKey() {
        // 与 MesWmItemReceiptServiceImpl.BPM_PROCESS_DEFINITION_KEY 保持一致：采购入库单审批流程
        return "mes-wm-item-receipt-approval";
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        BpmHttpRequestUtils.executeBpmHttpRequest(event,
                "http://mes-server/rpc-api/mes/wm-receipt/on-approval-result",
                loadBalancedRestTemplate);
    }

}

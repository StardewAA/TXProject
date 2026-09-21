package cn.iocoder.txgy.module.bpm.api.event;

import cn.iocoder.txgy.module.bpm.framework.flowable.core.util.BpmHttpRequestUtils;
import jakarta.annotation.Resource;
import org.springframework.web.client.RestTemplate;

/** 将采购退货审批结果转发到 MES 服务，回写退货单状态。 */
public class ReturnVendorStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource
    private RestTemplate loadBalancedRestTemplate;

    @Override
    protected String getProcessDefinitionKey() {
        // 与 MesWmReturnVendorServiceImpl.BPM_PROCESS_DEFINITION_KEY 保持一致：采购退货单审批流程
        return "mes-wm-return-vendor-approval";
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        BpmHttpRequestUtils.executeBpmHttpRequest(event,
                "http://mes-server/rpc-api/mes/wm-return-vendor/on-approval-result",
                loadBalancedRestTemplate);
    }

}

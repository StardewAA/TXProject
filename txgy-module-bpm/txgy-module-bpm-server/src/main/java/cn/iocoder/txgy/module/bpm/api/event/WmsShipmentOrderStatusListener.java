package cn.iocoder.txgy.module.bpm.api.event;

import cn.iocoder.txgy.module.bpm.framework.flowable.core.util.BpmHttpRequestUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** 将 WMS 出库流程终态回写到 WMS 微服务。 */
@Component
public class WmsShipmentOrderStatusListener extends BpmProcessInstanceStatusEventListener {

    private static final String PROCESS_KEY = "wms_shipment_approval";

    @Resource
    private RestTemplate loadBalancedRestTemplate;

    @Override
    protected String getProcessDefinitionKey() {
        return PROCESS_KEY;
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        BpmHttpRequestUtils.executeBpmHttpRequest(event,
                "http://wms-server/rpc-api/wms/shipment-order/update-approval-status",
                loadBalancedRestTemplate);
    }
}

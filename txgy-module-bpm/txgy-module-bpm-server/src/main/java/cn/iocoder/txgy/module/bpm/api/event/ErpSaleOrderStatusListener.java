package cn.iocoder.txgy.module.bpm.api.event;

import cn.iocoder.txgy.module.bpm.framework.flowable.core.util.BpmHttpRequestUtils;
import jakarta.annotation.Resource;
import org.springframework.web.client.RestTemplate;

/** 将销售订单审批结果转发到 ERP 服务。 */
public class ErpSaleOrderStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource
    private RestTemplate loadBalancedRestTemplate;

    @Override
    protected String getProcessDefinitionKey() {
        return "erp-sale-order-approval";
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        BpmHttpRequestUtils.executeBpmHttpRequest(event,
                "http://erp-server/rpc-api/erp/sale-order/update-audit-status",
                loadBalancedRestTemplate);
    }

}

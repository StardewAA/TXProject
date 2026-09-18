package cn.iocoder.txgy.module.bpm.api.event;

import cn.iocoder.txgy.module.bpm.framework.flowable.core.util.BpmHttpRequestUtils;
import jakarta.annotation.Resource;
import org.springframework.web.client.RestTemplate;

/** 将销售订单审批结果转发到 MES 服务，触发生产项目自动生成。 */
public class MesSaleOrderStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource
    private RestTemplate loadBalancedRestTemplate;

    @Override
    protected String getProcessDefinitionKey() {
        // 与 ErpSaleOrderStatusListener 保持一致：销售订单审批流程
        return "erp-sale-order-approval";
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        BpmHttpRequestUtils.executeBpmHttpRequest(event,
                "http://mes-server/rpc-api/mes/pro-project/on-sale-order-approved",
                loadBalancedRestTemplate);
    }

}

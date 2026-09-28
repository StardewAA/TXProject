package cn.iocoder.txgy.module.bpm.api.event;

import cn.iocoder.txgy.module.bpm.framework.flowable.core.util.BpmHttpRequestUtils;
import jakarta.annotation.Resource;
import org.springframework.web.client.RestTemplate;

/** 将销售出库审批结果转发到 MES 服务，回写出库单状态。 */
public class ProductSalesStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource
    private RestTemplate loadBalancedRestTemplate;

    @Override
    protected String getProcessDefinitionKey() {
        // 与 MesWmProductSalesServiceImpl.BPM_PROCESS_DEFINITION_KEY 保持一致：销售出库单审批流程
        return "mes-wm-product-sales-approval";
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        BpmHttpRequestUtils.executeBpmHttpRequest(event,
                "http://mes-server/rpc-api/mes/wm-product-sales/on-approval-result",
                loadBalancedRestTemplate);
    }

}

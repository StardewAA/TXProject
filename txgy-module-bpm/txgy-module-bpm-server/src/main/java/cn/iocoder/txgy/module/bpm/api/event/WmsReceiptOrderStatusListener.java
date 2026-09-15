package cn.iocoder.txgy.module.bpm.api.event;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static cn.iocoder.txgy.framework.web.core.util.WebFrameworkUtils.HEADER_TENANT_ID;

/** 将真实 BPM 入库审批终态同步回 WMS；回写失败必须向调用方报错。 */
@Component
public class WmsReceiptOrderStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource
    private RestTemplate loadBalancedRestTemplate;

    @Override
    protected String getProcessDefinitionKey() {
        return "wms_receipt_approval";
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        if (!BpmProcessInstanceStatusEnum.isProcessEndStatus(event.getStatus())) {
            return;
        }
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        Long tenantId = TenantContextHolder.getTenantId();
        Assert.notNull(user, "入库审批回写缺少 RPC 登录上下文");
        Assert.notNull(tenantId, "入库审批回写缺少租户上下文");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HEADER_TENANT_ID, tenantId.toString());
        // 与 LoginUserRequestInterceptor 保持一致，RestTemplate 不执行 Feign 拦截器。
        headers.set(SecurityFrameworkUtils.LOGIN_USER_HEADER,
                URLEncoder.encode(JsonUtils.toJsonString(user), StandardCharsets.UTF_8));
        ResponseEntity<String> response = loadBalancedRestTemplate.exchange(
                "http://wms-server/rpc-api/wms/receipt-order/update-approval-status",
                HttpMethod.POST, new HttpEntity<>(event, headers), String.class);
        CommonResult<?> result = JsonUtils.parseObject(response.getBody(), CommonResult.class);
        Assert.notNull(result, "入库审批回写响应为空");
        result.checkError();
    }
}

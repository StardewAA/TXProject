package cn.iocoder.txgy.module.ai.api.customer;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.ai.api.customer.dto.AiCustomerServiceReplyReqDTO;
import cn.iocoder.txgy.module.ai.api.customer.dto.AiCustomerServiceReplyRespDTO;
import cn.iocoder.txgy.module.ai.enums.ApiConstants;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = ApiConstants.NAME, contextId = "aiCustomerServiceApi")
public interface AiCustomerServiceApi {

    String PREFIX = ApiConstants.PREFIX + "/customer-service";

    @PostMapping(PREFIX + "/reply")
    CommonResult<AiCustomerServiceReplyRespDTO> reply(
            @Valid @RequestBody AiCustomerServiceReplyReqDTO request);
}

package cn.iocoder.txgy.module.ai.api.model;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.ai.api.model.dto.AiRuntimeChatReqDTO;
import cn.iocoder.txgy.module.ai.api.model.dto.AiRuntimeChatRespDTO;
import cn.iocoder.txgy.module.ai.api.model.dto.AiRuntimeEmbeddingReqDTO;
import cn.iocoder.txgy.module.ai.api.model.dto.AiRuntimeEmbeddingRespDTO;
import cn.iocoder.txgy.module.ai.enums.ApiConstants;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** 业务模块统一通过 AI 服务调用可热切换的模型。 */
@FeignClient(name = ApiConstants.NAME)
public interface AiRuntimeModelApi {

    String PREFIX = ApiConstants.PREFIX + "/runtime-model";

    @PostMapping(PREFIX + "/chat")
    CommonResult<AiRuntimeChatRespDTO> chat(@Valid @RequestBody AiRuntimeChatReqDTO request);

    @PostMapping(PREFIX + "/embedding")
    CommonResult<AiRuntimeEmbeddingRespDTO> embedding(@Valid @RequestBody AiRuntimeEmbeddingReqDTO request);
}

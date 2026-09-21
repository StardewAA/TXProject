package cn.iocoder.txgy.module.ai.api.model;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.ai.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import static cn.iocoder.txgy.module.ai.enums.ApiConstants.PREFIX;

/**
 * AI 聊天模型 API
 *
 * <p>供其他业务模块（如 WMS 自研 AI 客服）按模型编号取回连接信息。
 * 这样模型/Key 仍在 AI 模块统一维护，业务模块无需自行拼地址、也无需读 AI 的库表。
 */
@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - AI 聊天模型")
public interface AiChatModelApi {

    String PREFIX_RPC = PREFIX + "/chat-model";

    /**
     * 获取指定模型编号的聊天模型连接信息
     *
     * @param modelId 模型编号（ai_model.id）
     * @return 连接信息；模型不存在或未配置 Key 时返回错误
     */
    @GetMapping(PREFIX_RPC + "/get-conn")
    @Operation(summary = "获取聊天模型连接信息")
    CommonResult<AiChatModelConnDTO> getChatModelConn(
            @Parameter(description = "模型编号", required = true, example = "22")
            @RequestParam("modelId") Long modelId);

}

package cn.iocoder.txgy.module.erp.controller.admin.assistant;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.ratelimiter.core.annotation.RateLimiter;
import cn.iocoder.txgy.framework.ratelimiter.core.keyresolver.impl.UserRateLimiterKeyResolver;
import cn.iocoder.txgy.module.erp.controller.admin.assistant.vo.ErpAssistantChatReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.assistant.vo.ErpAssistantChatRespVO;
import cn.iocoder.txgy.module.erp.service.assistant.ErpAssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * ERP 智能助手（首页 AI 对话）
 *
 * <p>说明：本接口只要求<b>登录</b>即可调用（首页对已授权用户开放），未额外限定业务权限码，
 * 避免出现「能进首页却问不了」的情况。</p>
 *
 * @author OPENLAB BS
 */
@Tag(name = "管理后台 - ERP 智能助手")
@RestController
@RequestMapping("/erp/assistant")
@Validated
public class ErpAssistantController {

    @Resource
    private ErpAssistantService assistantService;

    @PostMapping("/chat")
    @RateLimiter(time = 1, timeUnit = TimeUnit.MINUTES, count = 20,
            keyResolver = UserRateLimiterKeyResolver.class, message = "提问过于频繁，请稍后再试")
    @Operation(summary = "ERP 智能助手对话", description = "基于真实业务数据回答：找单据/产品/客商、查库存、查经营统计、看待办")
    public CommonResult<ErpAssistantChatRespVO> chat(@Validated @RequestBody ErpAssistantChatReqVO reqVO) {
        return success(assistantService.chat(reqVO.getMessage()));
    }

}
